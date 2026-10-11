package cn.starry.narcissus.functions.packets.out.protocol.v774;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import cn.starry.narcissus.functions.world.WorldLighting;
import java.io.IOException;
import java.util.BitSet;

public final class PacketPlayOutLightUpdate774 implements PacketOut {
    private final int chunkX, chunkZ;
    private final LightData774 data;
    public PacketPlayOutLightUpdate774(int chunkX, int chunkZ, WorldLighting lighting,
            BitSet skySections, BitSet blockSections) {
        this(chunkX, chunkZ, lighting, skySections, blockSections, false);
    }
    public PacketPlayOutLightUpdate774(int chunkX, int chunkZ, WorldLighting lighting,
            BitSet skySections, BitSet blockSections, boolean byteArrayMasks) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.data = new LightData774(chunkX, chunkZ, lighting.sectionCount(), lighting.hasSkyLight(),
                lighting, skySections, blockSections, byteArrayMasks);
    }
    @Override public void write(PacketSerializer out) throws IOException {
        out.writeVarInt(chunkX);
        out.writeVarInt(chunkZ);
        data.write(out);
    }
}
