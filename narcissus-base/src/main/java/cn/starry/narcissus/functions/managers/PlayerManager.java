package cn.starry.narcissus.functions.managers;

import cn.starry.narcissus.functions.packets.AbstractProtocol;
import cn.starry.narcissus.functions.packets.PlayerListEntry;
import cn.starry.narcissus.functions.packets.Status;
import cn.starry.narcissus.functions.world.NpcSchedule;
import cn.starry.narcissus.functions.world.World;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by Rigner on 29/08/16 for project Limbo.
 * All rights reserved.
 *
 * Holds per-connection state: an incremental inbound byte accumulator, an
 * outbound frame queue drained by the selector thread, the real transport
 * address (used for trusting BungeeCord forwarding) and the forwarded player
 * address, plus per-connection keepalive tracking.
 */
public class PlayerManager
{
    private static final int INITIAL_BUFFER = 512;

    private final SocketChannel socket;
    private final Queue<ByteBuffer> outboundQueue = new ConcurrentLinkedQueue<>();

    private byte[] inboundBuffer = new byte[INITIAL_BUFFER];
    private int inboundLength = 0;

    private AbstractProtocol protocol;
    private Status status;
    /** Mutable world instance owned exclusively by this connection. */
    private World world;
    private String userName;
    private UUID uuid;
    private boolean uuidProvided;
    private boolean uuidForwarded;
    private final InetSocketAddress transportAddress;
    private InetSocketAddress inetAddress;
    private int protocolId;
    private long lastKeepAlive;

    // ---- modern (1.20.2+) protocol state ----
    private boolean loginAcknowledged;
    private boolean configurationFinished;
    private boolean authoritativeBlocksPending;
    private boolean chunkViewCenterInitialized;
    private int chunkViewCenterX;
    private int chunkViewCenterZ;
    private boolean initialChunkBatchReceived;
    private final Map<Long, Integer> blockOverrides = new ConcurrentHashMap<>();
    private int pendingTeleportId;
    private boolean awaitingTeleportConfirm;
    private long lastSentKeepAliveId;
    private String clientBrand;
    private volatile PlayerListEntry.ProfileProperty skinProperty;
    private volatile int skinParts = 0x7F;
    private NpcSchedule.State npcState;
    private boolean npc2Visible;

    public PlayerManager(NetworkManager networkManager, SocketChannel socket) throws IOException
    {
        this.socket = socket;
        this.protocol = networkManager.getProtocolByVersionId(-1);
        this.status = Status.HANDSHAKE;
        this.userName = "";
        this.protocolId = -1;
        this.lastKeepAlive = System.currentTimeMillis();
        this.uuid = UUID.randomUUID();
        this.transportAddress = (InetSocketAddress) socket.getRemoteAddress();
        this.inetAddress = this.transportAddress;
    }

    public SocketChannel getSocket()
    {
        return this.socket;
    }

    // ---- inbound accumulation -------------------------------------------------

    public void appendInbound(byte[] chunk)
    {
        ensureCapacity(this.inboundLength + chunk.length);
        System.arraycopy(chunk, 0, this.inboundBuffer, this.inboundLength, chunk.length);
        this.inboundLength += chunk.length;
    }

    public byte[] getInboundData()
    {
        return this.inboundBuffer;
    }

    public int getInboundLength()
    {
        return this.inboundLength;
    }

    public void consumeInbound(int count)
    {
        if (count <= 0)
            return;
        if (count >= this.inboundLength)
        {
            this.inboundLength = 0;
            return;
        }
        System.arraycopy(this.inboundBuffer, count, this.inboundBuffer, 0, this.inboundLength - count);
        this.inboundLength -= count;
    }

    private void ensureCapacity(int required)
    {
        if (required <= this.inboundBuffer.length)
            return;
        int newSize = this.inboundBuffer.length;
        while (newSize < required)
            newSize <<= 1;
        this.inboundBuffer = Arrays.copyOf(this.inboundBuffer, newSize);
    }

    // ---- outbound queue -------------------------------------------------------

    public void queueOutbound(ByteBuffer buffer)
    {
        this.outboundQueue.add(buffer);
    }

    public Queue<ByteBuffer> getOutboundQueue()
    {
        return this.outboundQueue;
    }

    // ---- protocol / status ----------------------------------------------------

    public AbstractProtocol getProtocol()
    {
        return this.protocol;
    }

    public void setProtocol(AbstractProtocol protocol)
    {
        this.protocol = protocol;
    }

    public World getWorld()
    {
        return this.world;
    }

    public void setWorld(World world)
    {
        this.world = world;
    }

    public Status getStatus()
    {
        return this.status;
    }

    public void setStatus(Status status)
    {
        this.status = status;
    }


    public String getUserName()
    {
        return this.userName;
    }

    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    public void setProtocolId(int protocolId)
    {
        this.protocolId = protocolId;
    }

    public int getProtocolId()
    {
        return this.protocolId;
    }

