package edu.oswego.cs;

import edu.oswego.cs.network.packets.Packet;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public class Chatroom extends Thread {

    private final String name;
    private ConcurrentHashMap<Integer, ClientConnection> clientConnections;

    public Chatroom(String name) {
        this.name = name;
        this.clientConnections = new ConcurrentHashMap<>();
    }

    @Override
    public void run() {

    }

    public String getChatroomName() {
        return name;
    }

    public void addClientConnection(int port, ClientConnection clientConnection) {
        clientConnections.put(port, clientConnection);
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

}
