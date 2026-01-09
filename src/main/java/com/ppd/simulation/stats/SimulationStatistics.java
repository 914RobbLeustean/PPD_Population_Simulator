package com.ppd.simulation.stats;

import com.ppd.simulation.util.SimulationConfig;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate statistics for the entire simulation run.
 * Collects yearly statistics and computes performance metrics.
 */
public class SimulationStatistics {
    private final SimulationConfig config;
    private final List<YearStatistics> yearlyStats;
    private long simulationStartTime;
    private long simulationEndTime;
    private int peakPopulation;
    private int minPopulation;

    public SimulationStatistics(SimulationConfig config) {
        this.config = config;
        this.yearlyStats = Collections.synchronizedList(new ArrayList<>());
        this.peakPopulation = 0;
        this.minPopulation = Integer.MAX_VALUE;
    }

    public void recordSimulationStart() {
        simulationStartTime = System.nanoTime();
    }

    public void recordSimulationEnd() {
        simulationEndTime = System.nanoTime();
    }

    public void addYearStats(YearStatistics stats) {
        yearlyStats.add(stats);
        int pop = stats.getPopulation();
        if (pop > peakPopulation) peakPopulation = pop;
        if (pop < minPopulation) minPopulation = pop;
    }

    public List<YearStatistics> getYearlyStats() {
        return new ArrayList<>(yearlyStats);
    }

    //  Performance Metrics 

    public long getTotalSimulationTimeNs() {
        return simulationEndTime - simulationStartTime;
    }

    public double getTotalSimulationTimeMs() {
        return getTotalSimulationTimeNs() / 1_000_000.0;
    }

    public double getTotalSimulationTimeSec() {
        return getTotalSimulationTimeNs() / 1_000_000_000.0;
    }

    public double getAverageYearTimeMs() {
        if (yearlyStats.isEmpty()) return 0;
        long totalNs = 0;
        for (YearStatistics stats : yearlyStats) {
            totalNs += stats.getPhaseTimeNs();
        }
        return (totalNs / yearlyStats.size()) / 1_000_000.0;
    }

    public int getPeakPopulation() {
        return peakPopulation;
    }

    public int getMinPopulation() {
        return minPopulation;
    }

    public int getFinalPopulation() {
        if (yearlyStats.isEmpty()) return 0;
        return yearlyStats.get(yearlyStats.size() - 1).getPopulation();
    }

    public int getTotalBirths() {
        int total = 0;
        for (YearStatistics stats : yearlyStats) {
            total += stats.getBirths();
        }
        return total;
    }

    public int getTotalDeaths() {
        int total = 0;
        for (YearStatistics stats : yearlyStats) {
            total += stats.getDeaths();
        }
        return total;
    }

    /**
     * Calculate speedup compared to a baseline (single-threaded) time.
     */
    public double calculateSpeedup(double baselineTimeMs) {
        return baselineTimeMs / getTotalSimulationTimeMs();
    }

    /**
     * Calculate efficiency (speedup / thread count).
     */
    public double calculateEfficiency(double baselineTimeMs) {
        return calculateSpeedup(baselineTimeMs) / config.getThreadCount();
    }

    //  Reporting 

    public void printSummary() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("SIMULATION SUMMARY");
        System.out.println("=".repeat(70));
        System.out.println(config);
        System.out.println("-".repeat(70));
        System.out.printf("Total simulation time: %.2f seconds (%.2f ms)%n",
            getTotalSimulationTimeSec(), getTotalSimulationTimeMs());
        System.out.printf("Average year time: %.2f ms%n", getAverageYearTimeMs());
        System.out.printf("Years simulated: %d%n", yearlyStats.size());
        System.out.println("-".repeat(70));
        System.out.printf("Starting population: %d%n", config.getStartingPopulation());
        System.out.printf("Final population: %d%n", getFinalPopulation());
        System.out.printf("Peak population: %d%n", peakPopulation);
        System.out.printf("Minimum population: %d%n", minPopulation);
        System.out.printf("Total births: %d%n", getTotalBirths());
        System.out.printf("Total deaths: %d%n", getTotalDeaths());
        System.out.println("=".repeat(70));
    }

    public void printPhaseBreakdown() {
        if (yearlyStats.isEmpty()) return;

        long totalSensing = 0, totalDecision = 0, totalAction = 0;
        long totalMaintenance = 0, totalStats = 0;

        for (YearStatistics stats : yearlyStats) {
            totalSensing += stats.getSensingTimeNs();
            totalDecision += stats.getDecisionTimeNs();
            totalAction += stats.getActionTimeNs();
            totalMaintenance += stats.getMaintenanceTimeNs();
            totalStats += stats.getStatsTimeNs();
        }

        long totalPhases = totalSensing + totalDecision + totalAction + totalMaintenance + totalStats;
        
        System.out.println("\nPHASE TIMING BREAKDOWN:");
        System.out.println("-".repeat(50));
        System.out.printf("  Sensing:     %8.2f ms (%5.1f%%)%n", 
            totalSensing / 1_000_000.0, 100.0 * totalSensing / totalPhases);
        System.out.printf("  Decision:    %8.2f ms (%5.1f%%)%n", 
            totalDecision / 1_000_000.0, 100.0 * totalDecision / totalPhases);
        System.out.printf("  Action:      %8.2f ms (%5.1f%%)%n", 
            totalAction / 1_000_000.0, 100.0 * totalAction / totalPhases);
        System.out.printf("  Maintenance: %8.2f ms (%5.1f%%)%n", 
            totalMaintenance / 1_000_000.0, 100.0 * totalMaintenance / totalPhases);
        System.out.printf("  Statistics:  %8.2f ms (%5.1f%%)%n", 
            totalStats / 1_000_000.0, 100.0 * totalStats / totalPhases);
        System.out.println("-".repeat(50));
    }

    /**
     * Export statistics to CSV file.
     */
    public void exportToCsv(String filename) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println(YearStatistics.csvHeader());
            for (YearStatistics stats : yearlyStats) {
                writer.println(stats.toCsv());
            }
        }
        System.out.println("Statistics exported to: " + filename);
    }

    /**
     * Print population over time (simple ASCII graph).
     */
    public void printPopulationGraph() {
        if (yearlyStats.isEmpty()) return;

        System.out.println("\nPOPULATION OVER TIME:");
        System.out.println("-".repeat(70));

        int maxPop = peakPopulation;
        int graphWidth = 50;
        
        // Sample every N years for readability
        int step = Math.max(1, yearlyStats.size() / 25);
        
        for (int i = 0; i < yearlyStats.size(); i += step) {
            YearStatistics stats = yearlyStats.get(i);
            int barLen = maxPop > 0 ? (stats.getPopulation() * graphWidth / maxPop) : 0;
            String bar = "█".repeat(barLen);
            System.out.printf("Y%4d |%s %d%n", stats.getYear(), bar, stats.getPopulation());
        }
        System.out.println("-".repeat(70));
    }
}

