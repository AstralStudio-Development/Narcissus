package cn.starry.narcissus.functions.packets.protocols;

import cn.starry.narcissus.functions.config.Configuration;
import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.modern.RegistryFixtures;
import cn.starry.narcissus.functions.packets.AbstractProtocol;
import cn.starry.narcissus.functions.packets.Status;
import cn.starry.narcissus.functions.packets.in.PacketInIgnored;
import cn.starry.narcissus.functions.packets.in.PacketInPluginMessage;
import cn.starry.narcissus.functions.packets.in.PacketLoginInStart;
import cn.starry.narcissus.functions.packets.in.PacketStatusInPing;
import cn.starry.narcissus.functions.packets.in.PacketStatusInRequest;
import cn.starry.narcissus.functions.packets.in.protocol.v774.*;
import cn.starry.narcissus.functions.packets.out.PacketLoginOutSuccess;
import cn.starry.narcissus.functions.packets.out.PacketOutDisconnect;
import cn.starry.narcissus.functions.packets.out.PacketStatusOutPong;
import cn.starry.narcissus.functions.packets.out.PacketStatusOutResponse;
import cn.starry.narcissus.functions.packets.out.protocol.v774.*;
import cn.starry.narcissus.functions.world.Chunk;
import cn.starry.narcissus.functions.world.SignTileEntity;
import cn.starry.narcissus.functions.world.lighting.BlockLightingProperties;

import java.util.List;

/**
 * Native protocol implementation for Minecraft 1.21.11 (protocol 774).
 *
 * Packet ids are taken from the authoritative minecraft-data protocol.json for
 * 1.21.11 (see spec://protocol-774-reference.md). The modern login/configuration/
 * play flow is driven by {@link ModernConfigurationDriver}; registry data is sent
 * from bundled byte fixtures under /registry/v774/.
 *
 * The limbo dimension is selected from {@link Configuration#getDimension()} and
 * mapped onto the official overworld/nether/end entries already present in the
 * bundled registry fixture.
 */
public class Protocol774 extends AbstractProtocol implements ModernProtocol {

    // Official Mojang packets.json ids for 1.21.11. Protocol776 overrides the
    // PLAY ids that shifted in 26.2; everything else (status/login/configuration
    // and the unchanged play packets) is inherited.
    protected static final int ID_PLAY_IN_ACCEPT_TELEPORT = 0x00;
    protected static final int ID_PLAY_IN_CHAT_COMMAND    = 0x06;
    protected static final int ID_PLAY_IN_CHAT_COMMAND_SIGNED = 0x07;
    protected static final int ID_PLAY_IN_CHAT            = 0x08;
    protected static final int ID_PLAY_IN_CHAT_ACK        = 0x05;
    protected static final int ID_PLAY_IN_CHAT_SESSION    = 0x09;
    protected static final int ID_PLAY_IN_CUSTOM_PAYLOAD  = 0x15;
    protected static final int ID_PLAY_IN_COMMAND_SUGGESTION = 0x0e;
    protected static final int ID_PLAY_IN_CHUNK_BATCH_RECEIVED = 0x0a;
    protected static final int ID_PLAY_IN_POSITION        = 0x1d;
    protected static final int ID_PLAY_IN_POSITION_AND_LOOK = 0x1e;
    protected static final int ID_PLAY_IN_KEEP_ALIVE      = 0x1b;
    protected static final int ID_PLAY_IN_PLAYER_ACTION   = 0x28;
    protected static final int ID_PLAY_OUT_BLOCK_ENTITY_DATA = 0x06;
    protected static final int ID_PLAY_OUT_BLOCK_UPDATE   = 0x08;
    protected static final int ID_PLAY_OUT_CHUNK_BATCH_FINISHED = 0x0b;
    protected static final int ID_PLAY_OUT_CHUNK_BATCH_START = 0x0c;
    protected static final int ID_PLAY_OUT_DISCONNECT     = 0x20;
    protected static final int ID_PLAY_OUT_GAME_EVENT     = 0x26;
    protected static final int ID_PLAY_OUT_KEEP_ALIVE     = 0x2b;
    protected static final int ID_PLAY_OUT_CHUNK          = 0x2c;
    protected static final int ID_PLAY_OUT_LIGHT_UPDATE   = 0x2f;
    protected static final int ID_PLAY_OUT_JOIN_GAME      = 0x30;
    protected static final int ID_PLAY_OUT_ABILITIES      = 0x3e;
    protected static final int ID_PLAY_OUT_POSITION       = 0x46;
    protected static final int ID_PLAY_OUT_CHUNK_CENTER   = 0x5c;
    protected static final int ID_PLAY_OUT_ENTITY_METADATA = 0x61;
    protected static final int ID_PLAY_OUT_SYSTEM_CHAT    = 0x77;
    protected static final int ID_PLAY_OUT_TAB_LIST       = 0x78;
    protected static final int ID_PLAY_OUT_TELEPORT_ENTITY = 0x7b;
    protected static final int ID_PLAY_OUT_PLAYER_INFO_REMOVE = 0x43;
    protected static final int ID_PLAY_OUT_PLAYER_INFO_UPDATE = 0x44;
    protected static final int ID_PLAY_OUT_REMOVE_ENTITIES = 0x4b;
    protected static final int ID_PLAY_OUT_SET_PASSENGERS = 0x69;
    protected static final int ID_PLAY_OUT_SET_PLAYER_TEAM = 0x6b;
    protected static final int ID_PLAY_OUT_SET_TIME       = 0x6f;
    protected static final int ID_PLAY_OUT_COMMAND_SUGGESTIONS = 0x0f;
    protected static final int ID_PLAY_OUT_COMMANDS       = 0x10;

