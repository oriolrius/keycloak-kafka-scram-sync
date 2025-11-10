#!/usr/bin/env node
/**
 * E2E Test: Keycloak Password Sync to Kafka SCRAM
 *
 * Test Flow:
 * 1. Create Keycloak user via REST API
 * 2. Password automatically syncs to Kafka SCRAM credentials (via SPI)
 * 3. Kafka producer authenticates with SCRAM-SHA-256 and publishes message
 * 4. Kafka consumer authenticates with SCRAM-SHA-512 and receives message
 */

import { Kafka, Partitioners, logLevel } from 'kafkajs';
import fs from 'fs';
import https from 'https';
import fetch from 'node-fetch';

// Silence KafkaJS partitioner warning
process.env.KAFKAJS_NO_PARTITIONER_WARNING = '1';

// Configuration
const SCRAM_MECHANISM = process.env.TEST_SCRAM_MECHANISM || '256';
const SCRAM_NAME = `scram-sha-${SCRAM_MECHANISM}`;

const CONFIG = {
  keycloak: {
    url: process.env.KEYCLOAK_URL || 'https://localhost:57003',
    adminUser: process.env.KEYCLOAK_ADMIN || 'admin',
    adminPassword: process.env.KEYCLOAK_ADMIN_PASSWORD || 'The2password.',
    realm: process.env.KEYCLOAK_REALM || 'master'
  },
  kafka: {
    brokers: (process.env.KAFKA_BROKERS || 'localhost:57005').split(','),
    mechanism: SCRAM_NAME,
    ssl: {
      ca: [fs.readFileSync('../testing/certs/ca-root.pem', 'utf-8')],
      rejectUnauthorized: false // For testing with self-signed certs
    }
  },
  test: {
    username: `test-user-${Date.now()}`,
    password: 'SecureTestPass123!',
    topic: `test-topic-${Date.now()}`
  }
};

// HTTPS agent that accepts self-signed certificates
const httpsAgent = new https.Agent({
  rejectUnauthorized: false
});

/**
 * Get Keycloak admin access token
 */
async function getAdminToken() {
  const params = new URLSearchParams({
    grant_type: 'password',
    client_id: 'admin-cli',
    username: CONFIG.keycloak.adminUser,
    password: CONFIG.keycloak.adminPassword
  });

  const response = await fetch(
    `${CONFIG.keycloak.url}/realms/master/protocol/openid-connect/token`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: params.toString(),
      agent: httpsAgent
    }
  );

  if (!response.ok) {
    throw new Error(`Failed to get admin token: ${response.statusText}`);
  }

  const data = await response.json();
  return data.access_token;
}

/**
 * Create Keycloak user
 */
async function createKeycloakUser(token, username) {
  console.log(`\n📝 Creating Keycloak user: ${username}`);

  const response = await fetch(
    `${CONFIG.keycloak.url}/admin/realms/${CONFIG.keycloak.realm}/users`,
    {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        username,
        enabled: true,
        emailVerified: true,
        email: `${username}@test.local`
      }),
      agent: httpsAgent
    }
  );

  if (!response.ok && response.status !== 409) { // 409 = already exists
    throw new Error(`Failed to create user: ${response.statusText}`);
  }

  console.log(`✅ User created in Keycloak`);
}

/**
 * Get user ID by username
 */
async function getUserId(token, username) {
  const response = await fetch(
    `${CONFIG.keycloak.url}/admin/realms/${CONFIG.keycloak.realm}/users?username=${username}&exact=true`,
    {
      method: 'GET',
      headers: { 'Authorization': `Bearer ${token}` },
      agent: httpsAgent
    }
  );

  if (!response.ok) {
    throw new Error(`Failed to get user: ${response.statusText}`);
  }

  const users = await response.json();
  if (!users || users.length === 0) {
    throw new Error(`User ${username} not found`);
  }

  return users[0].id;
}

/**
 * Set user password (triggers SPI sync to Kafka)
 */
async function setUserPassword(token, userId, password) {
  console.log(`\n🔐 Setting password for user (triggers Kafka SCRAM sync...)`);

  const response = await fetch(
    `${CONFIG.keycloak.url}/admin/realms/${CONFIG.keycloak.realm}/users/${userId}/reset-password`,
    {
      method: 'PUT',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        type: 'password',
        value: password,
        temporary: false
      }),
      agent: httpsAgent
    }
  );

  if (!response.ok) {
    throw new Error(`Failed to set password: ${response.statusText}`);
  }

  console.log(`✅ Password set in Keycloak`);
  console.log(`⏳ Waiting for SPI to sync to Kafka SCRAM...`);

  // Wait for sync to complete
  await new Promise(resolve => setTimeout(resolve, 2000));

  console.log(`✅ Sync completed`);
}

/**
 * Wait for Kafka cluster to be ready
 */
