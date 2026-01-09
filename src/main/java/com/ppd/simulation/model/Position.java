package com.ppd.simulation.model;

import java.util.Objects;

/**
 * Immutable position class representing coordinates on the grid.
 * Designed for easy serialization in distributed (MPI) scenarios.
 */
public final class Position {
    private final int x;
    private final int y;

    public Position(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    /**
     * Calculate Manhattan distance to another position.
     * Used for vision radius calculations.
     */
    public int manhattanDistance(Position other) {
        return Math.abs(this.x - other.x) + Math.abs(this.y - other.y);
    }

    /**
     * Calculate Euclidean distance to another position.
     */
    public double euclideanDistance(Position other) {
        int dx = this.x - other.x;
        int dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Check if another position is within the given radius (Manhattan distance).
     */
    public boolean isWithinRadius(Position other, int radius) {
        return manhattanDistance(other) <= radius;
    }

    /**
     * Create a new position moved by the given delta.
     */
    public Position move(int dx, int dy) {
        return new Position(x + dx, y + dy);
    }

    /**
     * Clamp position to grid boundaries.
     */
    public Position clamp(int minX, int maxX, int minY, int maxY) {
        int newX = Math.max(minX, Math.min(maxX, x));
        int newY = Math.max(minY, Math.min(maxY, y));
        return new Position(newX, newY);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Position position = (Position) o;
        return x == position.x && y == position.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }

    /**
     * Serialize position to array for MPI communication.
     */
    public int[] toArray() {
        return new int[]{x, y};
    }

    /**
     * Deserialize position from array.
     */
    public static Position fromArray(int[] arr) {
        return new Position(arr[0], arr[1]);
    }
}

