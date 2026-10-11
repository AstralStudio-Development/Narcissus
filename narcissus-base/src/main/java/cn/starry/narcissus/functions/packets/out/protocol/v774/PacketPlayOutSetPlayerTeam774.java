package cn.starry.narcissus.functions.packets.out.protocol.v774;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import java.io.IOException;

/** Create a one-member team hiding the NPC's nametag. */
public record PacketPlayOutSetPlayerTeam774(String teamName, String memberName) implements PacketOut {
    @Override public void write(PacketSerializer out) throws IOException {
        out.writeString(teamName);
        out.writeByte((byte) 0); // create
        ModernPacketEncoding.component(out, memberName);
        out.writeByte((byte) 0); // team options
        out.writeVarInt(1); // visibility NEVER
        out.writeVarInt(0); // collision ALWAYS
        out.writeVarInt(21); // ChatFormatting.RESET
        ModernPacketEncoding.component(out, "");
        ModernPacketEncoding.component(out, "");
        out.writeVarInt(1);
        out.writeString(memberName);
    }
}