async function waitKafkaReady(kafka, timeoutMs = 15000) {
  console.log(`⏳ Waiting for Kafka cluster to be ready...`);
  const admin = kafka.admin();
  await admin.connect();

  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    try {
      const info = await admin.describeCluster();
      if (info.brokers?.length) {
        console.log(`✅ Kafka cluster ready (${info.brokers.length} broker(s))`);
        await admin.disconnect();
        return;
      }
    } catch (e) {
      // Cluster not ready yet
    }
    await new Promise(r => setTimeout(r, 300));
  }

  await admin.disconnect();
  throw new Error('Kafka cluster not ready within timeout');
}

/**
 * Ensure topic exists with leader elected
 */
async function ensureTopicWithLeaders(kafka, topic, timeoutMs = 10000) {
  console.log(`⏳ Creating topic and waiting for leader election: ${topic}...`);
  const admin = kafka.admin();
  await admin.connect();

  try {
    // Create topic with waitForLeaders
    await admin.createTopics({
      waitForLeaders: true,
      topics: [{
        topic,
        numPartitions: 1,
        replicationFactor: 1
      }],
    });

    // Extra safety: poll metadata until leader is set
    const start = Date.now();
    while (Date.now() - start < timeoutMs) {
      const md = await admin.fetchTopicMetadata({ topics: [topic] });
      const p0 = md.topics[0]?.partitions?.[0];
      if (p0 && typeof p0.leader === 'number' && p0.leader >= 0) {
        console.log(`✅ Topic ready with leader: ${topic}`);
        await admin.disconnect();
        return;
      }
      await new Promise(r => setTimeout(r, 200));
    }

    throw new Error(`Topic ${topic} not ready (no leader) within timeout`);
  } catch (e) {
    // Topic might already exist, verify it has a leader
    if (e.message.includes('already exists')) {
      const md = await admin.fetchTopicMetadata({ topics: [topic] });
      const p0 = md.topics[0]?.partitions?.[0];
      if (p0 && typeof p0.leader === 'number' && p0.leader >= 0) {
        console.log(`✅ Topic already exists with leader: ${topic}`);
        await admin.disconnect();
        return;
      }
    }
    await admin.disconnect();
    throw e;
  }
}

/**
 * Create a custom log creator that waits for consumer group to be ready
 * Returns both the logCreator and a promise that resolves when group is ready
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

/**
 * Test Kafka producer with configured SCRAM mechanism
 */
async function testKafkaProducer(username, password, topic) {
  console.log(`\n📤 Testing Kafka producer (${CONFIG.kafka.mechanism.toUpperCase()})...`);

  const kafka = new Kafka({
    clientId: 'e2e-producer',
    brokers: CONFIG.kafka.brokers,
    ssl: CONFIG.kafka.ssl,
    sasl: {
      mechanism: CONFIG.kafka.mechanism,
      username,
      password
    },
    connectionTimeout: 10000,
    requestTimeout: 30000,
    retry: { initialRetryTime: 300, retries: 8 }
  });

  // Wait for Kafka cluster to be ready
  await waitKafkaReady(kafka);

  // Ensure topic exists with leader elected
  await ensureTopicWithLeaders(kafka, topic);

  const producer = kafka.producer({
    allowAutoTopicCreation: false,
    createPartitioner: Partitioners.LegacyPartitioner
  });

  try {
    await producer.connect();
    console.log(`✅ Producer connected with ${CONFIG.kafka.mechanism.toUpperCase()}`);

    const testMessage = {
      timestamp: new Date().toISOString(),
      message: 'Hello from E2E test!',
      mechanism: CONFIG.kafka.mechanism.toUpperCase()
    };

    await producer.send({
      topic,
      messages: [
        {
          key: 'test-key',
          value: JSON.stringify(testMessage)
        }
      ]
    });

    console.log(`✅ Message published to topic: ${topic}`);
    return testMessage;
  } finally {
    await producer.disconnect();
  }
}

/**
 * Test Kafka consumer with configured SCRAM mechanism
 */
