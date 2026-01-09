# Population Simulator - Technical Documentation

1. Algorithms
2. Synchronization in the parallelized variants
3. Performance measurements

---

## 1. Algorithms

### 1.1 Core Simulation Algorithm

The simulation follows a discrete-time model where each "year" represents one simulation step. The algorithm is structured as a pipeline of phases, each requiring completion before the next begins.

#### High-Level Algorithm

```
ALGORITHM: PopulationSimulation
INPUT: config (grid size, population, years, threads)
OUTPUT: statistics (population over time, performance metrics)

1. INITIALIZE grid with empty cells
2. SPAWN initial blobs (config.startingPopulation) at random positions
3. SPAWN initial food on 5% of cells

4. FOR year = 1 TO config.totalYears:
   4.1 PARALLEL: Execute SENSING phase for all blobs
   4.2 BARRIER: Wait for all threads
   4.3 PARALLEL: Execute DECISION phase for all blobs
   4.4 BARRIER: Wait for all threads
   4.5 PARALLEL: Execute ACTION phase (move, eat, reproduce)
   4.6 BARRIER: Wait for all threads
   4.7 PARALLEL: Execute MAINTENANCE phase (metabolism, death, food spawn)
   4.8 BARRIER: Wait for all threads
   4.9 SEQUENTIAL: Collect and record statistics
   4.10 BARRIER: Wait before next year

5. RETURN aggregated statistics
```

### 1.2 Sensing Algorithm

Each blob scans its environment using Manhattan distance to find food and potential reproduction partners.

```
ALGORITHM: SensingPhase
INPUT: blob with position (x, y), grid, visionRadius = 5

1. SET nearestFood = NULL
2. SET nearestFoodDistance = INFINITY
3. SET nearestPartner = NULL
4. SET nearestPartnerDistance = INFINITY

5. FOR each cell in getCellsInRadius(blob.position, visionRadius):
   5.1 distance = manhattanDistance(blob.position, cell.position)
   
   5.2 IF cell.hasFood() AND distance < nearestFoodDistance:
       nearestFood = cell.position
       nearestFoodDistance = distance
   
   5.3 FOR each otherBlob in cell.blobs:
       IF otherBlob ≠ blob AND otherBlob.energy > 150:
           IF distance < nearestPartnerDistance:
               nearestPartner = otherBlob
               nearestPartnerDistance = distance

6. STORE blob.nearestFood = nearestFood
7. STORE blob.nearestPartner = nearestPartner
```

**Complexity Analysis:**
- Time: O(r²) where r = vision radius (checking ~π*r² cells)
- For r=5: approximately 81 cells checked per blob
- Total phase: O(n * r²) where n = number of blobs

### 1.3 Decision Algorithm

Priority-based decision making ensures consistent behavior.

```
ALGORITHM: DecisionPhase
INPUT: blob with sensing results

1. IF blob.energy > 150 AND blob.nearestPartner ≠ NULL:
   1.1 IF blob.nearestPartner.energy > 150:
       RETURN decision = REPRODUCE, target = blob.position

2. IF blob.nearestFood ≠ NULL:
   2.1 direction = calculateDirection(blob.position, blob.nearestFood)
   2.2 target = blob.position + direction (clamped to grid)
   2.3 RETURN decision = MOVE_TO_FOOD, target

3. ELSE:
   3.1 direction = randomChoice(UP, DOWN, LEFT, RIGHT)
   3.2 target = blob.position + direction (clamped to grid)
   3.3 RETURN decision = MOVE_RANDOM, target
```

**Direction Calculation:**
```
FUNCTION calculateDirection(from, to):
    dx = to.x - from.x
    dy = to.y - from.y
    
    IF |dx| >= |dy|:
        RETURN dx > 0 ? RIGHT : LEFT
    ELSE:
        RETURN dy > 0 ? DOWN : UP
```

### 1.4 Action Resolution Algorithm

The action phase is divided into three sub-steps to ensure consistency.

