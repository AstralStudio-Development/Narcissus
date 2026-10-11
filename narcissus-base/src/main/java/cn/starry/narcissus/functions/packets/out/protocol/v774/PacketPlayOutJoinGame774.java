package cn.starry.narcissus.functions.packets.out.protocol.v774;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import java.io.IOException;

/** Login-to-Play body for 1.21.11 and 26.1, before 26.3's VarInt gamemodes. */
public final class PacketPlayOutJoinGame774 implements PacketOut {
    private final int entityId, maxPlayers, viewDistance, simulationDistance, dimensionTypeId, gameMode, seaLevel;
    private final String[] worldNames;
    private final String dimensionName;
    private final boolean reducedDebugInfo;

    public PacketPlayOutJoinGame774(int entityId, String[] worldNames, int maxPlayers,
            int viewDistance, int simulationDistance, boolean reducedDebugInfo,
            int dimensionTypeId, String dimensionName, int gameMode, int seaLevel) {
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

    @Override public void write(PacketSerializer out) throws IOException {
        out.writeInt(entityId);
        out.writeBoolean(false);
        out.writeVarInt(worldNames.length);
        for (String name : worldNames) out.writeString(name);
        out.writeVarInt(maxPlayers);
        out.writeVarInt(viewDistance);
        out.writeVarInt(simulationDistance);
        out.writeBoolean(reducedDebugInfo);
        out.writeBoolean(true); // death screen
        out.writeBoolean(false); // limited crafting
        out.writeVarInt(dimensionTypeId);
        out.writeString(dimensionName);
        out.writeLong(0L); // hashed seed
        out.writeByte((byte) gameMode);
        out.writeByte((byte) -1); // no previous gamemode
        out.writeBoolean(false); // debug
        out.writeBoolean(false); // flat
        out.writeBoolean(false); // no last death position
        out.writeVarInt(0); // portal cooldown
        out.writeVarInt(seaLevel);
        out.writeBoolean(false); // secure chat not enforced
    }
}
