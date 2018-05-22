package Server;

import Models.Packet;
import Utils.NetworkUtils;

import javax.swing.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;

public class Server extends BaseServer{

    private static final int MAX_PACKET_SIZE = 65509;

    /* Define the socket that receives requests */
    private DatagramSocket inServerSocket;

    public ServerClientThread thread;

    public Server(String ip, int port, JTextArea systemOut) {
        super();
        setIp(ip);
        setPort(port);
        setSystemOut(systemOut);

        try {
            inServerSocket = new DatagramSocket(getPort());
            print(getInstanceName() + " with " +
                    getIp() + ":" + getPort() + " opened! Is now listening...");
        } catch (SocketException e) {
            e.printStackTrace();
            print("------------------------------------");
            print("inServerSocket new error");
            print("------------------------------------");
        }

        this.handleListening();
    }

    public void handleListening() {
        // listen until sb tells you otherwise

        byte[] inPacket = new byte[MAX_PACKET_SIZE];
        DatagramPacket inDatagramPacket = new DatagramPacket(inPacket, MAX_PACKET_SIZE);

        boolean keepListening = true;
        while (keepListening) {
            try {
                inServerSocket.setSoTimeout(10000);
                inServerSocket.receive(inDatagramPacket);
                print("******************************");
                print("A Packet Received on default port");
                print("******************************");

            } catch (IOException e) {
                e.printStackTrace();
                print("------------------------------------");
                print("inServerSocket.receive(inDatagramPacket) Error");
                print("------------------------------------");
                continue;
            }

            // In case the packet is corrupt continue to next iteration of the loop
            Packet packetReceived = getPacketIfValid(inDatagramPacket);
            if(packetReceived == null) continue;

            // If the we haven't received the initialize handshake packet from this client
            if(!packetReceived.isNoDataPacket()
                    || (packetReceived.getSequenceNumber() == 1
                        && packetReceived.isNoDataPacket()
                        && !packetReceived.isNotLastPacket() )
                ){
                print("------------------------------------");
                print("Client tried to send data without handshaking first!");
                print("Ignoring this...");
                print("------------------------------------");
                continue;
            }

            try {
                // create a new socket with the same IP but different port for the server
                int newPort = NetworkUtils.GetNextAvailablePort();
                DatagramSocket threadSocket = new DatagramSocket(newPort);

                thread = new ServerClientThread(packetReceived.getSequenceNumber(), threadSocket, newPort,
                        inDatagramPacket.getAddress(), inDatagramPacket.getPort());
                thread.start();
            } catch (SocketException e) {
                e.printStackTrace();
                print("------------------------------------");
                print("new DatagramSocket Error");
                print("------------------------------------");
            }
        }
    }

    /**
     * Gets the Packet out of the raw data of the DatagramPacket param passed
     * @param inDatagramPacket
     * @return Packet that is received if it is valid
     *         null otherwise
     */
    public Packet getPacketIfValid(DatagramPacket inDatagramPacket){
        Packet packetRecieved = new Packet(inDatagramPacket.getData());

        // If Packet is Valid
        if (packetRecieved.isValid(inDatagramPacket.getData())) {
            return packetRecieved;
        }
        // If Packet is Corrupt
        else {
            print("------------------------------------");
            print("Corrupted Packet");
            print("\nCheckSum on Packet: " + packetRecieved.getChecksum());
            print("\nCheckSum on Calculated: " + packetRecieved.caluclateCheckSumFromRawData(inDatagramPacket.getData()));
            print("\nDropping Packet. Ignore receiving and wait for retransmission...");
            print("------------------------------------");
            return null;
        }
    }

    public class ServerClientThread extends Thread {
        private int MAX_PACKET_SIZE_IN_THIS_CONNECTION = 65509;

        private DatagramSocket socket;
        private int inPort;
        private int clientPort;
        private InetAddress clientAddress;
        private byte seqExpecting;

        private int clientId;
        private int counter;
        private boolean handshakeInComplete = true;
        private String fileName = null;
        private File outFile = null;

        boolean recievedCloseACK = false;
        boolean notLastPacket = true;
        boolean firstPacket = true;

        private int timeoutExceedCount = 0;

        DatagramPacket ackPacket;

