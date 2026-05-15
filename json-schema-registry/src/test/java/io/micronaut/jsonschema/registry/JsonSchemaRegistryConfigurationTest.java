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
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.health.HealthStatus;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import io.micronaut.management.health.indicator.HealthResult;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JsonSchemaRegistryConfigurationTest {

    @Test
    void bindsRegistryConfiguration() {
        try (ApplicationContext context = ApplicationContext.run(Map.ofEntries(
            Map.entry("json-schema.registry.enabled", "false"),
            Map.entry("json-schema.registry.authority", "oracle"),
            Map.entry("json-schema.registry.fail-fast-strategy", "startup_abort"),
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
            assertEquals(JsonSchemaRegistryFailFastStrategy.STARTUP_ABORT, configuration.getFailFastStrategy());
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

    @Test
    void builtInDomainNamingAppliesOracleIdentifierTruncation() throws Exception {
        String logicalName = "com.acme." + "OrderCreated".repeat(20);
        String computed = ("APP_" + logicalName.replace('.', '_')).toUpperCase();
        String hash = HexFormat.of()
            .formatHex(MessageDigest.getInstance("SHA-256").digest(computed.getBytes(StandardCharsets.UTF_8)))
            .substring(0, 8)
            .toUpperCase();

        String domainName = DefaultJsonSchemaRegistryReconciler.domainNameFromLogicalName("APP_", logicalName);

        assertEquals(128, domainName.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(computed.substring(0, 119) + "_" + hash, domainName);
        assertTrue(domainName.endsWith("_" + hash));
    }

    @Test
    void observabilityRecordsMetricsWhenMeterRegistryIsAvailable() {
        try (ApplicationContext context = ApplicationContext.run()) {
            SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
            context.registerSingleton(MeterRegistry.class, meterRegistry);
            JsonSchemaRegistryConfiguration configuration = new JsonSchemaRegistryConfiguration();
            configuration.setAuthority(JsonSchemaRegistryAuthority.SR);
            configuration.getSr().getPolicy().setMode(JsonSchemaRegistryPolicyMode.OBSERVE_ONLY);
            DefaultJsonSchemaRegistryObservability observability = new DefaultJsonSchemaRegistryObservability(context);

            observability.record(
                configuration,
                Duration.ofMillis(25),
                List.of(JsonSchemaRegistryOutcome.ok(
                    new LogicalSchema("com.acme.Order", "com.acme.Order", null),
                    "sr",
                    JsonSchemaRegistryOutcomeStatus.EQUIVALENT,
                    "ok"
                ))
            );

            assertEquals(1.0, meterRegistry.get("json.schema.registry.outcomes")
                .tag("target", "sr")
                .tag("authority", "sr")
                .tag("mode", "observe_only")
                .tag("result", "equivalent")
                .tag("failure", "false")
                .counter()
                .count());
            assertEquals(1, meterRegistry.get("json.schema.registry.reconcile.duration")
                .tag("authority", "sr")
                .tag("dry_run", "false")
                .tag("failure", "false")
                .timer()
                .count());
        }
    }

    @Test
    void readinessIndicatorReadsLastReconciliationState() throws Exception {
        try (ApplicationContext context = ApplicationContext.run(Map.of("json-schema.registry.enabled", "true"))) {
            JsonSchemaRegistryConfiguration configuration = context.getBean(JsonSchemaRegistryConfiguration.class);
            JsonSchemaRegistryState state = context.getBean(JsonSchemaRegistryState.class);
            JsonSchemaRegistryReadinessIndicator indicator = context.getBean(JsonSchemaRegistryReadinessIndicator.class);

            assertEquals(HealthStatus.DOWN, healthResult(indicator).getStatus());

            state.completed(Duration.ofMillis(10), List.of(JsonSchemaRegistryOutcome.failure(
                new LogicalSchema("com.acme.Order", "com.acme.Order", null),
                "sr",
                JsonSchemaRegistryOutcomeStatus.FAILED,
                "failure"
            )));

            assertEquals(HealthStatus.DOWN, healthResult(indicator).getStatus());

            state.completed(Duration.ofMillis(10), List.of(JsonSchemaRegistryOutcome.ok(
                new LogicalSchema("com.acme.Order", "com.acme.Order", null),
                "sr",
                JsonSchemaRegistryOutcomeStatus.EQUIVALENT,
                "ok"
            )));

            assertEquals(JsonSchemaRegistryFailFastStrategy.READINESS_GATE, configuration.getFailFastStrategy());
            assertEquals(HealthStatus.UP, healthResult(indicator).getStatus());
        }
    }

    @Test
    void readinessIndicatorStaysUpWhenFailureStrategyIsNone() throws Exception {
        try (ApplicationContext context = ApplicationContext.run(Map.ofEntries(
            Map.entry("json-schema.registry.enabled", "true"),
            Map.entry("json-schema.registry.fail-fast-strategy", "none")
        ))) {
            JsonSchemaRegistryState state = context.getBean(JsonSchemaRegistryState.class);
            JsonSchemaRegistryReadinessIndicator indicator = context.getBean(JsonSchemaRegistryReadinessIndicator.class);

            state.completed(Duration.ofMillis(10), List.of(JsonSchemaRegistryOutcome.failure(
                new LogicalSchema("com.acme.Order", "com.acme.Order", null),
                "sr",
                JsonSchemaRegistryOutcomeStatus.FAILED,
                "failure"
            )));

            assertEquals(HealthStatus.UP, healthResult(indicator).getStatus());
        }
    }

    @Test
    void readinessIndicatorStaysUpWhenFailureStrategyIsStartupAbortAfterStartup() throws Exception {
        try (ApplicationContext context = ApplicationContext.run(Map.ofEntries(
            Map.entry("json-schema.registry.enabled", "true"),
            Map.entry("json-schema.registry.fail-fast-strategy", "startup_abort")
        ))) {
            JsonSchemaRegistryState state = context.getBean(JsonSchemaRegistryState.class);
            JsonSchemaRegistryReadinessIndicator indicator = context.getBean(JsonSchemaRegistryReadinessIndicator.class);

            state.completed(Duration.ofMillis(10), List.of(JsonSchemaRegistryOutcome.failure(
                new LogicalSchema("com.acme.Order", "com.acme.Order", null),
                "sr",
                JsonSchemaRegistryOutcomeStatus.FAILED,
                "failure"
            )));

            assertEquals(HealthStatus.UP, healthResult(indicator).getStatus());
        }
    }

    @Test
    void startupAbortStrategyThrowsFromStartupListenerWhenStartupReconciliationFails() {
        JsonSchemaRegistryConfiguration configuration = new JsonSchemaRegistryConfiguration();
        configuration.setFailFastStrategy(JsonSchemaRegistryFailFastStrategy.STARTUP_ABORT);
        JsonSchemaRegistryStartupListener listener = new JsonSchemaRegistryStartupListener(
            configuration,
            () -> List.of(JsonSchemaRegistryOutcome.failure(
                new LogicalSchema("com.acme.Order", "com.acme.Order", null),
                "sr",
                JsonSchemaRegistryOutcomeStatus.FAILED,
                "failure"
            ))
        );

        JsonSchemaRegistryException exception = assertThrows(JsonSchemaRegistryException.class,
            () -> listener.onApplicationEvent(null));

        assertEquals("JSON Schema Registry startup reconciliation failed", exception.getMessage());
    }

    @Test
    void resyncServiceUpdatesStateAndObservability() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "service-reconciler"))) {
            JsonSchemaRegistryService service = context.getBean(JsonSchemaRegistryService.class);
            JsonSchemaRegistryState state = context.getBean(JsonSchemaRegistryState.class);
            CountingObservability observability = context.getBean(CountingObservability.class);

            List<JsonSchemaRegistryOutcome> outcomes = service.resync();

            assertEquals(1, outcomes.size());
            assertEquals(JsonSchemaRegistryOutcomeStatus.EQUIVALENT, outcomes.get(0).status());
            assertEquals(JsonSchemaRegistryState.RunStatus.SUCCESS, state.snapshot().status());
            assertEquals(1, observability.records.get());
        }
    }

    @Test
    void resyncServiceRecordsExceptionAsFailedOutcome() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "throwing-reconciler"))) {
            JsonSchemaRegistryService service = context.getBean(JsonSchemaRegistryService.class);
            JsonSchemaRegistryState state = context.getBean(JsonSchemaRegistryState.class);
            CountingObservability observability = context.getBean(CountingObservability.class);

            List<JsonSchemaRegistryOutcome> outcomes = service.resync();

            assertEquals(1, outcomes.size());
            assertTrue(outcomes.get(0).failure());
            assertEquals(JsonSchemaRegistryOutcomeStatus.FAILED, outcomes.get(0).status());
            assertEquals(JsonSchemaRegistryState.RunStatus.FAILED, state.snapshot().status());
            assertEquals(1, observability.records.get());
        }
    }

    @Test
    void resyncServiceRejectsConcurrentRuns() throws Exception {
        SlowReconciler.entered = new CountDownLatch(1);
        SlowReconciler.release = new CountDownLatch(1);
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "slow-reconciler"))) {
            JsonSchemaRegistryService service = context.getBean(JsonSchemaRegistryService.class);

            CompletableFuture<List<JsonSchemaRegistryOutcome>> first = CompletableFuture.supplyAsync(service::resync);
            assertTrue(SlowReconciler.entered.await(5, TimeUnit.SECONDS));

            JsonSchemaRegistryException exception = assertThrows(JsonSchemaRegistryException.class, service::resync);
            assertEquals("JSON Schema Registry reconciliation is already running", exception.getMessage());

            SlowReconciler.release.countDown();
            assertEquals(JsonSchemaRegistryOutcomeStatus.EQUIVALENT, first.get(5, TimeUnit.SECONDS).get(0).status());
        } finally {
            SlowReconciler.release.countDown();
        }
    }

    private static HealthResult healthResult(JsonSchemaRegistryReadinessIndicator indicator) throws Exception {
        CompletableFuture<HealthResult> result = new CompletableFuture<>();
        indicator.getResult().subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(1);
            }

            @Override
            public void onNext(HealthResult healthResult) {
                result.complete(healthResult);
            }

            @Override
            public void onError(Throwable throwable) {
                result.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                // No-op.
            }
        });
        return result.get(5, TimeUnit.SECONDS);
    }

    @Singleton
    @Primary
    @Requires(property = "spec.name", pattern = "service-reconciler|throwing-reconciler|slow-reconciler")
    static final class CountingObservability implements JsonSchemaRegistryObservability {
        final AtomicInteger records = new AtomicInteger();

        @Override
        public void record(JsonSchemaRegistryConfiguration configuration,
                           Duration duration,
                           List<JsonSchemaRegistryOutcome> outcomes) {
            records.incrementAndGet();
        }
    }

    @Singleton
    @Primary
    @Requires(property = "spec.name", value = "service-reconciler")
    static final class ServiceReconciler implements JsonSchemaRegistryReconciler {
        @Override
        public List<JsonSchemaRegistryOutcome> reconcile() {
            return List.of(JsonSchemaRegistryOutcome.ok(
                new LogicalSchema("com.acme.Order", "com.acme.Order", null),
                "sr",
                JsonSchemaRegistryOutcomeStatus.EQUIVALENT,
                "ok"
            ));
        }
    }

    @Singleton
    @Primary
    @Requires(property = "spec.name", value = "throwing-reconciler")
    static final class ThrowingReconciler implements JsonSchemaRegistryReconciler {
        @Override
        public List<JsonSchemaRegistryOutcome> reconcile() {
            throw new JsonSchemaRegistryException("boom");
        }
    }

    @Singleton
    @Primary
    @Requires(property = "spec.name", value = "slow-reconciler")
    static final class SlowReconciler implements JsonSchemaRegistryReconciler {
        static CountDownLatch entered = new CountDownLatch(1);
        static CountDownLatch release = new CountDownLatch(1);

        @Override
        public List<JsonSchemaRegistryOutcome> reconcile() {
            entered.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new JsonSchemaRegistryException("Interrupted while waiting", e);
            }
            return List.of(JsonSchemaRegistryOutcome.ok(
                new LogicalSchema("com.acme.Order", "com.acme.Order", null),
                "sr",
                JsonSchemaRegistryOutcomeStatus.EQUIVALENT,
                "ok"
            ));
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
