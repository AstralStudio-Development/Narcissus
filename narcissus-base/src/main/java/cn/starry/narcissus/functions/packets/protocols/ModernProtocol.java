package cn.starry.narcissus.functions.packets.protocols;

import cn.starry.narcissus.functions.packets.PlayerListEntry;
import cn.starry.narcissus.functions.packets.out.PacketOut;

import java.util.List;
import java.util.UUID;

/**
 * Capabilities a modern (1.20.2+) protocol exposes to the shared
 * {@link ModernConfigurationDriver}: the registry data to send during
 * configuration and the dimension parameters used in Join Game and chunks.
 */
public interface ModernProtocol {

    /** Version advertised for the vanilla minecraft:core known pack. */
    String getCorePackVersion();

    /**
     * Pre-serialized registry_data bodies (one per registry), in send order.
     * When the client accepted minecraft:core, values may be omitted and loaded
     * from that known pack, matching vanilla's compact registry response.
     */
    List<byte[]> getRegistryBodies(boolean clientKnowsCore);

    /**
     * Non-null pre-serialized update_tags body (after the packet id).
     * Required vanilla tag fixtures are validated during protocol initialization.
     */
    byte[] getUpdateTagsBody();

    /** Registry index of the limbo dimension within the dimension_type registry. */
    int getDimensionTypeId();

    /** Identifier of the limbo dimension / world name (e.g. "minecraft:the_end"). */
    String getDimensionName();

    /** Number of 16-block sections for the chosen dimension height. */
    int getSectionCount();

    /** Lowest absolute block Y represented by section zero in the chosen dimension. */
    int getMinY();

    /** Single biome registry id used to fill chunk biome palettes. */
    int getBiomeId();

    /** Whether the selected dimension has skylight in its official codec. */
    boolean hasSkyLight();

    /** Whether this protocol writes the sample 26.2 section marker after blockCount. */
    boolean hasSectionBlockCountMarker();

    /** 26.3 replaces variable BitSet long arrays with little-endian byte arrays. */
    default boolean hasByteArrayLightMasks() { return false; }

    /** Maps legacy schematic id:meta to this protocol's global block-state ids. */
    cn.starry.narcissus.functions.modern.LegacyBlockMap getLegacyBlockMap();

    /** Maps canonical Sponge block-state strings to this protocol's global ids. */
    cn.starry.narcissus.functions.modern.ModernBlockStateMap getModernBlockStateMap();

    /** Maps modern block-entity identifiers to this protocol's registry ids. */
    cn.starry.narcissus.functions.modern.BlockEntityTypeMap getBlockEntityTypeMap();

    /** Exact official per-global-state lighting properties for this protocol. */
    cn.starry.narcissus.functions.world.lighting.BlockLightingProperties getLightingProperties();

    /** Sea level reported in Join Game. */
    int getSeaLevel();

    /** Protocol-correct Login Success packet (776 appends a session UUID). */
    PacketOut createLoginSuccess(UUID uuid, String username);

    /** Protocol-correct Join Game packet (776 appends onlineMode). */
    PacketOut createJoinGame(int entityId, String[] worldNames, int maxPlayers, int viewDistance,
                             int simulationDistance, boolean reducedDebugInfo, int dimensionTypeId,
                             String dimensionName, int gameMode, int seaLevel);

    /** Creates the protocol-correct entity metadata update for skin layers. */
    PacketOut createEntityMetadata(int entityId, int skinParts);

    /** Creates the protocol-correct player-shaped NPC spawn packet. */
    PacketOut createPlayerNpc(int entityId, UUID uuid, double x, double y, double z,
                              float yaw, float pitch);

    /** Creates the invisible marker ArmorStand used as NPC1's seat. */
    PacketOut createNpcSeat(int entityId, double x, double y, double z,
                            float yaw, float pitch);

    /** Creates player metadata containing skin layers and the requested NPC pose. */
    PacketOut createNpcMetadata(int entityId, int skinParts, int poseId,
                                boolean sleeping, int sleepingX, int sleepingY, int sleepingZ);

    /** Creates invisible/no-gravity/marker metadata for NPC1's ArmorStand seat. */
    PacketOut createNpcSeatMetadata(int entityId);

    /** Creates the protocol-correct remove_entities packet. */
    PacketOut createRemoveEntities(int... entityIds);

    /** Creates the protocol-correct set_passengers packet. */
    PacketOut createSetPassengers(int vehicleId, int... passengerIds);

    PacketOut createTeleportEntity(int entityId, double x, double y, double z,
                                    float yaw, float pitch);

    PacketOut createPlayerInfoUpdate(List<PlayerListEntry> entries);


    /** Creates the protocol-correct player_info_remove packet. */
    PacketOut createPlayerInfoRemove(List<UUID> uuids);

    /** Creates a private scoreboard team whose members never render nametags. */
    PacketOut createHiddenNameTagTeam(String teamName, String memberName);

    /** Creates the protocol-correct tab_list header/footer packet. */
    PacketOut createTabList(String header, String footer);

    /** Creates the protocol-correct frozen world-time update. */
    PacketOut createSetTime(long gameTime, long worldClockTime, long dayTime);

    /** Creates the protocol-correct incremental light update for changed sections. */
    PacketOut createLightUpdate(int chunkX, int chunkZ,
                                cn.starry.narcissus.functions.world.WorldLighting lighting,
                                java.util.BitSet skySections, java.util.BitSet blockSections);
}
