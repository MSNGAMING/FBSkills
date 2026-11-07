package com.fbskills.util;

import org.bukkit.block.Block;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance, anti-exploit tracker for player-placed blocks.
 * Prevents exploit loops (e.g., place-and-break ores or crops for infinite XP).
 * Supports piston shift adjustments.
 */
public class BlockTracker {

    public record BlockKey(UUID worldUid, int x, int y, int z) {}

    private final Map<BlockKey, Long> placedBlocks = new ConcurrentHashMap<>();
    private final long ttlMillis;

    public BlockTracker(long ttlMinutes) {
        this.ttlMillis = Math.max(5L, ttlMinutes) * 60_000L;
    }

    /**
     * Marks a block as placed by a player.
     */
    public void track(Block block) {
        if (block == null || block.getWorld() == null) return;
        BlockKey key = new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        placedBlocks.put(key, System.currentTimeMillis());
    }

    /**
     * Checks if a block was player-placed and is still within the tracking window.
     */
    public boolean isPlayerPlaced(Block block) {
        if (block == null || block.getWorld() == null) return false;
        BlockKey key = new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        Long timestamp = placedBlocks.get(key);
        if (timestamp == null) return false;

        if (System.currentTimeMillis() - timestamp > ttlMillis) {
            placedBlocks.remove(key);
            return false;
        }
        return true;
    }

    /**
     * Removes a block from tracking once consumed.
     */
    public void unregister(Block block) {
        if (block == null || block.getWorld() == null) return;
        placedBlocks.remove(new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()));
    }

    /**
     * Shifts block tracking when moved by a piston to prevent bypasses.
     */
    public void shift(UUID worldUid, int fromX, int fromY, int fromZ, int toX, int toY, int toZ) {
        BlockKey oldKey = new BlockKey(worldUid, fromX, fromY, fromZ);
        Long timestamp = placedBlocks.remove(oldKey);
        if (timestamp != null) {
            placedBlocks.put(new BlockKey(worldUid, toX, toY, toZ), timestamp);
        }
    }

    /**
     * Purges expired entries to prevent unbounded memory growth.
     */
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        placedBlocks.entrySet().removeIf(entry -> (now - entry.getValue()) > ttlMillis);
    }

    public void clear() {
        placedBlocks.clear();
    }
}
