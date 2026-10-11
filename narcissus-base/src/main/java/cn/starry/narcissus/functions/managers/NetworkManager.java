package cn.starry.narcissus.functions.managers;

import cn.starry.narcissus.Narcissus;
import cn.starry.narcissus.functions.auth.AuthenticationProvider;
import cn.starry.narcissus.functions.auth.AuthenticationResult;
import cn.starry.narcissus.functions.auth.AuthenticationSessionHandler;
import cn.starry.narcissus.functions.config.Configuration;
import cn.starry.narcissus.functions.packets.AbstractProtocol;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.Status;
import cn.starry.narcissus.functions.packets.in.PacketIn;
import cn.starry.narcissus.functions.commands.CommandManager;
import cn.starry.narcissus.functions.packets.PlayerListEntry;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import cn.starry.narcissus.functions.packets.out.protocol.v47.*;
import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketPlayOutSystemChat774;
import cn.starry.narcissus.functions.packets.protocols.*;
import cn.starry.narcissus.util.MojangSkinProvider;
import cn.starry.narcissus.util.SkinPropertyPayload;
import cn.starry.narcissus.util.TextUtil;
import cn.starry.narcissus.functions.world.NpcSchedule;
import cn.starry.narcissus.functions.world.World;
import cn.starry.narcissus.functions.world.WorldTimeService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;

/**
 * Created by Rigner on 30/08/16, Update by Stalyer for project Limbo.
 * All rights reserved.
 *
 * Reworked for the plugin lifecycle:
 * <ul>
 *   <li>Only Handshake + Protocol47 register as the backend line protocol.</li>
 *   <li>Non-blocking selector with a per-connection write queue and OP_WRITE, so
 *       large (~196 KiB) chunk packets are never truncated by a partial write.</li>
 *   <li>Cross-thread work is posted to a task queue and the selector is woken up,
 *       so login and shutdown never race the selector thread on the same channel.</li>
 *   <li>An incremental, bounded VarInt frame accumulator handles split/joined
 *       packets cleanly.</li>
 * </ul>
 */
public class NetworkManager
{
    /** Hard cap for a single inbound frame (2 MiB) to bound memory on malicious input. */
    public static final int MAX_FRAME_SIZE = 2 * 1024 * 1024;

    private static final String WELCOME_MESSAGE_ONE = "&c你被移动至Limbo服务器";
    private static final String WELCOME_MESSAGE_TWO = "&b输入 /limbo 来获取更多信息";
    private static final String TIME_COMMAND_USAGE = "&e用法：/time <HH:mm|0-23999|sync>";
    private static final String TAB_HEADER = " \n&b&l间歇泉\n         &7就此沉眠吧...         \n ";
    private static final String TAB_FOOTER = " \n&bʏᴜᴍᴇɢᴀᴍᴇs\n ";

    private static final int NPC_ENTITY_ID = 2;
    private static final int NPC_SEAT_ENTITY_ID = 3;
    private static final String NPC_NAME = "NPC1";
    private static final String NPC_TEAM_NAME = "nar_npc_hidden";
    private static final UUID NPC_PROFILE_UUID =
            UUID.fromString("38aef317-7922-4c5a-aaf4-e973691f0c15");
    private static final String NPC_SKIN_VALUE =
            "ewogICJ0aW1lc3RhbXAiIDogMTc3NDUzMzMyNjg2NywKICAicHJvZmlsZUlkIiA6ICIzOGFlZjMxNzc5MjI0YzVhYWFmNGU5" +
            "NzM2OTFmMGMxNSIsCiAgInByb2ZpbGVOYW1lIiA6ICJiYW5hbmFhcHBsZTEyMzQiLAogICJzaWduYXR1cmVSZXF1aXJlZCIg" +
            "OiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5t" +
            "aW5lY3JhZnQubmV0L3RleHR1cmUvNDEyYzQzYzliODJlYjE1ZTYzZTlmZGQ5ZDdmYjg5YjNlZjU3ZmRjNWJjMDYzYjA1ZDM3" +
            "NWQ0MzAwNTIzNDdhYiIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAg" +
            "ICB9CiAgfQp9";
    private static final String NPC_SKIN_SIGNATURE =
            "STEGAt9GCE4d69JFDG0wYv0M4eVYqXRI4C1kYYXHfWaV2Msak7xv9isZL7pDRTZDZZq8pFCe/ZYX4k6T7wxVh7U7JviTeCgz" +
            "eRIuZABQYI5PF6iHwY/yB9sr0U/Z1J2b6Yj3QhRS+joEQE9TuekrDahNYHpABpKZdSxPEFjVmAxqnYqx1Nff+M4GewmkbKfp" +
            "FOCXdYKmDnVdYfl06jp7unMGu2gpV8H2ooD5ZKtHSZwDiIA5havW7tV/Nb5oEfUeYSnNIQV9IGAbi8HG6s9zk/CE1XzMQvyS" +
            "y9vsaPulygeglFyBtzNjPMqmQwaybCoizh5M7n1x9m2WY/JTBkadhmpoorZzG8UZqhQYfatLh9SXFDwS7CrzZmiRAW/ChQ+c" +
            "6intwUFEb6Q7+g/LMmJ9o2MD8lc75XFa0IPk4whhUhaofvOd5mVH+GNrnwJrP/KraGmtHJjm/QoII3jFai0OZ0dhTIkv6t+c" +
            "MLEiTD3NwjvSAzLH6LQqwmhmCYGxMBO5wz544Du3cLkuTEcGH8hWIaW7ufWzZlQV6KAXVPMf0qV7iBr8YGbHjtPDN0ohEPof" +
            "h03tEu+P8ao68vjTg37oq4hHacXPt7g9VpCHI6y47kAac1UXVEWSmrcsZRc6aFtSX3DwhhRPDMg2kqGVne9HLHVrlu2Xh5wL" +
            "M8qG55KkG3E=";
    private static final PlayerListEntry.ProfileProperty NPC_SKIN_PROPERTY =
            new PlayerListEntry.ProfileProperty("textures", NPC_SKIN_VALUE, NPC_SKIN_SIGNATURE);
    private static final int NPC2_ENTITY_ID = 4;
    private static final int NPC2_SEAT_ENTITY_ID = 5;
    private static final String NPC2_NAME = "NPC2";
    private static final String NPC2_TEAM_NAME = "nar_npc2_hidden";
    private static final String NPC2_SKIN_VALUE =
            "ewogICJ0aW1lc3RhbXAiIDogMTYzMDQ0NDU5MDkyMywKICAicHJvZmlsZUlkIiA6ICJjZGM5MzQ0NDAzODM0ZDdkYmRmOWUyMmVj" +
            "ZmM5MzBiZiIsCiAgInByb2ZpbGVOYW1lIiA6ICJSYXdMb2JzdGVycyIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAg" +
            "InRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQv" +
            "dGV4dHVyZS8yMjM1YzMzNDE1NWQyMTE4Zjk2YTgyZDA5N2UzN2I5MzFjM2MzYjFlZjEzZjkyZjE5Zjg1ZTEwN2RkYzRjMmU1IiwK" +
            "ICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=";
    private static final String NPC2_SKIN_SIGNATURE =
            "eoqLoNgsEbRxQ9o7s44aRL3tHFn6B47NmbFJCMncxROvPJPAvsFa7oSOthf83aKiR4V9RhvhDogRLQa4TDWPOyX16Txa3K9+27QRQ/" +
            "1PDmJhRhmkU5MfWmEKDo7+7vkvxn2068MRbKKtE4rOcTvrsUAG3zLoNy8PuaRLh0FR+lAAwJVBfzb17UHoINMIJ1shwSkyuvp9bH" +
            "5+eHQYvXttzVT1LirJZ4nLKCCl7Clh5RGAC21NcrpbZkZ5hOHfJtH8BwguLiB1KhxqapzgK8I/ffj+aLZvIuLztdz1aY2XK25Gdfh" +
            "M7iSjJ0isswzi8XRdujYKhvWlfX9Ki8GqOn274ifHtVczAChUPuQxFE7jJLitTDsrcVxdywWCP179hkO/mNO4VXgvp70vXzWcjPXi7" +
            "HL/fN51H3hvRG/pr6QNyL0NYt/obWp3cn8pRsbcikVbKkkHCGvsaz2DThdhDfXnh3E77bwpvZeSJaIHDSoP0N1BCoYmXYyxkEBiKcD3" +
            "jh3gm682EOohD9Wvxqgyol7TYjFxoa5CEI32UcNq+bqYhpV5xOKy6wHqJLpT+Im8ys/dUjeaGq39TVtfT7ExZlUT2W2rDx4oX5bc7X3" +
            "Pq0AULclSO90K3KqdhV7s3rmifjioRrf0crn+9ike+v/Z0P3gkKe3tHwyk8syd7CYwAiufBY=";
    private static final PlayerListEntry.ProfileProperty NPC2_SKIN_PROPERTY =
            new PlayerListEntry.ProfileProperty("textures", NPC2_SKIN_VALUE, NPC2_SKIN_SIGNATURE);
    private static final UUID NPC2_PROFILE_UUID = npc2ProfileUuid();
    private static final double NPC2_X = -1.5D;
    private static final double NPC2_Y = 87.0D;
    private static final double NPC2_Z = -11.5D;
    private static final float NPC2_YAW = 90.0F;
    private static final float NPC2_PITCH = 0.0F;

