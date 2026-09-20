package cn.starry.narcissus.functions.packets.in.protocol.v47;

import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.in.PacketIn;
import cn.starry.narcissus.functions.packets.out.protocol.v47.PacketPlayOutTabComplete47;

import java.io.IOException;
import java.util.List;

/** Serverbound tab-complete request for protocol 47. */
public class PacketPlayInTabComplete47 implements PacketIn {

    private String text;

    @Override
    public void readPacket(PacketSerializer packetSerializer) throws IOException {
        this.text = packetSerializer.readString();
    }

    @Override
    public void handlePacket(NetworkManager networkManager, PlayerManager playerManager) {
        List<String> matches = networkManager.getCommandManager().suggestLegacy(this.text);
        networkManager.sendPacket(playerManager, new PacketPlayOutTabComplete47(matches));
    }

    public String getText() {
        return this.text;
    }
}
