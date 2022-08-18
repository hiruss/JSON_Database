package client;


import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.gson.Gson;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {

    public static final String SERVER_ADDRESS = "127.0.0.1";
    public static final int SERVER_PORT = 23456;
    public static final String FILE_DIR
            = System.getProperty("user.dir").replace("\\JSON Database\\task", "")
            + "\\JSON Database\\task\\src\\client\\data\\";

    @Parameter(names = {"-t"})
    String type;
    @Parameter(names = {"-k"})
    String key;
    @Parameter(names = {"-v"})
    String value;
    @Parameter(names = {"-in"})
    String file;

    public static void main(String[] args) {

        Main main = new Main();
        JCommander.newBuilder().addObject(main).build().parse(args);
        main.execute();
    }

    private void execute() {

        boolean ignored = new File(FILE_DIR).mkdirs();
        try (Socket socket = new Socket(InetAddress.getByName(SERVER_ADDRESS), SERVER_PORT);
             DataInputStream input = new DataInputStream(socket.getInputStream());
             DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
            System.out.println("Client started!");
            String req;
            if (file != null) {
                req = Files.readString(Path.of(FILE_DIR + file));
            } else {
                Gson gson = new Gson();
                req = gson.toJson(new Request(this.type, this.key, this.value));
            }
            output.writeUTF(req);
            System.out.println("Sent: " + req);
            String resp = input.readUTF();
            System.out.println("Received: " + resp);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class Request {
    String type;
    String key;
    String value;

    Request(String type, String key, String value) {
        this.type = type;
        this.key = key;
        this.value = value;
    }
}
