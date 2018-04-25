package Server;

import Models.Packet;
import sun.rmi.server.InactiveGroupException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;

public class Server extends BaseServer{

    private static final int MAX_PACKET_SIZE = 65509;

    private HashMap<InetAddress, File> filesIncomplete = new HashMap<>();
    private HashMap<InetAddress, Byte> lastSeqNumberReceived = new HashMap<>();

    // Key: client ip, Value: 0 - if the ACK of the 3-way-handshake has been received
    //                        1 - if not
    private HashMap<InetAddress, Byte> handshakes = new HashMap<>();

    private ArrayList<InetAddress> unhandledPacketsIps = new ArrayList<>();
    private HashMap<InetAddress, ArrayList<DatagramPacket>> unhandledPackets = new HashMap<>();

    public Server(String ip, int port) {
        super();
        setIp(ip);
        setPort(port);

        this.openServer();
    }

    /*
        The server sends back to the client an ACK with the SEQ number of the packet he expects
        ACK messages contain the ACK triple in order to be able minimize the error
     */
    private void sendACK(InetAddress address, int port){
        byte[] ackMessage = new byte[3];
        for(int i = 0; i < ackMessage.length; i++){
            ackMessage[i] = lastSeqNumberReceived.get(address) == 0 ? (byte)1 : (byte)0;
        }

        System.out.println("\n\t ACK Number = " + ackMessage[0] + "   " + ackMessage[1] + "   " + "   " +ackMessage[2]);
        DatagramPacket ackPacket = new DatagramPacket(ackMessage, ackMessage.length, address, port);
        try {
            serverSocket.send(ackPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public synchronized void run() {
        if (unhandledPackets.isEmpty() || unhandledPacketsIps.isEmpty()) return;

        // until we find a client that is has a packet in line (just for safety checkings)
        InetAddress randomClientWaiting = unhandledPacketsIps.remove(0);
        while(!unhandledPackets.containsKey(randomClientWaiting)){
            if(unhandledPacketsIps.isEmpty()) return;
            randomClientWaiting = unhandledPacketsIps.remove(0);
        }
        DatagramPacket inPacket = unhandledPackets.get(randomClientWaiting).remove(0);

        // in case we removed the last packet came from this client remove the whole arraylist
        if(unhandledPackets.get(randomClientWaiting).isEmpty())
            unhandledPackets.remove(randomClientWaiting);
        unhandledPackets.clear();

        int port = inPacket.getPort();
        InetAddress clientAddress = inPacket.getAddress();
        byte[] rawPacket = inPacket.getData();

        Packet packet = new Packet(rawPacket, inPacket.getAddress(), port);
        System.out.println(packet.toString());

        //region ------------ CASE:   CORRUPTED PACKET --------------
        // if the packet failed passing validation (corrupted packet) ignore it
        if(!packet.isValid(rawPacket)) {
            System.out.println("Server: Corrupt packet received. \n\t Ignoring this...");
        }
        //endregion


        //region ------------ CASE:   FIRST STEP OF 3-WAY-HANDSHAKE --------------
        /*  if this is the first time communicating with this client,
            simply add him to handshakes and
            send an ACK to establish 3-way-handshake
         */
        else if(!lastSeqNumberReceived.containsKey(clientAddress)){
            handshakes.put(clientAddress, (byte)1);
            lastSeqNumberReceived.put(clientAddress, packet.getSequenceNumber());
            sendACK(clientAddress, port);
        }
        //endregion


        //region ------------ CASE:   DUPLICATE PACKET --------------
        // received same packet as the last one, resend the ACK and ignore the packet
        else if(lastSeqNumberReceived.get(clientAddress) == packet.getSequenceNumber()){
            sendACK(clientAddress, port);
            System.out.println("Sever: Sent duplicate Ack " +
                    "after receiving duplicate packet " + packet.getSequenceNumber() +
                    "from " + clientAddress.getHostAddress());
        }
        //endregion

        // new packet received
        else {

            //region ------------ CASE:   LAST STEP OF 3-WAY-HANDSHAKE --------------
            /*  if this package is the ACK of the 3-way-handshake
             *  update the handshake HashMap and ignore the packet
             *  since it only contains the ACK
             *  Wait for the rest communication
             */
            if(handshakes.get(clientAddress) == (byte)1){
                handshakes.put(clientAddress, (byte)0);
                lastSeqNumberReceived.put(clientAddress, packet.getSequenceNumber());
                sendACK(clientAddress, port);
                return;
            }
            //endregion

            // finally here it is a new packet transferring data
            FileOutputStream fileOutputStream = null;
            //region ------------ CASE: FIRST DATA PACKET - ONLY FILENAME --------------
            // the first one will contain the name of the file
            if(!filesIncomplete.containsKey(clientAddress)){
                String fileName = new String(packet.getData());
                File file = new File(fileName);
                filesIncomplete.put(clientAddress, file);
                if (!file.exists()) {
                    try {
                        file.createNewFile();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    fileOutputStream = new FileOutputStream(file);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }

                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //TODO MAKE SURE NO MORE INFO NEEDED TO WRITE IN
                System.out.println("Server: Received first packet informing about the file name" +
                        "from " + clientAddress.getHostAddress() +
                        "\n\t FileName:" + fileName +
                        "\n\t Sedning ACK..." +
                        "\n\t Waiting for the file transfer...");
                lastSeqNumberReceived.put(clientAddress, packet.getSequenceNumber());
                sendACK(clientAddress, port);
            }
            //endregion

            //region ------------ CASE:   MIDDLE NEW DATA PACKET --------------
            else if(packet.isNotLastPacket()){
                try {
                    fileOutputStream = new FileOutputStream(filesIncomplete.get(clientAddress));
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }

                try {
                    fileOutputStream.write(packet.getData());
                    fileOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("SERVER: New Packet Received from "
                        + clientAddress.getHostAddress()  +
                        "\n\t Sending ACK...");
                lastSeqNumberReceived.put(clientAddress, packet.getSequenceNumber());
                sendACK(clientAddress, port);
            }
            //endregion

            //region ------------ CASE:   LAST PACKET --------------
            else {
                filesIncomplete.remove(clientAddress);
                System.out.println("SERVER: All packets from "
                        + clientAddress.getHostAddress() + "received! +" +
                        "\n\t File Transferred!");
                lastSeqNumberReceived.put(clientAddress, packet.getSequenceNumber());
                sendACK(clientAddress, port);
            }
            //endregion
        }


/* WAIT FINISH TRANSFER TO CREATE FILE


 if(!messagesReceived.containsKey(clientAddress) && packet.getSequenceNumber() != 0){
            messagesReceived.put(clientAddress, new ArrayList<>());
        }else{
            ArrayList<Packet> messages = messagesReceived.get(clientAddress);
            messages.add(message);
            messagesReceived.put(clientAddress, messages);
        }

        if (!message.isNotLastPacket()) {

            //region [Concatenate File]

            ArrayList<Packet> messages = messagesReceived.get(message.getAddress());
            messages.sort(Comparator.comparingInt(Packet::getSequenceNumber));

            int totalSize = messages.stream().map(Packet::getPayloadSize).reduce(0, (a , b) -> a + b);
            byte[] fileByteArray = new byte[totalSize];
            messages.parallelStream().forEach(msg -> {
                int startIndex = msg.getSequenceNumber() * msg.getPayloadSize();
                System.arraycopy(msg.getData(), 0, fileByteArray, startIndex, msg.getPayloadSize());
            });

            File file = new File("test");
            FileOutputStream fOut = null;
            try {
                fOut = new FileOutputStream(file);
                fOut.write(fileByteArray);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            finally {
                if(fOut != null){
                    try {
                        fOut.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            //endregion
 */
    }

    @Override
    public void openServer() {
        try {
            serverSocket = new DatagramSocket(getPort());

            System.out.println(getInstanceName() + " with " +
                    getIp() + ":" + getPort() + " opened!");

            //noinspection InfiniteLoopStatement
            while (true) {
                DatagramPacket datagramPacket = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);
                serverSocket.setSoTimeout(0);
                serverSocket.receive(datagramPacket);

                System.out.println("NEW PACKET ");
                // ignore a packet if it is from a client that there is already a packet in line
                if(unhandledPackets.size() != 0) {
                    if(unhandledPacketsIps.contains(datagramPacket.getAddress())){
                        // ignore packet
                        System.out.println("Ignoring this client's packet for now");
                    }
                    else{
                        unhandledPacketsIps.add(datagramPacket.getAddress());
                        ArrayList<DatagramPacket> packages = new ArrayList();
                        packages.add(datagramPacket);
                        unhandledPackets.put(datagramPacket.getAddress(), packages);
                    }
                }
                unhandledPacketsIps.add(datagramPacket.getAddress());
                ArrayList<DatagramPacket> packages = new ArrayList();
                packages.add(datagramPacket);
                unhandledPackets.put(datagramPacket.getAddress(), packages);
                new Thread(this).start();
            }
        }catch(IOException ignored){}
        finally {
            if(serverSocket != null){
                serverSocket.close();
            }
        }
    }
}
