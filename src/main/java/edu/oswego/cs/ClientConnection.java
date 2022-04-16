package edu.oswego.cs;

import edu.oswego.cs.network.packets.Packet;
import edu.oswego.cs.network.packets.ParticipantACK;
import edu.oswego.cs.network.packets.ParticipantData;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

public class ClientConnection extends Thread {
    private final int PORT;
    private ServerSocket serverSocket;
    private Socket socket;
    private int MAX_BUFFER = 1024;
    private final VoicechatServer voicechatServer;

    public ClientConnection(int port, VoicechatServer voicechatServer) {
        this.PORT = port;
        this.voicechatServer = voicechatServer;
    }

    private void participantRequest(ParticipantData participantData) throws IOException {
        switch (participantData.getParticipantOpcode()) {

            case CREATE_SERVER: voicechatServer.createChatroom(participantData.getParams()[0]);
            case LIST_SERVERS: {
                ParticipantACK participantACK = new ParticipantACK(
                        participantData.getParticipantOpcode(),
                        PORT,
                        voicechatServer.getChatrooms());
                socket.getOutputStream().write(participantACK.getBytes());
            }
        }
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(PORT);
            socket = serverSocket.accept();
            InputStream in = socket.getInputStream();
            while(socket.isConnected()) {

                byte[] buffer = new byte[MAX_BUFFER];
                in.read(buffer, 0, buffer.length);

                new Thread( () -> {
                    Packet packet = Packet.parse(buffer);
                    if (packet == null) return ;
                    try {
                        switch (packet.getOpcode()) {
                            case PARTICIPANT: participantRequest((ParticipantData) packet);
                        }
                    } catch (Exception e) {}
               }).start();

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getPort() {
        return PORT;
    }


}
