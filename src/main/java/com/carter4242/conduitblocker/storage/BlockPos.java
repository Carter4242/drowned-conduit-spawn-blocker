package com.carter4242.conduitblocker.storage;

import java.util.Objects;

/**
 * Represents a block position with efficient chunk key calculation.
 */
public final class BlockPos {
    private final int x, y, z;

    public BlockPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    /**
     * Calculates a unique key for the chunk containing this position.
     */
    public long chunkKey() {
        return (((long) (x >> 4)) << 32) ^ ((z >> 4) & 0xffffffffL);
    }

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof BlockPos blockPos))
            return false;
        return x == blockPos.x && y == blockPos.y && z == blockPos.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    @Override
    public String toString() {
        return String.format("BlockPos{x=%d, y=%d, z=%d}", x, y, z);
    }
}