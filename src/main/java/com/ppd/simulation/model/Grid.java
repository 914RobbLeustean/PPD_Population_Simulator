package com.ppd.simulation.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The simulation grid containing all cells and managing spatial queries.
 * 
 * Design Notes for MPI Extension:
 * - The grid can be partitioned into horizontal/vertical strips
 * - Each MPI process owns a contiguous range of rows/columns
 * - Border cells (within vision radius) need to be exchanged between processes
 * - The getRegion() method supports arbitrary partitioning
 */
public class Grid {
    private final int width;
    private final int height;
    private final Cell[][] cells;
    private final CopyOnWriteArrayList<Blob> allBlobs;
    private final int visionRadius;

    public Grid(int width, int height, int visionRadius) {
        this.width = width;
        this.height = height;
        this.visionRadius = visionRadius;
        this.cells = new Cell[height][width];
        this.allBlobs = new CopyOnWriteArrayList<>();

        // Initialize all cells
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                cells[y][x] = new Cell(x, y);
            }
        }
    }

    //  Grid Properties 

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getVisionRadius() {
        return visionRadius;
    }

    public int getTotalCells() {
        return width * height;
    }

    //  Cell Access 

    public Cell getCell(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return null;
        }
        return cells[y][x];
    }

    public Cell getCell(Position pos) {
        return getCell(pos.getX(), pos.getY());
    }

    /**
     * Check if a position is within grid bounds.
     */
    public boolean isValidPosition(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    public boolean isValidPosition(Position pos) {
        return isValidPosition(pos.getX(), pos.getY());
    }

    /**
     * Clamp a position to grid boundaries.
     */
    public Position clampPosition(Position pos) {
        return pos.clamp(0, width - 1, 0, height - 1);
    }

    //  Blob Management 

    public List<Blob> getAllBlobs() {
        return new ArrayList<>(allBlobs);
    }

    public List<Blob> getLivingBlobs() {
        List<Blob> living = new ArrayList<>();
        for (Blob blob : allBlobs) {
            if (blob.isAlive()) {
                living.add(blob);
            }
        }
        return living;
    }

    public int getBlobCount() {
        return allBlobs.size();
    }

    public int getLivingBlobCount() {
        int count = 0;
        for (Blob blob : allBlobs) {
            if (blob.isAlive()) count++;
        }
        return count;
    }

    /**
     * Add a blob to the grid.
     */
    public void addBlob(Blob blob) {
        allBlobs.add(blob);
        Cell cell = getCell(blob.getPosition());
        if (cell != null) {
            cell.addBlob(blob);
        }
    }

    /**
     * Remove a blob from the grid.
     */
    public void removeBlob(Blob blob) {
        allBlobs.remove(blob);
        Cell cell = getCell(blob.getPosition());
        if (cell != null) {
            cell.removeBlob(blob);
        }
    }

    /**
     * Remove all dead blobs from the grid.
     */
    public int removeDeadBlobs() {
        List<Blob> deadBlobs = new ArrayList<>();
        for (Blob blob : allBlobs) {
            if (!blob.isAlive()) {
                deadBlobs.add(blob);
            }
        }
        for (Blob blob : deadBlobs) {
            Cell cell = getCell(blob.getPosition());
            if (cell != null) {
                cell.removeBlob(blob);
            }
            allBlobs.remove(blob);
        }
        return deadBlobs.size();
    }

    /**
     * Move a blob from one cell to another.
     */
    public void moveBlob(Blob blob, Position newPosition) {
        Cell oldCell = getCell(blob.getPosition());
        Cell newCell = getCell(newPosition);

        if (oldCell != null) {
            oldCell.removeBlob(blob);
        }
        blob.setPosition(newPosition);
        if (newCell != null) {
            newCell.addBlob(blob);
        }
    }

    //  Spatial Queries 

    /**
     * Get all cells within the vision radius of a position.
     */
    public List<Cell> getCellsInRadius(Position center, int radius) {
        List<Cell> result = new ArrayList<>();
        int startX = Math.max(0, center.getX() - radius);
        int endX = Math.min(width - 1, center.getX() + radius);
        int startY = Math.max(0, center.getY() - radius);
        int endY = Math.min(height - 1, center.getY() + radius);

        for (int y = startY; y <= endY; y++) {
            for (int x = startX; x <= endX; x++) {
                Position pos = new Position(x, y);
                if (center.isWithinRadius(pos, radius)) {
                    result.add(cells[y][x]);
                }
            }
        }
        return result;
    }

    /**
     * Get all blobs within the vision radius of a position (excluding the blob at center).
     */
    public List<Blob> getBlobsInRadius(Position center, int radius) {
        List<Blob> result = new ArrayList<>();
        for (Cell cell : getCellsInRadius(center, radius)) {
            result.addAll(cell.getBlobs());
        }
        return result;
    }

    /**
     * Find nearest food within radius of a position.
     * Returns null if no food found.
     */
    public Position findNearestFood(Position center, int radius) {
        Position nearestFood = null;
        int nearestDistance = Integer.MAX_VALUE;

        for (Cell cell : getCellsInRadius(center, radius)) {
            if (cell.hasFood()) {
                int distance = center.manhattanDistance(cell.getPosition());
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestFood = cell.getPosition();
                }
            }
        }
        return nearestFood;
    }

    /**
     * Find nearest blob that can reproduce (energy > 150) within radius.
     * Returns null if no suitable blob found.
     */
    public Blob findNearestReproductionCandidate(Blob seeker, int radius) {
        Blob nearestCandidate = null;
        int nearestDistance = Integer.MAX_VALUE;

        for (Blob other : getBlobsInRadius(seeker.getPosition(), radius)) {
            if (other != seeker && other.isAlive() && other.canReproduce()) {
                int distance = seeker.getPosition().manhattanDistance(other.getPosition());
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestCandidate = other;
                }
            }
        }
        return nearestCandidate;
    }

    //  Region Support (for Threading/MPI) 

    /**
     * Get all cells in a rectangular region.
     * Useful for dividing grid among threads or MPI processes.
     */
    public List<Cell> getRegion(int startX, int endX, int startY, int endY) {
        List<Cell> region = new ArrayList<>();
        for (int y = startY; y <= endY && y < height; y++) {
            for (int x = startX; x <= endX && x < width; x++) {
                region.add(cells[y][x]);
            }
        }
        return region;
    }

    /**
     * Get all blobs in a rectangular region.
     */
    public List<Blob> getBlobsInRegion(int startX, int endX, int startY, int endY) {
        List<Blob> blobs = new ArrayList<>();
        for (int y = startY; y <= endY && y < height; y++) {
            for (int x = startX; x <= endX && x < width; x++) {
                blobs.addAll(cells[y][x].getBlobs());
            }
        }
        return blobs;
    }

    /**
     * Get border cells for a region (cells within vision radius of region boundary).
     * Used in MPI to determine which cells need to be shared with neighbors.
     */
    public List<Cell> getBorderCells(int startX, int endX, int startY, int endY, int borderWidth) {
        List<Cell> border = new ArrayList<>();
        
        // Top border
        for (int y = Math.max(0, startY - borderWidth); y < startY && y < height; y++) {
            for (int x = startX; x <= endX && x < width; x++) {
                border.add(cells[y][x]);
            }
        }
        // Bottom border
        for (int y = endY + 1; y <= endY + borderWidth && y < height; y++) {
            for (int x = startX; x <= endX && x < width; x++) {
                border.add(cells[y][x]);
            }
        }
        // Left border (excluding corners already added)
        for (int y = startY; y <= endY && y < height; y++) {
            for (int x = Math.max(0, startX - borderWidth); x < startX && x < width; x++) {
                border.add(cells[y][x]);
            }
        }
        // Right border (excluding corners already added)
        for (int y = startY; y <= endY && y < height; y++) {
            for (int x = endX + 1; x <= endX + borderWidth && x < width; x++) {
                border.add(cells[y][x]);
            }
        }
        return border;
    }

    //  Food Management 

    /**
     * Spawn food on a percentage of empty cells.
     * Returns the number of food items spawned.
     */
    public int spawnFood(double percentage, Random rng) {
        int spawned = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Cell cell = cells[y][x];
                if (cell.canSpawnFood() && rng.nextDouble() < percentage) {
                    cell.spawnFood();
                    spawned++;
                }
            }
        }
        return spawned;
    }

    /**
     * Spawn initial food on a percentage of all cells.
     */
    public int spawnInitialFood(double percentage, Random rng) {
        int spawned = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (rng.nextDouble() < percentage) {
                    cells[y][x].spawnFood();
                    spawned++;
                }
            }
        }
        return spawned;
    }

    /**
     * Count total food on the grid.
     */
    public int countFood() {
        int count = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (cells[y][x].hasFood()) count++;
            }
        }
        return count;
    }

    //  Initialization 

    /**
     * Initialize the grid with random blobs.
     */
    public void initializeBlobs(int count, int startingEnergy, Random rng) {
        for (int i = 0; i < count; i++) {
            int x = rng.nextInt(width);
            int y = rng.nextInt(height);
            Position pos = new Position(x, y);
            
            // Ensure no two blobs start on the same cell
            while (getCell(pos).hasBlobs()) {
                x = rng.nextInt(width);
                y = rng.nextInt(height);
                pos = new Position(x, y);
            }
            
            Blob blob = new Blob(pos, startingEnergy);
            addBlob(blob);
        }
    }
}

