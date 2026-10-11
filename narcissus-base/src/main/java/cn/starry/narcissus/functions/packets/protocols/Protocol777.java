package cn.starry.narcissus.functions.packets.protocols;

import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.packets.Status;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import cn.starry.narcissus.functions.packets.out.protocol.v777.PacketPlayOutJoinGame777;

/**
 * Minecraft 26.3 (protocol 777).
 *
 * Packet ids follow Mojang's official 26.3 GameProtocols registration table.
 * add_transient_block, post_effects and swing_animation shift clientbound ids
 * by one, two or three; this must not be treated as an alias of protocol 776.
 * The serverbound packets consumed by this limbo retain their 26.2 ids.
 */
public class Protocol777 extends Protocol776 {
    public Protocol777(NetworkManager networkManager) {
        super(networkManager);
        registerPacketOut(Status.PLAY, idPlayOutJoinGame(), PacketPlayOutJoinGame777.class);
    }

    @Override
    public PacketOut createJoinGame(int entityId, String[] worldNames, int maxPlayers,
                                    int viewDistance, int simulationDistance,
                                    boolean reducedDebugInfo, int dimensionTypeId,
                                    String dimensionName, int gameMode, int seaLevel) {
        return new PacketPlayOutJoinGame777(entityId, worldNames, maxPlayers, viewDistance,
                simulationDistance, reducedDebugInfo, dimensionTypeId, dimensionName, gameMode, seaLevel);
    }

    @Override
    protected String registryVersionDir() {
        return "v777";
    }

    @Override
    public String getCorePackVersion() {
        return "26.3";
    }

    @Override
    public boolean hasByteArrayLightMasks() { return true; }
    @Override
    public int getBiomeId() {
        return switch (getDimensionName()) {
            case "minecraft:overworld" -> 41;
            case "minecraft:the_nether" -> 35;
            default -> 58;
        };
    }

    @Override
    protected int idConfigOutEnabledFeatures() { return 0x0d; }
    @Override
    protected int idConfigOutUpdateTags() { return 0x0e; }
    @Override
    protected int idConfigOutSelectKnownPacks() { return 0x0f; }
    @Override
    protected int playerEntityTypeId() { return 159; }

    @Override
    protected int idPlayOutGameEvent() { return 0x27; }
    @Override
    protected int idPlayOutKeepAlive() { return 0x2d; }
    @Override
    protected int idPlayOutChunk() { return 0x2e; }
    @Override
    protected int idPlayOutLightUpdate() { return 0x31; }
    @Override
    protected int idPlayOutJoinGame() { return 0x32; }
    @Override
    protected int idPlayOutAbilities() { return 0x41; }
    @Override
    protected int idPlayOutPosition() { return 0x49; }
    @Override
    protected int idPlayOutPlayerInfoRemove() { return 0x46; }
    @Override
    protected int idPlayOutPlayerInfoUpdate() { return 0x47; }
    @Override
    protected int idPlayOutRemoveEntities() { return 0x4e; }
    @Override
    protected int idPlayOutChunkCenter() { return 0x60; }
    @Override
    protected int idPlayOutEntityMetadata() { return 0x65; }
    @Override
    protected int idPlayOutSetPassengers() { return 0x6d; }
    @Override
    protected int idPlayOutSetPlayerTeam() { return 0x6f; }
    @Override
    protected int idPlayOutSetTime() { return 0x73; }
    @Override
    protected int idPlayOutSystemChat() { return 0x7c; }
    @Override
    protected int idPlayOutTabList() { return 0x7d; }
    @Override
    protected int idPlayOutTeleportEntity() { return 0x80; }

    @Override
    public int[] getVersions() {
        return new int[]{777};
    }
}
