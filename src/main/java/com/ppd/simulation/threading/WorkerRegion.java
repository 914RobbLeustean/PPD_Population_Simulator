package com.ppd.simulation.threading;

/**
 * Defines a rectangular region of the grid assigned to a worker thread.
 * 
 * Design Notes for MPI Extension:
 * - Each MPI process would own one or more WorkerRegions
 * - Regions can be serialized and communicated between processes
 * - Border regions (within vision radius) need special handling for MPI
 */
public class WorkerRegion {
    private final int id;
    private final int startX;
    private final int endX;
    private final int startY;
    private final int endY;

    public WorkerRegion(int id, int startX, int endX, int startY, int endY) {
        this.id = id;
        this.startX = startX;
        this.endX = endX;
        this.startY = startY;
        this.endY = endY;
    }

    public int getId() { return id; }
    public int getStartX() { return startX; }
    public int getEndX() { return endX; }
    public int getStartY() { return startY; }
    public int getEndY() { return endY; }

    public int getWidth() { return endX - startX + 1; }
    public int getHeight() { return endY - startY + 1; }
    public int getCellCount() { return getWidth() * getHeight(); }

    /**
     * Check if a position is within this region.
     */
    public boolean contains(int x, int y) {
        return x >= startX && x <= endX && y >= startY && y <= endY;
    }

    /**
     * Create regions by dividing the grid into horizontal strips.
     * This is the recommended layout for MPI as it minimizes border communication.
     */
    public static WorkerRegion[] createHorizontalStrips(int gridWidth, int gridHeight, int regionCount) {
        WorkerRegion[] regions = new WorkerRegion[regionCount];
        int stripHeight = gridHeight / regionCount;
        int remainder = gridHeight % regionCount;
        
        int currentY = 0;
        for (int i = 0; i < regionCount; i++) {
            int thisHeight = stripHeight + (i < remainder ? 1 : 0);
            regions[i] = new WorkerRegion(i, 0, gridWidth - 1, currentY, currentY + thisHeight - 1);
            currentY += thisHeight;
        }
        return regions;
    }

    /**
     * Create regions by dividing the grid into a square-ish grid of regions.
     * Good for load balancing when blobs are distributed uniformly.
     */
    public static WorkerRegion[] createGrid(int gridWidth, int gridHeight, int regionCount) {
        // Find factors closest to square root
        int cols = (int) Math.ceil(Math.sqrt(regionCount));
        int rows = (regionCount + cols - 1) / cols;
        
        // Adjust to match exact region count
        while (rows * cols > regionCount) {
            if (cols > rows) cols--;
            else rows--;
        }
        while (rows * cols < regionCount) {
            cols++;
        }

        WorkerRegion[] regions = new WorkerRegion[regionCount];
        int cellWidth = gridWidth / cols;
        int cellHeight = gridHeight / rows;
        int widthRemainder = gridWidth % cols;
        int heightRemainder = gridHeight % rows;

        int regionId = 0;
        int currentY = 0;
        for (int r = 0; r < rows && regionId < regionCount; r++) {
            int thisHeight = cellHeight + (r < heightRemainder ? 1 : 0);
            int currentX = 0;
            for (int c = 0; c < cols && regionId < regionCount; c++) {
                int thisWidth = cellWidth + (c < widthRemainder ? 1 : 0);
                regions[regionId] = new WorkerRegion(regionId, 
                    currentX, currentX + thisWidth - 1,
                    currentY, currentY + thisHeight - 1);
                regionId++;
                currentX += thisWidth;
            }
            currentY += thisHeight;
        }
        return regions;
    }

    @Override
    public String toString() {
        return String.format("Region[%d: (%d,%d)-(%d,%d), %dx%d=%d cells]",
            id, startX, startY, endX, endY, getWidth(), getHeight(), getCellCount());
    }
}