    // 1.21.11 fixture order: overworld=0, the_end=2, the_nether=3.
    private static final int OVERWORLD_DIMENSION_TYPE_ID = 0;
    private static final int END_DIMENSION_TYPE_ID = 2;
    private static final int NETHER_DIMENSION_TYPE_ID = 3;
    private static final int OVERWORLD_BIOME_ID = 40; // minecraft:plains
    private static final int END_BIOME_ID = 56;       // minecraft:the_end
    private static final int NETHER_BIOME_ID = 34;    // minecraft:nether_wastes

    private final int configuredDimension;
    private final List<byte[]> registryBodies;
    private final List<byte[]> knownPackRegistryBodies;
    private final byte[] updateTagsBody;
    private final cn.starry.narcissus.functions.modern.LegacyBlockMap legacyBlockMap;
    private final cn.starry.narcissus.functions.modern.ModernBlockStateMap modernBlockStateMap;
    private final cn.starry.narcissus.functions.modern.BlockEntityTypeMap blockEntityTypeMap;
    private final BlockLightingProperties lightingProperties;

    public Protocol774(NetworkManager networkManager) {
        super(networkManager);
        this.configuredDimension = resolveConfiguredDimension(
                networkManager == null ? null : networkManager.getLimboConfiguration());
        RegistryFixtures fixtures = RegistryFixtures.load(registryVersionDir());
        this.registryBodies = fixtures.getRegistryBodies();
        String knownDir = knownRegistryVersionDir();
        this.knownPackRegistryBodies = knownDir == null
                ? java.util.Collections.emptyList()
                : RegistryFixtures.load(knownDir).getRegistryBodies();
        this.updateTagsBody = fixtures.getUpdateTagsBody();
        this.legacyBlockMap = cn.starry.narcissus.functions.modern.LegacyBlockMap.load(registryVersionDir());
        this.modernBlockStateMap = cn.starry.narcissus.functions.modern.ModernBlockStateMap.load(registryVersionDir());
        this.blockEntityTypeMap = cn.starry.narcissus.functions.modern.BlockEntityTypeMap.load(registryVersionDir());
        this.lightingProperties = BlockLightingProperties.load(registryVersionDir(),
                getCorePackVersion(), this.modernBlockStateMap.size());
        registerPackets();
    }

    private static int resolveConfiguredDimension(Configuration configuration) {
        int dimension = configuration == null ? 1 : configuration.getDimension();
        if (dimension == -1 || dimension == 0 || dimension == 1) {
            return dimension;
        }
        cn.starry.narcissus.Narcissus.LOGGER.warning(
                "Unsupported modern dimension " + dimension + "; falling back to the_end");
        return 1;
    }

    protected String registryVersionDir() {
        return "v774";
    }

    /** Compact fixtures used when the client accepts minecraft:core. */
    protected String knownRegistryVersionDir() {
        return "v774-known";
    }

