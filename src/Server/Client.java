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

        payloadsToSent = new HashMap<>();

        File file = new File(fileDir + "\\" + filename);
        try {
            InputStream inputStream = new FileInputStream(file);
            byte[] fileByteArray = new byte[(int) file.length()];
            inputStream.read(fileByteArray);
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
            while(notFinished){
                handShake();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void splitToPackets(byte[] bytes){
        int fullSteps = bytes.length / payloadSize;

        //region [Add Filename Packet]

        byte[] filenameBytes = filename.getBytes();

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

        payloadsToSent.put(-1, fileNamePacket);

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

        for(int i = 1; i < Packet.PAYLOAD_START_INDEX; i++){
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
                dummyPacket[0] = lastPacketSeq;
                try {
                    packetToSent = new DatagramPacket(dummyPacket, dummyPacket.length, getInetServerAddress(), serverPort);
                    sendPacketNoListen(packetToSent);
                    lastPacketSeq = NetworkUtils.calculateNextSeqNumber(lastPacketSeq);
                    Thread.sleep(1000);
                    reSend = false;
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
            }
        }
        System.out.println("Strating to Send The File!");

        for(int key : payloadsToSent.keySet()){
            try {
                System.out.println("Strating to Send The File!");
                DatagramPacket filePartToSend = createPacket(key);

                int result = sendPacket(filePartToSend);
                while (result != SEND_NEXT){
                    result = sendPacket(filePartToSend);
                }
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("------------------------------------");
                System.out.println("Failed to Send Packet with Key: " + key);
                System.out.println("------------------------------------");
            }
        }
    }

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

            // packet sent, waiting for ACK
            byte[] ackReceived = new byte[3];
            DatagramPacket ackReceivedPacket = new DatagramPacket(ackReceived, ackReceived.length);
            try {
                try {
                    packetSocket.setSoTimeout(10000);
                    System.out.println("Sent timeout");
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


                serverPort = ackReceivedPacket.getPort();

                System.out.println("******************************");
                System.out.println("A ACK Received by Server");
                System.out.println("ACK = " + ackReceived[0] + "\t" + ackReceived[1] + "\t" + ackReceived[2]);
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


            byte ackDecoded = decodeACK(ackReceived);
            // If there was error in the transmitted ACK, Retransmit packet
            if(ackDecoded == ERROR_IN_ACK) {
                System.out.println("------------------------------------");
                System.out.println("Ack Error in Transmission");
                System.out.println("------------------------------------");
            }
            // If the ACK is not for the corresponding packet, thus means probably the packet lost, so retransmit it
            else if(ackDecoded != lastPacketSeq){
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
        return new DatagramPacket(packet, packet.length, InetAddress.getByName(serverIp), getPort());

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
        if(acks[0] == acks[1] && acks[1] == acks[2]){
            return acks[0];
        }
        else {
            return ERROR_IN_ACK;
        }
    }


}
