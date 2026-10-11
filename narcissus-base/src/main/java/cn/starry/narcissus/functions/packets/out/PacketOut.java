package cn.starry.narcissus.functions.packets.out;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import java.io.IOException;

/** Writes a packet body; the network manager supplies the version-specific id and frame. */
@FunctionalInterface
public interface PacketOut {
    void write(PacketSerializer out) throws IOException;
}
