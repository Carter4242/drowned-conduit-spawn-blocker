package com.example.conduitstopper;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class ConduitStore {
    private final File file;
    // worldId -> set of BlockPos
    private final Map<UUID, Set<com.example.conduitstopper.DrownedSpawnListener.BlockPos>> data = new HashMap<>();
    private boolean dirty = false;

    public ConduitStore(File file) {
        this.file = file;
        if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
    }

    public void load() {
        data.clear();
        dirty = false;
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String worldKey : cfg.getKeys(false)) {
            try {
                UUID wid = UUID.fromString(worldKey);
                List<String> list = cfg.getStringList(worldKey);
                Set<com.example.conduitstopper.DrownedSpawnListener.BlockPos> set = new HashSet<>();
                for (String s : list) {
                    // format: x,y,z
                    String[] parts = s.split(",");
                    if (parts.length != 3) continue;
                    int x = Integer.parseInt(parts[0].trim());
                    int y = Integer.parseInt(parts[1].trim());
                    int z = Integer.parseInt(parts[2].trim());
                    set.add(new com.example.conduitstopper.DrownedSpawnListener.BlockPos(x, y, z));
                }
                if (!set.isEmpty()) data.put(wid, set);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void save() {
        if (!dirty) return;
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Set<com.example.conduitstopper.DrownedSpawnListener.BlockPos>> e : data.entrySet()) {
            List<String> list = new ArrayList<>(e.getValue().size());
            for (com.example.conduitstopper.DrownedSpawnListener.BlockPos p : e.getValue()) list.add(p.x + "," + p.y + "," + p.z);
            cfg.set(e.getKey().toString(), list);
        }
        try { cfg.save(file); dirty = false; } catch (IOException ex) { ex.printStackTrace(); }
    }

    public void add(UUID wid, com.example.conduitstopper.DrownedSpawnListener.BlockPos pos) {
        if (data.computeIfAbsent(wid, k -> new HashSet<>()).add(pos)) dirty = true;
    }

    public void remove(UUID wid, com.example.conduitstopper.DrownedSpawnListener.BlockPos pos) {
        Set<com.example.conduitstopper.DrownedSpawnListener.BlockPos> set = data.get(wid);
        if (set == null) return;
        if (set.remove(pos)) dirty = true;
        if (set.isEmpty()) data.remove(wid);
    }

    public boolean contains(UUID wid, com.example.conduitstopper.DrownedSpawnListener.BlockPos pos) {
        Set<com.example.conduitstopper.DrownedSpawnListener.BlockPos> set = data.get(wid);
        return set != null && set.contains(pos);
    }

    public void forEachConduit(java.util.function.BiConsumer<UUID, com.example.conduitstopper.DrownedSpawnListener.BlockPos> consumer) {
        data.forEach((wid, set) -> set.forEach(p -> consumer.accept(wid, p)));
    }

    public void forEachConduit(UUID wid, java.util.function.Consumer<com.example.conduitstopper.DrownedSpawnListener.BlockPos> consumer) {
        Set<com.example.conduitstopper.DrownedSpawnListener.BlockPos> set = data.get(wid);
        if (set != null) set.forEach(consumer);
    }
}
