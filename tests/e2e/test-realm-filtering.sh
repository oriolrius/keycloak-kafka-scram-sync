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
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
TESTING_DIR="$ROOT_DIR/tests/infrastructure"

echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}   Keycloak → Kafka SCRAM: Realm Filtering E2E Test${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"

# Step 0a: Check and install Node.js dependencies
echo -e "\n${YELLOW}═══════════════════════════════════════════════════════${NC}"
echo -e "${YELLOW}STEP 0a: Checking Node.js dependencies${NC}"
echo -e "${YELLOW}═══════════════════════════════════════════════════════${NC}"
cd "$SCRIPT_DIR"

if [ ! -d "node_modules" ] || [ ! -f "node_modules/.package-lock.json" ]; then
    echo -e "${YELLOW}📦 Installing npm dependencies...${NC}"
    npm install --silent
    echo -e "${GREEN}✅ Dependencies installed${NC}"
else
    echo -e "${GREEN}✅ Dependencies already installed${NC}"
fi

# Step 0b: Build the SPI
echo -e "\n${YELLOW}═══════════════════════════════════════════════════════${NC}"
echo -e "${YELLOW}STEP 0b: Building Keycloak SPI${NC}"
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

    # Ensure directories exist with correct ownership for container user (uid 1000)
    mkdir -p data/kafka data/keycloak data/kms
    sudo chown -R $USER:$USER data/

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

# Function to start services with realm filtering configured
start_services_with_realm_filtering() {
    echo -e "\n${YELLOW}🚀 Starting services with realm filtering enabled...${NC}"
    echo -e "${YELLOW}   Configured realm: test-realm${NC}"
    echo -e "${YELLOW}   Filtered realm: master${NC}"
    cd "$TESTING_DIR"

    # Export configuration for realm filtering
    export KAFKA_SCRAM_MECHANISM=256
    export PASSWORD_SYNC_REALMS=test-realm

    # Update docker-compose to pass PASSWORD_SYNC_REALMS to Keycloak container
    echo -e "${YELLOW}   Setting PASSWORD_SYNC_REALMS=test-realm${NC}"

    docker compose up -d

    wait_for_services || exit 1

    # Enable the event listener
    echo -e "${YELLOW}🔧 Enabling password-sync-listener...${NC}"
    chmod +x scripts/enable-event-listener.sh
    ./scripts/enable-event-listener.sh > /dev/null 2>&1
    echo -e "${GREEN}✅ Event listener enabled${NC}"

    # Verify realm filtering configuration in Keycloak logs
    echo -e "${YELLOW}🔍 Verifying realm filtering configuration...${NC}"
    sleep 2
    if docker logs keycloak 2>&1 | grep -q "Realm filtering ENABLED"; then
        echo -e "${GREEN}✅ Realm filtering is enabled in Keycloak${NC}"
        docker logs keycloak 2>&1 | grep "Realm filtering ENABLED" | tail -1
    else
        echo -e "${YELLOW}⚠️  Could not verify realm filtering in logs (may not be initialized yet)${NC}"
    fi
}

# Function to run realm filtering test
run_realm_filtering_test() {
    echo -e "\n${YELLOW}═══════════════════════════════════════════════════════${NC}"
    echo -e "${YELLOW}Running Realm Filtering E2E Test${NC}"
    echo -e "${YELLOW}═══════════════════════════════════════════════════════${NC}"

    cd "$SCRIPT_DIR"
    export TEST_SCRAM_MECHANISM=256
    node realm-filtering-e2e.test.js

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ Realm filtering test PASSED${NC}"
        return 0
    else
        echo -e "${RED}❌ Realm filtering test FAILED${NC}"
        return 1
    fi
}

# Main test execution
main() {
    local test_result=0

    echo -e "\n${BLUE}═══════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}   Starting Realm Filtering Test${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"

    cleanup_services
    start_services_with_realm_filtering

    if ! run_realm_filtering_test; then
        test_result=1
    fi

    # Show recent Keycloak logs for debugging
    echo -e "\n${YELLOW}═══════════════════════════════════════════════════════${NC}"
    echo -e "${YELLOW}Recent Keycloak Logs (last 30 lines)${NC}"
    echo -e "${YELLOW}═══════════════════════════════════════════════════════${NC}"
    docker logs keycloak 2>&1 | tail -30

    # Final summary
    echo -e "\n${BLUE}═══════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}   TEST SUMMARY${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"

    if [ $test_result -eq 0 ]; then
        echo -e "${GREEN}✅ Realm Filtering E2E Test: PASSED${NC}"
        echo -e "\n${GREEN}═══════════════════════════════════════════════════════${NC}"
        echo -e "${GREEN}   🎉 TEST PASSED${NC}"
        echo -e "${GREEN}═══════════════════════════════════════════════════════${NC}"
        exit 0
    else
        echo -e "${RED}❌ Realm Filtering E2E Test: FAILED${NC}"
        echo -e "\n${RED}═══════════════════════════════════════════════════════${NC}"
        echo -e "${RED}   ❌ TEST FAILED${NC}"
        echo -e "${RED}═══════════════════════════════════════════════════════${NC}"
        exit 1
    fi
}

# Run main function
main