    private final Configuration configuration;
    /** Immutable schematic template; never mutated by a player action. */
    private final World templateWorld;
    private final CommandManager commandManager;
    private final AuthenticationProvider authenticationProvider;
    private final AuthenticationSessionHandler authenticationSessionHandler;
    private final MojangSkinProvider skinProvider;
    private final WorldTimeService worldTimeService;
    private final Map<SocketChannel, PlayerManager> playerManagers = new ConcurrentHashMap<>();
    private final List<AbstractProtocol> protocols = new ArrayList<>();
    private final Queue<Runnable> pendingTasks = new ConcurrentLinkedQueue<>();

    private Selector selector;
    private ServerSocketChannel serverSocket;
    private volatile Thread selectorThread;
    private volatile InetSocketAddress boundAddress;

    private long lastKeepAlive;
    private int keepAliveId;

    public NetworkManager(World world, Configuration configuration)
    {
        this(world, configuration, null, null);
    }

    public NetworkManager(World world, Configuration configuration,
                          AuthenticationProvider authenticationProvider,
                          AuthenticationSessionHandler authenticationSessionHandler)
    {
        this(world, configuration, authenticationProvider, authenticationSessionHandler,
                new WorldTimeService());
    }

    /** Test injection point for deterministic UTC+8 scheduling. */
    NetworkManager(World world, Configuration configuration,
                   AuthenticationProvider authenticationProvider,
                   AuthenticationSessionHandler authenticationSessionHandler,
                   WorldTimeService worldTimeService)
    {
        this.configuration = configuration;
        this.templateWorld = world;
        this.commandManager = new CommandManager();
        this.authenticationProvider = authenticationProvider;
        this.authenticationSessionHandler = authenticationSessionHandler;
        this.skinProvider = new MojangSkinProvider();
        if (worldTimeService == null)
            throw new IllegalArgumentException("WorldTimeService cannot be null");
        this.worldTimeService = worldTimeService;
        this.lastKeepAlive = System.currentTimeMillis();
        this.keepAliveId = 0;

        // Native multi-protocol backend (no ViaVersion). Each supported client
        // protocol has its own implementation; the handshake selects by number.
        this.protocols.add(new HandshakeProtocol(this));
        this.protocols.add(new Protocol47(this));
        this.protocols.add(new Protocol774(this));
        this.protocols.add(new Protocol775(this));
        this.protocols.add(new Protocol776(this));
        this.protocols.add(new Protocol777(this));

        // Precompute every supported protocol profile on the immutable template.
        // Player copies share those arrays until their first private block edit.
        if (this.templateWorld != null)
        {
            for (AbstractProtocol protocol : this.protocols)
            {
                if (protocol instanceof Protocol47 legacy)
                {
                    legacy.prepareLighting(this.templateWorld);
                }
                else if (protocol instanceof ModernProtocol modern)
                {
                    this.templateWorld.prepareModernLighting(modern.getLightingProperties(),
                            modern.getLegacyBlockMap(), modern.getModernBlockStateMap(),
                            modern.getMinY(), modern.getSectionCount(), modern.hasSkyLight());
                }
            }
        }
    }

    public void bind(String ip, int port) throws IOException
    {
        this.selector = Selector.open();
        this.serverSocket = ServerSocketChannel.open();
        this.serverSocket.configureBlocking(false);
        this.serverSocket.bind(new InetSocketAddress(ip, port));
        this.serverSocket.register(this.selector, SelectionKey.OP_ACCEPT);
        this.boundAddress = (InetSocketAddress) this.serverSocket.getLocalAddress();
    }

    public InetSocketAddress getBoundAddress()
    {
        return this.boundAddress;
    }

