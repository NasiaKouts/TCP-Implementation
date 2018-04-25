package Server;

import Models.Packet;
import Utils.NetworkUtils;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

public class Client extends BaseServer{
    private ArrayList<byte[]> packetsToSent;
    private byte packetSequenceNumber;
    Semaphore s;

    private String serverIp;
    private int serverPort;
    private String filename;
    private String fileDir;
    private int payloadSize;

    public Client(String serverIp, int serverPort, String filename, String fileDir, int payloadSize){
        super();
        setServerIp(serverIp);
        setServerPort(serverPort);
        setFilename(filename);
        setFileDir(fileDir);
        setPayloadSize(payloadSize);
        packetSequenceNumber = (byte)0;
        packetsToSent = new ArrayList<>();
        setPort(NetworkUtils.GetNextAvailablePort());

        this.openServer();
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
        File file = new File("/d:/desktop/DSC_0409.JPG");
        if(file.exists() && !file.isDirectory()){

            Path path = Paths.get("/d:/desktop/DSC_0409.JPG");
            byte[] fileDataByteArray = Files.readAllBytes(path);
        /* todo ??
        InputStream inFromFile = new FileInputStream(file);
        byte[] fileDataByteArray = new byte[(int)file.length()];
        inFromFile.read(fileDataByteArray);
        */



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
                packetSequenceNumber = nextPacketSeq();

                // TODO den eimai sigouri oti einai sostos o elegos.
                // an einai last packet thelw na valw payload toso oso to "ipoloipomeno" meros tu pinaka
                if(lastPacketFlag && payloadSize > i - fileDataByteArray.length){
                    payloadSize = fileDataByteArray.length;
                }

                ByteBuffer packetBytesBuffer = ByteBuffer.allocate(payloadSize + 6);
                byte[] seqFlag = new byte[2];
                seqFlag[0] = packetSequenceNumber;
                seqFlag[1] = (!lastPacketFlag ? (byte)0 : (byte)1);
                byte[] payloadSizeSent = ByteBuffer.allocate(4).putInt(payloadSize).array();

                byte[] data = new byte[payloadSize];
                if (lastPacketFlag) {
                    // if it is the last packet send the rest bytes of the array
                    for (int j=0;  j < fileDataByteArray.length - i; j++) {
                        data[j+3] = fileDataByteArray[i+j];
                    }
                } else {
                    for (int j = 0; j < payloadSize; j++) {
                        data[j+3] = fileDataByteArray[i+j];
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
                        notReplyToHandshake = false;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                socket.send(sendPacket);
                System.out.println("Sent: Sequence number = " + packetSequenceNumber +
                        ", Flag = " + (lastPacketFlag ? "Last packet" : "Note last packet") );

            }

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

    @Override
    public void run() {
        //
    }

    DatagramSocket socket;
    @Override
    public void openServer() {
        try {
            socket = new DatagramSocket(getPort());
        } catch (SocketException e) {
            e.printStackTrace();
        }

        handShake();
        sendFileName();
        try {
            sendFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    boolean notReplyToHandshake = true;

    private void handShake(){
        // Send dummy package to initialize handshake
        byte [] dummyPacket = new byte[payloadSize + 14];
        dummyPacket[Packet.SEQ_NUM_INDEX] = packetSequenceNumber;
        for(int i = 1; i < Packet.PAYLOAD_START_INDEX; i++){
            dummyPacket[i] = 0;
        }

        System.out.println("Client: First step of 3-way-handshake..." +
                "\n\t PACKET Num = " + packetSequenceNumber);
        DatagramPacket sendPacket = new DatagramPacket(dummyPacket, dummyPacket.length, getInetServerAddress(), serverPort);
        while(notReplyToHandshake){
            try {
                byte ack = sendPacketAndDecodeAck(sendPacket);
                if(ack == 2 || ack != nextPacketSeq()) continue;
                System.out.println("Client: Received ACK on 3-way-handshake");
                notReplyToHandshake = false;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        System.out.println("Client: Last step of 3-way-handshake" +
                    "\n\t Wait a little bit till start sending the file");
        packetSequenceNumber = nextPacketSeq();
        dummyPacket[Packet.SEQ_NUM_INDEX] = packetSequenceNumber;
        sendPacket = new DatagramPacket(dummyPacket, dummyPacket.length, getInetServerAddress(), serverPort);
        try {
            socket.send(sendPacket);

            Thread.sleep(9000);
            packetSequenceNumber = nextPacketSeq();

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    private void sendFileName(){
        byte [] filenameBytes = filename.getBytes();

        ByteBuffer fileNamePacketBuffer = ByteBuffer.allocate(filenameBytes.length + 6);
        byte[] seqFlag = new byte[2];
        seqFlag[0] = packetSequenceNumber;
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
                "\n\t Sequence num: " + packetSequenceNumber +
                "\n\t Payload: " + filenameBytes.length +
                "\n\t Checksum: " + checkSum);

        DatagramPacket sendPacket = new DatagramPacket(fileNamePacket, fileNamePacket.length, getInetServerAddress(), serverPort);
        boolean notReplyToFilenamePacket = true;
        while(notReplyToFilenamePacket){
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

    private byte sendPacketAndDecodeAck(DatagramPacket sendPacket) throws IOException {
        socket.send(sendPacket);
        byte[] ackMessageReceived = new byte[3];
        DatagramPacket receivedPacket = new DatagramPacket(ackMessageReceived, ackMessageReceived.length);
        socket.setSoTimeout(1000);
        socket.receive(receivedPacket);

        System.out.println();
        System.out.println("Rceived ACK = " + ackMessageReceived[0] + "   " +  ackMessageReceived[1] + "   " +  ackMessageReceived[2] + "   ");
        ackMessageReceived = receivedPacket.getData();
        return decodeACK(ackMessageReceived);
    }

    private byte nextPacketSeq(){
        return packetSequenceNumber == (byte)0 ? (byte)1 : (byte)0;
    }

    private InetAddress getInetServerAddress(){
        try {
            return InetAddress.getByName(serverIp);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return null;
    }

    private byte decodeACK(byte[] acks){
        if(acks[0] == acks[1] && acks[1] == acks[2]){
            return acks[0];
        }
        else {
            return (byte)2;
        }
    }
}
