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
package io.micronaut.jsonschema.generator

import io.micronaut.jsonschema.generator.records.JsonSchemaRecordsGeneratorConfig
import spock.lang.Specification

class JsonSchemaRecordsGeneratorConfigSpec extends Specification {

    void "connection properties include credentials wallet and additional jdbc properties"() {
        given:
        def config = new JsonSchemaRecordsGeneratorConfig(
            "jdbc:oracle:thin:@db",
            "app",
            "secret",
            "/opt/oracle/network/admin",
            "/opt/oracle/wallet",
            [
                "oracle.net.ssl_server_dn_match": "true",
                "oracle.net.tns_admin"         : "ignored"
            ],
            "example.generated",
            21,
            temporaryFolder.resolve("cache"),
            temporaryFolder.resolve("generated"),
            [],
            false,
            true
        )

        when:
        def properties = config.connectionProperties()

        then:
        properties.getProperty("user") == "app"
        properties.getProperty("password") == "secret"
        properties.getProperty("oracle.net.tns_admin") == "/opt/oracle/network/admin"
        properties.getProperty("oracle.net.wallet_location") == "/opt/oracle/wallet"
        properties.getProperty("oracle.net.ssl_server_dn_match") == "true"
    }

    private static java.nio.file.Path getTemporaryFolder() {
        java.nio.file.Files.createTempDirectory("json-schema-records-config")
    }
}
