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

import java.nio.charset.StandardCharsets
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException

final class OracleDomainMaterializerSpec extends Specification {

    void "large schema literal is split into clob chunks"() {
        given:
        String schema = '{"type":"object","description":"' + "x".repeat(6100) + "'}"

        when:
        String literal = OracleDomainMaterializer.schemaLiteral(schema)

        then:
        literal.startsWith("to_clob('")
        literal.contains(" || to_clob('")
        literal.count("to_clob(") == 3
        !literal.contains("description':''")
    }

    void "schema literal chunks are sized after SQL escaping"() {
        given:
        String schema = '{"description":"' + "'".repeat(3100) + '"}'

        when:
        String literal = OracleDomainMaterializer.schemaLiteral(schema)

        then:
        literal.startsWith("to_clob('")
        List<String> chunks = literal.findAll(/to_clob\('((?:''|[^'])*)'\)/) { match, chunk -> chunk }
        chunks.size() > 1
        chunks.every { it.getBytes(StandardCharsets.UTF_8).length <= 3000 }
    }

    void "small schema literal is escaped as regular SQL literal"() {
        expect:
        OracleDomainMaterializer.schemaLiteral('{"const":"can\'t"}') == '\'{"const":"can\'\'t"}\''
    }

    void "dry run missing domain outcome is prefixed"() {
        given:
        OracleDomainMaterializer materializer = new OracleDomainMaterializer(new DefaultJsonSchemaNormalizer())
        Connection connection = Mock()
        PreparedStatement existsStatement = Mock()
        ResultSet existsResult = Mock()

        when:
        JsonSchemaRegistryOutcome outcome = materializer.reconcile(
                connection,
                new OracleMaterializationRequest(
                        new JsonSchemaCandidate(new LogicalSchema("REG_DRY_RUN", null, "REG_DRY_RUN"), '{"type":"object"}', "test"),
                        "REG_DRY_RUN",
                        null,
                        [:],
                        JsonSchemaRegistryPolicyMode.MANAGE,
                        JsonSchemaRegistryDriftMode.REPORT,
                        true
                )
        )

        then:
        1 * connection.prepareStatement("SELECT name FROM user_domains WHERE name = ?") >> existsStatement
        1 * existsStatement.setString(1, "REG_DRY_RUN")
        1 * existsStatement.executeQuery() >> existsResult
        1 * existsResult.next() >> false
        1 * existsResult.close()
        1 * existsStatement.close()
        outcome.status() == JsonSchemaRegistryOutcomeStatus.CREATED
        !outcome.failure()
        outcome.message() == "[DRY-RUN] would create Oracle domain REG_DRY_RUN"
    }

    void "unreadable existing domain schema is reported as drift"() {
        given:
        OracleDomainMaterializer materializer = new OracleDomainMaterializer(new DefaultJsonSchemaNormalizer())
        Connection connection = Mock()
        PreparedStatement existsStatement = Mock()
        ResultSet existsResult = Mock()

        when:
        JsonSchemaRegistryOutcome outcome = materializer.reconcile(
                connection,
                new OracleMaterializationRequest(
                        new JsonSchemaCandidate(new LogicalSchema("REG_UNREADABLE", null, "REG_UNREADABLE"), '{"type":"object"}', "test"),
                        "REG_UNREADABLE",
                        null,
                        [:],
                        JsonSchemaRegistryPolicyMode.MANAGE,
                        JsonSchemaRegistryDriftMode.FAIL,
                        false
                )
        )

        then:
        1 * connection.prepareStatement("SELECT name FROM user_domains WHERE name = ?") >> existsStatement
        1 * existsStatement.setString(1, "REG_UNREADABLE")
        1 * existsStatement.executeQuery() >> existsResult
        1 * existsResult.next() >> true
        1 * existsResult.close()
        1 * existsStatement.close()
        1 * connection.prepareStatement("SELECT name FROM USER_DOMAINS") >> { throw new SQLException("metadata unavailable") }
        outcome.status() == JsonSchemaRegistryOutcomeStatus.DRIFT
        outcome.failure()
        outcome.message().contains("metadata unavailable")
    }
}