    protected void registerPackets() {
        // STATUS (ids identical in 774 and 776)
        registerPacketIn(Status.STATUS, 0x00, PacketStatusInRequest.class);
        registerPacketIn(Status.STATUS, 0x01, PacketStatusInPing.class);
        registerPacketOut(Status.STATUS, 0x00, PacketStatusOutResponse.class);
        registerPacketOut(Status.STATUS, 0x01, PacketStatusOutPong.class);

        // LOGIN (ids identical in 774 and 776)
        registerPacketIn(Status.LOGIN, 0x00, PacketLoginInStart.class);
        registerPacketIn(Status.LOGIN, 0x03, PacketLoginInAcknowledged774.class);
        registerPacketOut(Status.LOGIN, 0x00, PacketOutDisconnect.class);        // JSON string reason
        registerPacketOut(Status.LOGIN, 0x02, PacketLoginOutSuccess774.class);

        // CONFIGURATION (ids identical in 774 and 776)
        registerPacketIn(Status.CONFIGURATION, 0x00, PacketConfigInClientInformation774.class); // client_information
        registerPacketIn(Status.CONFIGURATION, 0x02, PacketInIgnored.class);     // custom_payload (brand)
        registerPacketIn(Status.CONFIGURATION, 0x03, PacketConfigInFinish774.class);
        registerPacketIn(Status.CONFIGURATION, 0x04, PacketInKeepAlive774.class);
        registerPacketIn(Status.CONFIGURATION, 0x05, PacketInIgnored.class);     // pong
        registerPacketIn(Status.CONFIGURATION, 0x07, PacketConfigInSelectKnownPacks774.class);
        registerPacketOut(Status.CONFIGURATION, 0x02, PacketOutDisconnect774.class);
        registerPacketOut(Status.CONFIGURATION, 0x03, PacketConfigOutFinish774.class);
        registerPacketOut(Status.CONFIGURATION, 0x04, PacketOutKeepAlive774.class);
        registerPacketOut(Status.CONFIGURATION, 0x07, PacketConfigOutRegistryData774.class);
        registerPacketOut(Status.CONFIGURATION, idConfigOutEnabledFeatures(), PacketConfigOutUpdateEnabledFeatures774.class);
        registerPacketOut(Status.CONFIGURATION, idConfigOutUpdateTags(), PacketConfigOutUpdateTags774.class);
        registerPacketOut(Status.CONFIGURATION, idConfigOutSelectKnownPacks(), PacketConfigOutSelectKnownPacks774.class);

        // PLAY (serverbound: consume everything, react to chat/commands,
        // teleport/keepalive/chunk batch and player actions)
        registerPacketIn(Status.PLAY, idPlayInAcceptTeleport(), PacketPlayInAcceptTeleport774.class);
        registerPacketIn(Status.PLAY, idPlayInChatCommand(), PacketPlayInChatCommand774.class);
        registerPacketIn(Status.PLAY, idPlayInChatCommandSigned(), PacketPlayInChatCommand774.class);
        registerPacketIn(Status.PLAY, idPlayInChat(), PacketPlayInChat774.class);
        registerPacketIn(Status.PLAY, idPlayInChatAck(), PacketInIgnored.class);
        registerPacketIn(Status.PLAY, idPlayInChatSession(), PacketInIgnored.class);
        registerPacketIn(Status.PLAY, idPlayInCustomPayload(), PacketInPluginMessage.class);
        registerPacketIn(Status.PLAY, idPlayInCommandSuggestion(), PacketPlayInCommandSuggestion774.class);
        registerPacketIn(Status.PLAY, idPlayInChunkBatchReceived(), PacketPlayInChunkBatchReceived774.class);
        registerPacketIn(Status.PLAY, idPlayInKeepAlive(), PacketInKeepAlive774.class);
        registerPacketIn(Status.PLAY, idPlayInPlayerAction(), PacketPlayInPlayerAction774.class);
        registerPacketIn(Status.PLAY, idPlayInPlayerLoaded(), PacketPlayInPlayerLoaded774.class);
        // Common serverbound movement/etc are ignored (frame already sliced off).
        for (int id : ignoredPlayServerboundIds()) {
            registerPacketIn(Status.PLAY, id, PacketInIgnored.class);
        }

        // PLAY clientbound
        registerPacketOut(Status.PLAY, idPlayOutDisconnect(), PacketOutDisconnect774.class);
        registerPacketOut(Status.PLAY, idPlayOutGameEvent(), PacketPlayOutGameEvent774.class);
        registerPacketOut(Status.PLAY, idPlayOutChunkBatchFinished(), PacketPlayOutChunkBatchFinished774.class);
        registerPacketOut(Status.PLAY, idPlayOutChunkBatchStart(), PacketPlayOutChunkBatchStart774.class);
        registerPacketOut(Status.PLAY, idPlayOutKeepAlive(), PacketOutKeepAlive774.class);
        registerPacketOut(Status.PLAY, idPlayOutChunk(), PacketPlayOutChunkWithLight774.class);
        registerPacketOut(Status.PLAY, idPlayOutLightUpdate(), PacketPlayOutLightUpdate774.class);
        registerPacketOut(Status.PLAY, idPlayOutJoinGame(), PacketPlayOutJoinGame774.class);
        registerPacketOut(Status.PLAY, idPlayOutAbilities(), PacketPlayOutPlayerAbilities774.class);
        registerPacketOut(Status.PLAY, idPlayOutPosition(), PacketPlayOutSyncPosition774.class);
        registerPacketOut(Status.PLAY, 0x01, PacketPlayOutAddEntity774.class);
        registerPacketOut(Status.PLAY, idPlayOutRemoveEntities(), PacketPlayOutRemoveEntities774.class);
        registerPacketOut(Status.PLAY, idPlayOutEntityMetadata(), PacketPlayOutEntityMetadata774.class);
        registerPacketOut(Status.PLAY, idPlayOutSetPassengers(), PacketPlayOutSetPassengers774.class);
        registerPacketOut(Status.PLAY, idPlayOutChunkCenter(), PacketPlayOutSetChunkCacheCenter774.class);
        registerPacketOut(Status.PLAY, idPlayOutSystemChat(), PacketPlayOutSystemChat774.class);
        registerPacketOut(Status.PLAY, idPlayOutTabList(), PacketPlayOutTabList774.class);
        registerPacketOut(Status.PLAY, idPlayOutTeleportEntity(), PacketPlayOutTeleportEntity774.class);
        registerPacketOut(Status.PLAY, idPlayOutPlayerInfoRemove(), PacketPlayOutPlayerInfoRemove774.class);
        registerPacketOut(Status.PLAY, idPlayOutPlayerInfoUpdate(), PacketPlayOutPlayerInfoUpdate774.class);
        registerPacketOut(Status.PLAY, idPlayOutSetPlayerTeam(), PacketPlayOutSetPlayerTeam774.class);
        registerPacketOut(Status.PLAY, idPlayOutSetTime(), PacketPlayOutSetTime774.class);
        registerPacketOut(Status.PLAY, idPlayOutCommandSuggestions(), PacketPlayOutCommandSuggestions774.class);
        registerPacketOut(Status.PLAY, idPlayOutCommands(), PacketPlayOutCommands774.class);
        registerPacketOut(Status.PLAY, idPlayOutBlockEntityData(), PacketPlayOutBlockEntityData774.class);
        registerPacketOut(Status.PLAY, idPlayOutBlockUpdate(), PacketPlayOutBlockUpdate774.class);
    }