        /**
         * Constructor
         */
        private ServerClientThread(byte seq, DatagramSocket socket, int newPortIn, InetAddress clientAddress, int clientPort) {
            this.seqExpecting = NetworkUtils.calculateNextSeqNumber(seq);
            this.socket = socket;
            this.inPort = newPortIn;
            this.clientAddress = clientAddress;
            this.clientPort = clientPort;
            this.clientId = ++counter;

            print("******************************");
            print("New Thread Listening No: " + counter);
            print("Listening to port: " + inPort);
            print("For client " + clientAddress.getHostAddress());
            print("");
            print("Received the first step of the 3-Way-Handshake! SEQ = " + seq);

            respondToHandshake();
        }

        private void respondToHandshake() {
            print("Starting the second step of the 3-Way-Handshake! (Send ACK with my new Port server)");
            print("******************************");
            sendSynAckWithNewPort();
        }

        /**
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
            forwardAckPacket(ackMessage, ackPacket);
        }

        /**
          The server sends back to the client an ACK with the SEQ number of the packet he expects next
          ACK messages contain the ACK triple times in order to be able to detect error in transition

          Also the new port of the socket server listening to this client
          Used only for the first ACK send when
        */
        private void sendSynAckWithNewPort(){
            byte[] ackMessage = new byte[1];
            // ACK 0, so 3 times 0 is int 0
            // ACK 1, so 3 times 1 is int 7
            ackMessage[0] = seqExpecting == (byte) (0) ? (byte) 0 : (byte) 7;

            ByteBuffer ackWithNewPort = ByteBuffer.allocate(5);
            byte[] newPortByteArray = ByteBuffer.allocate(4).putInt(inPort).array();
            ackWithNewPort.put(ackMessage);
            ackWithNewPort.put(newPortByteArray);

            byte[] message = ackWithNewPort.array();

            ackPacket = new DatagramPacket(message, message.length, clientAddress, clientPort);
            forwardAckPacket(ackMessage, ackPacket);
        }

        private void forwardAckPacket(byte[] ackMessage, DatagramPacket ackPacket) {
            try {
                socket.send(ackPacket);
                print("******************************");
                print("Sent ACK = " + (ackMessage[0] == (byte)0 ? "0" : "1"));
                print("******************************");
            } catch (IOException e) {
                e.printStackTrace();
                e.printStackTrace();
                print("------------------------------------");
                print("new ACK packet Error");
                print("------------------------------------");
            }
        }

        private void handshakeInComplete() {
            while (handshakeInComplete) {
                byte[] inPacket = new byte[MAX_PACKET_SIZE_IN_THIS_CONNECTION];
                DatagramPacket inDatagramPacket = new DatagramPacket(inPacket, MAX_PACKET_SIZE_IN_THIS_CONNECTION);

                try {
                    socket.setSoTimeout(10000);
                } catch (SocketException e) {
                    print("------------------------------------");
                    print("Sending SYN-ACK Again");
                    print("------------------------------------");

                    if(timeoutExceedCount > 2) {
                        print("Unable to establish 3-hand-shake! Closing thread communication with client...");
                        return;
                    }

                    timeoutExceedCount++;
                    sendSynAckWithNewPort();
                    continue;
                }

                try {
                    print(socket.getLocalPort());
                    socket.receive(inDatagramPacket);
                    print("******************************");
                    print("A Packet Received by client no " + clientId);
                    print("******************************");
                } catch (IOException e) {
                    print("------------------------------------");
                    print("packetSocket.receive() Error");
                    print("------------------------------------");
                    continue;
                }

                // In case the packet is CORRUPT continue to next iteration of the loop
                Packet packetReceived = getPacketIfValid(inDatagramPacket);
                if (packetReceived == null) {
                    print("CORRUPTED");
                    continue;
                }

                if(!packetReceived.isNoDataPacket()) {
                    print("------------------------------------");
                    print("Client with no: " +clientId+ " started sending data before connection established!");
                    print("Handshake failed! Closing Conn...");
                    print("------------------------------------");
                    timeoutExceedCount = 3;
                    return;
                }

                print("Packet Seq == " + packetReceived.getSequenceNumber());
                print("Seq Expecting == " + seqExpecting);

                // If it is RETRANSMISSION continue to next iteration of the loop after sending duplicate ACK
                if (packetReceived.getSequenceNumber() != seqExpecting) {
                    print("The retransmitted packet has been dropped");
                    print("Send Duplicate ACK");
                    print("******************************");
                    print("******************************");
                    sendSynAckWithNewPort();
                    continue;
                }

                // If it is the Client's ACK -> 3rd pard of the 3-Way-Handshake
                // simple update the handshake state and the seq number expecting

                print("Received Client's ACK, the 3rd part of the 3-Way-Handshake");
                print("No Ack Sending. Simply waiting the file transfer...");
                print("3-Way-Handshake Completed!!!");
                print("******************************");
                print("");
                handshakeInComplete = false;

                seqExpecting = 0;
                print("ResetSeqNumber " + seqExpecting);
            }
        }

