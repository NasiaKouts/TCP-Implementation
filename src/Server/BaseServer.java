package Server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramSocket;
import java.net.Socket;

public abstract class BaseServer implements Runnable{
    private String ip;
    private int port;

    protected boolean firstTransfer;

    protected static final int FIRST_PAYLOAD = 4;

    /* Define the socket that receives requests */
    protected DatagramSocket serverSocket;

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public DatagramSocket getServerSocket() {
        return serverSocket;
    }

    public void setServerSocket(DatagramSocket serverSocket) {
        this.serverSocket = serverSocket;
    }

    /**
     * Runnable Implementation
     */
    public abstract void run();

    public abstract void openServer();

    public void closeServer(){
        if(serverSocket != null){
            serverSocket.close();
        }
    }

    protected String getInstanceName(){
        if(this instanceof Server) return "Server";
        return "Client";
    }

    public void CloseConnections(ObjectInputStream in, ObjectOutputStream out){
        try{
            if (in != null) {
                in.close();
            }
            if (out != null){
                out.close();
            }
        }
        catch(IOException ignored){}
    }

    public void CloseConnections(Socket socket, ObjectInputStream in, ObjectOutputStream out){
        try{
            if (socket != null){
                socket.close();
            }
            CloseConnections(in, out);
        }
        catch(IOException ignored){}
    }


    @Override
    public String toString() {
        return  "**************************************" +
                "\n" + getInstanceName() + ": " +
                "\n\t" + "IP: " + ip + ":" + port +
                "\n" + "**************************************";
    }
}
