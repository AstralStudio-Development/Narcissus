package cn.starry.narcissus.functions.packets.in;

import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.packets.Status;
import cn.starry.narcissus.functions.config.Configuration;
import cn.starry.narcissus.functions.packets.out.PacketLoginOutSuccess;
import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketLoginOutSuccess774;
import cn.starry.narcissus.functions.packets.protocols.ModernProtocol;
import cn.starry.narcissus.functions.packets.protocols.Protocol47;
import cn.starry.narcissus.functions.world.Chunk;

import java.io.IOException;
import java.util.UUID;

/**
 * Created by Rigner on 29/08/16 for project Limbo.
 * All rights reserved.
 *
 * Handles Login Start for every native protocol and branches by capability:
 * <ul>
 *   <li>Modern protocols (1.20.2+, e.g. 774/776): read the optional client UUID,
 *       send the modern Login Success and then WAIT for Login Acknowledged. The
 *       CONFIGURATION/PLAY sequence is driven from
 *       {@code PacketLoginInAcknowledged774} via {@code ModernConfigurationDriver}.
 *       No PLAY packets are sent here.</li>
 *   <li>Legacy v47: run the classic Login Success -> PLAY -> Join -> chunks ->
 *       position sequence in order through the write queue.</li>
 * </ul>
 */
public class PacketLoginInStart implements PacketIn {

    private String userName;
    private UUID uuid;
    private boolean hasUuid;

    @Override
    public void readPacket(PacketSerializer packetSerializer) throws IOException {
        this.userName = packetSerializer.readString();
        // Modern login_start carries a trailing UUID; legacy v47 does not. Read it
        // only when bytes remain so both shapes parse from the sliced frame.
        if (packetSerializer.available() >= 16) {
            this.uuid = packetSerializer.readUUID();
            this.hasUuid = true;
        }
    }

    @Override
    public void handlePacket(NetworkManager networkManager, PlayerManager playerManager) {
        // Create the mutable world only after LOGIN starts so STATUS-only
        // connections do not allocate a schematic copy.
        if (playerManager.getWorld() == null && networkManager.getTemplateWorld() != null)
            playerManager.setWorld(networkManager.createPlayerWorld());

        playerManager.setUserName(this.userName);
        // When connected through BungeeCord, the forwarded UUID is the trusted
        // GameProfile identity. Do not let modern Login Start overwrite it with
        // the client-side UUID copy.
        if (this.hasUuid && this.uuid != null && !playerManager.isUuidForwarded()) {
            playerManager.setUuid(this.uuid);
        }

        if (playerManager.getProtocol() instanceof ModernProtocol) {
            // Modern: send Login Success and wait for Login Acknowledged.
            ModernProtocol modern = (ModernProtocol) playerManager.getProtocol();
            networkManager.sendPacket(playerManager,
                    modern.createLoginSuccess(playerManager.getUuid(), this.userName));
            // State stays LOGIN until the client acknowledges.
            return;
        }

        // Legacy v47 flow.
        Configuration configuration = networkManager.getLimboConfiguration();
        networkManager.sendPacket(playerManager, new PacketLoginOutSuccess(playerManager.getUuid(), this.userName));
        playerManager.setStatus(Status.PLAY);

        playerManager.getProtocol().sendJoinGame(playerManager, 1, configuration.getGameMode(),
                configuration.getDimension(), (byte) 0, (byte) 1, "default", configuration.isReducedDebugInfo());

        if (playerManager.getWorld() != null) {
            for (Chunk[] tab : playerManager.getWorld().getChunks()) {
                if (tab == null) continue;
                for (Chunk chunk : tab) {
                    if (chunk != null) {
                        playerManager.getProtocol().sendChunk(playerManager, chunk);
                        chunk.sendTileEntities(playerManager);
                    }
                }
            }
            if (playerManager.getProtocol() instanceof Protocol47 protocol47)
                protocol47.sendLightHaloChunks(playerManager);
        }

        // The configured spawn is authoritative. Do not replace it with the
        // schematic's highest nearby block; operators may intentionally spawn
        // inside or below the loaded structure.
        playerManager.getProtocol().sendPosition(playerManager,
                configuration.getSpawnX(),
                configuration.getSpawnY(),
                configuration.getSpawnZ(),
                configuration.getSpawnYaw(),
                configuration.getSpawnPitch());

        playerManager.keepAlive();
        networkManager.sendWelcomeMessages(playerManager);
        networkManager.playerJoined(playerManager);
    }
}
