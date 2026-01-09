package com.ppd.simulation;

import com.ppd.simulation.engine.SimulationEngine;
import com.ppd.simulation.stats.SimulationStatistics;
import com.ppd.simulation.util.SimulationConfig;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Main entry point for the Population Simulation.
 * Supports command-line arguments for configuration.
 * 
 * Usage:
 *   java Main [options]
 * 
 * Options:
 *   --width N         Grid width (default: 1000)
 *   --height N        Grid height (default: 1000)
 *   --population N    Starting population (default: 500)
 *   --years N         Number of years to simulate (default: 100)
 *   --threads N       Number of worker threads (default: CPU cores)
 *   --seed N          Random seed (default: 0 = system time)
 *   --debug           Use small debug configuration
 *   --benchmark       Run benchmark with multiple thread counts
 *   --export FILE     Export statistics to CSV file
 */
public class Main {
    
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║        POPULATION SIMULATION - Multi-Threaded Edition        ║");
        System.out.println("║                   PPD End of Course Project                  ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        try {
            // Parse command line arguments
            CommandLineArgs cmdArgs = parseArgs(args);
            
            if (cmdArgs.benchmark) {
                runBenchmark(cmdArgs);
            } else {
                runSimulation(cmdArgs);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void runSimulation(CommandLineArgs cmdArgs) throws IOException {
        // Build configuration
        SimulationConfig.Builder builder = new SimulationConfig.Builder();
        
        if (cmdArgs.debug) {
            // Use debug configuration
            builder.gridSize(200, 200)
                   .startingPopulation(100)
                   .totalYears(50)
                   .threadCount(cmdArgs.threads > 0 ? cmdArgs.threads : 4);
        } else {
            builder.gridSize(cmdArgs.width, cmdArgs.height)
                   .startingPopulation(cmdArgs.population)
                   .totalYears(cmdArgs.years)
                   .threadCount(cmdArgs.threads > 0 ? cmdArgs.threads : 
                               Runtime.getRuntime().availableProcessors());
        }
        
        if (cmdArgs.seed > 0) {
            builder.randomSeed(cmdArgs.seed);
        }

        SimulationConfig config = builder.build();
        
        // Create and run simulation
        SimulationEngine engine = new SimulationEngine(config);
        engine.initialize();
        
        SimulationStatistics stats = engine.run();
        
        // Print results
        stats.printSummary();
        stats.printPhaseBreakdown();
        stats.printPopulationGraph();
        
        // Export to CSV if requested
        if (cmdArgs.exportFile != null) {
            stats.exportToCsv(cmdArgs.exportFile);
        }
    }

    private static void runBenchmark(CommandLineArgs cmdArgs) throws IOException {
        System.out.println("Running benchmark with multiple thread counts...\n");
        
        int[] threadCounts = {1, 2, 4, 8, 16};
        List<BenchmarkResult> results = new ArrayList<>();
        double baselineTime = 0;

        for (int threads : threadCounts) {
            if (threads > Runtime.getRuntime().availableProcessors() * 2) {
                break; // Skip if too many threads for this machine
            }

            System.out.println("\n" + "=".repeat(60));
            System.out.println("BENCHMARK: " + threads + " thread(s)");
            System.out.println("=".repeat(60));

            SimulationConfig config = new SimulationConfig.Builder()
                .gridSize(cmdArgs.width > 0 ? cmdArgs.width : 500,
                         cmdArgs.height > 0 ? cmdArgs.height : 500)
                .startingPopulation(cmdArgs.population > 0 ? cmdArgs.population : 300)
                .totalYears(cmdArgs.years > 0 ? cmdArgs.years : 50)
                .threadCount(threads)
                .randomSeed(42) // Fixed seed for reproducibility
                .build();

            SimulationEngine engine = new SimulationEngine(config);
            engine.initialize();
            
            SimulationStatistics stats = engine.run();
            
            double timeMs = stats.getTotalSimulationTimeMs();
            if (threads == 1) {
                baselineTime = timeMs;
            }
            
            double speedup = baselineTime / timeMs;
            double efficiency = speedup / threads;
            
            results.add(new BenchmarkResult(threads, timeMs, speedup, efficiency, 
                stats.getFinalPopulation()));
            
            System.out.printf("\n  Time: %.2f ms, Speedup: %.2fx, Efficiency: %.1f%%%n",
                timeMs, speedup, efficiency * 100);
        }

        // Print benchmark summary
        System.out.println("\n" + "=".repeat(70));
        System.out.println("BENCHMARK SUMMARY");
        System.out.println("=".repeat(70));
        System.out.println("Threads | Time (ms) | Speedup | Efficiency | Final Pop");
        System.out.println("-".repeat(70));
        for (BenchmarkResult result : results) {
            System.out.printf("%7d | %9.2f | %7.2fx | %9.1f%% | %9d%n",
                result.threads, result.timeMs, result.speedup, 
                result.efficiency * 100, result.finalPopulation);
        }
        System.out.println("=".repeat(70));
    }

    private static CommandLineArgs parseArgs(String[] args) {
        CommandLineArgs cmdArgs = new CommandLineArgs();
        
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--width":
                    cmdArgs.width = Integer.parseInt(args[++i]);
                    break;
                case "--height":
                    cmdArgs.height = Integer.parseInt(args[++i]);
                    break;
                case "--population":
                    cmdArgs.population = Integer.parseInt(args[++i]);
                    break;
                case "--years":
                    cmdArgs.years = Integer.parseInt(args[++i]);
                    break;
                case "--threads":
                    cmdArgs.threads = Integer.parseInt(args[++i]);
                    break;
                case "--seed":
                    cmdArgs.seed = Long.parseLong(args[++i]);
                    break;
                case "--debug":
                    cmdArgs.debug = true;
                    break;
                case "--benchmark":
                    cmdArgs.benchmark = true;
                    break;
                case "--export":
                    cmdArgs.exportFile = args[++i];
                    break;
                case "--help":
                case "-h":
                    printHelp();
                    System.exit(0);
                    break;
                default:
                    System.err.println("Unknown option: " + args[i]);
                    printHelp();
                    System.exit(1);
            }
        }
        
        // Apply defaults
        if (cmdArgs.width <= 0) cmdArgs.width = 1000;
        if (cmdArgs.height <= 0) cmdArgs.height = 1000;
        if (cmdArgs.population <= 0) cmdArgs.population = 500;
        if (cmdArgs.years <= 0) cmdArgs.years = 100;
        
        return cmdArgs;
    }

