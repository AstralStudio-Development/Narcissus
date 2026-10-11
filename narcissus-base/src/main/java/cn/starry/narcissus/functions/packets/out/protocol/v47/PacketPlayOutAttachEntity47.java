package cn.starry.narcissus.functions.packets.out.protocol.v47;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;

import java.io.IOException;

/** 1.8 Attach Entity (0x1B): Int riding, Int vehicle, Bool leash. */
public final class PacketPlayOutAttachEntity47 implements PacketOut {
    private final int ridingEntityId;
    private final int vehicleEntityId;

    public PacketPlayOutAttachEntity47(int ridingEntityId, int vehicleEntityId) {
        this.ridingEntityId = ridingEntityId;
        this.vehicleEntityId = vehicleEntityId;
    }

    @Override
    public void write(PacketSerializer out) throws IOException {
        out.writeInt(this.ridingEntityId);
        out.writeInt(this.vehicleEntityId);
        out.writeBoolean(false); // leash
    }
}
