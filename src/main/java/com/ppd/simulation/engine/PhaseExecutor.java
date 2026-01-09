package com.ppd.simulation.engine;

import com.ppd.simulation.model.*;
import com.ppd.simulation.stats.YearStatistics;
import com.ppd.simulation.threading.WorkerRegion;
import com.ppd.simulation.util.SimulationConfig;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Executes simulation phases for a specific worker thread.
 * Each worker is assigned a region of the grid and processes blobs in that region.
 * 
 * Design Notes for MPI Extension:
 * - This class can be adapted to work with MPI by:
 *   1. Using MPI_Barrier instead of CyclicBarrier for synchronization
 *   2. Exchanging border blob data before sensing phase
 *   3. Using MPI_Allreduce for global statistics
 * - The phase methods are designed to be self-contained and parallelizable
 */
public class PhaseExecutor {
    private final SimulationConfig config;
    private final Grid grid;
    private final WorkerRegion region;
    private final int workerId;
    
    // Shared structures for cross-thread coordination
    private final Map<Position, List<Blob>> cellOccupancy;
    private final List<Blob> newBorns;
    private final Set<Blob> processedForReproduction;

    public PhaseExecutor(SimulationConfig config, Grid grid, WorkerRegion region, int workerId,
                         Map<Position, List<Blob>> cellOccupancy, List<Blob> newBorns,
                         Set<Blob> processedForReproduction) {
        this.config = config;
        this.grid = grid;
        this.region = region;
        this.workerId = workerId;
        this.cellOccupancy = cellOccupancy;
        this.newBorns = newBorns;
        this.processedForReproduction = processedForReproduction;
    }

    /**
     * Get blobs that this worker is responsible for.
     * A worker is responsible for blobs currently in its region.
     */
    public List<Blob> getMyBlobs() {
        return grid.getBlobsInRegion(region.getStartX(), region.getEndX(),
                                      region.getStartY(), region.getEndY());
    }

    // Phase 1: Sensing

    /**
     * Execute sensing phase for all blobs in this worker's region.
     * Each blob scans for food and reproduction candidates within vision radius.
     */
    public void executeSensingPhase() {
        List<Blob> myBlobs = getMyBlobs();
        int visionRadius = config.getVisionRadius();

        for (Blob blob : myBlobs) {
            if (!blob.isAlive()) continue;

            blob.resetYearData();

            // Find nearest food
            Position nearestFood = grid.findNearestFood(blob.getPosition(), visionRadius);

            // Find nearest reproduction candidate (if this blob can reproduce)
            Blob nearestCandidate = null;
            if (blob.canReproduce()) {
                nearestCandidate = grid.findNearestReproductionCandidate(blob, visionRadius);
            }

            blob.setSensingResults(nearestFood, nearestCandidate);
        }
    }

    // Phase 2: Decision 

    /**
     * Execute decision phase for all blobs in this worker's region.
     * Each blob decides: REPRODUCE, MOVE_TO_FOOD, or MOVE_RANDOM.
     */
    public void executeDecisionPhase() {
        List<Blob> myBlobs = getMyBlobs();
        Random rng = ThreadLocalRandom.current();

        for (Blob blob : myBlobs) {
            if (!blob.isAlive()) continue;

            Decision decision;
            Position targetPosition = blob.getPosition();

            // Priority 1: Reproduce if possible - move toward partner
            if (blob.canReproduce() && blob.getNearestReproductionCandidate() != null) {
                Blob candidate = blob.getNearestReproductionCandidate();
                if (candidate.isAlive() && candidate.canReproduce()) {
                    decision = Decision.REPRODUCE;
                    // Move toward reproduction partner
                    if (blob.getPosition().equals(candidate.getPosition())) {
                        targetPosition = blob.getPosition(); // Already on same cell
                    } else {
                        Direction dir = Direction.toward(blob.getPosition(), candidate.getPosition());
                        targetPosition = calculateNewPosition(blob.getPosition(), dir);
                    }
                    blob.setDecision(decision, targetPosition);
                    continue;
                }
            }

            // Priority 2: Move to food if visible
            if (blob.getNearestFoodPosition() != null) {
                decision = Decision.MOVE_TO_FOOD;
                Direction dir = Direction.toward(blob.getPosition(), blob.getNearestFoodPosition());
                targetPosition = calculateNewPosition(blob.getPosition(), dir);
            }
            // Priority 3: Move randomly
            else {
                decision = Decision.MOVE_RANDOM;
                Direction dir = Direction.random(rng);
                targetPosition = calculateNewPosition(blob.getPosition(), dir);
            }

            blob.setDecision(decision, targetPosition);
        }
    }

