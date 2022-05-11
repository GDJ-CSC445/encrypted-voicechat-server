package edu.oswego.cs;

import edu.oswego.cs.network.opcodes.PacketOpcode;
import edu.oswego.cs.network.packets.Packet;
import edu.oswego.cs.network.packets.SoundData;
import edu.oswego.cs.network.packets.SoundPacket;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class Chatroom extends Thread {

    private final String name;
    private ConcurrentHashMap<Integer, ClientConnection> clientConnections;
    private final int maxParticipants;

    public Chatroom(String name, int maxParticipants) {
        this.name = name;
        this.clientConnections = new ConcurrentHashMap<>();
        this.maxParticipants = maxParticipants;
    }

    @Override
    public void run() {

    }

    public List<ClientConnection> getClientConnections() {
        return new ArrayList<>(clientConnections.values());
    }

    public String getChatroomName() {
        return name;
    }

    public int getMaxParticipants() {
        return maxParticipants;
    }

    public void addClientConnection(int port, ClientConnection clientConnection) {
        clientConnections.put(port, clientConnection);
    }

    public int getChatroomSize() {
        return clientConnections.size();
    }

    public void removeClientConnection(int port) {
        clientConnections.remove(port);
    }

    public void broadcastPacketToChatroom(Packet packet, ClientConnection clientConnection) {
        clientConnections.forEach( (port, client) -> {
            if (port != clientConnection.getPort()) {
                try  {
                    client.sendPacketToClient(packet);
                } catch (IOException e) {e.printStackTrace();}
            }
        } );
    }

    public void broadcastSoundDataToChatroom(byte[] soundData, ClientConnection clientConnection) {
        clientConnections.forEach( (port, client) -> {
            if (port != clientConnection.getPort()) {
                try {
                    SoundPacket s = new SoundPacket(PacketOpcode.SACK, clientConnection.getPort());
                    client.sendPacketToClient(s);
                    Thread.sleep(1000);
                    ObjectOutputStream objOut = new ObjectOutputStream(client.getSocket().getOutputStream());
                    objOut.writeObject(soundData);
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } );
    }

}
