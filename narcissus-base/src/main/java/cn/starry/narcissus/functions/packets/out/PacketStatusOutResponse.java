package cn.starry.narcissus.functions.packets.out;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import java.io.IOException;
import java.util.Objects;

public final class PacketStatusOutResponse implements PacketOut {
    private final String response;

    public PacketStatusOutResponse(String response) {
        this.response = Objects.requireNonNull(response);
    }

    @Override
    public void write(PacketSerializer out) throws IOException {
        out.writeString(this.response);
    }
}
