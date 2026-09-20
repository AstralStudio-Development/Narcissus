package cn.starry.narcissus.functions.packets.protocols;

import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.packets.Status;
import cn.starry.narcissus.functions.packets.out.protocol.v776.PacketLoginOutSuccess776;
import cn.starry.narcissus.functions.packets.out.protocol.v776.PacketPlayOutEntityMetadata776;
import cn.starry.narcissus.functions.packets.out.protocol.v776.PacketPlayOutJoinGame776;
import cn.starry.narcissus.functions.packets.out.protocol.v776.PacketPlayOutPlayerInfoRemove776;
import cn.starry.narcissus.functions.packets.out.protocol.v776.PacketPlayOutPlayerInfoUpdate776;
import cn.starry.narcissus.functions.packets.out.protocol.v776.PacketPlayOutRemoveEntities776;
import cn.starry.narcissus.functions.packets.out.protocol.v776.PacketPlayOutSetPassengers776;
import cn.starry.narcissus.functions.packets.out.protocol.v776.PacketPlayOutSetPlayerTeam776;
import cn.starry.narcissus.functions.packets.out.protocol.v776.PacketPlayOutSetTime776;
import cn.starry.narcissus.functions.packets.out.protocol.v776.PacketPlayOutTabList776;
import cn.starry.narcissus.functions.packets.out.protocol.v776.PacketPlayOutTeleportEntity776;

/**
 * Native protocol implementation for Minecraft 26.2 (protocol 776), EXPERIMENTAL.
 *
 * Packet ids below are taken from Mojang's official 26.2 server data report
 * (generated via `net.minecraft.data.Main --reports`; see
 * protocol-data/reports-26.2/reports/packets.json and protocol-data/774-776-diff.txt).
 * In 26.2 the PLAY state shifted by two new packets inserted before the classic
 * range (game_rule_values at 0x27 and low_disk_space_warning at 0x32), moving
 * keep_alive/chunk/join/abilities/position/chunk-center/system-chat by +1 or +2
 * relative to 1.21.11. STATUS/LOGIN/CONFIGURATION ids are unchanged.
 *
 * Three packets also differ structurally in 26.2 vs 1.21.11: Login Success
 * appends a session UUID, Join Game appends onlineMode, and Set Time replaces
 * dayTime/tickDayTime with a world-clock state map. Those use dedicated v776
 * serializer classes below; unchanged packets reuse their 774 serializers.
 *
 * Until a real 26.2 client handshake has been exercised end-to-end, this path is
 * best-effort and logged as experimental.
 */
public class Protocol776 extends Protocol774 {

    // Official 26.2 PLAY ids (from packets.json); everything else inherits 774.
    private static final int P776_IN_KEEP_ALIVE    = 0x1c;
    private static final int P776_IN_PLAYER_ACTION = 0x29;
    private static final int P776_OUT_KEEP_ALIVE   = 0x2c;
    private static final int P776_OUT_CHUNK        = 0x2d;
    private static final int P776_OUT_LIGHT_UPDATE = 0x30;
    private static final int P776_OUT_JOIN_GAME    = 0x31;
    private static final int P776_OUT_ENTITY_METADATA = 0x63;
    private static final int P776_OUT_REMOVE_ENTITIES = 0x4d;
    private static final int P776_OUT_SET_PASSENGERS = 0x6b;
    private static final int P776_OUT_ABILITIES    = 0x40;
    private static final int P776_OUT_POSITION     = 0x48;
    private static final int P776_OUT_CHUNK_CENTER = 0x5e;
    private static final int P776_OUT_SET_TIME     = 0x71;
    private static final int P776_OUT_SYSTEM_CHAT  = 0x79;
    private static final int P776_OUT_TELEPORT_ENTITY = 0x7d;

