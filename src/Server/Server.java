package Server;

import Models.Packet;
import Utils.NetworkUtils;

import java.io.IOException;
import java.net.*;

public class Server extends BaseServer{

    private static final int MAX_PACKET_SIZE = 65509;
    private static int timeOut = 300;

    /* Define the socket that receives requests */
    protected DatagramSocket inServerSocket;

    private boolean keepListening = true;

    public Server(String ip, int port) {
        super();
        setIp(ip);
        setPort(port);
        try {
            inServerSocket = new DatagramSocket(getPort());
            System.out.println(getInstanceName() + " with " +
                    getIp() + ":" + getPort() + " opened! Is now listening...");
        } catch (SocketException e) {
            e.printStackTrace();
            System.out.println("------------------------------------");
            System.out.println("inServerSocket new error");
            System.out.println("------------------------------------");
        }
        this.handleListening();
    }

    public void handleListening() {
        // listen until sb tells you otherwise

        byte[] inPacket = new byte[MAX_PACKET_SIZE];
        DatagramPacket inDatagramPacket = new DatagramPacket(inPacket, MAX_PACKET_SIZE);

        while (keepListening) {
            try {
                inServerSocket.receive(inDatagramPacket);
                System.out.println("******************************");
                System.out.println("A Packet Received on default port");
                System.out.println("******************************");

            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("------------------------------------");
                System.out.println("inServerSocket.receive(inDatagramPacket) Error");
                System.out.println("------------------------------------");
                continue;
            }

            // In case the packet is corrupt continue to next iteration of the loop
            Packet packetReceived = handleReceivingPacket(inDatagramPacket);
            if(packetReceived == null) continue;

            try {

                // create a new socket with the same IP but different port for the server
                DatagramSocket threadSocket = new DatagramSocket(NetworkUtils.GetNextAvailablePort());

                new ServerClientThread(packetReceived.getSequenceNumber(), threadSocket,
                        inDatagramPacket.getAddress(), inDatagramPacket.getPort())
                        .start();
            } catch (SocketException e) {
                e.printStackTrace();
                System.out.println("------------------------------------");
                System.out.println("new DatagramSocket Error");
                System.out.println("------------------------------------");
                continue;
            }

        }
    }

    /**
     * Gets the Packet out of the raw data of the DatagramPacket param passed
     * @param inDatagramPacket
     * @return Packet that is received if it is valid
     *         null otherwise
     */
    public Packet handleReceivingPacket(DatagramPacket inDatagramPacket){
        Packet packetRecieved = new Packet(inDatagramPacket.getData());

        // If Packet is Valid
        if (packetRecieved.isValid(inDatagramPacket.getData())) {
            return packetRecieved;
        }
        // If Packet is Corrupt
        else {
            System.out.println("------------------------------------");
            System.out.println("Corrupted Packet");
            System.out.println("\nCheckSum on Packet: " + packetRecieved.getChecksum());
            System.out.println("\nCheckSum on Calculated: " + packetRecieved.caluclateCheckSumFromRawData(inDatagramPacket.getData()));
            System.out.println("\nDropping Packet. Ignore receiving and wait for retransmission...");
            System.out.println("------------------------------------");

            return null;
        }
    }

    public class ServerClientThread extends Thread {
        private DatagramSocket socket;
        private int clientPort;
        private InetAddress clientAddress;
        private byte seqExpecting;

        private int clientId;
        private int counter = 0;
        private int port;
        private boolean handshakeInComplete = true;
        private String fileName = null;

        private boolean listenForMore = true;

        // ServerClientThread constructor
        public ServerClientThread(byte seq, DatagramSocket socket, InetAddress clientAddress, int clientPort) {
            this.seqExpecting = NetworkUtils.calculateNextSeqNumber(seq);
            this.socket = socket;
            this.clientAddress = clientAddress;
            this.clientPort = clientPort;
            this.clientId = ++counter;
            this.port = socket.getPort();

            System.out.println("******************************");
            System.out.println("New Thread Listening No: " + counter);
            System.out.println("For client " + clientAddress.getHostAddress());
            System.out.println("");
            System.out.println("Received the first step of the 3-Way-Handshake! SEQ = " + seq);
            SecondPartHandshake();
        }
        DatagramPacket ackPacket;

        public void SecondPartHandshake(){
            System.out.println("Starting the second step of the 3-Way-Handshake! (Send ACK)");
            System.out.println("******************************");

            byte[] ackMessage = new byte[3];
            for(int i = 0; i < ackMessage.length; i++){
                ackMessage[i] = seqExpecting;
            }

            ackPacket = new DatagramPacket(ackMessage, ackMessage.length, clientAddress, clientPort);
            try {
                socket.send(ackPacket);
                System.out.println("******************************");
                System.out.println("Sent ACK = " + ackMessage[0] + "\t" + ackMessage[1] + "\t" + ackMessage[2]);
                System.out.println("******************************");
            } catch (IOException e) {
                e.printStackTrace();
                e.printStackTrace();
                System.out.println("------------------------------------");
                System.out.println("new ACK packet Error");
                System.out.println("------------------------------------");
            }
        }




