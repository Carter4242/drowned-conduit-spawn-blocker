package com.example.conduitstopper;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class DrownedSpawnListener implements Listener {
    private static final int RADIUS = 32;
    private static final int RADIUS_SQ = RADIUS * RADIUS;
    private static final long AUTOSAVE_TICKS = 20L * 60L * 30; // 30 minutes

    // World UUID -> (ChunkKey -> List<BlockPos>)
    private final Map<UUID, Map<Long, List<BlockPos>>> chunkedConduits = new ConcurrentHashMap<>();
    private final ConduitStore store;
    private BukkitTask autosaveTask;
    private final Plugin plugin;

    public DrownedSpawnListener(Plugin plugin) {
        this.plugin = plugin;
        this.store = new ConduitStore(new java.io.File(plugin.getDataFolder(), "conduits.yml"));
        store.load();
        loadConduitsFromStore();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        autosaveTask = Bukkit.getScheduler().runTaskTimer(plugin, store::save, AUTOSAVE_TICKS, AUTOSAVE_TICKS);
    }

    public void shutdown() {
        if (autosaveTask != null) autosaveTask.cancel();
        store.save();
        chunkedConduits.clear();
    }


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

    private void addConduit(World w, BlockPos pos) {
        UUID wid = w.getUID();
        store.add(wid, pos);
        long chunkKey = pos.chunkKey();
        chunkedConduits
            .computeIfAbsent(wid, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(chunkKey, k -> new ArrayList<>())
            .add(pos);
    }

    private void removeConduit(World w, BlockPos pos) {
        UUID wid = w.getUID();
        store.remove(wid, pos);
        long chunkKey = pos.chunkKey();
        Map<Long, List<BlockPos>> worldMap = chunkedConduits.get(wid);
        if (worldMap != null) {
            List<BlockPos> list = worldMap.get(chunkKey);
            if (list != null) {
                list.remove(pos);
                if (list.isEmpty()) worldMap.remove(chunkKey);
            }
            if (worldMap.isEmpty()) chunkedConduits.remove(wid);
        }
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (e.getBlockPlaced().getType() != Material.CONDUIT) return;
        addConduit(e.getBlockPlaced().getWorld(),
                   new BlockPos(e.getBlockPlaced().getX(), e.getBlockPlaced().getY(), e.getBlockPlaced().getZ()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (e.getBlock().getType() != Material.CONDUIT) return;
        removeConduit(e.getBlock().getWorld(),
                      new BlockPos(e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPreSpawn(com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent e) {
        if (!shouldCheck(e.getType(), e.getReason())) return;
        boolean near = nearConduit(e.getSpawnLocation());
        int bx = e.getSpawnLocation().getBlockX();
        int by = e.getSpawnLocation().getBlockY();
        int bz = e.getSpawnLocation().getBlockZ();
        int chunkX = bx >> 4;
        int chunkZ = bz >> 4;
        if (near) {
            e.setCancelled(true);
            plugin.getLogger().info("Cancelled Drowned spawn at " + bx + "," + by + "," + bz +
                " (chunk " + chunkX + "," + chunkZ + ")");
        } else {
            plugin.getLogger().info("Allowed Drowned spawn at " + bx + "," + by + "," + bz +
                " (chunk " + chunkX + "," + chunkZ + ")");
        }
    }

    private boolean shouldCheck(EntityType type, CreatureSpawnEvent.SpawnReason reason) {
        if (type != EntityType.DROWNED) return false;
        return switch (reason) {
            case NATURAL, CHUNK_GEN, REINFORCEMENTS -> true;
            default -> false;
        };
    }

    private boolean nearConduit(Location loc) {
        World w = loc.getWorld();
        if (w == null) return false;
        UUID wid = w.getUID();
        Map<Long, List<BlockPos>> worldMap = chunkedConduits.get(wid);
        if (worldMap == null || worldMap.isEmpty()) return false;

        final int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();
        int chunkX = bx >> 4, chunkZ = bz >> 4;

        // Check all chunks in a 5x5 grid centered on the spawn chunk
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                long key = (((long)(chunkX + dx)) << 32) | ((chunkZ + dz) & 0xffffffffL);
                List<BlockPos> list = worldMap.get(key);
                if (list == null) continue;
                for (BlockPos p : list) {
                    if (Math.abs(p.y - by) > 12) continue;
                    int dX = p.x - bx, dY = p.y - by, dZ = p.z - bz;
                    if (dX*dX + dY*dY + dZ*dZ <= RADIUS_SQ) return true;
                }
            }
        }
        return false;
    }

    static final class BlockPos {
        final int x, y, z;
        BlockPos(int x, int y, int z){ this.x=x; this.y=y; this.z=z; }
        long chunkKey(){ return (((long)(x>>4))<<32) ^ ((z>>4) & 0xffffffffL); }
        int chunkX(){ return x>>4; }
        int chunkZ(){ return z>>4; }
        @Override public boolean equals(Object o){ if(this==o) return true; if(!(o instanceof BlockPos b)) return false; return x==b.x && y==b.y && z==b.z; }
        @Override public int hashCode(){ return Objects.hash(x,y,z); }
    }
}