    public long getLastKeepAlive()
    {
        return this.lastKeepAlive;
    }

    public void keepAlive()
    {
        this.lastKeepAlive = System.currentTimeMillis();
    }

    public UUID getUuid()
    {
        return this.uuid;
    }

    public void setUuid(UUID uuid)
    {
        if (this.uuidForwarded)
            return;
        this.uuid = uuid;
        this.uuidProvided = uuid != null;
    }

    /** Stores the UUID supplied by trusted BungeeCord legacy forwarding. */
    public void setForwardedUuid(UUID uuid)
    {
        this.uuid = uuid;
        this.uuidProvided = uuid != null;
        this.uuidForwarded = uuid != null;
    }

    public boolean isUuidProvided()
    {
        return this.uuidProvided;
    }

    public boolean isUuidForwarded()
    {
        return this.uuidForwarded;
    }

    /** The address BungeeCord forwarding may override with the real player IP. */
    public InetSocketAddress getInetAddress()
    {
        return this.inetAddress;
    }

    public void setInetAddress(InetSocketAddress inetAddress)
    {
        this.inetAddress = inetAddress;
    }

    /** The genuine transport-level remote address; never overwritten by forwarding. */
    public InetSocketAddress getTransportAddress()
    {
        return this.transportAddress;
    }

    public boolean isLoopbackTransport()
    {
        return this.transportAddress != null
                && this.transportAddress.getAddress() != null
                && this.transportAddress.getAddress().isLoopbackAddress();
    }

    // ---- modern protocol state accessors ----

    public boolean isLoginAcknowledged()
    {
        return this.loginAcknowledged;
    }

    public void setLoginAcknowledged(boolean loginAcknowledged)
    {
        this.loginAcknowledged = loginAcknowledged;
    }

    public boolean isConfigurationFinished()
    {
        return this.configurationFinished;
    }

    public void setConfigurationFinished(boolean configurationFinished)
    {
        this.configurationFinished = configurationFinished;
    }

    public boolean isAuthoritativeBlocksPending()
    {
        return this.authoritativeBlocksPending;
    }

    public void setAuthoritativeBlocksPending(boolean authoritativeBlocksPending)
    {
        this.authoritativeBlocksPending = authoritativeBlocksPending;
    }

    public boolean isChunkViewCenterInitialized()
    {
        return this.chunkViewCenterInitialized;
    }

    public int getChunkViewCenterX()
    {
        return this.chunkViewCenterX;
    }

    public int getChunkViewCenterZ()
    {
        return this.chunkViewCenterZ;
    }

    public void setChunkViewCenter(int chunkX, int chunkZ)
    {
        this.chunkViewCenterX = chunkX;
        this.chunkViewCenterZ = chunkZ;
        this.chunkViewCenterInitialized = true;
    }

    public boolean isInitialChunkBatchReceived()
    {
        return this.initialChunkBatchReceived;
    }

    public void setInitialChunkBatchReceived(boolean initialChunkBatchReceived)
    {
        this.initialChunkBatchReceived = initialChunkBatchReceived;
    }

    public void recordBlockOverride(long position, int blockStateId)
    {
        this.blockOverrides.put(position, blockStateId);
    }

    public Map<Long, Integer> getBlockOverrides()
    {
        return this.blockOverrides;
    }

    public int getPendingTeleportId()
    {
        return this.pendingTeleportId;
    }

    public void setPendingTeleportId(int pendingTeleportId)
    {
        this.pendingTeleportId = pendingTeleportId;
        this.awaitingTeleportConfirm = true;
    }

    public boolean isAwaitingTeleportConfirm()
    {
        return this.awaitingTeleportConfirm;
    }

    public void confirmTeleport()
    {
        this.awaitingTeleportConfirm = false;
    }

    public long getLastSentKeepAliveId()
    {
        return this.lastSentKeepAliveId;
    }

    public void setLastSentKeepAliveId(long lastSentKeepAliveId)
    {
        this.lastSentKeepAliveId = lastSentKeepAliveId;
    }

    public String getClientBrand()
    {
        return this.clientBrand;
    }

    public void setClientBrand(String clientBrand)
    {
        this.clientBrand = clientBrand;
    }

    public PlayerListEntry.ProfileProperty getSkinProperty()
    {
        return this.skinProperty;
    }

    public void setSkinProperty(PlayerListEntry.ProfileProperty skinProperty)
    {
        this.skinProperty = skinProperty;
    }

    public int getSkinParts()
    {
        return this.skinParts;
    }

    public void setSkinParts(int skinParts)
    {
        this.skinParts = skinParts & 0x7F;
    }

    public NpcSchedule.State getNpcState()
    {
        return this.npcState;
    }

    public void setNpcState(NpcSchedule.State npcState)
    {
        this.npcState = npcState;
    }

    public boolean isNpc2Visible()
    {
        return this.npc2Visible;
    }

    public void setNpc2Visible(boolean npc2Visible)
    {
        this.npc2Visible = npc2Visible;
    }
}
