package cn.starry.narcissus.functions.packets.out.protocol.v774;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import java.io.IOException;

/** The verified fixture already contains the entire registry_data body. */
public record PacketConfigOutRegistryData774(byte[] body) implements PacketOut {
    public PacketConfigOutRegistryData774 { body = body.clone(); }
    @Override public void write(PacketSerializer out) throws IOException { out.writeRawBytes(body); }
}