    // Packet-id hooks keep inherited serializers registered at each release's
    // actual ids, including the configuration changes introduced in 26.3.
    protected int idConfigOutEnabledFeatures() { return 0x0c; }
    protected int idConfigOutUpdateTags() { return 0x0d; }
    protected int idConfigOutSelectKnownPacks() { return 0x0e; }
    protected int playerEntityTypeId() { return 155; }
    protected int idPlayInAcceptTeleport() { return ID_PLAY_IN_ACCEPT_TELEPORT; }
    protected int idPlayInChatCommand() { return ID_PLAY_IN_CHAT_COMMAND; }
    protected int idPlayInChatCommandSigned() { return ID_PLAY_IN_CHAT_COMMAND_SIGNED; }
    protected int idPlayInChat() { return ID_PLAY_IN_CHAT; }
    protected int idPlayInChatAck() { return ID_PLAY_IN_CHAT_ACK; }
    protected int idPlayInChatSession() { return ID_PLAY_IN_CHAT_SESSION; }
    protected int idPlayInCustomPayload() { return ID_PLAY_IN_CUSTOM_PAYLOAD; }
    protected int idPlayInCommandSuggestion() { return ID_PLAY_IN_COMMAND_SUGGESTION; }
    protected int idPlayInPosition()        { return ID_PLAY_IN_POSITION; }
    protected int idPlayInPositionAndLook()  { return ID_PLAY_IN_POSITION_AND_LOOK; }
    protected int idPlayInChunkBatchReceived() { return ID_PLAY_IN_CHUNK_BATCH_RECEIVED; }
    protected int idPlayInKeepAlive()      { return ID_PLAY_IN_KEEP_ALIVE; }
    protected int idPlayInPlayerAction()   { return ID_PLAY_IN_PLAYER_ACTION; }
    protected int idPlayInPlayerLoaded()    { return 0x2b; }
    protected int idPlayOutBlockUpdate()   { return ID_PLAY_OUT_BLOCK_UPDATE; }
    protected int idPlayOutChunkBatchFinished() { return ID_PLAY_OUT_CHUNK_BATCH_FINISHED; }
    protected int idPlayOutChunkBatchStart() { return ID_PLAY_OUT_CHUNK_BATCH_START; }
    protected int idPlayOutDisconnect()    { return ID_PLAY_OUT_DISCONNECT; }
    protected int idPlayOutGameEvent()     { return ID_PLAY_OUT_GAME_EVENT; }
    protected int idPlayOutKeepAlive()     { return ID_PLAY_OUT_KEEP_ALIVE; }
    protected int idPlayOutChunk()         { return ID_PLAY_OUT_CHUNK; }
    protected int idPlayOutLightUpdate()   { return ID_PLAY_OUT_LIGHT_UPDATE; }
    protected int idPlayOutJoinGame()      { return ID_PLAY_OUT_JOIN_GAME; }
    protected int idPlayOutAbilities()     { return ID_PLAY_OUT_ABILITIES; }
    protected int idPlayOutPosition()      { return ID_PLAY_OUT_POSITION; }
    protected int idPlayOutEntityMetadata() { return ID_PLAY_OUT_ENTITY_METADATA; }
    protected int idPlayOutChunkCenter()   { return ID_PLAY_OUT_CHUNK_CENTER; }
    protected int idPlayOutSystemChat()  { return ID_PLAY_OUT_SYSTEM_CHAT; }
    protected int idPlayOutTabList() { return ID_PLAY_OUT_TAB_LIST; }
    protected int idPlayOutTeleportEntity() { return ID_PLAY_OUT_TELEPORT_ENTITY; }
    protected int idPlayOutPlayerInfoRemove() { return ID_PLAY_OUT_PLAYER_INFO_REMOVE; }
    protected int idPlayOutPlayerInfoUpdate() { return ID_PLAY_OUT_PLAYER_INFO_UPDATE; }
    protected int idPlayOutRemoveEntities() { return ID_PLAY_OUT_REMOVE_ENTITIES; }
    protected int idPlayOutSetPassengers() { return ID_PLAY_OUT_SET_PASSENGERS; }
    protected int idPlayOutSetPlayerTeam() { return ID_PLAY_OUT_SET_PLAYER_TEAM; }
    protected int idPlayOutSetTime() { return ID_PLAY_OUT_SET_TIME; }
    protected int idPlayOutCommandSuggestions() { return ID_PLAY_OUT_COMMAND_SUGGESTIONS; }
    protected int idPlayOutCommands()       { return ID_PLAY_OUT_COMMANDS; }
    protected int idPlayOutBlockEntityData() { return ID_PLAY_OUT_BLOCK_ENTITY_DATA; }

