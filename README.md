# PPD Population Simulator

A multi-threaded population simulation where blobs compete for resources, reproduce, and survive on a 2D grid. This project is designed for the PPD (Parallel and Distributed Programming) End of Course collaborative project.

## Table of Contents

1. [Overview](#overview)
2. [Quick Start](#quick-start)
3. [Architecture](#architecture)
4. [Algorithm Description](#algorithm-description)
5. [Synchronization Mechanisms](#synchronization-mechanisms)
6. [Performance Measurements](#performance-measurements)
7. [MPI Extension Guide](#mpi-extension-guide)
8. [Configuration Options](#configuration-options)

---

## Overview

This simulation models a population of entities ("blobs") on a 1000×1000 grid. Each blob has an energy level and can:
- **Sense** their environment within a 5-cell vision radius
- **Decide** to reproduce, seek food, or move randomly
- **Act** on their decisions
- **Survive** by maintaining positive energy through metabolism

The simulation runs in discrete time steps (years), with each year consisting of 5 synchronized phases.

### Key Features

- **Multi-threaded execution** with configurable thread count
- **Phase-based synchronization** using CyclicBarrier
- **Region-based work distribution** for load balancing
- **Extensible design** prepared for MPI distribution
- **Comprehensive statistics** and performance metrics

---

## Quick Start

### Prerequisites

- Java 11 or higher
- (Optional) Maven 3.6+

### Building

**Using the build script (no Maven required):**

```bash
chmod +x build.sh
./build.sh
```

**Using Maven:**

```bash
mvn clean package
```

### Running

**Basic run:**

```bash
java -jar build/population-simulator.jar
```

**Debug mode (smaller grid, faster iteration):**

```bash
java -jar build/population-simulator.jar --debug
```

**Custom configuration:**

```bash
java -jar build/population-simulator.jar --threads 8 --years 100 --population 500
```

**Performance benchmark:**

```bash
java -jar build/population-simulator.jar --benchmark
```

---

## Architecture

### Project Structure

```
src/main/java/com/ppd/simulation/
├── Main.java                 # Entry point with CLI parsing
├── model/
│   ├── Position.java         # Immutable coordinate class
│   ├── Direction.java        # Movement directions (UP/DOWN/LEFT/RIGHT)
│   ├── Decision.java         # Blob decision enum
│   ├── Blob.java             # Entity with energy and behavior
│   ├── Cell.java             # Grid cell with food and blob management
│   └── Grid.java             # 2D grid with spatial queries
├── engine/
│   ├── SimulationPhase.java  # Phase enumeration
│   ├── PhaseExecutor.java    # Per-worker phase execution
│   └── SimulationEngine.java # Main simulation orchestrator
├── threading/
│   └── WorkerRegion.java     # Grid region assignment
├── stats/
│   ├── YearStatistics.java   # Per-year statistics
│   └── SimulationStatistics.java # Aggregate statistics
└── util/
    └── SimulationConfig.java # Configuration parameters
```

### Class Diagram

```
┌─────────────────┐     ┌──────────────────┐
│ SimulationEngine│────▶│ Grid             │
│                 │     │ - cells[y][x]    │
│ - phaseBarrier  │     │ - allBlobs       │
│ - executor      │     └──────────────────┘
│ - regions[]     │              │
└─────────────────┘              ▼
        │              ┌──────────────────┐
        │              │ Cell             │
        ▼              │ - position       │
┌─────────────────┐    │ - hasFood        │
│ PhaseExecutor   │    │ - blobs[]        │
│ - region        │    │ - lock           │
│ - workerId      │    └──────────────────┘
└─────────────────┘              │
                                 ▼
                       ┌──────────────────┐
                       │ Blob             │
                       │ - id, position   │
                       │ - energy         │
                       │ - decision       │
                       └──────────────────┘
```

---

## Algorithm Description

### Year Structure

Each simulation year consists of 5 synchronized phases:

```
┌─────────────────────────────────────────────────────────────────┐
│                        SIMULATION YEAR                          │
├─────────────────────────────────────────────────────────────────┤
│ Phase 1: SENSING                                                │
│   • Each blob scans cells within 5-cell radius                  │
│   • Identifies: nearest food, reproduction candidates           │
│   ════════════ BARRIER ════════════                             │
│ Phase 2: DECISION                                               │
│   • Each blob decides based on priority:                        │
│     1. REPRODUCE (if energy > 150 AND partner available)        │
│     2. MOVE_TO_FOOD (if food visible)                           │
│     3. MOVE_RANDOM (otherwise)                                  │
│   ════════════ BARRIER ════════════                             │
│ Phase 3: ACTION                                                 │
│   • Step 3a: Movement execution                                 │
│   • Step 3b: Food consumption (energy shared if contested)      │
│   • Step 3c: Reproduction (create offspring, parents lose 50)   │
│   ════════════ BARRIER ════════════                             │
│ Phase 4: MAINTENANCE                                            │
│   • Metabolism: all blobs lose 20 energy                        │
│   • Death: remove blobs with energy ≤ 0                         │
│   • Food spawning: 5% of empty cells get food                   │
│   ════════════ BARRIER ════════════                             │
│ Phase 5: STATISTICS                                             │
│   • Collect population count, births, deaths                    │
│   • Calculate average energy, total food                        │
│   ════════════ BARRIER ════════════                             │
└─────────────────────────────────────────────────────────────────┘
```

### Blob Decision Algorithm

```
function makeDecision(blob):
    if blob.energy > 150 AND nearbyBlobWithEnergy > 150:
        return REPRODUCE
    else if foodVisible within radius 5:
        return MOVE_TO_FOOD
    else:
        return MOVE_RANDOM
```

### Movement Algorithm

```
function move(blob, decision):
    if decision == REPRODUCE:
        stay in place
    else if decision == MOVE_TO_FOOD:
        direction = calculateDirectionToward(nearestFood)
        newPosition = currentPosition + direction
        clampToGridBoundaries(newPosition)
    else:  // MOVE_RANDOM
        direction = randomChoice(UP, DOWN, LEFT, RIGHT)
        newPosition = currentPosition + direction
        clampToGridBoundaries(newPosition)
```

### Food Consumption Algorithm

```
function consumeFood(cell):
    blobsOnCell = cell.getBlobs()
    if cell.hasFood():
        energyPerBlob = 100 / count(blobsOnCell)
        for each blob in blobsOnCell:
            blob.addEnergy(energyPerBlob)
        cell.removeFood()
```

### Reproduction Algorithm

```
function reproduce(cell):
    candidates = blobs on cell with decision == REPRODUCE
    if count(candidates) >= 2:
        for i = 0 to count(candidates) - 1 step 2:
            parent1 = candidates[i]
            parent2 = candidates[i+1]
            if parent1.energy > 150 AND parent2.energy > 150:
                offspring = new Blob(position=cell, energy=100)
                parent1.removeEnergy(50)
                parent2.removeEnergy(50)
                addToSimulation(offspring)
```

### Energy Economics

| Action | Energy Change |
|--------|---------------|
| Starting energy | 100 |
| Eat food (alone) | +100 |
| Eat food (shared with N blobs) | +100/N |
| Reproduce (parent) | -50 |
| Reproduce (offspring) | 100 (new blob) |
| Metabolism (per year) | -20 |
| Death threshold | ≤ 0 |
| Reproduction threshold | > 150 |

---

## Synchronization Mechanisms

### 1. CyclicBarrier for Phase Synchronization

The primary synchronization mechanism is a `CyclicBarrier` that ensures all worker threads complete each phase before any thread proceeds to the next phase.

```java
// SimulationEngine.java
private final CyclicBarrier phaseBarrier;

// Initialize with thread count
phaseBarrier = new CyclicBarrier(threadCount);

// Wait at barrier after each phase
private void awaitBarrier() {
    try {
        phaseBarrier.await();
    } catch (InterruptedException | BrokenBarrierException e) {
        // Handle error
    }
}
```

**Why CyclicBarrier?**
- Reusable across all phases and years
- Blocks all threads until all arrive
- Automatic reset for the next barrier point

### 2. Cell-Level Locking

For operations that modify cell state (food consumption, reproduction), we use `ReentrantLock` on each cell:

```java
// Cell.java
private final ReentrantLock lock;

public void withLock(Runnable action) {
    lock.lock();
    try {
        action.run();
    } finally {
        lock.unlock();
    }
}
```

**Lock ordering to prevent deadlocks:**
- Cells are always locked individually
- Complex operations lock one cell at a time
- No nested cell locks

### 3. Atomic Operations

Energy modifications use synchronized methods:

```java
// Blob.java
public synchronized void addEnergy(int amount) {
    this.energy += amount;
}

public synchronized void removeEnergy(int amount) {
    this.energy -= amount;
    if (this.energy <= 0) {
        this.alive = false;
    }
}
```

### 4. Thread-Safe Collections

Shared data structures use concurrent collections:

```java
// ConcurrentHashMap for cell occupancy tracking
Map<Position, List<Blob>> cellOccupancy = new ConcurrentHashMap<>();

// CopyOnWriteArrayList for blob lists
CopyOnWriteArrayList<Blob> blobs = new CopyOnWriteArrayList<>();

// ConcurrentHashMap.KeySetView for processed blobs
Set<Blob> processedForReproduction = ConcurrentHashMap.newKeySet();
```

### 5. Volatile Variables

For visibility across threads without locking:

```java
// Blob.java
private volatile Position position;
private volatile int energy;
private volatile boolean alive;
private volatile Decision currentDecision;
```

### Synchronization Diagram

```
Thread 0    Thread 1    Thread 2    Thread 3
    │           │           │           │
    ▼           ▼           ▼           ▼
┌───────────────────────────────────────────┐
│          Phase 1: SENSING                 │
│  (parallel, read-only operations)         │
└───────────────────────────────────────────┘
    │           │           │           │
    ▼           ▼           ▼           ▼
═══════════ CYCLIC BARRIER ════════════════
    │           │           │           │
    ▼           ▼           ▼           ▼
┌───────────────────────────────────────────┐
│          Phase 2: DECISION                │
│  (parallel, write to own blobs only)      │
└───────────────────────────────────────────┘
    │           │           │           │
    ▼           ▼           ▼           ▼
═══════════ CYCLIC BARRIER ════════════════
    │           │           │           │
    ▼           ▼           ▼           ▼
┌───────────────────────────────────────────┐
│          Phase 3: ACTION                  │
│  (parallel with cell locks for writes)    │
│  • Cell locks for movement                │
│  • Cell locks for food consumption        │
│  • Cell locks for reproduction            │
└───────────────────────────────────────────┘
    │           │           │           │
    ▼           ▼           ▼           ▼
═══════════ CYCLIC BARRIER ════════════════
    ... (continues for remaining phases)
```

---

## Performance Measurements

### Metrics Collected

1. **Total Simulation Time**: Wall-clock time for entire simulation
2. **Average Year Time**: Mean time per simulation year
3. **Phase Breakdown**: Time spent in each phase
4. **Speedup**: Ratio of single-thread time to multi-thread time
5. **Efficiency**: Speedup divided by thread count

### Running Benchmarks

```bash
java -jar build/population-simulator.jar --benchmark
```

This runs the simulation with 1, 2, 4, 8, and 16 threads and reports:

```
BENCHMARK SUMMARY
======================================================================
Threads | Time (ms) | Speedup | Efficiency | Final Pop
----------------------------------------------------------------------
      1 |   5432.12 |   1.00x |     100.0% |      1234
      2 |   2891.45 |   1.88x |      94.0% |      1234
      4 |   1523.67 |   3.57x |      89.2% |      1234
      8 |    891.23 |   6.10x |      76.2% |      1234
======================================================================
```

### Phase Timing Analysis

```
PHASE TIMING BREAKDOWN:
--------------------------------------------------
  Sensing:      1234.56 ms ( 25.1%)
  Decision:      456.78 ms (  9.3%)
  Action:       2345.67 ms ( 47.6%)
  Maintenance:   567.89 ms ( 11.5%)
  Statistics:    321.00 ms (  6.5%)
--------------------------------------------------
```

### Exporting Statistics

```bash
java -jar build/population-simulator.jar --export results.csv
```

CSV format:
```csv
year,population,births,deaths,avgEnergy,food,timeMs,sensingMs,decisionMs,actionMs,maintenanceMs,statsMs
1,500,0,12,98.5,48234,45.23,12.34,5.67,20.12,5.10,2.00
2,488,15,25,95.2,47891,43.12,...
```

### Expected Performance Characteristics

| Grid Size | Blobs | 1 Thread | 4 Threads | 8 Threads |
|-----------|-------|----------|-----------|-----------|
| 200×200   | 100   | ~50ms/yr | ~15ms/yr  | ~10ms/yr  |
| 500×500   | 300   | ~200ms/yr| ~60ms/yr  | ~35ms/yr  |
| 1000×1000 | 500   | ~800ms/yr| ~220ms/yr | ~130ms/yr |

**Note**: Actual performance depends on hardware, population dynamics, and blob distribution.

---

## MPI Extension Guide

This codebase is designed for easy extension to MPI (Message Passing Interface). Here's how another developer can implement the distributed version:

### Key Design Decisions for MPI

1. **Horizontal Strip Partitioning**: The grid is divided into horizontal strips (one per MPI process). This minimizes border communication.

2. **Serialization Methods**: All model classes have `toArray()` and `fromArray()` methods for MPI data transfer.

3. **Barrier Equivalence**: Each `CyclicBarrier.await()` maps directly to `MPI_Barrier()`.

### MPI Implementation Steps

#### Step 1: Replace Barrier with MPI_Barrier

```java
// Current (shared memory):
phaseBarrier.await();

// MPI version:
MPI.COMM_WORLD.Barrier();
```

#### Step 2: Assign Regions to MPI Ranks

```java
int rank = MPI.COMM_WORLD.Rank();
int size = MPI.COMM_WORLD.Size();

// Each rank owns a horizontal strip
int rowsPerProcess = gridHeight / size;
int startY = rank * rowsPerProcess;
int endY = (rank == size - 1) ? gridHeight - 1 : startY + rowsPerProcess - 1;
```

#### Step 3: Exchange Border Data Before Sensing

Blobs near region boundaries need data from neighboring regions:

```java
// Exchange border blobs with neighbors
if (rank > 0) {
    // Send top border to rank-1
    int[] borderData = serializeBorderBlobs(topBorderBlobs);
    MPI.COMM_WORLD.Send(borderData, 0, borderData.length, MPI.INT, rank - 1, TAG);
    
    // Receive bottom border from rank-1
    int[] received = new int[maxBorderSize];
    MPI.COMM_WORLD.Recv(received, 0, maxBorderSize, MPI.INT, rank - 1, TAG);
    addGhostBlobs(deserializeBorderBlobs(received));
}
```

#### Step 4: Aggregate Statistics with MPI_Reduce

```java
// Gather global statistics
int[] localStats = {population, births, deaths};
int[] globalStats = new int[3];

MPI.COMM_WORLD.Reduce(localStats, 0, globalStats, 0, 3, MPI.INT, MPI.SUM, 0);
```

### MPI-Compatible Class Methods

```java
// Blob serialization
public int[] toArray() {
    return new int[]{id, x, y, energy, alive ? 1 : 0, decision.getCode()};
}

public static Blob fromArray(int[] arr) {
    return new Blob(arr[0], new Position(arr[1], arr[2]), arr[3]);
}

// Cell serialization
public int[] toArray() {
    return new int[]{x, y, hasFood ? 1 : 0, blobCount};
}
```

### Recommended MPI Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    MPI_COMM_WORLD                           │
├─────────────────────────────────────────────────────────────┤
│ Rank 0          │ Rank 1          │ Rank 2          │ Rank 3│
│ Rows 0-249      │ Rows 250-499    │ Rows 500-749    │ 750-999│
│                 │                 │                 │       │
│ ┌─────────────┐ │ ┌─────────────┐ │ ┌─────────────┐ │  ...  │
│ │ SimEngine   │ │ │ SimEngine   │ │ │ SimEngine   │ │       │
│ │ + threads   │ │ │ + threads   │ │ │ + threads   │ │       │
│ └─────────────┘ │ └─────────────┘ │ └─────────────┘ │       │
│       ↕         │       ↕         │       ↕         │       │
│ Ghost cells     │ Ghost cells     │ Ghost cells     │       │
│ from Rank 1     │ from 0 & 2      │ from 1 & 3      │       │
└─────────────────────────────────────────────────────────────┘
```

---

## Configuration Options

### Command-Line Arguments

| Argument | Description | Default |
|----------|-------------|---------|
| `--width N` | Grid width | 1000 |
| `--height N` | Grid height | 1000 |
| `--population N` | Starting population | 500 |
| `--years N` | Simulation years | 100 |
| `--threads N` | Worker threads | CPU cores |
| `--seed N` | Random seed | System time |
| `--debug` | Small test config | - |
| `--benchmark` | Run scaling test | - |
| `--export FILE` | Export to CSV | - |

### Programmatic Configuration

```java
SimulationConfig config = new SimulationConfig.Builder()
    .gridSize(1000, 1000)
    .startingPopulation(500)
    .startingEnergy(100)
    .reproductionThreshold(150)
    .reproductionCost(50)
    .offspringEnergy(100)
    .metabolismCost(20)
    .initialFoodPercentage(0.05)
    .yearlyFoodSpawnPercentage(0.05)
    .foodEnergyValue(100)
    .visionRadius(5)
    .totalYears(100)
    .threadCount(8)
    .randomSeed(42)
    .build();
```

---

## License

This project is part of the PPD End of Course collaborative project.

## Contributors

- [Leustean Robert] - Multi-threaded implementation
- [Kurti Mark] - MPI extension
