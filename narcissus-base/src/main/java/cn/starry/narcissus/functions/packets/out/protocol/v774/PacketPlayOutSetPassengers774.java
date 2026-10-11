package cn.starry.narcissus.functions.packets.out.protocol.v774;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import java.io.IOException;

public class PacketPlayOutSetPassengers774 implements PacketOut {
    private final int vehicleId;
    private final int[] passengerIds;
    public PacketPlayOutSetPassengers774(int vehicleId, int... passengerIds) {
        this.vehicleId = vehicleId;
        this.passengerIds = passengerIds.clone();
    }
    @Override public void write(PacketSerializer out) throws IOException {
        out.writeVarInt(vehicleId);
        out.writeVarInt(passengerIds.length);
        for (int id : passengerIds) out.writeVarInt(id);
    }
}
