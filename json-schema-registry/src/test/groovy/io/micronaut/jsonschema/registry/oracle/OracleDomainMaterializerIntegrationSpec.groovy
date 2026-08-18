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
package io.micronaut.jsonschema.registry.oracle

import io.micronaut.context.ApplicationContext
import io.micronaut.jsonschema.registry.DefaultJsonSchemaNormalizer
import io.micronaut.jsonschema.registry.JsonSchemaCandidate
import io.micronaut.jsonschema.registry.JsonSchemaRegistryDriftMode
import io.micronaut.jsonschema.registry.JsonSchemaRegistryOutcome
import io.micronaut.jsonschema.registry.JsonSchemaRegistryOutcomeStatus
import io.micronaut.jsonschema.registry.JsonSchemaRegistryPolicyMode
import io.micronaut.jsonschema.registry.LogicalSchema
import org.opentest4j.TestAbortedException
import spock.lang.Shared
import spock.lang.Specification

import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Statement
import java.sql.ResultSet

final class OracleDomainMaterializerIntegrationSpec extends Specification {

    private static final Map<String, Object> TEST_PROPERTIES = [
            "datasources.default.dialect": "oracle"
    ]
    private static final String SCHEMA = '{"type":"object","properties":{"phase":{"type":"string","minLength":1}},"required":["phase"]}'
    private static final String DRIFT_SCHEMA = '{"type":"object","properties":{"phase":{"type":"string","minLength":2}},"required":["phase"]}'
    private static final String CAST_SCHEMA = '{"type":"object","properties":{"publishedDate":{"extendedType":"timestamp"}},"required":["publishedDate"]}'

    @Shared
    private ApplicationContext context
    @Shared
    private String jdbcUrl
    @Shared
    private String username
    @Shared
    private String password
    @Shared
    private OracleDomainMaterializer materializer
    @Shared
    private String suffix

    void setupSpec() {
        try {
            context = ApplicationContext.run(TEST_PROPERTIES)
            jdbcUrl = context.getRequiredProperty("datasources.default.url", String)
            username = context.getRequiredProperty("datasources.default.username", String)
            password = context.getRequiredProperty("datasources.default.password", String)
        } catch (RuntimeException e) {
            context?.close()
            throw new TestAbortedException("Oracle Test Resources are not available", e)
        }
        materializer = new OracleDomainMaterializer(new DefaultJsonSchemaNormalizer())
        suffix = Long.toString(System.currentTimeMillis(), 36).toUpperCase(Locale.ENGLISH)
    }

    void cleanupSpec() {
        if (context != null && jdbcUrl != null) {
            withConnection { Connection connection ->
                dropDomain(connection, "REG_CREATE_${suffix}")
                dropDomain(connection, "REG_DRYRUN_${suffix}")
                dropDomain(connection, "REG_OBSERVE_${suffix}")
                dropDomain(connection, "REG_EQUIV_${suffix}")
                dropDomain(connection, "REG_DRIFT_${suffix}")
                dropTable(connection, "REG_CAST_TAB_${suffix}")
                dropDomain(connection, "REG_CAST_${suffix}")
                dropTable(connection, "REG_CAST_CONV_TAB_${suffix}")
                dropDomain(connection, "REG_CAST_CONV_${suffix}")
            }
        }
        context?.close()
    }

    void "manage creates missing domain"() {
        given:
        String domainName = "REG_CREATE_${suffix}"

        when:
        JsonSchemaRegistryOutcome outcome = reconcile(domainName, SCHEMA, JsonSchemaRegistryPolicyMode.MANAGE, JsonSchemaRegistryDriftMode.REPORT)

        then:
        outcome.status() == JsonSchemaRegistryOutcomeStatus.CREATED
        !outcome.failure()
        domainExists(domainName)
    }

    void "dry run reports missing domain creation without DDL"() {
        given:
        String domainName = "REG_DRYRUN_${suffix}"

        when:
        JsonSchemaRegistryOutcome outcome = reconcile(domainName, SCHEMA, JsonSchemaRegistryPolicyMode.MANAGE, JsonSchemaRegistryDriftMode.REPORT, true)

        then:
        outcome.status() == JsonSchemaRegistryOutcomeStatus.CREATED
        !outcome.failure()
        outcome.message().contains("[DRY-RUN]")
        !domainExists(domainName)
    }

    void "observe only reports missing domain without creating it"() {
        given:
        String domainName = "REG_OBSERVE_${suffix}"

        when:
        JsonSchemaRegistryOutcome outcome = reconcile(domainName, SCHEMA, JsonSchemaRegistryPolicyMode.OBSERVE_ONLY, JsonSchemaRegistryDriftMode.REPORT)

        then:
        outcome.status() == JsonSchemaRegistryOutcomeStatus.MISSING_TARGET
        !outcome.failure()
        !domainExists(domainName)
    }

    void "existing equivalent domain is reported equivalent"() {
        given:
        String domainName = "REG_EQUIV_${suffix}"
        createDomain(domainName, SCHEMA)

        when:
        JsonSchemaRegistryOutcome outcome = reconcile(domainName, SCHEMA, JsonSchemaRegistryPolicyMode.MANAGE, JsonSchemaRegistryDriftMode.REPORT)

        then:
        outcome.status() == JsonSchemaRegistryOutcomeStatus.EQUIVALENT
        !outcome.failure()
    }

    void "existing different domain is reported as drift"() {
        given:
        String domainName = "REG_DRIFT_${suffix}"
        createDomain(domainName, SCHEMA)

        when:
        JsonSchemaRegistryOutcome outcome = reconcile(domainName, DRIFT_SCHEMA, JsonSchemaRegistryPolicyMode.MANAGE, JsonSchemaRegistryDriftMode.FAIL)

        then:
        outcome.status() == JsonSchemaRegistryOutcomeStatus.DRIFT
        outcome.failure()
    }

