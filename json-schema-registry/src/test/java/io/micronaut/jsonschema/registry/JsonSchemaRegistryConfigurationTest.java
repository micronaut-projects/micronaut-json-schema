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
package io.micronaut.jsonschema.registry;

import io.micronaut.context.ApplicationContext;
import io.micronaut.jsonschema.registry.oracle.OracleDomainDiscoveryProvider;
import io.micronaut.jsonschema.registry.oracle.OracleDomainMaterializer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class JsonSchemaRegistryConfigurationTest {

    @Test
    void bindsRegistryConfiguration() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "json-schema.registry.enabled", "false",
            "json-schema.registry.authority", "oracle",
            "json-schema.registry.oracle.policy.mode", "observe_only",
            "json-schema.registry.oracle.drift.mode", "fail",
            "json-schema.registry.oracle.datasource", "orders",
            "json-schema.registry.oracle.domains[0]", "APP_COM_ACME_ORDER",
            "json-schema.registry.mappings[0].subject", "com.acme.Order",
            "json-schema.registry.mappings[0].domain", "APP_COM_ACME_ORDER"
        ))) {
            JsonSchemaRegistryConfiguration configuration = context.getBean(JsonSchemaRegistryConfiguration.class);

            assertFalse(configuration.isEnabled());
            assertEquals(JsonSchemaRegistryAuthority.ORACLE, configuration.getAuthority());
            assertEquals(JsonSchemaRegistryPolicyMode.OBSERVE_ONLY, configuration.getOracle().getPolicy().getMode());
            assertEquals(JsonSchemaRegistryDriftMode.FAIL, configuration.getOracle().getDrift().getMode());
            assertEquals("orders", configuration.getOracle().getDatasource());
            assertEquals("APP_COM_ACME_ORDER", configuration.getOracle().getDomains().get(0));
            assertEquals("com.acme.Order", configuration.getMappings().get(0).getSubject());
        }
    }

    @Test
    void resolvesDefaultOracleExtensionPoints() {
        JsonSchemaRegistryConfiguration configuration = new JsonSchemaRegistryConfiguration();

        assertEquals(OracleDomainDiscoveryProvider.class.getName(),
            configuration.resolveOracleAuthorityProviders().get(0).getProviderClassName());
        assertEquals(OracleDomainMaterializer.class.getName(),
            configuration.resolveOracleMaterializers().get(0).getProviderClassName());
    }
}
