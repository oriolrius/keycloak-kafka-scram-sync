package com.miimetiq.keycloak.sync.integration;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Integration test resource that manages Kafka and Keycloak Testcontainers.
 * This resource is used by integration tests to spin up real dependencies.
 *
 * Uses Apache Kafka 4.1.0 in KRaft mode (no Zookeeper needed).
 */
public class IntegrationTestResource implements QuarkusTestResourceLifecycleManager {

    private static final String KAFKA_IMAGE = "apache/kafka:4.1.0";
    private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:26.4";
    private static final int KAFKA_INTERNAL_PORT = 9092;
    private static final int KAFKA_EXTERNAL_PORT = 29092;  // Fixed port for host access

    private GenericContainer<?> kafka;
    private KeycloakContainer keycloak;
    private Network network;

    @Override
    public Map<String, String> start() {
        // Create network
        network = Network.newNetwork();

        // Start Apache Kafka 4.1.0 in KRaft mode (no Zookeeper)
        // Note: We configure for single-broker mode suitable for testing
        // Uses fixed port 29092 for deterministic external access
        kafka = new GenericContainer<>(DockerImageName.parse(KAFKA_IMAGE))
                .withNetwork(network)
                .withNetworkAliases("kafka")
                .withExposedPorts(KAFKA_INTERNAL_PORT)
                .withCreateContainerCmdModifier(cmd -> {
                    cmd.withHostConfig(
                        new HostConfig().withPortBindings(
                            new PortBinding(Ports.Binding.bindPort(KAFKA_EXTERNAL_PORT),
                                new ExposedPort(KAFKA_INTERNAL_PORT))
                        )
                    );
                })
                .withEnv("KAFKA_NODE_ID", "1")
                .withEnv("KAFKA_PROCESS_ROLES", "broker,controller")
                .withEnv("KAFKA_LISTENERS", "PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093")
                .withEnv("KAFKA_ADVERTISED_LISTENERS", "PLAINTEXT://localhost:29092")
                .withEnv("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER")
                .withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT")
                .withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", "PLAINTEXT")
                .withEnv("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@localhost:9093")
                .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
                .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
                .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
                .withEnv("KAFKA_LOG_DIRS", "/tmp/kraft-combined-logs")
                .withEnv("CLUSTER_ID", "MkU3OEVBNTcwNTJENDM2Qk")
                .waitingFor(Wait.forLogMessage(".*Kafka Server started.*", 1)
                        .withStartupTimeout(Duration.ofSeconds(90)));
        kafka.start();

        // Start Keycloak
        keycloak = new KeycloakContainer(KEYCLOAK_IMAGE)
                .withNetwork(network)
                .withAdminUsername("admin")
                .withAdminPassword("admin");
        keycloak.start();

        // Return configuration properties for the application
        Map<String, String> config = new HashMap<>();

        // Kafka configuration - use fixed external port for host access
        String kafkaBootstrapServers = String.format("localhost:%d", KAFKA_EXTERNAL_PORT);
        config.put("kafka.bootstrap-servers", kafkaBootstrapServers);
        config.put("kafka.security-protocol", "PLAINTEXT");
        // Fix timeout configuration: default-api-timeout must be >= request-timeout
        config.put("kafka.request-timeout-ms", "10000");
        config.put("kafka.connection-timeout-ms", "15000");

        // Keycloak configuration
        config.put("keycloak.url", keycloak.getAuthServerUrl());
        config.put("keycloak.realm", "master");
        config.put("keycloak.client-id", "admin-cli");
        config.put("keycloak.admin-username", "admin");
        config.put("keycloak.admin-password", "admin");

        // Use random port for test to avoid conflicts
        config.put("quarkus.http.test-port", "0");

        return config;
    }

    @Override
    public void stop() {
        if (kafka != null) {
            kafka.stop();
        }
        if (keycloak != null) {
            keycloak.stop();
        }
        if (network != null) {
            network.close();
        }
    }
}
