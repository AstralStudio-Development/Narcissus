package cn.starry.narcissus;

import cn.starry.narcissus.functions.config.Configuration;
import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.packets.Status;
import cn.starry.narcissus.functions.packets.in.protocol.v774.PacketLoginInAcknowledged774;
import cn.starry.narcissus.functions.packets.in.protocol.v774.PacketConfigInSelectKnownPacks774;
import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketConfigOutFinish774;
import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketConfigOutRegistryData774;
import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketConfigOutSelectKnownPacks774;
import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketConfigOutUpdateEnabledFeatures774;
import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketConfigOutUpdateTags774;
import cn.starry.narcissus.functions.packets.protocols.ModernProtocol;
import cn.starry.narcissus.functions.modern.BlockEntityTypeMap;
import cn.starry.narcissus.functions.modern.LegacyBlockMap;
import cn.starry.narcissus.functions.modern.ModernBlockStateMap;
import cn.starry.narcissus.functions.modern.RegistryFixtures;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.world.nbt.NBTTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ModernRegistryFixturesTest {
    @ParameterizedTest
    @ValueSource(ints = {774, 775, 776, 777})
    void everyModernProtocolCanSerializeItsBundledUpdateTags(int version) throws Exception {
        NetworkManager manager = new NetworkManager(null, Configuration.builder().build());
        ModernProtocol protocol = (ModernProtocol) manager.getProtocolByVersionId(version);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        // This is the same constructor and body used by sendRegistryData after select_known_packs.
        new PacketConfigOutUpdateTags774(protocol.getUpdateTagsBody()).write(new PacketSerializer(bytes));
        assertArrayEquals(protocol.getUpdateTagsBody(), bytes.toByteArray());
        PacketSerializer in = new PacketSerializer(new ByteArrayInputStream(bytes.toByteArray()));
        assertTrue(in.readVarInt() > 0, "vanilla tag registries must not be replaced by an empty packet");
    }

    @ParameterizedTest
    @CsvSource({"774,false", "774,true", "775,false", "775,true",
            "776,false", "776,true", "777,false", "777,true"})
    void knownPackResponseQueuesRegistriesThenRealTagsThenFinish(int version, boolean knowsCore) throws Exception {
        NetworkManager manager = new NetworkManager(null, Configuration.builder().build());
        try (SocketChannel channel = SocketChannel.open()) {
            PlayerManager player = new PlayerManager(manager, channel);
            player.setProtocol(manager.getProtocolByVersionId(version));
            player.setProtocolId(version);
            player.setStatus(Status.LOGIN);
            ModernProtocol protocol = (ModernProtocol) player.getProtocol();
            new PacketLoginInAcknowledged774().handlePacket(manager, player);
            assertEquals(Status.CONFIGURATION, player.getStatus());
            assertEquals(2, player.getOutboundQueue().size());
            assertNextPacket(player, player.getProtocol().getPacketOutByClass(
                    PacketConfigOutUpdateEnabledFeatures774.class, Status.CONFIGURATION), null);
            assertNextPacket(player, player.getProtocol().getPacketOutByClass(
                    PacketConfigOutSelectKnownPacks774.class, Status.CONFIGURATION), null);

            ByteArrayOutputStream response = new ByteArrayOutputStream();
            PacketSerializer out = new PacketSerializer(response);
            out.writeVarInt(knowsCore ? 1 : 0);
            if (knowsCore) {
                out.writeString("minecraft");
                out.writeString("core");
                out.writeString(protocol.getCorePackVersion());
            }
            PacketConfigInSelectKnownPacks774 selected = new PacketConfigInSelectKnownPacks774();
            selected.readPacket(new PacketSerializer(new ByteArrayInputStream(response.toByteArray())));
            selected.handlePacket(manager, player);

            assertEquals(protocol.getRegistryBodies(knowsCore).size() + 2, player.getOutboundQueue().size());
            for (byte[] registry : protocol.getRegistryBodies(knowsCore)) {
                assertNextPacket(player, player.getProtocol().getPacketOutByClass(
                        PacketConfigOutRegistryData774.class, Status.CONFIGURATION), registry);
            }
            assertNextPacket(player, player.getProtocol().getPacketOutByClass(
                    PacketConfigOutUpdateTags774.class, Status.CONFIGURATION), protocol.getUpdateTagsBody());
            assertNextPacket(player, player.getProtocol().getPacketOutByClass(
                    PacketConfigOutFinish774.class, Status.CONFIGURATION), new byte[0]);
            assertTrue(player.getOutboundQueue().isEmpty());
        }
    }

    private static void assertNextPacket(PlayerManager player, int packetId, byte[] expectedBody) throws Exception {
        ByteBuffer frame = player.getOutboundQueue().poll();
        assertNotNull(frame, "packet was not queued");
        byte[] encoded = new byte[frame.remaining()];
        frame.get(encoded);
        ByteArrayInputStream framed = new ByteArrayInputStream(encoded);
        int length = new PacketSerializer(framed).readVarInt();
        assertEquals(length, framed.available());
        byte[] payload = framed.readNBytes(length);
        assertEquals(0, framed.available());
        ByteArrayInputStream bytes = new ByteArrayInputStream(payload);
        assertEquals(packetId, new PacketSerializer(bytes).readVarInt());
        if (expectedBody != null) assertArrayEquals(expectedBody, bytes.readAllBytes());
    }

    @Test
    void releaseFixturesContainCompleteNetworkNbtAndCorrectDimensionAndBiomeIds() throws Exception {
        assertRegistries("v775", 28, 65, 40, 34, 56);
        assertRegistries("v776", 29, 66, 40, 34, 57);
        assertRegistries("v777", 32, 67, 41, 35, 58);
    }

    @ParameterizedTest
    @CsvSource({"775,661", "776,697", "777,773"})
    void bundledVanillaTagsAreCompleteAndUseTheTransmittedDynamicRegistryIds(int version, int expectedTags) throws Exception {
        RegistryFixtures fixtures = RegistryFixtures.load("v" + version);
        Map<String, Map<String, Integer>> registryIds = new HashMap<>();
        for (byte[] body : fixtures.getRegistryBodies()) {
            ByteArrayInputStream bytes = new ByteArrayInputStream(body);
            PacketSerializer in = new PacketSerializer(bytes);
            String registry = in.readString();
            int count = in.readVarInt();
            Map<String, Integer> ids = new HashMap<>();
            for (int id = 0; id < count; id++) {
                ids.put(in.readString(), id);
                if (in.readBoolean()) NBTTag.readUnnamedTag(bytes);
            }
            assertEquals(0, bytes.available());
            registryIds.put(registry, ids);
        }
        ByteArrayInputStream bytes = new ByteArrayInputStream(fixtures.getUpdateTagsBody());
        PacketSerializer in = new PacketSerializer(bytes);
        int registryCount = in.readVarInt();
        assertEquals(15, registryCount);
        int totalTags = 0, references = 0;
        Set<String> seenRegistries = new HashSet<>();
        boolean bypassesShieldContainsSulfurCube = false;
        for (int r = 0; r < registryCount; r++) {
            String registry = in.readString();
            assertTrue(seenRegistries.add(registry), "duplicate tag registry");
            int tagCount = in.readVarInt();
            assertTrue(tagCount > 0);
            totalTags += tagCount;
            Set<String> seenTags = new HashSet<>();
            for (int t = 0; t < tagCount; t++) {
                String tag = in.readString();
                assertTrue(seenTags.add(tag), "duplicate tag " + tag);
                int count = in.readVarInt();
                assertTrue(count >= 0);
                references += count;
                for (int i = 0; i < count; i++) {
                    int id = in.readVarInt();
                    assertTrue(id >= 0);
                    Map<String, Integer> dynamic = registryIds.get(registry);
                    if (dynamic != null) assertTrue(id < dynamic.size(), "tag ID outside transmitted registry");
                    if (registry.equals("minecraft:damage_type") && tag.equals("minecraft:bypasses_shield")
                            && Integer.valueOf(id).equals(dynamic.get("minecraft:sulfur_cube_hot"))) {
                        bypassesShieldContainsSulfurCube = true;
                    }
                }
            }
        }
        assertEquals(expectedTags, totalTags);
        assertTrue(references > 1000);
        assertTrue(seenRegistries.containsAll(Set.of("minecraft:block", "minecraft:item", "minecraft:fluid",
                "minecraft:entity_type", "minecraft:damage_type", "minecraft:worldgen/biome", "minecraft:enchantment")));
        if (version >= 776) assertTrue(bypassesShieldContainsSulfurCube, "new 26.2 damage tag must resolve by fixture ID");
        assertEquals(0, bytes.available(), "trailing tags bytes");
    }

    @Test
    void missingRequiredTagsFailDuringLoadingInsteadOfDuringClientConfiguration() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> RegistryFixtures.load("nonexistent-tags-regression"));
        assertTrue(error.getMessage().contains("/registry/nonexistent-tags-regression/update_tags.bin"));
    }

    @ParameterizedTest
    @CsvSource({"774,56", "775,56", "776,57", "777,58"})
    void eachProtocolUsesTheEndBiomeFromItsActualRegistry(int version, int expectedId) {
        NetworkManager manager = new NetworkManager(null, Configuration.builder().dimension((byte) 1).build());
        ModernProtocol protocol = (ModernProtocol) manager.getProtocolByVersionId(version);
        assertEquals(expectedId, protocol.getBiomeId(), "the_end must not alias sulfur_caves or another version's ID");
    }

    @Test
    void blockAndLegacyMappingsUseTheTargetReleaseStateRegistry() {
        assertBlockMappings("v775", 29873);
        assertBlockMappings("v776", 32366);
        assertBlockMappings("v777", 35723);
    }

    private static void assertRegistries(String version, int expectedRegistries, int expectedBiomes,
                                         int plainsId, int netherId, int endId) throws Exception {
        RegistryFixtures fixtures = RegistryFixtures.load(version);
        assertEquals(expectedRegistries, fixtures.getRegistryBodies().size(), version);
        Map<String, Map<String, Integer>> registries = new HashMap<>();
        for (byte[] body : fixtures.getRegistryBodies()) {
            ByteArrayInputStream bytes = new ByteArrayInputStream(body);
            PacketSerializer in = new PacketSerializer(bytes);
            String name = in.readString();
            int count = in.readVarInt();
            assertTrue(count > 0, "empty registry " + name);
            Map<String, Integer> ids = new LinkedHashMap<>();
            for (int id = 0; id < count; id++) {
                String entry = in.readString();
                assertNull(ids.put(entry, id), "duplicate entry " + entry);
                assertTrue(in.readBoolean(), "full fixtures must not depend on an accepted known pack");
                // A registry codec may encode a list or primitive as well as a compound.
                assertNotNull(NBTTag.readUnnamedTag(bytes), "invalid registry NBT " + entry);
            }
            assertEquals(0, bytes.available(), "unconsumed registry bytes " + name);
            assertNull(registries.put(name, ids), "duplicate registry " + name);
        }
        assertEquals(Map.of("minecraft:overworld", 0, "minecraft:overworld_caves", 1,
                "minecraft:the_end", 2, "minecraft:the_nether", 3), registries.get("minecraft:dimension_type"));
        Map<String, Integer> biomes = registries.get("minecraft:worldgen/biome");
        assertNotNull(biomes);
        assertEquals(expectedBiomes, biomes.size());
        assertEquals(plainsId, biomes.get("minecraft:plains"));
        assertEquals(netherId, biomes.get("minecraft:nether_wastes"));
        assertEquals(endId, biomes.get("minecraft:the_end"));
        assertEquals(Map.of("minecraft:overworld", 0, "minecraft:the_end", 1),
                registries.get("minecraft:world_clock"));
        if (version.equals("v777")) {
            assertTrue(registries.containsKey("minecraft:sulfur_cube_archetype"));
            assertTrue(registries.containsKey("minecraft:block_transformer"));
            assertTrue(registries.containsKey("minecraft:worldgen/block_state_provider"));
            assertTrue(registries.containsKey("minecraft:decorated_pot_pattern"));
        }
    }

    private static void assertBlockMappings(String version, int expectedStates) {
        ModernBlockStateMap states = ModernBlockStateMap.load(version);
        assertEquals(expectedStates, states.size());
        assertEquals(0, states.resolve("minecraft:air"));
        assertEquals(1, states.resolve("minecraft:stone"));
        assertEquals(7, BlockEntityTypeMap.load(version).resolve("minecraft:sign"));
        assertEquals(states.resolve("minecraft:stone"), LegacyBlockMap.load(version).toGlobalState(1, 0));
        // The legacy table intentionally only maps combinations in the bundled schematic.
        assertEquals(0, LegacyBlockMap.load(version).toGlobalState(-1, 0));
    }
}
