package cn.starry.narcissus;

import cn.starry.narcissus.functions.auth.AuthenticationProvider;
import cn.starry.narcissus.functions.auth.AuthenticationSessionHandler;
import cn.starry.narcissus.functions.config.Configuration;
import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.util.ConsoleUtil;
import cn.starry.narcissus.util.encryption.MessageDigestUtils;
import cn.starry.narcissus.functions.world.Schematic;
import cn.starry.narcissus.functions.world.World;
import cn.starry.narcissus.functions.world.nbt.CompoundTag;
import cn.starry.narcissus.functions.world.nbt.NBTTag;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Objects;
import java.util.logging.*;
import java.util.zip.GZIPInputStream;

/**
 * Created by Rigner on 30/08/16, Update by Stalyer for project Limbo.
 * All rights reserved.
 *
 * Reworked into an embeddable, restartable Limbo service. The BungeeCord plugin
 * layer constructs an instance with a {@link Configuration} and a data directory,
 * calls {@link #start()} (which blocks until the listener is bound) and later
 * {@link #stop()} / {@link #close()}. There is no static {@code main}, no JVM
 * shutdown hook and no static global network manager.
 */
public class Narcissus implements AutoCloseable
{
    public static final Logger LOGGER = Logger.getLogger("Narcissus");
    public static final String VERSION = ("260805");

    /** Fixed center point for the schematic-built limbo structure. */
    private static final double SCHEMATIC_ANCHOR_X = 0.0D;
    private static final double SCHEMATIC_ANCHOR_Y = 90.0D;
    private static final double SCHEMATIC_ANCHOR_Z = 0.0D;


    private static boolean loggerConfigured = false;

    private final Configuration configuration;
    private final Path dataDirectory;
    private final AuthenticationProvider authenticationProvider;
    private final AuthenticationSessionHandler authenticationSessionHandler;

    private volatile NetworkManager networkManager;
    private volatile World world;
    private volatile Thread networkThread;
    private volatile boolean running;

    public Narcissus(Configuration configuration, Path dataDirectory)
    {
        this(configuration, dataDirectory, null, null);
    }

    public Narcissus(Configuration configuration, Path dataDirectory,
                     AuthenticationProvider authenticationProvider,
                     AuthenticationSessionHandler authenticationSessionHandler)
    {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.authenticationProvider = authenticationProvider;
        this.authenticationSessionHandler = authenticationSessionHandler;
    }