    /** Serverbound PLAY ids deliberately ignored after join-time preload.
     * 774 values: client_tick_end(0x0c), custom_payload(0x15),
     * move_player_pos/pos_rot/rot/status_only(0x1d-0x20), player_loaded(0x2b),
     * pong(0x2c). */
    protected int[] ignoredPlayServerboundIds() {
        return new int[]{0x0c, 0x1d, 0x1e, 0x1f, 0x20, 0x2c};
    }

    @Override
    public int[] getVersions() {
        return new int[]{774};
    }

    // ---- ModernProtocol ----

    @Override
    public String getCorePackVersion() {
        return "1.21.11";
    }

    @Override
    public List<byte[]> getRegistryBodies(boolean clientKnowsCore) {
        if (clientKnowsCore && !this.knownPackRegistryBodies.isEmpty()) {
            return this.knownPackRegistryBodies;
        }
        return this.registryBodies;
    }

    @Override
    public byte[] getUpdateTagsBody() {
        return this.updateTagsBody;
    }

    @Override
    public int getDimensionTypeId() {
        return switch (this.configuredDimension) {
            case -1 -> NETHER_DIMENSION_TYPE_ID;
            case 0 -> OVERWORLD_DIMENSION_TYPE_ID;
            default -> END_DIMENSION_TYPE_ID;
        };
    }

