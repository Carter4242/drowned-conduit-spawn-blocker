package com.carter4242.conduitblocker;

import org.bukkit.plugin.java.JavaPlugin;

import com.carter4242.conduitblocker.listener.DrownedSpawnListener;
import com.carter4242.conduitblocker.storage.ConduitStore;

import java.io.File;
import java.util.concurrent.Callable;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SingleLineChart;

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
        // Register custom chart for spawns prevented averaged over an hour
        metrics.addCustomChart(
                new SingleLineChart("spawns_prevented", new Callable<Integer>() {
                    @Override
                    public Integer call() {
                        return listener.getAndResetSpawnsPreventedAverage();
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
