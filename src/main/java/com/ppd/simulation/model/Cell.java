package com.ppd.simulation.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Represents a single cell in the simulation grid.
 * Thread-safe for concurrent access by multiple threads.
 * 
 * Design Notes for MPI Extension:
 * - Cells at region boundaries need to be synchronized across processes
 * - The lock mechanism can be replaced with MPI collective operations
 * - Food state is a simple boolean, easy to synchronize
 */
public class Cell {
    private final Position position;
    private final AtomicBoolean hasFood;
    private final CopyOnWriteArrayList<Blob> blobs;
    private final ReentrantLock lock;

    public Cell(int x, int y) {
        this.position = new Position(x, y);
        this.hasFood = new AtomicBoolean(false);
        this.blobs = new CopyOnWriteArrayList<>();
        this.lock = new ReentrantLock();
    }

    public Cell(Position position) {
        this(position.getX(), position.getY());
    }

    // ============ Position ============

    public Position getPosition() {
        return position;
    }

    public int getX() {
        return position.getX();
    }

    public int getY() {
        return position.getY();
    }

    //  Food Management 

    public boolean hasFood() {
        return hasFood.get();
    }

    /**
     * Spawn food on this cell.
     */
    public void spawnFood() {
        hasFood.set(true);
    }

    /**
     * Consume food from this cell.
     * Returns true if food was consumed, false if no food was present.
     */
    public boolean consumeFood() {
        return hasFood.compareAndSet(true, false);
    }

    /**
     * Check if cell is empty (no blobs and no food).
     */
    public boolean isEmpty() {
        return blobs.isEmpty() && !hasFood.get();
    }

    /**
     * Check if cell can spawn food (no blobs and no existing food).
     */
    public boolean canSpawnFood() {
        return blobs.isEmpty() && !hasFood.get();
    }

    //  Blob Management 

    public List<Blob> getBlobs() {
        return new ArrayList<>(blobs);
    }

    public int getBlobCount() {
        return blobs.size();
    }

    public boolean hasBlobs() {
        return !blobs.isEmpty();
    }

    /**
     * Add a blob to this cell.
     * Thread-safe using CopyOnWriteArrayList.
     */
    public void addBlob(Blob blob) {
        blobs.add(blob);
    }

    /**
     * Remove a blob from this cell.
     * Thread-safe using CopyOnWriteArrayList.
     */
    public void removeBlob(Blob blob) {
        blobs.remove(blob);
    }

    /**
     * Clear all blobs from this cell.
     */
    public void clearBlobs() {
        blobs.clear();
    }

    //  Locking for Complex Operations 

    /**
     * Lock this cell for complex operations (food consumption, reproduction).
     */
    public void lock() {
        lock.lock();
    }

    /**
     * Unlock this cell.
     */
    public void unlock() {
        lock.unlock();
    }

    /**
     * Try to lock this cell.
     */
    public boolean tryLock() {
        return lock.tryLock();
    }

    /**
     * Execute an action while holding the cell lock.
     */
    public <T> T withLock(java.util.function.Supplier<T> action) {
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Execute an action while holding the cell lock.
     */
    public void withLock(Runnable action) {
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    //  Serialization for MPI 

    /**
     * Serialize cell state to int array for MPI transfer.
     * Format: [x, y, hasFood, blobCount]
     */
    public int[] toArray() {
        return new int[]{
            position.getX(),
            position.getY(),
            hasFood.get() ? 1 : 0,
            blobs.size()
        };
    }

    @Override
    public String toString() {
        return String.format("Cell[%s, food=%b, blobs=%d]", 
            position, hasFood.get(), blobs.size());
    }
}

