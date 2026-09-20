package cn.starry.narcissus;

import cn.starry.narcissus.functions.config.Configuration;
import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.packets.AbstractProtocol;
import cn.starry.narcissus.functions.packets.PlayerListEntry;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.Status;
import cn.starry.narcissus.functions.packets.in.PacketIn;
import cn.starry.narcissus.functions.packets.in.PacketInPluginMessage;
import cn.starry.narcissus.functions.packets.in.protocol.v774.PacketConfigInClientInformation774;
import cn.starry.narcissus.functions.packets.in.protocol.v774.PacketInKeepAlive774;
import cn.starry.narcissus.functions.packets.in.protocol.v774.PacketPlayInAcceptTeleport774;
import cn.starry.narcissus.functions.packets.in.protocol.v774.PacketPlayInChat774;
import cn.starry.narcissus.functions.packets.in.protocol.v774.PacketPlayInChatCommand774;
import cn.starry.narcissus.functions.packets.in.protocol.v774.PacketPlayInCommandSuggestion774;
import cn.starry.narcissus.functions.packets.out.protocol.v774.*;
import cn.starry.narcissus.functions.packets.out.protocol.v776.PacketLoginOutSuccess776;
import cn.starry.narcissus.functions.packets.out.protocol.v776.PacketPlayOutEntityMetadata776;
import cn.starry.narcissus.functions.packets.out.protocol.v776.PacketPlayOutJoinGame776;
import cn.starry.narcissus.functions.packets.out.protocol.v776.PacketPlayOutPlayerInfoRemove776;
import cn.starry.narcissus.functions.packets.out.protocol.v776.PacketPlayOutPlayerInfoUpdate776;
import cn.starry.narcissus.functions.packets.out.protocol.v776.PacketPlayOutRemoveEntities776;
import cn.starry.narcissus.functions.packets.out.protocol.v776.PacketPlayOutSetPassengers776;
import cn.starry.narcissus.functions.packets.out.protocol.v776.PacketPlayOutSetPlayerTeam776;
import cn.starry.narcissus.functions.packets.out.protocol.v776.PacketPlayOutTabList776;
import cn.starry.narcissus.functions.packets.out.protocol.v776.PacketPlayOutTeleportEntity776;
import cn.starry.narcissus.util.MojangSkinProvider;
import cn.starry.narcissus.util.TextUtil;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the protocol-776 (Minecraft 26.2) packet id registrations against
 * Mojang's official 26.2 packets.json report. 26.2 shifted the PLAY ids by two
 * new packets (game_rule_values 0x27, low_disk_space_warning 0x32), so the ids
 * differ from 774 while field encodings stay identical.
 */
class Protocol776Test {

    private static AbstractProtocol p776() {
        NetworkManager manager = new NetworkManager(null, Configuration.builder().build());
        AbstractProtocol p = manager.getProtocolByVersionId(776);
        assertNotNull(p, "protocol 776 must be registered");
        return p;
    }

    private static AbstractProtocol p774() {
        NetworkManager manager = new NetworkManager(null, Configuration.builder().build());
        AbstractProtocol p = manager.getProtocolByVersionId(774);
        assertNotNull(p, "protocol 774 must be registered");
        return p;
    }

    @Test
    void reportsVersion776() {
        assertArrayEquals(new int[]{776}, p776().getVersions());
    }

    @Test
    void loginAndConfigurationIdsUnchangedFrom774() {
        AbstractProtocol p = p776();
        // LOGIN (776 uses its own serializer class but the same id 0x02)
        assertEquals(0x02, p.getPacketOutByClass(PacketLoginOutSuccess776.class, Status.LOGIN));
        // CONFIGURATION
        assertEquals(0x07, p.getPacketOutByClass(PacketConfigOutRegistryData774.class, Status.CONFIGURATION));
        assertEquals(0x03, p.getPacketOutByClass(PacketConfigOutFinish774.class, Status.CONFIGURATION));
        assertEquals(0x04, p.getPacketOutByClass(PacketOutKeepAlive774.class, Status.CONFIGURATION));
    }

    @Test
    void protocol774RegistersOfficialLightUpdateId() {
        assertEquals(0x2f, p774().getPacketOutByClass(
                PacketPlayOutLightUpdate774.class, Status.PLAY));
    }

    @Test
    void playClientboundIdsMatchOfficial26_2() {
        AbstractProtocol p = p776();
        // Official 26.2 packets.json ids (clientbound play)
        assertEquals(0x20, p.getPacketOutByClass(PacketOutDisconnect774.class, Status.PLAY));
        assertEquals(0x26, p.getPacketOutByClass(PacketPlayOutGameEvent774.class, Status.PLAY));
        assertEquals(0x2c, p.getPacketOutByClass(PacketOutKeepAlive774.class, Status.PLAY));
        assertEquals(0x2d, p.getPacketOutByClass(PacketPlayOutChunkWithLight774.class, Status.PLAY));
        assertEquals(0x30, p.getPacketOutByClass(PacketPlayOutLightUpdate774.class, Status.PLAY));
        assertEquals(0x31, p.getPacketOutByClass(PacketPlayOutJoinGame776.class, Status.PLAY));
        assertEquals(0x40, p.getPacketOutByClass(PacketPlayOutPlayerAbilities774.class, Status.PLAY));
        assertEquals(0x48, p.getPacketOutByClass(PacketPlayOutSyncPosition774.class, Status.PLAY));
        assertEquals(0x5e, p.getPacketOutByClass(PacketPlayOutSetChunkCacheCenter774.class, Status.PLAY));
        assertEquals(0x79, p.getPacketOutByClass(PacketPlayOutSystemChat774.class, Status.PLAY));
        assertEquals(0x45, p.getPacketOutByClass(PacketPlayOutPlayerInfoRemove776.class, Status.PLAY));
        assertEquals(0x46, p.getPacketOutByClass(PacketPlayOutPlayerInfoUpdate776.class, Status.PLAY));
        assertEquals(0x6d, p.getPacketOutByClass(PacketPlayOutSetPlayerTeam776.class, Status.PLAY));
        assertEquals(0x7a, p.getPacketOutByClass(PacketPlayOutTabList776.class, Status.PLAY));
        assertEquals(0x0f, p.getPacketOutByClass(PacketPlayOutCommandSuggestions774.class, Status.PLAY));
        assertEquals(0x10, p.getPacketOutByClass(PacketPlayOutCommands774.class, Status.PLAY));
        assertEquals(0x06, p.getPacketOutByClass(PacketPlayOutBlockEntityData774.class, Status.PLAY));
        assertEquals(0x4d, p.getPacketOutByClass(PacketPlayOutRemoveEntities776.class, Status.PLAY));
        assertEquals(0x63, p.getPacketOutByClass(PacketPlayOutEntityMetadata776.class, Status.PLAY));
        assertEquals(0x6b, p.getPacketOutByClass(PacketPlayOutSetPassengers776.class, Status.PLAY));
        assertEquals(0x7d, p.getPacketOutByClass(PacketPlayOutTeleportEntity776.class, Status.PLAY));
    }

