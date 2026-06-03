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

final class OracleDualityJsonViewMaterializerSpec extends Specification {

    void "missing duality view is created only from explicit ddl"() {
        given:
        OracleDualityJsonViewMaterializer materializer = new OracleDualityJsonViewMaterializer(new DefaultJsonSchemaNormalizer())
        Connection connection = Mock()
        PreparedStatement discoveryStatement = Mock()
        ResultSet discoveryResult = Mock()
        PreparedStatement createStatement = Mock()
        PreparedStatement verificationStatement = Mock()
        ResultSet verificationResult = Mock()
        String ddl = "CREATE JSON RELATIONAL DUALITY VIEW ORDER_DV AS SELECT JSON {} FROM DUAL"

        when:
        JsonSchemaRegistryOutcome outcome = materializer.reconcile(
                connection,
                request([viewName: "ORDER_DV", viewDdl: ddl], JsonSchemaRegistryPolicyMode.MANAGE, JsonSchemaRegistryDriftMode.REPORT, false)
        )

        then:
        2 * connection.prepareStatement("SELECT view_name, json_schema FROM USER_JSON_DUALITY_VIEWS") >>> [discoveryStatement, verificationStatement]
        1 * discoveryStatement.executeQuery() >> discoveryResult
        1 * discoveryResult.next() >> false
        1 * discoveryResult.close()
        1 * discoveryStatement.close()
        1 * connection.prepareStatement(ddl) >> createStatement
        1 * createStatement.executeUpdate()
        1 * createStatement.close()
        1 * verificationStatement.executeQuery() >> verificationResult
        2 * verificationResult.next() >>> [true, false]
        1 * verificationResult.getString(1) >> "ORDER_DV"
        1 * verificationResult.getString(2) >> '{"properties":{},"type":"object"}'
        1 * verificationResult.close()
        1 * verificationStatement.close()
        outcome.status() == JsonSchemaRegistryOutcomeStatus.CREATED
        !outcome.failure()
    }

    void "created duality view reports drift when exposed schema differs"() {
        given:
        OracleDualityJsonViewMaterializer materializer = new OracleDualityJsonViewMaterializer(new DefaultJsonSchemaNormalizer())
        Connection connection = Mock()
        PreparedStatement discoveryStatement = Mock()
        ResultSet discoveryResult = Mock()
        PreparedStatement createStatement = Mock()
        PreparedStatement verificationStatement = Mock()
        ResultSet verificationResult = Mock()
        String ddl = "CREATE JSON RELATIONAL DUALITY VIEW ORDER_DV AS SELECT JSON {} FROM DUAL"

        when:
        JsonSchemaRegistryOutcome outcome = materializer.reconcile(
                connection,
                request([viewName: "ORDER_DV", viewDdl: ddl], JsonSchemaRegistryPolicyMode.MANAGE, JsonSchemaRegistryDriftMode.FAIL, false)
        )

        then:
        2 * connection.prepareStatement("SELECT view_name, json_schema FROM USER_JSON_DUALITY_VIEWS") >>> [discoveryStatement, verificationStatement]
        1 * discoveryStatement.executeQuery() >> discoveryResult
        1 * discoveryResult.next() >> false
        1 * discoveryResult.close()
        1 * discoveryStatement.close()
        1 * connection.prepareStatement(ddl) >> createStatement
        1 * createStatement.executeUpdate()
        1 * createStatement.close()
        1 * verificationStatement.executeQuery() >> verificationResult
        2 * verificationResult.next() >>> [true, false]
        1 * verificationResult.getString(1) >> "ORDER_DV"
        1 * verificationResult.getString(2) >> '{"type":"string"}'
        1 * verificationResult.close()
        1 * verificationStatement.close()
        outcome.status() == JsonSchemaRegistryOutcomeStatus.DRIFT
        outcome.failure()
    }

