package cn.starry.narcissus.functions.packets.out.protocol.v774;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import java.io.IOException;

public record PacketPlayOutPlayerAbilities774(byte flags, float flyingSpeed, float walkingSpeed) implements PacketOut {
    @Override public void write(PacketSerializer out) throws IOException {
        out.writeByte(flags);
        out.writeFloat(flyingSpeed);
        out.writeFloat(walkingSpeed);
    }
}
