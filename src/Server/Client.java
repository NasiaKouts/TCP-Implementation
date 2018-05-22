package Server;

import Models.Packet;
import Utils.NetworkUtils;

import javax.swing.*;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.stream.IntStream;

public class Client extends BaseServer{
    // Parallel
    private HashMap<Integer, int[]> payloadsToSent;

    private byte lastPacketSeq = 0;

    private String serverIp;
    private int serverPort;

    private String filename;
    private String fileDir;

    private int payloadSize;

    private int totalPackets;
    private long totalBytesSent;

    private boolean gotNewServerPort = false;

    private InputStream inputStream;
    private DatagramSocket packetSocket;

    private JProgressBar progressBar;

    private boolean handshakeIncomplete = true;

    public Client(String serverIp,
                  int serverPort,
                  String filename,
                  String fileDir,
                  int payloadSize,
                  JTextArea systemOut,
                  JProgressBar progressBar){
        super();
        setServerIp(serverIp);
        setServerPort(serverPort);
        setFilename(filename);
        setFileDir(fileDir);
        setPayloadSize(payloadSize);
        setPort(NetworkUtils.GetNextAvailablePort());
        setIp(NetworkUtils.GetCurrentAddress());
        setSystemOut(systemOut);

        totalPackets = 0;
        totalBytesSent = 0;
        payloadsToSent = new HashMap<>();
        this.progressBar = progressBar;

        File file = new File(fileDir + "/" + filename);

        // Loading file into byte array
        inputStream = null;
        try {
            inputStream = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            print("ERROR FILE INPUT STREAM");
            e.printStackTrace();
        }

        // Split byte file array of file's data into payloads
        splitToPackets(file.length());

        try {
            packetSocket = new DatagramSocket(getPort(), getInetServerAddress());
            print("Opened server socket! Is now able to send packets");
        } catch (SocketException e) {
            e.printStackTrace();
            print("------------------------------------");
            print("packetSocket = new DatagramSocket(serverPort, InetAddress.getByName(getServerIp()) error");
            print("Re-open Client and Try Again!");
            print("------------------------------------");
        } catch (UnknownHostException e) {
            e.printStackTrace();
            print("------------------------------------");
            print("Unknown Hoet Error");
            print("Re-open Client and Try Again!");
            print("------------------------------------");

        }
        handShake();
    }

    private void splitToPackets(long bytes){
        int fullSteps = (int)bytes / payloadSize;

        payloadsToSent.put(-1, new int[] {-1, 0});

        for(int i = 0; i <= fullSteps; i++){
            int startIndex = i * payloadSize;
            int bytesLength = payloadSize;
            if (i == fullSteps){
                int lastLength = (int)bytes - startIndex;
                if(lastLength <= 0) return;
                bytesLength = lastLength;
            }

            payloadsToSent.put(i, new int[] {startIndex, startIndex + bytesLength - 1});
        }
    }

