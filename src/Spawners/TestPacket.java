package Spawners;


import Models.Packet;
import Utils.NetworkUtils;

import java.io.*;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.stream.IntStream;

// everything except sockets works fine!
public class TestPacket {
    static String fileDir ="D:\\Desktop\\101D3200";
    static String filename =  "DSC_0001.jpg";
    static int payloadSize = 1000;
    private static HashMap<Integer, byte[]> payloadsToSent = new HashMap<>();

    public static void main(String args[]){
        File file = new File(fileDir + "\\" + filename);
        try {
            InputStream inputStream = null;
            try {
                inputStream = new FileInputStream(file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                System.out.println("ERROR INPUTE STREAM");
            }
            byte[] fileByteArray = new byte[(int) file.length()];
            inputStream.read(fileByteArray);
            splitToPackets(fileByteArray);

            //remake
            boolean first = true;
            File outfile = null;
            for(int key : payloadsToSent.keySet()) {
                System.out.println("Strating to Send The File!");
                DatagramPacket filePartToSend = createPacket(key);

                Packet packet = new Packet(filePartToSend.getData());
                if(first) {
                    String fileName = new String(packet.getData());
                    outfile = new File(fileName);
                    if (!outfile.exists()) {
                        try {
                            outfile.createNewFile();
                        } catch (IOException e) {
                            e.printStackTrace();
                            return;
                        }
                    }
                    first = false;
                }
                else {
                    if(outfile != null){
                        FileOutputStream saltOutFile = new FileOutputStream(outfile, true);
                        saltOutFile.write(packet.getData());
                        saltOutFile.close();
                    }

                }
            }


    } catch (IOException e) {
            e.printStackTrace();
            System.out.println("ERRORFILE READ");
        }
    }

    private static void splitToPackets(byte[] bytes){
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

    private static byte lastPacketSeq = 0;

    private static DatagramPacket createPacket(int key) throws IOException {
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
        return new DatagramPacket(packet, packet.length, InetAddress.getByName("localhost"), 4200);

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
}
