package cn.starry.narcissus.functions.packets.out.protocol.v774;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.PlayerListEntry;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import java.io.IOException;
import java.util.List;

public class PacketPlayOutPlayerInfoUpdate774 implements PacketOut {
    private final List<PlayerListEntry> entries;
    public PacketPlayOutPlayerInfoUpdate774(List<PlayerListEntry> entries) { this.entries = List.copyOf(entries); }
    @Override public void write(PacketSerializer out) throws IOException { ModernPacketEncoding.playerInfo(out, entries); }
}
