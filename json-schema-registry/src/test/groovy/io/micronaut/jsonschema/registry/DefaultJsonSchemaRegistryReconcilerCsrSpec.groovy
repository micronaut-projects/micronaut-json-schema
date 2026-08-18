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

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.jsonschema.generator.discovery.DiscoveredSchema
import io.micronaut.jsonschema.generator.discovery.DiscoveryResult
import io.micronaut.jsonschema.generator.discovery.JsonSchemaRecordsLogger
import io.micronaut.jsonschema.generator.discovery.SchemaDiscoveryContext
import io.micronaut.jsonschema.generator.discovery.SchemaDiscoveryProvider
import io.micronaut.jsonschema.generator.discovery.SourceSpec
import io.micronaut.jsonschema.generator.oracle.OracleDiscoveryScope
import jakarta.inject.Singleton
import spock.lang.Specification

import javax.sql.DataSource
import java.nio.charset.StandardCharsets
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

final class DefaultJsonSchemaRegistryReconcilerCsrSpec extends Specification {

    private HttpServer server
    private final List<String> registrations = []

    void cleanup() {
        server?.stop(0)
    }

    void "CSR authority validates latest schema when Oracle target is disabled"() {
        given:
        startServer([
                "/subjects/com.acme.Order/versions/latest": response(200, '{"schema":"{\\"type\\":\\"object\\"}"}')
        ])

        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "micronaut.jsonschema.registry.enabled"        : "true",
                "micronaut.jsonschema.registry.authority"      : "csr",
                "micronaut.jsonschema.registry.csr.enabled"     : "true",
                "micronaut.jsonschema.registry.csr.url"         : serverUrl(),
                "micronaut.jsonschema.registry.csr.subjects[0]" : "com.acme.Order",
                "micronaut.jsonschema.registry.oracle.enabled" : "false"
        ]) { ApplicationContext context ->
            context.getBean(JsonSchemaRegistryReconciler).reconcile()
        }

        then:
        outcomes.size() == 1
        outcomes[0].status() == JsonSchemaRegistryOutcomeStatus.EQUIVALENT
        outcomes[0].target() == "csr.authority"
    }

    void "CSR authority discovers subjects by prefix"() {
        given:
        startServer([
                "/subjects"                                : response(200, '["com.acme.Order","other.Ignore"]'),
                "/subjects/com.acme.Order/versions/latest" : response(200, '{"schema":"{\\"type\\":\\"object\\"}"}')
        ])

        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "micronaut.jsonschema.registry.enabled"               : "true",
                "micronaut.jsonschema.registry.authority"             : "csr",
                "micronaut.jsonschema.registry.csr.enabled"            : "true",
                "micronaut.jsonschema.registry.csr.url"                : serverUrl(),
                "micronaut.jsonschema.registry.naming.subject.prefix" : "com.acme.",
                "micronaut.jsonschema.registry.oracle.enabled"        : "false"
        ]) { ApplicationContext context ->
            context.getBean(JsonSchemaRegistryReconciler).reconcile()
        }

        then:
        outcomes.size() == 1
        outcomes[0].logicalSchema().logicalFqcn() == "Order"
        outcomes[0].logicalSchema().subject() == "com.acme.Order"
        outcomes[0].status() == JsonSchemaRegistryOutcomeStatus.EQUIVALENT
    }

    void "application authority registers missing CSR subject"() {
        given:
        startServer([
                "/subjects/io.micronaut.jsonschema.registry.ApplicationAuthorityExample/versions/latest": response(404, "{}")
        ])

        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "micronaut.jsonschema.registry.enabled"        : "true",
                "micronaut.jsonschema.registry.authority"      : "application",
                "micronaut.jsonschema.registry.csr.enabled"     : "true",
                "micronaut.jsonschema.registry.csr.url"         : serverUrl(),
                "micronaut.jsonschema.registry.oracle.enabled" : "false"
        ]) { ApplicationContext context ->
            context.getBean(JsonSchemaRegistryReconciler).reconcile()
        }

        then:
        outcomes.any { it.target() == "application.authority" && it.status() == JsonSchemaRegistryOutcomeStatus.EQUIVALENT }
        outcomes.any { it.status() == JsonSchemaRegistryOutcomeStatus.CREATED }
        !registrations.isEmpty()
        registrations[0].contains('"schemaType":"JSON"')
    }

    void "application authority dry run does not register missing CSR subject"() {
        given:
        startServer([
                "/subjects/io.micronaut.jsonschema.registry.ApplicationAuthorityExample/versions/latest": response(404, "{}")
        ])

        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "micronaut.jsonschema.registry.enabled"        : "true",
                "micronaut.jsonschema.registry.authority"      : "application",
                "micronaut.jsonschema.registry.dry-run"        : "true",
                "micronaut.jsonschema.registry.csr.enabled"     : "true",
                "micronaut.jsonschema.registry.csr.url"         : serverUrl(),
                "micronaut.jsonschema.registry.oracle.enabled" : "false"
        ]) { ApplicationContext context ->
            context.getBean(JsonSchemaRegistryReconciler).reconcile()
        }

        then:
        outcomes.any {
            it.target() == "csr" &&
                    it.status() == JsonSchemaRegistryOutcomeStatus.CREATED &&
                    it.message().contains("[DRY-RUN]")
        }
        registrations.isEmpty()
    }

    void "application authority reports missing CSR subject in observe only"() {
        given:
        startServer([
                "/subjects/io.micronaut.jsonschema.registry.ApplicationAuthorityExample/versions/latest": response(404, "{}")
        ])

        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "micronaut.jsonschema.registry.enabled"        : "true",
                "micronaut.jsonschema.registry.authority"      : "application",
                "micronaut.jsonschema.registry.csr.enabled"     : "true",
                "micronaut.jsonschema.registry.csr.url"         : serverUrl(),
                "micronaut.jsonschema.registry.csr.policy.mode" : "observe_only",
                "micronaut.jsonschema.registry.oracle.enabled" : "false"
        ]) { ApplicationContext context ->
            context.getBean(JsonSchemaRegistryReconciler).reconcile()
        }

        then:
        outcomes.any { it.target() == "csr" && it.status() == JsonSchemaRegistryOutcomeStatus.MISSING_TARGET }
        registrations.isEmpty()
    }

    void "application authority registers new CSR version when subject drifts"() {
        given:
        startServer([
                "/subjects/io.micronaut.jsonschema.registry.ApplicationAuthorityExample/versions/latest":
                        response(200, '{"schema":"{\\"type\\":\\"string\\"}"}')
        ])

        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "micronaut.jsonschema.registry.enabled"        : "true",
                "micronaut.jsonschema.registry.authority"      : "application",
                "micronaut.jsonschema.registry.csr.enabled"     : "true",
                "micronaut.jsonschema.registry.csr.url"         : serverUrl(),
                "micronaut.jsonschema.registry.oracle.enabled" : "false"
        ]) { ApplicationContext context ->
            context.getBean(JsonSchemaRegistryReconciler).reconcile()
        }

        then:
        outcomes.any { it.target() == "csr" && it.status() == JsonSchemaRegistryOutcomeStatus.CREATED }
        registrations.size() == 1
        registrations[0].contains('"schemaType":"JSON"')
    }

    void "application authority reports CSR drift in observe only"() {
        given:
        startServer([
                "/subjects/io.micronaut.jsonschema.registry.ApplicationAuthorityExample/versions/latest":
                        response(200, '{"schema":"{\\"type\\":\\"string\\"}"}')
        ])

        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "micronaut.jsonschema.registry.enabled"        : "true",
                "micronaut.jsonschema.registry.authority"      : "application",
                "micronaut.jsonschema.registry.csr.enabled"     : "true",
                "micronaut.jsonschema.registry.csr.url"         : serverUrl(),
                "micronaut.jsonschema.registry.csr.policy.mode" : "observe_only",
                "micronaut.jsonschema.registry.oracle.enabled" : "false"
        ]) { ApplicationContext context ->
            context.getBean(JsonSchemaRegistryReconciler).reconcile()
        }

        then:
        outcomes.any { it.target() == "csr" && it.status() == JsonSchemaRegistryOutcomeStatus.DRIFT }
        registrations.isEmpty()
    }

    void "CSR target reports compatibility rejection as failed"() {
        given:
        startServer([
                "/subjects/io.micronaut.jsonschema.registry.ApplicationAuthorityExample/versions/latest":
                        response(200, '{"schema":"{\\"type\\":\\"string\\"}"}'),
                "/compatibility/subjects/io.micronaut.jsonschema.registry.ApplicationAuthorityExample/versions/latest":
                        response(200, '{"is_compatible":false}')
        ])

        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "micronaut.jsonschema.registry.enabled"        : "true",
                "micronaut.jsonschema.registry.authority"      : "application",
                "micronaut.jsonschema.registry.csr.enabled"     : "true",
                "micronaut.jsonschema.registry.csr.url"         : serverUrl(),
                "micronaut.jsonschema.registry.oracle.enabled" : "false"
        ]) { ApplicationContext context ->
            context.getBean(JsonSchemaRegistryReconciler).reconcile()
        }

        then:
        outcomes.any { it.target() == "csr" && it.status() == JsonSchemaRegistryOutcomeStatus.FAILED && it.failure() }
        !outcomes.any { it.target() == "csr" && it.status() == JsonSchemaRegistryOutcomeStatus.PROJECTION_INCOMPATIBILITY }
        registrations.isEmpty()
    }

    void "CSR target honors global non writable mode fallback"() {
        given:
        startServer([
                "/subjects/io.micronaut.jsonschema.registry.ApplicationAuthorityExample/versions/latest": response(404, "{}"),
                "/mode"                                                                   : response(200, '{"mode":"READONLY"}')
        ])

        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "micronaut.jsonschema.registry.enabled"        : "true",
                "micronaut.jsonschema.registry.authority"      : "application",
                "micronaut.jsonschema.registry.csr.enabled"     : "true",
                "micronaut.jsonschema.registry.csr.url"         : serverUrl(),
                "micronaut.jsonschema.registry.oracle.enabled" : "false"
        ]) { ApplicationContext context ->
            context.getBean(JsonSchemaRegistryReconciler).reconcile()
        }

        then:
        outcomes.any { it.target() == "csr" && it.status() == JsonSchemaRegistryOutcomeStatus.FAILED && it.message().contains("READONLY") }
        registrations.isEmpty()
    }

    void "CSR authority requires explicit mapping for non prefixed subjects when Oracle target is enabled"() {
        given:
        startServer([
                "/subjects/legacy.Order/versions/latest": response(200, '{"schema":"{\\"type\\":\\"object\\"}"}')
        ])

        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "micronaut.jsonschema.registry.enabled"               : "true",
                "micronaut.jsonschema.registry.authority"             : "csr",
                "micronaut.jsonschema.registry.csr.enabled"            : "true",
                "micronaut.jsonschema.registry.csr.url"                : serverUrl(),
                "micronaut.jsonschema.registry.csr.subjects[0]"        : "legacy.Order",
                "micronaut.jsonschema.registry.naming.subject.prefix" : "com.acme.",
                "micronaut.jsonschema.registry.oracle.enabled"        : "true"
        ]) { ApplicationContext context ->
            context.getBean(JsonSchemaRegistryReconciler).reconcile()
        }

        then:
        outcomes.any {
            it.target() == "oracle" &&
                    it.status() == JsonSchemaRegistryOutcomeStatus.FAILED &&
                    it.message().contains("missing_mapping") &&
                    it.message().contains("Unable to derive logicalFqcn")
        }
    }

    void "Oracle domain authority mapping registers missing CSR subject"() {
        given:
        startServer([
                "/subjects/csr.com.acme.Order/versions/latest": response(404, "{}")
        ])

        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "spec.name"                                                        : "csr-domain-discovery-provider",
                "micronaut.jsonschema.registry.enabled"                                      : "true",
                "micronaut.jsonschema.registry.authority"                                    : "oracle",
                "micronaut.jsonschema.registry.naming.subject.prefix"                        : "csr.",
                "micronaut.jsonschema.registry.oracle.enabled"                               : "true",
                "micronaut.jsonschema.registry.oracle.authority.providers[0].name"           : "domains",
                "micronaut.jsonschema.registry.oracle.authority.providers[0].providerClassName": CsrDomainDiscoveryProvider.name,
                "micronaut.jsonschema.registry.mappings[0].subject"                         : "csr.com.acme.Order",
                "micronaut.jsonschema.registry.mappings[0].domain"                          : "APP_COM_ACME_ORDER",
                "micronaut.jsonschema.registry.csr.enabled"                                   : "true",
                "micronaut.jsonschema.registry.csr.url"                                       : serverUrl()
        ]) { ApplicationContext context ->
            DataSource dataSource = Stub()
            dataSource.getConnection() >> null
            context.registerSingleton(DataSource, dataSource, Qualifiers.byName("default"), false)
            context.getBean(JsonSchemaRegistryReconciler).reconcile()
        }

        then:
        outcomes.any { it.target() == "oracle.authority" && it.status() == JsonSchemaRegistryOutcomeStatus.EQUIVALENT }
        outcomes.any { it.target() == "csr" && it.status() == JsonSchemaRegistryOutcomeStatus.CREATED }
        registrations.size() == 1
        registrations[0].contains('"schemaType":"JSON"')
    }

    void "CSR authority reconciles built-in Oracle domain target"() {
        given:
        startServer([
                "/subjects/com.acme.Order/versions/latest": response(200, '{"schema":"{\\"type\\":\\"object\\"}"}')
        ])

        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "micronaut.jsonschema.registry.enabled"               : "true",
                "micronaut.jsonschema.registry.authority"             : "csr",
                "micronaut.jsonschema.registry.csr.enabled"            : "true",
                "micronaut.jsonschema.registry.csr.url"                : serverUrl(),
                "micronaut.jsonschema.registry.csr.subjects[0]"        : "com.acme.Order",
                "micronaut.jsonschema.registry.oracle.enabled"        : "true",
                "micronaut.jsonschema.registry.oracle.policy.mode"    : "observe_only",
                "micronaut.jsonschema.registry.naming.domain.prefix"  : "APP_"
        ]) { ApplicationContext context ->
            DataSource dataSource = Mock()
            Connection connection = Mock()
            PreparedStatement statement = Mock()
            ResultSet resultSet = Mock()
            dataSource.getConnection() >> connection
            connection.prepareStatement("SELECT name FROM user_domains WHERE name = ?") >> statement
            statement.executeQuery() >> resultSet
            resultSet.next() >> false
            context.registerSingleton(DataSource, dataSource, Qualifiers.byName("default"), false)
            context.getBean(JsonSchemaRegistryReconciler).reconcile()
        }

        then:
        outcomes.any { it.target() == "csr.authority" && it.status() == JsonSchemaRegistryOutcomeStatus.EQUIVALENT }
        outcomes.any {
            it.target() == "oracle.domain" &&
                    it.status() == JsonSchemaRegistryOutcomeStatus.MISSING_TARGET &&
                    it.logicalSchema().oracleArtifactName() == null &&
                    it.message().contains("APP_COM_ACME_ORDER")
        }
    }

    void "built-in Oracle domain authority provider registers missing CSR subject"() {
        given:
        startServer([
                "/subjects/csr.com.acme.Order/versions/latest": response(404, "{}")
        ])

        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "micronaut.jsonschema.registry.enabled"                       : "true",
                "micronaut.jsonschema.registry.authority"                     : "oracle",
                "micronaut.jsonschema.registry.naming.subject.prefix"         : "csr.",
                "micronaut.jsonschema.registry.oracle.enabled"                : "true",
                "micronaut.jsonschema.registry.oracle.domains[0]"             : "APP_COM_ACME_ORDER",
                "micronaut.jsonschema.registry.mappings[0].subject"          : "csr.com.acme.Order",
                "micronaut.jsonschema.registry.mappings[0].domain"           : "APP_COM_ACME_ORDER",
                "micronaut.jsonschema.registry.csr.enabled"                   : "true",
                "micronaut.jsonschema.registry.csr.url"                       : serverUrl()
        ]) { ApplicationContext context ->
            DataSource dataSource = Mock()
            Connection connection = Mock()
            PreparedStatement domainListStatement = Mock()
            ResultSet domainListResultSet = Mock()
            PreparedStatement ddlStatement = Mock()
            ResultSet ddlResultSet = Mock()
            dataSource.getConnection() >> connection
            connection.prepareStatement("SELECT name FROM USER_DOMAINS WHERE name IN (?) ORDER BY name") >> domainListStatement
            domainListStatement.setString(1, "APP_COM_ACME_ORDER")
            domainListStatement.executeQuery() >> domainListResultSet
            domainListResultSet.next() >>> [true, false]
            domainListResultSet.getString(1) >> "APP_COM_ACME_ORDER"
            connection.prepareStatement("SELECT dbms_metadata.get_ddl('SQL_DOMAIN', ?) FROM dual") >> ddlStatement
            ddlStatement.executeQuery() >> ddlResultSet
            ddlResultSet.next() >> true
            ddlResultSet.getString(1) >> 'CREATE DOMAIN APP_COM_ACME_ORDER AS JSON VALIDATE USING \'{"type":"object"}\''
            context.registerSingleton(DataSource, dataSource, Qualifiers.byName("default"), false)
            context.getBean(JsonSchemaRegistryReconciler).reconcile()
        }

        then:
        outcomes.any {
            it.target() == "oracle.authority" &&
                    it.status() == JsonSchemaRegistryOutcomeStatus.EQUIVALENT &&
                    it.logicalSchema().subject() == "csr.com.acme.Order"
        }
        outcomes.any { it.target() == "csr" && it.status() == JsonSchemaRegistryOutcomeStatus.CREATED }
        registrations.size() == 1
        registrations[0].contains('"schemaType":"JSON"')
    }

    void "application authority validates generated schema when targets are disabled"() {
        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "micronaut.jsonschema.registry.enabled"        : "true",
                "micronaut.jsonschema.registry.authority"      : "application",
                "micronaut.jsonschema.registry.csr.enabled"     : "false",
                "micronaut.jsonschema.registry.oracle.enabled" : "false"
        ]) { ApplicationContext context ->
            context.getBean(JsonSchemaRegistryReconciler).reconcile()
        }

        then:
        outcomes.size() == 1
        outcomes[0].target() == "application.authority"
        outcomes[0].status() == JsonSchemaRegistryOutcomeStatus.EQUIVALENT
    }

    private void startServer(Map<String, FixedResponse> responses) {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
        server.createContext("/") { HttpExchange exchange ->
            if (exchange.requestMethod == "POST") {
                if (exchange.requestURI.path.startsWith("/compatibility/")) {
                    FixedResponse fixedResponse = responses[exchange.requestURI.path]
                    if (fixedResponse == null) {
                        send(exchange, 200, '{"is_compatible":true}')
                    } else {
                        send(exchange, fixedResponse.status, fixedResponse.body)
                    }
                    return
                }
                registrations << new String(exchange.requestBody.readAllBytes(), StandardCharsets.UTF_8)
                send(exchange, 200, '{"id":1}')
                return
            }
            FixedResponse fixedResponse = responses[exchange.requestURI.path]
            if (fixedResponse == null) {
                send(exchange, 404, "{}")
            } else {
                send(exchange, fixedResponse.status, fixedResponse.body)
            }
        }
        server.start()
    }

    private String serverUrl() {
        "http://localhost:${server.address.port}"
    }

    private static FixedResponse response(int status, String body) {
        new FixedResponse(status, body)
    }

    private static void send(HttpExchange exchange, int status, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8)
        exchange.sendResponseHeaders(status, bytes.length)
        exchange.responseBody.write(bytes)
        exchange.close()
    }

    private static <T> T withContext(Map<String, Object> properties, Closure<T> callback) {
        ApplicationContext context = ApplicationContext.run(properties)
        try {
            callback(context)
        } finally {
            context.close()
        }
    }

    private record FixedResponse(int status, String body) {
    }

    @Singleton
    @Requires(property = "spec.name", value = "csr-domain-discovery-provider")
    static final class CsrDomainDiscoveryProvider implements SchemaDiscoveryProvider {
        @Override
        String providerId() { "csr-domain-discovery-provider" }

        @Override
        DiscoveryResult discover(SchemaDiscoveryContext context, SourceSpec source) {
            new DiscoveryResult([
                    new DiscoveredSchema(OracleDiscoveryScope.DOMAIN.name(), "APP_COM_ACME_ORDER", '{"type":"object"}', "test")
            ], [], [])
        }
    }
}
