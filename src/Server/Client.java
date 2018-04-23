package Server;

public class Client extends BaseServer{
    private String serverIp;
    private int serverPort;
    private String filename;
    private String filePath;
    private int payloadSize;

    public Client(String serverIp, int serverPort, String filename, String filePath, int payloadSize){
        super();
        setServerIp(serverIp);
        setServerPort(serverPort);
        setFilename(filename);
        setFilePath(filePath);
        setPayloadSize(payloadSize);
    }

    public String getServerIp() {
        return serverIp;
    }

    public void setServerIp(String serverIp) {
        this.serverIp = serverIp;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public int getPayloadSize() {
        return payloadSize;
    }

    public void setPayloadSize(int payloadSize) {
        this.payloadSize = payloadSize;
    }

    @Override
    public void run() {

    }
}
