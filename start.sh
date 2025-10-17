#!/bin/bash

# Cacummaro Application Start Script

echo "🚀 Starting Cacummaro PDF Ingest & Categorization Application"
echo "============================================================"

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "❌ Java is not installed. Please install Java 11 or higher."
    exit 1
fi

# Check Java version
java_version=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$java_version" -lt "11" ]; then
    echo "❌ Java 11 or higher is required. Current version: $(java -version 2>&1 | head -1)"
    exit 1
fi

# Check if Docker is installed and running
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed. Please install Docker to run CouchDB."
    exit 1
fi

if ! docker info &> /dev/null; then
    echo "❌ Docker is not running. Please start Docker."
    exit 1
fi

echo "✅ Prerequisites check passed"
echo ""

# Start CouchDB
echo "📂 Starting CouchDB..."
docker-compose up -d
if [ $? -eq 0 ]; then
    echo "✅ CouchDB started successfully"
else
    echo "❌ Failed to start CouchDB"
    exit 1
fi

# Wait for CouchDB to be ready
echo "⏳ Waiting for CouchDB to be ready..."
timeout=30
counter=0
while [ $counter -lt $timeout ]; do
    if curl -s http://localhost:5984/ > /dev/null 2>&1; then
        echo "✅ CouchDB is ready"
        break
    fi
    sleep 1
    counter=$((counter + 1))
    echo -n "."
done

if [ $counter -eq $timeout ]; then
    echo ""
    echo "❌ CouchDB failed to start within $timeout seconds"
    exit 1
fi

echo ""

# Create Obsidian vault directory
echo "📝 Creating Obsidian vault directory..."
mkdir -p ./obsidian-vault
echo "✅ Obsidian vault directory created"

# Compile and package the application
echo "🔨 Building application..."
mvn clean package -q
if [ $? -eq 0 ]; then
    echo "✅ Application built successfully"
else
    echo "❌ Failed to build application"
    exit 1
fi

echo ""
echo "🌟 Starting Cacummaro Application..."
echo "------------------------------------"
echo "🌐 API will be available at: http://localhost:8080/api/v1/"
echo "🩺 Health check: http://localhost:8080/actuator/health"
echo "📊 CouchDB Fauxton: http://localhost:5984/_utils/"
echo "📝 Obsidian vault: ./obsidian-vault/"
echo ""
echo "Press Ctrl+C to stop the application"
echo ""

# Start the Spring Boot application
java -jar target/cacummaro-1.0-SNAPSHOT.jar

# Cleanup when script exits
cleanup() {
    echo ""
    echo "🛑 Stopping services..."
    docker-compose down
    echo "✅ Services stopped"
}

trap cleanup EXIT