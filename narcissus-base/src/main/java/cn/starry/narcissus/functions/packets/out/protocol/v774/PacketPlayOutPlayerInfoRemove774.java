package cn.starry.narcissus.functions.packets.out.protocol.v774;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class PacketPlayOutPlayerInfoRemove774 implements PacketOut {
    private final List<UUID> uuids;
    public PacketPlayOutPlayerInfoRemove774(List<UUID> uuids) { this.uuids = List.copyOf(uuids); }
    @Override public void write(PacketSerializer out) throws IOException {
        out.writeVarInt(uuids.size());
        for (UUID uuid : uuids) out.writeUUID(uuid);
    }
}
