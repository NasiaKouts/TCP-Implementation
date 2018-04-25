package Server;

import Models.Packet;
import Utils.NetworkUtils;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.HashMap;

public class Client extends BaseServer{

    // Parallel
    private HashMap<Integer, byte[]> payloadsToSent;
    private HashMap<Integer, byte[]> payloadsSizes;

    private byte lastPacketSeq = 0;

    private String serverIp;
    private int serverPort;
    private String filename;
    private String fileDir;
    private int payloadSize;

    boolean notFinished = true;
    boolean handshakeIncomplete = true;
    boolean noValidAckReceived = true;

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

        try {
            ackSocket = new DatagramSocket(getPort());
            System.out.println(getInstanceName() + " with " +
                    getIp() + ":" + getPort() + " opened! Is now able to receive ACKs");
        } catch (SocketException e) {
            e.printStackTrace();
            System.out.println("------------------------------------");
            System.out.println("ackSocket = new DatagramSocket(getPort()) error");
            System.out.println("Re-open Client!");
            System.out.println("------------------------------------");
            return;
        }

        try {
            packetSocket = new DatagramSocket(serverPort, getInetServerAddress());
            System.out.println("Opened server socket! Is now able to send packets");
        } catch (SocketException e) {
            e.printStackTrace();
            System.out.println("------------------------------------");
            System.out.println("packetSocket = new DatagramSocket(serverPort, InetAddress.getByName(getServerIp()) error");
            System.out.println("Re-open Client and Try Again!");
            System.out.println("------------------------------------");
            return;
        } catch (UnknownHostException e) {
            e.printStackTrace();
            System.out.println("------------------------------------");
            System.out.println("Unknown Hoest Error");
            System.out.println("Re-open Client and Try Again!");
            System.out.println("------------------------------------");
            return;
        }

        byte[] fileByteArray = loadFile(fileDir + "\\" + filename);
        splitToPackets(fileByteArray);