        /*
            The server sends back to the client an ACK with the SEQ number of the packet he expects next
            ACK messages contain the ACK triple times in order to be able to detect error in transition
        */
        private void sendACK(){
            byte[] ackMessage = new byte[3];
            for(int i = 0; i < ackMessage.length; i++){
                ackMessage[i] = seqExpecting;
            }

            DatagramPacket ackPacket = new DatagramPacket(ackMessage, ackMessage.length, clientAddress, clientPort);
            try {
                socket.send(ackPacket);
                System.out.println("******************************");
                System.out.println("Sent ACK = " + ackMessage[0] + "\t" + ackMessage[1] + "\t" + ackMessage[2]);
                System.out.println("******************************");
            } catch (IOException e) {
                e.printStackTrace();
                e.printStackTrace();
                System.out.println("------------------------------------");
                System.out.println("new ACK packet Error");
                System.out.println("------------------------------------");
            }
        }

        @Override
        public void run() {
            System.out.println("Got in run");
            while(listenForMore){
                byte[] inPacket = new byte[MAX_PACKET_SIZE];
                System.out.println("WHILE LISTEN SERVER");
                DatagramPacket inDatagramPacket = new DatagramPacket(inPacket, MAX_PACKET_SIZE);

                try {
                    System.out.println(socket.getLocalPort());
                    socket.receive(inDatagramPacket);
                    System.out.println("******************************");
                    System.out.println("A Packet Received by client no: " + clientId);
                    System.out.println("******************************");
                } catch (IOException e) {
                    e.printStackTrace();
                    System.out.println("------------------------------------");
                    System.out.println("packetSocket.receive(inDatagramPacket) Error");
                    System.out.println("------------------------------------");
                    continue;
                }

                // In case the packet is CORRUPT continue to next iteration of the loop
                Packet packetReceived = handleReceivingPacket(inDatagramPacket);
                if(packetReceived == null) continue;
                System.out.println("Packet Seq == " + packetReceived.getSequenceNumber());
                System.out.println("Seq Expecting == " + seqExpecting);

                // If it is RETRANSMISSION continue to next iteration of the loop after sending duplicate ACK
                if(packetReceived.getSequenceNumber() != seqExpecting){
                    System.out.println("The retransmitted packet has been dropped");
                    System.out.println("Send Duplicate ACK");
                    System.out.println("******************************");
                    System.out.println("******************************");
                    System.out.println("");
                    sendACK();
                    continue;
                }

                // If it is the Client's ACK -> 3rd pard of the 3-Way-Handshake
                // simple update the handshake state and the seq number expecting
                if(handshakeInComplete) {
                    System.out.println("Received Client's ACK, the 3rd part of the 3-Way-Handshake");
                    System.out.println("No Ack Sending. Simply waiting the file transfer...");
                    System.out.println("3-Way-Handshake Completed!!!");
                    System.out.println("******************************");
                    System.out.println("");
                    handshakeInComplete = false;

                    seqExpecting = 0;
                    System.out.println("ResetSeqNumber " + seqExpecting);
                    continue;
                }

                // If the packet is the first packet received after the Handshake completion
                // it is transferring only the name of the file
                if(fileName == null){
                    fileName = new String(packetReceived.getData());
                    System.out.println("Received the FileName: " + fileName);
                    System.out.println("******************************");
                    System.out.println("");
                    seqExpecting = NetworkUtils.calculateNextSeqNumber(seqExpecting);
                    sendACK();
                    continue;

                    //region CREATE FILE TODO
                    /*File file = new File(fileName);
                    filesIncomplete.put(clientAddress, file);
                    if (!file.exists()) {
                        try {
                            file.createNewFile();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    try {
                        fileOutputStream = new FileOutputStream(file);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }

                    try {
                        fileOutputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    //TODO MAKE SURE NO MORE INFO NEEDED TO WRITE IN
                    System.out.println("Server: Received first packet informing about the file name" +
                            "from " + clientAddress.getHostAddress() +
                            "\n\t FileName:" + fileName +
                            "\n\t Sedning ACK..." +
                            "\n\t Waiting for the file transfer...");
                    lastSeqNumberReceived.put(clientAddress, packet.getSequenceNumber());
                    sendACK(clientAddress, port); */
                    //endregion
                }

                // If the packet simple data of the file being transferred
                //region WRITE PACKET DATA TO FILE TODO
                /*try {
                    fileOutputStream = new FileOutputStream(filesIncomplete.get(clientAddress));
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }

                try {
                    fileOutputStream.write(packet.getData());
                    fileOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("SERVER: New Packet Received from "
                        + clientAddress.getHostAddress() +
                        "\n\t Sending ACK...");
                lastSeqNumberReceived.put(clientAddress, packet.getSequenceNumber());*/
                //endregion
                seqExpecting = NetworkUtils.calculateNextSeqNumber(seqExpecting);
                sendACK();

                if(packetReceived.isNotLastPacket()){
                    System.out.println("Write file data to file");
                    System.out.println("******************************");
                    System.out.println("");
                }
                else{
                    listenForMore = false;
                    System.out.println("File Transfer Completed!");
                    System.out.println("******************************");
                    System.out.println("");
                }
            }

            System.out.println("Closing Communication with client: " + counter + "...");
            socket.close();
        }
    }

}