    @Test
    void modernClientInformationAndEntityMetadataUseVersionSpecificRegistrations() {
        AbstractProtocol p774 = p774();
        AbstractProtocol p776 = p776();
        assertEquals(PacketConfigInClientInformation774.class,
                p774.getPacketInById(0x00, Status.CONFIGURATION));
        assertEquals(PacketConfigInClientInformation774.class,
                p776.getPacketInById(0x00, Status.CONFIGURATION));
        assertEquals(0x61, p774.getPacketOutByClass(PacketPlayOutEntityMetadata774.class, Status.PLAY));
        assertEquals(0x63, p776.getPacketOutByClass(PacketPlayOutEntityMetadata776.class, Status.PLAY));
    }

    @Test
    void modernNpcPacketsEncodePoseMovementAndMountState() throws IOException {
        for (AbstractProtocol protocol : Arrays.asList(p774(), p776())) {
            boolean is776 = protocol.getVersions()[0] == 776;
            cn.starry.narcissus.functions.packets.protocols.ModernProtocol modern =
                    (cn.starry.narcissus.functions.packets.protocols.ModernProtocol) protocol;

            PacketSerializer sleeping = new PacketSerializer(new ByteArrayInputStream(encode(
                    modern.createNpcMetadata(2, 0x7F, 2, true, -6, 88, 10),
                    Status.PLAY, protocol)));
            assertEquals(is776 ? 0x63 : 0x61, sleeping.readVarInt());
            assertEquals(2, sleeping.readVarInt());
            assertEquals(6, sleeping.readUnsignedByte());
            assertEquals(20, sleeping.readVarInt());
            assertEquals(2, sleeping.readVarInt());
            assertEquals(14, sleeping.readUnsignedByte());
            assertEquals(11, sleeping.readVarInt());
            assertTrue(sleeping.readBoolean());
            long bedPosition = sleeping.readLong();
            assertEquals(-6, (int) (bedPosition >> 38));
            assertEquals(88, (int) (bedPosition << 52 >> 52));
            assertEquals(10, (int) (bedPosition << 26 >> 38));
            assertEquals(16, sleeping.readUnsignedByte());
            assertEquals(0, sleeping.readVarInt());
            assertEquals(0x7F, sleeping.readUnsignedByte());
            assertEquals(0xFF, sleeping.readUnsignedByte());
            assertEquals(0, sleeping.available());

            PacketSerializer sitting = new PacketSerializer(new ByteArrayInputStream(encode(
                    modern.createNpcMetadata(2, 0x7F, 10, false, 0, 0, 0),
                    Status.PLAY, protocol)));
            sitting.readVarInt();
            assertEquals(2, sitting.readVarInt());
            assertEquals(6, sitting.readUnsignedByte());
            assertEquals(20, sitting.readVarInt());
            assertEquals(10, sitting.readVarInt());
            assertEquals(14, sitting.readUnsignedByte());
            assertEquals(11, sitting.readVarInt());
            assertFalse(sitting.readBoolean(), "leaving sleep must clear the optional bed position");

            PacketSerializer seatMetadata = new PacketSerializer(new ByteArrayInputStream(encode(
                    modern.createNpcSeatMetadata(3), Status.PLAY, protocol)));
            assertEquals(is776 ? 0x63 : 0x61, seatMetadata.readVarInt());
            assertEquals(3, seatMetadata.readVarInt());
            assertEquals(0, seatMetadata.readUnsignedByte());
            assertEquals(0, seatMetadata.readVarInt());
            assertEquals(0x20, seatMetadata.readUnsignedByte());
            assertEquals(5, seatMetadata.readUnsignedByte());
            assertEquals(8, seatMetadata.readVarInt());
            assertTrue(seatMetadata.readBoolean());
            assertEquals(15, seatMetadata.readUnsignedByte());
            assertEquals(0, seatMetadata.readVarInt());
            assertEquals(0x19, seatMetadata.readUnsignedByte());
            assertEquals(0xFF, seatMetadata.readUnsignedByte());
            assertEquals(0, seatMetadata.available());

            PacketSerializer teleport = new PacketSerializer(new ByteArrayInputStream(encode(
                    modern.createTeleportEntity(2, 6.5D, 88.0D, 12.5D, 180.0F, 0.0F),
                    Status.PLAY, protocol)));
            assertEquals(is776 ? 0x7d : 0x7b, teleport.readVarInt());
            assertEquals(2, teleport.readVarInt());
            assertEquals(6.5D, Double.longBitsToDouble(teleport.readLong()));
            assertEquals(88.0D, Double.longBitsToDouble(teleport.readLong()));
            assertEquals(12.5D, Double.longBitsToDouble(teleport.readLong()));
            assertEquals(0.0D, Double.longBitsToDouble(teleport.readLong()));
            assertEquals(0.0D, Double.longBitsToDouble(teleport.readLong()));
            assertEquals(0.0D, Double.longBitsToDouble(teleport.readLong()));
            assertEquals(180.0F, Float.intBitsToFloat(teleport.readInt()));
            assertEquals(0.0F, Float.intBitsToFloat(teleport.readInt()));
            assertEquals(0, teleport.readInt());
            assertTrue(teleport.readBoolean());
            assertEquals(0, teleport.available());

            PacketSerializer passengers = new PacketSerializer(new ByteArrayInputStream(encode(
                    modern.createSetPassengers(3, 2), Status.PLAY, protocol)));
            assertEquals(is776 ? 0x6b : 0x69, passengers.readVarInt());
            assertEquals(3, passengers.readVarInt());
            assertEquals(1, passengers.readVarInt());
            assertEquals(2, passengers.readVarInt());
            assertEquals(0, passengers.available());

            PacketSerializer remove = new PacketSerializer(new ByteArrayInputStream(encode(
                    modern.createRemoveEntities(2, 3), Status.PLAY, protocol)));
            assertEquals(is776 ? 0x4d : 0x4b, remove.readVarInt());
            assertEquals(2, remove.readVarInt());
            assertEquals(2, remove.readVarInt());
            assertEquals(3, remove.readVarInt());
            assertEquals(0, remove.available());
        }
    }