    /**
     * Ensures the shared JUL logger has a formatter/handler. Safe to call multiple
     * times; only the first call installs handlers. Standalone entry points may use
     * this, plugin hosts typically rely on their own logging.
     */
    public static synchronized void configureStandaloneLogger()
    {
        if (loggerConfigured)
            return;
        LOGGER.setUseParentHandlers(false);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new LogFormatter());
        LOGGER.addHandler(handler);
        loggerConfigured = true;
    }

    /**
     * Loads the world from the configured schematic, resolved against the data
     * directory. Falls back to the bundled classpath resource when the file does
     * not yet exist in the data directory.
     */
    private void loadWorld() throws IOException
    {
        String fileName = this.configuration.getSchematicFile();
        Path schematicPath = this.dataDirectory.resolve(fileName);
        ConsoleUtil.sendMessage("Narcissus - " + VERSION);

        if (!Files.exists(schematicPath))
        {
            try (InputStream bundled = Narcissus.class.getResourceAsStream("/" + fileName))
            {
                if (bundled != null)
                {
                    Files.createDirectories(schematicPath.getParent());
                    Files.copy(bundled, schematicPath);
                    ConsoleUtil.sendMessage("Extracted bundled world to " + schematicPath);
                }
            }
        }

        if (!Files.exists(schematicPath))
            throw new IOException("Schematic file not found: " + schematicPath);

        ConsoleUtil.sendMessage("Loading schematic from " + schematicPath);
        try (InputStream in = Files.newInputStream(schematicPath))
        {
            CompoundTag schematic = NBTTag.readTag(new GZIPInputStream(in)).toCompoundTag();
            Schematic.Placement placement = Schematic.centered(schematic,
                    SCHEMATIC_ANCHOR_X, SCHEMATIC_ANCHOR_Y, SCHEMATIC_ANCHOR_Z);
            this.world = placement.getWorld();
            ConsoleUtil.sendMessage("Schematic format=" + (placement.isModern() ? "Sponge" : "legacy")
                    + " v" + placement.getVersion() + " size=" + placement.getWidth() + "x"
                    + placement.getHeight() + "x" + placement.getLength());
            ConsoleUtil.sendMessage("Schematic centered at fixed anchor (0,90,0): offset="
                    + placement.getOffsetX() + "," + placement.getOffsetY() + "," + placement.getOffsetZ());
        }
        ConsoleUtil.sendMessage("World has been loaded!");
    }

    /**
     * Starts the Limbo backend. Blocks until the listener socket is bound, then
     * hands packet processing off to a dedicated network thread. Throws when the
     * world cannot be loaded or the socket cannot be bound, so the caller can
     * abort registration of a half-usable backend.
     */
    public synchronized void start() throws IOException
    {
        if (this.running)
            throw new IllegalStateException("Narcissus is already running");

        this.loadWorld();

        NetworkManager manager = new NetworkManager(this.world, this.configuration,
                this.authenticationProvider, this.authenticationSessionHandler);
        manager.bind(this.configuration.getIp(), this.configuration.getPort());
        this.networkManager = manager;
        this.running = true;

        Thread thread = new Thread(() ->
        {
            try
            {
                while (this.running)
                    manager.select();
            }
            catch (Exception ex)
            {
                if (this.running)
                    LOGGER.log(Level.SEVERE, "Network loop terminated: " + ex.getMessage(), ex);
            }
        }, "Narcissus-Network");
        thread.setDaemon(true);
        this.networkThread = thread;
        thread.start();

        ConsoleUtil.sendMessage("Listening on " + manager.getBoundAddress());
    }

    public boolean isRunning()
    {
        return this.running;
    }

    public InetSocketAddress getBoundAddress()
    {
        NetworkManager manager = this.networkManager;
        return manager == null ? null : manager.getBoundAddress();
    }

    public NetworkManager getNetworkManager()
    {
        return this.networkManager;
    }

    public Configuration getConfiguration()
    {
        return this.configuration;
    }

    public World getWorld()
    {
        return this.world;
    }

    /**
     * Stops the backend. Idempotent: safe to call when never started, already
     * stopped, or after a failed start. Wakes the selector, closes the listener
     * and player connections, and waits briefly for the network thread to exit so
     * the port can be rebound immediately.
     */
    public synchronized void stop()
    {
        this.running = false;
        NetworkManager manager = this.networkManager;
        if (manager != null)
            manager.stop();

        Thread thread = this.networkThread;
        if (thread != null)
        {
            try
            {
                thread.join(5000L);
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt();
            }
        }
        this.networkThread = null;
        this.networkManager = null;
    }

    @Override
    public void close()
    {
        this.stop();
    }

    private static class LogFormatter extends Formatter
    {
        private static final String FORMAT = "[%1$tT][%3$s] %4$s : %5$s%6$s%n";
        private final Date date = new Date();

        @Override
        public String format(LogRecord record)
        {
            this.date.setTime(record.getMillis());
            String source;
            if (record.getSourceClassName() != null)
            {
                source = record.getSourceClassName();
                if (record.getSourceMethodName() != null)
                    source += " " + record.getSourceMethodName();
            }
            else
                source = record.getLoggerName();
            String message = formatMessage(record);
            String throwable = "";
            if (record.getThrown() != null)
            {
                java.io.StringWriter sw = new java.io.StringWriter();
                java.io.PrintWriter pw = new java.io.PrintWriter(sw);
                pw.println();
                record.getThrown().printStackTrace(pw);
                pw.close();
                throwable = sw.toString();
            }
            return String.format(LogFormatter.FORMAT,
                    this.date,
                    source,
                    record.getLoggerName(),
                    record.getLevel().getLocalizedName(),
                    message,
                    throwable);
        }
    }
}
