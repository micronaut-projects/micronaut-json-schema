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
package io.micronaut.jsonschema.registry.oracle;

import io.micronaut.context.ApplicationContext;
import io.micronaut.jsonschema.registry.DefaultJsonSchemaNormalizer;
import io.micronaut.jsonschema.registry.JsonSchemaCandidate;
import io.micronaut.jsonschema.registry.JsonSchemaRegistryDriftMode;
import io.micronaut.jsonschema.registry.JsonSchemaRegistryOutcome;
import io.micronaut.jsonschema.registry.JsonSchemaRegistryOutcomeStatus;
import io.micronaut.jsonschema.registry.JsonSchemaRegistryPolicyMode;
import io.micronaut.jsonschema.registry.LogicalSchema;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OracleDomainMaterializerIntegrationTest {
    private static final Map<String, Object> TEST_PROPERTIES = Map.of(
        "datasources.default.dialect", "oracle"
    );
    private static final String SCHEMA = """
        {"type":"object","properties":{"phase":{"type":"string","minLength":1}},"required":["phase"]}\
        """;
    private static final String DRIFT_SCHEMA = """
        {"type":"object","properties":{"phase":{"type":"string","minLength":2}},"required":["phase"]}\
        """;

    private static ApplicationContext context;
    private static String jdbcUrl;
    private static String username;
    private static String password;
    private static OracleDomainMaterializer materializer;
    private static String suffix;

    @BeforeAll
    static void setup() {
        try {
            context = ApplicationContext.run(TEST_PROPERTIES);
            jdbcUrl = context.getRequiredProperty("datasources.default.url", String.class);
            username = context.getRequiredProperty("datasources.default.username", String.class);
            password = context.getRequiredProperty("datasources.default.password", String.class);
        } catch (RuntimeException e) {
            if (context != null) {
                context.close();
            }
            throw new TestAbortedException("Oracle Test Resources are not available", e);
        }
        materializer = new OracleDomainMaterializer(new DefaultJsonSchemaNormalizer());
        suffix = Long.toString(System.currentTimeMillis(), 36).toUpperCase(Locale.ENGLISH);
    }

    @AfterAll
    static void cleanup() throws Exception {
        if (context != null && jdbcUrl != null) {
            withConnection(connection -> {
                dropDomain(connection, "REG_CREATE_" + suffix);
                dropDomain(connection, "REG_OBSERVE_" + suffix);
                dropDomain(connection, "REG_EQUIV_" + suffix);
                dropDomain(connection, "REG_DRIFT_" + suffix);
            });
        }
        if (context != null) {
            context.close();
        }
    }

    @Test
    void manageCreatesMissingDomain() throws Exception {
        String domainName = "REG_CREATE_" + suffix;
        JsonSchemaRegistryOutcome outcome = reconcile(domainName, SCHEMA, JsonSchemaRegistryPolicyMode.MANAGE, JsonSchemaRegistryDriftMode.REPORT);

        assertEquals(JsonSchemaRegistryOutcomeStatus.CREATED, outcome.status());
        assertFalse(outcome.failure());
        assertTrue(domainExists(domainName));
    }

    @Test
    void observeOnlyReportsMissingDomainWithoutCreatingIt() throws Exception {
        String domainName = "REG_OBSERVE_" + suffix;
        JsonSchemaRegistryOutcome outcome = reconcile(domainName, SCHEMA, JsonSchemaRegistryPolicyMode.OBSERVE_ONLY, JsonSchemaRegistryDriftMode.REPORT);

        assertEquals(JsonSchemaRegistryOutcomeStatus.MISSING_TARGET, outcome.status());
        assertFalse(outcome.failure());
        assertFalse(domainExists(domainName));
    }

    @Test
    void existingEquivalentDomainIsReportedEquivalent() throws Exception {
        String domainName = "REG_EQUIV_" + suffix;
        createDomain(domainName, SCHEMA);

        JsonSchemaRegistryOutcome outcome = reconcile(domainName, SCHEMA, JsonSchemaRegistryPolicyMode.MANAGE, JsonSchemaRegistryDriftMode.REPORT);

        assertEquals(JsonSchemaRegistryOutcomeStatus.EQUIVALENT, outcome.status());
        assertFalse(outcome.failure());
    }

    @Test
    void existingDifferentDomainIsReportedAsDrift() throws Exception {
        String domainName = "REG_DRIFT_" + suffix;
        createDomain(domainName, SCHEMA);

        JsonSchemaRegistryOutcome outcome = reconcile(domainName, DRIFT_SCHEMA, JsonSchemaRegistryPolicyMode.MANAGE, JsonSchemaRegistryDriftMode.FAIL);

        assertEquals(JsonSchemaRegistryOutcomeStatus.DRIFT, outcome.status());
        assertTrue(outcome.failure());
    }

    private static JsonSchemaRegistryOutcome reconcile(String domainName,
                                                       String schemaJson,
                                                       JsonSchemaRegistryPolicyMode policyMode,
                                                       JsonSchemaRegistryDriftMode driftMode) throws Exception {
        try (Connection connection = connection()) {
            return materializer.reconcile(
                connection,
                new OracleMaterializationRequest(
                    new JsonSchemaCandidate(new LogicalSchema(domainName, null, domainName), schemaJson, "test"),
                    domainName,
                    null,
                    Map.of(),
                    policyMode,
                    driftMode,
                    false
                )
            );
        }
    }

    private static void createDomain(String domainName, String schemaJson) throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE DOMAIN " + domainName + " AS JSON VALIDATE USING '" + schemaJson.replace("'", "''") + "'");
        }
    }

    private static boolean domainExists(String domainName) throws Exception {
        try (Connection connection = connection(); var statement = connection.prepareStatement("SELECT name FROM user_domains WHERE name = ?")) {
            statement.setString(1, domainName);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    private static void withConnection(SqlConsumer<Connection> consumer) throws Exception {
        try (Connection connection = connection()) {
            consumer.accept(connection);
        }
    }

    private static void dropDomain(Connection connection, String domainName) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP DOMAIN " + domainName + " FORCE");
        } catch (SQLException ignored) {
            // Best-effort cleanup for tests that may have failed before creating the domain.
        }
    }

    @FunctionalInterface
    private interface SqlConsumer<T> {
        void accept(T value) throws Exception;
    }
}
