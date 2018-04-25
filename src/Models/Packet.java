package Models;

import Utils.NetworkUtils;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.stream.IntStream;

public class Packet {
    private byte sequenceNumber;
    private boolean notLastPacket;
    private int payloadSize;
    private byte[] data;
    private long checksum;

    private InetAddress address;
    private int port;

    public Packet( byte[] src, InetAddress clientAddress, int port) {
        this(src);
        this.address = clientAddress;
        this.port = port;
    }

    public final static int SEQ_NUM_INDEX = 0;
    public final static int FLAG_INDEX = 1;
    public final static int PAYLOAD_SIZE_START_INDEX = 2;
    public final static int PAYLOAD_START_INDEX = 6;

    /*
        Packet format:
            1 byte SEQ NUM
            1 byte flag ->  1 if it is the last packet
                            0 if it is not the last packet
            4 bytes Payload size
            x bytes Actual data
            8 bytes CheckSum
     */
    public Packet(byte[] packetByteArray){

        this.sequenceNumber = packetByteArray[SEQ_NUM_INDEX];

        this.notLastPacket = packetByteArray[FLAG_INDEX] == 0x00;

        this.payloadSize = ((packetByteArray[PAYLOAD_SIZE_START_INDEX] & 0xff) << 24) |
                ((packetByteArray[PAYLOAD_SIZE_START_INDEX + 1] & 0xff) << 16) |
                ((packetByteArray[PAYLOAD_SIZE_START_INDEX + 2] & 0xff) << 8) |
                (packetByteArray[PAYLOAD_SIZE_START_INDEX + 3] & 0xff);

        this.data = new byte[payloadSize];
        IntStream
                .range(0, payloadSize)
                .parallel()
                .forEach(index -> data[index] = packetByteArray[PAYLOAD_START_INDEX + index]);

        /*byte[] destArr = new byte[8];
        System.arraycopy(packetByteArray, PAYLOAD_START_INDEX + payloadSize, destArr, 0, 8);
        this.checksum = destArr; */
        this.checksum = ((long)(packetByteArray[PAYLOAD_START_INDEX + payloadSize] & 0x00ff) << 56) +
                ((long)(packetByteArray[PAYLOAD_START_INDEX + payloadSize + 1] & 0x00ff) << 48) +
                ((long)(packetByteArray[PAYLOAD_START_INDEX + payloadSize + 2] & 0x00ff) << 40) +
                ((long)(packetByteArray[PAYLOAD_START_INDEX + payloadSize + 3] & 0x00ff) << 32) +
                ((packetByteArray[PAYLOAD_START_INDEX + payloadSize + 4] & 0x00ff) << 24) +
                ((packetByteArray[PAYLOAD_START_INDEX + payloadSize + 5] & 0x00ff) << 16) +
                ((packetByteArray[PAYLOAD_START_INDEX + payloadSize + 6] & 0x00ff) << 8) +
                (packetByteArray[PAYLOAD_START_INDEX + payloadSize + 7] & 0x00ff);
    }

    //region [Default Getters and Setters]

    public int getPayloadSize() {
        return payloadSize;
    }

    public void setPayloadSize(int payloadSize) {
        this.payloadSize = payloadSize;
    }

    public byte getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(byte sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public boolean isNotLastPacket() {
        return notLastPacket;
    }

    public void setNotLastPacket(boolean notLastPacket) {
        this.notLastPacket = notLastPacket;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public long getChecksum() {
        return checksum;
    }

    public void setChecksum(long checksum) {
        this.checksum = checksum;
    }

    public InetAddress getAddress() {
        return address;
    }

    public void setAddress(InetAddress address) {
        this.address = address;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
    //endregion

    private void setDataFromSrc(byte[] src){
        data = new byte[payloadSize];
        IntStream
                .range(0, payloadSize)
                .parallel()
                .forEach(index -> data[index] = src[6+index]);
    }

    public boolean isValid(byte[] rawPacket){
        // in case the client sent ACK for the 3-way-handshake
        if(payloadSize == 0) return true;

        byte[] realPacket = new byte[payloadSize + 6];
        for(int i = 0; i < realPacket.length; i++){
            realPacket[i] = rawPacket[i];
        }
        long current = NetworkUtils.calculateCheckSum(realPacket);
        //byte[] currenctCheckSum = ByteBuffer.allocate(8).putLong(current).array();

        System.out.println("Calculated checksum: " + current +
                    "\n Received checksum: " + checksum);
        //return Arrays.equals(currenctCheckSum, checksum);
        return current == checksum;
        /*
            If using CRC
            CRC32 checkSumCalculated = new CRC32();
			checkSumCalculated.update(realPacket);
			return checkSumCalculated == checksum;
         */
    }

    @Override
    public String toString() {
        return "PACKET INFO " +
                "\nSEQ = " + sequenceNumber +
                "\nFLAG = " + notLastPacket +
                "\nPAYLOAD SIZE = " + payloadSize +
                "\nCHECKSUM = " + checksum;
    }
}
