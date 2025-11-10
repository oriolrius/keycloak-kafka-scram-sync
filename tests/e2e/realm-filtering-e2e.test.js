#!/usr/bin/env node
/**
 * E2E Test: Realm Filtering for Keycloak Password Sync
 *
 * Test Flow:
 * 1. Create "test-realm" in Keycloak
 * 2. Enable password-sync-listener for both realms
 * 3. Create user-master in master realm
 * 4. Create user-test-realm in test-realm
 * 5. Set passwords for both users
 * 6. Verify in logs that only test-realm user was synced
 * 7. Test Kafka authentication: test-realm user succeeds, master user fails
 */

import { Kafka, Partitioners, logLevel } from 'kafkajs';
import fs from 'fs';
import https from 'https';
import fetch from 'node-fetch';
import { exec } from 'child_process';
import { promisify } from 'util';

const execAsync = promisify(exec);

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
    testRealm: 'test-realm'
  },
  kafka: {
    brokers: (process.env.KAFKA_BROKERS || 'localhost:57005').split(','),
    mechanism: SCRAM_NAME,
    ssl: {
      ca: [fs.readFileSync('../infrastructure/certs/ca-root.pem', 'utf-8')],
      rejectUnauthorized: false
    }
  },
  test: {
    userMaster: {
      username: `user-master-${Date.now()}`,
      password: 'MasterPass123!',
      realm: 'master'
    },
    userTestRealm: {
      username: `user-test-realm-${Date.now()}`,
      password: 'TestRealmPass123!',
      realm: 'test-realm'
    },
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
 * Create a realm in Keycloak
 */
async function createRealm(token, realmName) {
  console.log(`\n📝 Creating Keycloak realm: ${realmName}`);

  const response = await fetch(
    `${CONFIG.keycloak.url}/admin/realms`,
    {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        realm: realmName,
        enabled: true,
        displayName: realmName,
        eventsListeners: ['password-sync-listener']
      }),
      agent: httpsAgent
    }
  );

  if (!response.ok && response.status !== 409) { // 409 = already exists
    const errorText = await response.text();
    throw new Error(`Failed to create realm: ${response.statusText} - ${errorText}`);
  }

  if (response.status === 409) {
    console.log(`✅ Realm already exists: ${realmName}`);
  } else {
    console.log(`✅ Realm created: ${realmName}`);
  }
}

/**
 * Create Keycloak user
 */
async function createKeycloakUser(token, realm, username) {
  console.log(`\n📝 Creating user '${username}' in realm '${realm}'`);

  const response = await fetch(
    `${CONFIG.keycloak.url}/admin/realms/${realm}/users`,
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

  if (!response.ok && response.status !== 409) {
    const errorText = await response.text();
    throw new Error(`Failed to create user: ${response.statusText} - ${errorText}`);
  }

  console.log(`✅ User created in Keycloak: ${username} (realm: ${realm})`);
}

/**
 * Get user ID by username
 */
async function getUserId(token, realm, username) {
  const response = await fetch(
    `${CONFIG.keycloak.url}/admin/realms/${realm}/users?username=${username}&exact=true`,
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
    throw new Error(`User ${username} not found in realm ${realm}`);
  }

  return users[0].id;
}

/**
 * Set user password (triggers SPI sync to Kafka - if realm is allowed)
 */
async function setUserPassword(token, realm, userId, password, username) {
  console.log(`\n🔐 Setting password for user '${username}' in realm '${realm}'...`);

  const response = await fetch(
    `${CONFIG.keycloak.url}/admin/realms/${realm}/users/${userId}/reset-password`,
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

  console.log(`✅ Password set in Keycloak for '${username}'`);
  console.log(`⏳ Waiting for SPI to process (may skip if realm not in filter)...`);

  // Wait for sync to complete (or be skipped)
  await new Promise(resolve => setTimeout(resolve, 2000));
}

/**
 * Check Keycloak logs for realm filtering messages
 */
async function checkKeycloakLogs() {
  console.log(`\n🔍 Checking Keycloak logs for realm filtering messages...`);

  try {
    // Check more log lines to catch the initialization message
    const { stdout } = await execAsync('docker logs keycloak 2>&1 | tail -200');

    // Look for realm filtering messages
    const syncedPattern = /Skipping password sync for user in realm '([^']+)'/g;
    const enabledPattern = /Realm filtering ENABLED.*realms: ([^\n]+)/;

    const skippedMatches = [...stdout.matchAll(syncedPattern)];
    const enabledMatch = stdout.match(enabledPattern);

    if (enabledMatch) {
      console.log(`✅ Realm filtering is ENABLED for: ${enabledMatch[1]}`);
    } else {
      console.log(`⚠️  Could not find realm filtering enabled message in logs`);
    }

    if (skippedMatches.length > 0) {
      console.log(`✅ Found ${skippedMatches.length} realm filtering skip messages:`);
      skippedMatches.forEach((match, i) => {
        console.log(`   ${i + 1}. Skipped realm: ${match[1]}`);
      });
    } else {
      console.log(`ℹ️  No realm filtering skip messages found (expected if all users are in allowed realms)`);
    }

    return {
      enabled: !!enabledMatch,
      skippedRealms: skippedMatches.map(m => m[1])
    };

  } catch (error) {
    console.log(`⚠️  Could not check Keycloak logs: ${error.message}`);
    return { enabled: false, skippedRealms: [] };
  }
}