    public void select() throws IOException
    {
        this.selectorThread = Thread.currentThread();
        this.runPendingTasks();
        if (this.worldTimeService.shouldBroadcastNow())
            this.broadcastWorldTime(this.worldTimeService.currentTime());

        long time = System.currentTimeMillis();
        if (time >= this.lastKeepAlive + 10000L)
        {
            ++this.keepAliveId;
            this.lastKeepAlive = time;
            final long deadline = time - 30000L;
            List<PlayerManager> connections = new ArrayList<>(this.playerManagers.values());
            for (PlayerManager connection : connections)
            {
                if (connection.getStatus() == Status.PLAY)
                {
                    if (connection.getLastKeepAlive() < deadline)
                        this.disconnect(connection, "Timed out");
                    else
                        connection.getProtocol().sendKeepAlive(connection, this.keepAliveId);
                }
            }
        }

        long timeout = this.lastKeepAlive + 10000L - System.currentTimeMillis();
        if (timeout < 1L)
            timeout = 1L;
        timeout = Math.min(timeout, this.worldTimeService.millisUntilNextHour());
        int n = this.selector.select(timeout);

        if (n == 0)
            return;

        for (SelectionKey key : this.selector.selectedKeys())
        {
            if (!key.isValid())
                continue;
            try
            {
                if (key.isAcceptable())
                    this.accept();
                else
                {
                    if (key.isReadable())
                        this.readFromChannel((SocketChannel) key.channel());
                    if (key.isValid() && key.isWritable())
                        this.flushChannel((SocketChannel) key.channel(), key);
                }
            }
            catch (CancelledKeyException ignored)
            {
            }
        }
        this.selector.selectedKeys().clear();
    }

    private void accept() throws IOException
    {
        SocketChannel channel = this.serverSocket.accept();
        if (channel == null)
            return;
        channel.configureBlocking(false);
        PlayerManager playerManager = new PlayerManager(this, channel);
        this.playerManagers.put(channel, playerManager);
        channel.register(this.selector, SelectionKey.OP_READ, playerManager);
    }

    private void readFromChannel(SocketChannel socket)
    {
        PlayerManager playerManager = this.playerManagers.get(socket);
        if (playerManager == null)
            return;

        try
        {
            ByteBuffer readBuffer = ByteBuffer.allocate(8192);
            int read = socket.read(readBuffer);
            if (read == -1)
            {
                this.disconnect(playerManager, null);
                return;
            }
            if (read == 0)
                return;

            readBuffer.flip();
            byte[] chunk = new byte[read];
            readBuffer.get(chunk);
            playerManager.appendInbound(chunk);

            this.processFrames(playerManager, socket);
        }
        catch (IOException ex)
        {
            this.disconnect(playerManager, null);
        }
        catch (Exception ex)
        {
            Narcissus.LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            this.disconnect(playerManager, null);
        }
    }

    private void processFrames(PlayerManager playerManager, SocketChannel socket) throws IOException
    {
        while (true)
        {
            byte[] data = playerManager.getInboundData();
            int available = playerManager.getInboundLength();

            int[] header = PacketSerializer.tryReadVarInt(data, 0, available);
            if (header == null)
                return; // length prefix not fully arrived yet

            int frameLength = header[0];
            int headerBytes = header[1];
            if (frameLength < 0 || frameLength > MAX_FRAME_SIZE)
            {
                this.disconnect(playerManager, "Invalid packet, too big");
                return;
            }
            if (available - headerBytes < frameLength)
                return; // full frame not yet available

            byte[] frame = new byte[frameLength];
            System.arraycopy(data, headerBytes, frame, 0, frameLength);
            playerManager.consumeInbound(headerBytes + frameLength);

            if (frameLength == 0)
                continue;

            PacketSerializer serializer = new PacketSerializer(new ByteArrayInputStream(frame));
            int packetId = serializer.readVarInt();

            Class<? extends PacketIn> packetInClass =
                    playerManager.getProtocol().getPacketInById(packetId, playerManager.getStatus());
            if (packetInClass == null)
                continue; // unknown/ignored serverbound packet: drop the whole frame

            PacketIn packetIn;
            try
            {
                packetIn = packetInClass.getDeclaredConstructor().newInstance();
            }
            catch (ReflectiveOperationException ex)
            {
                Narcissus.LOGGER.log(Level.SEVERE, "Failed to instantiate " + packetInClass, ex);
                continue;
            }
            packetIn.readPacket(serializer);
            packetIn.handlePacket(this, playerManager);

            if (!socket.isOpen())
                return;
        }
    }

