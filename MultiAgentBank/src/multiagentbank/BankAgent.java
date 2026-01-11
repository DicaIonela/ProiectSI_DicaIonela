package multiagentbank;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BankAgent extends Agent {
    private JFrame gui;
    private JTextArea logArea;
    private Map<String, Double> accounts = new ConcurrentHashMap<>();
    private Map<String, String> pins = new ConcurrentHashMap<>();

    // Lista sesiunilor active pentru mecanismul Publish-Subscribe
    private Map<String, Set<AID>> activeSessions = new ConcurrentHashMap<>();

    private static final String FILE = "bank_accounts.dat";

    protected void setup() {
        // Initializare agent: incarcare date, inregistrare DF si pornire comportamente
        loadData();
        registerDF();
        createGui();
        addBehaviour(new RequestHandler());
        addBehaviour(new ShutdownHandler());
        log("Central Bank STARTED. Database loaded.");
    }

    private void registerDF() {
        DFAgentDescription d = new DFAgentDescription();
        d.setName(getAID());
        ServiceDescription s = new ServiceDescription();
        s.setType("bank-service");
        s.setName("central-bank");
        d.addServices(s);
        try {
            DFService.register(this, d);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createGui() {
        gui = new JFrame("Central Bank Server");
        gui.setSize(500, 300);
        gui.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        gui.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                JOptionPane.showMessageDialog(gui, "Use Shutdown Controller to close the system!");
            }
        });

        logArea = new JTextArea();
        logArea.setEditable(false);
        gui.add(new JScrollPane(logArea));
        gui.setVisible(true);
    }

    private String createAccount(String pin) {
        Random r = new Random();
        String acc;
        do {
            acc = "RO" + (10 + r.nextInt(89)) + "BANK" + (1000 + r.nextInt(9000));
            // Generare IBAN unic. Verifica daca exista deja pentru a evita duplicatele
        } while (accounts.containsKey(acc));

        accounts.put(acc, 0.0);
        pins.put(acc, pin);
        saveData();
        return acc;
    }

    private void subscribe(String account, AID agent) {
        activeSessions.computeIfAbsent(account, k -> Collections.synchronizedSet(new HashSet<>()))
                .add(agent);
    }

    private void unsubscribe(String account, AID agent) {
        if (activeSessions.containsKey(account)) {
            activeSessions.get(account).remove(agent);
        }
    }

    // Trimite mesaj PROPAGATE catre toate filialele conectate pentru actualizare live
    private void notifySubscribers(String account, double newBalance, String sourceBranch) {
        Set<AID> subscribers = activeSessions.get(account);
        if (subscribers == null || subscribers.isEmpty())
            return;

        ACLMessage notification = new ACLMessage(ACLMessage.PROPAGATE);
        notification.setConversationId("balance-update");
        notification.setContent("BALANCE|" + newBalance + "|" + sourceBranch);

        for (AID sub : subscribers) {
            notification.addReceiver(sub);
        }
        send(notification);
    }

    // Gestioneaza cererile de la filiale: creare cont, autentificare si tranzactii
    private class RequestHandler extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                MessageTemplate.not(MessageTemplate.MatchContent("SHUTDOWN"))
            );
            ACLMessage msg = receive(mt);
            if (msg == null) {
                block();
                return;
            }

            ACLMessage reply = msg.createReply();
            String content = msg.getContent();
            String senderName = msg.getSender().getLocalName();

            try {
                String[] parts = content.split("\\|");
                String type = parts[0];

                if (type.equals("CREATE")) {
                    String pin = parts[2];
                    String newAcc = createAccount(pin);
                    // Creare cont nou si abonare automata la notificari
                    subscribe(newAcc, msg.getSender());
                    reply.setContent("SUCCESS:" + newAcc + ":0.0");
                    reply.setPerformative(ACLMessage.INFORM);
                    log(senderName + " created account " + newAcc);

                } else if (type.equals("LOGIN")) {
                    String acc = parts[1];
                    String pin = parts[2];
                    // Validare credentiale si adaugare in lista de sesiuni active
                    if (isValid(acc, pin)) {
                        subscribe(acc, msg.getSender());
                        double bal = accounts.get(acc);
                        reply.setContent("SUCCESS:Logged In:" + bal);
                        reply.setPerformative(ACLMessage.INFORM);
                        log(senderName + " logged into " + acc);
                    } else {
                        reply.setPerformative(ACLMessage.FAILURE);
                        reply.setContent("ERROR:Invalid Credentials");
                    }

                } else if (type.equals("LOGOUT")) {
                    String acc = parts[1];
                    unsubscribe(acc, msg.getSender());
                    reply.setContent("SUCCESS:Logged Out");
                    reply.setPerformative(ACLMessage.INFORM);
                    log(senderName + " logged out from " + acc);

                } else {
                    String acc = parts[1];
                    String pin = parts[2];
                    double amount = Double.parseDouble(parts[3]);

                    if (!isValid(acc, pin)) {
                        reply.setPerformative(ACLMessage.FAILURE);
                        reply.setContent("ERROR:Auth Failed");
                    } else {
                        boolean success = processTransaction(type, acc, amount);
                        if (success) {
                            double newBal = accounts.get(acc);
                            saveData();
                            // Procesare tranzactie si notificare tuturor filialelor despre noul sold
                            reply.setPerformative(ACLMessage.INFORM);
                            reply.setContent("SUCCESS:Operation Complete");
                            notifySubscribers(acc, newBal, senderName);
                            log(senderName + " " + type + " " + amount + " on " + acc);
                        } else {
                            reply.setPerformative(ACLMessage.FAILURE);
                            reply.setContent("ERROR:Insufficient Funds");
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                reply.setPerformative(ACLMessage.FAILURE);
                reply.setContent("ERROR:System Error");
            }

            send(reply);
        }
    }

    private boolean isValid(String acc, String pin) {
        return pins.containsKey(acc) && pins.get(acc).equals(pin);
    }

    private boolean processTransaction(String type, String acc, double amount) {
        double balance = accounts.get(acc);
        if (type.equals("DEPOSIT")) {
            accounts.put(acc, balance + amount);
            return true;
        } else if (type.equals("WITHDRAW")) {
            if (balance >= amount) {
                accounts.put(acc, balance - amount);
                return true;
            }
        }
        return false;
    }

    // Asculta comanda de oprire, salveaza datele si inchide agentul
    private class ShutdownHandler extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                    MessageTemplate.MatchContent("SHUTDOWN"));
            ACLMessage msg = receive(mt);
            if (msg != null) {
                log("Shutdown command received. Saving data...");
                saveData();

                // Trimite ACK înainte de a se închide
                ACLMessage ack = msg.createReply();
                ack.setContent("SHUTDOWN_ACK");
                send(ack);

                log("Shutdown ACK sent. Closing...");

                // Așteaptă puțin pentru ca ACK-ul să fie trimis
                addBehaviour(new WakerBehaviour(myAgent, 500) {
                    protected void onWake() {
                        SwingUtilities.invokeLater(() -> gui.dispose());
                        doDelete();
                    }
                });
            } else {
                block();
            }
        }
    }

    // Salvare si incarcare date folosind serializare in fisier binar
    private void loadData() {
        File f = new File(FILE);
        if (!f.exists())
            return;
        try (ObjectInputStream o = new ObjectInputStream(new FileInputStream(f))) {
            accounts = (Map<String, Double>) o.readObject();
            pins = (Map<String, String>) o.readObject();
        } catch (Exception e) {
            log("Error loading data.");
        }
    }

    private void saveData() {
        try (ObjectOutputStream o = new ObjectOutputStream(new FileOutputStream(FILE))) {
            o.writeObject(accounts);
            o.writeObject(pins);
        } catch (Exception e) {
            log("Error saving data.");
        }
    }

    private void log(String m) {
        SwingUtilities.invokeLater(() -> logArea.append(m + "\n"));
    }

    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (Exception e) {
        }
        System.out.println("Central Bank terminated");
    }
}