package Server;

import Models.Packet;
import Utils.NetworkUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.HashMap;

public class Server extends BaseServer{

    private static final int MAX_PACKET_SIZE = 65509;
    private static int timeOut = 300;

    private static HashMap<InetAddress, Integer> handshakeStarted = new HashMap<>();

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

            // If the we havent received the initialize handshake packet from this client
            if(packetReceived.getFlag() != Packet.NO_DATA_FILE) {
                handshakeStarted.put(packetReceived.getAddress(), 1);
                System.out.println("------------------------------------");
                System.out.println("Client tried to send data without handshaking first!");
                System.out.println("Ignoring this...");
                System.out.println("------------------------------------");
                continue;
            }

            try {

                // create a new socket with the same IP but different port for the server
                int newPort = NetworkUtils.GetNextAvailablePort();
                DatagramSocket threadSocket = new DatagramSocket(newPort);

                new ServerClientThread(packetReceived.getSequenceNumber(), threadSocket, newPort,
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
    private final static byte SIGNAL_SERVER_TERMINATION = 3;
    public class ServerClientThread extends Thread {
        private int MAX_PACKET_SIZE_IN_THIS_CONNECTION = 65509;

        private DatagramSocket socket;
        private int inPort;
        private int clientPort;
        private InetAddress clientAddress;
        private byte seqExpecting;

        private int clientId;
        private int counter = 0;
        private int port;
        private boolean handshakeInComplete = true;
        private String fileName = null;
        private File outFile = null;

        boolean notLastPacket = true;
        boolean firstPacket = true;

        // ServerClientThread constructor
        public ServerClientThread(byte seq, DatagramSocket socket, int newPortIn, InetAddress clientAddress, int clientPort) {
            this.seqExpecting = NetworkUtils.calculateNextSeqNumber(seq);
            this.socket = socket;
            this.inPort = newPortIn;
            this.clientAddress = clientAddress;
            this.clientPort = clientPort;
            this.clientId = ++counter;
            this.port = socket.getPort();

            System.out.println("******************************");
            System.out.println("New Thread Listening No: " + counter);
            System.out.println("Listening to port: " + inPort);
            System.out.println("For client " + clientAddress.getHostAddress());
            System.out.println("");
            System.out.println("Received the first step of the 3-Way-Handshake! SEQ = " + seq);
            SecondPartHandshake();
        }

        DatagramPacket ackPacket;

        public void SecondPartHandshake() {
            System.out.println("Starting the second step of the 3-Way-Handshake! (Send ACK with my new Port server)");
            System.out.println("******************************");
            sendSynAckWithNewPort();
        }


        /*
            The server sends back to the client an ACK with the SEQ number of the packet he expects next
            ACK messages contain the ACK triple times in order to be able to detect error in transition
        */
        private void sendACK() {
            byte[] ackMessage = new byte[1];
            // ACK 0, so 3 times 0 is int 0
            if(seqExpecting == (byte)(0)) {
                ackMessage[0] = (byte)0;
            }
            // ACK 1, so 3 times 1 is int 7
            else {
                ackMessage[0] = (byte)7;
            }

            DatagramPacket ackPacket = new DatagramPacket(ackMessage, ackMessage.length, clientAddress, clientPort);
            try {
                socket.send(ackPacket);
                System.out.println("******************************");
                System.out.println("Sent ACK = " + (ackMessage[0] == (byte)0 ? "0" : "1"));
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

          Also the new port of the socket server listening to this client
          Used only for the first ACK send when
        */
        private void sendSynAckWithNewPort(){
            byte[] ackMessage = new byte[1];
            // ACK 0, so 3 times 0 is int 0
            if(seqExpecting == (byte)(0)) {
                ackMessage[0] = (byte)0;
            }
            // ACK 1, so 3 times 1 is int 7
            else {
                ackMessage[0] = (byte)7;
            }

            ByteBuffer ackWithNewPort = ByteBuffer.allocate(5);
            byte[] newPortByteArray = ByteBuffer.allocate(4).putInt(inPort).array();
            ackWithNewPort.put(ackMessage);
            ackWithNewPort.put(newPortByteArray);

            byte[] message = ackWithNewPort.array();

            ackPacket = new DatagramPacket(message, message.length, clientAddress, clientPort);
            try {
                socket.send(ackPacket);
                System.out.println("******************************");
                System.out.println("Sent ACK = " + (ackMessage[0] == (byte)0 ? "0" : "1"));
                System.out.println("******************************");
            } catch (IOException e) {
                e.printStackTrace();
                e.printStackTrace();
                System.out.println("------------------------------------");
                System.out.println("new ACK packet Error");
                System.out.println("------------------------------------");
            }
        }

        private void handshakeInComplete() {
            while (handshakeInComplete) {
                byte[] inPacket = new byte[MAX_PACKET_SIZE_IN_THIS_CONNECTION];
                DatagramPacket inDatagramPacket = new DatagramPacket(inPacket, MAX_PACKET_SIZE_IN_THIS_CONNECTION);

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
                if (packetReceived == null) {
                    System.out.println("CORRUPTED");
                    continue;
                }

                if(packetReceived.getFlag() != Packet.NO_DATA_FILE) {
                    System.out.println("------------------------------------");
                    System.out.println("Client with no: " +clientId+ "started sending data before connection established!");
                    System.out.println("Handshake failed! Closing this...");
                    System.out.println("------------------------------------");
                    //TODO CLOSE WHEN THIS HAPPEN OR TIMEOUT HAPPEN
                }

                System.out.println("Packet Seq == " + packetReceived.getSequenceNumber());
                System.out.println("Seq Expecting == " + seqExpecting);

                // If it is RETRANSMISSION continue to next iteration of the loop after sending duplicate ACK
                if (packetReceived.getSequenceNumber() != seqExpecting) {
                    System.out.println("The retransmitted packet has been dropped");
                    System.out.println("Send Duplicate ACK");
                    System.out.println("******************************");
                    System.out.println("******************************");
                    System.out.println("");
                    sendSynAckWithNewPort();
                    continue;
                }

                // If it is the Client's ACK -> 3rd pard of the 3-Way-Handshake
                // simple update the handshake state and the seq number expecting

                System.out.println("Received Client's ACK, the 3rd part of the 3-Way-Handshake");
                System.out.println("No Ack Sending. Simply waiting the file transfer...");
                System.out.println("3-Way-Handshake Completed!!!");
                System.out.println("******************************");
                System.out.println("");
                handshakeInComplete = false;

                seqExpecting = 0;
                System.out.println("ResetSeqNumber " + seqExpecting);
            }
        }

        // If the packet received is valid and accepted one returns the packet
        // Otherwise sends the corresponding ACK if needed and returns null;
        private Packet listenSocket(){
            byte[] inPacket = new byte[MAX_PACKET_SIZE_IN_THIS_CONNECTION];
            System.out.println("WHILE LISTEN SERVER");
            DatagramPacket inDatagramPacket = new DatagramPacket(inPacket, MAX_PACKET_SIZE_IN_THIS_CONNECTION);

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
                return null;
            }

            // In case the packet is CORRUPT continue to next iteration of the loop
            Packet packetReceived = handleReceivingPacket(inDatagramPacket);
            if (packetReceived == null) {
                System.out.println("CORRUPTED");
                return null;
            }
            System.out.println("Packet Seq == " + packetReceived.getSequenceNumber());
            System.out.println("Seq Expecting == " + seqExpecting);

            // If it is RETRANSMISSION continue to next iteration of the loop after sending duplicate ACK
            if (packetReceived.getSequenceNumber() != seqExpecting) {
                System.out.println("The retransmitted packet has been dropped");
                System.out.println("Send Duplicate ACK");
                System.out.println("******************************");
                System.out.println("******************************");
                System.out.println("");
                sendACK();
                return null;
            }
            return packetReceived;
        }

        // Handles the accepted packet
        // Either creates the file - first packet received
        // Either appends data to this file - rest packets
        private void handleAcceptedPacket(Packet packetReceived){
            // If the packet is the first packet received after the Handshake completion
            // it is transferring only the name of the file
            if (firstPacket) {
                fileName = new String(packetReceived.getData());
                System.out.println("Received the FileName: " + fileName);
                System.out.println("******************************");
                System.out.println("");
                seqExpecting = NetworkUtils.calculateNextSeqNumber(seqExpecting);
                sendACK();
                firstPacket = false;

                //region CREATE FILE
                outFile = new File(fileName);
                if (!outFile.exists()) {
                    try {
                        outFile.createNewFile();
                    } catch (IOException e) {
                        e.printStackTrace();
                        return;
                    }
                }
                //endregion
            } else {
                // Change the max Payload the first time he finally learns about the max payload
                if(MAX_PACKET_SIZE_IN_THIS_CONNECTION == MAX_PACKET_SIZE_IN_THIS_CONNECTION)
                    MAX_PACKET_SIZE_IN_THIS_CONNECTION = packetReceived.getPayloadSize() + Packet.HEADER_SIZE + Packet.CHECKSUM_SIZE;

                // If the packet simple data of the file being transferred
                //region WRITE PACKET DATA TO FILE
                if (outFile != null) {
                    FileOutputStream saltOutFile = null;
                    try {
                        saltOutFile = new FileOutputStream(outFile, true);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    try {
                        saltOutFile.write(packetReceived.getData());
                    } catch (IOException e) {
                        System.out.println("ERROR FILE WRITE");
                        e.printStackTrace();
                    }
                    try {
                        saltOutFile.close();
                    } catch (IOException e) {
                        System.out.println("ERROR TRYING TO CLOSE THE FILE");
                        e.printStackTrace();
                    }
                }
                //endregion
                seqExpecting = NetworkUtils.calculateNextSeqNumber(seqExpecting);
                sendACK();

                if (packetReceived.isNotLastPacket()) {
                    System.out.println("Write file data to file");
                    System.out.println("******************************");
                    System.out.println("");
                } else {
                    System.out.println("File Transfer Completed!");
                    System.out.println("******************************");
                    System.out.println("");
                    notLastPacket = false;
                }
            }
        }

        @Override
        public void run() {
            System.out.println("Got in run");
            handshakeInComplete();
            while (notLastPacket) {
                Packet packetReceived = listenSocket();
                if(packetReceived == null) continue;

                handleAcceptedPacket(packetReceived);
            }

            // Make sure removing him from the handshake list in order to have to handshake again if he wants to send another file
            handshakeStarted.remove(clientAddress);

            System.out.println("Closing Communication with client: " + counter + "...");
            socket.close();
        }
    }

}

