package cn.starry.narcissus.functions.packets.out.protocol.v47;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;

import java.io.IOException;

/**
 * 1.8 Teams (0x3E). Creates a team that hides nametags for its members:
 * name, mode 0 create, display name/prefix/suffix, friendly flags,
 * name-tag visibility "never", color 0xFF (1.8 format) followed by the
 * member count and names.
 */
public final class PacketPlayOutTeams47 implements PacketOut {
    private final String teamName;
    private final String memberName;

    public PacketPlayOutTeams47(String teamName, String memberName) {
        this.teamName = teamName;
        this.memberName = memberName;
    }

    @Override
    public void write(PacketSerializer out) throws IOException {
        out.writeString(this.teamName);
        out.writeByte((byte) 0); // mode: create team
        out.writeString(this.memberName); // team display name
        out.writeString(""); // prefix
        out.writeString(""); // suffix
        out.writeByte((byte) 0); // friendly fire / see invisible flags
        out.writeString("never"); // name tag visibility
        out.writeByte((byte) 0xFF); // color: reset (1.8 packet format)
        out.writeVarInt(1);
        out.writeString(this.memberName);
    }
}
