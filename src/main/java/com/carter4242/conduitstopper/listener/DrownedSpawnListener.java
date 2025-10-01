package com.carter4242.conduitstopper.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import com.carter4242.conduitstopper.storage.BlockPos;
import com.carter4242.conduitstopper.storage.ConduitStore;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles conduit placement/removal and prevents drowned spawning near conduits.
 */
public class DrownedSpawnListener implements Listener {
    private final int chunkCheckRadius;

    // World UUID -> (ChunkKey -> List<BlockPos>)
    private final Map<UUID, Map<Long, List<BlockPos>>> chunkedConduits = new ConcurrentHashMap<>();
    private final ConduitStore store;
    private BukkitTask autosaveTask;

    public DrownedSpawnListener(Plugin plugin, ConduitStore store, int chunkCheckRadius, long autosaveTicks) {
        this.store = store;
        this.chunkCheckRadius = chunkCheckRadius;
        store.load();
        loadConduitsFromStore();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        autosaveTask = Bukkit.getScheduler().runTaskTimer(plugin, store::save, autosaveTicks, autosaveTicks);
    }

    public void shutdown() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        store.save();
        chunkedConduits.clear();
    }

    /**
     * Loads conduits from persistent storage into the chunked cache.
     */
    private void loadConduitsFromStore() {
        chunkedConduits.clear();
        store.forEachConduit((worldId, pos) -> {
            long chunkKey = pos.chunkKey();
            chunkedConduits
                .computeIfAbsent(worldId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(chunkKey, k -> new ArrayList<>())
                .add(pos);
        });
    }

    /**
     * Adds a conduit to both persistent storage and chunk cache.
     */
    private void addConduit(World world, BlockPos position) {
        UUID worldId = world.getUID();
        store.add(worldId, position);
        
        long chunkKey = position.chunkKey();
        chunkedConduits
            .computeIfAbsent(worldId, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(chunkKey, k -> new ArrayList<>())
            .add(position);
    }

    /**
     * Removes a conduit from both persistent storage and chunk cache.
     */
    private void removeConduit(World world, BlockPos position) {
        UUID worldId = world.getUID();
        store.remove(worldId, position);
        
        long chunkKey = position.chunkKey();
        Map<Long, List<BlockPos>> worldMap = chunkedConduits.get(worldId);
        if (worldMap != null) {
            List<BlockPos> chunkConduits = worldMap.get(chunkKey);
            if (chunkConduits != null) {
                chunkConduits.remove(position);
                if (chunkConduits.isEmpty()) {
                    worldMap.remove(chunkKey);
                }
            }
            if (worldMap.isEmpty()) {
                chunkedConduits.remove(worldId);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConduitPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.CONDUIT) return;
        
        Location loc = event.getBlockPlaced().getLocation();
        addConduit(event.getBlockPlaced().getWorld(),
                   new BlockPos(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConduitBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.CONDUIT) return;
        
        Location loc = event.getBlock().getLocation();
        removeConduit(event.getBlock().getWorld(),
                      new BlockPos(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrownedPreSpawn(com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent event) {
        if (!shouldPreventSpawn(event.getType(), event.getReason())) return;
        
        if (isNearConduit(event.getSpawnLocation())) {
            event.setShouldAbortSpawn(true);
            event.setCancelled(true);
        }
    }

    /**
     * Determines if drowned spawning should be prevented for the given spawn reason.
     */
    private boolean shouldPreventSpawn(EntityType entityType, CreatureSpawnEvent.SpawnReason reason) {
        if (entityType != EntityType.DROWNED) return false;
        
        return switch (reason) {
            case NATURAL, REINFORCEMENTS -> true;
            default -> false;
        };
    }

    /**
     * Checks if the given location is near any conduit using chunk-based lookup.
     */
    private boolean isNearConduit(Location location) {
        World world = location.getWorld();
        if (world == null) return false;
        
        UUID worldId = world.getUID();
        Map<Long, List<BlockPos>> worldMap = chunkedConduits.get(worldId);
        if (worldMap == null || worldMap.isEmpty()) return false;

        int blockX = location.getBlockX();
        int blockZ = location.getBlockZ();
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;

        // Check all chunks in a grid centered on the spawn chunk
        for (int dx = -chunkCheckRadius; dx <= chunkCheckRadius; dx++) {
            for (int dz = -chunkCheckRadius; dz <= chunkCheckRadius; dz++) {
                long chunkKey = (((long)(chunkX + dx)) << 32) | ((chunkZ + dz) & 0xffffffffL);
                List<BlockPos> chunkConduits = worldMap.get(chunkKey);
                if (chunkConduits != null && !chunkConduits.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }
}