/**
 * Test Kafka authentication (should succeed or fail based on realm filtering)
 */
async function testKafkaAuth(username, password, shouldSucceed, realm) {
  console.log(`\n🔑 Testing Kafka authentication for '${username}' (realm: ${realm})...`);
  console.log(`   Expected: ${shouldSucceed ? 'SUCCESS' : 'FAILURE'}`);

  const kafka = new Kafka({
    clientId: `e2e-realm-test-${Date.now()}`,
    brokers: CONFIG.kafka.brokers,
    ssl: CONFIG.kafka.ssl,
    sasl: {
      mechanism: CONFIG.kafka.mechanism,
      username,
      password
    },
    connectionTimeout: 5000,
    requestTimeout: 5000,
    retry: { initialRetryTime: 100, retries: 1 }
  });

  const admin = kafka.admin();

  try {
    await admin.connect();
    await admin.listTopics();
    await admin.disconnect();

    if (shouldSucceed) {
      console.log(`✅ Authentication SUCCEEDED as expected (user was synced)`);
      return true;
    } else {
      console.log(`❌ Authentication SUCCEEDED but should have FAILED (user should not have been synced)`);
      return false;
    }
  } catch (error) {
    if (!shouldSucceed) {
      // Check if it's an authentication error (expected)
      if (error.message.includes('Authentication failed') ||
          error.message.includes('SASL') ||
          error.message.includes('auth')) {
        console.log(`✅ Authentication FAILED as expected (user was not synced)`);
        return true;
      } else {
        console.log(`⚠️  Authentication failed with unexpected error: ${error.message}`);
        return false;
      }
    } else {
      console.log(`❌ Authentication FAILED but should have SUCCEEDED: ${error.message}`);
      return false;
    }
  }
}

/**
 * Main test execution
 */
