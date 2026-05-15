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
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jsonschema.generator.oracle.OracleDualityJsonViewDiscoveryProvider;
import io.micronaut.jsonschema.registry.JsonSchemaRegistryOutcome;
import io.micronaut.jsonschema.registry.JsonSchemaRegistryOutcomeStatus;
import io.micronaut.jsonschema.registry.JsonSchemaRegistryReconciler;
import oracle.jdbc.datasource.impl.OracleDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OracleDualityViewAuthorityIntegrationTest {
    private static final String DUALITY_PROVIDER = OracleDualityJsonViewDiscoveryProvider.class.getName();

    private static ApplicationContext context;
    private static String jdbcUrl;
    private static String username;
    private static String password;
    private static String tableName;
    private static String viewName;

    @BeforeAll
    static void setup() throws Exception {
        String suffix = Long.toString(System.currentTimeMillis(), 36).toUpperCase(Locale.ENGLISH);
        tableName = "REG_DV_T_" + suffix;
        viewName = "REG_DV_V_" + suffix;
        try {
            context = ApplicationContext.run(Map.ofEntries(
                Map.entry("datasources.default.dialect", "oracle"),
                Map.entry("json-schema.registry.enabled", "true"),
                Map.entry("json-schema.registry.authority", "oracle"),
                Map.entry("json-schema.registry.oracle.enabled", "true"),
                Map.entry("json-schema.registry.oracle.authority.providers[0].name", "duality-views"),
                Map.entry("json-schema.registry.oracle.authority.providers[0].providerClassName", DUALITY_PROVIDER),
                Map.entry("json-schema.registry.oracle.authority.providers[0].options.include", viewName),
                Map.entry("json-schema.registry.sr.enabled", "false")
            ));
            jdbcUrl = context.getRequiredProperty("datasources.default.url", String.class);
            username = context.getRequiredProperty("datasources.default.username", String.class);
            password = context.getRequiredProperty("datasources.default.password", String.class);
            context.registerSingleton(
                DataSource.class,
                dataSource(jdbcUrl, username, password),
                Qualifiers.byName("default"),
                false
            );
            createDualityView();
        } catch (RuntimeException e) {
            if (context != null) {
                context.close();
            }
            throw new TestAbortedException("Oracle Test Resources are not available", e);
        } catch (SQLException e) {
            if (context != null) {
                context.close();
            }
            throw new TestAbortedException("Oracle duality views are not available", e);
        }
    }

    @AfterAll
    static void cleanup() throws Exception {
        if (context != null && jdbcUrl != null) {
            withConnection(connection -> {
                dropView(connection, viewName);
                dropTable(connection, tableName);
            });
        }
        if (context != null) {
            context.close();
        }
    }

    @Test
    void oracleAuthorityDiscoversDualityViewThroughConfiguredProvider() {
        JsonSchemaRegistryReconciler reconciler = context.getBean(JsonSchemaRegistryReconciler.class);

        List<JsonSchemaRegistryOutcome> outcomes = reconciler.reconcile();

        assertEquals(1, outcomes.size());
        JsonSchemaRegistryOutcome outcome = outcomes.get(0);
        assertEquals(JsonSchemaRegistryOutcomeStatus.EQUIVALENT, outcome.status());
        assertFalse(outcome.failure());
        assertEquals(viewName, outcome.logicalSchema().logicalFqcn());
        assertEquals(viewName, outcome.logicalSchema().oracleArtifactName());
        assertTrue(outcome.message().contains("DUALITY_VIEW:" + viewName));
    }

    private static void createDualityView() throws Exception {
        withConnection(connection -> {
            try (Statement statement = connection.createStatement()) {
                // Test fixture setup only. Registry materializers do not infer or create supporting tables.
                statement.execute("""
                    CREATE TABLE %s (
                        BUILDING_ID NUMBER(19) NOT NULL,
                        FLAT_ID NUMBER(19) NOT NULL,
                        UNIT_NAME VARCHAR2(100) NOT NULL,
                        FLOOR_NO NUMBER(10),
                        STATUS VARCHAR2(20),
                        CREATED_AT TIMESTAMP WITH TIME ZONE,
                        CONSTRAINT %s_PK PRIMARY KEY (BUILDING_ID, FLAT_ID)
                    )
                    """.formatted(tableName, tableName));
                statement.execute("""
                    CREATE JSON RELATIONAL DUALITY VIEW %s AS
                    SELECT JSON {
                        '_id': {'buildingId': ap.building_id, 'flatId': ap.flat_id},
                        'unitName': ap.unit_name,
                        'floorNo': ap.floor_no,
                        'status': ap.status,
                        'createdAt': ap.created_at
                    }
                    FROM %s ap WITH UPDATE INSERT DELETE
                    """.formatted(viewName, tableName));
            }
        });
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    private static OracleDataSource dataSource(String url, String username, String password) throws SQLException {
        OracleDataSource dataSource = new OracleDataSource();
        dataSource.setURL(url);
        dataSource.setUser(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    private static void withConnection(SqlConsumer<Connection> consumer) throws Exception {
        try (Connection connection = connection()) {
            consumer.accept(connection);
        }
    }

    private static void dropView(Connection connection, String name) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP VIEW " + name);
        } catch (SQLException ignored) {
            // Best-effort cleanup for tests that may have failed before creating the view.
        }
    }

    private static void dropTable(Connection connection, String name) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE " + name + " PURGE");
        } catch (SQLException ignored) {
            // Best-effort cleanup for tests that may have failed before creating the table.
        }
    }

    @FunctionalInterface
    private interface SqlConsumer<T> {
        void accept(T value) throws Exception;
    }
}
