package cn.starry.narcissus.functions.packets.out.protocol.v774;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import java.io.IOException;

public record PacketOutKeepAlive774(long id) implements PacketOut {
    @Override public void write(PacketSerializer out) throws IOException { out.writeLong(id); }
}
