package server;

import com.google.gson.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Main {

    public static final String SERVER_ADDRESS = "127.0.0.1";
    public static final int SERVER_PORT = 23456;
    public static final String FILE_DIR
            = System.getProperty("user.dir").replace("\\JSON Database\\task", "")
            + "\\JSON Database\\task\\src\\server\\data\\db.json";
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    private Response process(Request request) throws IOException {
        final Gson gson = new Gson();
        final Path filePath = Path.of(FILE_DIR);
        switch (request.type) {
            case "set" -> {
                synchronized (lock.writeLock()) {
                    JsonObject db = JsonParser.parseString(Files.readString(filePath)).getAsJsonObject();
                    findTargetNode(db, request.key, request.value);
                    Files.writeString(filePath, gson.toJson(db));
                }
                return new Response("OK", null, null);
            }
            case "get" -> {
                Response resp = new Response("ERROR", "No such key", null);
                synchronized (lock.readLock()) {
                    JsonObject db = JsonParser.parseString(Files.readString(filePath)).getAsJsonObject();
                    JsonElement value = findTargetNode(db, request.key, false);
                    if (value != null) {
                        resp = new Response("OK", null, value);
                    }
                }
                return resp;
            }
            case "delete" -> {
                Response resp = new Response("OK", null, null);
                synchronized (lock.writeLock()) {
                    JsonObject db = JsonParser.parseString(Files.readString(filePath)).getAsJsonObject();
                    JsonElement value = findTargetNode(db, request.key, true);
                    Files.writeString(filePath, gson.toJson(db));
                }
                return resp;
            }
        }
        return new Response("ERROR", null, null);
    }

    private JsonElement findTargetNode(JsonObject dbNode, JsonElement key, boolean delete) {
        if (key.isJsonArray()) {
            JsonArray jsonArray = key.getAsJsonArray();
            int lastIndex = jsonArray.size() - 1;
            key = jsonArray.get(lastIndex);
            for (int i = 0; i < lastIndex && dbNode != null; i++) {
                dbNode = dbNode.getAsJsonObject(jsonArray.get(i).getAsString());
            }
        }
        if (dbNode == null) return null;
        if (delete) return dbNode.remove(key.getAsString());
        return dbNode.get(key.getAsString());
    }

    private void findTargetNode(JsonObject dbNode, JsonElement key, JsonElement setValue) {
        if (key.isJsonArray()) {
            JsonArray jsonArray = key.getAsJsonArray();
            int lastIndex = jsonArray.size() - 1;
            key = jsonArray.get(lastIndex);
            for (int i = 0; i < lastIndex; i++) {
                String keyValue = jsonArray.get(i).getAsString();
                if (!dbNode.has(keyValue)) {
                    dbNode.add(keyValue, new JsonObject());
                }
                dbNode = dbNode.getAsJsonObject(keyValue);
            }
        }
        dbNode.add(key.getAsString(), setValue);
    }

    private void init() {
        try (ServerSocket server = new ServerSocket(SERVER_PORT, 50, InetAddress.getByName(SERVER_ADDRESS))) {
            System.out.println("Server started!");
            File file = new File(FILE_DIR);
            if (!file.exists() && file.getParentFile().mkdirs() && file.createNewFile()) {
                Files.writeString(Path.of(FILE_DIR), "{}");
            }
            Gson gson = new Gson();
            while (!pool.awaitTermination(100, TimeUnit.MILLISECONDS)) {
                try {
                    Socket socket = server.accept();
                    pool.submit(() -> {
                        try (DataInputStream input = new DataInputStream(socket.getInputStream());
                             DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
                            String req = input.readUTF();
                            Request request = gson.fromJson(req, Request.class);
                            if (request.type.equals("exit")) {
                                pool.shutdown();
                                output.writeUTF(gson.toJson(new Response("OK", null, null)));
                            } else {
                                Response resp = process(request);
                                output.writeUTF(gson.toJson(resp));
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            pool.shutdown();
        }
    }

    public static void main(String[] args) {

        new Main().init();
    }
}


class Request {
    String type;
    JsonElement key;
    JsonElement value;
}

class Response {
    String response;
    String reason;
    JsonElement value;

    Response(String response, String reason, JsonElement value) {
        this.response = response;
        this.reason = reason;
        this.value = value;
    }
}
