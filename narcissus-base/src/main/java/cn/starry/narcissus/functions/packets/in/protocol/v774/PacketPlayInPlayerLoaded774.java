package cn.starry.narcissus.functions.packets.in.protocol.v774;

import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.in.PacketIn;
import cn.starry.narcissus.functions.packets.protocols.ModernConfigurationDriver;

import java.io.IOException;

/**
 * Clientbound-world readiness acknowledgement (player_loaded).
 * The server uses it as the safe point to apply the authoritative block snapshot:
 * the client has created its chunk storage, so block_update packets are no longer
 * discarded as updates for unknown chunks.
 */
public class PacketPlayInPlayerLoaded774 implements PacketIn {

    @Override
    public void readPacket(PacketSerializer s) throws IOException {
        // no fields
    }

    @Override
    public void handlePacket(NetworkManager networkManager, PlayerManager playerManager) {
        ModernConfigurationDriver.flushAuthoritativeBlocks(networkManager, playerManager);
    }
}
