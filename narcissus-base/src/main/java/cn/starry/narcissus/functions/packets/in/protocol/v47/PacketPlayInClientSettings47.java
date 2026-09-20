package cn.starry.narcissus.functions.packets.in.protocol.v47;

import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.in.PacketIn;

import java.io.IOException;

/** Reads the 1.8 client settings field containing enabled skin layers. */
public class PacketPlayInClientSettings47 implements PacketIn {
    private int skinParts;

    @Override
    public void readPacket(PacketSerializer packetSerializer) throws IOException {
        packetSerializer.readString(); // locale
        packetSerializer.readByte();   // view distance
        packetSerializer.readVarInt(); // chat visibility
        packetSerializer.readBoolean(); // chat colors
        this.skinParts = packetSerializer.readUnsignedByte();
    }

    @Override
    public void handlePacket(NetworkManager networkManager, PlayerManager playerManager) {
        networkManager.updateSkinParts(playerManager, this.skinParts);
    }

    public int getSkinParts() {
        return this.skinParts;
    }
}
