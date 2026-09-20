package cn.starry.narcissus.functions.packets.in;

import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.packets.PacketSerializer;

import java.io.IOException;

/** Receives proxy-to-backend plugin payloads, including forwarded skin properties. */
public class PacketInPluginMessage implements PacketIn {
    private String channel;
    private byte[] data;

    @Override
    public void readPacket(PacketSerializer packetSerializer) throws IOException {
        this.channel = packetSerializer.readString();
        this.data = packetSerializer.readBytes(packetSerializer.available());
    }

    @Override
    public void handlePacket(NetworkManager networkManager, PlayerManager playerManager) {
        networkManager.handlePluginMessage(playerManager, this.channel, this.data);
    }

    public String getChannel() {
        return this.channel;
    }

    public byte[] getData() {
        return this.data;
    }
}
