package cn.starry.narcissus.functions.packets.out.protocol.v774;

import cn.starry.narcissus.functions.modern.Nbt;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.PlayerListEntry;
import java.io.IOException;
import java.util.List;

/** Shared wire primitives, not packet ids, for native modern protocol bodies. */
public final class ModernPacketEncoding {
    private ModernPacketEncoding() {}

    public static long position(int x, int y, int z) {
        return ((x & 0x3ffffffL) << 38) | ((z & 0x3ffffffL) << 12) | (y & 0xfffL);
    }

    public static byte angle(float degrees) {
        return (byte) Math.floor(degrees * 256.0 / 360.0);
    }

    public static void component(PacketSerializer out, String text) throws IOException {
        out.writeRawBytes(new Nbt.NbtString(text).toNetworkBytes());
    }

    public static void playerInfo(PacketSerializer out, List<PlayerListEntry> entries) throws IOException {
        out.writeByte((byte) 0x3d); // add, gamemode, listed, latency, display name
        out.writeVarInt(entries.size());
        for (PlayerListEntry entry : entries) {
            out.writeUUID(entry.getUuid());
            out.writeString(entry.getUsername());
            out.writeVarInt(entry.getProperties().size());
            for (PlayerListEntry.ProfileProperty property : entry.getProperties()) {
                out.writeString(property.getName());
                out.writeString(property.getValue());
                out.writeBoolean(property.getSignature() != null);
                if (property.getSignature() != null) out.writeString(property.getSignature());
            }
            out.writeVarInt(entry.getGameMode());
            out.writeBoolean(entry.isListed());
            out.writeVarInt(0); // latency
            out.writeBoolean(true);
            component(out, "§7" + entry.getUsername());
        }
    }

    public static void positionMove(PacketSerializer out, double x, double y, double z,
                                    float yaw, float pitch) throws IOException {
        out.writeDouble(x);
        out.writeDouble(y);
        out.writeDouble(z);
        out.writeDouble(0.0); // velocity
        out.writeDouble(0.0);
        out.writeDouble(0.0);
        out.writeFloat(yaw);
        out.writeFloat(pitch);
        out.writeInt(0); // no relative PositionMoveRotation flags
    }
}
