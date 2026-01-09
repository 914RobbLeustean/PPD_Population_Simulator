package com.ppd.simulation.engine;

/**
 * Enum representing the phases of each simulation year.
 * Each phase has a barrier synchronization point.
 */
public enum SimulationPhase {
    /**
     * Phase 1: Sensing
     * Each blob scans cells within vision radius to find food and other blobs.
     */
    SENSING(1, "Sensing"),

    /**
     * Phase 2: Decision
     * Each blob decides: REPRODUCE, MOVE_TO_FOOD, or MOVE_RANDOM.
     */
    DECISION(2, "Decision"),

    /**
     * Phase 3: Action Resolution
     * Movement, food consumption, and reproduction are executed.
     */
    ACTION(3, "Action"),

    /**
     * Phase 4: Maintenance
     * Metabolism applied, dead blobs removed, food spawned.
     */
    MAINTENANCE(4, "Maintenance"),

    /**
     * Phase 5: Statistics
     * Collect and record year statistics.
     */
    STATISTICS(5, "Statistics");

    private final int order;
    private final String name;

    SimulationPhase(int order, String name) {
        this.order = order;
        this.name = name;
    }

    public int getOrder() {
        return order;
    }

    public String getName() {
        return name;
    }

    /**
     * Get the next phase in sequence.
     */
    public SimulationPhase next() {
        SimulationPhase[] phases = values();
        int nextOrdinal = (this.ordinal() + 1) % phases.length;
        return phases[nextOrdinal];
    }

    /**
     * Check if this is the last phase of the year.
     */
    public boolean isLastPhase() {
        return this == STATISTICS;
    }
}

