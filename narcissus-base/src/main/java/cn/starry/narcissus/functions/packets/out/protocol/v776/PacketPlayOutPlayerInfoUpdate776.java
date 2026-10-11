package cn.starry.narcissus.functions.packets.out.protocol.v776;

import cn.starry.narcissus.functions.packets.PlayerListEntry;
import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketPlayOutPlayerInfoUpdate774;
import java.util.List;

public final class PacketPlayOutPlayerInfoUpdate776 extends PacketPlayOutPlayerInfoUpdate774 {
    public PacketPlayOutPlayerInfoUpdate776(List<PlayerListEntry> entries) { super(entries); }
}
