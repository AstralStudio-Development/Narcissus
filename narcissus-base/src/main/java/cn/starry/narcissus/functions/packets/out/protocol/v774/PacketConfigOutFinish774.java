package cn.starry.narcissus.functions.packets.out.protocol.v774;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;

/** finish_configuration is a genuine zero-field packet. */
public final class PacketConfigOutFinish774 implements PacketOut {
    @Override public void write(PacketSerializer out) { /* No wire body by specification. */ }
}
