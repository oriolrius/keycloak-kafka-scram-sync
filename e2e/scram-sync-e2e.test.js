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

import { Kafka } from 'kafkajs';
import fs from 'fs';
import https from 'https';
import fetch from 'node-fetch';

// Configuration
const CONFIG = {
  keycloak: {
    url: process.env.KEYCLOAK_URL || 'https://localhost:57003',
    adminUser: process.env.KEYCLOAK_ADMIN || 'admin',
    adminPassword: process.env.KEYCLOAK_ADMIN_PASSWORD || 'The2password.',
    realm: process.env.KEYCLOAK_REALM || 'master'
  },
  kafka: {
    brokers: (process.env.KAFKA_BROKERS || 'localhost:57005').split(','),
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
 * Test Kafka producer with SCRAM-SHA-256
 */
async function testKafkaProducer(username, password, topic) {
  console.log(`\n📤 Testing Kafka producer (SCRAM-SHA-256)...`);

  const kafka = new Kafka({
    clientId: 'e2e-producer',
    brokers: CONFIG.kafka.brokers,
    ssl: CONFIG.kafka.ssl,
    sasl: {
      mechanism: 'scram-sha-256',
      username,
      password
    },
    connectionTimeout: 10000,
    requestTimeout: 10000
  });

  const producer = kafka.producer();

  try {
    await producer.connect();
    console.log(`✅ Producer connected with SCRAM-SHA-256`);

    const testMessage = {
      timestamp: new Date().toISOString(),
      message: 'Hello from E2E test!',
      mechanism: 'SCRAM-SHA-256'
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
 * Test Kafka consumer with SCRAM-SHA-512
 */
async function testKafkaConsumer(username, password, topic, expectedMessage) {
  console.log(`\n📥 Testing Kafka consumer (SCRAM-SHA-512)...`);

  const kafka = new Kafka({
    clientId: 'e2e-consumer',
    brokers: CONFIG.kafka.brokers,
    ssl: CONFIG.kafka.ssl,
    sasl: {
      mechanism: 'scram-sha-512',
      username,
      password
    },
    connectionTimeout: 10000,
    requestTimeout: 10000
  });

  const consumer = kafka.consumer({ groupId: `e2e-test-${Date.now()}` });

  try {
    await consumer.connect();
    console.log(`✅ Consumer connected with SCRAM-SHA-512`);

    await consumer.subscribe({ topic, fromBeginning: true });
    console.log(`✅ Subscribed to topic: ${topic}`);

    return new Promise((resolve, reject) => {
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
  } finally {
    await consumer.disconnect();
  }
}

/**
 * Main test execution
 */
async function runE2ETest() {
  console.log('═══════════════════════════════════════════════════════');
  console.log('   Keycloak → Kafka SCRAM Sync E2E Test');
  console.log('═══════════════════════════════════════════════════════');
  console.log(`\nConfiguration:`);
  console.log(`  Keycloak: ${CONFIG.keycloak.url}`);
  console.log(`  Kafka: ${CONFIG.kafka.brokers.join(', ')}`);
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

    // Step 3: Test Kafka producer with SCRAM-SHA-256
    console.log(`\n${'='.repeat(55)}`);
    console.log('STEP 3: Publish Message (SCRAM-SHA-256)');
    console.log('='.repeat(55));
    const publishedMessage = await testKafkaProducer(
      CONFIG.test.username,
      CONFIG.test.password,
      CONFIG.test.topic
    );

    // Step 4: Test Kafka consumer with SCRAM-SHA-512
    console.log(`\n${'='.repeat(55)}`);
    console.log('STEP 4: Consume Message (SCRAM-SHA-512)');
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
    console.log(`   2. Password synced to Kafka SCRAM (both SHA-256 & SHA-512)`);
    console.log(`   3. Producer authenticated with SCRAM-SHA-256`);
    console.log(`   4. Consumer authenticated with SCRAM-SHA-512`);
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
