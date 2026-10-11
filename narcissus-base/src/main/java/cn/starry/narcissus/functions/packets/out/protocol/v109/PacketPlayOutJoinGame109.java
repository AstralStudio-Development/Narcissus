package cn.starry.narcissus.functions.packets.out.protocol.v109;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;

import java.io.IOException;

/**
 * 1.9 Join Game (0x23): Int entity id, UByte gamemode, Int dimension, UByte
 * difficulty, UByte max players, String level type, Bool reduced debug.
 */
public final class PacketPlayOutJoinGame109 implements PacketOut {
    private final int entityId;
    private final byte gameMode;
    private final int dimension;
    private final byte difficulty;
    private final byte maxPlayers;
    private final String levelType;
    private final boolean debugInfo;

    public PacketPlayOutJoinGame109(int entityId, byte gameMode, int dimension,
                                    byte difficulty, byte maxPlayers,
                                    String levelType, boolean debugInfo) {
        this.entityId = entityId;
        this.gameMode = gameMode;
        this.dimension = dimension;
        this.difficulty = difficulty;
        this.maxPlayers = maxPlayers;
        this.levelType = levelType;
        this.debugInfo = debugInfo;
    }

    @Override
    public void write(PacketSerializer out) throws IOException {
        out.writeInt(this.entityId);
        out.writeByte(this.gameMode);
        out.writeInt(this.dimension);
        out.writeByte(this.difficulty);
        out.writeByte(this.maxPlayers);
        out.writeString(this.levelType);
        out.writeBoolean(this.debugInfo);
    }
}
