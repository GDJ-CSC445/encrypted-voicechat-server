package edu.oswego.cs;

import edu.oswego.cs.network.opcodes.ErrorOpcode;
import edu.oswego.cs.network.packets.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

/**
 * Handler for client connection requests on a new thread
 */
public class ClientConnection extends Thread {
    private final int PORT;
    private ServerSocket serverSocket;
    private Socket socket;
    private int MAX_BUFFER = 1024;
    // reference to the main client connection handler - mainly for the concurrent map of client connections
    private final VoicechatServer voicechatServer;
    private Chatroom chatroom = null;

    public ClientConnection(int port, VoicechatServer voicechatServer) {
        this.PORT = port;
        this.voicechatServer = voicechatServer;
    }

    /**
     * Packet Parsing for the packet to be used as intended
     * @param packet A packet object that contains an opcode as the first two bytes.
     */
    private void parsePacket(Packet packet) {
        if (packet == null) return;
        try {
            switch (packet.getOpcode()) {
                case PARTICIPANT: participantRequest((ParticipantData) packet);
                case DEBUG:       debugRequest((DebugPacket) packet);
            }
        } catch (Exception e) {}
    }

    /**
     * Packet handler for Participant Data packets depending on the participant opcode .
     * @param participantData Packet that contains participant data requests
     * @throws IOException If ACK can not be sent back to the client
     */
    private void participantRequest(ParticipantData participantData) throws IOException {
        // switch for all opcodes for participant data packets
        switch (participantData.getParticipantOpcode()) {

            case CREATE_SERVER: {
                createChatroomRequest(participantData);
                break;
            }
            case LIST_SERVERS: {
                listChatroomsRequest(participantData);
                break;
            }
            case JOIN: {
                joinChatroomRequest(participantData);
                break;
            }
            case LEAVE: {
                leaveChatroomRequest();
                break;
            }
        }
    }

    /**
     * Packet handler for listing all chatrooms on the server
     * @param participantData Incoming packet request with opcode LIST
     * @throws IOException If the ACK cannot be sent back to the client
     */
    private void listChatroomsRequest(ParticipantData participantData) throws IOException {
        ParticipantACK participantACK = new ParticipantACK(
                participantData.getParticipantOpcode(),
                PORT,
                voicechatServer.getChatrooms());
        socket.getOutputStream().write(participantACK.getBytes());
    }

    /**
     * Packet handler for leaving a chatroom.
     * Contains the opcode LEAVE
     */
    private void leaveChatroomRequest() {
        if (chatroom != null) {
            chatroom.removeClientConnection(PORT);
            voicechatServer.displayInfo("PORT " + PORT + " Has Left Chatroom:\t" + this.chatroom.getChatroomName());
            chatroom = null;
        }
    }

    /**
     * Packet handler for joining a chatroom
     * @param participantData Incoming packet request with opcode JOIN
     * @throws IOException
     */
    private void joinChatroomRequest(ParticipantData participantData) throws IOException {
        try {
            if (chatroom == null) {

                if (participantData.getParams().length == 0) {
                    ErrorPacket errorPacket = new ErrorPacket(ErrorOpcode.CHATROOM_DNE);
                    socket.getOutputStream().write(errorPacket.getBytes());
                    return;
                }
                Chatroom chatroom = voicechatServer.findChatroomByName(participantData.getParams()[0]);
                if (chatroom == null) {
                    ErrorPacket errorPacket = new ErrorPacket(ErrorOpcode.CHATROOM_DNE);
                    socket.getOutputStream().write(errorPacket.getBytes());
                    return;
                }
                if (chatroom.getMaxParticipants() <= chatroom.getChatroomSize()) {
                    ErrorPacket errorPacket = new ErrorPacket(ErrorOpcode.CHATROOM_FULL);
                    socket.getOutputStream().write(errorPacket.getBytes());
                    return;
                }
                chatroom.addClientConnection(PORT, this);
                this.chatroom = chatroom;
                voicechatServer.displayInfo("PORT " + PORT + " Has Joined Chatroom:\t" + this.chatroom.getChatroomName());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Packet Handler for creating a chatroom
     * @param participantData Incoming packet request with opcode CREATE
     * @throws IOException If an ack packet cannot be sent back to the client
     */
    private void createChatroomRequest(ParticipantData participantData) throws IOException {
        // Most of the content in this function is error handling
        System.out.println(Arrays.toString(participantData.getParams()));
        if (participantData.getParams().length == 0){
            ErrorPacket errorPacket = new ErrorPacket(ErrorOpcode.UNDEF, "No parameters specified in the packet.");
            socket.getOutputStream().write(errorPacket.getBytes());
        }
        String serverName = participantData.getParams()[0];
        if (Arrays.asList(voicechatServer.getChatrooms()).contains(serverName)) {
            ErrorPacket errorPacket = new ErrorPacket(ErrorOpcode.CHATROOM_EXISTS, "Chatroom name: " + serverName + " already exists.");
            socket.getOutputStream().write(errorPacket.getBytes());
            return;
        }
        if (participantData.getParams().length == 1) {
            ErrorPacket errorPacket = new ErrorPacket(ErrorOpcode.UNDEF, "No parameter specifying the number of participants.");
            socket.getOutputStream().write(errorPacket.getBytes());
            return;
        }
        int numberOfParticipants;
        try {
            numberOfParticipants = Integer.parseInt(participantData.getParams()[1]);
        } catch (Exception e) {
            ErrorPacket errorPacket = new ErrorPacket(ErrorOpcode.UNDEF, "Can not cast number of participants (expected in parameters[2]) to a integer value.");
            socket.getOutputStream().write(errorPacket.getBytes());
            return;
        }

        // If no errors, then finally create the server
        voicechatServer.createChatroom(serverName, numberOfParticipants);
    }

    /**
     * Debugging functionality that was made pre-gui.
     * @param debugPacket Contains the opcode DEBUG and has a msg attached to the packet
     */
    private void debugRequest(DebugPacket debugPacket) {
        voicechatServer.displayInfo("Debug Message From PORT " + PORT + ":\t"+ debugPacket.getMsg());
        if (! (chatroom == null)) chatroom.broadcastPacketToChatroom(debugPacket, this);
    }



    public void sendPacketToClient(Packet packet) throws IOException {
        socket.getOutputStream().write(packet.getBytes());
    }

    /**
     * Thread runner for reading incoming TCP/IP voicechat packets from client
     */
    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(PORT);
            socket = serverSocket.accept();
            InputStream in = socket.getInputStream();
            while(socket.isConnected()) {

                byte[] buffer = new byte[MAX_BUFFER];
                // ready to accept a new packet at any time - blocks until a packet is received
                if (-1 == in.read(buffer, 0, buffer.length))
                    break;

                // spawns a new thread to parse packet
                new Thread( () -> {
                    // gets a packet object from the buffer received through TCP/IP
                    Packet packet = Packet.parse(buffer);
                    if (packet == null) return ;
                    // calls the respective method to handle each packet received
                    parsePacket(packet);
               }).start();

            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Always try to close the TCP/IP connection if not being used
            voicechatServer.displayInfo("Client on port " + PORT + " has disconnected.");
            try {
                voicechatServer.removeConnection(PORT);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public int getPort() {
        return PORT;
    }

    public Socket getSocket() {
        return socket;
    }

    public void setChatroom(Chatroom chatroom) {
        this.chatroom = chatroom;
    }

    public void leaveChatroom() {
        this.chatroom = null;
    }

}
