package com.ppd.simulation.model;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents an entity (blob) in the simulation.
 * Thread-safe for concurrent access during parallel phases.
 * 
 * Design Notes for MPI Extension:
 * - All fields are serializable primitives or can be converted to primitives
 * - The toArray() and fromArray() methods support MPI data transfer
 * - Blob ID is globally unique across all processes
 */
public class Blob {
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(0);
    
    private final int id;
    private volatile Position position;
    private volatile int energy;
    private volatile boolean alive;
    
    // Decision phase results - reset each year
    private volatile Decision currentDecision;
    private volatile Position targetPosition;
    private volatile Blob reproductionPartner;
    
    // Sensing phase results - reset each year
    private volatile Position nearestFoodPosition;
    private volatile Blob nearestReproductionCandidate;

    /**
     * Create a new blob with default starting energy.
     */
    public Blob(Position position) {
        this(position, 100);
    }

    /**
     * Create a new blob with specified energy.
     */
    public Blob(Position position, int energy) {
        this.id = ID_GENERATOR.incrementAndGet();
        this.position = position;
        this.energy = energy;
        this.alive = true;
        this.currentDecision = Decision.STAY;
    }

    /**
     * Create a blob with a specific ID (for deserialization/MPI).
     */
    public Blob(int id, Position position, int energy) {
        this.id = id;
        this.position = position;
        this.energy = energy;
        this.alive = true;
        this.currentDecision = Decision.STAY;
    }

    // ============ Getters ============

    public int getId() {
        return id;
    }

    public Position getPosition() {
        return position;
    }

    public int getX() {
        return position.getX();
    }

    public int getY() {
        return position.getY();
    }

    public int getEnergy() {
        return energy;
    }

    public boolean isAlive() {
        return alive;
    }

    public Decision getCurrentDecision() {
        return currentDecision;
    }

    public Position getTargetPosition() {
        return targetPosition;
    }

    public Blob getReproductionPartner() {
        return reproductionPartner;
    }

    public Position getNearestFoodPosition() {
        return nearestFoodPosition;
    }

    public Blob getNearestReproductionCandidate() {
        return nearestReproductionCandidate;
    }

    //  State Modifiers 

    /**
     * Set the blob's position. Used during movement phase.
     */
    public void setPosition(Position position) {
        this.position = position;
    }

    /**
     * Add energy to the blob (e.g., from eating food).
     * Thread-safe using synchronized block.
     */
    public synchronized void addEnergy(int amount) {
        this.energy += amount;
    }

    /**
     * Remove energy from the blob (e.g., metabolism, reproduction).
     * Thread-safe using synchronized block.
     */
    public synchronized void removeEnergy(int amount) {
        this.energy -= amount;
        if (this.energy <= 0) {
            this.alive = false;
        }
    }

    /**
     * Apply metabolism cost. Returns true if blob survives.
     */
    public synchronized boolean applyMetabolism(int cost) {
        this.energy -= cost;
        if (this.energy <= 0) {
            this.alive = false;
            return false;
        }
        return true;
    }

    /**
     * Kill the blob.
     */
    public void kill() {
        this.alive = false;
    }

    /**
     * Check if blob can reproduce (energy > 150).
     */
    public boolean canReproduce() {
        return alive && energy > 150;
    }

    //  Phase Methods 

    /**
     * Reset per-year data at the start of each year.
     */
    public void resetYearData() {
        this.currentDecision = Decision.STAY;
        this.targetPosition = null;
        this.reproductionPartner = null;
        this.nearestFoodPosition = null;
        this.nearestReproductionCandidate = null;
    }

    /**
     * Store sensing results.
     */
    public void setSensingResults(Position nearestFood, Blob nearestReproductionCandidate) {
        this.nearestFoodPosition = nearestFood;
        this.nearestReproductionCandidate = nearestReproductionCandidate;
    }

    /**
     * Set the decision for this year.
     */
    public void setDecision(Decision decision, Position targetPosition) {
        this.currentDecision = decision;
        this.targetPosition = targetPosition;
    }

    /**
     * Set reproduction partner (mutual agreement needed).
     */
    public void setReproductionPartner(Blob partner) {
        this.reproductionPartner = partner;
    }

    //  Serialization for MPI 

    /**
     * Serialize blob to int array for MPI transfer.
     * Format: [id, x, y, energy, alive, decision]
     */
    public int[] toArray() {
        return new int[]{
            id,
            position.getX(),
            position.getY(),
            energy,
            alive ? 1 : 0,
            currentDecision.getCode()
        };
    }

    /**
     * Deserialize blob from int array.
     */
    public static Blob fromArray(int[] arr) {
        Blob blob = new Blob(arr[0], new Position(arr[1], arr[2]), arr[3]);
        if (arr[4] == 0) blob.kill();
        blob.currentDecision = Decision.fromCode(arr[5]);
        return blob;
    }

    /**
     * Size of serialized array.
     */
    public static int serializedSize() {
        return 6;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Blob blob = (Blob) o;
        return id == blob.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return String.format("Blob[id=%d, pos=%s, energy=%d, alive=%b]", 
            id, position, energy, alive);
    }
}