    private Position calculateNewPosition(Position current, Direction dir) {
        Position newPos = current.move(dir.getDx(), dir.getDy());
        return grid.clampPosition(newPos);
    }

    //  Phase 3: Action Resolution 

    /**
     * Execute action phase: movement, food consumption, reproduction.
     * This phase requires synchronization for shared cell access.
     */
    public void executeActionPhase(YearStatistics stats) {
        executeMovement();
        executeFoodConsumption();
        executeReproduction(stats);
    }

    /**
     * Step 3a: Movement - move blobs to their target positions.
     */
    private void executeMovement() {
        List<Blob> myBlobs = getMyBlobs();

        for (Blob blob : myBlobs) {
            if (!blob.isAlive()) continue;

            Position target = blob.getTargetPosition();
            if (target != null && !target.equals(blob.getPosition())) {
                // Update cell occupancy atomically
                Position oldPos = blob.getPosition();
                
                // Remove from old cell
                Cell oldCell = grid.getCell(oldPos);
                if (oldCell != null) {
                    oldCell.lock();
                    try {
                        oldCell.removeBlob(blob);
                    } finally {
                        oldCell.unlock();
                    }
                }

                // Add to new cell
                Cell newCell = grid.getCell(target);
                if (newCell != null) {
                    newCell.lock();
                    try {
                        blob.setPosition(target);
                        newCell.addBlob(blob);
                    } finally {
                        newCell.unlock();
                    }
                }

                // Track for food consumption
                synchronized (cellOccupancy) {
                    cellOccupancy.computeIfAbsent(target, k -> 
                        Collections.synchronizedList(new ArrayList<>())).add(blob);
                }
            } else {
                // Blob stayed in place - still track for food consumption
                synchronized (cellOccupancy) {
                    cellOccupancy.computeIfAbsent(blob.getPosition(), k -> 
                        Collections.synchronizedList(new ArrayList<>())).add(blob);
                }
            }
        }
    }

    /**
     * Step 3b: Food Consumption - blobs on cells with food gain energy.
     */
    private void executeFoodConsumption() {
        List<Blob> myBlobs = getMyBlobs();
        int foodValue = config.getFoodEnergyValue();

        // Group blobs by their current cell
        Map<Position, List<Blob>> localCellMap = new HashMap<>();
        for (Blob blob : myBlobs) {
            if (!blob.isAlive()) continue;
            localCellMap.computeIfAbsent(blob.getPosition(), k -> new ArrayList<>()).add(blob);
        }

        // Process food consumption for each cell
        for (Map.Entry<Position, List<Blob>> entry : localCellMap.entrySet()) {
            Position pos = entry.getKey();
            List<Blob> blobsOnCell = entry.getValue();
            Cell cell = grid.getCell(pos);

            if (cell != null && cell.hasFood()) {
                cell.lock();
                try {
                    if (cell.consumeFood()) {
                        // Distribute food energy among all blobs on the cell
                        int energyPerBlob = foodValue / blobsOnCell.size();
                        for (Blob blob : blobsOnCell) {
                            blob.addEnergy(energyPerBlob);
                        }
                    }
                } finally {
                    cell.unlock();
                }
            }
        }
    }

