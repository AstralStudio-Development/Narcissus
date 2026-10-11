package cn.starry.narcissus.functions.packets.out;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import java.io.IOException;

public final class PacketStatusOutPong implements PacketOut {
    private final long payload;

    public PacketStatusOutPong(long payload) {
        this.payload = payload;
    }

    @Override
    public void write(PacketSerializer out) throws IOException {
        out.writeLong(this.payload);
    }
}
