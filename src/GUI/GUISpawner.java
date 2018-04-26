package GUI;

import Server.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

public class GUISpawner {
    private  JFrame window;
    private JPanel serverPanel;
    private JPanel clientPanel;

    private JLabel serverHeaderLabel;
    private JLabel clientHeaderLabel;

    private JPanel serverControlPanel;
    private JPanel clientControlPanel;

    private JTextArea serverTextArea;
    private JTextArea clientTextArea;

    private JScrollPane serverScrollPane;
    private JScrollPane clientScrollPane;

    // Text to display above the text fields
    private JLabel ipServerText;
    private JLabel ipClientText;
    private JLabel payloadText;

    private JLabel serverPortText;
    private JLabel clientPortText;


    private JButton fileButton;

    // Used for storage of the input
    private JTextField ipServerAddressField;
    private JTextField ipClientAddressField;


    private JTextField serverPortNumberInput;
    private JTextField clientPortNumberInput;

    private JTextField payloadSize;

    private String FilePath;
    private String FileName;

    // The final variables. THEY NEED TO BE CHECKED IF THEY ARE CORRECT!
    private String finalIp = "", finalPort = "";

    public GUISpawner(){
        prepareGUI();
    }

    public static void main(String[] args){
        GUISpawner clientView = new GUISpawner();
        //clientView.initServer();
    }

    private void prepareGUI(){
        window = new JFrame("Server");
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
        clientHeaderLabel = new JLabel("",JLabel.CENTER );
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
        serverHeaderLabel = new JLabel("",JLabel.CENTER );
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
        clientScrollPane = new JScrollPane(clientTextArea);
        clientScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        ipClientAddressField = new JTextField("",20);
        clientPortNumberInput = new JTextField("",20);

        ipClientAddressField.setActionCommand("IPClient");
        ipClientAddressField.addActionListener(new InformationListener());

        clientPortNumberInput.setActionCommand("PORTClient");
        clientPortNumberInput.addActionListener(new InformationListener());

        JButton submitButton = new JButton("Start");
        submitButton.setActionCommand("SubmitClient");
        submitButton.addActionListener(new InformationListener());

        JButton fileButton = new JButton("Search File");
        fileButton.setActionCommand("File");
        fileButton.addActionListener(new InformationListener());

        payloadSize = new JTextField("",20);

        ipClientText = new JLabel("Server IP   ");
        clientPortText = new JLabel("Server Port");
        payloadText = new JLabel("Packet Size");

        //JFileChooser chooser = new JFileChooser();
        clientControlPanel.add(ipClientText);
        clientControlPanel.add(Box.createRigidArea(new Dimension(0,2)));

        clientControlPanel.add(ipClientAddressField);

        clientControlPanel.add(clientPortText);
        clientControlPanel.add(Box.createRigidArea(new Dimension(0,2)));
        clientControlPanel.add(clientPortNumberInput);

        clientControlPanel.add(payloadText);
        clientControlPanel.add(payloadSize);


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
        serverScrollPane = new JScrollPane(serverTextArea);
        serverScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        ipServerAddressField = new JTextField("",20);
        serverPortNumberInput = new JTextField("",20);

        ipServerAddressField.setActionCommand("IP");
        ipServerAddressField.addActionListener(new InformationListener());

        serverPortNumberInput.setActionCommand("PORT");
        serverPortNumberInput.addActionListener(new InformationListener());

        JButton submitButton = new JButton("Start");

        submitButton.setActionCommand("SubmitServer");
        submitButton.addActionListener(new InformationListener());


        ipServerText = new JLabel("IP Address   ");
        serverPortText = new JLabel("Port Number");
        serverControlPanel.add(ipServerText);
        serverControlPanel.add(ipServerAddressField);
        serverControlPanel.add(serverPortText);
        serverControlPanel.add(serverPortNumberInput);
        serverControlPanel.add(submitButton);
        serverPanel.add(serverScrollPane);
    }

    private class InformationListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            String command = e.getActionCommand();

            if( command.equals( "IP" ))  {
                finalIp = ipServerAddressField.getText();
            } else if( command.equals( "PORT" ) )  {
                finalPort = serverPortNumberInput.getText();
            } else if(command.equals("SubmitServer")){
                finalIp = ipServerAddressField.getText();
                finalPort = serverPortNumberInput.getText();
                try{
                    // Initialize the server
                    new Thread(() -> {
                        new Server(finalIp, Integer.parseInt(finalPort));
                    }).start();

                    // try to open the server

                    // Success
                    serverTextArea.append("Server Opened!\n");
                }catch (NumberFormatException exc){
                    // Display the proper exception
                    serverTextArea.append("Wrong port Input!");

                    // Empty the fields
                    ipServerAddressField.setText("");
                    serverPortNumberInput.setText("");
                }
            }else if(command.equals("SubmitClient")){

                finalIp = ipClientAddressField.getText();
                finalPort = clientPortNumberInput.getText();
                String payload = payloadSize.getText();

                try{
                    // Initialize the server
                    new Thread(() -> {
                        new Client("localhost", Integer.parseInt(finalPort), FileName, FilePath, Integer.parseInt(payload));
                    }).start();

                    // try to open the server

                    // Success
                    clientTextArea.append("Client Opened!\n");
                }catch (NumberFormatException exc){
                    // Display the proper exception
                    clientTextArea.append("Wrong port Input!");

                    // Empty the fields
                    ipClientAddressField.setText("");
                    clientPortNumberInput.setText("");
                }
            } else if(command.equals("File")){
                System.out.println("Got here");
                JFileChooser chooser = new JFileChooser();
                int option = chooser.showOpenDialog(clientPanel); // parentComponent must a component like JFrame, JDialog...
                if(option == JFileChooser.APPROVE_OPTION){
                    File selectedFile = chooser.getSelectedFile();
                    FilePath = selectedFile.getParent();
                    FileName = selectedFile.getName();
                    clientTextArea.setText("File Found!");
                }else{

                }
            }
        }
    }
}
