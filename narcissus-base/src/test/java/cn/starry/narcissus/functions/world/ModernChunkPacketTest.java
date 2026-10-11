package cn.starry.narcissus.functions.world;

import cn.starry.narcissus.Narcissus;
import cn.starry.narcissus.functions.config.Configuration;
import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.packets.Status;
import cn.starry.narcissus.functions.packets.protocols.ModernConfigurationDriver;
import cn.starry.narcissus.functions.packets.protocols.ModernProtocol;
import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketPlayOutBlockUpdate774;
import cn.starry.narcissus.functions.modern.BlockEntityTypeMap;
import cn.starry.narcissus.functions.modern.LegacyBlockMap;
import cn.starry.narcissus.functions.modern.ModernBlockStateMap;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketPlayOutBlockEntityData774;
import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketPlayOutChunkWithLight774;
import cn.starry.narcissus.functions.world.nbt.CompoundTag;
import cn.starry.narcissus.functions.world.nbt.IntTag;
import cn.starry.narcissus.functions.world.nbt.NBTTag;
import cn.starry.narcissus.functions.world.nbt.StringTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModernChunkPacketTest
{
    @Test
    void writesModernStatesAndUnnamedBlockEntityNbt() throws Exception
    {
        ModernBlockPalette palette = new ModernBlockPalette();
        World world = new World(1, 1, 0, 0, palette);
        world.setModernBlockState(0, 20, 0, palette.indexOf("minecraft:stone"));

        CompoundTag data = new CompoundTag();
        data.put("id", new StringTag("minecraft:sign"));
        data.put("x", new IntTag(0));
        data.put("y", new IntTag(20));
        data.put("z", new IntTag(0));
        data.put("Text1", new StringTag("{\"text\":\"{nar_ver}\"}"));
        world.addModernBlockEntity(new ModernBlockEntity("minecraft:sign",
                new BlockPosition(0, 20, 0), data));

        Chunk chunk = world.getChunks()[0][0];
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new PacketPlayOutChunkWithLight774(world, chunk, 24, 40,
                LegacyBlockMap.load("v774"), ModernBlockStateMap.load("v774"),
                BlockEntityTypeMap.load("v774"), "1.21.11", -64, true, false)
                .write(new PacketSerializer(bytes));

        PacketSerializer input = new PacketSerializer(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(0, input.readInt());
        assertEquals(0, input.readInt());
        assertEquals(1, input.readVarInt());
        assertEquals(4, input.readVarInt());
        int heightmapLongs = input.readVarInt();
        input.readBytes(heightmapLongs * 8);
        int chunkDataLength = input.readVarInt();
        assertTrue(chunkDataLength > 0);
        input.readBytes(chunkDataLength);

        assertEquals(1, input.readVarInt());
        assertEquals(0, input.readUnsignedByte());
        assertEquals(20, input.readShort());
        assertEquals(7, input.readVarInt());
        CompoundTag decoded = NBTTag.readUnnamedTag(
                new ByteArrayInputStream(input.readBytes(input.available()))).toCompoundTag();
        assertEquals("minecraft:sign", decoded.get("id").toStringTag().getValue());
        assertEquals(0, decoded.get("x").toIntTag().getValue());
        assertEquals(20, decoded.get("y").toIntTag().getValue());
        assertEquals(0, decoded.get("z").toIntTag().getValue());
        String decodedText = decoded.get("Text1").toStringTag().getValue();
        assertTrue(decodedText.contains("1.21.11"));
        assertTrue(decodedText.contains("\"color\":\"blue\""));
        assertEquals("{\"text\":\"{nar_ver}\"}", data.get("Text1").toStringTag().getValue());
    }

    @Test
    void emptyChunkBodyIsReusableAcrossCoordinates() throws Exception
    {
        ByteArrayOutputStream firstBytes = new ByteArrayOutputStream();
        new PacketPlayOutChunkWithLight774(0, 0, 16, 56, true, false)
                .write(new PacketSerializer(firstBytes));

        ByteArrayOutputStream secondBytes = new ByteArrayOutputStream();
        new PacketPlayOutChunkWithLight774(17, -4, 16, 56, true, false)
                .write(new PacketSerializer(secondBytes));

        byte[] first = firstBytes.toByteArray();
        byte[] second = secondBytes.toByteArray();
        assertEquals(first.length, second.length);
        assertArrayEquals(java.util.Arrays.copyOfRange(first, 8, first.length),
                java.util.Arrays.copyOfRange(second, 8, second.length));
    }

    @Test
    void refreshesModernBlockEntityDataAfterChunkLoad() throws Exception
    {
        CompoundTag data = new CompoundTag();
        data.put("Text1", new StringTag("{nar_ver}"));
        ModernBlockEntity entity = new ModernBlockEntity("minecraft:sign",
                new BlockPosition(18, 20, 34), data);
        int typeId = BlockEntityTypeMap.load("v774").resolve("minecraft:sign");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new PacketPlayOutBlockEntityData774(entity, typeId, "1.21.11")
                .write(new PacketSerializer(bytes));

        PacketSerializer input = new PacketSerializer(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(packBlockPosition(18, 20, 34), input.readLong());
        assertEquals(typeId, input.readVarInt());
        CompoundTag decoded = NBTTag.readUnnamedTag(
                new ByteArrayInputStream(input.readBytes(input.available()))).toCompoundTag();
        assertEquals("§91.21.11", decoded.get("Text1").toStringTag().getValue());
        assertEquals("{nar_ver}", data.get("Text1").toStringTag().getValue());
    }

    @ParameterizedTest
    @ValueSource(ints = {774, 775, 776, 777})
    void legacyBedNbtDoesNotProduceUnknownEntityWarningsOrInvalidPackets(int version) throws Exception
    {
        ModernBlockPalette palette = new ModernBlockPalette();
        World world = new World(1, 1, 0, 0, palette);
        String bedState = "minecraft:red_bed[facing=north,occupied=false,part=head]";
        world.setModernBlockState(0, 20, 0, palette.indexOf(bedState));
        CompoundTag data = new CompoundTag();
        data.put("id", new StringTag("minecraft:bed"));
        world.addModernBlockEntity(new ModernBlockEntity("minecraft:bed", new BlockPosition(0, 20, 0), data));
        NetworkManager manager = new NetworkManager(null, Configuration.builder().build());
        List<String> warnings = new ArrayList<>();
        Handler capture = new Handler()
        {
            @Override public void publish(LogRecord record) { warnings.add(record.getMessage()); }
            @Override public void flush() {}
            @Override public void close() {}
        };
        Narcissus.LOGGER.addHandler(capture);
        try (SocketChannel channel = SocketChannel.open())
        {
            PlayerManager player = new PlayerManager(manager, channel);
            player.setProtocol(manager.getProtocolByVersionId(version));
            player.setProtocolId(version);
            player.setStatus(Status.PLAY);
            player.setWorld(world);
            player.setChunkViewCenter(0, 0);
            player.setInitialChunkBatchReceived(true);
            player.setAuthoritativeBlocksPending(true);
            ModernConfigurationDriver.flushAuthoritativeBlocks(manager, player);
            assertTrue(warnings.stream().noneMatch(w -> w.contains("Skipping unknown modern block entity type minecraft:bed")),
                    () -> "valid legacy bed must be translated without unknown-type warnings: " + warnings);
            ModernProtocol protocol = (ModernProtocol) player.getProtocol();
            PacketSerializer block = queuedPayload(player.getOutboundQueue().poll());
            assertEquals(player.getProtocol().getPacketOutByClass(PacketPlayOutBlockUpdate774.class, Status.PLAY), block.readVarInt());
            assertEquals(packBlockPosition(0, 20, 0), block.readLong());
            assertEquals(protocol.getModernBlockStateMap().resolve(bedState), block.readVarInt());
            assertEquals(0, block.available());
            if (version <= 775)
            {
                PacketSerializer entity = queuedPayload(player.getOutboundQueue().poll());
                assertEquals(player.getProtocol().getPacketOutByClass(PacketPlayOutBlockEntityData774.class, Status.PLAY), entity.readVarInt());
                assertEquals(packBlockPosition(0, 20, 0), entity.readLong());
                assertEquals(25, entity.readVarInt());
            }
            assertTrue(player.getOutboundQueue().isEmpty(), "26.2+ must not receive a removed bed block-entity type");
            assertEquals(1, world.getChunk(0, 0).getModernBlockEntities().size(), "do not mutate source world for older clients");
            assertEquals("minecraft:bed", data.get("id").toStringTag().getValue());
        }
        finally
        {
            Narcissus.LOGGER.removeHandler(capture);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {774, 775, 776, 777})
    void chunkBedEntitiesFollowTheTargetVersionWithoutChangingSourceNbt(int version) throws Exception
    {
        String directory = "v" + version;
        String release = switch (version) {
            case 775 -> "26.1";
            case 776 -> "26.2";
            case 777 -> "26.3";
            default -> "1.21.11";
        };
        ModernBlockPalette palette = new ModernBlockPalette();
        World world = new World(1, 1, 0, 0, palette);
        world.setModernBlockState(0, 20, 0, palette.indexOf("minecraft:red_bed[facing=north,occupied=false,part=head]"));
        world.setModernBlockState(0, 20, 1, palette.indexOf("minecraft:red_bed[facing=north,occupied=false,part=foot]"));
        for (int z = 0; z < 2; z++)
        {
            CompoundTag data = new CompoundTag();
            data.put("id", new StringTag("minecraft:bed"));
            data.put("color", new IntTag(14));
            world.addModernBlockEntity(new ModernBlockEntity("minecraft:bed", new BlockPosition(0, 20, z), data));
        }
        BlockEntityTypeMap types = BlockEntityTypeMap.load(directory);
        assertEquals(version >= 776, types.isKnownRemoved("minecraft:bed"));
        assertEquals(version >= 776, types.isKnownRemoved("bed"));
        assertTrue(!types.isKnownRemoved("minecraft:sign"));
        assertTrue(!types.isKnownRemoved("example:bed"));
        assertTrue(!types.isKnownRemoved("minecraft:truly_unknown"));
        assertEquals(version <= 775 ? 25 : -1, types.resolve("minecraft:bed"));
        assertEquals(version <= 775 ? 26 : 25, types.resolve("minecraft:conduit"));
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        new PacketPlayOutChunkWithLight774(world, world.getChunk(0, 0), 16, 40,
                LegacyBlockMap.load(directory), ModernBlockStateMap.load(directory), types,
                release, 0, false, version >= 775).write(new PacketSerializer(encoded));
        ByteArrayInputStream bytes = new ByteArrayInputStream(encoded.toByteArray());
        PacketSerializer in = new PacketSerializer(bytes);
        assertEquals(0, in.readInt());
        assertEquals(0, in.readInt());
        int heightmaps = in.readVarInt();
        for (int i = 0; i < heightmaps; i++)
        {
            in.readVarInt();
            in.readBytes(in.readVarInt() * 8);
        }
        int sectionBytes = in.readVarInt();
        assertTrue(sectionBytes > 0);
        in.readBytes(sectionBytes);
        assertEquals(version <= 775 ? 2 : 0, in.readVarInt(), "removed bed type must not alias conduit in the chunk");
        if (version <= 775)
        {
            for (int z = 0; z < 2; z++)
            {
                assertEquals(z, in.readUnsignedByte());
                assertEquals(20, in.readShort());
                assertEquals(25, in.readVarInt());
                CompoundTag decoded = NBTTag.readUnnamedTag(bytes).toCompoundTag();
                assertEquals("minecraft:bed", decoded.get("id").toStringTag().getValue());
                assertEquals(14, decoded.get("color").toIntTag().getValue());
            }
        }
        assertEquals(2, world.getChunk(0, 0).getModernBlockEntities().size());
    }

    @ParameterizedTest
    @ValueSource(ints = {776, 777})
    void trulyUnknownBlockEntityTypesStillWarn(int version) throws Exception
    {
        World world = new World(1, 1, 0, 0, new ModernBlockPalette());
        world.setModernBlockState(0, 20, 0, world.getModernBlockPalette().indexOf("minecraft:stone"));
        world.addModernBlockEntity(new ModernBlockEntity("example:unknown", new BlockPosition(0, 20, 0), new CompoundTag()));
        NetworkManager manager = new NetworkManager(null, Configuration.builder().build());
        List<String> warnings = new ArrayList<>();
        Handler capture = new Handler()
        {
            @Override public void publish(LogRecord record) { warnings.add(record.getMessage()); }
            @Override public void flush() {}
            @Override public void close() {}
        };
        Narcissus.LOGGER.addHandler(capture);
        try (SocketChannel channel = SocketChannel.open())
        {
            PlayerManager player = new PlayerManager(manager, channel);
            player.setProtocol(manager.getProtocolByVersionId(version));
            player.setStatus(Status.PLAY);
            player.setWorld(world);
            player.setChunkViewCenter(0, 0);
            player.setInitialChunkBatchReceived(true);
            player.setAuthoritativeBlocksPending(true);
            ModernConfigurationDriver.flushAuthoritativeBlocks(manager, player);
            assertTrue(warnings.contains("Skipping unknown modern block entity type example:unknown"));
        }
        finally
        {
            Narcissus.LOGGER.removeHandler(capture);
        }
    }

    private static PacketSerializer queuedPayload(ByteBuffer frame) throws Exception
    {
        assertTrue(frame != null, "expected outbound packet");
        byte[] encoded = new byte[frame.remaining()];
        frame.get(encoded);
        ByteArrayInputStream bytes = new ByteArrayInputStream(encoded);
        int size = new PacketSerializer(bytes).readVarInt();
        assertEquals(size, bytes.available());
        return new PacketSerializer(new ByteArrayInputStream(bytes.readNBytes(size)));
    }

    private static long packBlockPosition(int x, int y, int z)
    {
        return ((long) (x & 0x3FFFFFF) << 38)
                | ((long) (z & 0x3FFFFFF) << 12)
                | (y & 0xFFFL);
    }
}