    public Protocol776(NetworkManager networkManager) {
        super(networkManager);
        // Register the 776-specific serializer classes so getPacketOutByClass
        // resolves them to the correct (26.2) ids instead of the 774 classes.
        registerPacketOut(Status.LOGIN, 0x02, PacketLoginOutSuccess776.class);
        registerPacketOut(Status.PLAY, P776_OUT_JOIN_GAME, PacketPlayOutJoinGame776.class);
        registerPacketOut(Status.PLAY, P776_OUT_REMOVE_ENTITIES, PacketPlayOutRemoveEntities776.class);
        registerPacketOut(Status.PLAY, P776_OUT_ENTITY_METADATA, PacketPlayOutEntityMetadata776.class);
        registerPacketOut(Status.PLAY, P776_OUT_SET_PASSENGERS, PacketPlayOutSetPassengers776.class);
        registerPacketOut(Status.PLAY, 0x45, PacketPlayOutPlayerInfoRemove776.class);
        registerPacketOut(Status.PLAY, 0x46, PacketPlayOutPlayerInfoUpdate776.class);
        registerPacketOut(Status.PLAY, 0x6d, PacketPlayOutSetPlayerTeam776.class);
        registerPacketOut(Status.PLAY, P776_OUT_SET_TIME, PacketPlayOutSetTime776.class);
        registerPacketOut(Status.PLAY, 0x7a, PacketPlayOutTabList776.class);
        registerPacketOut(Status.PLAY, P776_OUT_TELEPORT_ENTITY, PacketPlayOutTeleportEntity776.class);
    }

    @Override
    protected String registryVersionDir() {
        return "v776";
    }

    @Override
    protected String knownRegistryVersionDir() {
        // Compact 26.2 fixtures have not been captured yet; full fixtures remain
        // valid after known-pack negotiation and are used as the fallback.
        return null;
    }

    @Override
    public String getCorePackVersion() {
        return "26.2";
    }

    @Override
    public cn.starry.narcissus.functions.packets.out.PacketOut createLoginSuccess(java.util.UUID uuid, String username) {
        return new PacketLoginOutSuccess776(uuid, username, new java.util.UUID(0L, 0L));
    }

    @Override
    public cn.starry.narcissus.functions.packets.out.PacketOut createJoinGame(
            int entityId, String[] worldNames, int maxPlayers, int viewDistance,
            int simulationDistance, boolean reducedDebugInfo, int dimensionTypeId,
            String dimensionName, int gameMode, int seaLevel) {
        return new PacketPlayOutJoinGame776(entityId, worldNames, maxPlayers, viewDistance,
                simulationDistance, reducedDebugInfo, dimensionTypeId, dimensionName, gameMode, seaLevel);
    }

    @Override
    public cn.starry.narcissus.functions.packets.out.PacketOut createEntityMetadata(int entityId, int skinParts) {
        return new PacketPlayOutEntityMetadata776(entityId, skinParts);
    }

    @Override
    public cn.starry.narcissus.functions.packets.out.PacketOut createPlayerNpc(
            int entityId, java.util.UUID uuid, double x, double y, double z,
            float yaw, float pitch) {
        return new cn.starry.narcissus.functions.packets.out.protocol.v774.PacketPlayOutAddEntity774(
                entityId, uuid, 156, x, y, z, yaw, pitch);
    }

    @Override
    public cn.starry.narcissus.functions.packets.out.PacketOut createNpcMetadata(
            int entityId, int skinParts, int poseId, boolean sleeping,
            int sleepingX, int sleepingY, int sleepingZ) {
        return new PacketPlayOutEntityMetadata776(entityId, skinParts, poseId,
                sleeping, sleepingX, sleepingY, sleepingZ);
    }

    @Override
    public cn.starry.narcissus.functions.packets.out.PacketOut createNpcSeatMetadata(int entityId) {
        return new PacketPlayOutEntityMetadata776(entityId);
    }

    @Override
    public cn.starry.narcissus.functions.packets.out.PacketOut createRemoveEntities(int... entityIds) {
        return new PacketPlayOutRemoveEntities776(entityIds);
    }

    @Override
    public cn.starry.narcissus.functions.packets.out.PacketOut createSetPassengers(
            int vehicleId, int... passengerIds) {
        return new PacketPlayOutSetPassengers776(vehicleId, passengerIds);
    }

    @Override
    public cn.starry.narcissus.functions.packets.out.PacketOut createTeleportEntity(
            int entityId, double x, double y, double z, float yaw, float pitch) {
        return new PacketPlayOutTeleportEntity776(entityId, x, y, z, yaw, pitch);
    }

    @Override
    public cn.starry.narcissus.functions.packets.out.PacketOut createPlayerInfoUpdate(
            java.util.List<cn.starry.narcissus.functions.packets.PlayerListEntry> entries) {
        return new PacketPlayOutPlayerInfoUpdate776(entries);
    }

