package cn.starry.narcissus.functions.packets.out.protocol.v774;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;

/** chunk_batch_start is a genuine zero-field packet; it brackets the following chunks. */
public final class PacketPlayOutChunkBatchStart774 implements PacketOut {
    @Override public void write(PacketSerializer out) { /* No wire body by specification. */ }
}
