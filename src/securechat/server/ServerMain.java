package securechat.server;

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class ServerMain {

    private static final int PORT = 43221;

    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();

    public static void main(String[] args){
        new ServerMain().start();
    }
    public void start(){
        System.out.println("Starting chat server on port " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {

        while (true){
            Socket clientSocket = serverSocket.accept();

            ClientHandler handler = new ClientHandler(clientSocket, this);
            clients.add(handler);
            Thread t = new Thread(handler);
            t.start();

            System.out.println("New client connected from" +
                    clientSocket.getRemoteSocketAddress());

        }

    } catch (IOException e) {
        System.err.println("Server exception: " + e.getMessage());
        e.printStackTrace();
    }
}
public void broadcast(String message, ClientHandler sender){
        for (ClientHandler client : clients){
            client.send(message);
        }
}

public void removeClient(ClientHandler ch) {
    clients.remove(ch);
}

static class ClientHandler implements Runnable {
    private final Socket socket;
    private final ServerMain server;
    private PrintWriter out;
    private BufferedReader in;
    private String username = "Anonymous";

    ClientHandler(Socket socket, ServerMain server) {
        this.socket = socket;
        this.server = server;
    }


    @Override
    public void run() {
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println("Enter_USERNAME");
            String nameLine = in.readLine();
            if (nameLine != null && !nameLine.isEmpty()) {
                username = nameLine.trim();
            }
            server.broadcast(username + "has joined the chat.", this);
            System.out.println(username + "joined from" + socket.getRemoteSocketAddress());

            String line;
            while ((line = in.readLine()) != null){
                if (line.equalsIgnoreCase("/quit")) {
                    break;
                }
                String message = username + ": " + line;
                System.out.println("Received: " + message);
                server.broadcast(message, this);
            }

        } catch (IOException e) {
            System.err.println("Connection error with " + username + ": " + e.getMessage());
        } finally {
            try {
                server.removeClient(this);
                server.broadcast(username + "has left the chat.", this);
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException ignored) {

            }
            System.out.println(username + " disconnected.");
        }
    }
    void send(String message){
        if (out != null) out.println(message);
    }
}
}
