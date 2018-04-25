package Utils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Random;

public class NetworkUtils {
    /**
     * Returns the current IP Address
     * @return the current IP Address
     */
    public static String GetCurrentAddress(){
        String currentIp = "";
        try {
            currentIp = InetAddress.getLocalHost().getHostAddress();
        }
        catch (IOException ignored) {}

        return currentIp;
    }

    /**
     * Returns an available port
     * @return an available port
     */
    public static int GetNextAvailablePort(){
        Random random = new Random(System.currentTimeMillis());
        while(true){
            ServerSocket socketConn = null;
            try{
                int port = random.nextInt(25000);
                if(port < 16000) continue;

                socketConn = new ServerSocket(port);
                return port;
            }
            catch(IOException ignored){ }
            finally{
                if(socketConn != null){
                    try{
                        socketConn.close();
                    }
                    catch(IOException ignored){ }
                }
            }
        }
    }

    /**\
     * /**
     * Calculates the 16-bit 1's complement checksum.
     * @param buffer : The buffer to which we are going to calculate the checksum.
     *
     * Returns the checksum of the given buffer
     * @return the checksum of the given buffer
     */
    public static long calculateCheckSum(byte[] buffer) {
        int length = buffer.length;
        int i = 0;

        long sum = 0;
        long data;

        // Handle all pairs
        while (length > 1) {
            data = (((buffer[i] << 8) & 0xFF00) | ((buffer[i + 1]) & 0xFF));
            sum += data;
            // 1's complement carry bit correction in 16-bits (detecting sign extension)
            if ((sum & 0xFFFF0000) > 0) {
                sum = sum & 0xFFFF;
                sum += 1;
            }

            i += 2;
            length -= 2;
        }

        // Handle remaining byte in odd length buffers
        if (length > 0) {
            // Corrected to include @Andy's edits and various comments on Stack Overflow
            sum += (buffer[i] << 8 & 0xFF00);
            // 1's complement carry bit correction in 16-bits (detecting sign extension)
            if ((sum & 0xFFFF0000) > 0) {
                sum = sum & 0xFFFF;
                sum += 1;
            }
        }

        // Final 1's complement value correction to 16-bits
        sum = ~sum;
        sum = sum & 0xFFFF;
        return sum;
    }
}
