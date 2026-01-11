package multiagentbank;

import jade.core.*;
import jade.domain.*;
import jade.domain.FIPAAgentManagement.*;
import jade.lang.acl.*;
import jade.core.behaviours.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class BranchAgent extends Agent {
    // Componente GUI
    private JFrame gui;
    private JPanel mainContainer;
    private CardLayout cardLayout;

    // Panel Autentificare
    private JTextField loginAccField, loginPinField;

    // Panel Operatiuni
    private JLabel headerLabel;
    private JLabel balanceLabel;
    private JTextField amountField;
    private JTextArea logArea;

    // Stare interna
    private AID bank;
    private String currentAcc = null;
    private String currentPin = null;

    protected void setup() {
        register();
        findBank();
        initGui();

        // Pornire comportamente pentru ascultare notificari si comenzi de oprire
        addBehaviour(new NotificationListener());
        addBehaviour(new ShutdownHandler());
    }

    private void initGui() {
        gui = new JFrame(getLocalName());
        gui.setSize(400, 500);
        gui.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        gui.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                doDelete();
            }
        });

        cardLayout = new CardLayout();
        mainContainer = new JPanel(cardLayout);

        // --- ECRAN 1: AUTENTIFICARE ---
        JPanel authPanel = new JPanel(new GridBagLayout());
        authPanel.setBackground(new Color(240, 240, 245));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("Welcome to " + getLocalName());
        title.setFont(new Font("Arial", Font.BOLD, 18));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        authPanel.add(title, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = 1;
        authPanel.add(new JLabel("Account:"), gbc);

        loginAccField = new JTextField(15);
        gbc.gridx = 1;
        authPanel.add(loginAccField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        authPanel.add(new JLabel("PIN:"), gbc);

        loginPinField = new JTextField(15);
        gbc.gridx = 1;
        authPanel.add(loginPinField, gbc);

        JButton btnLogin = new JButton("LOGIN");
        btnLogin.setBackground(new Color(100, 200, 100));
        JButton btnCreate = new JButton("CREATE NEW ACCOUNT");

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(20, 5, 5, 5);
        authPanel.add(btnLogin, gbc);

        gbc.gridy = 4;
        gbc.insets = new Insets(5, 5, 5, 5);
        authPanel.add(btnCreate, gbc);

        // --- ECRAN 2: OPERATIUNI ---
        JPanel opsPanel = new JPanel(new BorderLayout(10, 10));
        opsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel topInfo = new JPanel(new GridLayout(2, 1));
        headerLabel = new JLabel("Account: ???");
        headerLabel.setFont(new Font("Arial", Font.BOLD, 14));
        balanceLabel = new JLabel("Balance: ---");
        balanceLabel.setFont(new Font("Arial", Font.BOLD, 24));
        balanceLabel.setForeground(new Color(0, 100, 0));
        balanceLabel.setHorizontalAlignment(SwingConstants.CENTER);
        topInfo.add(headerLabel);
        topInfo.add(balanceLabel);
        opsPanel.add(topInfo, BorderLayout.NORTH);

        JPanel centerAction = new JPanel(new GridLayout(4, 1, 5, 5));
        amountField = new JTextField();
        amountField.setBorder(BorderFactory.createTitledBorder("Amount"));
        JButton btnDep = new JButton("DEPOSIT");
        JButton btnWdr = new JButton("WITHDRAW");
        JButton btnLogout = new JButton("LOGOUT");
        btnLogout.setBackground(new Color(255, 100, 100));

        centerAction.add(amountField);
        centerAction.add(btnDep);
        centerAction.add(btnWdr);
        centerAction.add(btnLogout);
        opsPanel.add(centerAction, BorderLayout.CENTER);

        logArea = new JTextArea(8, 20);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        opsPanel.add(new JScrollPane(logArea), BorderLayout.SOUTH);

        // --- Adaugare Listeners ---
        btnLogin.addActionListener(e -> requestLogin());
        btnCreate.addActionListener(e -> requestCreate());
        btnDep.addActionListener(e -> requestOp("DEPOSIT"));
        btnWdr.addActionListener(e -> requestOp("WITHDRAW"));
        btnLogout.addActionListener(e -> requestLogout());

        mainContainer.add(authPanel, "AUTH");
        mainContainer.add(opsPanel, "OPS");

        gui.add(mainContainer);
        gui.setVisible(true);
    }

    // --- LOGICA DE TRIMITERE CERERI ---
    private void requestLogin() {
        if (bank == null) findBank();
        String acc = loginAccField.getText().trim();
        String pin = loginPinField.getText().trim();
        if (acc.isEmpty() || pin.isEmpty()) return;

        sendReq("LOGIN|" + acc + "|" + pin, res -> {
            String[] p = res.split(":");
            switchToOps(acc, pin, p[2]);
            log("Logged in successfully.");
        });
    }

    private void requestCreate() {
        if (bank == null) findBank();
        String pin = JOptionPane.showInputDialog(gui, "Set PIN for new account:");
        if (pin == null || pin.length() < 4) return;

        sendReq("CREATE|NEW|" + pin, res -> {
            String[] p = res.split(":");
            String newAcc = p[1];
            String bal = p[2];
            JOptionPane.showMessageDialog(gui, "Account Created: " + newAcc);
            loginAccField.setText(newAcc);
            switchToOps(newAcc, pin, bal);
            log("Account created.");
        });
    }

    private void requestOp(String type) {
        String amt = amountField.getText().trim();
        if (amt.isEmpty()) return;

        // Trimite cererea. Confirmarea vine pe doua canale:
        // 1. Raspuns direct (Request-Reply) pentru confirmare actiune
        // 2. Notificare (Propagate) pentru actualizare sold GUI
        sendReq(type + "|" + currentAcc + "|" + currentPin + "|" + amt, res -> {
            amountField.setText("");
            log("Operation requested...");
        });
    }

    private void requestLogout() {
        if (currentAcc != null) {
            // Trimite logout pentru a dezabona agentul de la notificari
            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(bank);
            msg.setContent("LOGOUT|" + currentAcc);
            send(msg);
        }
        currentAcc = null;
        currentPin = null;
        logArea.setText("");
        cardLayout.show(mainContainer, "AUTH");
    }

    private void switchToOps(String acc, String pin, String bal) {
        this.currentAcc = acc;
        this.currentPin = pin;
        headerLabel.setText("Account: " + acc);
        balanceLabel.setText(bal + " RON");
        cardLayout.show(mainContainer, "OPS");
    }

    // --- COMUNICARE AGENT ---
    // Trimite mesaj REQUEST si asteapta raspuns (pseudo-sincron)
    private void sendReq(String content, java.util.function.Consumer<String> successCallback) {
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.addReceiver(bank);
        msg.setContent(content);
        String cid = "req-" + System.currentTimeMillis();
        msg.setConversationId(cid);
        send(msg);

        addBehaviour(new SimpleBehaviour(this) {
            boolean done = false;
            long timeout = System.currentTimeMillis() + 4000;

            public void action() {
                MessageTemplate mt = MessageTemplate.MatchConversationId(cid);
                ACLMessage r = receive(mt);
                if (r != null) {
                    done = true;
                    if (r.getPerformative() == ACLMessage.INFORM) {
                        successCallback.accept(r.getContent());
                    } else {
                        JOptionPane.showMessageDialog(gui, "Error: " + r.getContent());
                    }
                } else {
                    if (System.currentTimeMillis() > timeout) {
                        done = true;
                        JOptionPane.showMessageDialog(gui, "Timeout: Bank did not respond.");
                    } else
                        block(100);
                }
            }

            public boolean done() {
                return done;
            }
        });
    }

    // --- ASCULTARE NOTIFICARI LIVE ---
    // Asculta mesaje PROPAGATE de la banca pentru sincronizarea soldului in timp real
    private class NotificationListener extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.PROPAGATE),
                    MessageTemplate.MatchConversationId("balance-update"));
            ACLMessage msg = receive(mt);
            if (msg != null) {
                // Format: BALANCE|NoulSold|Sursa
                String[] p = msg.getContent().split("\\|");
                if (p[0].equals("BALANCE")) {
                    String newBal = p[1];
                    String source = p[2];
                    SwingUtilities.invokeLater(() -> {
                        balanceLabel.setText(newBal + " RON");
                        String logMsg = (source.equals(getLocalName())) ? 
                            "You updated the balance to " + newBal :
                            "[" + source + "] changed balance to " + newBal;
                        log(logMsg);
                    });
                }
            } else {
                block();
            }
        }
    }

    // --- UTILITARE ---
    private void register() {
        DFAgentDescription d = new DFAgentDescription();
        d.setName(getAID());
        ServiceDescription s = new ServiceDescription();
        s.setType("branch-service");
        s.setName(getLocalName());
        d.addServices(s);
        try {
            DFService.register(this, d);
        } catch (Exception e) {
        }
    }

    private void findBank() {
        DFAgentDescription t = new DFAgentDescription();
        ServiceDescription s = new ServiceDescription();
        s.setType("bank-service");
        t.addServices(s);
        try {
            DFAgentDescription[] r = DFService.search(this, t);
            if (r.length > 0)
                bank = r[0].getName();
        } catch (Exception e) {
        }
    }

    private void log(String m) {
        SwingUtilities.invokeLater(() -> logArea.append(m + "\n"));
    }

    // Asculta comanda de shutdown pentru a inchide GUI-ul si agentul
    private class ShutdownHandler extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                    MessageTemplate.MatchContent("SHUTDOWN"));
            ACLMessage msg = receive(mt);
            if (msg != null) {
                // Trimite ACK
                ACLMessage ack = msg.createReply();
                ack.setContent("SHUTDOWN_ACK");
                send(ack);

                // Închide după un scurt delay pentru ca ACK-ul să fie trimis
                addBehaviour(new WakerBehaviour(myAgent, 300) {
                    protected void onWake() {
                        gui.dispose();
                        doDelete();
                    }
                });
            } else
                block();
        }
    }

    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (Exception e) {
        }
        System.out.println(getLocalName() + " terminated");
    }
}