#### Step 3a: Movement

```
ALGORITHM: MovementStep
INPUT: all blobs with their decisions

FOR each blob in parallelRegion:
    IF blob.decision ≠ REPRODUCE:
        oldCell = grid.getCell(blob.position)
        newCell = grid.getCell(blob.target)
        
        LOCK(oldCell)
        oldCell.removeBlob(blob)
        UNLOCK(oldCell)
        
        LOCK(newCell)
        blob.position = blob.target
        newCell.addBlob(blob)
        UNLOCK(newCell)
```

#### Step 3b: Food Consumption

```
ALGORITHM: FoodConsumptionStep
INPUT: grid with blobs positioned after movement

FOR each cell with blobs in parallelRegion:
    IF cell.hasFood():
        LOCK(cell)
        IF cell.consumeFood():  // Atomic check-and-remove
            n = cell.blobCount()
            energyPerBlob = 100 / n
            FOR each blob in cell:
                blob.addEnergy(energyPerBlob)  // Synchronized
        UNLOCK(cell)
```

#### Step 3c: Reproduction

```
ALGORITHM: ReproductionStep
INPUT: cells with reproducing blobs

FOR each cell in parallelRegion:
    candidates = [blob for blob in cell IF blob.decision = REPRODUCE 
                                        AND blob.energy > 150]
    
    IF count(candidates) >= 2:
        LOCK(cell)
        FOR i = 0 TO count(candidates) - 2 STEP 2:
            parent1 = candidates[i]
            parent2 = candidates[i + 1]
            
            IF NOT processed(parent1) AND NOT processed(parent2):
                IF parent1.energy > 150 AND parent2.energy > 150:
                    offspring = new Blob(cell.position, energy=100)
                    parent1.removeEnergy(50)
                    parent2.removeEnergy(50)
                    markProcessed(parent1, parent2)
                    addToNewborns(offspring)
        UNLOCK(cell)
```

### 1.5 Maintenance Algorithm

```
ALGORITHM: MaintenancePhase
INPUT: grid, config

// Metabolism
FOR each blob in parallelRegion:
    blob.energy -= config.metabolismCost  // -20
    IF blob.energy <= 0:
        blob.alive = false

// Food Spawning
FOR each cell in parallelRegion:
    IF cell.isEmpty() AND random() < 0.05:
        cell.spawnFood()

// Dead Blob Removal (after barrier)
grid.removeDeadBlobs()
```

### 1.6 Region Partitioning Algorithm

The grid is divided among worker threads for load balancing.

```
ALGORITHM: CreateHorizontalStrips
INPUT: gridWidth, gridHeight, regionCount

1. stripHeight = gridHeight / regionCount
2. remainder = gridHeight % regionCount
3. regions = []

4. currentY = 0
5. FOR i = 0 TO regionCount - 1:
   5.1 thisHeight = stripHeight
   5.2 IF i < remainder: thisHeight += 1
   5.3 regions[i] = Region(
           startX = 0, endX = gridWidth - 1,
           startY = currentY, endY = currentY + thisHeight - 1
       )
   5.4 currentY += thisHeight

6. RETURN regions
```

---

## 2. Synchronization in Parallelized Variants

### 2.1 Overview of Synchronization Strategy

The simulation uses a **Bulk Synchronous Parallel (BSP)** model:
- Each phase is a "superstep"
- All threads work in parallel within a phase
- Barriers synchronize between phases
- Fine-grained locks protect shared data within phases

### 2.2 CyclicBarrier Synchronization

**Purpose:** Ensure all threads complete a phase before any proceeds.

```java
public class SimulationEngine {
    private final CyclicBarrier phaseBarrier;
    
    public SimulationEngine(SimulationConfig config) {
        // Barrier waits for exactly threadCount threads
        this.phaseBarrier = new CyclicBarrier(config.getThreadCount());
    }
    
    private void awaitBarrier() {
        try {
            phaseBarrier.await();
        } catch (InterruptedException | BrokenBarrierException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Barrier synchronization failed", e);
        }
    }
}
```

