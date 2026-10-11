package cn.starry.narcissus.functions.packets.out.protocol.v777;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;

import java.io.IOException;

/** 26.3 Join Game: CommonPlayerSpawnInfo, then onlineMode and enforcesSecureChat. */
public final class PacketPlayOutJoinGame777 implements PacketOut {
    private final int entityId;
    private final String[] worldNames;
    private final int maxPlayers;
    private final int viewDistance;
    private final int simulationDistance;
    private final boolean reducedDebugInfo;
    private final int dimensionTypeId;
    private final String dimensionName;
    private final int gameMode;
    private final int seaLevel;

    public PacketPlayOutJoinGame777(int entityId, String[] worldNames, int maxPlayers,
                                   int viewDistance, int simulationDistance,
                                   boolean reducedDebugInfo, int dimensionTypeId,
                                   String dimensionName, int gameMode, int seaLevel) {
        this.entityId = entityId;
        this.worldNames = worldNames.clone();
        this.maxPlayers = maxPlayers;
        this.viewDistance = viewDistance;
        this.simulationDistance = simulationDistance;
        this.reducedDebugInfo = reducedDebugInfo;
        this.dimensionTypeId = dimensionTypeId;
        this.dimensionName = dimensionName;
        this.gameMode = gameMode;
        this.seaLevel = seaLevel;
    }

    @Override
    public void write(PacketSerializer out) throws IOException {
        out.writeInt(this.entityId);
        out.writeBoolean(false); // hardcore
        out.writeVarInt(this.worldNames.length);
        for (String worldName : this.worldNames) {
            out.writeString(worldName);
        }
        out.writeVarInt(this.maxPlayers);
        out.writeVarInt(this.viewDistance);
        out.writeVarInt(this.simulationDistance);
        out.writeBoolean(this.reducedDebugInfo);
        out.writeBoolean(true); // show death screen
        out.writeBoolean(false); // limited crafting

        out.writeVarInt(this.dimensionTypeId);
        out.writeString(this.dimensionName);
        out.writeLong(0L); // hashed seed
        out.writeVarInt(this.gameMode);
        out.writeVarInt(0); // OPTIONAL_VAR_INT: zero means no previous game mode
        out.writeBoolean(false); // debug world
        out.writeBoolean(false); // flat world
        out.writeBoolean(false); // last death location absent
        out.writeVarInt(0); // portal cooldown
        out.writeVarInt(this.seaLevel);

        out.writeBoolean(false); // online mode (offline limbo behind the proxy)
        out.writeBoolean(false); // 26.3: secure chat is not enforced
    }
}
