package com.ppd.simulation.util;

/**
 * Configuration parameters for the simulation.
 * Immutable after construction for thread safety.
 * 
 * Design Notes for MPI Extension:
 * - This configuration should be broadcast to all MPI processes at startup
 * - All processes must use identical configuration
 */
public final class SimulationConfig {
    // Grid settings
    private final int gridWidth;
    private final int gridHeight;
    
    // Population settings
    private final int startingPopulation;
    private final int startingEnergy;
    private final int reproductionThreshold;
    private final int reproductionCost;
    private final int offspringEnergy;
    private final int metabolismCost;
    private final int deathThreshold;
    
    // Food settings
    private final double initialFoodPercentage;
    private final double yearlyFoodSpawnPercentage;
    private final int foodEnergyValue;
    
    // Vision settings
    private final int visionRadius;
    
    // Simulation settings
    private final int totalYears;
    private final int threadCount;
    
    // Random seed (0 = use system time)
    private final long randomSeed;

    private SimulationConfig(Builder builder) {
        this.gridWidth = builder.gridWidth;
        this.gridHeight = builder.gridHeight;
        this.startingPopulation = builder.startingPopulation;
        this.startingEnergy = builder.startingEnergy;
        this.reproductionThreshold = builder.reproductionThreshold;
        this.reproductionCost = builder.reproductionCost;
        this.offspringEnergy = builder.offspringEnergy;
        this.metabolismCost = builder.metabolismCost;
        this.deathThreshold = builder.deathThreshold;
        this.initialFoodPercentage = builder.initialFoodPercentage;
        this.yearlyFoodSpawnPercentage = builder.yearlyFoodSpawnPercentage;
        this.foodEnergyValue = builder.foodEnergyValue;
        this.visionRadius = builder.visionRadius;
        this.totalYears = builder.totalYears;
        this.threadCount = builder.threadCount;
        this.randomSeed = builder.randomSeed;
    }

    //  Getters 

    public int getGridWidth() { return gridWidth; }
    public int getGridHeight() { return gridHeight; }
    public int getStartingPopulation() { return startingPopulation; }
    public int getStartingEnergy() { return startingEnergy; }
    public int getReproductionThreshold() { return reproductionThreshold; }
    public int getReproductionCost() { return reproductionCost; }
    public int getOffspringEnergy() { return offspringEnergy; }
    public int getMetabolismCost() { return metabolismCost; }
    public int getDeathThreshold() { return deathThreshold; }
    public double getInitialFoodPercentage() { return initialFoodPercentage; }
    public double getYearlyFoodSpawnPercentage() { return yearlyFoodSpawnPercentage; }
    public int getFoodEnergyValue() { return foodEnergyValue; }
    public int getVisionRadius() { return visionRadius; }
    public int getTotalYears() { return totalYears; }
    public int getThreadCount() { return threadCount; }
    public long getRandomSeed() { return randomSeed; }

    /**
     * Create a default configuration suitable for testing.
     */
    public static SimulationConfig defaultConfig() {
        return new Builder().build();
    }

    /**
     * Create a small configuration for quick debugging.
     */
    public static SimulationConfig debugConfig() {
        return new Builder()
            .gridSize(200, 200)
            .startingPopulation(100)
            .totalYears(50)
            .threadCount(4)
            .build();
    }

    /**
     * Create a full-scale configuration for performance testing.
     */
    public static SimulationConfig fullScaleConfig(int threads) {
        return new Builder()
            .gridSize(1000, 1000)
            .startingPopulation(500)
            .totalYears(100)
            .threadCount(threads)
            .build();
    }

    @Override
    public String toString() {
        return String.format(
            "SimulationConfig{\n" +
            "  Grid: %dx%d (%d cells)\n" +
            "  Population: %d (energy=%d)\n" +
            "  Reproduction: threshold=%d, cost=%d, offspring=%d\n" +
            "  Metabolism: cost=%d, death<=%d\n" +
            "  Food: initial=%.1f%%, yearly=%.1f%%, value=%d\n" +
            "  Vision radius: %d\n" +
            "  Years: %d, Threads: %d\n" +
            "  Seed: %d\n" +
            "}",
            gridWidth, gridHeight, gridWidth * gridHeight,
            startingPopulation, startingEnergy,
            reproductionThreshold, reproductionCost, offspringEnergy,
            metabolismCost, deathThreshold,
            initialFoodPercentage * 100, yearlyFoodSpawnPercentage * 100, foodEnergyValue,
            visionRadius,
            totalYears, threadCount,
            randomSeed
        );
    }

    /**
     * Serialize to int array for MPI broadcast.
     */
    public int[] toIntArray() {
        return new int[]{
            gridWidth, gridHeight,
            startingPopulation, startingEnergy,
            reproductionThreshold, reproductionCost, offspringEnergy,
            metabolismCost, deathThreshold,
            (int)(initialFoodPercentage * 10000),
            (int)(yearlyFoodSpawnPercentage * 10000),
            foodEnergyValue,
            visionRadius,
            totalYears, threadCount
        };
    }

    //  Builder 

    public static class Builder {
        private int gridWidth = 1000;
        private int gridHeight = 1000;
        private int startingPopulation = 500;
        private int startingEnergy = 100;
        private int reproductionThreshold = 150;
        private int reproductionCost = 50;
        private int offspringEnergy = 100;
        private int metabolismCost = 20;
        private int deathThreshold = 0;
        private double initialFoodPercentage = 0.05;
        private double yearlyFoodSpawnPercentage = 0.05;
        private int foodEnergyValue = 100;
        private int visionRadius = 5;
        private int totalYears = 100;
        private int threadCount = Runtime.getRuntime().availableProcessors();
        private long randomSeed = 0;

        public Builder gridSize(int width, int height) {
            this.gridWidth = width;
            this.gridHeight = height;
            return this;
        }

        public Builder startingPopulation(int pop) {
            this.startingPopulation = pop;
            return this;
        }

        public Builder startingEnergy(int energy) {
            this.startingEnergy = energy;
            return this;
        }

        public Builder reproductionThreshold(int threshold) {
            this.reproductionThreshold = threshold;
            return this;
        }

        public Builder reproductionCost(int cost) {
            this.reproductionCost = cost;
            return this;
        }

        public Builder offspringEnergy(int energy) {
            this.offspringEnergy = energy;
            return this;
        }

        public Builder metabolismCost(int cost) {
            this.metabolismCost = cost;
            return this;
        }

        public Builder initialFoodPercentage(double pct) {
            this.initialFoodPercentage = pct;
            return this;
        }

        public Builder yearlyFoodSpawnPercentage(double pct) {
            this.yearlyFoodSpawnPercentage = pct;
            return this;
        }

        public Builder foodEnergyValue(int value) {
            this.foodEnergyValue = value;
            return this;
        }

        public Builder visionRadius(int radius) {
            this.visionRadius = radius;
            return this;
        }

        public Builder totalYears(int years) {
            this.totalYears = years;
            return this;
        }

        public Builder threadCount(int threads) {
            this.threadCount = threads;
            return this;
        }

        public Builder randomSeed(long seed) {
            this.randomSeed = seed;
            return this;
        }

        public SimulationConfig build() {
            return new SimulationConfig(this);
        }
    }
}

