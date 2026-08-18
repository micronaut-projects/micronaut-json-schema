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

import io.micronaut.jsonschema.generator.discovery.DiscoveryResult
import io.micronaut.jsonschema.generator.discovery.JsonSchemaRecordsLogger
import io.micronaut.jsonschema.generator.discovery.SchemaDiscoveryContext
import io.micronaut.jsonschema.generator.discovery.SourceSpec
import io.micronaut.jsonschema.generator.oracle.OracleDualityViewSchemaDiscoveryProvider
import io.micronaut.jsonschema.registry.DefaultJsonSchemaNormalizer
import io.micronaut.jsonschema.registry.JsonSchemaCandidate
import io.micronaut.jsonschema.registry.JsonSchemaRegistryDriftMode
import io.micronaut.jsonschema.registry.JsonSchemaRegistryOutcome
import io.micronaut.jsonschema.registry.JsonSchemaRegistryOutcomeStatus
import io.micronaut.jsonschema.registry.JsonSchemaRegistryPolicyMode
import io.micronaut.jsonschema.registry.LogicalSchema
import spock.lang.Specification

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException

final class OracleDualityJsonViewMaterializerSpec extends Specification {

    void "remote ref is projection incompatible for duality views"() {
        given:
        OracleDualityJsonViewMaterializer materializer = new OracleDualityJsonViewMaterializer(new DefaultJsonSchemaNormalizer())

        when:
        Optional<JsonSchemaRegistryOutcome> outcome = materializer.projectionCompatibility(
                request([viewName: "ORDER_DV"], JsonSchemaRegistryPolicyMode.MANAGE, JsonSchemaRegistryDriftMode.FAIL, false,
                        '{"type":"object","properties":{"customer":{"$ref":"https://example.com/customer.schema.json"}}}')
        )

        then:
        outcome.present
        outcome.get().status() == JsonSchemaRegistryOutcomeStatus.PROJECTION_INCOMPATIBILITY
        outcome.get().failure()
        outcome.get().message().contains("https://example.com/customer.schema.json")
    }

    void "local ref is projection compatible for duality views"() {
        given:
        OracleDualityJsonViewMaterializer materializer = new OracleDualityJsonViewMaterializer(new DefaultJsonSchemaNormalizer())

        expect:
        materializer.projectionCompatibility(
                request([viewName: "ORDER_DV"], JsonSchemaRegistryPolicyMode.MANAGE, JsonSchemaRegistryDriftMode.FAIL, false,
                        '{"$defs":{"customer":{"type":"object"}},"$ref":"#/$defs/customer"}')
        ).empty
    }

    void "unreadable duality view schema is reported as drift"() {
        given:
        OracleDualityJsonViewMaterializer materializer = new OracleDualityJsonViewMaterializer(new DefaultJsonSchemaNormalizer())
        Connection connection = Mock()

        when:
        JsonSchemaRegistryOutcome outcome = materializer.reconcile(
                connection,
                request([viewName: "ORDER_DV"], JsonSchemaRegistryPolicyMode.MANAGE, JsonSchemaRegistryDriftMode.FAIL, false)
        )

        then:
        1 * connection.prepareStatement("SELECT view_name, json_schema FROM USER_JSON_DUALITY_VIEWS WHERE view_name IN (?) ORDER BY view_name") >> {
            throw new SQLException("metadata unavailable")
        }
        outcome.status() == JsonSchemaRegistryOutcomeStatus.DRIFT
        outcome.failure()
        outcome.message().contains("UNREADABLE_SCHEMA")
    }

    void "existing duality view without JSON_SCHEMA is reported as drift without DDL"() {
        given:
        OracleDualityJsonViewMaterializer materializer = new OracleDualityJsonViewMaterializer(new DefaultJsonSchemaNormalizer())
        Connection connection = Mock()
        PreparedStatement statement = Mock()
        ResultSet resultSet = Mock()

        when:
        JsonSchemaRegistryOutcome outcome = materializer.reconcile(
                connection,
                request([viewName: "ORDER_DV", viewDdl: "CREATE JSON RELATIONAL DUALITY VIEW ORDER_DV"], JsonSchemaRegistryPolicyMode.MANAGE, JsonSchemaRegistryDriftMode.FAIL, false)
        )

        then:
        1 * connection.prepareStatement("SELECT view_name, json_schema FROM USER_JSON_DUALITY_VIEWS WHERE view_name IN (?) ORDER BY view_name") >> statement
        1 * statement.setString(1, "ORDER_DV")
        1 * statement.executeQuery() >> resultSet
        2 * resultSet.next() >>> [true, false]
        1 * resultSet.getString(1) >> "ORDER_DV"
        1 * resultSet.getString(2) >> null
        1 * resultSet.close()
        1 * statement.close()
        0 * connection.prepareStatement("CREATE JSON RELATIONAL DUALITY VIEW ORDER_DV")
        outcome.status() == JsonSchemaRegistryOutcomeStatus.DRIFT
        outcome.failure()
        outcome.message().contains("MISSING_JSON_SCHEMA")
    }

