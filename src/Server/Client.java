package Server;

import Models.Packet;
import Utils.NetworkUtils;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.stream.IntStream;

public class Client extends BaseServer{

    // Parallel
    private HashMap<Integer, byte[]> payloadsToSent;

    private byte lastPacketSeq = 0;

    private String serverIp;
    private int serverPort;
    private String filename;
    private String fileDir;
    private int payloadSize;
    private boolean gotNewServerPort = false;

    private boolean handshakeIncomplete = true;
    private boolean noValidAckReceived = true;

    // in
    private DatagramSocket ackSocket;
    // out
    private DatagramSocket packetSocket;

    public Client(String serverIp, int serverPort, String filename, String fileDir, int payloadSize){
        super();
        setServerIp(serverIp);
        setServerPort(serverPort);
        setFilename(filename);
        setFileDir(fileDir);
        setPayloadSize(payloadSize);
        setPort(NetworkUtils.GetNextAvailablePort());
        setIp(NetworkUtils.GetCurrentAddress());

        payloadsToSent = new HashMap<>();

        File file = new File(fileDir + "\\" + filename);
        try {
            // Loading file into byte array
            InputStream inputStream = null;
            try {
                inputStream = new FileInputStream(file);
            } catch (FileNotFoundException e) {
                System.out.println("ERROR FILE INPUT STREAM");
                e.printStackTrace();
            }
            byte[] fileByteArray = new byte[(int) file.length()];
            inputStream.read(fileByteArray);

            // Split byte file array of file's data into payloads
            splitToPackets(fileByteArray);

            try {
                packetSocket = new DatagramSocket(getPort(), getInetServerAddress());
                System.out.println("Opened server socket! Is now able to send packets");
            } catch (SocketException e) {
                e.printStackTrace();
                System.out.println("------------------------------------");
                System.out.println("packetSocket = new DatagramSocket(serverPort, InetAddress.getByName(getServerIp()) error");
                System.out.println("Re-open Client and Try Again!");
                System.out.println("------------------------------------");
            } catch (UnknownHostException e) {
                e.printStackTrace();
                System.out.println("------------------------------------");
                System.out.println("Unknown Hoest Error");
                System.out.println("Re-open Client and Try Again!");
                System.out.println("------------------------------------");

            }
            handShake();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void splitToPackets(byte[] bytes){
        int fullSteps = bytes.length / payloadSize;

        //region [Add Filename Packet]

        byte[] filenameBytes = filename.getBytes();

        payloadsToSent.put(-1, filenameBytes);

        //endregion
        for(int i = 0; i <= fullSteps; i++){
            int startIndex = i * payloadSize;
            int bytesLength = payloadSize;
            if (i == fullSteps){
                int lastLength = bytes.length - startIndex;
                if(lastLength <= 0) return;
                bytesLength = lastLength;
            }

            byte[] payload = new byte[bytesLength];
            IntStream.range(0, bytesLength)
                    .parallel()
                    .forEach(k -> payload[k] = bytes[startIndex + k]);

            payloadsToSent.put(i, payload);
        }
    }

    private void handShake(){
        // Send dummy package to initialize handshake, contains only a SEQ number
        byte[] dummyPacket = new byte[payloadSize + Packet.HEADER_SIZE + Packet.CHECKSUM_SIZE];

        dummyPacket[Packet.SEQ_NUM_INDEX] = lastPacketSeq;
        dummyPacket[Packet.FLAG_INDEX] = Packet.NO_DATA_FILE;

        for(int i = 2; i < Packet.PAYLOAD_START_INDEX; i++){
            dummyPacket[i] = 0;
        }

        System.out.println("******************************");
        System.out.println("Client about to create start handshake");
        System.out.println("Client " + NetworkUtils.GetCurrentAddress());
        System.out.println("Sending dummy data");
        System.out.println("Starting the first step of the 3-Way-Handshake!");
        System.out.println("******************************");

        DatagramPacket packetToSent;
        try {
            packetToSent = new DatagramPacket(dummyPacket, dummyPacket.length, getInetServerAddress(), serverPort);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            System.out.println("------------------------------------");
            System.out.println("Unknown Host Error");
            System.out.println("Re-open Client and Try Again!");
            System.out.println("------------------------------------");
            return;
        }

        boolean reSend = true;
        while (reSend){
            int result = sendPacket(packetToSent);

            //Sending Client ACK
            if(result == SEND_NEXT){
                System.out.println("Received server's ACK of the 3-Way-Handshake!");
                dummyPacket[0] = lastPacketSeq;
                try {
                    packetToSent = new DatagramPacket(dummyPacket, dummyPacket.length, getInetServerAddress(), serverPort);
                    sendPacketNoListen(packetToSent);
                    System.out.println("Sent my ACK back 3-Way-Handshake!");
                    // reset SEQ
                    lastPacketSeq = -1;
                    reSend = false;
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
            }
        }
        System.out.println("Starting Sending The File!");
        long timeStarted = System.currentTimeMillis();

        if(lastPacketSeq == -1) lastPacketSeq = 0;
        for(int key : payloadsToSent.keySet()){
            try {
                System.out.println("current key " + key);
                DatagramPacket filePartToSend = createPacket(key);

                int result = sendPacket(filePartToSend);
                while (result != SEND_NEXT){
                    filePartToSend = createPacket(key);
                    result = sendPacket(filePartToSend);
                }
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("------------------------------------");
                System.out.println("Failed to Send Packet with Key: " + key);
                System.out.println("------------------------------------");
            }
        }

        long startTime = System.nanoTime();

        System.out.println("");
        System.out.println("******************************");
        System.out.println("File Transfer has been Completed! ");
        System.out.println("Send server multi times the last ACK to inform that I received that he got the file");

        for(int i = 0; i<10; i++){
            dummyPacket[0] = SIGNAL_SERVER_TERMINATION;
            try {
                packetToSent = new DatagramPacket(dummyPacket, dummyPacket.length, getInetServerAddress(), serverPort);
                sendPacketNoListen(packetToSent);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }

        long estimatedTime = System.nanoTime() - startTime;

        System.out.println("");
        System.out.println("Total Transfer Time:\t" + (estimatedTime/1000000) + " ms");
        System.out.println("Transfer Speed:\t");
        System.out.println("Total Packets Used:\t");

        System.out.println("My job has been done! Time to close");
        System.out.println("******************************");
    }

    private final static byte SIGNAL_SERVER_TERMINATION = 3;
    private final static Integer UNABLE_TO_RESUME = -1;
    private final static Integer RETRANSMIT = 1;
    private final static Integer SEND_NEXT = 2;

    private Integer sendPacketNoListen(DatagramPacket packetToSent){
        // sending the packet
        try {
            packetSocket.send(packetToSent);
            System.out.println("******************************");
            System.out.println("Sent Packet Seq = " + lastPacketSeq);
            System.out.println("To: "+this.serverIp);
            System.out.println("Port: "+serverPort);
            System.out.println("******************************");
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("------------------------------------");
            System.out.println("Error trying to send packet Seq = " + lastPacketSeq);
            System.out.println("Reboot System Please!");
            System.out.println("------------------------------------");
            return UNABLE_TO_RESUME;
        }
        return SEND_NEXT;
    }

    private Integer sendPacket(DatagramPacket packetToSent){
        noValidAckReceived = true;
        while(noValidAckReceived){
            // sending the packet
            try {
                packetSocket.send(packetToSent);
                System.out.println("******************************");
                System.out.println("Sent Packet Seq = " + lastPacketSeq);
                System.out.println("To: "+this.serverIp);
                System.out.println("Port: "+serverPort);
                System.out.println("******************************");
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("------------------------------------");
                System.out.println("Error trying to send packet Seq = " + lastPacketSeq);
                System.out.println("Reboot System Please!");
                System.out.println("------------------------------------");
                return UNABLE_TO_RESUME;
            }

            int sizeOfReceived = 1;
            if(handshakeIncomplete)  sizeOfReceived = 5;
            // packet sent, waiting for ACK
            byte[] ackReceived = new byte[sizeOfReceived];
            DatagramPacket ackReceivedPacket = new DatagramPacket(ackReceived, ackReceived.length);
            try {
                try {
                    packetSocket.setSoTimeout(10000);
                    //System.out.println("Sent timeout");
                } catch (SocketException e) {
                    e.printStackTrace();
                    System.out.println("------------------------------------");
                    System.out.println("Timeout! Retransmit packet Seq = " + lastPacketSeq);
                    System.out.println("------------------------------------");
                    return RETRANSMIT;
                }
                // ACK received
                packetSocket.receive(ackReceivedPacket);
                ackReceived = ackReceivedPacket.getData();

                System.out.println("******************************");
                System.out.println("A ACK Received by Server");
                System.out.println("ACK = " + (ackReceived[0] == (byte)0 ? "0" : "1"));
                System.out.println("Last Packet Sent from Client, Seq = " + lastPacketSeq);
                System.out.println("New Server Port Is: "+serverPort);
                System.out.println("******************************");

            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("------------------------------------");
                System.out.println("packetSocket.receive(inDatagramPacket) Error");
                System.out.println("------------------------------------");
                continue;
            }

            byte[] acks = new byte[1];
            if(!gotNewServerPort) {

                byte[] serverNewPort = new byte[5];
                int j = 0;
                for(int i=0; i<ackReceived.length; i++){
                    if(i == 0) acks[i] = ackReceived[i];
                    else {
                        serverNewPort[j] = ackReceived[i];
                        j++;
                    }
                }

                //byte array to int
                serverPort = ((serverNewPort[0] & 0xff) << 24) |
                        ((serverNewPort[1] & 0xff) << 16) |
                        ((serverNewPort[2] & 0xff) << 8) |
                        (serverNewPort[3] & 0xff);
                gotNewServerPort = true;
            }
            else {
                acks = ackReceived;
            }
            byte ackDecoded = decodeACK(acks);
            // If there was error in the transmitted ACK, Retransmit packet
            if(ackDecoded == ERROR_IN_ACK) {
                System.out.println("------------------------------------");
                System.out.println("Ack Error in Transmission");
                System.out.println("------------------------------------");
            }
            // If the ACK is not for the corresponding packet, thus means probably the packet lost, so retransmit it
            else if(ackDecoded == lastPacketSeq){
                System.out.println("------------------------------------");
                System.out.println("Not correct Ack Number Error");
                System.out.println("------------------------------------");
            }
            // Valid ACK move to next Packet
            else {
                System.out.println("******************************");
                System.out.println("Valid Ack! Move to the next packet");
                System.out.println("******************************");

                lastPacketSeq = NetworkUtils.calculateNextSeqNumber(lastPacketSeq);
                noValidAckReceived = false;
            }
        }
        return SEND_NEXT;
    }

    //region Default Setters and Getters
    public String getServerIp() {
        return serverIp;
    }

    public void setServerIp(String serverIp) {
        this.serverIp = serverIp;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getFileDir() {
        return fileDir;
    }

    public void setFileDir(String fileDir) {
        this.fileDir = fileDir;
    }

    public int getPayloadSize() {
        return payloadSize;
    }

    public void setPayloadSize(int payloadSize) {
        this.payloadSize = payloadSize;
    }
    //endregion

    private DatagramPacket createPacket(int key) throws IOException {
        /*
            Packet format:
                1 byte SEQ NUM
                1 byte flag ->  1 if it is the last packet
                                0 if it is not the last packet
                4 bytes Payload size
                x bytes Actual data
                8 bytes CheckSum
         */
        boolean isLastPacket = key == (payloadsToSent.size() - 2);
        byte[] filePartPayloadData = payloadsToSent.get(key);

        ByteBuffer packetBytesBuffer = ByteBuffer.allocate(filePartPayloadData.length + 6);
        byte[] seqFlag = new byte[2];
        seqFlag[0] = lastPacketSeq;
        seqFlag[1] = isLastPacket ? (byte)1 : (byte)0;
        byte[] payloadSizeSent = ByteBuffer.allocate(4).putInt(filePartPayloadData.length).array();

        packetBytesBuffer.put(seqFlag);
        packetBytesBuffer.put(payloadSizeSent);
        packetBytesBuffer.put(filePartPayloadData);
        byte[] packetBytesPreCheckSum = packetBytesBuffer.array();
        long checkSum = NetworkUtils.calculateCheckSum(packetBytesPreCheckSum);

        ByteBuffer packetBytes = ByteBuffer.allocate(packetBytesPreCheckSum.length + 8);
        packetBytes.put(packetBytesPreCheckSum);
        packetBytes.putLong(checkSum);

        byte[] packet = packetBytes.array();
        return new DatagramPacket(packet, packet.length, InetAddress.getByName(serverIp), serverPort);

        /*
        CRC32 checksum = new CRC32();
        checksum.update(seqNumBytes);
        checksum.update(dataBytes);
        byte[] checksumBytes = ByteBuffer.allocate(8).putLong(checksum.getValue()).array();	// checksum (8 bytes)

        // generate packet
        ByteBuffer pktBuf = ByteBuffer.allocate(8 + 4 + dataBytes.length);
        pktBuf.put(checksumBytes);
        pktBuf.put(seqNumBytes);
        pktBuf.put(dataBytes);
        return pktBuf.array();*/

    }

    private InetAddress getInetServerAddress() throws UnknownHostException {
        return InetAddress.getByName(serverIp);
    }

    private static final byte ERROR_IN_ACK = 3;

    private byte decodeACK(byte[] acks){
        if(acks[0] == (byte)7){
            return (byte)1;
        }
        else if(acks[0] == (byte)0){
            return (byte)0;
        }
        else {
            return ERROR_IN_ACK;
        }
    }


}
