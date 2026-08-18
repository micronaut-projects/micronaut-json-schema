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
import io.micronaut.jsonschema.registry.JsonSchemaRegistryOutcomeStatus
import io.micronaut.jsonschema.registry.JsonSchemaRegistryPolicyMode
import io.micronaut.jsonschema.registry.LogicalSchema
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

final class OracleDomainMaterializerSpec extends Specification {

    void "splits large multibyte schema literals by UTF-8 byte size"() {
        given:
        OracleDomainMaterializer materializer = new OracleDomainMaterializer(new DefaultJsonSchemaNormalizer())
        Connection connection = Mock()
        PreparedStatement existsStatement = Mock()
        PreparedStatement ddlStatement = Mock()
        ResultSet resultSet = Mock()
        String schema = '{"type":"string","description":"' + ('😀' * 2000) + '"}'
        String ddl

        when:
        def outcome = materializer.reconcile(
                connection,
                new OracleMaterializationRequest(
                        new JsonSchemaCandidate(new LogicalSchema("com.acme.Order", "com.acme.Order", "ORDER_DOMAIN"), schema, "test"),
                        "ORDER_DOMAIN",
                        null,
                        [:],
                        JsonSchemaRegistryPolicyMode.MANAGE,
                        JsonSchemaRegistryDriftMode.REPORT,
                        false
                )
        )

        then:
        1 * connection.prepareStatement("SELECT name FROM user_domains WHERE name = ?") >> existsStatement
        1 * existsStatement.setString(1, "ORDER_DOMAIN")
        1 * existsStatement.executeQuery() >> resultSet
        1 * resultSet.next() >> false
        1 * connection.prepareStatement({ String sql ->
            sql.startsWith("CREATE DOMAIN ORDER_DOMAIN AS JSON VALIDATE USING ")
        }) >> { String sql ->
            ddl = sql
            ddlStatement
        }
        1 * ddlStatement.executeUpdate()
        outcome.status() == JsonSchemaRegistryOutcomeStatus.CREATED
        def chunks = (ddl =~ /to_clob\('([^']*)'\)/).collect { it[1] }
        chunks.size() > 1
        chunks.every { it.getBytes(StandardCharsets.UTF_8).length <= 3000 }
    }

    void "creates a domain with Oracle CAST validation when configured"() {
        given:
        OracleDomainMaterializer materializer = new OracleDomainMaterializer(new DefaultJsonSchemaNormalizer())
        Connection connection = Mock()
        PreparedStatement existsStatement = Mock()
        PreparedStatement ddlStatement = Mock()
        ResultSet resultSet = Mock()

        when:
        def outcome = materializer.reconcile(
                connection,
                new OracleMaterializationRequest(
                        new JsonSchemaCandidate(new LogicalSchema("com.acme.Order", "com.acme.Order", "ORDER_DOMAIN"),
                                '{"type":"object","properties":{"publishedDate":{"extendedType":"timestamp"}}}', "test"),
                        "ORDER_DOMAIN",
                        null,
                        [castMode: "cast"],
                        JsonSchemaRegistryPolicyMode.MANAGE,
                        JsonSchemaRegistryDriftMode.REPORT,
                        false
                )
        )

        then:
        1 * connection.prepareStatement("SELECT name FROM user_domains WHERE name = ?") >> existsStatement
        1 * existsStatement.setString(1, "ORDER_DOMAIN")
        1 * existsStatement.executeQuery() >> resultSet
        1 * resultSet.next() >> false
        1 * connection.prepareStatement({ String sql ->
            sql.startsWith("CREATE DOMAIN ORDER_DOMAIN AS JSON VALIDATE CAST USING ")
        }) >> ddlStatement
        1 * ddlStatement.executeUpdate()
        outcome.status() == JsonSchemaRegistryOutcomeStatus.CREATED
    }
}
