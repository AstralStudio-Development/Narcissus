package cn.starry.narcissus.functions.packets.out.protocol.v774;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import java.io.IOException;

public record PacketPlayOutSyncPosition774(int teleportId, double x, double y, double z,
        float yaw, float pitch) implements PacketOut {
    @Override public void write(PacketSerializer out) throws IOException {
        out.writeVarInt(teleportId);
        ModernPacketEncoding.positionMove(out, x, y, z, yaw, pitch);
    }
}
