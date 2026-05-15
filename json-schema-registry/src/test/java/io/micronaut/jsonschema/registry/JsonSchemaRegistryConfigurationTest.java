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
import io.micronaut.context.annotation.Requires;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jsonschema.generator.oracle.OracleDiscoveryResult;
import io.micronaut.jsonschema.generator.oracle.OracleJsonSchemaLogger;
import io.micronaut.jsonschema.generator.oracle.OracleSchemaDiscoveryProvider;
import io.micronaut.jsonschema.generator.oracle.OracleSourceSpec;
import io.micronaut.jsonschema.registry.oracle.OracleMaterializationRequest;
import io.micronaut.jsonschema.registry.oracle.OracleDomainDiscoveryProvider;
import io.micronaut.jsonschema.registry.oracle.OracleDomainMaterializer;
import io.micronaut.jsonschema.registry.oracle.OracleSchemaDiscoveryProviderResolver;
import io.micronaut.jsonschema.registry.oracle.OracleSchemaMaterializer;
import io.micronaut.jsonschema.registry.oracle.OracleSchemaMaterializerResolver;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class JsonSchemaRegistryConfigurationTest {

    @Test
    void bindsRegistryConfiguration() {
        try (ApplicationContext context = ApplicationContext.run(Map.ofEntries(
            Map.entry("json-schema.registry.enabled", "false"),
            Map.entry("json-schema.registry.authority", "oracle"),
            Map.entry("json-schema.registry.oracle.policy.mode", "observe_only"),
            Map.entry("json-schema.registry.oracle.drift.mode", "fail"),
            Map.entry("json-schema.registry.oracle.datasource", "orders"),
            Map.entry("json-schema.registry.oracle.domains[0]", "APP_COM_ACME_ORDER"),
            Map.entry("json-schema.registry.oracle.authority.providers[0].name", "duality-views"),
            Map.entry("json-schema.registry.oracle.authority.providers[0].providerClassName", "example.DualityProvider"),
            Map.entry("json-schema.registry.oracle.authority.providers[0].owner", "HR"),
            Map.entry("json-schema.registry.oracle.authority.providers[0].options.include", "ORDER_DV"),
            Map.entry("json-schema.registry.oracle.materializers[0].name", "duality-materializer"),
            Map.entry("json-schema.registry.oracle.materializers[0].providerClassName", "example.DualityMaterializer"),
            Map.entry("json-schema.registry.oracle.materializers[0].owner", "HR"),
            Map.entry("json-schema.registry.oracle.materializers[0].options.viewName", "ORDER_DV"),
            Map.entry("json-schema.registry.mappings[0].subject", "com.acme.Order"),
            Map.entry("json-schema.registry.mappings[0].domain", "APP_COM_ACME_ORDER")
        ))) {
            JsonSchemaRegistryConfiguration configuration = context.getBean(JsonSchemaRegistryConfiguration.class);

            assertFalse(configuration.isEnabled());
            assertEquals(JsonSchemaRegistryAuthority.ORACLE, configuration.getAuthority());
            assertEquals(JsonSchemaRegistryPolicyMode.OBSERVE_ONLY, configuration.getOracle().getPolicy().getMode());
            assertEquals(JsonSchemaRegistryDriftMode.FAIL, configuration.getOracle().getDrift().getMode());
            assertEquals("orders", configuration.getOracle().getDatasource());
            assertEquals("APP_COM_ACME_ORDER", configuration.getOracle().getDomains().get(0));
            assertEquals("example.DualityProvider",
                configuration.getOracle().getAuthority().getProviders().get(0).getProviderClassName());
            assertEquals("HR",
                configuration.getOracle().getAuthority().getProviders().get(0).getOwner());
            assertEquals("ORDER_DV",
                configuration.getOracle().getAuthority().getProviders().get(0).getOptions().get("include"));
            assertEquals("example.DualityMaterializer",
                configuration.getOracle().getMaterializers().get(0).getProviderClassName());
            assertEquals("HR",
                configuration.getOracle().getMaterializers().get(0).getOwner());
            assertEquals("ORDER_DV",
                configuration.getOracle().getMaterializers().get(0).getOptions().get("viewName"));
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

    @Test
    void configuredOracleDomainsBecomeBuiltInProviderSelectedSet() {
        JsonSchemaRegistryConfiguration configuration = new JsonSchemaRegistryConfiguration();
        configuration.getOracle().setDomains(List.of("APP_COM_ACME_ORDER", "APP_COM_ACME_INVOICE"));

        JsonSchemaRegistryConfiguration.ProviderConfiguration provider =
            configuration.resolveOracleAuthorityProviders().get(0);

        assertEquals(OracleDomainDiscoveryProvider.class.getName(), provider.getProviderClassName());
        assertEquals("APP_COM_ACME_ORDER,APP_COM_ACME_INVOICE", provider.getOptions().get("include"));
    }

    @Test
    void configuredOracleExtensionPointsReplaceBuiltInDefaults() {
        JsonSchemaRegistryConfiguration configuration = new JsonSchemaRegistryConfiguration();
        JsonSchemaRegistryConfiguration.ProviderConfiguration authorityProvider =
            new JsonSchemaRegistryConfiguration.ProviderConfiguration();
        authorityProvider.setName("duality-views");
        authorityProvider.setProviderClassName("example.oracle.OrderDualityViewDiscoveryProvider");
        authorityProvider.setOptions(Map.of("viewName", "ORDER_DV"));
        configuration.getOracle().getAuthority().setProviders(List.of(authorityProvider));
        JsonSchemaRegistryConfiguration.ProviderConfiguration materializer =
            new JsonSchemaRegistryConfiguration.ProviderConfiguration();
        materializer.setName("duality-views");
        materializer.setProviderClassName("example.oracle.OrderDualityViewMaterializer");
        materializer.setOptions(Map.of("viewName", "ORDER_DV"));
        configuration.getOracle().setMaterializers(List.of(materializer));

        assertEquals(List.of(authorityProvider), configuration.resolveOracleAuthorityProviders());
        assertEquals(List.of(materializer), configuration.resolveOracleMaterializers());
    }

    @Test
    void resolvesOracleDiscoveryProviderFromMicronautBean() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "bean-discovery-provider"))) {
            OracleSchemaDiscoveryProvider provider = context.getBean(OracleSchemaDiscoveryProviderResolver.class)
                .resolve(BeanDiscoveryProvider.class.getName(), getClass().getClassLoader());

            assertEquals(BeanDiscoveryProvider.class, provider.getClass());
        }
    }

    @Test
    void resolvesOracleMaterializerFromMicronautBean() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "bean-materializer"))) {
            OracleSchemaMaterializer materializer = context.getBean(OracleSchemaMaterializerResolver.class)
                .resolve(BeanMaterializer.class.getName(), getClass().getClassLoader());

            assertEquals(BeanMaterializer.class, materializer.getClass());
        }
    }

    @Test
    void configuredOracleMaterializerReceivesProviderSpecificArtifactName() {
        try (ApplicationContext context = ApplicationContext.run(Map.ofEntries(
            Map.entry("spec.name", "bean-materializer"),
            Map.entry("json-schema.registry.oracle.enabled", "true"),
            Map.entry("json-schema.registry.oracle.materializers[0].name", "duality-views"),
            Map.entry("json-schema.registry.oracle.materializers[0].providerClassName", BeanMaterializer.class.getName()),
            Map.entry("json-schema.registry.oracle.materializers[0].options.viewName", "ORDER_DV")
        ))) {
            context.registerSingleton(DataSource.class, new NullDataSource(), Qualifiers.byName("default"), false);
            DefaultJsonSchemaRegistryReconciler reconciler = context.getBean(DefaultJsonSchemaRegistryReconciler.class);

            List<JsonSchemaRegistryOutcome> outcomes = reconciler.reconcileOracleTarget(List.of(
                new JsonSchemaCandidate(new LogicalSchema("com.acme.Order", "com.acme.Order", null), "{\"type\":\"object\"}", "test")
            ));

            assertEquals(1, outcomes.size());
            assertEquals("ORDER_DV", outcomes.get(0).message());
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "bean-discovery-provider")
    static final class BeanDiscoveryProvider implements OracleSchemaDiscoveryProvider {
        @Override
        public OracleDiscoveryResult discover(Connection connection,
                                              OracleSourceSpec source,
                                              boolean skipOnError,
                                              OracleJsonSchemaLogger logger) {
            return new OracleDiscoveryResult(List.of(), List.of(), List.of());
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "bean-materializer")
    static final class BeanMaterializer implements OracleSchemaMaterializer {
        @Override
        public JsonSchemaRegistryOutcome reconcile(Connection connection, OracleMaterializationRequest request) {
            return JsonSchemaRegistryOutcome.ok(
                request.candidate().logicalSchema(),
                "oracle.duality-view",
                JsonSchemaRegistryOutcomeStatus.EQUIVALENT,
                request.artifactName()
            );
        }
    }

    static final class NullDataSource implements DataSource {
        @Override
        public Connection getConnection() {
            return null;
        }

        @Override
        public Connection getConnection(String username, String password) {
            return null;
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("Not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