    /**
     * Step 3c: Reproduction - pairs of blobs with energy > 150 create offspring.
     */
    private void executeReproduction(YearStatistics stats) {
        List<Blob> myBlobs = getMyBlobs();

        // Group reproducing blobs by cell
        Map<Position, List<Blob>> reproducingBlobsByCell = new HashMap<>();
        for (Blob blob : myBlobs) {
            if (!blob.isAlive()) continue;
            if (blob.getCurrentDecision() != Decision.REPRODUCE) continue;
            if (!blob.canReproduce()) continue;

            reproducingBlobsByCell.computeIfAbsent(blob.getPosition(), k -> new ArrayList<>()).add(blob);
        }

        // Process reproduction for each cell
        for (Map.Entry<Position, List<Blob>> entry : reproducingBlobsByCell.entrySet()) {
            Position pos = entry.getKey();
            List<Blob> candidates = entry.getValue();
            Cell cell = grid.getCell(pos);

            if (cell == null || candidates.size() < 2) continue;

            cell.lock();
            try {
                // Pair up candidates
                for (int i = 0; i + 1 < candidates.size(); i += 2) {
                    Blob parent1 = candidates.get(i);
                    Blob parent2 = candidates.get(i + 1);

                    // Check they haven't been processed already and can still reproduce
                    synchronized (processedForReproduction) {
                        if (processedForReproduction.contains(parent1) || 
                            processedForReproduction.contains(parent2)) {
                            continue;
                        }
                        if (!parent1.canReproduce() || !parent2.canReproduce()) {
                            continue;
                        }

                        // Create offspring
                        Blob offspring = new Blob(pos, config.getOffspringEnergy());
                        
                        // Parents lose energy
                        parent1.removeEnergy(config.getReproductionCost());
                        parent2.removeEnergy(config.getReproductionCost());

                        // Mark as processed
                        processedForReproduction.add(parent1);
                        processedForReproduction.add(parent2);

                        // Add to newborns list (will be added to grid after barrier)
                        synchronized (newBorns) {
                            newBorns.add(offspring);
                        }
                        stats.recordBirth();
                    }
                }
            } finally {
                cell.unlock();
            }
        }
    }

    //  Phase 4: Maintenance 

    /**
     * Execute maintenance phase: metabolism, death, food spawning.
     * Returns the number of deaths in this worker's region.
     */
    public int executeMaintenancePhase() {
        List<Blob> myBlobs = getMyBlobs();
        int metabolismCost = config.getMetabolismCost();
        int deaths = 0;

        // Apply metabolism to all blobs
        for (Blob blob : myBlobs) {
            if (!blob.isAlive()) continue;
            if (!blob.applyMetabolism(metabolismCost)) {
                deaths++;
            }
        }

        return deaths;
    }

    /**
     * Spawn food in this worker's region.
     * Returns number of food items spawned.
     */
    public int spawnFoodInRegion() {
        Random rng = ThreadLocalRandom.current();
        double spawnRate = config.getYearlyFoodSpawnPercentage();
        int spawned = 0;

        for (int y = region.getStartY(); y <= region.getEndY(); y++) {
            for (int x = region.getStartX(); x <= region.getEndX(); x++) {
                Cell cell = grid.getCell(x, y);
                if (cell != null && cell.canSpawnFood() && rng.nextDouble() < spawnRate) {
                    cell.spawnFood();
                    spawned++;
                }
            }
        }
        return spawned;
    }

    //  Phase 5: Statistics Collection 

    /**
     * Collect statistics for blobs in this worker's region.
     */
    public void collectRegionStatistics(YearStatistics stats) {
        List<Blob> myBlobs = getMyBlobs();
        long totalEnergy = 0;
        int livingCount = 0;

        for (Blob blob : myBlobs) {
            if (blob.isAlive()) {
                totalEnergy += blob.getEnergy();
                livingCount++;
            }
        }

        // Thread-safe updates to shared statistics
        stats.addEnergy((int) totalEnergy);
    }
}

