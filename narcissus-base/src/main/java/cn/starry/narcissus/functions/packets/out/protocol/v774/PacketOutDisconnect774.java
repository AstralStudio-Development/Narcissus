package cn.starry.narcissus.functions.packets.out.protocol.v774;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import java.io.IOException;

/** Configuration and Play reasons are unnamed NBT components, unlike Login. */
public record PacketOutDisconnect774(String reason) implements PacketOut {
    @Override public void write(PacketSerializer out) throws IOException {
        ModernPacketEncoding.component(out, reason);
    }
}
