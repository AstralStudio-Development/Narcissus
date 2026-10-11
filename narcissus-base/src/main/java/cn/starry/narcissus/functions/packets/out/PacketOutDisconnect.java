package cn.starry.narcissus.functions.packets.out;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.util.TextUtil;
import java.io.IOException;

/** Login and legacy Play disconnect reasons are JSON text components. */
public final class PacketOutDisconnect implements PacketOut {
    private final String reason;

    public PacketOutDisconnect(String reason) {
        this.reason = TextUtil.toJsonComponent(reason);
    }

    @Override
    public void write(PacketSerializer out) throws IOException {
        out.writeString(this.reason);
    }
}
