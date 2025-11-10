#!/usr/bin/env bash
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
TESTING_DIR="$ROOT_DIR/testing"

echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}   Keycloak → Kafka SCRAM E2E Test (Both Mechanisms)${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"

# Step 0: Build the SPI
echo -e "\n${YELLOW}═══════════════════════════════════════════════════════${NC}"
echo -e "${YELLOW}STEP 0: Building Keycloak SPI${NC}"
echo -e "${YELLOW}═══════════════════════════════════════════════════════${NC}"
cd "$ROOT_DIR/src"
mvn clean package -DskipTests -q
echo -e "${GREEN}✅ SPI built successfully${NC}"

# Function to cleanup and stop services
cleanup_services() {
    echo -e "\n${YELLOW}🧹 Stopping services and cleaning up data...${NC}"
    cd "$TESTING_DIR"
    docker compose down -v --remove-orphans

    echo -e "${YELLOW}🧹 Removing Kafka and Keycloak data directories...${NC}"
    sudo rm -rf data/kafka/* data/keycloak/*

    echo -e "${GREEN}✅ Cleanup complete${NC}"
}

# Function to check if Keycloak is ready
check_keycloak() {
    curl -k -sf https://localhost:57003/realms/master > /dev/null 2>&1
}

# Function to check if Kafka is ready
check_kafka() {
    docker exec kafka /bin/sh -c 'nc -z localhost 9093' > /dev/null 2>&1
}

# Function to wait for services to be ready
wait_for_services() {
    echo -e "${YELLOW}⏳ Waiting for services to be ready (max 60 seconds)...${NC}"
    local max_attempts=60
    local attempt=0

    # Wait for Keycloak
    while ! check_keycloak; do
        attempt=$((attempt + 1))
        if [ $attempt -ge $max_attempts ]; then
            echo -e "${RED}❌ Timeout: Keycloak did not start in time${NC}"
            return 1
        fi
        sleep 1
    done
    echo -e "${GREEN}✅ Keycloak is ready (after $attempt seconds)${NC}"

    # Wait for Kafka
    attempt=0
    while ! check_kafka; do
        attempt=$((attempt + 1))
        if [ $attempt -ge $max_attempts ]; then
            echo -e "${RED}❌ Timeout: Kafka did not start in time${NC}"
            return 1
        fi
        sleep 1
    done
    echo -e "${GREEN}✅ Kafka is ready (after $attempt seconds)${NC}"

    # Give services a few more seconds to fully stabilize
    echo -e "${YELLOW}⏳ Stabilizing services...${NC}"
    sleep 5
    echo -e "${GREEN}✅ All services ready${NC}"
}

# Function to start services with specific SCRAM mechanism
start_services() {
    local mechanism=$1
    echo -e "\n${YELLOW}🚀 Starting services with SCRAM-SHA-$mechanism...${NC}"
    cd "$TESTING_DIR"
    export KAFKA_SCRAM_MECHANISM=$mechanism
    docker compose up -d

    wait_for_services || exit 1

    # Enable the event listener
    echo -e "${YELLOW}🔧 Enabling password-sync-listener...${NC}"
    chmod +x scripts/enable-event-listener.sh
    ./scripts/enable-event-listener.sh > /dev/null 2>&1
    echo -e "${GREEN}✅ Event listener enabled${NC}"
}

# Function to run e2e test for specific mechanism
run_test() {
    local mechanism=$1
    local test_name=$2

    echo -e "\n${YELLOW}═══════════════════════════════════════════════════════${NC}"
    echo -e "${YELLOW}$test_name${NC}"
    echo -e "${YELLOW}═══════════════════════════════════════════════════════${NC}"

    cd "$SCRIPT_DIR"
    export TEST_SCRAM_MECHANISM=$mechanism
    node scram-sync-e2e.test.js --mechanism=$mechanism

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ Test passed for SCRAM-SHA-$mechanism${NC}"
        return 0
    else
        echo -e "${RED}❌ Test failed for SCRAM-SHA-$mechanism${NC}"
        return 1
    fi
}

# Main test execution
main() {
    local sha256_result=0
    local sha512_result=0

    # === SCENARIO 1: SCRAM-SHA-256 ===
    echo -e "\n${BLUE}═══════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}   SCENARIO 1: Testing SCRAM-SHA-256${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"

    cleanup_services
    start_services "256"

    if ! run_test "256" "TEST 1: SCRAM-SHA-256 End-to-End"; then
        sha256_result=1
    fi

    # === SCENARIO 2: SCRAM-SHA-512 ===
    echo -e "\n${BLUE}═══════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}   SCENARIO 2: Testing SCRAM-SHA-512${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"

    cleanup_services
    start_services "512"

    if ! run_test "512" "TEST 2: SCRAM-SHA-512 End-to-End"; then
        sha512_result=1
    fi

    # Final summary
    echo -e "\n${BLUE}═══════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}   TEST SUMMARY${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"

    if [ $sha256_result -eq 0 ]; then
        echo -e "${GREEN}✅ SCRAM-SHA-256: PASSED${NC}"
    else
        echo -e "${RED}❌ SCRAM-SHA-256: FAILED${NC}"
    fi

    if [ $sha512_result -eq 0 ]; then
        echo -e "${GREEN}✅ SCRAM-SHA-512: PASSED${NC}"
    else
        echo -e "${RED}❌ SCRAM-SHA-512: FAILED${NC}"
    fi

    if [ $sha256_result -eq 0 ] && [ $sha512_result -eq 0 ]; then
        echo -e "\n${GREEN}═══════════════════════════════════════════════════════${NC}"
        echo -e "${GREEN}   🎉 ALL TESTS PASSED${NC}"
        echo -e "${GREEN}═══════════════════════════════════════════════════════${NC}"
        exit 0
    else
        echo -e "\n${RED}═══════════════════════════════════════════════════════${NC}"
        echo -e "${RED}   ❌ SOME TESTS FAILED${NC}"
        echo -e "${RED}═══════════════════════════════════════════════════════${NC}"
        exit 1
    fi
}

# Run main function
main
