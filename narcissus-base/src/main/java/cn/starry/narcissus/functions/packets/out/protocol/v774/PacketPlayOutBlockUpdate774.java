package cn.starry.narcissus.functions.packets.out.protocol.v774;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import java.io.IOException;

public record PacketPlayOutBlockUpdate774(long position, int stateId) implements PacketOut {
    @Override public void write(PacketSerializer out) throws IOException {
        out.writeLong(position);
        out.writeVarInt(stateId);
    }
}
