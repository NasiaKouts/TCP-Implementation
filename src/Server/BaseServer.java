package Server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public abstract class BaseServer implements Runnable{
    protected String ip;
    protected int port;

    /* Define the socket that receives requests */
    private ServerSocket serverSocket;

    /* Define the socket that is used to handle the connection */
    private Socket clientConn = null;

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

    /**
     * Runnable Implementation
     */
    public abstract void run();

    public void OpenServer(){
        try {
            serverSocket = new ServerSocket(getPort());

            System.out.println(getInstanceName() + " wiht " +
                    getIp() + ":" + getPort() + " opened!");

            //noinspection InfiniteLoopStatement
            while (true) {
                clientConn = serverSocket.accept();

                /*
                    Creates a new thread from the runnable implementation
                    instance and start it. Remember this will call the
                    override method of Worker/Master/Client respectively
                 */
                (new Thread(this)).start();
            }
        }catch(IOException ignored){}
        finally {
            if(serverSocket != null){
                try{
                    serverSocket.close();
                    System.out.println(getInstanceName() + " with " +
                            getIp() + ":" + getPort() + " closed!");
                }catch(IOException ignored){}
            }
        }
    }

    public void CloseServer(){
        if(serverSocket != null){
            try{
                serverSocket.close();
            }catch(IOException ignored){}
        }
    }

    protected String getInstanceName(){
        if(this instanceof Server) return "Server";
        return "Client";
    }

    @Override
    public String toString() {
        return  "**************************************" +
                "\n" + getInstanceName() + ": " +
                "\n\t" + "IP: " + ip + ":" + port +
                "\n" + "**************************************";
    }
}
