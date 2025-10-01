package com.carter4242.conduitstopper;

import org.bukkit.plugin.java.JavaPlugin;

import com.carter4242.conduitstopper.listener.DrownedSpawnListener;
import com.carter4242.conduitstopper.storage.ConduitStore;

import java.io.File;

// Import the relocated Metrics class
import org.bstats.bukkit.Metrics;

/**
 * Main plugin class for DrownedConduitSpawnBlocker.
 * Prevents drowned from spawning near conduits.
 */
public class DrownedConduitSpawnBlocker extends JavaPlugin {
    private DrownedSpawnListener listener;
    private ConduitStore store;

    @Override
    public void onEnable() {
        // Ensure config.yml exists
        saveDefaultConfig();

        // Create data folder if it doesn't exist
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Read config values
        int chunkCheckRadius = getConfig().getInt("chunk-check-radius", 2);
        long autosaveTicks = getConfig().getLong("autosave-ticks", 6000L);
        boolean debug = getConfig().getBoolean("debug", false);

        // Initialize storage
        File conduitsFile = new File(getDataFolder(), "conduits.yml");
        store = new ConduitStore(conduitsFile, getLogger());

        // Initialize listener with config values
        listener = new DrownedSpawnListener(this, store, chunkCheckRadius, autosaveTicks, debug);

        // bStats Metrics integration
        int pluginId = 27422;
        Metrics metrics = new Metrics(this, pluginId);
        // Register custom chart for spawns prevented per hour
        metrics.addCustomChart(
                new org.bstats.charts.SingleLineChart("spawns_prevented", new java.util.concurrent.Callable<Integer>() {
                    @Override
                    public Integer call() {
                        return listener.getAndResetSpawnsPrevented();
                    }
                }));

        getLogger().info("ConduitSpawnStopper has been enabled!");
    }

    @Override
    public void onDisable() {
        if (listener != null) {
            listener.shutdown();
        }
        getLogger().info("ConduitSpawnStopper has been disabled!");
    }
}
