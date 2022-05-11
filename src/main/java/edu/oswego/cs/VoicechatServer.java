package edu.oswego.cs;

import edu.oswego.cs.network.packets.EndPacket;
import edu.oswego.cs.network.packets.Packet;
import edu.oswego.cs.network.packets.SoundData;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Main Voice chat server that handles all incoming connection requests from clients
 */
public class VoicechatServer {
    private final String HOST;
    private final int PORT;
    private int CONNECTION_PORT;

    private int chatroomCount = 0;
    // Concurrent map to keep track of ports and current opened client connections
    public static ConcurrentHashMap<Integer, ClientConnection> clientConnections = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<Integer, Chatroom> chatrooms = new ConcurrentHashMap<>();

    private static ServerSocket serverSocket;

    private static final String TEXT_GREEN = "\u001B[32m";
    public static final String TEXT_RED = "\u001B[31m";
    private static final String TEXT_RESET = "\u001B[0m";

    public VoicechatServer(String host, int port, int connectionStartingPort) {
        this.HOST = host;
        this.PORT = port;
        this.CONNECTION_PORT = connectionStartingPort;
        displayServerStartup();
    }

    /**
     * Handler for client connection requests. Multithreaded - when a client connects using TCP/IP, the server handler
     * passes the request to another thread and gets ready to accept another client request.
     * @throws IOException Cannot open server on port
     */
    public void start() throws IOException {
         serverSocket = new ServerSocket(PORT);

         new Thread( () -> {
             while (true) {
                 Scanner scanner = new Scanner(System.in);
                 String userIn = scanner.nextLine();

                 if (userIn.startsWith("-a")) {
                     for (Chatroom chatroom : chatrooms.values()) {
                         System.out.println(chatroom.getChatroomName() + "(" + chatroom.getChatroomSize() + "/" + chatroom.getMaxParticipants() + ")");
                         for (ClientConnection connection : chatroom.getClientConnections()) {
                             System.out.println("\t" + connection.getPort());
                         }
                     }
                 }
                 else if (userIn.startsWith("-c")) {
                     chatrooms = new ConcurrentHashMap<>();
                 }
             }
         } ).start();

        // Forever loop to grab every possible connection
        for (;;CONNECTION_PORT++) {
            Socket clientSocket = serverSocket.accept();
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            // opens a new server socket on a new port
            List<Integer> usedPorts = new ArrayList<>(clientConnections.keySet());
            Collections.sort(usedPorts);

            int reusedPort = -1;
            for (int portIndex = 0; portIndex < usedPorts.size(); portIndex++) {
                if (portIndex == usedPorts.size() - 1) continue;
                if (usedPorts.get(portIndex) == usedPorts.get(portIndex + 1) - 1 ) continue;
                reusedPort = usedPorts.get(portIndex) + 1;
                break;
            }

            ClientConnection connection = (reusedPort == -1) ?
                    new ClientConnection(CONNECTION_PORT, this) :
                    new ClientConnection(reusedPort, this);

            if (reusedPort == -1) {
                clientConnections.put(CONNECTION_PORT, connection);
                out.println(CONNECTION_PORT);
            }
            else {
                clientConnections.put(reusedPort, connection);
                out.println(reusedPort);
                CONNECTION_PORT --;
            }
            // messages the client a new port to talk to the server on
            displayInfo("New connection on PORT:\t" + connection.getPort());
            connection.start();
            clientSocket.close();
        }
    }

    /**
     * Functionality to allow participants to create chatrooms
     * @param name Name of the requested new chatroom
     * @param numberOfParticipants Number of participants allowed in the chatroom
     */
    public void createChatroom(String name, int numberOfParticipants) {
        // check if any of the chatrooms that currently exists have the same name
        if (! chatrooms.values().stream()
                .map(Chatroom::getChatroomName)
                .anyMatch(cname -> cname.equals(name))) {

            chatrooms.put(chatroomCount, new Chatroom(name, numberOfParticipants));
            chatroomCount++;
            displayInfo("Chatroom Created: " + name);
            return;
        }
        displayError("Chatroom: " + name + " already exists.");
    }

    /**
     * @return Gets all chatrooms active on the server
     */
    public String[] getChatrooms() {
        ArrayList<String> chatroomNames = new ArrayList<>() ;
        chatrooms.forEach( (index, name) -> {
            chatroomNames.add(name.getChatroomName() + ";" + name.getChatroomSize() + "/" + name.getMaxParticipants());
        });
        return chatroomNames.toArray(new String[0]);
    }

    /**
     * Allows a chatroom to be found by its name
     * @param chatroomName The requested chatroom name
     * @return If the chatroom is found or else NULL
     */
    public Chatroom findChatroomByName(String chatroomName) {
        Optional<Chatroom> chatroom = chatrooms.values().stream()
                .filter(room -> room.getChatroomName().equals(chatroomName))
                .findFirst();
        return chatroom.orElse(null);
    }