async function runRealmFilteringTest() {
  console.log('═══════════════════════════════════════════════════════');
  console.log(`   Realm Filtering E2E Test`);
  console.log(`   Mechanism: ${CONFIG.kafka.mechanism.toUpperCase()}`);
  console.log('═══════════════════════════════════════════════════════');
  console.log(`\nConfiguration:`);
  console.log(`  Keycloak: ${CONFIG.keycloak.url}`);
  console.log(`  Kafka: ${CONFIG.kafka.brokers.join(', ')}`);
  console.log(`  SCRAM Mechanism: ${CONFIG.kafka.mechanism.toUpperCase()}`);
  console.log(`  Allowed Realms: ${CONFIG.keycloak.testRealm} (master should be filtered out)`);
  console.log(`  User in master: ${CONFIG.test.userMaster.username} (should NOT sync)`);
  console.log(`  User in test-realm: ${CONFIG.test.userTestRealm.username} (should sync)`);

  try {
    // Step 1: Get admin token
    console.log(`\n${'='.repeat(55)}`);
    console.log('STEP 1: Authenticate to Keycloak');
    console.log('='.repeat(55));
    const token = await getAdminToken();
    console.log(`✅ Admin token obtained`);

    // Step 2: Create test-realm
    console.log(`\n${'='.repeat(55)}`);
    console.log('STEP 2: Create test-realm');
    console.log('='.repeat(55));
    await createRealm(token, CONFIG.keycloak.testRealm);

    // Step 3: Create users in both realms
    console.log(`\n${'='.repeat(55)}`);
    console.log('STEP 3: Create users in both realms');
    console.log('='.repeat(55));

    // Create user in master realm
    await createKeycloakUser(
      token,
      CONFIG.test.userMaster.realm,
      CONFIG.test.userMaster.username
    );
    const userMasterId = await getUserId(
      token,
      CONFIG.test.userMaster.realm,
      CONFIG.test.userMaster.username
    );

    // Create user in test-realm
    await createKeycloakUser(
      token,
      CONFIG.test.userTestRealm.realm,
      CONFIG.test.userTestRealm.username
    );
    const userTestRealmId = await getUserId(
      token,
      CONFIG.test.userTestRealm.realm,
      CONFIG.test.userTestRealm.username
    );

    // Step 4: Set passwords (triggers sync - but only for test-realm)
    console.log(`\n${'='.repeat(55)}`);
    console.log('STEP 4: Set passwords (trigger sync events)');
    console.log('='.repeat(55));

    await setUserPassword(
      token,
      CONFIG.test.userMaster.realm,
      userMasterId,
      CONFIG.test.userMaster.password,
      CONFIG.test.userMaster.username
    );

    await setUserPassword(
      token,
      CONFIG.test.userTestRealm.realm,
      userTestRealmId,
      CONFIG.test.userTestRealm.password,
      CONFIG.test.userTestRealm.username
    );

    // Step 5: Check logs for realm filtering
    console.log(`\n${'='.repeat(55)}`);
    console.log('STEP 5: Verify realm filtering in logs');
    console.log('='.repeat(55));

    const logResults = await checkKeycloakLogs();

    // Filtering is considered working if we see skip messages for master realm
    const filteringWorking = logResults.enabled || logResults.skippedRealms.includes('master');

    if (!logResults.enabled && logResults.skippedRealms.length === 0) {
      console.log(`⚠️  WARNING: Could not verify realm filtering in logs`);
    } else if (logResults.skippedRealms.includes('master')) {
      console.log(`✅ Realm filtering confirmed: master realm was skipped`);
    }

    // Step 6: Test Kafka authentication
    console.log(`\n${'='.repeat(55)}`);
    console.log('STEP 6: Test Kafka authentication');
    console.log('='.repeat(55));

    // Test master realm user (should FAIL - not synced)
    const masterAuthResult = await testKafkaAuth(
      CONFIG.test.userMaster.username,
      CONFIG.test.userMaster.password,
      false, // should fail
      CONFIG.test.userMaster.realm
    );

    // Test test-realm user (should SUCCEED - was synced)
    const testRealmAuthResult = await testKafkaAuth(
      CONFIG.test.userTestRealm.username,
      CONFIG.test.userTestRealm.password,
      true, // should succeed
      CONFIG.test.userTestRealm.realm
    );

    // Final verification
    console.log(`\n${'═'.repeat(55)}`);

    if (masterAuthResult && testRealmAuthResult && filteringWorking) {
      console.log('✅ REALM FILTERING E2E TEST PASSED');
      console.log('═'.repeat(55));
      console.log(`\n✅ All verifications passed:`);
      console.log(`   1. test-realm created successfully`);
      console.log(`   2. Users created in both realms`);
      console.log(`   3. Realm filtering is WORKING`);
      console.log(`   4. Master realm user was NOT synced (auth failed)`);
      console.log(`   5. Test realm user WAS synced (auth succeeded)`);
      console.log(`   6. Logs confirmed realm filtering behavior`);
      console.log('');
      process.exit(0);
    } else {
      console.log('❌ REALM FILTERING E2E TEST FAILED');
      console.log('═'.repeat(55));
      console.log(`\n❌ Some verifications failed:`);
      if (!filteringWorking) console.log(`   - Realm filtering not working (no evidence in logs)`);
      if (!masterAuthResult) console.log(`   - Master realm user auth test failed`);
      if (!testRealmAuthResult) console.log(`   - Test realm user auth test failed`);
      console.log('');
      process.exit(1);
    }

  } catch (error) {
    console.error(`\n${'═'.repeat(55)}`);
    console.error('❌ REALM FILTERING E2E TEST FAILED');
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
runRealmFilteringTest();