    void "cast mode is part of domain equivalence"() {
        given:
        String domainName = "REG_CAST_${suffix}"

        when:
        JsonSchemaRegistryOutcome created = reconcile(domainName, CAST_SCHEMA, JsonSchemaRegistryPolicyMode.MANAGE, JsonSchemaRegistryDriftMode.REPORT, false, [castMode: "cast"])
        JsonSchemaRegistryOutcome strict = reconcile(domainName, CAST_SCHEMA, JsonSchemaRegistryPolicyMode.MANAGE, JsonSchemaRegistryDriftMode.REPORT, false, [:])
        JsonSchemaRegistryOutcome cast = reconcile(domainName, CAST_SCHEMA, JsonSchemaRegistryPolicyMode.MANAGE, JsonSchemaRegistryDriftMode.REPORT, false, [castMode: "cast"])

        then:
        created.status() == JsonSchemaRegistryOutcomeStatus.CREATED
        strict.status() == JsonSchemaRegistryOutcomeStatus.DRIFT
        cast.status() == JsonSchemaRegistryOutcomeStatus.EQUIVALENT
    }

    void "cast domain converts an ISO timestamp string into an Oracle JSON timestamp"() {
        given:
        String domainName = "REG_CAST_CONV_${suffix}"
        String tableName = "REG_CAST_CONV_TAB_${suffix}"
        JsonSchemaRegistryOutcome created = reconcile(domainName, CAST_SCHEMA, JsonSchemaRegistryPolicyMode.MANAGE, JsonSchemaRegistryDriftMode.REPORT, false, [castMode: "cast"])

        when:
        String jsonType
        withConnection { Connection connection ->
            Statement statement = connection.createStatement()
            try {
                statement.execute("CREATE TABLE ${tableName} (payload JSON DOMAIN ${domainName})")
                statement.execute("INSERT INTO ${tableName} VALUES ('{\"publishedDate\":\"2026-08-18T09:31:43.123\"}')")
                ResultSet resultSet = statement.executeQuery("SELECT json_value(payload, '\$.publishedDate.type()') FROM ${tableName}")
                try {
                    resultSet.next()
                    jsonType = resultSet.getString(1)
                } finally {
                    resultSet.close()
                }
            } finally {
                statement.close()
            }
        }

        then:
        created.status() == JsonSchemaRegistryOutcomeStatus.CREATED
        jsonType.equalsIgnoreCase("timestamp")

        cleanup:
        if (context != null && jdbcUrl != null) {
            withConnection { Connection connection ->
                dropTable(connection, tableName)
            }
        }
    }

    private JsonSchemaRegistryOutcome reconcile(String domainName,
                                               String schemaJson,
                                               JsonSchemaRegistryPolicyMode policyMode,
                                               JsonSchemaRegistryDriftMode driftMode) {
        reconcile(domainName, schemaJson, policyMode, driftMode, false)
    }

    private JsonSchemaRegistryOutcome reconcile(String domainName,
                                               String schemaJson,
                                               JsonSchemaRegistryPolicyMode policyMode,
                                               JsonSchemaRegistryDriftMode driftMode,
                                               boolean dryRun) {
        reconcile(domainName, schemaJson, policyMode, driftMode, dryRun, [:])
    }

    private JsonSchemaRegistryOutcome reconcile(String domainName,
                                               String schemaJson,
                                               JsonSchemaRegistryPolicyMode policyMode,
                                               JsonSchemaRegistryDriftMode driftMode,
                                               boolean dryRun,
                                               Map<String, String> options) {
        withConnection { Connection connection ->
            materializer.reconcile(
                    connection,
                    new OracleMaterializationRequest(
                            new JsonSchemaCandidate(new LogicalSchema(domainName, null, domainName), schemaJson, "test"),
                            domainName,
                            null,
                            options,
                            policyMode,
                            driftMode,
                            dryRun
                    )
            )
        }
    }

    private void createDomain(String domainName, String schemaJson) {
        withConnection { Connection connection ->
            Statement statement = connection.createStatement()
            try {
                statement.execute("CREATE DOMAIN ${domainName} AS JSON VALIDATE USING '${schemaJson.replace("'", "''")}'")
            } finally {
                statement.close()
            }
        }
    }

    private boolean domainExists(String domainName) {
        withConnection { Connection connection ->
            def statement = connection.prepareStatement("SELECT name FROM user_domains WHERE name = ?")
            try {
                statement.setString(1, domainName)
                def resultSet = statement.executeQuery()
                try {
                    resultSet.next()
                } finally {
                    resultSet.close()
                }
            } finally {
                statement.close()
            }
        }
    }

    private Connection connection() throws SQLException {
        DriverManager.getConnection(jdbcUrl, username, password)
    }

    private def <T> T withConnection(Closure<T> consumer) {
        Connection connection = connection()
        try {
            consumer(connection)
        } finally {
            connection.close()
        }
    }

    private static void dropDomain(Connection connection, String domainName) {
        Statement statement = connection.createStatement()
        try {
            statement.execute("DROP DOMAIN ${domainName} FORCE")
        } catch (SQLException ignored) {
            // Best-effort cleanup for tests that may have failed before creating the domain.
        } finally {
            statement.close()
        }
    }

    private static void dropTable(Connection connection, String tableName) {
        Statement statement = connection.createStatement()
        try {
            statement.execute("DROP TABLE ${tableName} PURGE")
        } catch (SQLException ignored) {
            // Best-effort cleanup for tests that may have failed before creating the table.
        } finally {
            statement.close()
        }
    }
}