    @Override
    public cn.starry.narcissus.functions.packets.out.PacketOut createPlayerInfoRemove(
            java.util.List<java.util.UUID> uuids) {
        return new PacketPlayOutPlayerInfoRemove776(uuids);
    }

    @Override
    public cn.starry.narcissus.functions.packets.out.PacketOut createHiddenNameTagTeam(
            String teamName, String memberName) {
        return new PacketPlayOutSetPlayerTeam776(teamName, memberName);
    }

    @Override
    public cn.starry.narcissus.functions.packets.out.PacketOut createTabList(String header, String footer) {
        return new PacketPlayOutTabList776(header, footer);
    }

    @Override
    public cn.starry.narcissus.functions.packets.out.PacketOut createSetTime(
            long gameTime, long worldClockTime, long dayTime) {
        return new PacketPlayOutSetTime776(gameTime, worldClockTime);
    }

    @Override
    public boolean hasSectionBlockCountMarker() { return true; }

    @Override
    protected int idPlayInChatCommand() { return 0x07; }
    @Override
    protected int idPlayInChatCommandSigned() { return 0x08; }
    @Override
    protected int idPlayInChat() { return 0x09; }
    @Override
    protected int idPlayInChatAck() { return 0x06; }
    @Override
    protected int idPlayInChatSession() { return 0x0a; }

    @Override
    protected int idPlayInCustomPayload() { return 0x16; }
    @Override
    protected int idPlayInCommandSuggestion() { return 0x0f; }
    @Override
    protected int idPlayInPosition() { return 0x1e; }
    @Override
    protected int idPlayInPositionAndLook() { return 0x1f; }
    @Override
    protected int idPlayInChunkBatchReceived() { return 0x0b; }
    @Override
    protected int idPlayInKeepAlive()    { return P776_IN_KEEP_ALIVE; }
    @Override
    protected int idPlayInPlayerAction() { return P776_IN_PLAYER_ACTION; }
    @Override
    protected int idPlayInPlayerLoaded() { return 0x2c; }
    @Override
    protected int idPlayOutKeepAlive()   { return P776_OUT_KEEP_ALIVE; }
    @Override
    protected int idPlayOutChunk()       { return P776_OUT_CHUNK; }
    @Override
    protected int idPlayOutLightUpdate() { return P776_OUT_LIGHT_UPDATE; }
    @Override
    protected int idPlayOutJoinGame()    { return P776_OUT_JOIN_GAME; }
    @Override
    protected int idPlayOutAbilities()   { return P776_OUT_ABILITIES; }
    @Override
    protected int idPlayOutPosition()    { return P776_OUT_POSITION; }
    @Override
    protected int idPlayOutEntityMetadata() { return P776_OUT_ENTITY_METADATA; }
    @Override
    protected int idPlayOutRemoveEntities() { return P776_OUT_REMOVE_ENTITIES; }
    @Override
    protected int idPlayOutSetPassengers() { return P776_OUT_SET_PASSENGERS; }
    @Override
    protected int idPlayOutChunkCenter() { return P776_OUT_CHUNK_CENTER; }
    @Override
    protected int idPlayOutSystemChat()  { return P776_OUT_SYSTEM_CHAT; }
    @Override
    protected int idPlayOutTabList() { return 0x7a; }
    @Override
    protected int idPlayOutTeleportEntity() { return P776_OUT_TELEPORT_ENTITY; }
    @Override
    protected int idPlayOutPlayerInfoRemove() { return 0x45; }
    @Override
    protected int idPlayOutPlayerInfoUpdate() { return 0x46; }
    @Override
    protected int idPlayOutSetPlayerTeam() { return 0x6d; }
    @Override
    protected int idPlayOutSetTime() { return P776_OUT_SET_TIME; }

    /**
     * 776 serverbound ids (from packets.json): client_tick_end(0x0d),
     * custom_payload(0x16), move_player_pos/pos_rot/rot/status_only(0x1e-0x21),
     * player_loaded(0x2c), pong(0x2d).
     */
    @Override
    protected int[] ignoredPlayServerboundIds() {
        return new int[]{0x0d, 0x1e, 0x1f, 0x20, 0x21, 0x2d};
    }

    @Override
    public int[] getVersions() {
        return new int[]{776};
    }
}
