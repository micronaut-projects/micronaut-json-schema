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
import spock.lang.Specification

import java.nio.charset.StandardCharsets

final class DefaultJsonSchemaRegistryReconcilerSrSpec extends Specification {

    private HttpServer server
    private final List<String> registrations = []

    void cleanup() {
        server?.stop(0)
    }

    void "SR authority validates latest schema when Oracle target is disabled"() {
        given:
        startServer([
                "/subjects/com.acme.Order/versions/latest": response(200, '{"schema":"{\\"type\\":\\"object\\"}"}')
        ])

        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "json-schema.registry.enabled"        : "true",
                "json-schema.registry.authority"      : "sr",
                "json-schema.registry.sr.enabled"     : "true",
                "json-schema.registry.sr.url"         : serverUrl(),
                "json-schema.registry.sr.subjects[0]" : "com.acme.Order",
                "json-schema.registry.oracle.enabled" : "false"
        ]) { ApplicationContext context ->
            context.getBean(JsonSchemaRegistryReconciler).reconcile()
        }

        then:
        outcomes.size() == 1
        outcomes[0].status() == JsonSchemaRegistryOutcomeStatus.EQUIVALENT
        outcomes[0].target() == "sr.authority"
    }

    void "SR authority discovers subjects by prefix"() {
        given:
        startServer([
                "/subjects"                                : response(200, '["com.acme.Order","other.Ignore"]'),
                "/subjects/com.acme.Order/versions/latest" : response(200, '{"schema":"{\\"type\\":\\"object\\"}"}')
        ])

        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "json-schema.registry.enabled"               : "true",
                "json-schema.registry.authority"             : "sr",
                "json-schema.registry.sr.enabled"            : "true",
                "json-schema.registry.sr.url"                : serverUrl(),
                "json-schema.registry.naming.subject.prefix" : "com.acme.",
                "json-schema.registry.oracle.enabled"        : "false"
        ]) { ApplicationContext context ->
            context.getBean(JsonSchemaRegistryReconciler).reconcile()
        }

        then:
        outcomes.size() == 1
        outcomes[0].logicalSchema().logicalFqcn() == "Order"
        outcomes[0].logicalSchema().subject() == "com.acme.Order"
        outcomes[0].status() == JsonSchemaRegistryOutcomeStatus.EQUIVALENT
    }

    void "application authority registers missing SR subject"() {
        given:
        startServer([
                "/subjects/io.micronaut.jsonschema.registry.ApplicationAuthorityExample/versions/latest": response(404, "{}")
        ])

        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "json-schema.registry.enabled"        : "true",
                "json-schema.registry.authority"      : "application",
                "json-schema.registry.sr.enabled"     : "true",
                "json-schema.registry.sr.url"         : serverUrl(),
                "json-schema.registry.oracle.enabled" : "false"
        ]) { ApplicationContext context ->
            context.getBean(JsonSchemaRegistryReconciler).reconcile()
        }

        then:
        outcomes.any { it.target() == "application.authority" && it.status() == JsonSchemaRegistryOutcomeStatus.EQUIVALENT }
        outcomes.any { it.status() == JsonSchemaRegistryOutcomeStatus.CREATED }
        !registrations.isEmpty()
        registrations[0].contains('"schemaType":"JSON"')
    }

    void "application authority reports missing SR subject in observe only"() {
        given:
        startServer([
                "/subjects/io.micronaut.jsonschema.registry.ApplicationAuthorityExample/versions/latest": response(404, "{}")
        ])

        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "json-schema.registry.enabled"        : "true",
                "json-schema.registry.authority"      : "application",
                "json-schema.registry.sr.enabled"     : "true",
                "json-schema.registry.sr.url"         : serverUrl(),
                "json-schema.registry.sr.policy.mode" : "observe_only",
                "json-schema.registry.oracle.enabled" : "false"
        ]) { ApplicationContext context ->
            context.getBean(JsonSchemaRegistryReconciler).reconcile()
        }

        then:
        outcomes.any { it.target() == "sr" && it.status() == JsonSchemaRegistryOutcomeStatus.MISSING_TARGET }
        registrations.isEmpty()
    }

    void "application authority registers new SR version when subject drifts"() {
        given:
        startServer([
                "/subjects/io.micronaut.jsonschema.registry.ApplicationAuthorityExample/versions/latest":
                        response(200, '{"schema":"{\\"type\\":\\"string\\"}"}')
        ])

        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "json-schema.registry.enabled"        : "true",
                "json-schema.registry.authority"      : "application",
                "json-schema.registry.sr.enabled"     : "true",
                "json-schema.registry.sr.url"         : serverUrl(),
                "json-schema.registry.oracle.enabled" : "false"
        ]) { ApplicationContext context ->
            context.getBean(JsonSchemaRegistryReconciler).reconcile()
        }

        then:
        outcomes.any { it.target() == "sr" && it.status() == JsonSchemaRegistryOutcomeStatus.CREATED }
        registrations.size() == 1
        registrations[0].contains('"schemaType":"JSON"')
    }

    void "application authority reports SR drift in observe only"() {
        given:
        startServer([
                "/subjects/io.micronaut.jsonschema.registry.ApplicationAuthorityExample/versions/latest":
                        response(200, '{"schema":"{\\"type\\":\\"string\\"}"}')
        ])

        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "json-schema.registry.enabled"        : "true",
                "json-schema.registry.authority"      : "application",
                "json-schema.registry.sr.enabled"     : "true",
                "json-schema.registry.sr.url"         : serverUrl(),
                "json-schema.registry.sr.policy.mode" : "observe_only",
                "json-schema.registry.oracle.enabled" : "false"
        ]) { ApplicationContext context ->
            context.getBean(JsonSchemaRegistryReconciler).reconcile()
        }

        then:
        outcomes.any { it.target() == "sr" && it.status() == JsonSchemaRegistryOutcomeStatus.DRIFT }
        registrations.isEmpty()
    }

    void "SR target reports compatibility rejection as failed"() {
        given:
        startServer([
                "/subjects/io.micronaut.jsonschema.registry.ApplicationAuthorityExample/versions/latest":
                        response(200, '{"schema":"{\\"type\\":\\"string\\"}"}'),
                "/compatibility/subjects/io.micronaut.jsonschema.registry.ApplicationAuthorityExample/versions/latest":
                        response(200, '{"is_compatible":false}')
        ])

        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "json-schema.registry.enabled"        : "true",
                "json-schema.registry.authority"      : "application",
                "json-schema.registry.sr.enabled"     : "true",
                "json-schema.registry.sr.url"         : serverUrl(),
                "json-schema.registry.oracle.enabled" : "false"
        ]) { ApplicationContext context ->
            context.getBean(JsonSchemaRegistryReconciler).reconcile()
        }

        then:
        outcomes.any { it.target() == "sr" && it.status() == JsonSchemaRegistryOutcomeStatus.FAILED && it.failure() }
        !outcomes.any { it.target() == "sr" && it.status() == JsonSchemaRegistryOutcomeStatus.PROJECTION_INCOMPATIBILITY }
        registrations.isEmpty()
    }

    void "SR target honors global non writable mode fallback"() {
        given:
        startServer([
                "/subjects/io.micronaut.jsonschema.registry.ApplicationAuthorityExample/versions/latest": response(404, "{}"),
                "/mode"                                                                   : response(200, '{"mode":"READONLY"}')
        ])

        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "json-schema.registry.enabled"        : "true",
                "json-schema.registry.authority"      : "application",
                "json-schema.registry.sr.enabled"     : "true",
                "json-schema.registry.sr.url"         : serverUrl(),
                "json-schema.registry.oracle.enabled" : "false"
        ]) { ApplicationContext context ->
            context.getBean(JsonSchemaRegistryReconciler).reconcile()
        }

        then:
        outcomes.any { it.target() == "sr" && it.status() == JsonSchemaRegistryOutcomeStatus.FAILED && it.message().contains("READONLY") }
        registrations.isEmpty()
    }

    void "SR authority requires explicit mapping for non prefixed subjects when Oracle target is enabled"() {
        given:
        startServer([
                "/subjects/legacy.Order/versions/latest": response(200, '{"schema":"{\\"type\\":\\"object\\"}"}')
        ])

        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "json-schema.registry.enabled"               : "true",
                "json-schema.registry.authority"             : "sr",
                "json-schema.registry.sr.enabled"            : "true",
                "json-schema.registry.sr.url"                : serverUrl(),
                "json-schema.registry.sr.subjects[0]"        : "legacy.Order",
                "json-schema.registry.naming.subject.prefix" : "com.acme.",
                "json-schema.registry.oracle.enabled"        : "true"
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

    void "application authority validates generated schema when targets are disabled"() {
        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "json-schema.registry.enabled"        : "true",
                "json-schema.registry.authority"      : "application",
                "json-schema.registry.sr.enabled"     : "false",
                "json-schema.registry.oracle.enabled" : "false"
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
}
