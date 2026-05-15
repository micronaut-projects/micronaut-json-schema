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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultJsonSchemaRegistryReconcilerSrTest {
    private HttpServer server;
    private final List<String> registrations = new ArrayList<>();

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void srAuthorityValidatesLatestSchemaWhenOracleTargetDisabled() throws Exception {
        startServer(Map.of(
            "/subjects/com.acme.Order/versions/latest", response(200, "{\"schema\":\"{\\\"type\\\":\\\"object\\\"}\"}")
        ));

        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "json-schema.registry.enabled", "true",
            "json-schema.registry.authority", "sr",
            "json-schema.registry.sr.enabled", "true",
            "json-schema.registry.sr.url", serverUrl(),
            "json-schema.registry.sr.subjects[0]", "com.acme.Order",
            "json-schema.registry.oracle.enabled", "false"
        ))) {
            List<JsonSchemaRegistryOutcome> outcomes = context.getBean(JsonSchemaRegistryReconciler.class).reconcile();

            assertEquals(1, outcomes.size());
            assertEquals(JsonSchemaRegistryOutcomeStatus.EQUIVALENT, outcomes.get(0).status());
            assertEquals("sr.authority", outcomes.get(0).target());
        }
    }

    @Test
    void srAuthorityDiscoversSubjectsByPrefix() throws Exception {
        startServer(Map.of(
            "/subjects", response(200, "[\"com.acme.Order\",\"other.Ignore\"]"),
            "/subjects/com.acme.Order/versions/latest", response(200, "{\"schema\":\"{\\\"type\\\":\\\"object\\\"}\"}")
        ));

        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "json-schema.registry.enabled", "true",
            "json-schema.registry.authority", "sr",
            "json-schema.registry.sr.enabled", "true",
            "json-schema.registry.sr.url", serverUrl(),
            "json-schema.registry.naming.subject.prefix", "com.acme.",
            "json-schema.registry.oracle.enabled", "false"
        ))) {
            List<JsonSchemaRegistryOutcome> outcomes = context.getBean(JsonSchemaRegistryReconciler.class).reconcile();

            assertEquals(1, outcomes.size());
            assertEquals("Order", outcomes.get(0).logicalSchema().logicalFqcn());
            assertEquals("com.acme.Order", outcomes.get(0).logicalSchema().subject());
            assertEquals(JsonSchemaRegistryOutcomeStatus.EQUIVALENT, outcomes.get(0).status());
        }
    }

    @Test
    void applicationAuthorityRegistersMissingSrSubject() throws Exception {
        startServer(Map.of(
            "/subjects/io.micronaut.jsonschema.registry.ApplicationAuthorityExample/versions/latest", response(404, "{}")
        ));

        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "json-schema.registry.enabled", "true",
            "json-schema.registry.authority", "application",
            "json-schema.registry.sr.enabled", "true",
            "json-schema.registry.sr.url", serverUrl(),
            "json-schema.registry.oracle.enabled", "false"
        ))) {
            List<JsonSchemaRegistryOutcome> outcomes = context.getBean(JsonSchemaRegistryReconciler.class).reconcile();

            assertTrue(outcomes.stream().anyMatch(outcome -> outcome.target().equals("application.authority")
                && outcome.status() == JsonSchemaRegistryOutcomeStatus.EQUIVALENT));
            assertTrue(outcomes.stream().anyMatch(outcome -> outcome.status() == JsonSchemaRegistryOutcomeStatus.CREATED));
            assertFalse(registrations.isEmpty());
            assertTrue(registrations.get(0).contains("\"schemaType\":\"JSON\""));
        }
    }

    @Test
    void applicationAuthorityReportsMissingSrSubjectInObserveOnly() throws Exception {
        startServer(Map.of(
            "/subjects/io.micronaut.jsonschema.registry.ApplicationAuthorityExample/versions/latest", response(404, "{}")
        ));

        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "json-schema.registry.enabled", "true",
            "json-schema.registry.authority", "application",
            "json-schema.registry.sr.enabled", "true",
            "json-schema.registry.sr.url", serverUrl(),
            "json-schema.registry.sr.policy.mode", "observe_only",
            "json-schema.registry.oracle.enabled", "false"
        ))) {
            List<JsonSchemaRegistryOutcome> outcomes = context.getBean(JsonSchemaRegistryReconciler.class).reconcile();

            assertTrue(outcomes.stream().anyMatch(outcome -> outcome.target().equals("sr")
                && outcome.status() == JsonSchemaRegistryOutcomeStatus.MISSING_TARGET));
            assertTrue(registrations.isEmpty());
        }
    }

    @Test
    void applicationAuthorityRegistersNewSrVersionWhenSubjectDrifts() throws Exception {
        startServer(Map.of(
            "/subjects/io.micronaut.jsonschema.registry.ApplicationAuthorityExample/versions/latest",
            response(200, "{\"schema\":\"{\\\"type\\\":\\\"string\\\"}\"}")
        ));

        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "json-schema.registry.enabled", "true",
            "json-schema.registry.authority", "application",
            "json-schema.registry.sr.enabled", "true",
            "json-schema.registry.sr.url", serverUrl(),
            "json-schema.registry.oracle.enabled", "false"
        ))) {
            List<JsonSchemaRegistryOutcome> outcomes = context.getBean(JsonSchemaRegistryReconciler.class).reconcile();

            assertTrue(outcomes.stream().anyMatch(outcome -> outcome.target().equals("sr")
                && outcome.status() == JsonSchemaRegistryOutcomeStatus.CREATED));
            assertEquals(1, registrations.size());
            assertTrue(registrations.get(0).contains("\"schemaType\":\"JSON\""));
        }
    }

    @Test
    void applicationAuthorityReportsSrDriftInObserveOnly() throws Exception {
        startServer(Map.of(
            "/subjects/io.micronaut.jsonschema.registry.ApplicationAuthorityExample/versions/latest",
            response(200, "{\"schema\":\"{\\\"type\\\":\\\"string\\\"}\"}")
        ));

        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "json-schema.registry.enabled", "true",
            "json-schema.registry.authority", "application",
            "json-schema.registry.sr.enabled", "true",
            "json-schema.registry.sr.url", serverUrl(),
            "json-schema.registry.sr.policy.mode", "observe_only",
            "json-schema.registry.oracle.enabled", "false"
        ))) {
            List<JsonSchemaRegistryOutcome> outcomes = context.getBean(JsonSchemaRegistryReconciler.class).reconcile();

            assertTrue(outcomes.stream().anyMatch(outcome -> outcome.target().equals("sr")
                && outcome.status() == JsonSchemaRegistryOutcomeStatus.DRIFT));
            assertTrue(registrations.isEmpty());
        }
    }

    @Test
    void applicationAuthorityValidatesGeneratedSchemaWhenTargetsDisabled() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "json-schema.registry.enabled", "true",
            "json-schema.registry.authority", "application",
            "json-schema.registry.sr.enabled", "false",
            "json-schema.registry.oracle.enabled", "false"
        ))) {
            List<JsonSchemaRegistryOutcome> outcomes = context.getBean(JsonSchemaRegistryReconciler.class).reconcile();

            assertEquals(1, outcomes.size());
            assertEquals("application.authority", outcomes.get(0).target());
            assertEquals(JsonSchemaRegistryOutcomeStatus.EQUIVALENT, outcomes.get(0).status());
        }
    }

    private void startServer(Map<String, FixedResponse> responses) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                registrations.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                send(exchange, 200, "{\"id\":1}");
                return;
            }
            FixedResponse response = responses.get(exchange.getRequestURI().getPath());
            if (response == null) {
                send(exchange, 404, "{}");
            } else {
                send(exchange, response.status(), response.body());
            }
        });
        server.start();
    }

    private String serverUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private static FixedResponse response(int status, String body) {
        return new FixedResponse(status, body);
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private record FixedResponse(int status, String body) {
    }
}