    private static void printHelp() {
        System.out.println("Usage: java Main [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --width N         Grid width (default: 1000)");
        System.out.println("  --height N        Grid height (default: 1000)");
        System.out.println("  --population N    Starting population (default: 500)");
        System.out.println("  --years N         Number of years to simulate (default: 100)");
        System.out.println("  --threads N       Number of worker threads (default: CPU cores)");
        System.out.println("  --seed N          Random seed (default: 0 = system time)");
        System.out.println("  --debug           Use small debug configuration (200x200, 100 pop, 50 years)");
        System.out.println("  --benchmark       Run benchmark with multiple thread counts");
        System.out.println("  --export FILE     Export statistics to CSV file");
        System.out.println("  --help, -h        Show this help message");
    }

    private static class CommandLineArgs {
        int width = -1;
        int height = -1;
        int population = -1;
        int years = -1;
        int threads = -1;
        long seed = 0;
        boolean debug = false;
        boolean benchmark = false;
        String exportFile = null;
    }

    private static class BenchmarkResult {
        final int threads;
        final double timeMs;
        final double speedup;
        final double efficiency;
        final int finalPopulation;

        BenchmarkResult(int threads, double timeMs, double speedup, double efficiency, int finalPopulation) {
            this.threads = threads;
            this.timeMs = timeMs;
            this.speedup = speedup;
            this.efficiency = efficiency;
            this.finalPopulation = finalPopulation;
        }
    }
}

