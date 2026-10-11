package cn.starry.narcissus.functions.packets.out.protocol.v47;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;

import java.io.IOException;

/**
 * 1.8 Player List Header And Footer (0x47): two JSON chat components.
 */
public final class PacketPlayOutPlayerListHeaderFooter47 implements PacketOut {
    private final String header;
    private final String footer;

    public PacketPlayOutPlayerListHeaderFooter47(String header, String footer) {
        this.header = header;
        this.footer = footer;
    }

    @Override
    public void write(PacketSerializer out) throws IOException {
        out.writeString("{\"text\":\"" + quote(this.header) + "\"}");
        out.writeString("{\"text\":\"" + quote(this.footer) + "\"}");
    }

    private static String quote(String value) {
        String safe = value == null ? "" : value;
        return safe.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