    @Override
    public String getDimensionName() {
        return switch (this.configuredDimension) {
            case -1 -> "minecraft:the_nether";
            case 0 -> "minecraft:overworld";
            default -> "minecraft:the_end";
        };
    }

    @Override
    public int getSectionCount() {
        return this.configuredDimension == 0 ? 24 : 16;
    }

    @Override
    public int getMinY() {
        return this.configuredDimension == 0 ? -64 : 0;
    }

    @Override
    public int getBiomeId() {
        return switch (this.configuredDimension) {
            case -1 -> NETHER_BIOME_ID;
            case 0 -> OVERWORLD_BIOME_ID;
            default -> END_BIOME_ID;
        };
    }

    @Override
    public boolean hasSkyLight() {
        return this.configuredDimension == 0;
    }

    @Override
    public boolean hasSectionBlockCountMarker() {
        return false;
    }

    @Override
    public cn.starry.narcissus.functions.modern.LegacyBlockMap getLegacyBlockMap() {
        return this.legacyBlockMap;
    }

    @Override
    public cn.starry.narcissus.functions.modern.ModernBlockStateMap getModernBlockStateMap() {
        return this.modernBlockStateMap;
    }

    @Override
    public cn.starry.narcissus.functions.modern.BlockEntityTypeMap getBlockEntityTypeMap() {
        return this.blockEntityTypeMap;
    }

    @Override
    public BlockLightingProperties getLightingProperties() {
        return this.lightingProperties;
    }

    @Override
    public int getSeaLevel() {
        return this.configuredDimension == -1 ? 32 : 63;
    }

    // ---- ModernProtocol packet factories (774 shapes; 776 overrides) ----

    @Override
    public cn.starry.narcissus.functions.packets.out.PacketOut createLoginSuccess(java.util.UUID uuid, String username) {
        return new PacketLoginOutSuccess774(uuid, username);
    }

    @Override
    public cn.starry.narcissus.functions.packets.out.PacketOut createJoinGame(
            int entityId, String[] worldNames, int maxPlayers, int viewDistance,
            int simulationDistance, boolean reducedDebugInfo, int dimensionTypeId,
            String dimensionName, int gameMode, int seaLevel) {
        return new PacketPlayOutJoinGame774(entityId, worldNames, maxPlayers, viewDistance,
                simulationDistance, reducedDebugInfo, dimensionTypeId, dimensionName, gameMode, seaLevel);
    }

    @Override
    public cn.starry.narcissus.functions.packets.out.PacketOut createEntityMetadata(int entityId, int skinParts) {
        return new PacketPlayOutEntityMetadata774(entityId, skinParts);
    }

    @Override
    public cn.starry.narcissus.functions.packets.out.PacketOut createPlayerNpc(
            int entityId, java.util.UUID uuid, double x, double y, double z,
            float yaw, float pitch) {
        return new PacketPlayOutAddEntity774(entityId, uuid, playerEntityTypeId(), x, y, z, yaw, pitch);
    }

    @Override
    public cn.starry.narcissus.functions.packets.out.PacketOut createNpcSeat(
            int entityId, double x, double y, double z, float yaw, float pitch) {
        return new PacketPlayOutAddEntity774(entityId, new java.util.UUID(0L, entityId),
                5, x, y, z, yaw, pitch);
    }

    @Override
    public cn.starry.narcissus.functions.packets.out.PacketOut createNpcMetadata(
            int entityId, int skinParts, int poseId, boolean sleeping,
            int sleepingX, int sleepingY, int sleepingZ) {
        return new PacketPlayOutEntityMetadata774(entityId, skinParts, poseId,
                sleeping, sleepingX, sleepingY, sleepingZ);
    }

