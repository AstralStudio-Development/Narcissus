package cn.starry.narcissus.functions.packets.out.protocol.v774;

import cn.starry.narcissus.functions.modern.BlockEntityTypeMap;
import cn.starry.narcissus.functions.modern.LegacyBlockMap;
import cn.starry.narcissus.functions.modern.ModernBlockStateMap;
import cn.starry.narcissus.functions.modern.ModernChunk;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import cn.starry.narcissus.functions.world.lighting.BlockLightingProperties;
import cn.starry.narcissus.functions.world.Chunk;
import cn.starry.narcissus.functions.world.ModernBlockEntity;
import cn.starry.narcissus.functions.world.World;
import cn.starry.narcissus.functions.world.WorldLighting;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/** Native heightmaps, paletted sections, block entities and authoritative light. */
public final class PacketPlayOutChunkWithLight774 implements PacketOut {
    private final int chunkX, chunkZ;
    private final byte[] body;

    public PacketPlayOutChunkWithLight774(int chunkX, int chunkZ, int sectionCount,
            int biomeId, boolean hasSkyLight, boolean sectionBlockCountMarker) {
        this(null, chunkX, chunkZ, sectionCount, biomeId, 0, hasSkyLight, sectionBlockCountMarker);
    }
    public PacketPlayOutChunkWithLight774(World world, int chunkX, int chunkZ,
            int sectionCount, int biomeId, int minY, boolean hasSkyLight, boolean sectionBlockCountMarker) {
        this(world, chunkX, chunkZ, sectionCount, biomeId, minY, hasSkyLight, sectionBlockCountMarker, false);
    }
    public PacketPlayOutChunkWithLight774(World world, int chunkX, int chunkZ,
            int sectionCount, int biomeId, int minY, boolean hasSkyLight, boolean sectionBlockCountMarker,
            boolean byteArrayMasks) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        WorldLighting lighting = world == null ? null : world.getLighting();
        if (lighting != null && (lighting.minY() != minY || lighting.sectionCount() != sectionCount
                || lighting.hasSkyLight() != hasSkyLight)) {
            throw new IllegalArgumentException("World light profile does not match chunk dimension");
        }
        int bits = 32 - Integer.numberOfLeadingZeros(sectionCount * 16);
        long[] heightmap = new long[(256 + 64 / bits - 1) / (64 / bits)];
        body = encode(heightmap, ModernChunk.emptyChunkData(sectionCount, biomeId, sectionBlockCountMarker),
                List.of(), null, null, new LightData774(chunkX, chunkZ, sectionCount, hasSkyLight,
                        lighting, null, null, byteArrayMasks));
    }
    public PacketPlayOutChunkWithLight774(Chunk chunk, int sectionCount, int biomeId,
            int minY, boolean hasSkyLight, boolean sectionBlockCountMarker) {
        this(null, chunk, sectionCount, biomeId, LegacyBlockMap.load("v774"),
                ModernBlockStateMap.load("v774"), BlockEntityTypeMap.load("v774"), "1.21.11",
                minY, hasSkyLight, sectionBlockCountMarker);
    }
    public PacketPlayOutChunkWithLight774(World world, Chunk chunk, int sectionCount,
            int biomeId, LegacyBlockMap legacyMap, ModernBlockStateMap stateMap,
            BlockEntityTypeMap entityTypes, String version, int minY,
            boolean hasSkyLight, boolean sectionBlockCountMarker) {
        chunkX = chunk.getX();
        chunkZ = chunk.getZ();
        if (world != null) {
            WorldLighting existing = world.getLighting();
            if (existing == null || !existing.isModern() || existing.minY() != minY
                    || existing.sectionCount() != sectionCount || existing.hasSkyLight() != hasSkyLight) {
                String directory = switch (version) {
                    case "26.3" -> "v777";
                    case "26.2" -> "v776";
                    case "26.1", "26.1.1", "26.1.2" -> "v775";
                    default -> "v774";
                };
                int stateCount = stateMap == null ? ModernBlockStateMap.load(directory).size() : stateMap.size();
                world.prepareModernLighting(BlockLightingProperties.load(directory, version, stateCount),
                        legacyMap, stateMap, minY, sectionCount, hasSkyLight);
            }
        }
        List<ModernBlockEntity> entities = new ArrayList<>();
        if (entityTypes != null) {
            for (ModernBlockEntity entity : chunk.getModernBlockEntities()) {
                if (entityTypes.isKnownRemoved(entity.getId())) continue;
                int y = entity.getPosition().getY();
                if (y >= minY && y < minY + sectionCount * 16 && entityTypes.resolve(entity.getId()) >= 0) {
                    entities.add(entity);
                }
            }
        }
        body = encode(ModernChunk.buildMotionBlockingHeightmap(world, chunk, sectionCount, minY, stateMap),
                ModernChunk.buildChunkData(world, chunk, sectionCount, minY, biomeId, legacyMap, stateMap,
                        sectionBlockCountMarker), entities, entityTypes, version,
                new LightData774(chunkX, chunkZ, sectionCount, hasSkyLight,
                        world == null ? null : world.getLighting(), null, null, "26.3".equals(version)));
    }
    private static byte[] encode(long[] heightmap, byte[] sections, List<ModernBlockEntity> entities,
            BlockEntityTypeMap types, String version, LightData774 lighting) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            PacketSerializer out = new PacketSerializer(bytes);
            out.writeVarInt(1); // heightmap map
            out.writeVarInt(4); // MOTION_BLOCKING
            out.writeLongArray(heightmap);
            out.writeByteArray(sections);
            out.writeVarInt(entities.size());
            for (ModernBlockEntity entity : entities) {
                var pos = entity.getPosition();
                out.writeByte((byte) (((pos.getX() & 15) << 4) | (pos.getZ() & 15)));
                out.writeShort((short) pos.getY());
                out.writeVarInt(types.resolve(entity.getId()));
                out.writeRawBytes(PacketPlayOutBlockEntityData774.encodeNbt(entity.getData(), version));
            }
            lighting.write(out);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot encode chunk with light", e);
        }
    }
    @Override public void write(PacketSerializer out) throws IOException {
        out.writeInt(chunkX);
        out.writeInt(chunkZ);
        out.writeRawBytes(body);
    }
}