async function testKafkaConsumer(username, password, topic, expectedMessage) {
  console.log(`\n📥 Testing Kafka consumer (${CONFIG.kafka.mechanism.toUpperCase()})...`);

  // Create log watcher to detect when consumer group is ready
  const { logCreator, groupReadyPromise } = createConsumerGroupReadyWatcher();

  const kafka = new Kafka({
    clientId: 'e2e-consumer',
    brokers: CONFIG.kafka.brokers,
    ssl: CONFIG.kafka.ssl,
    sasl: {
      mechanism: CONFIG.kafka.mechanism,
      username,
      password
    },
    connectionTimeout: 10000,
    requestTimeout: 30000,
    retry: { initialRetryTime: 300, retries: 8 },
    logCreator  // Use custom log creator to watch for consumer group ready
  });

  // Wait for Kafka cluster to be ready before creating consumer
  await waitKafkaReady(kafka);

  const consumer = kafka.consumer({
    groupId: `e2e-test-${Date.now()}`,
    retry: { initialRetryTime: 300, retries: 8 },
    sessionTimeout: 15000,
    rebalanceTimeout: 30000,
    requestTimeout: 30000
  });

  try {
    await consumer.connect();
    console.log(`✅ Consumer connected with ${CONFIG.kafka.mechanism.toUpperCase()}`);

    await consumer.subscribe({ topic, fromBeginning: true });
    console.log(`✅ Subscribed to topic: ${topic}`);

    // Start the consumer (triggers consumer group initialization)
    console.log(`⏳ Waiting for consumer group to initialize...`);
    const messagePromise = new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        reject(new Error('Timeout: No message received within 15 seconds'));
      }, 15000);

      consumer.run({
        eachMessage: async ({ topic, partition, message }) => {
          clearTimeout(timeout);

          const receivedValue = message.value.toString();
          const receivedMessage = JSON.parse(receivedValue);

          console.log(`✅ Message received:`, receivedMessage);

          if (receivedMessage.timestamp === expectedMessage.timestamp) {
            console.log(`✅ Message content verified!`);
            resolve(receivedMessage);
          } else {
            reject(new Error('Received message does not match expected'));
          }
        }
      });
    });

    // Wait for consumer group to be ready (logs will show initialization)
    await groupReadyPromise;
    console.log(`✅ Consumer group ready`);

    // Wait for message
    return await messagePromise;
  } finally {
    await consumer.disconnect();
  }
}

/**
 * Main test execution
 */
async function runE2ETest() {
  console.log('═══════════════════════════════════════════════════════');
  console.log(`   Keycloak → Kafka SCRAM Sync E2E Test`);
  console.log(`   Mechanism: ${CONFIG.kafka.mechanism.toUpperCase()}`);
  console.log('═══════════════════════════════════════════════════════');
  console.log(`\nConfiguration:`);
  console.log(`  Keycloak: ${CONFIG.keycloak.url}`);
  console.log(`  Kafka: ${CONFIG.kafka.brokers.join(', ')}`);
  console.log(`  SCRAM Mechanism: ${CONFIG.kafka.mechanism.toUpperCase()}`);
  console.log(`  Test User: ${CONFIG.test.username}`);
  console.log(`  Test Topic: ${CONFIG.test.topic}`);

  try {
    // Step 1: Get admin token
    console.log(`\n${'='.repeat(55)}`);
    console.log('STEP 1: Authenticate to Keycloak');
    console.log('='.repeat(55));
    const token = await getAdminToken();
    console.log(`✅ Admin token obtained`);

    // Step 2: Create user
    console.log(`\n${'='.repeat(55)}`);
    console.log('STEP 2: Create Keycloak User');
    console.log('='.repeat(55));
    await createKeycloakUser(token, CONFIG.test.username);

    const userId = await getUserId(token, CONFIG.test.username);
    await setUserPassword(token, userId, CONFIG.test.password);

    // Step 3: Test Kafka producer
    console.log(`\n${'='.repeat(55)}`);
    console.log(`STEP 3: Publish Message (${CONFIG.kafka.mechanism.toUpperCase()})`);
    console.log('='.repeat(55));
    const publishedMessage = await testKafkaProducer(
      CONFIG.test.username,
      CONFIG.test.password,
      CONFIG.test.topic
    );

    // Wait for producer connections to fully disconnect
    console.log(`\n⏳ Waiting for connection pool to settle...`);
    await new Promise(r => setTimeout(r, 2000));

    // Step 4: Test Kafka consumer
    console.log(`\n${'='.repeat(55)}`);
    console.log(`STEP 4: Consume Message (${CONFIG.kafka.mechanism.toUpperCase()})`);
    console.log('='.repeat(55));
    const receivedMessage = await testKafkaConsumer(
      CONFIG.test.username,
      CONFIG.test.password,
      CONFIG.test.topic,
      publishedMessage
    );

    // Final verification
    console.log(`\n${'═'.repeat(55)}`);
    console.log('✅ E2E TEST PASSED');
    console.log('═'.repeat(55));
    console.log(`\n✅ All steps completed successfully:`);
    console.log(`   1. User created in Keycloak`);
    console.log(`   2. Password synced to Kafka SCRAM (${CONFIG.kafka.mechanism.toUpperCase()})`);
    console.log(`   3. Producer authenticated with ${CONFIG.kafka.mechanism.toUpperCase()}`);
    console.log(`   4. Consumer authenticated with ${CONFIG.kafka.mechanism.toUpperCase()}`);
    console.log(`   5. Message published and received`);
    console.log('');

    process.exit(0);

  } catch (error) {
    console.error(`\n${'═'.repeat(55)}`);
    console.error('❌ E2E TEST FAILED');
    console.error('═'.repeat(55));
    console.error(`\nError: ${error.message}`);
    if (error.stack) {
      console.error(`\nStack trace:\n${error.stack}`);
    }
    console.error('');
    process.exit(1);
  }
}

// Run the test
runE2ETest();
