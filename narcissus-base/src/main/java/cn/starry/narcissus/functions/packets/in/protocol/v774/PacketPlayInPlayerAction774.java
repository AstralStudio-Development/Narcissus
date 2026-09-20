package cn.starry.narcissus.functions.packets.in.protocol.v774;

import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.in.PacketIn;
import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketPlayOutBlockUpdate774;
import cn.starry.narcissus.functions.packets.protocols.ModernProtocol;
import cn.starry.narcissus.functions.world.World;
import cn.starry.narcissus.functions.world.WorldLighting;

import java.io.IOException;

/**
 * Player Action (player_action, serverbound PLAY 0x28 in 774 / 0x29 in 776).
 * Layout: varint action; i64 position; i8 face; varint sequence.
 *
 * Survival clients report FINISHED_DIGGING (action 2), while creative clients
 * can break immediately on STARTED_DIGGING (action 0). Both paths clear the
 * block in that player's private world and echo a block_update (air) back to
 * the digger so only that player's view changes.
 */
public class PacketPlayInPlayerAction774 implements PacketIn {

    private static final int ACTION_STARTED_DIGGING = 0;
    private static final int ACTION_FINISHED_DIGGING = 2;
    /** Global block-state id of air. */
    private static final int AIR_STATE = 0;

    private int action;
    private long position;
    @SuppressWarnings("unused")
    private byte face;
    @SuppressWarnings("unused")
    private int sequence;

    @Override
    public void readPacket(PacketSerializer s) throws IOException {
        this.action = s.readVarInt();
        this.position = s.readLong();
        this.face = s.readByte();
        this.sequence = s.readVarInt();
    }

    @Override
    public void handlePacket(NetworkManager networkManager, PlayerManager playerManager) {
        boolean creative = networkManager.getLimboConfiguration() != null
                && (networkManager.getLimboConfiguration().getGameMode() & 0xFF) == 1;
        boolean shouldBreak = this.action == ACTION_FINISHED_DIGGING
                || (creative && this.action == ACTION_STARTED_DIGGING);
        if (!shouldBreak) {
            return;
        }
        World world = playerManager.getWorld();
        if (world == null) {
            return;
        }
        // Protocol 774 packed block position: x = bits 38..63,
        // y = bits 26..37, z = bits 0..25; all three fields are signed.
        int x = (int) (this.position >> 38);
        int y = (int) ((this.position >> 26) & 0xFFF);
        if ((y & 0x800) != 0) {
            y -= 0x1000;
        }
        int z = (int) (this.position & 0x3FFFFFFL);
        if ((z & 0x2000000) != 0) {
            z -= 0x4000000;
        }

        if (!world.setBlockAir(x, y, z)) {
            return; // outside the loaded schematic: nothing to update
        }
        playerManager.recordBlockOverride(this.position, AIR_STATE);
        networkManager.sendPacket(playerManager,
                new PacketPlayOutBlockUpdate774(this.position, AIR_STATE));
        WorldLighting lighting = world.getLighting();
        if (lighting != null && playerManager.getProtocol() instanceof ModernProtocol protocol) {
            for (WorldLighting.ChunkLightChange change : world.consumeLightChanges()) {
                networkManager.sendPacket(playerManager, protocol.createLightUpdate(
                        change.chunkX(), change.chunkZ(), lighting,
                        change.skySections(), change.blockSections()));
            }
        }
    }
}
