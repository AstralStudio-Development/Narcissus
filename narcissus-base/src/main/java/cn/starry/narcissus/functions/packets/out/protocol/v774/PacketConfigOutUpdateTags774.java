package cn.starry.narcissus.functions.packets.out.protocol.v774;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import java.io.IOException;

/** A pre-encoded registry/tag map, including its count. */
public record PacketConfigOutUpdateTags774(byte[] body) implements PacketOut {
    public PacketConfigOutUpdateTags774 { body = body.clone(); }
    @Override public void write(PacketSerializer out) throws IOException { out.writeRawBytes(body); }
}