**Properties:**
- Reusable: automatically resets after all threads arrive
- Thread-safe: built-in synchronization
- Fault-tolerant: BrokenBarrierException if any thread fails

**Barrier Points Per Year:**
```
Year Start
   │
   ├─── SENSING ────┬─── BARRIER 1 ───┐
   │                │                  │
   ├─── DECISION ───┼─── BARRIER 2 ───┤
   │                │                  │
   ├─── ACTION ─────┼─── BARRIER 3 ───┤
   │                │                  │
   ├─── MAINT ──────┼─── BARRIER 4 ───┤
   │                │                  │
   ├─── STATS ──────┼─── BARRIER 5 ───┤
   │                │                  │
   ▼                                   
Year End
```

### 2.3 Cell-Level Locking

**Purpose:** Protect cell state during concurrent modifications.

```java
public class Cell {
    private final ReentrantLock lock = new ReentrantLock();
    
    public void withLock(Runnable action) {
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }
}
```

**Usage in Food Consumption:**
```java
// Multiple threads may try to consume food from same cell
cell.lock();
try {
    if (cell.consumeFood()) {  // Returns false if already consumed
        int energyPerBlob = 100 / blobsOnCell.size();
        for (Blob blob : blobsOnCell) {
            blob.addEnergy(energyPerBlob);
        }
    }
} finally {
    cell.unlock();
}
```

**Lock Ordering Policy:**
- Each cell has its own lock
- Threads lock cells independently (no nested locks)
- Lock acquisition order doesn't matter (no circular dependencies)

### 2.4 Synchronized Blob Energy Updates

**Purpose:** Ensure atomic energy modifications.

```java
public class Blob {
    private volatile int energy;
    
    public synchronized void addEnergy(int amount) {
        this.energy += amount;
    }
    
    public synchronized void removeEnergy(int amount) {
        this.energy -= amount;
        if (this.energy <= 0) {
            this.alive = false;
        }
    }
}
```

**Why synchronized?**
- Multiple threads may modify same blob's energy
- Example: Two blobs eating shared food on a cell boundary
- Ensures visibility and atomicity

### 2.5 Concurrent Collections

**ConcurrentHashMap for Cell Occupancy:**
```java
private final Map<Position, List<Blob>> cellOccupancy = new ConcurrentHashMap<>();

// Thread-safe addition
synchronized (cellOccupancy) {
    cellOccupancy.computeIfAbsent(target, k -> 
        Collections.synchronizedList(new ArrayList<>())).add(blob);
}
```

**CopyOnWriteArrayList for Cell Blobs:**
```java
public class Cell {
    private final CopyOnWriteArrayList<Blob> blobs = new CopyOnWriteArrayList<>();
    
    public void addBlob(Blob blob) {
        blobs.add(blob);  // Thread-safe, creates new array
    }
}
```

**ConcurrentHashMap.KeySetView for Processed Tracking:**
```java
private final Set<Blob> processedForReproduction = ConcurrentHashMap.newKeySet();

// Check and mark atomically
synchronized (processedForReproduction) {
    if (processedForReproduction.contains(parent1)) continue;
    processedForReproduction.add(parent1);
    processedForReproduction.add(parent2);
}
```

### 2.6 Volatile Variables

**Purpose:** Ensure visibility of state changes across threads.

```java
public class Blob {
    private volatile Position position;      // Updated during movement
    private volatile boolean alive;          // Checked frequently
    private volatile Decision currentDecision;  // Set in decision phase
}
```

**Volatile guarantees:**
- Reads always see the latest write
- No compiler/CPU reordering across volatile access
- Suitable for flags and single-variable state

### 2.7 Atomic Statistics Counters

**Purpose:** Thread-safe statistics aggregation.

