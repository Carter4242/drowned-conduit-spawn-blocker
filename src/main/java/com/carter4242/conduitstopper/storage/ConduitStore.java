package com.carter4242.conduitstopper.storage;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Manages persistent storage of conduit locations across worlds.
 */
public final class ConduitStore {
    private final File file;
    private final Logger logger;
    // worldId -> set of BlockPos
    private final Map<UUID, Set<BlockPos>> data = new HashMap<>();
    private boolean dirty = false;

    public ConduitStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
    }

    /**
     * Loads conduit data from the configuration file.
     */
    public void load() {
        data.clear();
        dirty = false;
        if (!file.exists())
            return;

        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            for (String worldKey : cfg.getKeys(false)) {
                try {
                    UUID worldId = UUID.fromString(worldKey);
                    List<String> positions = cfg.getStringList(worldKey);
                    Set<BlockPos> conduitSet = new HashSet<>();

                    for (String positionStr : positions) {
                        BlockPos pos = parseBlockPos(positionStr);
                        if (pos != null) {
                            conduitSet.add(pos);
                        }
                    }

                    if (!conduitSet.isEmpty()) {
                        data.put(worldId, conduitSet);
                    }
                } catch (IllegalArgumentException e) {
                    logger.warning("Invalid world ID in conduits.yml: " + worldKey);
                }
            }
        } catch (Exception e) {
            logger.severe("Failed to load conduits.yml: " + e.getMessage());
        }
    }

    /**
     * Saves conduit data to the configuration file if changes have been made.
     */
    public void save() {
        if (!dirty)
            return;

        try {
            YamlConfiguration cfg = new YamlConfiguration();
            for (Map.Entry<UUID, Set<BlockPos>> entry : data.entrySet()) {
                List<String> positions = new ArrayList<>(entry.getValue().size());
                for (BlockPos pos : entry.getValue()) {
                    positions.add(pos.getX() + "," + pos.getY() + "," + pos.getZ());
                }
                cfg.set(entry.getKey().toString(), positions);
            }
            cfg.save(file);
            dirty = false;
        } catch (IOException e) {
            logger.severe("Failed to save conduits.yml: " + e.getMessage());
        }
    }

    /**
     * Adds a conduit at the specified position in the given world.
     */
    public void add(UUID worldId, BlockPos position) {
        if (data.computeIfAbsent(worldId, k -> new HashSet<>()).add(position)) {
            dirty = true;
        }
    }

    /**
     * Removes a conduit at the specified position in the given world.
     */
    public void remove(UUID worldId, BlockPos position) {
        Set<BlockPos> worldConduits = data.get(worldId);
        if (worldConduits == null)
            return;

        if (worldConduits.remove(position)) {
            dirty = true;
            if (worldConduits.isEmpty()) {
                data.remove(worldId);
            }
        }
    }

    /**
     * Checks if a conduit exists at the specified position in the given world.
     */
    public boolean contains(UUID worldId, BlockPos position) {
        Set<BlockPos> worldConduits = data.get(worldId);
        return worldConduits != null && worldConduits.contains(position);
    }

    /**
     * Executes a function for each conduit across all worlds.
     */
    public void forEachConduit(BiConsumer<UUID, BlockPos> consumer) {
        data.forEach((worldId, conduits) -> conduits.forEach(pos -> consumer.accept(worldId, pos)));
    }

    /**
     * Executes a function for each conduit in a specific world.
     */
    public void forEachConduit(UUID worldId, Consumer<BlockPos> consumer) {
        Set<BlockPos> worldConduits = data.get(worldId);
        if (worldConduits != null) {
            worldConduits.forEach(consumer);
        }
    }

    /**
     * Parses a position string in the format "x,y,z".
     */
    private BlockPos parseBlockPos(String positionStr) {
        try {
            String[] parts = positionStr.split(",");
            if (parts.length != 3)
                return null;

            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            int z = Integer.parseInt(parts[2].trim());

            return new BlockPos(x, y, z);
        } catch (NumberFormatException e) {
            logger.warning("Invalid position format: " + positionStr);
            return null;
        }
    }
}