        /**
           If the packet received is valid and accepted one returns the packet
           Otherwise sends the corresponding ACK if needed and returns null;
         */
        private Packet listenSocket(){
            byte[] inPacket = new byte[MAX_PACKET_SIZE_IN_THIS_CONNECTION];
            print("WHILE LISTEN SERVER");
            DatagramPacket inDatagramPacket = new DatagramPacket(inPacket, MAX_PACKET_SIZE_IN_THIS_CONNECTION);

            try {
                print(socket.getLocalPort());
                socket.receive(inDatagramPacket);
                print("******************************");
                print("A Packet Received by client no: " + clientId);
                print("******************************");
            } catch (IOException e) {
                e.printStackTrace();
                print("------------------------------------");
                print("packetSocket.receive(inDatagramPacket) Error");
                print("------------------------------------");
                return null;
            }

            // In case the packet is CORRUPT continue to next iteration of the loop
            Packet packetReceived = getPacketIfValid(inDatagramPacket);
            if (packetReceived == null) {
                print("CORRUPTED");
                return null;
            }

            if(!notLastPacket){
                if(packetReceived.getSequenceNumber() == 1 &&
                        packetReceived.isNoDataPacket() &&
                        !packetReceived.isNotLastPacket()){
                    recievedCloseACK = true;
                }else{
                    sendACK();
                }
                return null;
            }

            print("Packet Seq == " + packetReceived.getSequenceNumber());
            print("Seq Expecting == " + seqExpecting);

            // If it is RETRANSMISSION continue to next iteration of the loop after sending duplicate ACK
            if (packetReceived.getSequenceNumber() != seqExpecting) {
                print("The retransmitted packet has been dropped");
                print("Send Duplicate ACK");
                print("******************************");
                print("******************************");
                print("");
                sendACK();
                return null;
            }
            return packetReceived;
        }

        /**
          Handles the accepted packet
          Either creates the file - first packet received
          Either appends data to this file - rest packets
        */
        private void handleAcceptedPacket(Packet packetReceived){
            /*
              If the packet is the first packet received after the Handshake completion
              it is transferring only the name of the file
            */
            if (firstPacket) {
                fileName = new String(packetReceived.getData());
                print("Received the FileName: " + fileName);
                print("******************************");
                print("");
                seqExpecting = NetworkUtils.calculateNextSeqNumber(seqExpecting);
                sendACK();
                firstPacket = false;

                //region CREATE FILE

                outFile = new File(fileName);
                try {
                    if (outFile.exists()) {
                        outFile.delete();
                    }
                    outFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                //endregion

            }
            else {
                // Change the max Payload the first time he finally learns about the max payload
                if(MAX_PACKET_SIZE_IN_THIS_CONNECTION == MAX_PACKET_SIZE)
                    MAX_PACKET_SIZE_IN_THIS_CONNECTION = packetReceived.getPayloadSize() + Packet.HEADER_SIZE + Packet.CHECKSUM_SIZE;

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
                        print("ERROR FILE WRITE");
                        e.printStackTrace();
                    }
                    try {
                        saltOutFile.close();
                    } catch (IOException e) {
                        print("ERROR TRYING TO CLOSE THE FILE");
                        e.printStackTrace();
                    }
                }

                //endregion

                seqExpecting = NetworkUtils.calculateNextSeqNumber(seqExpecting);
                sendACK();

                if (packetReceived.isNotLastPacket()) {
                    print("Write file data to file");
                    print("******************************");
                    print("");
                } else {
                    print("File Transfer Completed!");
                    print("******************************");
                    print("");
                    notLastPacket = false;
                }
            }
        }

        @Override
        public void run() {
            print("Got in run");
            handshakeInComplete();

            if(timeoutExceedCount >= 3){
                print("Closing Communication with client with error!");
                socket.close();
                return;
            }

            while (!recievedCloseACK) {
                Packet packetReceived = listenSocket();
                if(packetReceived == null) continue;

                handleAcceptedPacket(packetReceived);
            }

            print("Closing Communication with client: " + counter + "...");
            socket.close();
            interrupt();
        }
    }

}

