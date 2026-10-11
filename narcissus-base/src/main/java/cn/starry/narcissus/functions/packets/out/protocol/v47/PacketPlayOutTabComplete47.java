package cn.starry.narcissus.functions.packets.out.protocol.v47;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;

import java.io.IOException;

/**
 * 1.8 Tab Complete (0x3A): VarInt match count then each match as a string.
 */
public final class PacketPlayOutTabComplete47 implements PacketOut {
    private final String[] matches;

    public PacketPlayOutTabComplete47(String... matches) {
        this.matches = matches.clone();
    }

    public PacketPlayOutTabComplete47(java.util.List<String> matches) {
        this.matches = matches.toArray(new String[0]);
    }

    @Override
    public void write(PacketSerializer out) throws IOException {
        out.writeVarInt(this.matches.length);
        for (String match : this.matches)
            out.writeString(match);
    }
}