    @Test
    void modernDimensionSelectionFollowsConfiguration() {
        assertDimension(774, (byte) 0, 0, "minecraft:overworld", 24, -64, 40, true, 63);
        assertDimension(774, (byte) -1, 3, "minecraft:the_nether", 16, 0, 34, false, 32);
        assertDimension(774, (byte) 1, 2, "minecraft:the_end", 16, 0, 56, false, 63);
        assertDimension(774, (byte) 2, 2, "minecraft:the_end", 16, 0, 56, false, 63);

        // Protocol 776 inherits the dimension selection but keeps its own packet IDs.
        assertDimension(776, (byte) 0, 0, "minecraft:overworld", 24, -64, 40, true, 63);
        assertDimension(776, (byte) -1, 3, "minecraft:the_nether", 16, 0, 34, false, 32);
    }

    private static void assertDimension(int version, byte configuredDimension,
                                        int dimensionTypeId, String dimensionName,
                                        int sectionCount, int minY, int biomeId,
                                        boolean skyLight, int seaLevel) {
        NetworkManager manager = new NetworkManager(null,
                Configuration.builder().dimension(configuredDimension).build());
        cn.starry.narcissus.functions.packets.protocols.ModernProtocol protocol =
                (cn.starry.narcissus.functions.packets.protocols.ModernProtocol)
                        manager.getProtocolByVersionId(version);
        assertNotNull(protocol);
        assertEquals(dimensionTypeId, protocol.getDimensionTypeId());
        assertEquals(dimensionName, protocol.getDimensionName());
        assertEquals(sectionCount, protocol.getSectionCount());
        assertEquals(minY, protocol.getMinY());
        assertEquals(biomeId, protocol.getBiomeId());
        assertEquals(skyLight, protocol.hasSkyLight());
        assertEquals(seaLevel, protocol.getSeaLevel());
    }

    @Test
    void playerListIdsMatch774And776() {
        AbstractProtocol p774 = p774();
        AbstractProtocol p776 = p776();
        assertEquals(0x43, p774.getPacketOutByClass(PacketPlayOutPlayerInfoRemove774.class, Status.PLAY));
        assertEquals(0x44, p774.getPacketOutByClass(PacketPlayOutPlayerInfoUpdate774.class, Status.PLAY));
        assertEquals(0x6b, p774.getPacketOutByClass(PacketPlayOutSetPlayerTeam774.class, Status.PLAY));
        assertEquals(0x6d, p776.getPacketOutByClass(PacketPlayOutSetPlayerTeam776.class, Status.PLAY));
        assertEquals(0x78, p774.getPacketOutByClass(PacketPlayOutTabList774.class, Status.PLAY));
        assertNotEquals(p774.getPacketOutByClass(PacketPlayOutPlayerInfoUpdate774.class, Status.PLAY),
                p776.getPacketOutByClass(PacketPlayOutPlayerInfoUpdate776.class, Status.PLAY));
    }

    @Test
    void modernPluginMessageIdsAreRegisteredForSkinForwarding() {
        assertEquals(PacketInPluginMessage.class, p774().getPacketInById(0x15, Status.PLAY));
        assertEquals(PacketInPluginMessage.class, p776().getPacketInById(0x16, Status.PLAY));
    }

    @Test
    void modernPlayerListEncodesSignedSkinPropertyAndGrayDisplayName() throws IOException {
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000003");
        java.util.List<cn.starry.narcissus.functions.packets.PlayerListEntry.ProfileProperty> properties =
                java.util.Collections.singletonList(
                        new cn.starry.narcissus.functions.packets.PlayerListEntry.ProfileProperty(
                                "textures", "encoded-value", "encoded-signature"));
        java.util.List<cn.starry.narcissus.functions.packets.PlayerListEntry> entries =
                java.util.Collections.singletonList(
                        new cn.starry.narcissus.functions.packets.PlayerListEntry(uuid, "Alex", 1, properties));

        byte[] body = encode(new PacketPlayOutPlayerInfoUpdate774(entries), Status.PLAY, p774());
        PacketSerializer in = new PacketSerializer(new ByteArrayInputStream(body));
        assertEquals(0x44, in.readVarInt());
        assertEquals(0b00111101, in.readUnsignedByte());
        assertEquals(1, in.readVarInt());
        assertEquals(uuid, in.readUUID());
        assertEquals("Alex", in.readString());
        assertEquals(1, in.readVarInt());
        assertEquals("textures", in.readString());
        assertEquals("encoded-value", in.readString());
        assertTrue(in.readBoolean());
        assertEquals("encoded-signature", in.readString());
        assertEquals(1, in.readVarInt());
        assertTrue(in.readBoolean());
        assertEquals(0, in.readVarInt());
        assertTrue(in.readBoolean());
        assertEquals(8, in.readUnsignedByte());
        int displayLength = in.readShort() & 0xFFFF;
        assertEquals("§7Alex", new String(in.readBytes(displayLength), StandardCharsets.UTF_8));
    }

