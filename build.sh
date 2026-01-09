#!/bin/bash
# Build script for Population Simulator
# Works without Maven using plain javac

set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC_DIR="$PROJECT_DIR/src/main/java"
BUILD_DIR="$PROJECT_DIR/build"
CLASSES_DIR="$BUILD_DIR/classes"

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║              Building Population Simulator                    ║"
echo "╚══════════════════════════════════════════════════════════════╝"

# Clean and create build directory
rm -rf "$BUILD_DIR"
mkdir -p "$CLASSES_DIR"

# Find all Java files
JAVA_FILES=$(find "$SRC_DIR" -name "*.java")

echo "Compiling Java sources..."
javac -d "$CLASSES_DIR" $JAVA_FILES

echo "Creating JAR file..."
cd "$CLASSES_DIR"
jar cfe "$BUILD_DIR/population-simulator.jar" com.ppd.simulation.Main .

echo ""
echo "Build complete!"
echo "JAR file: $BUILD_DIR/population-simulator.jar"
echo ""
echo "Run with: java -jar $BUILD_DIR/population-simulator.jar [options]"
echo ""
echo "Examples:"
echo "  java -jar build/population-simulator.jar --debug"
echo "  java -jar build/population-simulator.jar --threads 4 --years 100"
echo "  java -jar build/population-simulator.jar --benchmark"