        while(notFinished){
            handShake();
        }

    }

    // TODO IMPLEMENT
    private byte[] loadFile(String path){
        return new byte[100];
    }

    // TODO IMPLEMENT
    private void splitToPackets(byte[] bytes){

    }

    private void handShake(){
        // Send dummy package to initialize handshake, contains only a SEQ number
        byte [] dummyPacket = new byte[payloadSize + Packet.HEADER_SIZE + Packet.CHECKSUM_SIZE];

        dummyPacket[Packet.SEQ_NUM_INDEX] = lastPacketSeq;

        for(int i = 1; i < Packet.PAYLOAD_START_INDEX; i++){
            dummyPacket[i] = 0;
        }

        System.out.println("******************************");
        System.out.println("Client about to create start handshake");
        System.out.println("Client " + NetworkUtils.GetCurrentAddress());
        System.out.println("Sending dummy data");
        System.out.println("Starting the first step of the 3-Way-Handshake!");
        System.out.println("******************************");

        DatagramPacket packetToSent = null;
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

        sendPacket(packetToSent);

        System.out.println("Client: Last step of 3-way-handshake" +
                "\n\t Wait a little bit till start sending the file");
        lastPacketSeq = nextPacketSeq();
        dummyPacket[Packet.SEQ_NUM_INDEX] = lastPacketSeq;
        packetToSent = new DatagramPacket(dummyPacket, dummyPacket.length, getInetServerAddress(), serverPort);
        try {
            socket.send(packetToSent);

            Thread.sleep(1000);
            lastPacketSeq = nextPacketSeq();

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    private final static Integer UNABLE_TO_RESUME = -1;
    private final static Integer RETRANSMIT = 1;
    private final static Integer SEND_NEXT = 2;

    private Integer sendPacket(DatagramPacket packetToSent){
        noValidAckReceived = true;
        while(noValidAckReceived){
            // sending the packet
            try {
                packetSocket.send(packetToSent);
                System.out.println("******************************");
                System.out.println("Sent Packet Seq = " + lastPacketSeq);
                System.out.println("******************************");
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("------------------------------------");
                System.out.println("Error trying to send packet Seq = " + lastPacketSeq);
                System.out.println("Reboot System Please!");
                System.out.println("------------------------------------");
                return UNABLE_TO_RESUME;
            }

            // packet sent, waiting for ACK
            byte[] ackReceived = new byte[3];
            DatagramPacket ackReceivedPacket = new DatagramPacket(ackReceived, ackReceived.length);
            try {
                try {
                    ackSocket.setSoTimeout(10000);
                    System.out.println("Sent timeout");
                } catch (SocketException e) {
                    e.printStackTrace();
                    System.out.println("------------------------------------");
                    System.out.println("Timeout! Retransmit packet Seq = " + lastPacketSeq);
                    System.out.println("------------------------------------");
                    return RETRANSMIT;
                }
                // ACK received
                ackSocket.receive(ackReceivedPacket);
                ackReceived = ackReceivedPacket.getData();
                System.out.println("******************************");
                System.out.println("A ACK Received by Server");
                System.out.println("ACK = " + ackReceived[0] + "\t" + ackReceived[1] + "\n" + ackReceived[2]);
                System.out.println("Last Packet Sent from Client, Seq = " + lastPacketSeq);
                System.out.println("******************************");

            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("------------------------------------");
                System.out.println("packetSocket.receive(inDatagramPacket) Error");
                System.out.println("------------------------------------");
                continue;
            }

            byte ackDecoded = decodeACK(ackReceived);
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
                // if is the handshake's ACK update out socket
                if(handshakeIncomplete){
                    try {
                        packetSocket = new DatagramSocket(ackReceivedPacket.getPort(), ackReceivedPacket.getAddress());
                    } catch (SocketException e) {
                        e.printStackTrace();
                        System.out.println("------------------------------------");
                        System.out.println("DatagramSocket(ackReceivedPacket.getPort(), ackReceivedPacket.getAddress()) Error");
                        System.out.println("------------------------------------");
                        return  UNABLE_TO_RESUME;
                    }
                }
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

    private void sendFile() throws IOException {
        System.out.println("Client: Sending file to server with ip: " + getServerIp() +
                            "\n\t File's name: " + getFilename());

        /*
            Create array of bytes to store the file's data
         */
        // TODO GET REAL PATH
        File file = new File("C:\\Users\\Nasia LiAD\\Videos\\DSC_0265.jpg");
        if(file.exists() && !file.isDirectory()){

            //Path path = Paths.get("C:\\Users\\Nasia LiAD\\Videos\\DSC_0265.jpg");
            //byte[] fileDataByteArray = Files.readAllBytes(path);
            InputStream inFromFile = new FileInputStream(file);
            byte[] fileDataByteArray = new byte[(int)file.length()];
            inFromFile.read(fileDataByteArray);



            /*
                Save the time before the sending have began
                in order to calculate the time needed to be sent
             */
            long startTime = System.nanoTime();

            /*
                Packet format:
                    1 byte SEQ NUM
                    1 byte flag ->  1 if it is the last packet
                                    0 if it is not the last packet
                    4 bytes Payload size
                    x bytes Actual data
                    8 bytes CheckSum
             */
            for(int i = 0; i < fileDataByteArray.length; i = i+payloadSize){
                boolean lastPacketFlag = (i+payloadSize) >= fileDataByteArray.length;
                lastPacketSeq = nextPacketSeq();

                // TODO den eimai sigouri oti einai sostos o elegos.
                // an einai last packet thelw na valw payload toso oso to "ipoloipomeno" meros tu pinaka
                if(lastPacketFlag && payloadSize > i - fileDataByteArray.length){
                    payloadSize = fileDataByteArray.length;
                }

                ByteBuffer packetBytesBuffer = ByteBuffer.allocate(payloadSize + 6);
                byte[] seqFlag = new byte[2];
                seqFlag[0] = lastPacketSeq;
                seqFlag[1] = (!lastPacketFlag ? (byte)0 : (byte)1);
                byte[] payloadSizeSent = ByteBuffer.allocate(4).putInt(payloadSize).array();

                byte[] data = new byte[payloadSize];
                if (lastPacketFlag) {
                    // if it is the last packet send the rest bytes of the array
                    for (int j=0;  j < fileDataByteArray.length - i; j++) {
                        data[j] = fileDataByteArray[i+j];
                    }
                } else {
                    for (int j = 0; j < payloadSize; j++) {
                        data[j] = fileDataByteArray[i+j];
                    }
                }

                packetBytesBuffer.put(seqFlag);
                packetBytesBuffer.put(payloadSizeSent);
                packetBytesBuffer.put(data);
                byte[] packetBytesPreCheckSum = packetBytesBuffer.array();
                long checkSum = NetworkUtils.calculateCheckSum(packetBytesPreCheckSum);

                ByteBuffer packetBytes = ByteBuffer.allocate(packetBytesPreCheckSum.length + 8);
                packetBytes.put(packetBytesPreCheckSum);
                packetBytes.putLong(checkSum);

                byte[] packet = packetBytes.array();

                DatagramPacket sendPacket = new DatagramPacket(packet, packet.length, InetAddress.getByName(serverIp), getPort());
                boolean noValidAckReceived = true;
                while(noValidAckReceived){
                    try {
                        byte ack = sendPacketAndDecodeAck(sendPacket);
                        if(ack == 2 || ack != nextPacketSeq()) continue;
                        System.out.println("Client: Received valid ACK!" +
                                (lastPacketFlag ? "\n\t My job is done here! I sent all my packets" : "\n\t Preparing to send next packet..."));
                        noValidAckReceived = false;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                socket.send(sendPacket);
                System.out.println("Sent: Sequence number = " + lastPacketSeq +
                        ", Flag = " + (lastPacketFlag ? "Last packet" : "Note last packet") );

            }

        }
        else {
            System.out.println("NOT FOUND");
        }

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

    private void sendFileName(){
        byte [] filenameBytes = filename.getBytes();

        ByteBuffer fileNamePacketBuffer = ByteBuffer.allocate(filenameBytes.length + 6);
        byte[] seqFlag = new byte[2];
        seqFlag[0] = lastPacketSeq;
        seqFlag[1] = 0;
        byte[] filenameSize = ByteBuffer.allocate(4).putInt(filenameBytes.length).array();
        byte[] filename = ByteBuffer.allocate(filenameBytes.length).put(filenameBytes).array();

        fileNamePacketBuffer.put(seqFlag);
        fileNamePacketBuffer.put(filenameSize);
        fileNamePacketBuffer.put(filename);

        byte[] filenamePacketPreCheckSum = fileNamePacketBuffer.array();
        long checkSum = NetworkUtils.calculateCheckSum(filenamePacketPreCheckSum);

        ByteBuffer fileNameFinalPacketBuffer = ByteBuffer.allocate(filenamePacketPreCheckSum.length + 8);
        fileNameFinalPacketBuffer.put(filenamePacketPreCheckSum);
        fileNameFinalPacketBuffer.putLong(checkSum);

        byte[] fileNamePacket = fileNameFinalPacketBuffer.array();

        System.out.println("Client: Sending the filename info packet..." +
                "\n\t Filename: " + getFilename() +
                "\n\t Sequence num: " + lastPacketSeq +
                "\n\t Payload: " + filenameBytes.length +
                "\n\t Checksum: " + checkSum);

        boolean notReplyToFilenamePacket = true;
        while(notReplyToFilenamePacket){
            DatagramPacket sendPacket = new DatagramPacket(fileNamePacket, fileNamePacket.length, getInetServerAddress(), serverPort);
            try {
                byte ack = sendPacketAndDecodeAck(sendPacket);
                if(ack == 2 || ack != nextPacketSeq()) continue;
                System.out.println("Client: Received ACK on filename info packet");
                notReplyToFilenamePacket = false;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private InetAddress getInetServerAddress() throws UnknownHostException {
        return InetAddress.getByName(serverIp);
    }

    private static final byte ERROR_IN_ACK = 3;

    private byte decodeACK(byte[] acks){
        if(acks[0] == acks[1] && acks[1] == acks[2]){
            return acks[0];
        }
        else {
            return ERROR_IN_ACK;
        }
    }


}
