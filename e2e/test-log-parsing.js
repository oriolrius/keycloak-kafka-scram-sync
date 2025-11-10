#!/usr/bin/env node
/**
 * Demonstration: Log Parsing for Consumer Group Readiness
 *
 * This script demonstrates the log parsing approach to detect when
 * a Kafka consumer group is ready before starting consumption.
 */

import { Kafka, logLevel } from 'kafkajs';
import fs from 'fs';

// Silence partitioner warning
process.env.KAFKAJS_NO_PARTITIONER_WARNING = '1';

const CONFIG = {
  kafka: {
    brokers: ['localhost:57005'],
    ssl: {
      ca: [fs.readFileSync('../testing/certs/ca-root.pem', 'utf-8')],
      rejectUnauthorized: false
    },
    sasl: {
      mechanism: 'scram-sha-256',
      username: 'admin',
      password: 'The2password.'
    }
  }
};

/**
 * Create a custom log creator that waits for consumer group to be ready
 */
function createConsumerGroupReadyWatcher() {
  let resolveGroupReady;
  const groupReadyPromise = new Promise((resolve) => {
    resolveGroupReady = resolve;
  });

  const logCreator = () => {
    return ({ level, log }) => {
      const { message } = log;

      // Check for consumer group ready message
      if (message && message.includes('Consumer has joined the group')) {
        console.log('\n🎯 DETECTED: Consumer group is ready!\n');
        resolveGroupReady();
      }

      // Output all logs for visibility
      const levelLabels = ['NOTHING', 'ERROR', 'WARN', 'INFO', 'DEBUG'];
      const levelLabel = levelLabels[level] || 'UNKNOWN';
      console.log(JSON.stringify({ level: levelLabel, ...log }));
    };
  };

  return { logCreator, groupReadyPromise };
}

async function demonstrateLogParsing() {
  console.log('═══════════════════════════════════════════════════════');
  console.log('   Log Parsing Demonstration');
  console.log('═══════════════════════════════════════════════════════\n');
  console.log('This demonstrates detecting consumer group readiness by');
  console.log('parsing KafkaJS logs for the join event.\n');
  console.log('Watch for the message:');
  console.log('  "[ConsumerGroup] Consumer has joined the group"\n');
  console.log('═══════════════════════════════════════════════════════\n');

  // Create log watcher
  const { logCreator, groupReadyPromise } = createConsumerGroupReadyWatcher();

  const kafka = new Kafka({
    clientId: 'log-parsing-demo',
    brokers: CONFIG.kafka.brokers,
    ssl: CONFIG.kafka.ssl,
    sasl: CONFIG.kafka.sasl,
    connectionTimeout: 10000,
    requestTimeout: 30000,
    retry: { initialRetryTime: 300, retries: 8 },
    logCreator  // Custom log creator that watches for consumer group ready
  });

  const consumer = kafka.consumer({
    groupId: `demo-group-${Date.now()}`,
    retry: { initialRetryTime: 300, retries: 8 },
    sessionTimeout: 15000,
    rebalanceTimeout: 30000,
    requestTimeout: 30000
  });

  try {
    console.log('📡 Connecting consumer...');
    await consumer.connect();
    console.log('✅ Consumer connected\n');

    console.log('📝 Subscribing to topic...');
    await consumer.subscribe({ topic: '__consumer_offsets', fromBeginning: false });
    console.log('✅ Subscribed to topic\n');

    console.log('🚀 Starting consumer (watch for group join event)...\n');

    // Start consumer run (this triggers group initialization)
    consumer.run({
      eachMessage: async ({ topic, partition, message }) => {
        // We don't care about messages in this demo
      }
    });

    // Wait for consumer group to be ready
    console.log('⏳ Waiting for consumer group to initialize...\n');
    await groupReadyPromise;

    console.log('\n═══════════════════════════════════════════════════════');
    console.log('✅ SUCCESS: Consumer group ready event detected!');
    console.log('═══════════════════════════════════════════════════════\n');
    console.log('The log parsing approach successfully detected when the');
    console.log('consumer group initialization completed.\n');
    console.log('In a real E2E test, we would now start consuming messages');
    console.log('without encountering transient coordinator errors.\n');

  } catch (error) {
    console.error('\n❌ Error:', error.message);
  } finally {
    await consumer.disconnect();
    process.exit(0);
  }
}

demonstrateLogParsing();
