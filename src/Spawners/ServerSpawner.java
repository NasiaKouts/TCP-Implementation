package Spawners;

import Server.Server;
import Utils.NetworkUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ServerSpawner {
    public static void main(String args[]){
        new Server("localhost", 4200);

        if(args.length == 0) {
            BufferedReader in;
            try {
                in = new BufferedReader(new InputStreamReader(System.in));

                System.out.println("Set the ip the server should listen to" +
                        "\n (Current IP: " +  NetworkUtils.GetCurrentAddress() + "):");
                String ip = in.readLine();

                System.out.println("Set the port the server should listen to" +
                        "\n (Available Port: " + NetworkUtils.GetNextAvailablePort() + "):");
                int port = Integer.parseInt(in.readLine());

                new Server(ip, port);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else {
            new Server(args[0], Integer.parseInt(args[1]));
        }

    }
}