    /**
     * Handler for any disconnects from the client
     * @param port Port being disconnected
     * @throws IOException If socket cannot be closed
     */
    public void removeConnection(int port) throws IOException {
        clientConnections.get(port).getSocket().close();
        clientConnections.remove(port);
    }

    // Main entry point for the server. Establishes .ENV variables and some other error handling
    public static void main( String[] args ) {

        Dotenv env = null;
        try {
            env = Dotenv.load();
        } catch (Exception ignored) {
            System.out.println("No .env file found.");
            System.exit(1);
        }
        try {
            String HOST = env.get("HOST");
            int PORT = Integer.parseInt(env.get("SERVER_PORT"));
            int STARTING_PORT = Integer.parseInt(env.get("CONNECTION_STARTING_PORT"));

            if (! HOST.equals("localhost") && ! HOST.contains(InetAddress.getLocalHost().getHostName())) {
                System.out.println(TEXT_RED + "[ERROR]" + TEXT_RESET + " Host name in .env does not match server host name.");
                System.exit(1);
            }

            SIGINTHandler();

            VoicechatServer server = new VoicechatServer(HOST, PORT, STARTING_PORT);

            server.start();
//            HashMap<Integer, SoundData> soundDataPackets = new HashMap<>();
//            ServerSocket serverSocket = new ServerSocket(1500);
//            Socket socket = serverSocket.accept();
//            System.out.println("CONNECTED");
//            Packet packet = null;
//            while (true) {
//                byte[] buffer = new byte[2000];
//                socket.getInputStream().read(buffer);
//
//                System.out.println(Arrays.toString(buffer));
//                packet = Packet.parse(buffer);
//                if (packet instanceof SoundData) {
//                    SoundData soundData = (SoundData) packet;
//                    System.out.println(soundData.getSequenceNumber());
//                    soundDataPackets.put(soundData.getSequenceNumber(), soundData);
//                }
//                else if (packet instanceof EndPacket || buffer[1] == 0) {
//                    break;
//                }
//
////                Thread.sleep(1000);
            //}

//            File outputFile = new File("incoming_file.wav");
//            try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
//                soundDataPackets.forEach( (k,v) -> {
//                    try {
//                        outputStream.write(v.getData());
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                } );
//            }

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Environment variables are empty.");
            System.exit(1);
        }
    }


    public static void displayInfo(String msg) {
        System.out.println(TEXT_GREEN + "[INFO]" + TEXT_RESET + " " + msg);
    }

    public static void displayError(String error) {
        System.out.println(TEXT_RED + "[ERROR]" + TEXT_RESET + " " + error);
    }

    private void displayServerStartup() {
        System.out.println(" __ __   ___  ____   __    ___         __  __ __   ____  ______       _____   ___  ____  __ __    ___  ____  \n" +
                "|  |  | /   \\|    | /  ]  /  _]       /  ]|  |  | /    ||      |     / ___/  /  _]|    \\|  |  |  /  _]|    \\ \n" +
                "|  |  ||     ||  | /  /  /  [_       /  / |  |  ||  o  ||      |    (   \\_  /  [_ |  D  )  |  | /  [_ |  D  )\n" +
                "|  |  ||  O  ||  |/  /  |    _]     /  /  |  _  ||     ||_|  |_|     \\__  ||    _]|    /|  |  ||    _]|    / \n" +
                "|  :  ||     ||  /   \\_ |   [_     /   \\_ |  |  ||  _  |  |  |       /  \\ ||   [_ |    \\|  :  ||   [_ |    \\ \n" +
                " \\   / |     ||  \\     ||     |    \\     ||  |  ||  |  |  |  |       \\    ||     ||  .  \\\\   / |     ||  .  \\\n" +
                "  \\_/   \\___/|____\\____||_____|     \\____||__|__||__|__|  |__|        \\___||_____||__|\\_| \\_/  |_____||__|\\_|\n" +
                "                                                                                                             ");
        System.out.println("===============================================================================================================\n");

        displayInfo("SERVER HOST:\t" + this.HOST);
        displayInfo("SERVING ON PORT:\t" + this.PORT);
        System.out.println();
    }

    /**
     *  Custom SIGINT For MACOS and Linux
     */
    private static void SIGINTHandler() {
        if (System.getProperty("os.name").equals("Mac OS X") ||
                System.getProperty("os.name").equals("Linux")) {
            Thread CUSTOM_SIGINT = new Thread( () -> {
                clientConnections.forEach( (port, connection) -> {
                    try {
                        connection.getSocket().close();
                        serverSocket.close();
                    }
                    catch (IOException ignored) {}
                } );
            });

            Runtime.getRuntime().addShutdownHook(CUSTOM_SIGINT);
        }
    }



}