    @Test
    void hiddenNpcTeamsUseVersionSpecificLayouts() throws IOException {
        cn.starry.narcissus.functions.packets.protocols.ModernProtocol modern774 =
                (cn.starry.narcissus.functions.packets.protocols.ModernProtocol) p774();
        cn.starry.narcissus.functions.packets.protocols.ModernProtocol modern776 =
                (cn.starry.narcissus.functions.packets.protocols.ModernProtocol) p776();

        PacketSerializer in774 = new PacketSerializer(new ByteArrayInputStream(encode(
                modern774.createHiddenNameTagTeam("nar_npc_hidden", "NPC"), Status.PLAY, p774())));
        assertEquals(0x6b, in774.readVarInt());
        assertEquals("nar_npc_hidden", in774.readString());
        assertEquals(0, in774.readUnsignedByte());
        assertEquals("NPC", readPlainTextComponent(in774));
        assertEquals(0, in774.readUnsignedByte());
        assertEquals(1, in774.readVarInt()); // Team.Visibility.NEVER
        assertEquals(0, in774.readVarInt()); // Team.CollisionRule.ALWAYS
        assertEquals(21, in774.readVarInt());
        assertEquals("", readPlainTextComponent(in774));
        assertEquals("", readPlainTextComponent(in774));
        assertEquals(1, in774.readVarInt());
        assertEquals("NPC", in774.readString());
        assertEquals(0, in774.available());

        PacketSerializer in776 = new PacketSerializer(new ByteArrayInputStream(encode(
                modern776.createHiddenNameTagTeam("nar_npc_hidden", "NPC"), Status.PLAY, p776())));
        assertEquals(0x6d, in776.readVarInt());
        assertEquals("nar_npc_hidden", in776.readString());
        assertEquals(0, in776.readUnsignedByte());
        assertEquals("NPC", readPlainTextComponent(in776));
        assertEquals("", readPlainTextComponent(in776));
        assertEquals("", readPlainTextComponent(in776));
        assertEquals(1, in776.readVarInt()); // Team.Visibility.NEVER
        assertEquals(0, in776.readVarInt()); // Team.CollisionRule.ALWAYS
        assertFalse(in776.readBoolean());
        assertEquals(0, in776.readUnsignedByte());
        assertEquals(1, in776.readVarInt());
        assertEquals("NPC", in776.readString());
        assertEquals(0, in776.available());
    }

    @Test
    void npcUsesConfiguredSignedSlimSkinAndFacesNorth() throws Exception {
        java.lang.reflect.Field uuidField = NetworkManager.class.getDeclaredField("NPC_PROFILE_UUID");
        uuidField.setAccessible(true);
        UUID expectedUuid = UUID.fromString("38aef317-7922-4c5a-aaf4-e973691f0c15");
        assertEquals(expectedUuid, uuidField.get(null));

        java.lang.reflect.Field skinField = NetworkManager.class.getDeclaredField("NPC_SKIN_PROPERTY");
        skinField.setAccessible(true);
        PlayerListEntry.ProfileProperty property =
                (PlayerListEntry.ProfileProperty) skinField.get(null);
        assertEquals("textures", property.getName());
        assertEquals(expectedUuid, MojangSkinProvider.parseTexturesProfileUuid(property).orElseThrow());
        String payload = new String(Base64.getDecoder().decode(property.getValue()), StandardCharsets.UTF_8);
        assertTrue(payload.contains("bananaapple1234"));
        assertTrue(payload.contains(
                "textures.minecraft.net/texture/412c43c9b82eb15e63e9fdd9d7fb89b3ef57fdc5bc063b05d375d430052347ab"));
        assertTrue(payload.contains("\"model\" : \"slim\""));
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        assertEquals("d18e10c60d175326ec4bba338bc571d4c3c7d5fd98264e60ac607ff6a86866dd",
                HexFormat.of().formatHex(sha256.digest(
                        property.getValue().getBytes(StandardCharsets.UTF_8))));
        assertEquals("b77448a35653897933d1f1b5cadd6cc16e51f9e3f6d99885d7306990f8deb330",
                HexFormat.of().formatHex(sha256.digest(
                        property.getSignature().getBytes(StandardCharsets.UTF_8))));

        assertEquals(180.0F, cn.starry.narcissus.functions.world.NpcSchedule.State.STANDING.yaw());
        assertEquals(0.0F, cn.starry.narcissus.functions.world.NpcSchedule.State.STANDING.pitch());
    }

    @Test
    void npc2UsesConfiguredSignedSlimSkinUuidAndFacesWest() throws Exception {
        java.lang.reflect.Field uuidField = NetworkManager.class.getDeclaredField("NPC2_PROFILE_UUID");
        uuidField.setAccessible(true);
        UUID expectedUuid = UUID.fromString("cdc93444-0383-4d7d-bdf9-e22ecfc930bf");
        assertEquals(expectedUuid, uuidField.get(null));

        java.lang.reflect.Field skinField = NetworkManager.class.getDeclaredField("NPC2_SKIN_PROPERTY");
        skinField.setAccessible(true);
        PlayerListEntry.ProfileProperty property =
                (PlayerListEntry.ProfileProperty) skinField.get(null);
        assertEquals("textures", property.getName());
        assertEquals(expectedUuid, MojangSkinProvider.parseTexturesProfileUuid(property).orElseThrow());
        String payload = new String(Base64.getDecoder().decode(property.getValue()), StandardCharsets.UTF_8);
        assertTrue(payload.contains("RawLobsters"));
        assertTrue(payload.contains(
                "textures.minecraft.net/texture/2235c334155d2118f96a82d097e37b931c3c3b1ef13f92f19f85e107ddc4c2e5"));
        assertTrue(payload.contains("\"model\" : \"slim\""));

        java.lang.reflect.Field yawField = NetworkManager.class.getDeclaredField("NPC2_YAW");
        yawField.setAccessible(true);
        assertEquals(90.0F, yawField.getFloat(null), "WEST must map to yaw 90 for Minecraft angles");
    }