    private void handShake(){
        // Send dummy package to initialize handshake, contains only a SEQ number
        byte[] dummyPacket = new byte[payloadSize + Packet.HEADER_SIZE + Packet.CHECKSUM_SIZE];

        //set flags
        dummyPacket[0] = 0;
        dummyPacket[0] |= (lastPacketSeq << 2);
        dummyPacket[0] |= (Packet.NO_DATA_FILE << 1);

        for(int i = 1; i < Packet.PAYLOAD_START_INDEX; i++){
            dummyPacket[i] = 0;
        }

        print("******************************");
        print("Client about to create start handshake");
        print("Client " + NetworkUtils.GetCurrentAddress());
        print("Sending dummy data");
        print("Starting the first step of the 3-Way-Handshake!");
        print("******************************");

        DatagramPacket packetToSent;
        try {
            packetToSent = new DatagramPacket(dummyPacket, dummyPacket.length, getInetServerAddress(), serverPort);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            print("------------------------------------");
            print("Unknown Host Error");
            print("Re-open Client and Try Again!");
            print("------------------------------------");
            return;
        }

        boolean reSend = true;
        while (reSend){
            int result = sendPacket(packetToSent);

            //Sending Client ACK
            if(result == SEND_NEXT){
                print("Received server's ACK of the 3-Way-Handshake!");
                //set flags
                dummyPacket[0] = 0;
                dummyPacket[0] |= (lastPacketSeq << 2);
                dummyPacket[0] |= (Packet.NO_DATA_FILE << 1);
                try {
                    packetToSent = new DatagramPacket(dummyPacket, dummyPacket.length, getInetServerAddress(), serverPort);
                    sendPacketNoListen(packetToSent);
                    print("Sent my ACK back 3-Way-Handshake!");
                    // reset SEQ
                    lastPacketSeq = -1;
                    reSend = false;
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
            }
        }

        handshakeIncomplete = false;

        long startTime = System.nanoTime();

        print("Starting Sending The File!");

        if(lastPacketSeq == -1) lastPacketSeq = 0;
        for(int key : payloadsToSent.keySet()){
            try {
                print("current key " + key);
                DatagramPacket filePartToSend = createPacket(key);

                int result = sendPacket(filePartToSend);
                while (result != SEND_NEXT){
                    filePartToSend = createPacket(key);
                    result = sendPacket(filePartToSend);
                }

                if(progressBar != null){
                    int value = (int) ((double) (key+1)*100/payloadsToSent.keySet().size());
                    if(key == payloadsToSent.keySet().size() - 2)
                        value = 100;
                    progressBar.setValue(value);
                }
            } catch (IOException e) {
                e.printStackTrace();
                print("------------------------------------");
                print("Failed to Send Packet with Key: " + key);
                print("------------------------------------");
            }
        }

        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) { }

        System.out.println();
        print("******************************");
        print("File Transfer has been Completed! ");
        print("Send server multi times the last ACK to inform that I received that he got the file");
        print("******************************");

        for(int i = 0; i < 10; i++){
            dummyPacket[Packet.HEADER_INDEX] |= ((byte)1 << 2);
            dummyPacket[Packet.HEADER_INDEX] |= ((byte)1 << 1);
            dummyPacket[Packet.HEADER_INDEX] |= (byte)1;
            try {
                packetToSent = new DatagramPacket(dummyPacket, dummyPacket.length, getInetServerAddress(), serverPort);
                sendPacketNoListen(packetToSent);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }

        long estimatedTimeInMs = (System.nanoTime() - startTime)/1000000;
        double kbps = (totalBytesSent/1000d) / (estimatedTimeInMs / 1000d);
        /* Round to 2 decimal */
        kbps = kbps*100;
        kbps = (double)((int) kbps);
        kbps = kbps /100;
        final JFrame parent = new JFrame();
        JButton button = new JButton();
        JOptionPane.showMessageDialog(parent,
                "Total Transfer Time:\t" + estimatedTimeInMs + " ms\n"+
                        "Transfer Speed:\t" + kbps + " kBps\n"+
                        "Total Packets Used:\t" + totalPackets);
        print("******************************");
        print("Total Transfer Time:\t" + estimatedTimeInMs + " ms");
        print("Transfer Speed:\t" + kbps + " kBps");
        print("Total Packets Used:\t" + totalPackets);
        print("******************************");
    }

    private final static Integer UNABLE_TO_RESUME = -1;
    private final static Integer SEND_NEXT = 2;

    private Integer sendPacketNoListen(DatagramPacket packetToSent){
        // sending the packet
        if (forwardPacket(packetToSent)) return UNABLE_TO_RESUME;
        return SEND_NEXT;
    }

    private boolean forwardPacket(DatagramPacket packetToSent) {
        try {
            packetSocket.send(packetToSent);
            totalPackets++;
            totalBytesSent += packetToSent.getLength();
            print("******************************");
            print("Sent Packet Seq = " + lastPacketSeq);
            print("To: "+this.serverIp);
            print("Port: "+serverPort);
            print("******************************");
        } catch (IOException e) {
            e.printStackTrace();
            print("------------------------------------");
            print("Error trying to send packet Seq = " + lastPacketSeq);
            print("Reboot System Please!");
            print("------------------------------------");
            return true;
        }
        return false;
    }

    private Integer sendPacket(DatagramPacket packetToSent){
        boolean noValidAckReceived = true;
        while(noValidAckReceived){
            // sending the packet
            if (forwardPacket(packetToSent)) return UNABLE_TO_RESUME;

            int sizeOfReceived = 1;
            if(handshakeIncomplete)  sizeOfReceived = 5;
            // packet sent, waiting for ACK
            byte[] ackReceived = new byte[sizeOfReceived];
            DatagramPacket ackReceivedPacket = new DatagramPacket(ackReceived, ackReceived.length);
            try {
                // ACK received
                packetSocket.setSoTimeout(10000);
                packetSocket.receive(ackReceivedPacket);
                ackReceived = ackReceivedPacket.getData();
                print("******************************");
                print("A ACK Received by Server");
                print("ACK = " + (ackReceived[0] == (byte)0 ? "0" : "1"));
                print("Last Packet Sent from Client, Seq = " + lastPacketSeq);
                print("New Server Port Is: "+serverPort);
                print("******************************");

            }
            catch (SocketTimeoutException ex){
                sendPacket(packetToSent);
            }
            catch (IOException e) {
                e.printStackTrace();
                print("------------------------------------");
                print("packetSocket.receive(inDatagramPacket) Error");
                print("------------------------------------");
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
                print("------------------------------------");
                print("Ack Error in Transmission");
                print("------------------------------------");
            }
            // If the ACK is not for the corresponding packet, thus means probably the packet lost, so retransmit it
            else if(ackDecoded == lastPacketSeq){
                print("------------------------------------");
                print("Not correct Ack Number Error");
                print("------------------------------------");
            }
            // Valid ACK move to next Packet
            else {
                print("******************************");
                print("Valid Ack! Move to the next packet");
                print("******************************");

                lastPacketSeq = NetworkUtils.calculateNextSeqNumber(lastPacketSeq);
                noValidAckReceived = false;

                return SEND_NEXT;
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
                2 bytes Payload size
                x bytes Actual data
                8 bytes CheckSum
         */
        boolean isLastPacket = key == (payloadsToSent.size() - 2);

        byte[] filePartPayloadData;

        if(key == -1){
            filePartPayloadData = filename.getBytes();
        }else{
            int[] indexes = payloadsToSent.get(key);

            filePartPayloadData = new byte[indexes[1] - indexes[0] + 1];
            inputStream.read(filePartPayloadData);
        }

        ByteBuffer packetBytesBuffer = ByteBuffer.allocate(filePartPayloadData.length + Packet.HEADER_SIZE);
        byte[] header = new byte[Packet.HEADER_SIZE];

        header[Packet.HEADER_INDEX] = 0;
        header[Packet.HEADER_INDEX] |= (lastPacketSeq << 2);
        header[Packet.HEADER_INDEX] |= (isLastPacket ? (byte)1 : (byte)0);

        // we now that max payload is 65500 wish is 0xFFDC or  (11111111 11011100)2
        // so we convert it to byte array and only keep the 2 last bytes
        // since we are sure the rest is zero
        byte[] payloadSizeToByteArray = new byte[] {
                (byte)(filePartPayloadData.length >>> 24),
                (byte)(filePartPayloadData.length >>> 16),
                (byte)(filePartPayloadData.length >>> 8),
                (byte)filePartPayloadData.length};

        header[Packet.PAYLOAD_SIZE_START_INDEX] = payloadSizeToByteArray[2];
        header[Packet.PAYLOAD_SIZE_START_INDEX + 1] = payloadSizeToByteArray[3];

        packetBytesBuffer.put(header);
        packetBytesBuffer.put(filePartPayloadData);
        byte[] packetBytesPreCheckSum = packetBytesBuffer.array();
        long checkSum = NetworkUtils.calculateCheckSum(packetBytesPreCheckSum);

        ByteBuffer packetBytes = ByteBuffer.allocate(packetBytesPreCheckSum.length + Packet.CHECKSUM_SIZE);
        packetBytes.put(packetBytesPreCheckSum);
        packetBytes.putLong(checkSum);

        byte[] packet = packetBytes.array();
        return new DatagramPacket(packet, packet.length, InetAddress.getByName(serverIp), serverPort);
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
