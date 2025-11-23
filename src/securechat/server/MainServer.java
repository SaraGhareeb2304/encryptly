package securechat.server;

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;

public class MainServer {

    public static final String KEYSTORE_LOCATION = "src/securechat/server/chatserver.keystore";
    public static final String KEYSTORE_PASSWORD = "password";
    public static final boolean DEBUG = false;

    private static final int PORT = 43221;

    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) {
        new MainServer().start();
    }

    public void start() {
        System.out.println("Starting chat server on port " + PORT);
        try {
            System.setProperty("javax.net.ssl.keyStore", KEYSTORE_LOCATION);
            System.setProperty("javax.net.ssl.keyStorePassword", KEYSTORE_PASSWORD);

            if (DEBUG) {
                System.setProperty("javax.net.debug", "all");
            }

            SSLServerSocketFactory ssf =
                    (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
            SSLServerSocket serverSocket =
                    (SSLServerSocket) ssf.createServerSocket(PORT);
            serverSocket.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});

            while (true) {
                SSLSocket clientSocket = (SSLSocket) serverSocket.accept();

                ClientHandler handler = new ClientHandler(clientSocket, this);
                clients.add(handler);

                Thread t = new Thread(handler);
                t.start();

                System.out.println("New client connected from" +
                        clientSocket.getRemoteSocketAddress());

            }

        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void broadcast(String message, ClientHandler sender) {
        for (ClientHandler client : clients) {
            client.send(message);
        }
    }

    public void removeClient(ClientHandler ch) {

        clients.remove(ch);
    }

    static class ClientHandler implements Runnable {
        private final SSLSocket socket;
        private final MainServer server;
        private PrintWriter out;
        private BufferedReader in;
        private String username = "Anonymous";

        ClientHandler(SSLSocket socket, MainServer server) {
            this.socket = socket;
            this.server = server;
        }


        @Override
        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                out.println("Enter username: ");
                String nameLine = in.readLine();

                if (nameLine != null && !nameLine.isEmpty()) {
                    username = nameLine.trim();
                }
                server.broadcast(username + " has joined the chat.", this);
                System.out.println(username + " joined from" + socket.getRemoteSocketAddress());

                String line;
                while ((line = in.readLine()) != null) {
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
                    server.broadcast(username + " has left the chat.", this);
                    if (socket != null && !socket.isClosed()) socket.close();
                } catch (IOException ignored) {

                }
                System.out.println(username + " disconnected.");
            }
        }

        void send(String message) {
            if (out != null) out.println(message);
        }
    }
}