    @Test
    void npcPlayerInfoAndSpawnUseTheSameProfileUuid() throws IOException {
        String payload = "{\"profileId\":\"0123456789abcdef0123456789abcdef\","
                + "\"profileName\":\"Killer\",\"textures\":{}}";
        PlayerListEntry.ProfileProperty property = new PlayerListEntry.ProfileProperty(
                "textures",
                Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8)),
                "encoded-signature");
        UUID npcUuid = MojangSkinProvider.parseTexturesProfileUuid(property).orElseThrow();
        PlayerListEntry entry = new PlayerListEntry(npcUuid, "NPC", 0,
                java.util.Collections.singletonList(property), false);

        for (AbstractProtocol protocol : Arrays.asList(p774(), p776())) {
            boolean is776 = protocol.getVersions()[0] == 776;
            cn.starry.narcissus.functions.packets.out.PacketOut info = is776
                    ? new PacketPlayOutPlayerInfoUpdate776(java.util.Collections.singletonList(entry))
                    : new PacketPlayOutPlayerInfoUpdate774(java.util.Collections.singletonList(entry));
            byte[] infoBytes = encode(info, Status.PLAY, protocol);
            PacketSerializer infoIn = new PacketSerializer(new ByteArrayInputStream(infoBytes));
            infoIn.readVarInt(); // packet id
            assertEquals(0b00111101, infoIn.readUnsignedByte());
            assertEquals(1, infoIn.readVarInt());
            assertEquals(npcUuid, infoIn.readUUID());
            assertEquals("NPC", infoIn.readString());
            assertEquals(1, infoIn.readVarInt());
            assertEquals("textures", infoIn.readString());
            infoIn.readString();
            assertTrue(infoIn.readBoolean());
            assertEquals("encoded-signature", infoIn.readString());
            assertEquals(0, infoIn.readVarInt());
            assertFalse(infoIn.readBoolean(), "NPC profile must stay client-side but remain unlisted");

            cn.starry.narcissus.functions.packets.protocols.ModernProtocol modern =
                    (cn.starry.narcissus.functions.packets.protocols.ModernProtocol) protocol;
            byte[] spawnBytes = encode(modern.createPlayerNpc(
                            2, npcUuid, 0.5D, 88.0D, 5.5D, 180.0F, 0.0F),
                    Status.PLAY, protocol);
            PacketSerializer spawnIn = new PacketSerializer(new ByteArrayInputStream(spawnBytes));
            spawnIn.readVarInt(); // add_entity packet id
            assertEquals(2, spawnIn.readVarInt());
            assertEquals(npcUuid, spawnIn.readUUID());
            spawnIn.readVarInt();
            spawnIn.readLong();
            spawnIn.readLong();
            spawnIn.readLong();
            assertEquals(0, spawnIn.readUnsignedByte());
            assertEquals(0, spawnIn.readUnsignedByte());
            assertEquals(128, spawnIn.readUnsignedByte());
            assertEquals(128, spawnIn.readUnsignedByte());
        }
    }

    @Test
    void playIdsDifferFrom774WhereOfficiallyShifted() {
        AbstractProtocol p774 = p774();
        AbstractProtocol p776 = p776();
        // These must NOT be equal across versions (guards against inheriting 774 ids).
        assertNotEquals(idOf(p774, PacketOutKeepAlive774.class), idOf(p776, PacketOutKeepAlive774.class));
        assertNotEquals(idOf(p774, PacketPlayOutChunkWithLight774.class), idOf(p776, PacketPlayOutChunkWithLight774.class));
        assertNotEquals(idOf(p774, PacketPlayOutJoinGame774.class), idOf(p776, PacketPlayOutJoinGame776.class));
        assertNotEquals(idOf(p774, PacketPlayOutSyncPosition774.class), idOf(p776, PacketPlayOutSyncPosition774.class));
        assertNotEquals(idOf(p774, PacketPlayOutSystemChat774.class), idOf(p776, PacketPlayOutSystemChat774.class));
        // And these MUST be equal (officially unchanged).
        assertEquals(idOf(p774, PacketOutDisconnect774.class), idOf(p776, PacketOutDisconnect774.class));
        assertEquals(idOf(p774, PacketPlayOutGameEvent774.class), idOf(p776, PacketPlayOutGameEvent774.class));
    }

    private static int idOf(AbstractProtocol p, Class<? extends cn.starry.narcissus.functions.packets.out.PacketOut> c) {
        return p.getPacketOutByClass(c, Status.PLAY);
    }

    @Test
    void playServerboundKeepAliveAndTeleportIdsMatchOfficial26_2() {
        AbstractProtocol p = p776();
        // keep_alive serverbound = 0x1c in 26.2 (0x1b in 774)
        assertEquals(PacketInKeepAlive774.class, p.getPacketInById(0x1c, Status.PLAY));
        // accept_teleportation = 0x00 in both
        assertEquals(PacketPlayInAcceptTeleport774.class, p.getPacketInById(0x00, Status.PLAY));
    }

    @Test
    void keepAliveIsNotIgnoredIn776() {
        AbstractProtocol p = p776();
        Class<? extends PacketIn> at1c = p.getPacketInById(0x1c, Status.PLAY);
        assertEquals(PacketInKeepAlive774.class, at1c,
                "776 keep_alive (0x1c) must not be swallowed by the legacy ignore list");
    }

    @Test
    void chatAndCommandIdsMatchOfficial774And776Reports() {
        AbstractProtocol p774 = p774();
        AbstractProtocol p776 = p776();

        assertEquals(PacketPlayInChatCommand774.class, p774.getPacketInById(0x06, Status.PLAY));
        assertEquals(PacketPlayInChatCommand774.class, p774.getPacketInById(0x07, Status.PLAY));
        assertEquals(PacketPlayInChat774.class, p774.getPacketInById(0x08, Status.PLAY));
        assertEquals(PacketPlayInCommandSuggestion774.class, p774.getPacketInById(0x0e, Status.PLAY));

        assertEquals(PacketPlayInChatCommand774.class, p776.getPacketInById(0x07, Status.PLAY));
        assertEquals(PacketPlayInChatCommand774.class, p776.getPacketInById(0x08, Status.PLAY));
        assertEquals(PacketPlayInChat774.class, p776.getPacketInById(0x09, Status.PLAY));
        assertEquals(PacketPlayInCommandSuggestion774.class, p776.getPacketInById(0x0f, Status.PLAY));
        assertEquals(cn.starry.narcissus.functions.packets.in.PacketInIgnored.class,
                p774.getPacketInById(0x1d, Status.PLAY));
        assertEquals(cn.starry.narcissus.functions.packets.in.PacketInIgnored.class,
                p774.getPacketInById(0x1e, Status.PLAY));
        assertEquals(cn.starry.narcissus.functions.packets.in.PacketInIgnored.class,
                p776.getPacketInById(0x1e, Status.PLAY));
        assertEquals(cn.starry.narcissus.functions.packets.in.PacketInIgnored.class,
                p776.getPacketInById(0x1f, Status.PLAY));
    }

    @Test
    void modernChatCommandReadsOnlyTheCommandField() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PacketSerializer writer = new PacketSerializer(out);
        writer.writeString("version");
        writer.writeRawBytes(new byte[]{0x01, 0x02, 0x03, 0x04});

        PacketPlayInChatCommand774 packet = new PacketPlayInChatCommand774();
        packet.readPacket(new PacketSerializer(new ByteArrayInputStream(out.toByteArray())));

        assertEquals("version", packet.getCommand());
    }

    @Test
    void modernSystemChatKeepsBodyAcross774And776() throws IOException {
        byte[] b774 = encode(new PacketPlayOutSystemChat774("§b测试", false), Status.PLAY, p774());
        byte[] b776 = encode(new PacketPlayOutSystemChat774("§b测试", false), Status.PLAY, p776());

        PacketSerializer id774 = new PacketSerializer(new ByteArrayInputStream(b774));
        PacketSerializer id776 = new PacketSerializer(new ByteArrayInputStream(b776));
        assertEquals(0x77, id774.readVarInt());
        assertEquals(0x79, id776.readVarInt());
        assertEquals(8, id774.readUnsignedByte());
        int textLength = id774.readShort() & 0xFFFF;
        String text = new String(id774.readBytes(textLength), StandardCharsets.UTF_8);
        assertEquals("§b测试", text);
        assertFalse(text.startsWith("{"), "modern system_chat must not wrap plain text in JSON");
        assertFalse(id774.readBoolean());

        byte[] body774 = new byte[b774.length - 1];
        byte[] body776 = new byte[b776.length - 1];
        System.arraycopy(b774, 1, body774, 0, body774.length);
        System.arraycopy(b776, 1, body776, 0, body776.length);
        assertArrayEquals(body774, body776);
    }

    @Test
    void clickableModernSystemChatUsesUnnamedCompoundNbt() throws IOException {
        byte[] body = encode(PacketPlayOutSystemChat774.clickable(
                "§f密钥：§eABC", "copy_to_clipboard", "ABC", false), Status.PLAY, p774());
        PacketSerializer in = new PacketSerializer(new ByteArrayInputStream(body));
        assertEquals(0x77, in.readVarInt());
        assertEquals(10, in.readUnsignedByte());
        assertEquals(8, in.readUnsignedByte());
        assertEquals("type", readNbtString(in));
        assertEquals("text", readNbtString(in));
        assertEquals(8, in.readUnsignedByte());
        assertEquals("text", readNbtString(in));
        assertEquals("§f密钥：§eABC", readNbtString(in));
        assertEquals(10, in.readUnsignedByte());
        assertEquals("click_event", readNbtString(in));
        assertEquals(8, in.readUnsignedByte());
        assertEquals("action", readNbtString(in));
        assertEquals("copy_to_clipboard", readNbtString(in));
        assertEquals(8, in.readUnsignedByte());
        assertEquals("value", readNbtString(in));
        assertEquals("ABC", readNbtString(in));
        assertEquals(0, in.readUnsignedByte());
        assertEquals(0, in.readUnsignedByte());
        assertFalse(in.readBoolean());
    }

    @Test
    void clickableSuffixModernSystemChatKeepsSecretOutsideClickEvent() throws IOException {
        byte[] body = encode(PacketPlayOutSystemChat774.clickableSuffix(
                "§f密钥：§eABC", " §b[点我复制]", "copy_to_clipboard", "ABC", false),
                Status.PLAY, p774());
        PacketSerializer in = new PacketSerializer(new ByteArrayInputStream(body));
        assertEquals(0x77, in.readVarInt());
        assertEquals(10, in.readUnsignedByte());
        assertEquals(8, in.readUnsignedByte());
        assertEquals("type", readNbtString(in));
        assertEquals("text", readNbtString(in));
        assertEquals(8, in.readUnsignedByte());
        assertEquals("text", readNbtString(in));
        assertEquals("§f密钥：§eABC", readNbtString(in));
        assertEquals(9, in.readUnsignedByte());
        assertEquals("extra", readNbtString(in));
        assertEquals(10, in.readUnsignedByte());
        assertEquals(1, in.readInt());
        assertEquals(8, in.readUnsignedByte());
        assertEquals("type", readNbtString(in));
        assertEquals("text", readNbtString(in));
        assertEquals(8, in.readUnsignedByte());
        assertEquals("text", readNbtString(in));
        assertEquals(" §b[点我复制]", readNbtString(in));
        assertEquals(10, in.readUnsignedByte());
        assertEquals("click_event", readNbtString(in));
        assertEquals(8, in.readUnsignedByte());
        assertEquals("action", readNbtString(in));
        assertEquals("copy_to_clipboard", readNbtString(in));
        assertEquals(8, in.readUnsignedByte());
        assertEquals("value", readNbtString(in));
        assertEquals("ABC", readNbtString(in));
        assertEquals(0, in.readUnsignedByte());
        assertEquals(0, in.readUnsignedByte());
        assertEquals(0, in.readUnsignedByte());
        assertFalse(in.readBoolean());
    }

    @Test
    void legacyClickableSuffixKeepsSecretOutsideClickEvent() {
        assertEquals(
                "{\"text\":\"§f密钥：§eABC\",\"extra\":[{\"text\":\" §b[点我复制]\","
                        + "\"clickEvent\":{\"action\":\"suggest_command\",\"value\":\"ABC\"}}]}",
                TextUtil.toJsonClickableSuffixComponent(
                        "§f密钥：§eABC", " §b[点我复制]", "suggest_command", "ABC"));
    }

    @Test
    void modernPlayerInfoDisplayNameUsesNbtComponent() throws IOException {
        java.util.UUID uuid = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001");
        java.util.List<cn.starry.narcissus.functions.packets.PlayerListEntry> entries =
                java.util.Collections.singletonList(
                        new cn.starry.narcissus.functions.packets.PlayerListEntry(uuid, "Steve", 1));
        byte[] body = encode(new PacketPlayOutPlayerInfoUpdate774(entries), Status.PLAY, p774());
        PacketSerializer in = new PacketSerializer(new ByteArrayInputStream(body));
        assertEquals(0x44, in.readVarInt());
        assertEquals(0b00111101, in.readUnsignedByte());
        assertEquals(1, in.readVarInt());
        assertEquals(uuid, in.readUUID());
        assertEquals("Steve", in.readString());
        assertEquals(0, in.readVarInt());
        assertEquals(1, in.readVarInt());
        assertTrue(in.readBoolean());
        assertEquals(0, in.readVarInt());
        assertTrue(in.readBoolean());
        assertEquals(8, in.readUnsignedByte());
        int textLength = in.readShort() & 0xFFFF;
        assertEquals("§7Steve", new String(in.readBytes(textLength), StandardCharsets.UTF_8));
    }

    @Test
    void commandTreeAndSuggestionEncodingsUseModernIds() throws IOException {
        byte[] tree = encode(new PacketPlayOutCommands774(Arrays.asList("limbo", "version")),
                Status.PLAY, p774());
        PacketSerializer treeIn = new PacketSerializer(new ByteArrayInputStream(tree));
        assertEquals(0x10, treeIn.readVarInt());
        assertEquals(3, treeIn.readVarInt()); // root + two literals
        assertEquals(0, treeIn.readUnsignedByte());
        assertEquals(2, treeIn.readVarInt());
        assertEquals(1, treeIn.readVarInt());
        assertEquals(2, treeIn.readVarInt());
        assertEquals(0x05, treeIn.readUnsignedByte());
        assertEquals(0, treeIn.readVarInt());
        assertEquals("limbo", treeIn.readString());
        assertEquals(0x05, treeIn.readUnsignedByte());
        assertEquals(0, treeIn.readVarInt());
        assertEquals("version", treeIn.readString());
        assertEquals(0, treeIn.readVarInt());

        byte[] suggestions = encode(new PacketPlayOutCommandSuggestions774(
                        7, 1, 2, Arrays.asList("limbo")),
                Status.PLAY, p774());
        PacketSerializer suggestionIn = new PacketSerializer(new ByteArrayInputStream(suggestions));
        assertEquals(0x0f, suggestionIn.readVarInt());
        assertEquals(7, suggestionIn.readVarInt());
        assertEquals(1, suggestionIn.readVarInt());
        assertEquals(2, suggestionIn.readVarInt());
        assertEquals(1, suggestionIn.readVarInt());
        assertEquals("limbo", suggestionIn.readString());
        assertFalse(suggestionIn.readBoolean());
    }

    @Test
    void entityMetadataUsesModernTypeAndVersionSpecificIds() throws IOException {
        byte[] b774 = encode(new PacketPlayOutEntityMetadata774(1, 0x35), Status.PLAY, p774());
        byte[] b776 = encode(new PacketPlayOutEntityMetadata776(1, 0x35), Status.PLAY, p776());

        PacketSerializer in774 = new PacketSerializer(new ByteArrayInputStream(b774));
        assertEquals(0x61, in774.readVarInt());
        assertEquals(1, in774.readVarInt());
        assertEquals(16, in774.readUnsignedByte());
        assertEquals(0, in774.readVarInt());
        assertEquals(0x35, in774.readUnsignedByte());
        assertEquals(0xFF, in774.readUnsignedByte());

        PacketSerializer in776 = new PacketSerializer(new ByteArrayInputStream(b776));
        assertEquals(0x63, in776.readVarInt());
        assertEquals(1, in776.readVarInt());
        assertEquals(16, in776.readUnsignedByte());
        assertEquals(0, in776.readVarInt());
        assertEquals(0x35, in776.readUnsignedByte());
        assertEquals(0xFF, in776.readUnsignedByte());

        assertArrayEquals(Arrays.copyOfRange(b774, 1, b774.length),
                Arrays.copyOfRange(b776, 1, b776.length));
    }

    @Test
    void playerNpcAddEntityUsesVersionSpecificPlayerTypeIds() throws IOException {
        UUID uuid = UUID.fromString("38aef317-7922-4c5a-aaf4-e973691f0c15");
        byte[] b774 = encode(((cn.starry.narcissus.functions.packets.protocols.ModernProtocol) p774())
                        .createPlayerNpc(2, uuid, 0.5D, 88.0D, 5.5D, 180.0F, 0.0F),
                Status.PLAY, p774());
        byte[] b776 = encode(((cn.starry.narcissus.functions.packets.protocols.ModernProtocol) p776())
                        .createPlayerNpc(2, uuid, 0.5D, 88.0D, 5.5D, 180.0F, 0.0F),
                Status.PLAY, p776());

        PacketSerializer in774 = new PacketSerializer(new ByteArrayInputStream(b774));
        assertEquals(0x01, in774.readVarInt());
        assertEquals(2, in774.readVarInt());
        assertEquals(uuid, in774.readUUID());
        assertEquals(155, in774.readVarInt());
        assertEquals(0.5D, Double.longBitsToDouble(in774.readLong()));
        assertEquals(88.0D, Double.longBitsToDouble(in774.readLong()));
        assertEquals(5.5D, Double.longBitsToDouble(in774.readLong()));
        assertEquals(0, in774.readUnsignedByte()); // zero LpVec3 movement marker
        assertEquals(0, in774.readUnsignedByte()); // pitch
        assertEquals(128, in774.readUnsignedByte()); // NORTH yaw
        assertEquals(128, in774.readUnsignedByte()); // NORTH head yaw
        assertEquals(0, in774.readVarInt());

        PacketSerializer in776 = new PacketSerializer(new ByteArrayInputStream(b776));
        assertEquals(0x01, in776.readVarInt());
        assertEquals(2, in776.readVarInt());
        assertEquals(uuid, in776.readUUID());
        assertEquals(156, in776.readVarInt());
        assertEquals(0.5D, Double.longBitsToDouble(in776.readLong()));
        assertEquals(88.0D, Double.longBitsToDouble(in776.readLong()));
        assertEquals(5.5D, Double.longBitsToDouble(in776.readLong()));
        assertEquals(0, in776.readUnsignedByte()); // zero LpVec3 movement marker
        assertEquals(0, in776.readUnsignedByte());
        assertEquals(128, in776.readUnsignedByte());
        assertEquals(128, in776.readUnsignedByte());
        assertEquals(0, in776.readVarInt());
    }

    @Test
    void golden_keepAliveEncodingUses776Id() throws IOException {
        byte[] body = encode(new PacketOutKeepAlive774(0x0102030405060708L), Status.PLAY, p776());
        PacketSerializer in = new PacketSerializer(new ByteArrayInputStream(body));
        assertEquals(0x2c, in.readVarInt(), "keep_alive clientbound id must be 0x2c in 26.2");
        assertEquals(0x0102030405060708L, in.readLong());
    }

    @Test
    void golden_joinGameEncodingUses776Id() throws IOException {
        byte[] body = encode(new PacketPlayOutJoinGame776(
                1, new String[]{"minecraft:the_end"}, 20, 2, 2, false, 2,
                "minecraft:the_end", 0, 63), Status.PLAY, p776());
        PacketSerializer in = new PacketSerializer(new ByteArrayInputStream(body));
        assertEquals(0x31, in.readVarInt(), "login (join game) id must be 0x31 in 26.2");
        assertEquals(1, in.readInt());       // entity id
        assertFalse(in.readBoolean());       // hardcore
        assertEquals(1, in.readVarInt());    // world count
        assertEquals("minecraft:the_end", in.readString());
    }

    @Test
    void joinGame776AppendsOnlineMode() throws IOException {
        // 776 join body must be one byte longer than 774 (extra onlineMode bool)
        byte[] b774 = encode(new PacketPlayOutJoinGame774(
                1, new String[]{"minecraft:the_end"}, 20, 2, 2, false, 2,
                "minecraft:the_end", 0, 63), Status.PLAY, p774());
        byte[] b776 = encode(new PacketPlayOutJoinGame776(
                1, new String[]{"minecraft:the_end"}, 20, 2, 2, false, 2,
                "minecraft:the_end", 0, 63), Status.PLAY, p776());
        assertEquals(b774.length + 1, b776.length, "776 join game must append onlineMode");
    }

    @Test
    void golden_loginSuccessEncoding() throws IOException {
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        byte[] body = encode(new PacketLoginOutSuccess776(uuid, "Steve", new UUID(0L, 0L)), Status.LOGIN, p776());
        PacketSerializer in = new PacketSerializer(new ByteArrayInputStream(body));
        assertEquals(0x02, in.readVarInt());
        byte[] uuidBytes = new byte[16];
        for (int i = 0; i < 16; i++) uuidBytes[i] = (byte) in.readByte();
        assertEquals("Steve", in.readString());
        assertEquals(0, in.readVarInt());    // properties count
        // 776 appends a 16-byte session UUID after properties
        assertEquals(16, body.length - (1 + 16 + (1 + 5) + 1), "776 login success must append sessionId");
    }

    private static String readPlainTextComponent(PacketSerializer in) throws IOException {
        assertEquals(8, in.readUnsignedByte());
        return readNbtString(in);
    }

    private static String readNbtString(PacketSerializer in) throws IOException {
        int length = in.readShort() & 0xFFFF;
        return new String(in.readBytes(length), StandardCharsets.UTF_8);
    }

    private static byte[] encode(cn.starry.narcissus.functions.packets.out.PacketOut packet,
                                 Status status, AbstractProtocol protocol) throws IOException {
        int id = protocol.getPacketOutByClass(packet.getClass(), status);
        assertTrue(id != -1, "packet not registered: " + packet.getClass());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PacketSerializer s = new PacketSerializer(out);
        s.writeVarInt(id);
        packet.write(s);
        return out.toByteArray();
    }
}
