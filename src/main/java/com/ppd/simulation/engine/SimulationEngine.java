package com.ppd.simulation.engine;

import com.ppd.simulation.model.*;
import com.ppd.simulation.stats.SimulationStatistics;
import com.ppd.simulation.stats.YearStatistics;
import com.ppd.simulation.threading.WorkerRegion;
import com.ppd.simulation.util.SimulationConfig;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main simulation engine that coordinates multi-threaded execution.
 * Uses CyclicBarrier for phase synchronization.
 * 
 * Design Notes for MPI Extension:
 * - Replace CyclicBarrier with MPI_Barrier
 * - Replace shared memory structures with MPI collective operations
 * - Each MPI process runs its own SimulationEngine instance
 * - Add MPI send/recv for border data exchange before sensing
 * - Use MPI_Allreduce for global statistics aggregation
 */
public class SimulationEngine {
    private final SimulationConfig config;
    private final Grid grid;
    private final SimulationStatistics statistics;
    private final Random rng;

    // Threading infrastructure
    private final ExecutorService executor;
    private final WorkerRegion[] regions;
    private final CyclicBarrier phaseBarrier;
    
    // Shared data structures for cross-thread coordination
    private final Map<Position, List<Blob>> cellOccupancy;
    private final List<Blob> newBorns;
    private final Set<Blob> processedForReproduction;
    
    // Synchronization for statistics
    private final AtomicInteger totalDeaths;
    private final AtomicInteger totalFoodSpawned;
    
    // Current year tracking
    private volatile int currentYear;
    private volatile YearStatistics currentYearStats;

    public SimulationEngine(SimulationConfig config) {
        this.config = config;
        this.grid = new Grid(config.getGridWidth(), config.getGridHeight(), config.getVisionRadius());
        this.statistics = new SimulationStatistics(config);
        this.rng = config.getRandomSeed() == 0 ? 
            new Random() : new Random(config.getRandomSeed());

        // Initialize thread pool
        int threadCount = config.getThreadCount();
        this.executor = Executors.newFixedThreadPool(threadCount);
        
        // Create worker regions (horizontal strips for MPI compatibility)
        this.regions = WorkerRegion.createHorizontalStrips(
            config.getGridWidth(), config.getGridHeight(), threadCount);

        // Create barrier that waits for all threads
        this.phaseBarrier = new CyclicBarrier(threadCount);

        // Initialize shared data structures
        this.cellOccupancy = new ConcurrentHashMap<>();
        this.newBorns = Collections.synchronizedList(new ArrayList<>());
        this.processedForReproduction = ConcurrentHashMap.newKeySet();
        this.totalDeaths = new AtomicInteger(0);
        this.totalFoodSpawned = new AtomicInteger(0);

        // Log region assignments
        System.out.println("Worker regions created:");
        for (WorkerRegion region : regions) {
            System.out.println("  " + region);
        }
    }

    /**
     * Initialize the simulation with starting population and food.
     */
    public void initialize() {
        System.out.println("Initializing simulation...");
        
        // Spawn initial blobs
        grid.initializeBlobs(config.getStartingPopulation(), config.getStartingEnergy(), rng);
        System.out.printf("  Spawned %d blobs%n", grid.getBlobCount());

        // Spawn initial food
        int foodCount = grid.spawnInitialFood(config.getInitialFoodPercentage(), rng);
        System.out.printf("  Spawned %d food items (%.1f%% of grid)%n", 
            foodCount, 100.0 * foodCount / grid.getTotalCells());
    }

