#!/bin/bash
# Run script for Population Simulator

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_FILE="$PROJECT_DIR/build/population-simulator.jar"

# Check if JAR exists
if [ ! -f "$JAR_FILE" ]; then
    echo "JAR file not found. Building first..."
    "$PROJECT_DIR/build.sh"
fi

# Run the simulation with all provided arguments
java -jar "$JAR_FILE" "$@"

