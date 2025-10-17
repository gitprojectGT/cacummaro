@echo off
setlocal enabledelayedexpansion

echo.
echo 🚀 Starting Cacummaro PDF Ingest ^& Categorization Application
echo ============================================================

REM Check if Java is installed
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Java is not installed. Please install Java 11 or higher.
    pause
    exit /b 1
)

REM Check if Docker is installed
docker --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Docker is not installed. Please install Docker to run CouchDB.
    pause
    exit /b 1
)

REM Check if Docker is running
docker info >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Docker is not running. Please start Docker Desktop.
    pause
    exit /b 1
)

echo ✅ Prerequisites check passed
echo.

REM Start CouchDB
echo 📂 Starting CouchDB...
docker-compose up -d
if %errorlevel% neq 0 (
    echo ❌ Failed to start CouchDB
    pause
    exit /b 1
)

echo ✅ CouchDB started successfully
echo.

REM Wait for CouchDB to be ready
echo ⏳ Waiting for CouchDB to be ready...
set timeout=30
set counter=0

:wait_loop
if %counter% geq %timeout% (
    echo.
    echo ❌ CouchDB failed to start within %timeout% seconds
    pause
    exit /b 1
)

curl -s http://localhost:5984/ >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ CouchDB is ready
    goto couchdb_ready
)

timeout /t 1 /nobreak >nul
set /a counter+=1
echo|set /p="."
goto wait_loop

:couchdb_ready
echo.

REM Create Obsidian vault directory
echo 📝 Creating Obsidian vault directory...
if not exist "obsidian-vault" mkdir "obsidian-vault"
echo ✅ Obsidian vault directory created

REM Compile and package the application
echo 🔨 Building application...
call mvn clean package -q
if %errorlevel% neq 0 (
    echo ❌ Failed to build application
    pause
    exit /b 1
)

echo ✅ Application built successfully
echo.
echo 🌟 Starting Cacummaro Application...
echo ------------------------------------
echo 🌐 API will be available at: http://localhost:8080/api/v1/
echo 🩺 Health check: http://localhost:8080/actuator/health
echo 📊 CouchDB Fauxton: http://localhost:5984/_utils/
echo 📝 Obsidian vault: ./obsidian-vault/
echo.
echo Press Ctrl+C to stop the application
echo.

REM Start the Spring Boot application
java -jar target\cacummaro-1.0-SNAPSHOT.jar

REM Cleanup when script exits
echo.
echo 🛑 Stopping services...
docker-compose down
echo ✅ Services stopped
pause