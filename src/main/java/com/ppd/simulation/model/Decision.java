package com.ppd.simulation.model;

/**
 * Enum representing possible blob decisions during the decision phase.
 * Designed for easy serialization in distributed scenarios.
 */
public enum Decision {
    /**
     * Blob will attempt to reproduce with a nearby blob.
     * Blob stays in current cell.
     */
    REPRODUCE(0),

    /**
     * Blob will move toward the nearest visible food.
     * Blob moves 1 cell toward food target.
     */
    MOVE_TO_FOOD(1),

    /**
     * Blob will move randomly in one of 4 directions.
     * Blob moves 1 cell in random direction.
     */
    MOVE_RANDOM(2),

    /**
     * Blob cannot move (at boundary or no valid moves).
     */
    STAY(3);

    private final int code;

    Decision(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static Decision fromCode(int code) {
        for (Decision d : values()) {
            if (d.code == code) return d;
        }
        throw new IllegalArgumentException("Unknown decision code: " + code);
    }
}

