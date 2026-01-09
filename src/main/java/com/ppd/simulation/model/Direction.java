package com.ppd.simulation.model;

import java.util.Random;

/**
 * Enum representing the four cardinal directions for movement.
 */
public enum Direction {
    UP(0, -1),
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0);

    private final int dx;
    private final int dy;

    Direction(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public int getDx() {
        return dx;
    }

    public int getDy() {
        return dy;
    }

    /**
     * Get a random direction.
     */
    public static Direction random(Random rng) {
        Direction[] values = values();
        return values[rng.nextInt(values.length)];
    }

    /**
     * Calculate direction to move from 'from' position toward 'to' position.
     * Returns the direction that reduces distance the most.
     */
    public static Direction toward(Position from, Position to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();

        // Prefer moving in the direction with larger difference
        if (Math.abs(dx) >= Math.abs(dy)) {
            return dx > 0 ? RIGHT : LEFT;
        } else {
            return dy > 0 ? DOWN : UP;
        }
    }
}

