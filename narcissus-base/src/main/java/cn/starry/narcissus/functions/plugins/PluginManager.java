package cn.starry.narcissus.functions.plugins;

import cn.starry.narcissus.Narcissus;
import cn.starry.narcissus.util.ConsoleUtil;

import java.io.File;
import java.util.logging.Level;

/**
 * Plugin Manager - Responsible for creating and managing the plugins directory.
 */
public class PluginManager {

    private static final String PLUGINS_FOLDER_NAME = "plugins";
    
    private final File pluginsFolder;
    private boolean initialized;

    public PluginManager() {
        this.pluginsFolder = new File(PLUGINS_FOLDER_NAME);
        this.initialized = false;
    }

    /**
     * Initialize the plugin manager.
     * Creates the plugins directory if it doesn't exist.
     * 
     * @return true if initialization was successful, false otherwise
     */
    public boolean initialize() {
        ConsoleUtil.sendMessage("Initializing plugin manager...");
        
        if (!pluginsFolder.exists()) {
            if (pluginsFolder.mkdirs()) {
                ConsoleUtil.sendMessage("Created plugins directory: " + pluginsFolder.getAbsolutePath());
            } else {
                Narcissus.LOGGER.log(Level.SEVERE, "Failed to create plugins directory: " + pluginsFolder.getAbsolutePath());
                return false;
            }
        } else {
            if (!pluginsFolder.isDirectory()) {
                Narcissus.LOGGER.log(Level.SEVERE, "plugins path exists but is not a directory: " + pluginsFolder.getAbsolutePath());
                return false;
            }
            ConsoleUtil.sendMessage("Plugins directory found: " + pluginsFolder.getAbsolutePath());
        }
        
        this.initialized = true;
        ConsoleUtil.sendMessage("Plugin manager initialized successfully!");
        return true;
    }

    /**
     * Get the plugins folder.
     * 
     * @return the plugins folder
     */
    public File getPluginsFolder() {
        return pluginsFolder;
    }

    /**
     * Check if the plugin manager has been initialized.
     * 
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        return initialized;
    }
}
