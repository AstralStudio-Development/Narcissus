package cn.starry.narcissus.functions.packets.out.protocol.v776;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketPlayOutJoinGame774;
import java.io.IOException;

/** 26.2 inserts onlineMode before the trailing enforcesSecureChat boolean. */
public final class PacketPlayOutJoinGame776 implements PacketOut {
    private final PacketPlayOutJoinGame774 common;
    public PacketPlayOutJoinGame776(int entityId, String[] worldNames, int maxPlayers,
            int viewDistance, int simulationDistance, boolean reducedDebugInfo,
            int dimensionTypeId, String dimensionName, int gameMode, int seaLevel) {
        common = new PacketPlayOutJoinGame774(entityId, worldNames, maxPlayers, viewDistance,
                simulationDistance, reducedDebugInfo, dimensionTypeId, dimensionName, gameMode, seaLevel);
    }
    @Override public void write(PacketSerializer out) throws IOException {
        // Both trailing fields are false in offline Limbo, so the shared body's
        // false and this false represent onlineMode and enforcesSecureChat respectively.
        common.write(out);
        out.writeBoolean(false);
    }
}
