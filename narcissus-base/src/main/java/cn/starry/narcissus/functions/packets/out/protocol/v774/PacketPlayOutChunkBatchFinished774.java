package cn.starry.narcissus.functions.packets.out.protocol.v774;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import java.io.IOException;

public record PacketPlayOutChunkBatchFinished774(int batchSize) implements PacketOut {
    @Override public void write(PacketSerializer out) throws IOException { out.writeVarInt(batchSize); }
}
