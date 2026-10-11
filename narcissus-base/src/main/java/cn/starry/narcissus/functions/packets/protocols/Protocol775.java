package cn.starry.narcissus.functions.packets.protocols;

import cn.starry.narcissus.functions.managers.NetworkManager;

/**
 * Minecraft 26.1, 26.1.1 and 26.1.2 (protocol 775).
 *
 * The relevant packet ids match 26.2, but Login Success, Join Game and team
 * parameters still use the older 1.21.11 layouts. World clocks and chunk
 * section markers use the newer layout. Registry data is version-specific.
 * Full registry entries are sent even when a 26.1.x client declines the 26.1
 * known-pack version, so patch releases do not depend on that negotiation.
 */
public class Protocol775 extends Protocol776 {
    public Protocol775(NetworkManager networkManager) {
        super(networkManager);
        registerPacketOut(cn.starry.narcissus.functions.packets.Status.LOGIN, 0x02,
                cn.starry.narcissus.functions.packets.out.protocol.v774.PacketLoginOutSuccess774.class);
        registerPacketOut(cn.starry.narcissus.functions.packets.Status.PLAY, idPlayOutJoinGame(),
                cn.starry.narcissus.functions.packets.out.protocol.v774.PacketPlayOutJoinGame774.class);
        registerPacketOut(cn.starry.narcissus.functions.packets.Status.PLAY, idPlayOutSetPlayerTeam(),
                cn.starry.narcissus.functions.packets.out.protocol.v774.PacketPlayOutSetPlayerTeam774.class);
    }

    @Override
    protected String registryVersionDir() {
        return "v775";
    }

    @Override
    public String getCorePackVersion() {
        return "26.1";
    }

    @Override
    public int getBiomeId() {
        return switch (getDimensionName()) {
            case "minecraft:overworld" -> 40;
            case "minecraft:the_nether" -> 34;
            default -> 56;
        };
    }

    @Override
    public cn.starry.narcissus.functions.packets.out.PacketOut createLoginSuccess(java.util.UUID uuid, String username) {
        return new cn.starry.narcissus.functions.packets.out.protocol.v774.PacketLoginOutSuccess774(uuid, username);
    }

    @Override
    public cn.starry.narcissus.functions.packets.out.PacketOut createJoinGame(int entityId, String[] worldNames,
            int maxPlayers, int viewDistance, int simulationDistance, boolean reducedDebugInfo,
            int dimensionTypeId, String dimensionName, int gameMode, int seaLevel) {
        return new cn.starry.narcissus.functions.packets.out.protocol.v774.PacketPlayOutJoinGame774(entityId,
                worldNames, maxPlayers, viewDistance, simulationDistance, reducedDebugInfo,
                dimensionTypeId, dimensionName, gameMode, seaLevel);
    }

    @Override
    public cn.starry.narcissus.functions.packets.out.PacketOut createHiddenNameTagTeam(String teamName, String memberName) {
        return new cn.starry.narcissus.functions.packets.out.protocol.v774.PacketPlayOutSetPlayerTeam774(teamName, memberName);
    }
    @Override
    protected int playerEntityTypeId() { return 155; }

    @Override
    public int[] getVersions() {
        return new int[]{775};
    }
}
