package Server;

import javax.swing.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class BaseServer{
    private String ip;
    private int port;
    private JTextArea systemOut;

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

    public JTextArea getSystemOut() {
        return systemOut;
    }

    public void setSystemOut(JTextArea systemOut) {
        this.systemOut = systemOut;
    }

    String getInstanceName(){
        if(this instanceof Server) return "Server";
        return "Client";
    }

    private void CloseConnections(ObjectInputStream in, ObjectOutputStream out){
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

    protected void print(String s){
        if(systemOut == null){
            System.out.println(s);
        }else{
            systemOut.append("\n" + s);
        }
    }

    protected void print(int s){
        if(systemOut == null){
            System.out.println(s);
        }else{
            systemOut.append("\n" + s);
        }
    }

    @Override
    public String toString() {
        return  "**************************************" +
                "\n" + getInstanceName() + ": " +
                "\n\t" + "IP: " + ip + ":" + port +
                "\n" + "**************************************";
    }
}
