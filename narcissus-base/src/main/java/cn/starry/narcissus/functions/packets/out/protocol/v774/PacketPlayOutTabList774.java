package cn.starry.narcissus.functions.packets.out.protocol.v774;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import java.io.IOException;

public class PacketPlayOutTabList774 implements PacketOut {
    private final String header, footer;
    public PacketPlayOutTabList774(String header, String footer) { this.header = header; this.footer = footer; }
    @Override public void write(PacketSerializer out) throws IOException {
        ModernPacketEncoding.component(out, header);
        ModernPacketEncoding.component(out, footer);
    }
}
