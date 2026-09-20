package cn.starry.narcissus.functions.packets.in.protocol.v774;

import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.in.PacketIn;

import java.io.IOException;

/**
 * Serverbound chat packet for protocol 774+.
 *
 * <p>The first field is the message string. The timestamp, salt, optional
 * signature and last-seen data are not needed by this Limbo, so the remaining
 * fields are intentionally ignored; NetworkManager already consumed the full
 * frame before dispatching this packet.</p>
 */
public class PacketPlayInChat774 implements PacketIn {

    private String message;

    @Override
    public void readPacket(PacketSerializer packetSerializer) throws IOException {
        this.message = packetSerializer.readString();
    }

    @Override
    public void handlePacket(NetworkManager networkManager, PlayerManager playerManager) {
        networkManager.getCommandManager().handleChat(networkManager, playerManager, this.message);
    }

    public String getMessage() {
        return this.message;
    }
}
