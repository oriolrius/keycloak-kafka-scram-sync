#!/bin/bash
set -e

# Set JAAS config for Kafka
export KAFKA_OPTS="-Djava.security.auth.login.config=/opt/kafka/config/kraft/kafka_server_jaas.conf"

# Cluster ID (can be overridden via environment variable)
CLUSTER_ID="${KAFKA_CLUSTER_ID:-MkU3OEVBNTcwNTJENDM2Qk}"

echo "=========================================="
echo "Kafka KRaft Mode Initialization"
echo "=========================================="
echo "Cluster ID: $CLUSTER_ID"
echo "Node ID: 1"
echo ""

# Check if storage is already formatted
if [ ! -f "/var/lib/kafka/data/meta.properties" ]; then
    echo "Formatting Kafka storage..."
    /opt/kafka/bin/kafka-storage.sh format \
        -t "$CLUSTER_ID" \
        -c /opt/kafka/config/kraft/server.properties
    echo "✅ Storage formatted successfully"
else
    echo "✅ Storage already formatted, skipping..."
fi

echo ""
echo "Starting Kafka server..."
echo "=========================================="

# Start Kafka in the background to set up SCRAM credentials
/opt/kafka/bin/kafka-server-start.sh /opt/kafka/config/kraft/server.properties &
KAFKA_PID=$!

# Wait for Kafka to be ready
echo "Waiting for Kafka to start..."
for i in {1..30}; do
    if /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092 > /dev/null 2>&1; then
        echo "✅ Kafka is ready"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "❌ Kafka failed to start within 30 seconds"
        kill $KAFKA_PID 2>/dev/null || true
        exit 1
    fi
    sleep 1
done

# Create or update SCRAM credentials for admin user
echo ""
echo "Setting up SCRAM credentials for admin user..."

# Create SCRAM-SHA-256 credentials
/opt/kafka/bin/kafka-configs.sh --bootstrap-server localhost:9092 \
    --alter \
    --add-config 'SCRAM-SHA-256=[password=The2password.]' \
    --entity-type users \
    --entity-name admin || true

# Create SCRAM-SHA-512 credentials
/opt/kafka/bin/kafka-configs.sh --bootstrap-server localhost:9092 \
    --alter \
    --add-config 'SCRAM-SHA-512=[password=The2password.]' \
    --entity-type users \
    --entity-name admin || true

echo "✅ SCRAM credentials configured"
echo ""
echo "=========================================="
echo "Kafka is running and ready!"
echo "=========================================="

# Keep the container running by waiting for Kafka process
wait $KAFKA_PID