    /**
     * Serialises a clientbound packet and enqueues it on the player's write queue.
     * Actual socket writes always happen on the selector thread.
     */
    public void sendPacket(PlayerManager playerManager, PacketOut packetOut)
    {
        try
        {
            int id = playerManager.getProtocol().getPacketOutByClass(packetOut.getClass(), playerManager.getStatus());
            if (id == -1)
            {
                Narcissus.LOGGER.warning("Sending unknown packet " + packetOut.getClass().getSimpleName());
                return;
            }
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            PacketSerializer bodySerializer = new PacketSerializer(body);
            bodySerializer.writeVarInt(id);
            packetOut.write(bodySerializer);
            byte[] payload = body.toByteArray();

            ByteArrayOutputStream framed = new ByteArrayOutputStream();
            PacketSerializer frameSerializer = new PacketSerializer(framed);
            frameSerializer.writeByteArray(payload);
            byte[] frame = framed.toByteArray();

            playerManager.queueOutbound(ByteBuffer.wrap(frame));
            this.markWritable(playerManager);
        }
        catch (Exception ex)
        {
            Narcissus.LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

    private void markWritable(PlayerManager playerManager)
    {
        SocketChannel socket = playerManager.getSocket();
        Selector sel = this.selector;
        if (sel == null)
            return;

        // Packet handlers and the large initial chunk loop already run on the
        // selector thread. Avoid creating one pending task and one wakeup per
        // packet; the OP_WRITE interest will be handled by the next select pass.
        if (Thread.currentThread() == this.selectorThread)
        {
            SelectionKey key = socket.keyFor(sel);
            if (key != null && key.isValid())
                key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
            return;
        }

        this.execute(() ->
        {
            SelectionKey key = socket.keyFor(sel);
            if (key != null && key.isValid())
            {
                this.flushChannel(socket, key);
            }
        });
    }

    private void flushChannel(SocketChannel socket, SelectionKey key)
    {
        PlayerManager playerManager = this.playerManagers.get(socket);
        if (playerManager == null)
            return;
        try
        {
            Queue<ByteBuffer> queue = playerManager.getOutboundQueue();
            ByteBuffer buffer;
            while ((buffer = queue.peek()) != null)
            {
                socket.write(buffer);
                if (buffer.hasRemaining())
                {
                    // socket buffer full; keep OP_WRITE and resume later
                    if (key.isValid())
                        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                    return;
                }
                queue.poll();
            }
            // queue drained; stop listening for writability
            if (key.isValid())
                key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
        }
        catch (IOException ex)
        {
            this.disconnect(playerManager, null);
        }
    }

    /** Posts a task to run on the selector thread and wakes the selector. */
    public void execute(Runnable task)
    {
        this.pendingTasks.add(task);
        Selector sel = this.selector;
        if (sel != null)
            sel.wakeup();
    }

    private void runPendingTasks()
    {
        Runnable task;
        while ((task = this.pendingTasks.poll()) != null)
        {
            try
            {
                task.run();
            }
            catch (Exception ex)
            {
                Narcissus.LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            }
        }
    }

    public void stop()
    {
        Selector sel = this.selector;
        if (sel == null)
            return;

        this.execute(() ->
        {
            for (PlayerManager connection : new ArrayList<>(this.playerManagers.values()))
                this.disconnect(connection, "Server closed");
            try
            {
                if (this.serverSocket != null)
                    this.serverSocket.close();
            }
            catch (IOException ex)
            {
                Narcissus.LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            }
            try
            {
                sel.close();
            }
            catch (IOException ignored)
            {
            }
        });
    }

    public AbstractProtocol getProtocolByVersionId(int version)
    {
        for (AbstractProtocol protocol : this.protocols)
            for (int id : protocol.getVersions())
                if (id == version)
                    return protocol;
        return null;
    }

    /** The legacy v47 protocol, used for old clients and as a status fallback. */
    public AbstractProtocol getBackendProtocol()
    {
        return this.getProtocolByVersionId(47);
    }

    /**
     * A protocol able to answer STATUS pings from clients whose version we do not
     * natively support. Status framing (request/ping) is identical across the
     * versions we register, so the v47 implementation is a safe responder.
     */
    public AbstractProtocol getStatusProtocol()
    {
        return this.getProtocolByVersionId(47);
    }

    public int getCurrentKeepAliveId()
    {
        return this.keepAliveId;
    }

    public void disconnect(PlayerManager playerManager, String message)
    {
        boolean wasPlaying = playerManager.getStatus() == Status.PLAY;
        if (this.playerManagers.remove(playerManager.getSocket()) == null)
            return;

        if (wasPlaying)
            this.playerLeft(playerManager);

        if (playerManager.getStatus() != Status.STATUS && playerManager.getStatus() != Status.HANDSHAKE)
            Narcissus.LOGGER.info(playerManager.getInetAddress().getHostString()
                    + (playerManager.getUserName().isEmpty() ? "" : " (" + playerManager.getUserName() + ")")
                    + " disconnected" + (message == null ? "" : " : " + message));

        if (message != null)
        {
            try
            {
                playerManager.getProtocol().disconnect(playerManager, message);
                // best-effort synchronous flush of the disconnect frame
                SelectionKey key = playerManager.getSocket().keyFor(this.selector);
                if (key != null && key.isValid())
                    this.flushChannel(playerManager.getSocket(), key);
            }
            catch (Exception ignored)
            {
            }
        }

        try
        {
            SelectionKey key = playerManager.getSocket().keyFor(this.selector);
            if (key != null)
                key.cancel();
            playerManager.getSocket().close();
        }
        catch (IOException ex)
        {
            Narcissus.LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

    /** Returns the immutable schematic template used to create player worlds. */
    public World getTemplateWorld()
    {
        return this.templateWorld;
    }

    /** Creates a fresh mutable world instance for one player connection. */
    public World createPlayerWorld()
    {
        return this.templateWorld == null ? null : this.templateWorld.copy();
    }

    /**
     * @deprecated Protocol handlers must use {@link PlayerManager#getWorld()}.
     * This compatibility accessor returns only the immutable template.
     */
    @Deprecated
    public World getWorld()
    {
        return this.templateWorld;
    }

    public Configuration getLimboConfiguration()
    {
        return this.configuration;
    }

    public CommandManager getCommandManager()
    {
        return this.commandManager;
    }

    public WorldTimeService getWorldTimeService()
    {
        return this.worldTimeService;
    }

    public boolean isStaffAccount(PlayerManager playerManager)
    {
        return playerManager != null
                && playerManager.isAuthenticated()
                && this.authenticationProvider != null
                && this.authenticationProvider.isStaffAccount(playerManager.getUserName());
    }

    /** Handles the side-effecting Staff-only personal world-time command. */
    public boolean handleTimeCommand(PlayerManager playerManager, String input)
    {
        String value = input == null ? "" : input.trim();
        if (!value.startsWith("/"))
            return false;

        String commandLine = value.substring(1).trim();
        if (commandLine.isEmpty())
            return false;
        String[] parts = commandLine.split("\\s+");
        if (!parts[0].equalsIgnoreCase("time"))
            return false;

        if (!this.isStaffAccount(playerManager))
        {
            this.sendMessage(playerManager, "&c只有已认证的 Staff 账号可以使用 /time。");
            return true;
        }
        if (parts.length != 2)
        {
            this.sendMessage(playerManager, TIME_COMMAND_USAGE);
            return true;
        }

        WorldTimeService timeService = this.getWorldTimeService();
        boolean sync = parts[1].equalsIgnoreCase("sync");
        WorldTimeService.Snapshot snapshot;
        if (sync)
        {
            snapshot = timeService.currentTime();
        }
        else
        {
            OptionalLong parsed = timeService.parseDayTime(parts[1]);
            if (parsed.isEmpty())
            {
                this.sendMessage(playerManager, TIME_COMMAND_USAGE);
                return true;
            }
            snapshot = timeService.atDayTime(parsed.getAsLong());
        }

        this.sendWorldTime(playerManager, snapshot);
        String displayTime = WorldTimeService.formatDayTime(snapshot.dayTime());
        if (sync)
            this.sendMessage(playerManager, "&a已恢复当前 UTC+8 世界时间：&e" + displayTime
                    + " &7(" + snapshot.dayTime() + " ticks)&a。");
        else
            this.sendMessage(playerManager, "&a已将你的世界时间设置为 &e" + displayTime
                    + " &7(" + snapshot.dayTime() + " ticks)&a。");
        this.sendMessage(playerManager, "&7下一个 UTC+8 整点将恢复全局同步。");
        return true;
    }

    /** Sends one frozen world-time state to a single PLAY connection. */
    public void sendWorldTime(PlayerManager playerManager, WorldTimeService.Snapshot snapshot)
    {
        if (playerManager == null || snapshot == null || playerManager.getStatus() != Status.PLAY
                || !playerManager.getSocket().isOpen())
            return;

        if (playerManager.getProtocol() instanceof ModernProtocol)
        {
            ModernProtocol modern = (ModernProtocol) playerManager.getProtocol();
            this.sendPacket(playerManager, modern.createSetTime(
                    snapshot.gameTime(), snapshot.worldClockTime(), snapshot.dayTime()));
        }
        else
        {
            this.sendPacket(playerManager,
                    new PacketPlayOutTimeUpdate47(snapshot.gameTime(), snapshot.dayTime()));
        }
        this.updatePlayerNpc(playerManager, NpcSchedule.stateAt(snapshot.utc8DateTime().toLocalTime()));
    }

    private void broadcastWorldTime(WorldTimeService.Snapshot snapshot)
    {
        for (PlayerManager playerManager : new ArrayList<>(this.playerManagers.values()))
            this.sendWorldTime(playerManager, snapshot);
    }

    public long getConnectedPlayers()
    {
        return this.playerManagers.values().stream()
                .filter(connection -> connection.getStatus() == Status.LOGIN || connection.getStatus() == Status.PLAY)
                .count();
    }

    /** Sends the common two-line welcome message to every native protocol. */
    public void sendWelcomeMessages(PlayerManager playerManager)
    {
        this.sendMessage(playerManager, WELCOME_MESSAGE_ONE);
        this.sendMessage(playerManager, WELCOME_MESSAGE_TWO);
    }

    /** Publishes only the current PLAY player's own entry to that player's Tab list. */
    public void playerJoined(PlayerManager playerManager)
    {
        if (playerManager.getStatus() != Status.PLAY)
            return;

        this.sendWorldTime(playerManager, this.worldTimeService.currentTime());
        this.sendTabHeaderFooter(playerManager);
        this.sendSkinParts(playerManager);
        PlayerListEntry.ProfileProperty forwardedSkin = playerManager.getSkinProperty();
        if (forwardedSkin != null) {
            this.publishPlayerInfo(playerManager, forwardedSkin, false);
        } else {
            this.skinProvider.lookup(playerManager.getUserName(), playerManager.getUuid(),
                            playerManager.isUuidProvided())
                    .whenComplete((property, error) -> this.execute(() -> {
                        if (playerManager.getSkinProperty() != null) {
                            this.publishPlayerInfo(playerManager, playerManager.getSkinProperty(), false);
                            return;
                        }
                        this.publishPlayerInfo(playerManager,
                                property != null && property.isPresent() ? property.get() : null,
                                false);
                    }));
        }

        this.sendAuthenticationPrompt(playerManager);
        this.sendPlayerNpc(playerManager);
    }

    /** Handles profile properties forwarded by the BungeeCord plugin channel. */
    public void handlePluginMessage(PlayerManager playerManager, String channel, byte[] data)
    {
        if (!SkinPropertyPayload.isSkinChannel(channel))
            return;

        PlayerListEntry.ProfileProperty property;
        try
        {
            property = SkinPropertyPayload.decode(data);
        }
        catch (IOException ex)
        {
            Narcissus.LOGGER.log(Level.FINE, "Invalid forwarded skin payload", ex);
            return;
        }
        if (property == null)
            return;

        playerManager.setSkinProperty(property);
        this.execute(() -> this.publishPlayerInfo(playerManager, property, true));
    }

    private void publishPlayerInfo(PlayerManager playerManager,
                                   PlayerListEntry.ProfileProperty property,
                                   boolean replaceExisting)
    {
        if (playerManager.getStatus() != Status.PLAY || !playerManager.getSocket().isOpen())
            return;

        PlayerListEntry.ProfileProperty effective = playerManager.getSkinProperty();
        if (effective == null && property != null)
        {
            playerManager.setSkinProperty(property);
            effective = property;
        }

        if (replaceExisting)
        {
            UUID uuid = playerManager.getUuid();
            if (playerManager.getProtocol() instanceof ModernProtocol)
            {
                ModernProtocol modern = (ModernProtocol) playerManager.getProtocol();
                this.sendPacket(playerManager, modern.createPlayerInfoRemove(Collections.singletonList(uuid)));
            }
            else
            {
                this.sendPacket(playerManager, PacketPlayOutPlayerInfo47.remove(Collections.singletonList(uuid)));
            }
        }

        List<PlayerListEntry.ProfileProperty> properties = effective == null
                ? Collections.emptyList()
                : Collections.singletonList(effective);
        this.sendPlayerInfoUpdate(playerManager,
                Collections.singletonList(this.toEntry(playerManager, properties)));
        if (replaceExisting)
            this.sendSkinParts(playerManager);
    }

    /** Updates and, once in PLAY, publishes the client's enabled skin layers. */
    public void updateSkinParts(PlayerManager playerManager, int skinParts)
    {
        playerManager.setSkinParts(skinParts);
        this.sendSkinParts(playerManager);
    }

    private void sendSkinParts(PlayerManager playerManager)
    {
        if (playerManager.getStatus() != Status.PLAY || !playerManager.getSocket().isOpen())
            return;

        // Join Game uses entity id 1 for the local player in both supported lines.
        if (playerManager.getProtocol() instanceof ModernProtocol)
        {
            ModernProtocol modern = (ModernProtocol) playerManager.getProtocol();
            this.sendPacket(playerManager, modern.createEntityMetadata(1, playerManager.getSkinParts()));
        }
        else
        {
            this.sendPacket(playerManager,
                    new PacketPlayOutEntityMetadata47(1, playerManager.getSkinParts()));
        }
    }

    /** Sends NPC1, plus NPC2 when NPC1 is sleeping, at the state selected by the current UTC+8 schedule. */
    private void sendPlayerNpc(PlayerManager playerManager)
    {
        if (playerManager.getStatus() != Status.PLAY || !playerManager.getSocket().isOpen())
            return;

        NpcSchedule.State state = NpcSchedule.stateAt(
                this.worldTimeService.currentTime().utc8DateTime().toLocalTime());
        boolean npc2Visible = state.isSleeping();
        if (playerManager.getProtocol() instanceof ModernProtocol)
        {
            ModernProtocol modern = (ModernProtocol) playerManager.getProtocol();
            this.sendPacket(playerManager, modern.createHiddenNameTagTeam(NPC_TEAM_NAME, NPC_NAME));
            // Keep the skin profile client-side but mark it unlisted so it never appears in TAB.
            this.sendPlayerInfoUpdate(playerManager, Collections.singletonList(this.createNpcEntry()));
            this.spawnModernNpc(playerManager, modern, state);
            if (npc2Visible)
                this.spawnModernNpc2(playerManager, modern);
        }
        else
        {
            this.sendPacket(playerManager, new PacketPlayOutTeams47(NPC_TEAM_NAME, NPC_NAME));
            this.spawnLegacyNpc(playerManager, state);
            if (npc2Visible)
                this.spawnLegacyNpc2(playerManager);
        }
        playerManager.setNpcState(state);
        playerManager.setNpc2Visible(npc2Visible);
    }

    /** Applies only actual schedule transitions; repeated time broadcasts are packet-free for NPC1. */
    private void updatePlayerNpc(PlayerManager playerManager, NpcSchedule.State nextState)
    {
        NpcSchedule.State currentState = playerManager.getNpcState();
        if (currentState == null || currentState == nextState)
            return;

        if (playerManager.getProtocol() instanceof ModernProtocol)
        {
            ModernProtocol modern = (ModernProtocol) playerManager.getProtocol();
            if (currentState == NpcSchedule.State.SITTING)
            {
                this.sendPacket(playerManager, modern.createSetPassengers(NPC_SEAT_ENTITY_ID));
                this.sendPacket(playerManager, modern.createRemoveEntities(NPC_SEAT_ENTITY_ID));
            }

            this.sendPacket(playerManager, modern.createTeleportEntity(
                    NPC_ENTITY_ID, nextState.x(), nextState.y(), nextState.z(),
                    nextState.yaw(), nextState.pitch()));
            this.sendPacket(playerManager, modern.createNpcMetadata(
                    NPC_ENTITY_ID, 0x7F, nextState.modernPoseId(),
                    nextState.isSleeping(),
                    nextState.sleepingBlockX(), nextState.sleepingBlockY(), nextState.sleepingBlockZ()));

            if (nextState.requiresSeat())
                this.spawnModernSeat(playerManager, modern, nextState);
        }
        else
        {
            this.sendPacket(playerManager,
                    new PacketPlayOutDestroyEntities47(NPC_ENTITY_ID, NPC_SEAT_ENTITY_ID));
            this.spawnLegacyNpc(playerManager, nextState);
        }
        playerManager.setNpcState(nextState);
        this.updateNpc2Visibility(playerManager, nextState.isSleeping());
    }

    /** Shows or hides NPC2 as NPC1 enters or leaves the bed; NPC2 never moves while visible. */
    private void updateNpc2Visibility(PlayerManager playerManager, boolean visible)
    {
        if (playerManager.isNpc2Visible() == visible)
            return;

        if (playerManager.getProtocol() instanceof ModernProtocol)
        {
            ModernProtocol modern = (ModernProtocol) playerManager.getProtocol();
            if (visible)
            {
                this.spawnModernNpc2(playerManager, modern);
            }
            else
            {
                this.sendPacket(playerManager,
                        modern.createRemoveEntities(NPC2_ENTITY_ID, NPC2_SEAT_ENTITY_ID));
                this.sendPacket(playerManager, modern.createPlayerInfoRemove(
                        Collections.singletonList(NPC2_PROFILE_UUID)));
            }
        }
        else
        {
            if (visible)
            {
                this.spawnLegacyNpc2(playerManager);
            }
            else
            {
                this.sendPacket(playerManager, new PacketPlayOutDestroyEntities47(
                        NPC2_ENTITY_ID, NPC2_SEAT_ENTITY_ID));
                this.sendPacket(playerManager, PacketPlayOutPlayerInfo47.remove(
                        Collections.singletonList(NPC2_PROFILE_UUID)));
            }
        }
        playerManager.setNpc2Visible(visible);
    }

    private void spawnModernNpc2(PlayerManager playerManager, ModernProtocol modern)
    {
        this.sendPacket(playerManager, modern.createHiddenNameTagTeam(NPC2_TEAM_NAME, NPC2_NAME));
        // Keep the skin profile client-side but mark it unlisted so it never appears in TAB.
        this.sendPlayerInfoUpdate(playerManager, Collections.singletonList(this.createNpc2Entry()));
        this.sendPacket(playerManager, modern.createPlayerNpc(
                NPC2_ENTITY_ID, NPC2_PROFILE_UUID,
                NPC2_X, NPC2_Y, NPC2_Z, NPC2_YAW, NPC2_PITCH));
        this.sendPacket(playerManager, modern.createNpcMetadata(
                NPC2_ENTITY_ID, 0x7F, 10, false, 0, 0, 0));
        this.sendPacket(playerManager, modern.createNpcSeat(
                NPC2_SEAT_ENTITY_ID, NPC2_X, NPC2_Y, NPC2_Z, NPC2_YAW, NPC2_PITCH));
        this.sendPacket(playerManager, modern.createNpcSeatMetadata(NPC2_SEAT_ENTITY_ID));
        this.sendPacket(playerManager,
                modern.createSetPassengers(NPC2_SEAT_ENTITY_ID, NPC2_ENTITY_ID));
    }

    private void spawnLegacyNpc2(PlayerManager playerManager)
    {
        this.sendPacket(playerManager, new PacketPlayOutTeams47(NPC2_TEAM_NAME, NPC2_NAME));
        this.sendPlayerInfoUpdate(playerManager,
                Collections.singletonList(this.createNpc2Entry()));
        this.sendPacket(playerManager, new PacketPlayOutSpawnMob47(
                NPC2_SEAT_ENTITY_ID, NPC2_X, NPC2_Y, NPC2_Z, NPC2_YAW, NPC2_PITCH));
        this.sendPacket(playerManager, new PacketPlayOutSpawnPlayer47(
                NPC2_ENTITY_ID, NPC2_PROFILE_UUID,
                NPC2_X, NPC2_Y, NPC2_Z, NPC2_YAW, NPC2_PITCH));
        this.sendPacket(playerManager, new PacketPlayOutEntityMetadata47(NPC2_ENTITY_ID, 0x7F));
        this.sendPacket(playerManager, new PacketPlayOutAttachEntity47(
                NPC2_ENTITY_ID, NPC2_SEAT_ENTITY_ID));
        // 1.8 has no listed flag; remove the profile after spawn while retaining the skin.
        this.sendPacket(playerManager,
                PacketPlayOutPlayerInfo47.remove(Collections.singletonList(NPC2_PROFILE_UUID)));
    }

    private PlayerListEntry createNpcEntry()
    {
        return new PlayerListEntry(NPC_PROFILE_UUID, NPC_NAME, 0,
                Collections.singletonList(NPC_SKIN_PROPERTY), false);
    }

    private PlayerListEntry createNpc2Entry()
    {
        return new PlayerListEntry(NPC2_PROFILE_UUID, NPC2_NAME, 0,
                Collections.singletonList(NPC2_SKIN_PROPERTY), false);
    }

    private static UUID npc2ProfileUuid()
    {
        return MojangSkinProvider.parseTexturesProfileUuid(NPC2_SKIN_PROPERTY)
                .orElseThrow(() -> new IllegalStateException(
                        "NPC2 skin must contain a 32-digit profileId"));
    }

    private void spawnModernNpc(PlayerManager playerManager, ModernProtocol modern,
                                NpcSchedule.State state)
    {
        if (state.requiresSeat())
            this.spawnModernSeatEntity(playerManager, modern, state);

        this.sendPacket(playerManager, modern.createPlayerNpc(
                NPC_ENTITY_ID, NPC_PROFILE_UUID, state.x(), state.y(), state.z(),
                state.yaw(), state.pitch()));
        this.sendPacket(playerManager, modern.createNpcMetadata(
                NPC_ENTITY_ID, 0x7F, state.modernPoseId(),
                state.isSleeping(),
                state.sleepingBlockX(), state.sleepingBlockY(), state.sleepingBlockZ()));

        if (state.requiresSeat())
            this.sendPacket(playerManager,
                    modern.createSetPassengers(NPC_SEAT_ENTITY_ID, NPC_ENTITY_ID));
    }

    private void spawnModernSeat(PlayerManager playerManager, ModernProtocol modern,
                                 NpcSchedule.State state)
    {
        this.spawnModernSeatEntity(playerManager, modern, state);
        this.sendPacket(playerManager,
                modern.createSetPassengers(NPC_SEAT_ENTITY_ID, NPC_ENTITY_ID));
    }

    private void spawnModernSeatEntity(PlayerManager playerManager, ModernProtocol modern,
                                       NpcSchedule.State state)
    {
        this.sendPacket(playerManager, modern.createNpcSeat(
                NPC_SEAT_ENTITY_ID, state.x(), state.y(), state.z(),
                state.yaw(), state.pitch()));
        this.sendPacket(playerManager, modern.createNpcSeatMetadata(NPC_SEAT_ENTITY_ID));
    }

    private void spawnLegacyNpc(PlayerManager playerManager, NpcSchedule.State state)
    {
        this.sendPlayerInfoUpdate(playerManager,
                Collections.singletonList(this.createNpcEntry()));
        if (state.requiresSeat())
        {
            this.sendPacket(playerManager, new PacketPlayOutSpawnMob47(
                    NPC_SEAT_ENTITY_ID, state.x(), state.y(), state.z(),
                    state.yaw(), state.pitch()));
        }

        this.sendPacket(playerManager, new PacketPlayOutSpawnPlayer47(
                NPC_ENTITY_ID, NPC_PROFILE_UUID, state.x(), state.y(), state.z(),
                state.yaw(), state.pitch()));
        this.sendPacket(playerManager, new PacketPlayOutEntityMetadata47(NPC_ENTITY_ID, 0x7F));
        if (state.isSleeping())
        {
            this.sendPacket(playerManager, new PacketPlayOutUseBed47(
                    NPC_ENTITY_ID, state.sleepingBlockX(),
                    state.sleepingBlockY(), state.sleepingBlockZ()));
        }
        else if (state.requiresSeat())
        {
            this.sendPacket(playerManager, new PacketPlayOutAttachEntity47(
                    NPC_ENTITY_ID, NPC_SEAT_ENTITY_ID));
        }

        // 1.8 has no listed flag; remove the profile after spawn while retaining the skin.
        this.sendPacket(playerManager,
                PacketPlayOutPlayerInfo47.remove(Collections.singletonList(NPC_PROFILE_UUID)));
    }

    /** No-op: other clients never receive this player's private Tab entry. */
    public void playerLeft(PlayerManager playerManager)
    {
        // Each connection owns a private Tab list, so there is no viewer to update.
    }

    private PlayerListEntry toEntry(PlayerManager player,
                                    List<PlayerListEntry.ProfileProperty> properties)
    {
        return new PlayerListEntry(player.getUuid(), player.getUserName(),
                this.configuration.getGameMode() & 0xFF, properties);
    }

    private void sendTabHeaderFooter(PlayerManager playerManager)
    {
        String header = TextUtil.replaceChar(TAB_HEADER);
        String footer = TextUtil.replaceChar(TAB_FOOTER);
        if (playerManager.getProtocol() instanceof ModernProtocol)
        {
            ModernProtocol modern = (ModernProtocol) playerManager.getProtocol();
            this.sendPacket(playerManager, modern.createTabList(header, footer));
        }
        else
        {
            this.sendPacket(playerManager, new PacketPlayOutPlayerListHeaderFooter47(header, footer));
        }
    }

    private void sendPlayerInfoUpdate(PlayerManager viewer, List<PlayerListEntry> entries)
    {
        if (entries.isEmpty())
            return;
        if (viewer.getProtocol() instanceof ModernProtocol)
        {
            ModernProtocol modern = (ModernProtocol) viewer.getProtocol();
            this.sendPacket(viewer, modern.createPlayerInfoUpdate(entries));
        }
        else
        {
            this.sendPacket(viewer, new PacketPlayOutPlayerInfo47(entries));
        }
    }

    /**
     * Intercepts authentication commands and blocks all other player commands
     * until the current Bungee session has authenticated.
     */
    public boolean handleAuthenticationCommand(PlayerManager playerManager, String input)
    {
        if (this.authenticationProvider == null)
            return false;

        String value = input == null ? "" : input.trim();
        if (!value.startsWith("/"))
        {
            if (!playerManager.isAuthenticated())
            {
                this.sendMessage(playerManager, "&e请先完成认证：/register <密码> 或 /login <密码>");
                return true;
            }
            return false;
        }

        String line = value.substring(1).trim();
        if (line.isEmpty())
            return this.rejectUnauthenticated(playerManager);

        String[] parts = line.split("\\s+");
        String command = parts[0].toLowerCase(java.util.Locale.ROOT);
        boolean authCommand = command.equals("register")
                || command.equals("login")
                || command.equals("2fa");
        if (!authCommand)
            return this.rejectUnauthenticated(playerManager);

        if (playerManager.isAuthenticated())
        {
            this.sendMessage(playerManager, "&e你已经登录，不能重复进入登录状态。");
            return true;
        }

        String[] args = java.util.Arrays.copyOfRange(parts, 1, parts.length);
        if (command.equals("register"))
        {
            if (args.length != 1)
            {
                this.sendMessage(playerManager, "&e用法：/register <密码>");
                return true;
            }
            AuthenticationResult result = this.authenticationProvider.register(
                    playerManager.getUserName(), args[0]);
            switch (result.getStatus())
            {
                case REGISTERED:
                    this.sendMessage(playerManager, "&a注册成功，请使用 &e/login <密码> &a登录。");
                    break;
                case STAFF_REGISTERED:
                    this.sendMessage(playerManager,
                            "&aStaff 账号注册成功，请使用 &e/2fa add &a配置 Microsoft Authenticator。");
                    break;
                case ALREADY_REGISTERED:
                    this.sendMessage(playerManager, "&e账号已经注册，请使用 &e/login <密码> &e登录。");
                    break;
                case STAFF_CODE_REQUIRED:
                    this.sendMessage(playerManager, "&cStaff 账号不能使用普通密码注册，请使用控制台注册码。");
                    break;
                case INVALID_STAFF_CODE:
                    this.sendMessage(playerManager, "&c注册码错误或已过期，请联系管理员重新获取注册码。");
                    break;
                case STORAGE_ERROR:
                    this.sendMessage(playerManager, "&c账号保存失败，请联系管理员检查 accounts.yml。");
                    break;
                default:
                    this.sendMessage(playerManager, "&c注册失败。");
                    break;
            }
            return true;
        }

        if (command.equals("login"))
        {
            if (args.length != 1)
            {
                this.sendMessage(playerManager, "&e用法：/login <密码或2FACode>");
                return true;
            }
            AuthenticationResult result = this.authenticationProvider.login(
                    playerManager.getUserName(), args[0]);
            switch (result.getStatus())
            {
                case LOGIN_SUCCESS:
                    this.completeAuthentication(playerManager);
                    break;
                case NOT_REGISTERED:
                    this.sendMessage(playerManager, "&e账号尚未注册，请使用 &e/register <密码> &e注册。");
                    break;
                case INVALID_PASSWORD:
                    this.sendMessage(playerManager, "&c密码错误。");
                    break;
                case TWO_FACTOR_NOT_CONFIGURED:
                    this.sendMessage(playerManager, "&c请先配置 2FA：/2fa add");
                    break;
                case INVALID_TWO_FACTOR:
                    this.sendMessage(playerManager, "&c2FA 验证码错误或已过期。");
                    break;
                case STORAGE_ERROR:
                    this.sendMessage(playerManager, "&c账号读取失败，请联系管理员检查 accounts.yml。");
                    break;
                default:
                    this.sendMessage(playerManager, "&c登录失败。");
                    break;
            }
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("add"))
        {
            AuthenticationResult result = this.authenticationProvider.beginTwoFactorSetup(
                    playerManager.getUserName());
            switch (result.getStatus())
            {
                case TWO_FACTOR_SETUP_READY:
                    this.sendMessage(playerManager, "&a请将以下密钥添加到 Microsoft Authenticator：");
                    this.sendCopyableMessage(playerManager, "&f密钥：&e", result.getValue());
                    this.sendMessage(playerManager, "&a添加完成后执行：&e/2fa confirm <验证码>");
                    break;
                case NOT_STAFF_ACCOUNT:
                    this.sendMessage(playerManager, "&c只有 Staff 账号需要配置 2FA。");
                    break;
                case NOT_REGISTERED:
                    this.sendMessage(playerManager, "&c请先完成 Staff 注册。");
                    break;
                case STORAGE_ERROR:
                    this.sendMessage(playerManager, "&c2FA 保存失败，请联系管理员检查 accounts.yml。");
                    break;
                default:
                    this.sendMessage(playerManager, "&c无法开始 2FA 配置。");
                    break;
            }
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("confirm"))
        {
            AuthenticationResult result = this.authenticationProvider.confirmTwoFactor(
                    playerManager.getUserName(), args[1]);
            switch (result.getStatus())
            {
                case TWO_FACTOR_ENABLED:
                    this.completeAuthentication(playerManager);
                    break;
                case INVALID_TWO_FACTOR:
                    this.sendMessage(playerManager, "&c2FA 验证码错误或已过期。");
                    break;
                case NOT_STAFF_ACCOUNT:
                    this.sendMessage(playerManager, "&c只有 Staff 账号需要配置 2FA。");
                    break;
                case TWO_FACTOR_NOT_CONFIGURED:
                    this.sendMessage(playerManager, "&c请先执行 /2fa add 获取密钥。");
                    break;
                default:
                    this.sendMessage(playerManager, "&c2FA 配置失败。");
                    break;
            }
            return true;
        }

        this.sendMessage(playerManager, "&e用法：/2fa add 或 /2fa confirm <验证码>");
        return true;
    }

    private boolean rejectUnauthenticated(PlayerManager playerManager)
    {
        if (playerManager.isAuthenticated())
            return false;
        this.sendMessage(playerManager, "&c请先完成认证，再使用其他命令。");
        return true;
    }

    private void sendAuthenticationPrompt(PlayerManager playerManager)
    {
        if (this.authenticationProvider == null)
            return;

        if (this.authenticationSessionHandler != null
                && this.authenticationSessionHandler.isAuthenticated(playerManager.getUserName()))
        {
            playerManager.setAuthenticated(true);
            return;
        }

        String username = playerManager.getUserName();
        boolean staff = this.authenticationProvider.isStaffAccount(username);
        if (!this.authenticationProvider.isRegistered(username))
        {
            if (staff)
                this.sendMessage(playerManager, "&e这是 Staff 账号，请向管理员获取注册码后使用 /register <注册码>。");
            else
                this.sendMessage(playerManager, "&e请先注册账号：&b/register <密码>");
            return;
        }

        if (staff)
        {
            if (this.authenticationProvider.isTwoFactorConfigured(username))
                this.sendMessage(playerManager, "&e请使用 &b/login <2FACode> &e登录。");
            else
                this.sendMessage(playerManager, "&e该 Staff 账号尚未配置 2FA，请使用 &b/2fa add&e。");
        }
        else
        {
            this.sendMessage(playerManager, "&e请使用 &b/login <密码> &e登录。");
        }
    }

    private void completeAuthentication(PlayerManager playerManager)
    {
        playerManager.setAuthenticated(true);
        this.sendMessage(playerManager, "&a登录成功，正在传送到目标服务器。");
        if (this.authenticationSessionHandler != null)
            this.authenticationSessionHandler.onAuthenticated(playerManager.getUserName());
    }

    /** Sends a value with a click-only suffix, leaving the value itself plain. */
    public void sendCopyableMessage(PlayerManager playerManager, String label, String value)
    {
        String formattedLabel = TextUtil.replaceChar(label);
        String display = formattedLabel + (value == null ? "" : value);
        String button = " §b[点我复制]";
        if (playerManager.getProtocol() instanceof ModernProtocol)
        {
            sendPacket(playerManager,
                    PacketPlayOutSystemChat774.clickableSuffix(
                            display, button, "copy_to_clipboard", value, false));
        }
        else
        {
            // 1.8 does not implement copy_to_clipboard; only the button gets the
            // suggest_command fallback so the secret text itself remains plain.
            String json = TextUtil.toJsonClickableSuffixComponent(
                    display, button, "suggest_command", value);
            sendPacket(playerManager, new PacketPlayOutChat47(json, true));
        }
    }

    // Shared player-message convenience for legacy and modern protocols.
    public void sendMessage(PlayerManager playerManager, String message)
    {
        String formatted = TextUtil.replaceChar(message);
        if (playerManager.getProtocol() instanceof ModernProtocol)
            sendPacket(playerManager, new PacketPlayOutSystemChat774(formatted, false));
        else
            sendPacket(playerManager, new PacketPlayOutChat47(formatted));
    }
}
