/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.jsonschema.registry;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfluentSchemaRegistryIntegrationTest {
    private static final DockerImageName KAFKA_IMAGE = DockerImageName.parse("confluentinc/confluent-local:7.6.1")
        .asCompatibleSubstituteFor("confluentinc/cp-kafka");
    private static final DockerImageName SCHEMA_REGISTRY_IMAGE = DockerImageName.parse("confluentinc/cp-schema-registry:7.6.1");

    private static Network network;
    private static ConfluentKafkaContainer kafka;
    private static GenericContainer<?> schemaRegistry;

    @BeforeAll
    static void setup() {
        try {
            network = Network.newNetwork();
            kafka = new ConfluentKafkaContainer(KAFKA_IMAGE)
                .withNetwork(network)
                .withListener("kafka:19092");
            kafka.start();
            schemaRegistry = new GenericContainer<>(SCHEMA_REGISTRY_IMAGE)
                .withNetwork(network)
                .withExposedPorts(8081)
                .dependsOn(kafka)
                .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
                .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
                .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:19092")
                .waitingFor(Wait.forHttp("/subjects").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(2)));
            schemaRegistry.start();
        } catch (RuntimeException e) {
            cleanup();
            throw new TestAbortedException("Confluent Schema Registry Testcontainers are not available", e);
        }
    }

    @AfterAll
    static void cleanup() {
        if (schemaRegistry != null) {
            schemaRegistry.stop();
        }
        if (kafka != null) {
            kafka.stop();
        }
        if (network != null) {
            network.close();
        }
    }

    @Test
    void applicationAuthorityRegistersJsonSchemaInConfluentSchemaRegistry() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "json-schema.registry.enabled", "true",
            "json-schema.registry.authority", "application",
            "json-schema.registry.sr.enabled", "true",
            "json-schema.registry.sr.url", schemaRegistryUrl(),
            "json-schema.registry.oracle.enabled", "false"
        ))) {
            List<JsonSchemaRegistryOutcome> outcomes = context.getBean(JsonSchemaRegistryService.class).resync();

            assertTrue(outcomes.stream().anyMatch(outcome -> outcome.target().equals("application.authority")
                && outcome.status() == JsonSchemaRegistryOutcomeStatus.EQUIVALENT));
            assertTrue(outcomes.stream().anyMatch(outcome -> outcome.target().equals("sr")
                && outcome.status() == JsonSchemaRegistryOutcomeStatus.CREATED));
        }

        ConfluentSchemaRegistryClient client = new ConfluentSchemaRegistryClient(schemaRegistryUrl());
        try {
            assertTrue(client.latestSchema(ApplicationAuthorityExample.class.getName()).isPresent());
        } catch (Exception e) {
            throw new AssertionError("Registered schema was not readable from Schema Registry", e);
        }
    }

    private static String schemaRegistryUrl() {
        return "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getMappedPort(8081);
    }
}