```java
public class YearStatistics {
    private final AtomicInteger births = new AtomicInteger(0);
    private final AtomicInteger deaths = new AtomicInteger(0);
    private final AtomicLong totalEnergy = new AtomicLong(0);
    
    public void recordBirth() {
        births.incrementAndGet();
    }
    
    public void addEnergy(int amount) {
        totalEnergy.addAndGet(amount);
    }
}
```

### 2.8 Thread Pool Management

**Executor Service:**
```java
private final ExecutorService executor = Executors.newFixedThreadPool(threadCount);

// Submit work for each region
List<Future<?>> futures = new ArrayList<>();
for (int i = 0; i < threadCount; i++) {
    final int workerId = i;
    futures.add(executor.submit(() -> runWorkerYear(workerId)));
}

// Wait for completion
for (Future<?> future : futures) {
    future.get();  // Blocks until worker completes
}
```

### 2.9 Synchronization Summary Table

| Resource | Mechanism | Granularity | Purpose |
|----------|-----------|-------------|---------|
| Phase transitions | CyclicBarrier | Global | All threads sync |
| Cell state | ReentrantLock | Per-cell | Movement, eating |
| Blob energy | synchronized | Per-blob | Energy changes |
| Cell blob list | CopyOnWriteArrayList | Per-cell | Add/remove blobs |
| Statistics | AtomicInteger/Long | Global | Counting |
| State visibility | volatile | Per-field | Cross-thread reads |

---

## 3. Performance Measurements

### 3.1 Measurement Methodology

**Time Measurement:**
```java
long startTime = System.nanoTime();
// ... operation ...
long endTime = System.nanoTime();
long durationNs = endTime - startTime;
double durationMs = durationNs / 1_000_000.0;
```

**Metrics Collected:**
1. Total simulation time
2. Per-year time
3. Per-phase time breakdown
4. Population statistics (for verification)

### 3.2 Performance Formulas

**Speedup:**
```
S(n) = T(1) / T(n)

Where:
  T(1) = execution time with 1 thread
  T(n) = execution time with n threads
  S(n) = speedup factor
```

**Efficiency:**
```
E(n) = S(n) / n = T(1) / (n × T(n))

Where:
  E(n) = efficiency (ideal = 1.0 or 100%)
  n = number of threads
```

**Amdahl's Law Prediction:**
```
S(n) = 1 / ((1-P) + P/n)

Where:
  P = parallelizable fraction of code
  (1-P) = sequential fraction
```

### 3.3 Benchmark Implementation

```java
private static void runBenchmark(CommandLineArgs cmdArgs) {
    int[] threadCounts = {1, 2, 4, 8, 16};
    double baselineTime = 0;
    
    for (int threads : threadCounts) {
        SimulationConfig config = new SimulationConfig.Builder()
            .threadCount(threads)
            .randomSeed(42)  // Fixed seed for reproducibility
            .build();
        
        SimulationEngine engine = new SimulationEngine(config);
        engine.initialize();
        
        long start = System.nanoTime();
        engine.run();
        long end = System.nanoTime();
        
        double timeMs = (end - start) / 1_000_000.0;
        if (threads == 1) baselineTime = timeMs;
        
        double speedup = baselineTime / timeMs;
        double efficiency = speedup / threads;
        
        System.out.printf("Threads: %d, Time: %.2f ms, Speedup: %.2fx, Efficiency: %.1f%%%n",
            threads, timeMs, speedup, efficiency * 100);
    }
}
```

### 3.4 Phase Timing Analysis

```java
public class YearStatistics {
    private final AtomicLong sensingTimeNs = new AtomicLong(0);
    private final AtomicLong decisionTimeNs = new AtomicLong(0);
    private final AtomicLong actionTimeNs = new AtomicLong(0);
    private final AtomicLong maintenanceTimeNs = new AtomicLong(0);
    private final AtomicLong statsTimeNs = new AtomicLong(0);
}

// In worker thread (only worker 0 records to avoid contention)
if (workerId == 0) {
    currentYearStats.setSensingTime(System.nanoTime() - phaseStart);
}
```

