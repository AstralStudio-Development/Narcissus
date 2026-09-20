package cn.starry.narcissus.functions.packets.protocols;

import cn.starry.narcissus.functions.config.Configuration;
import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.modern.BlockEntityTypeMap;
import cn.starry.narcissus.functions.modern.LegacyBlockMap;
import cn.starry.narcissus.functions.world.Chunk;
import cn.starry.narcissus.functions.world.ModernBlockEntity;
import cn.starry.narcissus.functions.world.World;
import cn.starry.narcissus.functions.packets.out.protocol.v774.*;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Drives the modern (1.20.2+) CONFIGURATION and PLAY entry sequence shared by
 * protocol 774 and 776. Kept protocol-agnostic: it asks the connection's current
 * {@link ModernProtocol} for the concrete registry bodies and dimension choice,
 * and sends packets through the normal write queue (ordered, on the selector
 * thread) via {@link NetworkManager#sendPacket}.
 */
public final class ModernConfigurationDriver {

    /** Single complete join batch loaded before the modern PLAY connection is usable. */
    static final int BOOTSTRAP_CHUNK_DIAMETER = 30;
    /** Authoritative block/entity updates use the same initial 30x30 view. */
    static final int JOIN_CHUNK_DIAMETER = BOOTSTRAP_CHUNK_DIAMETER;
    /** Send full palette-backed chunks like the reference 26.2 Limbo implementation. */
    private static final boolean SERVER_AUTHORITATIVE_MODERN_WORLD = false;
    /** Keep the block_update snapshot as a post-batch consistency check and fallback. */
    private static final boolean SEND_AUTHORITATIVE_MODERN_SNAPSHOT = true;

    private ModernConfigurationDriver() {}

    /**
     * Starts configuration exactly like vanilla: enable the vanilla feature set,
     * then negotiate the minecraft:core known pack. Registry elements must not be
     * sent before the client's select_known_packs response; doing so leaves the
     * client without the data-pack/feature context required to decode registry
     * codecs (notably enchantments).
     */
    public static void beginConfiguration(NetworkManager networkManager, PlayerManager playerManager) {
        ModernProtocol protocol = (ModernProtocol) playerManager.getProtocol();
        networkManager.sendPacket(playerManager, new PacketConfigOutUpdateEnabledFeatures774());
        networkManager.sendPacket(playerManager,
                new PacketConfigOutSelectKnownPacks774(protocol.getCorePackVersion()));
    }

    /** Sends all registry elements after known-pack negotiation, then finishes configuration. */
    public static void sendRegistryData(NetworkManager networkManager, PlayerManager playerManager,
                                        boolean clientKnowsCore) {
        ModernProtocol protocol = (ModernProtocol) playerManager.getProtocol();
        for (byte[] body : protocol.getRegistryBodies(clientKnowsCore)) {
            networkManager.sendPacket(playerManager, new PacketConfigOutRegistryData774(body));
        }
        // Clients require tag registries to be initialised before accepting PLAY chunks.
        networkManager.sendPacket(playerManager, new PacketConfigOutUpdateTags774(protocol.getUpdateTagsBody()));
        networkManager.sendPacket(playerManager, new PacketConfigOutFinish774());
        // Client replies with its own finish_configuration -> enterPlay().
    }

    /** Sends the PLAY join sequence once configuration is finished. */
    public static void enterPlay(NetworkManager networkManager, PlayerManager playerManager) {
        Configuration config = networkManager.getLimboConfiguration();
        ModernProtocol protocol = (ModernProtocol) playerManager.getProtocol();

        int maxPlayers = config.getMaxSlots() >= 0 ? config.getMaxSlots() : 20;
        String worldName = protocol.getDimensionName();

        networkManager.sendPacket(playerManager, protocol.createJoinGame(
                1,
                new String[]{worldName},
                maxPlayers,
                2,
                2,
                config.isReducedDebugInfo(),
                protocol.getDimensionTypeId(),
                worldName,
                config.getGameMode() & 0xFF,
                protocol.getSeaLevel()));

        // Publish the same command registry used by execution and suggestions
        // before the large chunk queue so the client can tab-complete immediately.
        networkManager.sendPacket(playerManager,
                new PacketPlayOutCommands774(networkManager.getCommandManager().getCommandLabels()));

        // Abilities flags follow the gamemode: survival(0)=none, creative(1)=
        // allowFlying|flying(0x06), adventure(2)=allowFlying(0x04, not flying).
        int gameMode = config.getGameMode() & 0xFF;
        byte abilitiesFlags;
        if (gameMode == 1) {
            abilitiesFlags = (byte) 0x06;
        } else if (gameMode == 2) {
            abilitiesFlags = (byte) 0x04;
        } else {
            abilitiesFlags = (byte) 0x00;
        }
        networkManager.sendPacket(playerManager, new PacketPlayOutPlayerAbilities774(abilitiesFlags, 0.05F, 0.1F));

        int centerChunkX = chunkCoordinate(config.getSpawnX());
        int centerChunkZ = chunkCoordinate(config.getSpawnZ());
        networkManager.sendPacket(playerManager,
                new PacketPlayOutGameEvent774(PacketPlayOutGameEvent774.START_WAITING_FOR_CHUNKS, 0.0F));

        // Compute one protocol-exact, cross-chunk light solution before any chunk is serialized.
        World playerWorld = playerManager.getWorld();
        if (playerWorld != null) {
            playerWorld.prepareModernLighting(protocol.getLightingProperties(),
                    protocol.getLegacyBlockMap(), protocol.getModernBlockStateMap(),
                    protocol.getMinY(), protocol.getSectionCount(), protocol.hasSkyLight());
        }

        // Send one complete 30x30 batch around the configured spawn chunk before
        // PLAY entry finishes. Real schematic chunks keep their palette/block
        // entities; missing neighbors are sent as valid empty chunks so the client
        // has the full initial render/light context without a second batch.
        sendChunkView(networkManager, playerManager, protocol, playerWorld,
                centerChunkX, centerChunkZ, BOOTSTRAP_CHUNK_DIAMETER);
        if (SEND_AUTHORITATIVE_MODERN_SNAPSHOT) {
            // Do not send the consistency snapshot until the client acknowledges
            // that the complete chunk batch is loaded; early updates can be
            // discarded before the client's chunk storage exists.
            playerManager.setAuthoritativeBlocksPending(true);
        }

        // The configured spawn is authoritative. Do not replace it with the
        // schematic's highest nearby block; operators may intentionally spawn
        // inside or below the loaded structure.
        int teleportId = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        playerManager.setPendingTeleportId(teleportId);
        networkManager.sendPacket(playerManager, new PacketPlayOutSyncPosition774(
                teleportId,
                config.getSpawnX(),
                config.getSpawnY(),
                config.getSpawnZ(),
                config.getSpawnYaw(),
                config.getSpawnPitch()));

        long keepAliveId = ThreadLocalRandom.current().nextLong();
        playerManager.setLastSentKeepAliveId(keepAliveId);
        playerManager.keepAlive();
        networkManager.sendPacket(playerManager, new PacketOutKeepAlive774(keepAliveId));

        networkManager.sendWelcomeMessages(playerManager);
        networkManager.playerJoined(playerManager);
    }

    /** Sends one complete chunk-cache center and square batch. */
    private static void sendChunkView(NetworkManager networkManager, PlayerManager playerManager,
                                      ModernProtocol protocol, World world,
                                      int centerChunkX, int centerChunkZ, int diameter) {
        networkManager.sendPacket(playerManager,
                new PacketPlayOutSetChunkCacheCenter774(centerChunkX, centerChunkZ));
        networkManager.sendPacket(playerManager, new PacketPlayOutChunkBatchStart774());
        int chunkBatchSize = 0;
        int startX = chunkViewStart(centerChunkX, diameter);
        int startZ = chunkViewStart(centerChunkZ, diameter);
        for (int cx = startX; cx < startX + diameter; cx++) {
            for (int cz = startZ; cz < startZ + diameter; cz++) {
                Chunk chunk = world == null ? null : findChunk(world, cx, cz);
                if (chunk == null) {
                    networkManager.sendPacket(playerManager, new PacketPlayOutChunkWithLight774(
                            world, cx, cz, protocol.getSectionCount(), protocol.getBiomeId(), protocol.getMinY(),
                            protocol.hasSkyLight(), protocol.hasSectionBlockCountMarker()));
                } else if (SERVER_AUTHORITATIVE_MODERN_WORLD) {
                    networkManager.sendPacket(playerManager, new PacketPlayOutChunkWithLight774(
                            chunk, protocol.getSectionCount(), protocol.getBiomeId(), protocol.getMinY(),
                            protocol.hasSkyLight(), protocol.hasSectionBlockCountMarker()));
                } else {
                    networkManager.sendPacket(playerManager, new PacketPlayOutChunkWithLight774(
                            world, chunk, protocol.getSectionCount(), protocol.getBiomeId(),
                            protocol.getLegacyBlockMap(), protocol.getModernBlockStateMap(),
                            protocol.getBlockEntityTypeMap(), protocol.getCorePackVersion(), protocol.getMinY(),
                            protocol.hasSkyLight(), protocol.hasSectionBlockCountMarker()));
                }
                chunkBatchSize++;
            }
        }
        networkManager.sendPacket(playerManager, new PacketPlayOutChunkBatchFinished774(chunkBatchSize));
        playerManager.setChunkViewCenter(centerChunkX, centerChunkZ);
    }

    /** Handles the client's acknowledgement for the single initial 3x3 chunk batch. */
    public static void onChunkBatchReceived(NetworkManager networkManager, PlayerManager playerManager) {
        playerManager.setInitialChunkBatchReceived(true);
        flushAuthoritativeBlocks(networkManager, playerManager);
    }

    /**
     * Flushes the deferred server-authoritative block snapshot only after the client
     * acknowledges the initial chunk storage. Teleport/player-loaded packets may
     * arrive earlier and are intentionally not sufficient on their own.
     */
    public static void flushAuthoritativeBlocks(NetworkManager networkManager, PlayerManager playerManager) {
        if (!playerManager.isInitialChunkBatchReceived()) {
            return;
        }
        if (!(playerManager.getProtocol() instanceof ModernProtocol)) {
            return;
        }
        Configuration config = networkManager.getLimboConfiguration();
        ModernProtocol protocol = (ModernProtocol) playerManager.getProtocol();
        World world = playerManager.getWorld();
        int centerChunkX = playerManager.isChunkViewCenterInitialized()
                ? playerManager.getChunkViewCenterX()
                : chunkCoordinate(config.getSpawnX());
        int centerChunkZ = playerManager.isChunkViewCenterInitialized()
                ? playerManager.getChunkViewCenterZ()
                : chunkCoordinate(config.getSpawnZ());
        if (!playerManager.isAuthoritativeBlocksPending()) {
            sendBlockOverrides(networkManager, playerManager, centerChunkX, centerChunkZ);
            return;
        }
        playerManager.setAuthoritativeBlocksPending(false);
        sendAuthoritativeBlocks(networkManager, playerManager, protocol, world, centerChunkX, centerChunkZ);
        sendAuthoritativeBlockEntities(networkManager, playerManager, protocol, world,
                centerChunkX, centerChunkZ);
        sendBlockOverrides(networkManager, playerManager, centerChunkX, centerChunkZ);
    }

    /** Sends every non-air block from the player's private World after empty chunks. */
    private static void sendAuthoritativeBlocks(NetworkManager networkManager,
                                                PlayerManager playerManager,
                                                ModernProtocol protocol,
                                                World world,
                                                int centerChunkX,
                                                int centerChunkZ) {
        if (world == null) {
            return;
        }
        LegacyBlockMap map = protocol.getLegacyBlockMap();
        cn.starry.narcissus.functions.modern.ModernBlockStateMap modernMap = protocol.getModernBlockStateMap();
        int maxY = protocol.getSectionCount() * 16;
        int sent = 0;
        int unmapped = 0;
        for (Chunk[] column : world.getChunks()) {
            for (Chunk chunk : column) {
                if (chunk == null || !isWithinJoinView(chunk, centerChunkX, centerChunkZ)) {
                    continue;
                }
                for (int y = 0; y < maxY; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            int globalState;
                            if (world.hasModernBlockStates()) {
                                int stateKey = chunk.getModernStateKey(x, y, z);
                                if (stateKey == 0) {
                                    continue;
                                }
                                globalState = modernMap.resolve(world.getModernState(stateKey));
                            } else {
                                int legacyId = chunk.getLegacyId(x, y, z);
                                if (legacyId == 0) {
                                    continue;
                                }
                                globalState = map.toGlobalState(world, chunk, x, y, z);
                            }
                            if (globalState == LegacyBlockMap.AIR) {
                                unmapped++;
                                continue;
                            }
                            int worldX = (chunk.getX() << 4) | x;
                            int worldZ = (chunk.getZ() << 4) | z;
                            networkManager.sendPacket(playerManager,
                                    new PacketPlayOutBlockUpdate774(packBlockPosition(worldX, y, worldZ), globalState));
                            sent++;
                        }
                    }
                }
            }
        }
    }

    /** Re-applies per-player block mutations after a chunk batch is decoded. */
    private static void sendBlockOverrides(NetworkManager networkManager, PlayerManager playerManager,
                                           int centerChunkX, int centerChunkZ) {
        for (Map.Entry<Long, Integer> entry : playerManager.getBlockOverrides().entrySet()) {
            long position = entry.getKey();
            int x = (int) (position >> 38);
            int z = (int) (position & 0x3FFFFFFL);
            if ((z & 0x2000000) != 0) {
                z -= 0x4000000;
            }
            int startX = chunkViewStart(centerChunkX, JOIN_CHUNK_DIAMETER);
            int startZ = chunkViewStart(centerChunkZ, JOIN_CHUNK_DIAMETER);
            if ((x >> 4) < startX || (x >> 4) >= startX + JOIN_CHUNK_DIAMETER
                    || (z >> 4) < startZ || (z >> 4) >= startZ + JOIN_CHUNK_DIAMETER) {
                continue;
            }
            networkManager.sendPacket(playerManager,
                    new PacketPlayOutBlockUpdate774(position, entry.getValue()));
        }
    }

    /** Re-sends modern block-entity NBT after the client has created the chunk storage. */
    private static void sendAuthoritativeBlockEntities(NetworkManager networkManager,
                                                       PlayerManager playerManager,
                                                       ModernProtocol protocol,
                                                       World world,
                                                       int centerChunkX,
                                                       int centerChunkZ) {
        if (world == null) {
            return;
        }
        BlockEntityTypeMap typeMap = protocol.getBlockEntityTypeMap();
        if (typeMap == null) {
            return;
        }
        for (Chunk[] column : world.getChunks()) {
            for (Chunk chunk : column) {
                if (chunk == null || !isWithinJoinView(chunk, centerChunkX, centerChunkZ)) {
                    continue;
                }
                for (ModernBlockEntity entity : chunk.getModernBlockEntities()) {
                    int typeId = typeMap.resolve(entity.getId());
                    if (typeId < 0) {
                        cn.starry.narcissus.Narcissus.LOGGER.warning(
                                "Skipping unknown modern block entity type " + entity.getId());
                        continue;
                    }
                    networkManager.sendPacket(playerManager,
                            new PacketPlayOutBlockEntityData774(entity, typeId, protocol.getCorePackVersion()));
                }
            }
        }
    }

    static int chunkCoordinate(double blockCoordinate) {
        return Math.floorDiv((int) Math.floor(blockCoordinate), 16);
    }

    static boolean isWithinJoinView(Chunk chunk, int centerChunkX, int centerChunkZ) {
        int startX = chunkViewStart(centerChunkX, JOIN_CHUNK_DIAMETER);
        int startZ = chunkViewStart(centerChunkZ, JOIN_CHUNK_DIAMETER);
        return chunk.getX() >= startX && chunk.getX() < startX + JOIN_CHUNK_DIAMETER
                && chunk.getZ() >= startZ && chunk.getZ() < startZ + JOIN_CHUNK_DIAMETER;
    }

    static int chunkViewStart(int centerChunk, int diameter) {
        return centerChunk - diameter / 2;
    }

    private static Chunk findChunk(World world, int chunkX, int chunkZ) {
        for (Chunk[] column : world.getChunks()) {
            for (Chunk chunk : column) {
                if (chunk != null && chunk.getX() == chunkX && chunk.getZ() == chunkZ)
                    return chunk;
            }
        }
        return null;
    }

    private static long packBlockPosition(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38)
                | ((long) (y & 0xFFF) << 26)
                | (z & 0x3FFFFFFL);
    }
}
