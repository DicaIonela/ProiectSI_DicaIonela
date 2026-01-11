package multiagentbank;

import jade.core.Agent;
import jade.core.AID;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.core.behaviours.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

public class ShutdownAgent extends Agent {
    private JFrame gui;
    private JTextArea logArea;

    protected void setup() {
        System.out.println("Shutdown Agent starting...");
        createGUI();
    }

    private void createGUI() {
        gui = new JFrame("Shutdown Controller");
        gui.setSize(500, 400);
        gui.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        gui.setLayout(new BorderLayout());

        JButton shutdownBtn = new JButton("SHUTDOWN SYSTEM");
        shutdownBtn.setFont(new Font("Arial", Font.BOLD, 16));
        shutdownBtn.setBackground(Color.RED);
        shutdownBtn.setForeground(Color.WHITE);
        shutdownBtn.setPreferredSize(new Dimension(200, 50));

        shutdownBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int confirm = JOptionPane.showConfirmDialog(gui,
                        "Shutdown entire system?\n\nSequence: Branches -> Bank -> Shutdown",
                        "Confirm",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (confirm == JOptionPane.YES_OPTION) {
                    shutdownBtn.setEnabled(false);
                    initiateShutdown();
                }
            }
        });

        JPanel topPanel = new JPanel();
        topPanel.add(shutdownBtn);
        gui.add(topPanel, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        gui.add(new JScrollPane(logArea), BorderLayout.CENTER);

        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel infoLabel = new JLabel("Shutdown order: Branches first, then Bank");
        infoLabel.setForeground(new Color(0, 100, 200));
        infoPanel.add(infoLabel);
        gui.add(infoPanel, BorderLayout.SOUTH);

        gui.setVisible(true);
        appendLog("Shutdown Controller ready");
        appendLog("Click button to shutdown system");
    }

    private void initiateShutdown() {
        appendLog("=== STARTING SHUTDOWN SEQUENCE ===");
        appendLog("");
        
        addBehaviour(new SequentialBehaviour(this) {
            {
                // Pas 1: Găsește și oprește filialele
                addSubBehaviour(new OneShotBehaviour() {
                    public void action() {
                        appendLog("STEP 1: Locating branches...");
                        List<AID> branches = findBranches();
                        appendLog("Found " + branches.size() + " branch(es)");
                        appendLog("");
                        
                        if (!branches.isEmpty()) {
                            appendLog("STEP 2: Shutting down branches...");
                            for (AID branch : branches) {
                                shutdownAgent(branch);
                            }
                        } else {
                            appendLog("No branches to shutdown");
                            appendLog("");
                        }
                    }
                });
                
                // Pas 2: Așteaptă confirmări de la filiale
                addSubBehaviour(new Behaviour() {
                    private int expectedAcks = 0;
                    private int receivedAcks = 0;
                    private boolean initialized = false;
                    private long timeout;
                    
                    public void action() {
                        if (!initialized) {
                            expectedAcks = findBranches().size();
                            timeout = System.currentTimeMillis() + 7000;
                            initialized = true;
                            
                            if (expectedAcks == 0) return; // Skip if no branches
                        }
                        
                        MessageTemplate mt = MessageTemplate.MatchContent("SHUTDOWN_ACK");
                        ACLMessage reply = myAgent.receive(mt);
                        
                        if (reply != null) {
                            receivedAcks++;
                            appendLog(" OK ACK received from: " + reply.getSender().getLocalName());
                            
                            if (receivedAcks >= expectedAcks) {
                                appendLog(" OK All branches acknowledged (" + receivedAcks + "/" + expectedAcks + ")");
                                appendLog("");
                            }
                        } else {
                            block(100);
                        }
                    }
                    
                    public boolean done() {
                        if (expectedAcks == 0) return true;
                        if (receivedAcks >= expectedAcks) return true;
                        if (System.currentTimeMillis() > timeout) {
                            appendLog(" WARNING: Timeout - Received " + receivedAcks + "/" + expectedAcks + " confirmations");
                            appendLog("");
                            return true;
                        }
                        return false;
                    }
                });
                
                // Pas 3: Pauză înainte de a închide banca
                addSubBehaviour(new WakerBehaviour(myAgent, 2000) {
                    protected void onWake() {
                        appendLog("Waiting completed");
                        appendLog("");
                    }
                });
                
                // Pas 4: Găsește și oprește banca
                addSubBehaviour(new OneShotBehaviour() {
                    public void action() {
                        appendLog("STEP 3: Locating central bank...");
                        AID bank = findBank();
                        
                        if (bank != null) {
                            appendLog("STEP 4: Shutting down central bank...");
                            shutdownAgent(bank);
                        } else {
                            appendLog("Bank not found");
                            appendLog("");
                        }
                    }
                });
                
                // Pas 5: Așteaptă confirmarea de la bancă
                addSubBehaviour(new Behaviour() {
                    private boolean bankAckReceived = false;
                    private long timeout;
                    private boolean bankExists = true;
                    private boolean initialized = false;
                    
                    public void action() {
                        if (!initialized) {
                            timeout = System.currentTimeMillis() + 7000;
                            initialized = true;
                            
                            if (findBank() == null) {
                                bankExists = false;
                                return;
                            }
                        }
                        
                        MessageTemplate mt = MessageTemplate.MatchContent("SHUTDOWN_ACK");
                        ACLMessage reply = myAgent.receive(mt);
                        
                        if (reply != null) {
                            appendLog(" OK ACK received from: " + reply.getSender().getLocalName());
                            appendLog("OK Bank closed");
                            appendLog("");
                            bankAckReceived = true;
                        } else {
                            block(100);
                        }
                    }
                    
                    public boolean done() {
                        if (!bankExists) return true;
                        if (bankAckReceived) return true;
                        if (initialized && System.currentTimeMillis() > timeout) {
                            appendLog(" WARNING: Bank did not respond");
                            appendLog("");
                            return true;
                        }
                        return false;
                    }
                });
                
                // Pas 6: Finalizare
                addSubBehaviour(new WakerBehaviour(myAgent, 3000) {
                    protected void onWake() {
                        appendLog("=== SHUTDOWN COMPLETE ===");
                        appendLog("Shutdown controller will close now...");
                        doDelete();
                    }
                });
            }
        });
    }

    private List<AID> findBranches() {
        List<AID> branches = new ArrayList<AID>();
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("branch-service");
        template.addServices(sd);

        try {
            DFAgentDescription[] result = DFService.search(this, template);
            for (int i = 0; i < result.length; i++) {
                branches.add(result[i].getName());
            }
        } catch (FIPAException fe) {
            appendLog("ERROR finding branches: " + fe.getMessage());
        }
        return branches;
    }

    private AID findBank() {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("bank-service");
        template.addServices(sd);

        try {
            DFAgentDescription[] result = DFService.search(this, template);
            if (result.length > 0) {
                return result[0].getName();
            }
        } catch (FIPAException fe) {
            appendLog("ERROR finding bank: " + fe.getMessage());
        }
        return null;
    }

    private void shutdownAgent(AID agent) {
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.addReceiver(agent);
        msg.setContent("SHUTDOWN");
        msg.setConversationId("shutdown");
        send(msg);
    }

    private void appendLog(String message) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (message.isEmpty()) {
                    logArea.append("\n");
                } else {
                    String timestamp = new java.text.SimpleDateFormat("HH:mm:ss")
                            .format(new java.util.Date());
                    logArea.append("[" + timestamp + "] " + message + "\n");
                }
                logArea.setCaretPosition(logArea.getDocument().getLength());
            }
        });
    }

    protected void takeDown() {
        if (gui != null) {
            gui.dispose();
        }
        System.out.println("Shutdown Agent terminated");
    }
}