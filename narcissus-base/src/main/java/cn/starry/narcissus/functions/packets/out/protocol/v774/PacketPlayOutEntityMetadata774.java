package cn.starry.narcissus.functions.packets.out.protocol.v774;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import java.io.IOException;

/** Player skin/pose metadata, or an invisible marker armour stand used as a seat. */
public class PacketPlayOutEntityMetadata774 implements PacketOut {
    private final int entityId, skinParts, poseId;
    private final boolean seat, includePose, sleeping;
    private final long bedPosition;

    public PacketPlayOutEntityMetadata774(int entityId) {
        this(entityId, 0, 0, false, false, true, 0L);
    }
    public PacketPlayOutEntityMetadata774(int entityId, int skinParts) {
        this(entityId, skinParts, 0, false, false, false, 0L);
    }
    public PacketPlayOutEntityMetadata774(int entityId, int skinParts, int poseId,
            boolean sleeping, int x, int y, int z) {
        this(entityId, skinParts, poseId, true, sleeping, false, ModernPacketEncoding.position(x, y, z));
    }
    private PacketPlayOutEntityMetadata774(int entityId, int skinParts, int poseId,
            boolean includePose, boolean sleeping, boolean seat, long bedPosition) {
        this.entityId = entityId;
        this.skinParts = skinParts;
        this.poseId = poseId;
        this.includePose = includePose;
        this.sleeping = sleeping;
        this.seat = seat;
        this.bedPosition = bedPosition;
    }
    @Override public void write(PacketSerializer out) throws IOException {
        out.writeVarInt(entityId);
        if (seat) {
            entry(out, 0, 0); out.writeByte((byte) 0x20); // invisible
            entry(out, 5, 8); out.writeBoolean(true); // no gravity
            entry(out, 15, 0); out.writeByte((byte) 0x19); // small, no base plate, marker
        } else {
            if (includePose) {
                entry(out, 6, 20); out.writeVarInt(poseId);
                entry(out, 14, 11); out.writeBoolean(sleeping);
                if (sleeping) out.writeLong(bedPosition);
            }
            entry(out, 16, 0); out.writeByte((byte) skinParts);
        }
        out.writeByte((byte) 0xff);
    }
    private static void entry(PacketSerializer out, int index, int type) throws IOException {
        out.writeByte((byte) index);
        out.writeVarInt(type);
    }
}
