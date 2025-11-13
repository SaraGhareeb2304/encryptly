package securechat.client;

import java.io.*;
import java.net.Socket;

public class ChatClient {

    private static final String HOST = "localhost";
    private static final int PORT = 43221;

    public static void main(String[] args) {
        new ChatClient().start();
    }

    private void start() {
        try (Socket socket = new Socket(HOST, PORT);
             BufferedReader serverIn = new BufferedReader(
                     new InputStreamReader(socket.getInputStream()));
             PrintWriter serverOut = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader consoleIn = new BufferedReader(
                     new InputStreamReader(System.in))) {

            /* 1. Handle the username handshake
            the server sends the message "Enter_USERNAME" when we first connect.
            we read that line using serverIn.readLine(). if it matches exactly,
            we ask the user to type a username in the console.

            if the user enters nothing, we use "Anonymous" as a default.
            then we send the chosen username back to the server.
            this lets the server announce us and tag all our messages with our name.
             */

            String firstLine = serverIn.readLine();
            if ("Enter_Username".equals(firstLine)) {
                System.out.print("Enter your username: ");
                String username = consoleIn.readLine();
                if (username == null || username.trim().isEmpty()) {
                    username = "Anonymous";
                }
                serverOut.println(username);
            } else if (firstLine != null) {
                // just in case, print anything unexpected from server
                System.out.print(firstLine);
            }

            /* 2. Start a threat to listen for messages from the server
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
             if the user types "/quits", we break out of the loop and disconnect.

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


            /* 4.automatic cleanup on disconnect
            this entire method uses a try-with-resources block, so when we break
            out of the loop above, java automatically closes:
            -> the socket connection / the input stream (serverIn)
             / the output stream (serverOut) / the console reader (consoleIn).

             closing the socket cleanly signals to the server that we have left the chat
             the catch block only runs if there was an error connecting initially
             */

        } catch (IOException e) {
            System.err.println("Could not connect to server: " + e.getMessage());
        }
    }
}

