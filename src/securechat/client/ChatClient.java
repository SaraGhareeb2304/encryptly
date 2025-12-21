package securechat.client;

import java.io.*;
import javax.net.ssl.*;

public class ChatClient {

    private static final String HOST = "172.20.10.1";
    private static final int PORT = 43221;

    // Truststore
    private static final String TRUSTSTORE_LOCATION = "src/securechat/client/chatclient.truststore";
    private static final String TRUSTSTORE_PASSWORD = "password";

    private static final boolean ENABLE_AUTH = true;

    private static final String AUTH_PROMPT = "Enter password:";
    private static final String AUTH_OK = "AUTH_OK";
    private static final String AUTH_FAIL = "AUTH_FAIL";


    public static void main(String[] args) {
        new ChatClient().start();
    }

    private void start() {
        System.setProperty("javax.net.ssl.trustStore", TRUSTSTORE_LOCATION);
        System.setProperty("javax.net.ssl.trustStorePassword", TRUSTSTORE_PASSWORD);

        try {
            SSLSocketFactory sf = (SSLSocketFactory) SSLSocketFactory.getDefault();
            try (SSLSocket socket = (SSLSocket) sf.createSocket(HOST, PORT);
                 BufferedReader serverIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter serverOut = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader consoleIn = new BufferedReader(new InputStreamReader(System.in))) {

                socket.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});

                socket.startHandshake();

            /* 1. Handle the username handshake
            the server sends the message "Enter_USERNAME" when we first connect.
            we read that line using serverIn.readLine(). if it matches exactly,
            we ask the user to type a username in the console.

            if the user enters nothing, we use "Anonymous" as a default.
            then we send the chosen username back to the server.
            this lets the server announce us and tag all our messages with our name.
             */

                String firstLine = serverIn.readLine();
                if (firstLine != null && firstLine.trim().equalsIgnoreCase("Enter username:")) {
                    System.out.print("Enter your username: ");
                    String username = consoleIn.readLine();
                    if (username == null || username.trim().isEmpty()) {
                        username = "Anonymous";
                    }
                    serverOut.println(username);

                    if (ENABLE_AUTH) {

                        String authLine = serverIn.readLine();

                        if (authLine != null && authLine.trim().toLowerCase().startsWith("enter password")) {
                            System.out.print("Enter password: ");
                            String password = consoleIn.readLine();
                            if (password == null) password = "";
                            serverOut.println(password);

                            authLine = serverIn.readLine();
                        }

                        if (authLine == null || authLine.trim().equalsIgnoreCase(AUTH_FAIL)) {
                            System.out.println("Authentication failed. Disconnecting.");
                            return; // exits start(), try-with-resources closes socket cleanly
                        }

                        if (!authLine.trim().equalsIgnoreCase(AUTH_OK)) {
                            System.out.println("Unexpected auth response: " + authLine);
                            System.out.println("Disconnecting for safety.");
                            return;
                        }

                        System.out.println("Authenticated! You can now chat.");
                    }

                } else {
                    System.out.println(firstLine == null ? "Server closed connection." : firstLine);
                    return;
                }


            /* 2. Start a thread to listen for messages from the server
             this thread runs in the background and constantly listens for messages
             coming from the server. when the server broadcasts a message,
             this thread immediately prints it on our screen.

             we use a separate thread so that our main program and still read
             user input at the same time (true "real-time" chat).
             marking it as a daemon thread means it automatically closes when
              the main program finishes
             * */

                Thread reader = new Thread(() -> {
                    try {
                        String msg;
                        while ((msg = serverIn.readLine()) != null) {
                            System.out.println(msg);
                        }
                    } catch (IOException e) {
                        System.out.println("Disconnected from server.");
                    }
                });
                reader.setDaemon(true);
                reader.start();


            /* 3. main thread : read from console and send to server
             this part waits for the user to type something into the console.
             every line typed is sent to the server using serverOut.println().
             if the user types "/quit", we break out of the loop and disconnect.

             meanwhile. the reader thread is still printing incoming messages.
             this lets sending and receiving happen at the same time.
             * */

                System.out.println("Type messages and press Enter. Type /quit to leave.");

                String line;
                while ((line = consoleIn.readLine()) != null) {
                    serverOut.println(line);
                    if ("/quit".equalsIgnoreCase(line.trim())) {
                        break;
                    }
                }

            }
        } catch (SSLHandshakeException e) {
            System.err.println("SSL handshake failed (truststore/certificate issue): " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Could not connect to server: " + e.getMessage());
        }
    }
}