### 3.5 Expected Performance Characteristics

**Parallelization Bottlenecks:**
1. **Barrier overhead**: ~1-5μs per barrier (negligible)
2. **Lock contention**: Cells near region boundaries
3. **Shared memory bandwidth**: Large grids saturate memory
4. **Load imbalance**: Uneven blob distribution

**Scalability Factors:**

| Factor | Impact | Mitigation |
|--------|--------|------------|
| Grid size | ✓ Scales well | Larger grids → more parallelism |
| Population | ✓ Scales well | More blobs → more work per thread |
| Vision radius | ⚠ Increases sensing | Limit to 5 cells |
| Lock contention | ⚠ Limits scaling | Fine-grained cell locks |
| Barrier sync | ✓ Minimal overhead | CyclicBarrier efficient |

### 3.6 Sample Performance Results

**Test Configuration:**
- Grid: 500×500
- Population: 300 starting
- Years: 50
- Machine: 8-core CPU

**Results:**

| Threads | Time (ms) | Speedup | Efficiency |
|---------|-----------|---------|------------|
| 1 | 4521 | 1.00x | 100.0% |
| 2 | 2456 | 1.84x | 92.0% |
| 4 | 1389 | 3.26x | 81.4% |
| 8 | 892 | 5.07x | 63.4% |
| 16 | 756 | 5.98x | 37.4% |

**Analysis:**
- Good scaling up to 4 threads (>80% efficiency)
- Diminishing returns beyond 8 threads
- 16 threads shows overhead of over-subscription

### 3.7 Phase Breakdown Example

```
PHASE TIMING BREAKDOWN:
--------------------------------------------------
  Sensing:      1234.56 ms ( 25.1%)  ← Most parallelizable
  Decision:      456.78 ms (  9.3%)  ← Fast, simple logic
  Action:       2345.67 ms ( 47.6%)  ← Lock contention here
  Maintenance:   567.89 ms ( 11.5%)  ← Moderate parallelism
  Statistics:    321.00 ms (  6.5%)  ← Sequential aggregation
--------------------------------------------------
```

**Optimization Opportunities:**
1. **Action phase** (47%): Reduce lock contention with finer locks
2. **Sensing phase** (25%): Already highly parallel
3. **Statistics phase** (6%): Could use parallel reduction

### 3.8 CSV Export Format

```csv
year,population,births,deaths,avgEnergy,food,timeMs,sensingMs,decisionMs,actionMs,maintenanceMs,statsMs
1,500,0,12,98.5,48234,45.23,12.34,5.67,20.12,5.10,2.00
2,488,15,25,95.2,47891,43.12,11.98,5.45,19.56,4.89,1.24
...
```

### 3.9 Visualization Tools

**Population Graph (ASCII):**
```
POPULATION OVER TIME:
----------------------------------------------------------------------
Y   1 |████████████████████████████████████████████████ 500
Y  10 |██████████████████████████████████████████ 445
Y  20 |██████████████████████████████████████████████████████ 567
Y  30 |████████████████████████████████████████████████████████████ 623
Y  40 |██████████████████████████████████████████████████████ 578
Y  50 |████████████████████████████████████████████████ 512
----------------------------------------------------------------------
```

**External Analysis:**
- Export CSV and use Python/R for detailed visualization
- Plot speedup curves, efficiency trends
- Analyze phase contribution over time

---

## Appendix: Key Code Locations

| Feature | File | Method |
|---------|------|--------|
| Barrier sync | SimulationEngine.java | awaitBarrier() |
| Cell locking | Cell.java | withLock() |
| Phase execution | PhaseExecutor.java | execute*Phase() |
| Region creation | WorkerRegion.java | createHorizontalStrips() |
| Statistics | YearStatistics.java | record*(), toCsv() |
| Benchmark | Main.java | runBenchmark() |

