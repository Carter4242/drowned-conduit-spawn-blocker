package com.example.conduitstopper;

import org.bukkit.plugin.java.JavaPlugin;

public class ConduitSpawnStopper extends JavaPlugin {
    private DrownedSpawnListener listener;

    @Override
    public void onEnable() {
        listener = new DrownedSpawnListener(this);
    }

    @Override
    public void onDisable() {
        if (listener != null) listener.shutdown();
    }
}
