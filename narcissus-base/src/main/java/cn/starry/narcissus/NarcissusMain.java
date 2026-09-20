package cn.starry.narcissus;

import cn.starry.narcissus.functions.config.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

/** Standalone entry for local reproduction: starts the native multi-protocol backend. */
public final class NarcissusMain {
    private NarcissusMain() {}

    public static void main(String[] args) throws Exception {
        Narcissus.configureStandaloneLogger();
        Path data = Paths.get("build/standalone-data");
        // Load spawn/gamemode/schematic from build/standalone-data/server.properties
        // (falls back to the bundled defaults). The bind port comes from args so
        // several test instances can run side by side.
        Configuration loaded = Configuration.load(data);
        int port = args.length > 0 ? Integer.parseInt(args[0])
                : (loaded != null ? loaded.getPort() : 25565);
        Configuration cfg = (loaded != null ? loaded.toBuilder() : Configuration.builder())
                .ip("127.0.0.1").port(port).build();
        Narcissus narcissus = new Narcissus(cfg, data);
        narcissus.start();
        System.out.println("NARCISSUS_READY port=" + narcissus.getBoundAddress().getPort());
        Thread.currentThread().join();
    }
}
