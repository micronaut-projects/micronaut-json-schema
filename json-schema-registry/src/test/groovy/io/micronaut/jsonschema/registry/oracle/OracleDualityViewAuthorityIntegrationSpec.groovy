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
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.jsonschema.registry.JsonSchemaRegistryOutcome
import io.micronaut.jsonschema.registry.JsonSchemaRegistryOutcomeStatus
import io.micronaut.jsonschema.registry.JsonSchemaRegistryReconciler
import oracle.jdbc.datasource.impl.OracleDataSource
import org.opentest4j.TestAbortedException
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Statement

final class OracleDualityViewAuthorityIntegrationSpec extends Specification {

    private static final String DUALITY_PROVIDER = OracleDualityJsonViewDiscoveryProvider.name

    @Shared
    private ApplicationContext context
    @Shared
    private String jdbcUrl
    @Shared
    private String username
    @Shared
    private String password
    @Shared
    private String tableName
    @Shared
    private String viewName

    void setupSpec() {
        String suffix = Long.toString(System.currentTimeMillis(), 36).toUpperCase(Locale.ENGLISH)
        tableName = "REG_DV_T_${suffix}"
        viewName = "REG_DV_V_${suffix}"
        try {
            context = ApplicationContext.run([
                    "datasources.default.dialect"                                      : "oracle",
                    "json-schema.registry.enabled"                                     : "true",
                    "json-schema.registry.authority"                                   : "oracle",
                    "json-schema.registry.oracle.enabled"                              : "true",
                    "json-schema.registry.oracle.authority.providers[0].name"          : "duality-views",
                    "json-schema.registry.oracle.authority.providers[0].providerClassName": DUALITY_PROVIDER,
                    "json-schema.registry.oracle.authority.providers[0].options.include": viewName,
                    "json-schema.registry.sr.enabled"                                  : "false"
            ])
            jdbcUrl = context.getRequiredProperty("datasources.default.url", String)
            username = context.getRequiredProperty("datasources.default.username", String)
            password = context.getRequiredProperty("datasources.default.password", String)
            context.registerSingleton(
                    DataSource,
                    dataSource(jdbcUrl, username, password),
                    Qualifiers.byName("default"),
                    false
            )
            createDualityView()
        } catch (RuntimeException e) {
            context?.close()
            throw new TestAbortedException("Oracle Test Resources are not available", e)
        } catch (SQLException e) {
            context?.close()
            throw new TestAbortedException("Oracle duality views are not available", e)
        }
    }

    void cleanupSpec() {
        if (context != null && jdbcUrl != null) {
            withConnection { Connection connection ->
                dropView(connection, viewName)
                dropTable(connection, tableName)
            }
        }
        context?.close()
    }

    void "Oracle authority discovers duality view through configured provider"() {
        when:
        List<JsonSchemaRegistryOutcome> outcomes = context.getBean(JsonSchemaRegistryReconciler).reconcile()

        then:
        outcomes.size() == 1
        JsonSchemaRegistryOutcome outcome = outcomes[0]
        outcome.status() == JsonSchemaRegistryOutcomeStatus.EQUIVALENT
        !outcome.failure()
        outcome.logicalSchema().logicalFqcn() == viewName
        outcome.logicalSchema().oracleArtifactName() == viewName
        outcome.message().contains("DUALITY_VIEW:${viewName}")
    }

    private void createDualityView() {
        withConnection { Connection connection ->
            Statement statement = connection.createStatement()
            try {
                // Test fixture setup only. Registry materializers do not infer or create supporting tables.
                statement.execute("""
                    CREATE TABLE ${tableName} (
                        BUILDING_ID NUMBER(19) NOT NULL,
                        FLAT_ID NUMBER(19) NOT NULL,
                        UNIT_NAME VARCHAR2(100) NOT NULL,
                        FLOOR_NO NUMBER(10),
                        STATUS VARCHAR2(20),
                        CREATED_AT TIMESTAMP WITH TIME ZONE,
                        CONSTRAINT ${tableName}_PK PRIMARY KEY (BUILDING_ID, FLAT_ID)
                    )
                    """)
                statement.execute("""
                    CREATE JSON RELATIONAL DUALITY VIEW ${viewName} AS
                    SELECT JSON {
                        '_id': {'buildingId': ap.building_id, 'flatId': ap.flat_id},
                        'unitName': ap.unit_name,
                        'floorNo': ap.floor_no,
                        'status': ap.status,
                        'createdAt': ap.created_at
                    }
                    FROM ${tableName} ap WITH UPDATE INSERT DELETE
                    """)
            } finally {
                statement.close()
            }
        }
    }

    private Connection connection() throws SQLException {
        DriverManager.getConnection(jdbcUrl, username, password)
    }

    private static OracleDataSource dataSource(String url, String username, String password) throws SQLException {
        OracleDataSource dataSource = new OracleDataSource()
        dataSource.URL = url
        dataSource.user = username
        dataSource.password = password
        dataSource
    }

    private def <T> T withConnection(Closure<T> consumer) {
        Connection connection = connection()
        try {
            consumer(connection)
        } finally {
            connection.close()
        }
    }

    private static void dropView(Connection connection, String name) {
        Statement statement = connection.createStatement()
        try {
            statement.execute("DROP VIEW ${name}")
        } catch (SQLException ignored) {
            // Best-effort cleanup for tests that may have failed before creating the view.
        } finally {
            statement.close()
        }
    }

    private static void dropTable(Connection connection, String name) {
        Statement statement = connection.createStatement()
        try {
            statement.execute("DROP TABLE ${name} PURGE")
        } catch (SQLException ignored) {
            // Best-effort cleanup for tests that may have failed before creating the table.
        } finally {
            statement.close()
        }
    }
}
