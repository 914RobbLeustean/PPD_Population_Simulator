package com.ppd.simulation.stats;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Statistics collected for a single simulation year.
 * Thread-safe for concurrent updates during parallel phases.
 */
public class YearStatistics {
    private final int year;
    private final AtomicInteger population;
    private final AtomicInteger births;
    private final AtomicInteger deaths;
    private final AtomicLong totalEnergy;
    private final AtomicInteger foodCount;
    private final AtomicLong phaseTimeNs;
    
    // Phase timing breakdown
    private final AtomicLong sensingTimeNs;
    private final AtomicLong decisionTimeNs;
    private final AtomicLong actionTimeNs;
    private final AtomicLong maintenanceTimeNs;
    private final AtomicLong statsTimeNs;

    public YearStatistics(int year) {
        this.year = year;
        this.population = new AtomicInteger(0);
        this.births = new AtomicInteger(0);
        this.deaths = new AtomicInteger(0);
        this.totalEnergy = new AtomicLong(0);
        this.foodCount = new AtomicInteger(0);
        this.phaseTimeNs = new AtomicLong(0);
        this.sensingTimeNs = new AtomicLong(0);
        this.decisionTimeNs = new AtomicLong(0);
        this.actionTimeNs = new AtomicLong(0);
        this.maintenanceTimeNs = new AtomicLong(0);
        this.statsTimeNs = new AtomicLong(0);
    }

    // ============ Atomic Updates ============

    public void recordBirth() {
        births.incrementAndGet();
    }

    public void recordBirths(int count) {
        births.addAndGet(count);
    }

    public void recordDeath() {
        deaths.incrementAndGet();
    }

    public void recordDeaths(int count) {
        deaths.addAndGet(count);
    }

    public void setDeaths(int count) {
        deaths.set(count);
    }

    public void setPopulation(int pop) {
        population.set(pop);
    }

    public void setTotalEnergy(long energy) {
        totalEnergy.set(energy);
    }

    public void addEnergy(int energy) {
        totalEnergy.addAndGet(energy);
    }

    public void setFoodCount(int count) {
        foodCount.set(count);
    }

    public void setPhaseTime(long ns) {
        phaseTimeNs.set(ns);
    }

    public void setSensingTime(long ns) {
        sensingTimeNs.set(ns);
    }

    public void setDecisionTime(long ns) {
        decisionTimeNs.set(ns);
    }

    public void setActionTime(long ns) {
        actionTimeNs.set(ns);
    }

    public void setMaintenanceTime(long ns) {
        maintenanceTimeNs.set(ns);
    }

    public void setStatsTime(long ns) {
        statsTimeNs.set(ns);
    }

    // ============ Getters ============

    public int getYear() { return year; }
    public int getPopulation() { return population.get(); }
    public int getBirths() { return births.get(); }
    public int getDeaths() { return deaths.get(); }
    public long getTotalEnergy() { return totalEnergy.get(); }
    public int getFoodCount() { return foodCount.get(); }

    public double getAverageEnergy() {
        int pop = population.get();
        return pop > 0 ? (double) totalEnergy.get() / pop : 0;
    }

    public long getPhaseTimeNs() { return phaseTimeNs.get(); }
    public double getPhaseTimeMs() { return phaseTimeNs.get() / 1_000_000.0; }

    public long getSensingTimeNs() { return sensingTimeNs.get(); }
    public long getDecisionTimeNs() { return decisionTimeNs.get(); }
    public long getActionTimeNs() { return actionTimeNs.get(); }
    public long getMaintenanceTimeNs() { return maintenanceTimeNs.get(); }
    public long getStatsTimeNs() { return statsTimeNs.get(); }

    @Override
    public String toString() {
        return String.format(
            "Year %4d: pop=%5d (+%3d/-%3d), avgEnergy=%6.1f, food=%6d, time=%.2fms",
            year, population.get(), births.get(), deaths.get(),
            getAverageEnergy(), foodCount.get(), getPhaseTimeMs()
        );
    }

    /**
     * Generate CSV header for statistics export.
     */
    public static String csvHeader() {
        return "year,population,births,deaths,avgEnergy,food,timeMs,sensingMs,decisionMs,actionMs,maintenanceMs,statsMs";
    }

    /**
     * Generate CSV row for this year's statistics.
     */
    public String toCsv() {
        return String.format("%d,%d,%d,%d,%.2f,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f",
            year, population.get(), births.get(), deaths.get(),
            getAverageEnergy(), foodCount.get(),
            getPhaseTimeMs(),
            sensingTimeNs.get() / 1_000_000.0,
            decisionTimeNs.get() / 1_000_000.0,
            actionTimeNs.get() / 1_000_000.0,
            maintenanceTimeNs.get() / 1_000_000.0,
            statsTimeNs.get() / 1_000_000.0
        );
    }
}

