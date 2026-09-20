package cn.starry.narcissus.functions.packets.in.protocol.v774;

import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.in.PacketIn;

import java.io.IOException;

/**
 * KeepAlive response (long id) for protocol 774, used in both CONFIGURATION
 * (0x04) and PLAY (0x1b). Refreshes the connection's keep-alive timestamp when
 * the id matches the last one we sent.
 */
public class PacketInKeepAlive774 implements PacketIn {

    private long id;

    @Override
    public void readPacket(PacketSerializer s) throws IOException {
        this.id = s.readLong();
    }

    @Override
    public void handlePacket(NetworkManager networkManager, PlayerManager playerManager) {
        if (this.id == playerManager.getLastSentKeepAliveId()) {
            playerManager.keepAlive();
        }
    }
}
