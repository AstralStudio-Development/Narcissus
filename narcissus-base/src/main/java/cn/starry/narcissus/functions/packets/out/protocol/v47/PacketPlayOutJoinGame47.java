package cn.starry.narcissus.functions.packets.out.protocol.v47;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;

import java.io.IOException;

/** 1.8 Join Game (0x01): Int, UByte, Int, UByte, UByte, String, Bool. */
public final class PacketPlayOutJoinGame47 implements PacketOut {
    private final int entityId;
    private final byte gameMode;
    private final byte dimension;
    private final byte difficulty;
    private final byte maxPlayers;
    private final String levelType;
    private final boolean debugInfo;

    public PacketPlayOutJoinGame47(int entityId, byte gameMode, byte dimension,
                                   byte difficulty, byte maxPlayers, String levelType,
                                   boolean debugInfo) {
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
