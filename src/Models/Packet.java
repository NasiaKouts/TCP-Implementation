package Models;

import Utils.NetworkUtils;

import java.net.InetAddress;
import java.util.stream.IntStream;
// flag = Packet.NO_DATE_FILE FOR HANDSHAKES
// payloadsize == 0 for handshakes
public class Packet {
    /*
    private byte sequenceNumber;
    private byte flagData;
    private byte flagLast; */

    private byte flags;
    // not really 4 bytes but 2 bytes. however short isnt accepted type cause it isnt unsigned
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

    // THIS CODE IS is used as flag during the handshake
    public final static byte NO_DATA_FILE = 1;

    public final static int HEADER_SIZE = 3;
    public final static int CHECKSUM_SIZE = 8;
    public final static int HEADER_INDEX = 0;
    public final static int PAYLOAD_SIZE_START_INDEX = 1;
    public final static int PAYLOAD_START_INDEX = 3;

    /*
        Packet format:
            1 byte SEQ NUM
            1 byte flag ->  1 if it is the last packet
                            0 if it is not the last packet
            2 bytes Payload size
            x bytes Actual data
            8 bytes CheckSum
     */

    public Packet(byte[] packetByteArray){

        flags = packetByteArray[HEADER_INDEX];

        this.payloadSize = ((packetByteArray[PAYLOAD_SIZE_START_INDEX] & 0xff) << 8) |
                ((packetByteArray[PAYLOAD_SIZE_START_INDEX + 1] & 0xff));

        this.data = new byte[payloadSize];
        IntStream
                .range(0, payloadSize)
                .parallel()
                .forEach(index -> data[index] = packetByteArray[PAYLOAD_START_INDEX + index]);

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
        byte seq = (byte)((flags & 0x04) >> 2);        // mask 0000 0010
        return seq;  // mask 0000 0100;
    }

    //1 byte flagLast ->  1 if it is the last packet
    //                      0 if it is not the last packet
    public boolean isNotLastPacket() {
        byte flagLast = (byte)(flags & 0x01);        // mask 0000 0001
        return flagLast == (byte)0;
    }

    public boolean isNoDataPacket() {
        byte flagData = (byte)((flags & 0x02) >> 1);        // mask 0000 0010
        return flagData == Packet.NO_DATA_FILE; }

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

        byte[] realPacket = new byte[payloadSize + Packet.HEADER_SIZE];
        System.arraycopy(rawPacket, 0, realPacket, 0, realPacket.length);
        return NetworkUtils.calculateCheckSum(realPacket) == checksum;
    }

    public long caluclateCheckSumFromRawData(byte[] rawPacket){
        byte[] realPacket = new byte[payloadSize + 6];
        for(int i = 0; i < realPacket.length; i++){
            realPacket[i] = rawPacket[i];
        }
        return NetworkUtils.calculateCheckSum(realPacket);
    }

    @Override
    public String toString() {
        return "PACKET INFO " +
                "\nSEQ = " + getSequenceNumber() +
                "\nIS NO DATA PACKET = " + isNoDataPacket() +
                "\nIS LAST PACKET = " + !isNotLastPacket() +
                "\nPAYLOAD SIZE = " + payloadSize +
                "\nCHECKSUM = " + checksum;
    }
}