package cn.starry.narcissus.functions.packets.out.protocol.v776;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import cn.starry.narcissus.functions.packets.out.protocol.v774.ModernPacketEncoding;
import java.io.IOException;

/** 26.2 team parameters reorder text fields and make the colour optional. */
public record PacketPlayOutSetPlayerTeam776(String teamName, String memberName) implements PacketOut {
    @Override public void write(PacketSerializer out) throws IOException {
        out.writeString(teamName);
        out.writeByte((byte) 0);
        ModernPacketEncoding.component(out, memberName);
        ModernPacketEncoding.component(out, "");
        ModernPacketEncoding.component(out, "");
        out.writeVarInt(1); // visibility NEVER
        out.writeVarInt(0); // collision ALWAYS
        out.writeBoolean(false); // no team colour
        out.writeByte((byte) 0); // team options
        out.writeVarInt(1);
        out.writeString(memberName);
    }
}
