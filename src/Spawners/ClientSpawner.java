package Spawners;

import Server.Client;
import Utils.NetworkUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ClientSpawner {
    public static void main(String args[]){
        if(args.length == 0) {
            new Client("192.168.1.13", 4200, "test.txt", "E:\\", 65000);


            BufferedReader in;
            try {
                in = new BufferedReader(new InputStreamReader(System.in));

                System.out.println("Set the ip the server should connect to" +
                        "\n (Current IP: " +  NetworkUtils.GetCurrentAddress() + "):");
                String serverIp = in.readLine();

                System.out.println("Set the port the server should listen to" +
                        "\n (Available Port: " + NetworkUtils.GetNextAvailablePort() + "):");
                int serverPort = Integer.parseInt(in.readLine());

                System.out.println("Enter the name of the file to be transferred");
                String filename = in.readLine();

                System.out.println("Enter the path of the folder containing the file to be transferred");
                String filePath = in.readLine();

                System.out.println("Set the payload size");
                int payloadSize = Integer.parseInt(in.readLine());

                new Client(serverIp, serverPort, filename, filePath, payloadSize);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else {
            new Client(args[0], Integer.parseInt(args[1]), args[2], args[3], Integer.parseInt(args[4]));
        }
    }
}