    @Override
    public cn.starry.narcissus.functions.packets.out.PacketOut createNpcSeatMetadata(int entityId) {
        return new PacketPlayOutEntityMetadata774(entityId);
    }

    @Override
    public cn.starry.narcissus.functions.packets.out.PacketOut createRemoveEntities(int... entityIds) {
        return new PacketPlayOutRemoveEntities774(entityIds);
    }

    @Override
    public cn.starry.narcissus.functions.packets.out.PacketOut createSetPassengers(
            int vehicleId, int... passengerIds) {
        return new PacketPlayOutSetPassengers774(vehicleId, passengerIds);
    }

    @Override
    public cn.starry.narcissus.functions.packets.out.PacketOut createTeleportEntity(
            int entityId, double x, double y, double z, float yaw, float pitch) {
        return new PacketPlayOutTeleportEntity774(entityId, x, y, z, yaw, pitch);
    }

    @Override
    public cn.starry.narcissus.functions.packets.out.PacketOut createPlayerInfoUpdate(
            java.util.List<cn.starry.narcissus.functions.packets.PlayerListEntry> entries) {
        return new PacketPlayOutPlayerInfoUpdate774(entries);
    }

    @Override
    public cn.starry.narcissus.functions.packets.out.PacketOut createPlayerInfoRemove(
            java.util.List<java.util.UUID> uuids) {
        return new PacketPlayOutPlayerInfoRemove774(uuids);
    }

    @Override
    public cn.starry.narcissus.functions.packets.out.PacketOut createHiddenNameTagTeam(
            String teamName, String memberName) {
        return new PacketPlayOutSetPlayerTeam774(teamName, memberName);
    }

    @Override
    public cn.starry.narcissus.functions.packets.out.PacketOut createTabList(String header, String footer) {
        return new PacketPlayOutTabList774(header, footer);
    }

    @Override
    public cn.starry.narcissus.functions.packets.out.PacketOut createSetTime(
            long gameTime, long worldClockTime, long dayTime) {
        return new PacketPlayOutSetTime774(gameTime, dayTime);
    }

    @Override
    public cn.starry.narcissus.functions.packets.out.PacketOut createLightUpdate(
            int chunkX, int chunkZ, cn.starry.narcissus.functions.world.WorldLighting lighting,
            java.util.BitSet skySections, java.util.BitSet blockSections) {
        return new PacketPlayOutLightUpdate774(chunkX, chunkZ, lighting,
                skySections, blockSections, hasByteArrayLightMasks());
    }

    // ---- AbstractProtocol abstract methods ----
    // The modern flow is driven by ModernConfigurationDriver, so the legacy
    // per-packet senders are not used. keepAlive is wired for the shared timeout
    // loop; the rest are no-ops for modern clients.

    @Override
    public void sendKeepAlive(PlayerManager playerManager, int id) {
        long keepAliveId = id & 0xFFFFFFFFL;
        playerManager.setLastSentKeepAliveId(keepAliveId);
        this.networkManager.sendPacket(playerManager, new PacketOutKeepAlive774(keepAliveId));
    }

    @Override
    public void sendChunk(PlayerManager playerManager, Chunk chunk) {
        // Legacy schematic chunks are not sent to modern clients.
    }

    @Override
    public void sendSign(PlayerManager playerManager, SignTileEntity signTileEntity) {
        // Not supported for the modern limbo.
    }

    @Override
    public void disconnect(PlayerManager playerManager, String message) {
        if (playerManager.getStatus() == Status.LOGIN) {
            this.networkManager.sendPacket(playerManager, new PacketOutDisconnect(message));
        } else {
            this.networkManager.sendPacket(playerManager, new PacketOutDisconnect774(message));
        }
    }

    @Override
    public void sendJoinGame(PlayerManager playerManager, int entityId, byte gameMode, int dimension,
                             byte difficulty, byte maxPlayers, String levelType, boolean debugInfo) {
        // Modern join is composed by ModernConfigurationDriver.enterPlay().
    }

    @Override
    public void sendPosition(PlayerManager playerManager, double x, double y, double z, float yaw, float pitch) {
        // Modern position is sent by ModernConfigurationDriver.enterPlay().
    }
}