    void "missing duality view without ddl reports missing target"() {
        given:
        OracleDualityJsonViewMaterializer materializer = new OracleDualityJsonViewMaterializer(new DefaultJsonSchemaNormalizer())
        Connection connection = Mock()
        PreparedStatement statement = Mock()
        ResultSet resultSet = Mock()

        when:
        JsonSchemaRegistryOutcome outcome = materializer.reconcile(
                connection,
                request([viewName: "ORDER_DV"], JsonSchemaRegistryPolicyMode.MANAGE, JsonSchemaRegistryDriftMode.REPORT, false)
        )

        then:
        1 * connection.prepareStatement("SELECT view_name, json_schema FROM USER_JSON_DUALITY_VIEWS WHERE view_name IN (?) ORDER BY view_name") >> statement
        1 * statement.setString(1, "ORDER_DV")
        1 * statement.executeQuery() >> resultSet
        1 * resultSet.next() >> false
        1 * resultSet.close()
        1 * statement.close()
        outcome.status() == JsonSchemaRegistryOutcomeStatus.MISSING_TARGET
        !outcome.failure()
    }

    void "duality view discovery uses DBA metadata when ALL metadata is unavailable"() {
        given:
        OracleDualityViewSchemaDiscoveryProvider provider = new OracleDualityViewSchemaDiscoveryProvider()
        Connection connection = Mock()
        PreparedStatement allStatement = Mock()
        PreparedStatement dbaStatement = Mock()
        ResultSet allResultSet = Mock()
        ResultSet dbaResultSet = Mock()

        when:
        DiscoveryResult result = provider.discover(
                new SchemaDiscoveryContext(true, false, null, null, [:],
                        { String ignored -> } as JsonSchemaRecordsLogger, { connection }),
                new SourceSpec("duality-views", provider.providerId(), [owner: "HR", include: "ORDER_DV"])
        )

        then:
        1 * connection.prepareStatement("SELECT 1 FROM ALL_JSON_DUALITY_VIEWS WHERE owner = ? FETCH FIRST 1 ROWS ONLY") >> allStatement
        1 * allStatement.setString(1, "HR")
        1 * allStatement.executeQuery() >> { throw new SQLException("ALL metadata unavailable") }
        1 * connection.prepareStatement("SELECT 1 FROM DBA_JSON_DUALITY_VIEWS WHERE owner = ? FETCH FIRST 1 ROWS ONLY") >> dbaStatement
        1 * dbaStatement.setString(1, "HR")
        1 * dbaStatement.executeQuery() >> dbaResultSet
        1 * connection.prepareStatement("SELECT view_name, json_schema FROM DBA_JSON_DUALITY_VIEWS WHERE owner = ? AND view_name IN (?) ORDER BY view_name") >> dbaStatement
        1 * dbaStatement.setString(1, "HR")
        1 * dbaStatement.setString(2, "ORDER_DV")
        1 * dbaStatement.executeQuery() >> dbaResultSet
        2 * dbaResultSet.next() >>> [true, false]
        1 * dbaResultSet.getString(1) >> "ORDER_DV"
        1 * dbaResultSet.getString(2) >> '{"type":"object"}'
        result.schemas().size() == 1
        result.schemas()[0].name() == "ORDER_DV"
        result.schemas()[0].retrievalMode() == "DUALITY_DB_PROVIDED"
    }

    private static OracleMaterializationRequest request(Map<String, String> options,
                                                        JsonSchemaRegistryPolicyMode policyMode,
                                                        JsonSchemaRegistryDriftMode driftMode,
                                                        boolean dryRun) {
        request(options, policyMode, driftMode, dryRun, '{"type":"object","properties":{}}')
    }

    private static OracleMaterializationRequest request(Map<String, String> options,
                                                        JsonSchemaRegistryPolicyMode policyMode,
                                                        JsonSchemaRegistryDriftMode driftMode,
                                                        boolean dryRun,
                                                        String schemaJson) {
        new OracleMaterializationRequest(
                new JsonSchemaCandidate(new LogicalSchema("com.acme.Order", "com.acme.Order", "ORDER_DV"), schemaJson, "test"),
                "ORDER_DV",
                null,
                options,
                policyMode,
                driftMode,
                dryRun
        )
    }
}
