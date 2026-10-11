package cn.starry.narcissus.functions.packets.out.protocol.v47;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;

import java.io.IOException;
import java.util.List;

/**
 * 1.8 Chat (0x02): JSON chat component string followed by the VarInt chat
 * type position byte (0 system, 1 chat, 2 action bar).
 */
public final class PacketPlayOutChat47 implements PacketOut {
    public static final byte CHAT = 0;
    public static final byte SYSTEM_MESSAGE = 1;
    public static final byte ACTION_BAR = 2;

    private final String json;
    private final byte position;

    public PacketPlayOutChat47(String json) {
        this(json, SYSTEM_MESSAGE);
    }

    public PacketPlayOutChat47(String json, boolean positionChat) {
        this(json, positionChat ? CHAT : SYSTEM_MESSAGE);
    }

    public PacketPlayOutChat47(String json, byte position) {
        this.json = json;
        this.position = position;
    }

    @Override
    public void write(PacketSerializer out) throws IOException {
        out.writeString(this.json);
        out.writeByte(this.position);
    }
}
