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
     * Packet handler for Participant Data packets depending on the participant opcode .
     * @param participantData Packet that contains participant data requests
     * @throws IOException If ACK can not be sent back to the client
     */
    private void participantRequest(ParticipantData participantData) throws IOException {
        // switch for all opcodes for participant data packets
        switch (participantData.getParticipantOpcode()) {

            case CREATE_SERVER: createChatroomRequest(participantData);
            case LIST_SERVERS: listChatroomsRequest(participantData);
            case JOIN: joinChatroomRequest(participantData);
            case LEAVE: leaveChatroomRequest();
        }
    }

    private void listChatroomsRequest(ParticipantData participantData) throws IOException {
        ParticipantACK participantACK = new ParticipantACK(
                participantData.getParticipantOpcode(),
                PORT,
                voicechatServer.getChatrooms());
        socket.getOutputStream().write(participantACK.getBytes());
    }

    private void leaveChatroomRequest() throws IOException {
        if (chatroom != null) {
            chatroom.removeClientConnection(PORT);
            voicechatServer.displayInfo("PORT " + PORT + " Has Left Chatroom:\t" + this.chatroom.getChatroomName());
            chatroom = null;
        }
    }

    private void joinChatroomRequest(ParticipantData participantData) throws IOException {
        try {
            System.out.println("THESE ARE THE PARAMS: " + Arrays.toString(participantData.getParams()));
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

    private void createChatroomRequest(ParticipantData participantData) throws IOException {
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
        voicechatServer.createChatroom(serverName, numberOfParticipants);
    }

    private void debugRequest(DebugPacket debugPacket) {
        voicechatServer.displayInfo("Debug Message From PORT " + PORT + ":\t"+ debugPacket.getMsg());
        if (! (chatroom == null)) {
            chatroom.broadcastPacketToChatroom(debugPacket, this);
        }
    }

    private void parsePacket(Packet packet) {
        if (packet == null) return;
        try {
            switch (packet.getOpcode()) {
                case PARTICIPANT: participantRequest((ParticipantData) packet);
                case DEBUG:       debugRequest((DebugPacket) packet);
            }
        } catch (Exception e) {}
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
                    Packet packet = Packet.parse(buffer);
                    if (packet == null) return ;
                    parsePacket(packet);
               }).start();

            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
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