    void "missing duality view without ddl reports missing target"() {
        given:
        OracleDualityJsonViewMaterializer materializer = new OracleDualityJsonViewMaterializer(new DefaultJsonSchemaNormalizer())
        Connection connection = Mock()
        PreparedStatement discoveryStatement = Mock()
        ResultSet discoveryResult = Mock()

        when:
        JsonSchemaRegistryOutcome outcome = materializer.reconcile(
                connection,
                request([viewName: "ORDER_DV"], JsonSchemaRegistryPolicyMode.MANAGE, JsonSchemaRegistryDriftMode.REPORT, false)
        )

        then:
        1 * connection.prepareStatement("SELECT view_name, json_schema FROM USER_JSON_DUALITY_VIEWS") >> discoveryStatement
        1 * discoveryStatement.executeQuery() >> discoveryResult
        1 * discoveryResult.next() >> false
        1 * discoveryResult.close()
        1 * discoveryStatement.close()
        0 * connection.prepareStatement({ it.startsWith("CREATE JSON RELATIONAL DUALITY VIEW") })
        outcome.status() == JsonSchemaRegistryOutcomeStatus.MISSING_TARGET
        !outcome.failure()
    }

    void "existing duality view schema is compared with normalized equivalence"() {
        given:
        OracleDualityJsonViewMaterializer materializer = new OracleDualityJsonViewMaterializer(new DefaultJsonSchemaNormalizer())
        Connection connection = Mock()
        PreparedStatement discoveryStatement = Mock()
        ResultSet discoveryResult = Mock()

        when:
        JsonSchemaRegistryOutcome outcome = materializer.reconcile(
                connection,
                request([viewName: "ORDER_DV"], JsonSchemaRegistryPolicyMode.OBSERVE_ONLY, JsonSchemaRegistryDriftMode.REPORT, false)
        )

        then:
        1 * connection.prepareStatement("SELECT view_name, json_schema FROM USER_JSON_DUALITY_VIEWS") >> discoveryStatement
        1 * discoveryStatement.executeQuery() >> discoveryResult
        2 * discoveryResult.next() >>> [true, false]
        1 * discoveryResult.getString(1) >> "ORDER_DV"
        1 * discoveryResult.getString(2) >> '{"properties":{},"type":"object"}'
        1 * discoveryResult.close()
        1 * discoveryStatement.close()
        outcome.status() == JsonSchemaRegistryOutcomeStatus.EQUIVALENT
        !outcome.failure()
    }

    void "duality view drift honors fail drift mode"() {
        given:
        OracleDualityJsonViewMaterializer materializer = new OracleDualityJsonViewMaterializer(new DefaultJsonSchemaNormalizer())
        Connection connection = Mock()
        PreparedStatement discoveryStatement = Mock()
        ResultSet discoveryResult = Mock()

        when:
        JsonSchemaRegistryOutcome outcome = materializer.reconcile(
                connection,
                request([viewName: "ORDER_DV"], JsonSchemaRegistryPolicyMode.OBSERVE_ONLY, JsonSchemaRegistryDriftMode.FAIL, false)
        )

        then:
        1 * connection.prepareStatement("SELECT view_name, json_schema FROM USER_JSON_DUALITY_VIEWS") >> discoveryStatement
        1 * discoveryStatement.executeQuery() >> discoveryResult
        2 * discoveryResult.next() >>> [true, false]
        1 * discoveryResult.getString(1) >> "ORDER_DV"
        1 * discoveryResult.getString(2) >> '{"type":"string"}'
        1 * discoveryResult.close()
        1 * discoveryStatement.close()
        outcome.status() == JsonSchemaRegistryOutcomeStatus.DRIFT
        outcome.failure()
    }

    private static OracleMaterializationRequest request(Map<String, String> options,
                                                        JsonSchemaRegistryPolicyMode policyMode,
                                                        JsonSchemaRegistryDriftMode driftMode,
                                                        boolean dryRun) {
        new OracleMaterializationRequest(
                new JsonSchemaCandidate(new LogicalSchema("com.acme.Order", "com.acme.Order", "ORDER_DV"), '{"type":"object","properties":{}}', "test"),
                "ORDER_DV",
                null,
                options,
                policyMode,
                driftMode,
                dryRun
        )
    }
}
