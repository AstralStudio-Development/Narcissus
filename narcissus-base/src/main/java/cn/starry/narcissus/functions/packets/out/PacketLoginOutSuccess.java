package cn.starry.narcissus.functions.packets.out;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/** Pre-1.16 Login Success uses textual UUIDs. */
public final class PacketLoginOutSuccess implements PacketOut {
    private final UUID uuid;
    private final String username;

    public PacketLoginOutSuccess(UUID uuid, String username) {
        this.uuid = Objects.requireNonNull(uuid);
        this.username = Objects.requireNonNull(username);
    }

    @Override
    public void write(PacketSerializer out) throws IOException {
        out.writeString(this.uuid.toString());
        out.writeString(this.username);
    }
}
