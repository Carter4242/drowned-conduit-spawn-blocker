package com.carter4242.conduitstopper;

import org.bukkit.plugin.java.JavaPlugin;

import com.carter4242.conduitstopper.listener.DrownedSpawnListener;
import com.carter4242.conduitstopper.storage.ConduitStore;

import java.io.File;

/**
 * Main plugin class for ConduitSpawnStopper.
 * Prevents drowned from spawning near conduits.
 */
public class ConduitSpawnStopper extends JavaPlugin {
    private DrownedSpawnListener listener;
    private ConduitStore store;

    @Override
    public void onEnable() {
        // Create data folder if it doesn't exist
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Initialize storage
        File conduitsFile = new File(getDataFolder(), "conduits.yml");
        store = new ConduitStore(conduitsFile, getLogger());

        // Initialize listener
        listener = new DrownedSpawnListener(this, store);

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
