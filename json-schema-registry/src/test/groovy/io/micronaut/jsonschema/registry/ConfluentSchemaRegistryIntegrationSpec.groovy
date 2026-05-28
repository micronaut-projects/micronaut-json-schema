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
package io.micronaut.jsonschema.registry

import io.micronaut.context.ApplicationContext
import org.opentest4j.TestAbortedException
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration

final class ConfluentSchemaRegistryIntegrationSpec extends Specification {

    private static final DockerImageName KAFKA_IMAGE = DockerImageName.parse("confluentinc/confluent-local:7.6.1")
            .asCompatibleSubstituteFor("confluentinc/cp-kafka")
    private static final DockerImageName SCHEMA_REGISTRY_IMAGE = DockerImageName.parse("confluentinc/cp-schema-registry:7.6.1")

    @Shared
    private Network network
    @Shared
    private ConfluentKafkaContainer kafka
    @Shared
    private GenericContainer<?> schemaRegistry

    void setupSpec() {
        try {
            network = Network.newNetwork()
            kafka = new ConfluentKafkaContainer(KAFKA_IMAGE)
                    .withNetwork(network)
                    .withListener("kafka:19092")
            kafka.start()
            schemaRegistry = new GenericContainer<>(SCHEMA_REGISTRY_IMAGE)
                    .withNetwork(network)
                    .withExposedPorts(8081)
                    .dependsOn(kafka)
                    .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
                    .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
                    .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:19092")
                    .waitingFor(Wait.forHttp("/subjects").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(2)))
            schemaRegistry.start()
        } catch (RuntimeException e) {
            cleanupSpec()
            throw new TestAbortedException("Confluent Schema Registry Testcontainers are not available", e)
        }
    }

    void cleanupSpec() {
        schemaRegistry?.stop()
        kafka?.stop()
        network?.close()
    }

    void "application authority registers JSON Schema in Confluent Schema Registry"() {
        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "json-schema.registry.enabled"        : "true",
                "json-schema.registry.authority"      : "application",
                "json-schema.registry.sr.enabled"     : "true",
                "json-schema.registry.sr.url"         : schemaRegistryUrl(),
                "json-schema.registry.oracle.enabled" : "false"
        ]) { ApplicationContext context ->
            context.getBean(JsonSchemaRegistryState).snapshot().outcomes()
        }

        then:
        outcomes.any { it.target() == "application.authority" && it.status() == JsonSchemaRegistryOutcomeStatus.EQUIVALENT }
        outcomes.any { it.target() == "sr" && it.status() == JsonSchemaRegistryOutcomeStatus.CREATED }

        and:
        new ConfluentSchemaRegistryClient(schemaRegistryUrl()).latestSchema(ApplicationAuthorityExample.name).present
    }

    private String schemaRegistryUrl() {
        "http://${schemaRegistry.host}:${schemaRegistry.getMappedPort(8081)}"
    }

    private static <T> T withContext(Map<String, Object> properties, Closure<T> callback) {
        ApplicationContext context = ApplicationContext.run(properties)
        try {
            callback(context)
        } finally {
            context.close()
        }
    }
}
