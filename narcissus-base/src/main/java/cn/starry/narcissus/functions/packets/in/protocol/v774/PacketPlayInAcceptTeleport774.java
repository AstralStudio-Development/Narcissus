package cn.starry.narcissus.functions.packets.in.protocol.v774;

import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.in.PacketIn;
import cn.starry.narcissus.functions.packets.protocols.ModernConfigurationDriver;

import java.io.IOException;

/**
 * Accept Teleportation (teleport_confirm, PLAY 0x00) for protocol 774.
 * Confirms the synchronize-position teleport id so the client is fully spawned.
 */
public class PacketPlayInAcceptTeleport774 implements PacketIn {

    private int teleportId;

    @Override
    public void readPacket(PacketSerializer s) throws IOException {
        this.teleportId = s.readVarInt();
    }

    @Override
    public void handlePacket(NetworkManager networkManager, PlayerManager playerManager) {
        if (this.teleportId == playerManager.getPendingTeleportId()) {
            playerManager.confirmTeleport();
            ModernConfigurationDriver.flushAuthoritativeBlocks(networkManager, playerManager);
        }
    }
}
