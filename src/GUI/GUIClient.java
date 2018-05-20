package GUI;

import Server.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

public class GUIClient {
    private JPanel serverPanel;
    private JPanel clientPanel;

    private JPanel serverControlPanel;
    private JPanel clientControlPanel;

    private JTextArea serverTextArea;
    private JTextArea clientTextArea;

    // Used for storage of the input
    private JTextField ipServerAddressField;
    private JTextField ipClientAddressField;

    private JTextField serverPortNumberInput;
    private JTextField clientPortNumberInput;
    private JProgressBar clientProgressBar;

    private JTextField payloadSize;

    private String filePath;
    private String fileName;

    private String finalIp = "";
    private String finalPort = "";

    private Thread serverThread;

    public static void main(String[] args){
        new GUIClient();
    }

    private GUIClient(){
        JFrame window = new JFrame("Server");
        window.setSize(350,400);
        window.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent windowEvent){
                System.exit(0);
            }
        });

        JTabbedPane tabbedPane = new JTabbedPane();
        window.add(tabbedPane);

        serverPanel = createServerPanel();
        tabbedPane.addTab("Server",serverPanel);
        initServer();
        clientPanel = createClientPanel();
        tabbedPane.addTab("Client",clientPanel);
        initClient();

        window.setVisible(true);
    }

    private JPanel createClientPanel(){
        JPanel clientPanel = new JPanel();

        clientPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        clientPanel.setLayout(new GridLayout(3, 1));
        JLabel clientHeaderLabel = new JLabel("", JLabel.CENTER);
        clientHeaderLabel.setText("Initialize the client");

        clientControlPanel = new JPanel();
        clientControlPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        clientPanel.add(clientHeaderLabel);
        clientPanel.add(clientControlPanel);
        clientPanel.setVisible(true);
        return clientPanel;
    }

    private JPanel createServerPanel(){
        JPanel serverPanel = new JPanel();

        serverPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        serverPanel.setLayout(new GridLayout(3, 1));
        JLabel serverHeaderLabel = new JLabel("", JLabel.CENTER);
        serverHeaderLabel.setText("Initialize the server");

        serverControlPanel = new JPanel();
        serverControlPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        serverPanel.add(serverHeaderLabel);
        serverPanel.add(serverControlPanel);
        serverPanel.setVisible(true);
        return serverPanel;
    }

    private void initClient(){
        clientTextArea = new JTextArea();

        clientTextArea.setColumns(20);
        clientTextArea.setLineWrap(true);
        clientTextArea.setRows(5);
        clientTextArea.setWrapStyleWord(true);
        clientTextArea.setEditable(false);
        JScrollPane clientScrollPane = new JScrollPane(clientTextArea);
        clientScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        ipClientAddressField = new JTextField("",20);
        ipClientAddressField.setText("localhost");
        clientPortNumberInput = new JTextField("",20);
        clientPortNumberInput.setText("4200");

        ipClientAddressField.setActionCommand("IPClient");
        ipClientAddressField.addActionListener(new InformationListener());

        clientPortNumberInput.setActionCommand("PORTClient");
        clientPortNumberInput.addActionListener(new InformationListener());

        clientProgressBar = new JProgressBar();
        clientProgressBar.setMaximum(100);
        clientProgressBar.setMinimum(0);
        clientProgressBar.setValue(0);
        clientProgressBar.setStringPainted(true);

        JButton submitButton = new JButton("Start");
        submitButton.setActionCommand("SubmitClient");
        submitButton.addActionListener(new InformationListener());

        JButton fileButton = new JButton("Search File");
        fileButton.setActionCommand("File");
        fileButton.addActionListener(new InformationListener());

        payloadSize = new JTextField("",20);

        JLabel ipClientText = new JLabel("Server IP   ");
        JLabel clientPortText = new JLabel("Server Port");
        JLabel payloadText = new JLabel("Packet Size");
        JLabel loadingText = new JLabel("Loading Progress");

        //JFileChooser chooser = new JFileChooser();
        clientControlPanel.add(ipClientText);
        clientControlPanel.add(Box.createRigidArea(new Dimension(0,2)));

        clientControlPanel.add(ipClientAddressField);

        clientControlPanel.add(clientPortText);
        clientControlPanel.add(Box.createRigidArea(new Dimension(0,2)));
        clientControlPanel.add(clientPortNumberInput);

        clientControlPanel.add(payloadText);
        clientControlPanel.add(payloadSize);

        clientControlPanel.add(loadingText);
        clientControlPanel.add(Box.createRigidArea(new Dimension(0,2)));
        clientControlPanel.add(clientProgressBar);

        clientControlPanel.add(submitButton);
        clientControlPanel.add(fileButton);

        clientPanel.add(clientScrollPane);
    }

    private void initServer(){
        serverTextArea = new JTextArea();

        serverTextArea.setColumns(20);
        serverTextArea.setLineWrap(true);
        serverTextArea.setRows(5);
        serverTextArea.setWrapStyleWord(true);
        serverTextArea.setEditable(false);
        JScrollPane serverScrollPane = new JScrollPane(serverTextArea);
        serverScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        ipServerAddressField = new JTextField("",20);
        ipServerAddressField.setText("localhost");
        serverPortNumberInput = new JTextField("",20);
        serverPortNumberInput.setText("4200");

        ipServerAddressField.setActionCommand("IP");
        ipServerAddressField.addActionListener(new InformationListener());

        serverPortNumberInput.setActionCommand("PORT");
        serverPortNumberInput.addActionListener(new InformationListener());

        JButton submitButton = new JButton("Start");
        submitButton.setActionCommand("SubmitServer");
        submitButton.addActionListener(new InformationListener());

        JButton stopButton = new JButton("Stop");
        stopButton.setActionCommand("StopServer");
        stopButton.addActionListener(new InformationListener());

        // Text to display above the text fields
        JLabel ipServerText = new JLabel("IP Address");
        JLabel serverPortText = new JLabel("Port Number");
        serverControlPanel.add(ipServerText);
        serverControlPanel.add(ipServerAddressField);
        serverControlPanel.add(serverPortText);
        serverControlPanel.add(serverPortNumberInput);
        serverControlPanel.add(submitButton);
        serverControlPanel.add(stopButton);
        serverPanel.add(serverScrollPane);
    }

    private class InformationListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            String command = e.getActionCommand();

            switch (command) {
                case "IP":
                    finalIp = ipServerAddressField.getText();
                    break;
                case "PORT":
                    finalPort = serverPortNumberInput.getText();
                    break;
                case "SubmitServer":
                    finalIp = ipServerAddressField.getText();
                    finalPort = serverPortNumberInput.getText();
                    try {
                        // Initialize the server
                        if(serverThread != null) return;

                        serverThread = new Thread(() -> new Server(finalIp, Integer.parseInt(finalPort), serverTextArea));
                        serverThread.start();

                        serverTextArea.append("Server Opened!\n");
                    } catch (Exception ex) {
                        serverTextArea.append("Wrong port Input!");

                        ipServerAddressField.setText("");
                        serverPortNumberInput.setText("");
                    }
                    break;
                case "StopServer":
                    if(serverThread == null) return;

                    serverThread.interrupt();
                    serverThread = null;
                    serverTextArea.append("\nServer Interrupted!");
                    break;
                case "SubmitClient":
                    finalIp = ipClientAddressField.getText();
                    finalPort = clientPortNumberInput.getText();
                    String payload = payloadSize.getText();

                    try {
                        new Thread(() ->
                            new Client(finalIp,
                                    Integer.parseInt(finalPort),
                                    fileName,
                                    filePath,
                                    Integer.parseInt(payload),
                                    clientTextArea,
                                    clientProgressBar))
                            .start();


                        clientTextArea.append("Client Opened!\n");
                    } catch (Exception ex) {
                        clientTextArea.append("Wrong port Input!");

                        ipClientAddressField.setText("");
                        clientPortNumberInput.setText("");
                    }
                    break;
                case "File":
                    System.out.println("Got here");
                    JFileChooser chooser = new JFileChooser();
                    int option = chooser.showOpenDialog(clientPanel);

                    if (option == JFileChooser.APPROVE_OPTION) {
                        File selectedFile = chooser.getSelectedFile();
                        filePath = selectedFile.getParent();
                        fileName = selectedFile.getName();
                        clientTextArea.setText("\nFile Found!");
                    }
                    break;
            }
        }
    }
}
