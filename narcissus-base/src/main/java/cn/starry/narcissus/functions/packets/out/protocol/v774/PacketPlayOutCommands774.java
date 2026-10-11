package cn.starry.narcissus.functions.packets.out.protocol.v774;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

/** An executable literal node for each published command, all below one root. */
public final class PacketPlayOutCommands774 implements PacketOut {
    private final List<String> commands;
    public PacketPlayOutCommands774(Collection<String> commands) { this.commands = List.copyOf(commands); }
    @Override public void write(PacketSerializer out) throws IOException {
        out.writeVarInt(commands.size() + 1);
        out.writeByte((byte) 0); // root
        out.writeVarInt(commands.size());
        for (int i = 1; i <= commands.size(); i++) out.writeVarInt(i);
        for (String command : commands) {
            out.writeByte((byte) 0x05); // literal, executable
            out.writeVarInt(0); // children
            out.writeString(command);
        }
        out.writeVarInt(0); // root index
    }
}