    /**
     * Run the complete simulation.
     */
    public SimulationStatistics run() {
        System.out.println("\nStarting simulation with " + config.getThreadCount() + " threads...\n");
        statistics.recordSimulationStart();

        try {
            for (int year = 1; year <= config.getTotalYears(); year++) {
                currentYear = year;
                runYear(year);
                
                // Check for extinction
                if (grid.getLivingBlobCount() == 0) {
                    System.out.println("\n*** EXTINCTION at year " + year + " ***");
                    break;
                }
            }
        } finally {
            executor.shutdown();
            try {
                executor.awaitTermination(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        statistics.recordSimulationEnd();
        return statistics;
    }

    /**
     * Run a single simulation year.
     */
    private void runYear(int year) {
        long yearStartTime = System.nanoTime();
        currentYearStats = new YearStatistics(year);
        
        // Reset shared state for new year
        cellOccupancy.clear();
        newBorns.clear();
        processedForReproduction.clear();
        totalDeaths.set(0);
        totalFoodSpawned.set(0);

        // Submit work for all threads
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < config.getThreadCount(); i++) {
            final int workerId = i;
            futures.add(executor.submit(() -> runWorkerYear(workerId)));
        }

        // Wait for all threads to complete
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("Error in worker thread: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Add newborns to grid (done by main thread after all workers finish)
        for (Blob newBorn : newBorns) {
            grid.addBlob(newBorn);
        }

        // Remove dead blobs from grid
        int removed = grid.removeDeadBlobs();

        // Finalize statistics
        currentYearStats.setPopulation(grid.getLivingBlobCount());
        currentYearStats.setFoodCount(grid.countFood());
        currentYearStats.setDeaths(totalDeaths.get());
        
        long yearEndTime = System.nanoTime();
        currentYearStats.setPhaseTime(yearEndTime - yearStartTime);
        
        statistics.addYearStats(currentYearStats);

        // Print progress
        if (year % 10 == 0 || year == 1 || year == config.getTotalYears()) {
            System.out.println(currentYearStats);
        }
    }

    /**
     * Run all phases for a single worker thread.
     */
    private void runWorkerYear(int workerId) {
        WorkerRegion region = regions[workerId];
        PhaseExecutor phaseExecutor = new PhaseExecutor(
            config, grid, region, workerId,
            cellOccupancy, newBorns, processedForReproduction
        );

        try {
            // Phase 1: Sensing
            long phaseStart = System.nanoTime();
            phaseExecutor.executeSensingPhase();
            awaitBarrier();
            if (workerId == 0) {
                currentYearStats.setSensingTime(System.nanoTime() - phaseStart);
            }

            // Phase 2: Decision
            phaseStart = System.nanoTime();
            phaseExecutor.executeDecisionPhase();
            awaitBarrier();
            if (workerId == 0) {
                currentYearStats.setDecisionTime(System.nanoTime() - phaseStart);
            }

            // Phase 3: Action Resolution
            phaseStart = System.nanoTime();
            phaseExecutor.executeActionPhase(currentYearStats);
            awaitBarrier();
            if (workerId == 0) {
                currentYearStats.setActionTime(System.nanoTime() - phaseStart);
            }

            // Phase 4: Maintenance
            phaseStart = System.nanoTime();
            int deaths = phaseExecutor.executeMaintenancePhase();
            totalDeaths.addAndGet(deaths);
            int foodSpawned = phaseExecutor.spawnFoodInRegion();
            totalFoodSpawned.addAndGet(foodSpawned);
            awaitBarrier();
            if (workerId == 0) {
                currentYearStats.setMaintenanceTime(System.nanoTime() - phaseStart);
            }

            // Phase 5: Statistics Collection
            phaseStart = System.nanoTime();
            phaseExecutor.collectRegionStatistics(currentYearStats);
            awaitBarrier();
            if (workerId == 0) {
                currentYearStats.setStatsTime(System.nanoTime() - phaseStart);
            }

        } catch (Exception e) {
            System.err.println("Worker " + workerId + " error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Wait at the barrier for all threads to synchronize.
     * Design Note for MPI: Replace with MPI_Barrier(MPI_COMM_WORLD)
     */
    private void awaitBarrier() {
        try {
            phaseBarrier.await();
        } catch (InterruptedException | BrokenBarrierException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Barrier synchronization failed", e);
        }
    }

    /**
     * Get the grid (for testing/visualization).
     */
    public Grid getGrid() {
        return grid;
    }

    /**
     * Get simulation statistics.
     */
    public SimulationStatistics getStatistics() {
        return statistics;
    }
}

