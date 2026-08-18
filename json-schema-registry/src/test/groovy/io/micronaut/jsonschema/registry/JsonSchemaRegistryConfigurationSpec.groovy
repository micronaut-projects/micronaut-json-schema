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

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.micronaut.health.HealthStatus
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.jsonschema.generator.discovery.DiscoveredSchema
import io.micronaut.jsonschema.generator.discovery.DiscoveryResult
import io.micronaut.jsonschema.generator.discovery.JsonSchemaRecordsLogger
import io.micronaut.jsonschema.generator.discovery.SchemaDiscoveryContext
import io.micronaut.jsonschema.generator.discovery.SchemaDiscoveryProvider
import io.micronaut.jsonschema.generator.discovery.SourceSpec
import io.micronaut.jsonschema.generator.oracle.OracleDomainSchemaDiscoveryProvider
import io.micronaut.jsonschema.generator.oracle.OracleDiscoveryScope
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.jsonschema.registry.oracle.OracleDomainMaterializer
import io.micronaut.jsonschema.registry.oracle.OracleDualityJsonViewMaterializer
import io.micronaut.jsonschema.registry.oracle.OracleMaterializationRequest
import io.micronaut.jsonschema.registry.oracle.OracleJsonSchemaCastMode
import io.micronaut.jsonschema.registry.oracle.OracleSchemaDiscoveryProviderResolver
import io.micronaut.jsonschema.registry.oracle.OracleSchemaMaterializer
import io.micronaut.jsonschema.registry.oracle.OracleSchemaMaterializerResolver
import io.micronaut.management.health.indicator.HealthResult
import jakarta.inject.Singleton
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.Specification

import javax.sql.DataSource
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.sql.Connection
import java.sql.SQLException
import java.sql.SQLFeatureNotSupportedException
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger

final class JsonSchemaRegistryConfigurationSpec extends Specification {

    void "binds registry configuration"() {
        when:
        JsonSchemaRegistryConfiguration configuration = withContext([
                "micronaut.jsonschema.registry.enabled"                                      : "false",
                "micronaut.jsonschema.registry.authority"                                    : "oracle",
                "micronaut.jsonschema.registry.fail-fast-strategy"                           : "startup_abort",
                "micronaut.jsonschema.registry.oracle.policy.mode"                           : "observe_only",
                "micronaut.jsonschema.registry.oracle.drift.mode"                            : "fail",
                "micronaut.jsonschema.registry.oracle.datasource"                            : "orders",
                "micronaut.jsonschema.registry.oracle.cast-mode"                             : "cast",
                "micronaut.jsonschema.registry.oracle.domains[0]"                            : "APP_COM_ACME_ORDER",
                "micronaut.jsonschema.registry.oracle.authority.providers[0].name"           : "duality-views",
                "micronaut.jsonschema.registry.oracle.authority.providers[0].providerClassName": "example.DualityProvider",
                "micronaut.jsonschema.registry.oracle.authority.providers[0].owner"          : "HR",
                "micronaut.jsonschema.registry.oracle.authority.providers[0].options.include": "ORDER_DV",
                "micronaut.jsonschema.registry.oracle.materializers[0].name"                 : "duality-materializer",
                "micronaut.jsonschema.registry.oracle.materializers[0].providerClassName"    : "example.DualityMaterializer",
                "micronaut.jsonschema.registry.oracle.materializers[0].owner"                : "HR",
                "micronaut.jsonschema.registry.oracle.materializers[0].options.viewName"     : "ORDER_DV",
                "micronaut.jsonschema.registry.mappings[0].subject"                         : "com.acme.Order",
                "micronaut.jsonschema.registry.mappings[0].domain"                          : "APP_COM_ACME_ORDER"
        ]) { ApplicationContext context ->
            context.getBean(JsonSchemaRegistryConfiguration)
        }

        then:
        !configuration.enabled
        configuration.authority == JsonSchemaRegistryAuthority.ORACLE
        configuration.failFastStrategy == JsonSchemaRegistryFailFastStrategy.STARTUP_ABORT
        configuration.oracle.policy.mode == JsonSchemaRegistryPolicyMode.OBSERVE_ONLY
        configuration.oracle.drift.mode == JsonSchemaRegistryDriftMode.FAIL
        configuration.oracle.datasource == "orders"
        configuration.oracle.castMode == OracleJsonSchemaCastMode.CAST
        configuration.oracle.domains[0] == "APP_COM_ACME_ORDER"
        configuration.oracle.authority.providers[0].providerClassName == "example.DualityProvider"
        configuration.oracle.authority.providers[0].owner == "HR"
        configuration.oracle.authority.providers[0].options.include == "ORDER_DV"
        configuration.oracle.materializers[0].providerClassName == "example.DualityMaterializer"
        configuration.oracle.materializers[0].owner == "HR"
        configuration.oracle.materializers[0].options.viewName == "ORDER_DV"
        configuration.mappings[0].subject == "com.acme.Order"
    }

    void "resolves default Oracle extension points"() {
        given:
        JsonSchemaRegistryConfiguration configuration = new JsonSchemaRegistryConfiguration()

        expect:
        configuration.resolveOracleAuthorityProviders()[0].providerClassName == OracleDomainSchemaDiscoveryProvider.name
        configuration.resolveOracleMaterializers()[0].providerClassName == OracleDomainMaterializer.name
    }

    void "configured Oracle domains become built-in provider selected set"() {
        given:
        JsonSchemaRegistryConfiguration configuration = new JsonSchemaRegistryConfiguration()
        configuration.oracle.domains = ["APP_COM_ACME_ORDER", "APP_COM_ACME_INVOICE"]

        when:
        JsonSchemaRegistryConfiguration.ProviderConfiguration provider = configuration.resolveOracleAuthorityProviders()[0]

        then:
        provider.providerClassName == OracleDomainSchemaDiscoveryProvider.name
        provider.options.include == "APP_COM_ACME_ORDER,APP_COM_ACME_INVOICE"
    }

    void "configured Oracle domains narrow explicitly configured built-in domain provider"() {
        given:
        JsonSchemaRegistryConfiguration configuration = new JsonSchemaRegistryConfiguration()
        configuration.oracle.domains = ["APP_COM_ACME_ORDER"]
        JsonSchemaRegistryConfiguration.ProviderConfiguration domainProvider = new JsonSchemaRegistryConfiguration.ProviderConfiguration(
                name: "domains",
                providerClassName: OracleDomainSchemaDiscoveryProvider.name,
                options: [prefix: "APP_"]
        )
        JsonSchemaRegistryConfiguration.ProviderConfiguration dualityProvider = new JsonSchemaRegistryConfiguration.ProviderConfiguration(
                name: "duality-views",
                providerClassName: "example.oracle.OrderDualityViewDiscoveryProvider",
                options: [include: "ORDER_DV"]
        )
        configuration.oracle.authority.providers = [domainProvider, dualityProvider]

        when:
        List<JsonSchemaRegistryConfiguration.ProviderConfiguration> providers = configuration.resolveOracleAuthorityProviders()

        then:
        providers[0].providerClassName == OracleDomainSchemaDiscoveryProvider.name
        providers[0].options.include == "APP_COM_ACME_ORDER"
        !providers[0].options.containsKey("prefix")
        providers[1] == dualityProvider
    }

    void "configured Oracle domains narrow defaulted built-in domain provider"() {
        given:
        JsonSchemaRegistryConfiguration configuration = new JsonSchemaRegistryConfiguration()
        configuration.oracle.domains = ["APP_COM_ACME_ORDER"]
        JsonSchemaRegistryConfiguration.ProviderConfiguration domainProvider = new JsonSchemaRegistryConfiguration.ProviderConfiguration(
                name: "domains",
                options: [prefix: "APP_"]
        )
        configuration.oracle.authority.providers = [domainProvider]

        when:
        JsonSchemaRegistryConfiguration.ProviderConfiguration provider = configuration.resolveOracleAuthorityProviders()[0]

        then:
        provider.providerClassName == null
        provider.options.include == "APP_COM_ACME_ORDER"
        !provider.options.containsKey("prefix")
    }

    void "configured Oracle extension points replace built-in defaults"() {
        given:
        JsonSchemaRegistryConfiguration configuration = new JsonSchemaRegistryConfiguration()
        JsonSchemaRegistryConfiguration.ProviderConfiguration authorityProvider = new JsonSchemaRegistryConfiguration.ProviderConfiguration(
                name: "duality-views",
                providerClassName: "example.oracle.OrderDualityViewDiscoveryProvider",
                options: [viewName: "ORDER_DV"]
        )
        JsonSchemaRegistryConfiguration.ProviderConfiguration materializer = new JsonSchemaRegistryConfiguration.ProviderConfiguration(
                name: "duality-views",
                providerClassName: "example.oracle.OrderDualityViewMaterializer",
                options: [viewName: "ORDER_DV"]
        )
        configuration.oracle.authority.providers = [authorityProvider]
        configuration.oracle.materializers = [materializer]

        expect:
        configuration.resolveOracleAuthorityProviders() == [authorityProvider]
        configuration.resolveOracleMaterializers() == [materializer]
    }

    void "resolves Oracle discovery provider from Micronaut bean"() {
        expect:
        withContext(["spec.name": "bean-discovery-provider"]) { ApplicationContext context ->
            context.getBean(OracleSchemaDiscoveryProviderResolver)
                    .resolve(BeanDiscoveryProvider.name, getClass().classLoader)
                    .class == BeanDiscoveryProvider
        }
    }

    void "resolves reusable generator Oracle discovery provider from Micronaut bean"() {
        when:
        DiscoveryResult result = withContext(["spec.name": "generator-bean-discovery-provider"]) { ApplicationContext context ->
            SchemaDiscoveryProvider provider = context.getBean(OracleSchemaDiscoveryProviderResolver)
                    .resolve(GeneratorBeanDiscoveryProvider.name, getClass().classLoader)
            provider.discover(
                    new SchemaDiscoveryContext(false, false, null, null, [:],
                            { String ignored -> } as JsonSchemaRecordsLogger, null),
                    new SourceSpec("generator", provider.providerId(), [:])
            )
        }

        then:
        result.schemas().size() == 1
        result.schemas()[0].scope() == OracleDiscoveryScope.CUSTOM.name()
        result.schemas()[0].name() == "ORDER_DV"
    }

    void "Oracle authority provider options resolve non domain logical identity"() {
        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "spec.name"                                                        : "option-discovery-provider",
                "micronaut.jsonschema.registry.enabled"                                      : "true",
                "micronaut.jsonschema.registry.authority"                                    : "oracle",
                "micronaut.jsonschema.registry.oracle.enabled"                               : "true",
                "micronaut.jsonschema.registry.oracle.authority.providers[0].name"           : "duality-views",
                "micronaut.jsonschema.registry.oracle.authority.providers[0].providerClassName": OptionDiscoveryProvider.name,
                "micronaut.jsonschema.registry.oracle.authority.providers[0].options.logicalFqcn": "com.acme.OrderView",
                "micronaut.jsonschema.registry.oracle.authority.providers[0].options.subject": "orders-value",
                "micronaut.jsonschema.registry.oracle.authority.providers[0].options.viewName": "ORDER_DV",
                "micronaut.jsonschema.registry.csr.enabled"                                   : "false"
        ]) { ApplicationContext context ->
            context.registerSingleton(DataSource, new NullDataSource(), Qualifiers.byName("default"), false)
            context.getBean(JsonSchemaRegistryReconciler).reconcile()
        }

        then:
        outcomes.size() == 1
        outcomes[0].target() == "oracle.authority"
        outcomes[0].logicalSchema().logicalFqcn() == "com.acme.OrderView"
        outcomes[0].logicalSchema().subject() == "orders-value"
        outcomes[0].logicalSchema().oracleArtifactName() == "ORDER_DV"
    }

    void "Oracle domain authority mapping derives logical identity from mapped subject"() {
        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "spec.name"                                                        : "domain-discovery-provider",
                "micronaut.jsonschema.registry.enabled"                                      : "true",
                "micronaut.jsonschema.registry.authority"                                    : "oracle",
                "micronaut.jsonschema.registry.naming.subject.prefix"                        : "csr.",
                "micronaut.jsonschema.registry.oracle.enabled"                               : "true",
                "micronaut.jsonschema.registry.oracle.authority.providers[0].name"           : "domains",
                "micronaut.jsonschema.registry.oracle.authority.providers[0].providerClassName": DomainDiscoveryProvider.name,
                "micronaut.jsonschema.registry.oracle.authority.providers[0].options.domain" : "APP_COM_ACME_ORDER",
                "micronaut.jsonschema.registry.mappings[0].subject"                         : "csr.com.acme.Order",
                "micronaut.jsonschema.registry.mappings[0].domain"                          : "APP_COM_ACME_ORDER",
                "micronaut.jsonschema.registry.csr.enabled"                                   : "false"
        ]) { ApplicationContext context ->
            context.registerSingleton(DataSource, new NullDataSource(), Qualifiers.byName("default"), false)
            context.getBean(JsonSchemaRegistryReconciler).reconcile()
        }

        then:
        outcomes.size() == 1
        outcomes[0].target() == "oracle.authority"
        outcomes[0].logicalSchema().logicalFqcn() == "com.acme.Order"
        outcomes[0].logicalSchema().subject() == "csr.com.acme.Order"
        outcomes[0].logicalSchema().oracleArtifactName() == "APP_COM_ACME_ORDER"
    }

    void "Oracle domain authority reports missing mapping for lossy domain to CSR pairing"() {
        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "spec.name"                                                        : "domain-discovery-provider",
                "micronaut.jsonschema.registry.enabled"                                      : "true",
                "micronaut.jsonschema.registry.authority"                                    : "oracle",
                "micronaut.jsonschema.registry.naming.subject.prefix"                        : "csr.",
                "micronaut.jsonschema.registry.naming.domain.prefix"                         : "APP_",
                "micronaut.jsonschema.registry.oracle.enabled"                               : "true",
                "micronaut.jsonschema.registry.oracle.authority.providers[0].name"           : "domains",
                "micronaut.jsonschema.registry.oracle.authority.providers[0].providerClassName": DomainDiscoveryProvider.name,
                "micronaut.jsonschema.registry.oracle.authority.providers[0].options.domain" : "APP_COM_ACME_ORDER",
                "micronaut.jsonschema.registry.csr.enabled"                                   : "true",
                "micronaut.jsonschema.registry.csr.url"                                       : "http://localhost:8081"
        ]) { ApplicationContext context ->
            context.registerSingleton(DataSource, new NullDataSource(), Qualifiers.byName("default"), false)
            context.getBean(JsonSchemaRegistryReconciler).reconcile()
        }

        then:
        outcomes.any {
            it.target() == "csr" &&
                    it.status() == JsonSchemaRegistryOutcomeStatus.FAILED &&
                    it.message().contains("missing_mapping")
        }
    }

    void "resolves Oracle materializer from Micronaut bean"() {
        expect:
        withContext(["spec.name": "bean-materializer"]) { ApplicationContext context ->
            context.getBean(OracleSchemaMaterializerResolver)
                    .resolve(BeanMaterializer.name, getClass().classLoader)
                    .class == BeanMaterializer
        }
    }

    void "built-in Oracle materializers expose representation descriptions"() {
        expect:
        new OracleDomainMaterializer(new DefaultJsonSchemaNormalizer())
                .representationDescription()
                .contains("Oracle JSON Domain")
        new OracleDualityJsonViewMaterializer(new DefaultJsonSchemaNormalizer())
                .representationDescription()
                .contains("JSON Relational Duality View")
    }

    void "configured Oracle materializer receives provider specific artifact name"() {
        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "spec.name"                                                   : "bean-materializer",
                "micronaut.jsonschema.registry.oracle.enabled"                         : "true",
                "micronaut.jsonschema.registry.oracle.materializers[0].name"           : "duality-views",
                "micronaut.jsonschema.registry.oracle.materializers[0].providerClassName": BeanMaterializer.name,
                "micronaut.jsonschema.registry.oracle.materializers[0].options.viewName": "ORDER_DV"
        ]) { ApplicationContext context ->
            context.registerSingleton(DataSource, new NullDataSource(), Qualifiers.byName("default"), false)
            context.getBean(DefaultJsonSchemaRegistryReconciler).reconcileOracleTarget([
                    new JsonSchemaCandidate(new LogicalSchema("com.acme.Order", "com.acme.Order", null), '{"type":"object"}', "test")
            ])
        }

        then:
        outcomes.size() == 1
        outcomes[0].message() == "ORDER_DV"
    }

    void "custom Oracle materializer can resolve artifact name from options"() {
        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "spec.name"                                                   : "bean-materializer",
                "micronaut.jsonschema.registry.oracle.enabled"                         : "true",
                "micronaut.jsonschema.registry.oracle.materializers[0].name"           : "duality-views",
                "micronaut.jsonschema.registry.oracle.materializers[0].providerClassName": BeanMaterializer.name
        ]) { ApplicationContext context ->
            context.registerSingleton(DataSource, new NullDataSource(), Qualifiers.byName("default"), false)
            context.getBean(DefaultJsonSchemaRegistryReconciler).reconcileOracleTarget([
                    new JsonSchemaCandidate(new LogicalSchema("com.acme.Order", "com.acme.Order", null), '{"type":"object"}', "test")
            ])
        }

        then:
        outcomes.size() == 1
        outcomes[0].message() == null
    }

    void "Oracle materializer operation metrics include projection materialize and introspection"() {
        given:
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry()

        when:
        withContext([
                "spec.name"                                                   : "bean-materializer",
                "micronaut.jsonschema.registry.oracle.enabled"                         : "true",
                "micronaut.jsonschema.registry.oracle.materializers[0].name"           : "duality-views",
                "micronaut.jsonschema.registry.oracle.materializers[0].providerClassName": BeanMaterializer.name,
                "micronaut.jsonschema.registry.oracle.materializers[0].options.viewName": "ORDER_DV"
        ]) { ApplicationContext context ->
            context.registerSingleton(MeterRegistry, meterRegistry)
            context.registerSingleton(DataSource, new NullDataSource(), Qualifiers.byName("default"), false)
            context.getBean(DefaultJsonSchemaRegistryReconciler).reconcileOracleTarget([
                    new JsonSchemaCandidate(new LogicalSchema("com.acme.Order", "com.acme.Order", null), '{"type":"object"}', "test")
            ])
        }

        then:
        meterRegistry.get("json.schema.registry.operation.duration")
                .tag("target", "oracle")
                .tag("operation", "projection")
                .tag("failure", "false")
                .timer()
                .count() == 1
        meterRegistry.get("json.schema.registry.operation.duration")
                .tag("target", "oracle")
                .tag("operation", "materialize")
                .tag("failure", "false")
                .timer()
                .count() == 1
        meterRegistry.get("json.schema.registry.operation.duration")
                .tag("target", "oracle")
                .tag("operation", "introspection")
                .tag("failure", "false")
                .timer()
                .count() == 1
    }

    void "duality view materializer resolves classpath DDL resource"() {
        given:
        def method = io.micronaut.jsonschema.registry.oracle.OracleDualityJsonViewMaterializer
                .getDeclaredMethod("resolveDdl", Map)
        method.accessible = true

        when:
        Optional<String> ddl = method.invoke(null, [viewDdlResource: "classpath:oracle/test-duality-view.sql"]) as Optional<String>

        then:
        ddl.present
        ddl.get().contains("CREATE JSON RELATIONAL DUALITY VIEW")
    }

    void "built-in domain naming applies Oracle identifier truncation"() {
        given:
        String logicalName = "com.acme." + "OrderCreated".repeat(20)
        String computed = ("APP_" + logicalName.replace('.', '_')).toUpperCase(Locale.ENGLISH)
        String hash = HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(computed.getBytes(StandardCharsets.UTF_8)))
                .substring(0, 8)
                .toUpperCase(Locale.ENGLISH)

        when:
        String domainName = DefaultJsonSchemaRegistryReconciler.domainNameFromLogicalName("APP_", logicalName)

        then:
        domainName.getBytes(StandardCharsets.UTF_8).length == 128
        domainName == computed.substring(0, 119) + "_" + hash
        domainName.endsWith("_" + hash)
    }

    void "observability records metrics when MeterRegistry is available"() {
        when:
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry()
        withContext([:]) { ApplicationContext context ->
            context.registerSingleton(MeterRegistry, meterRegistry)
            JsonSchemaRegistryConfiguration configuration = new JsonSchemaRegistryConfiguration(authority: JsonSchemaRegistryAuthority.CSR)
            configuration.csr.policy.mode = JsonSchemaRegistryPolicyMode.OBSERVE_ONLY
            DefaultJsonSchemaRegistryObservability observability = new DefaultJsonSchemaRegistryObservability(context)
            observability.record(
                    configuration,
                    Duration.ofMillis(25),
                    [JsonSchemaRegistryOutcome.ok(
                            new LogicalSchema("com.acme.Order", "com.acme.Order", null),
                            "csr.authority",
                            JsonSchemaRegistryOutcomeStatus.EQUIVALENT,
                            "ok"
                    )]
            )
        }

        then:
        meterRegistry.get("json.schema.registry.outcomes")
                .tag("target", "csr")
                .tag("authority", "csr")
                .tag("mode", "observe_only")
                .tag("result", "equivalent")
                .tag("failure", "false")
                .counter()
                .count() == 1.0d
        meterRegistry.get("json.schema.registry.reconcile.duration")
                .tag("authority", "csr")
                .tag("dry_run", "false")
                .tag("failure", "false")
                .timer()
                .count() == 1
        meterRegistry.get("json.schema.registry.discovered")
                .tag("authority", "csr")
                .tag("source", "csr")
                .counter()
                .count() == 1.0d
    }

    void "readiness indicator reads last reconciliation state"() {
        when:
        HealthStatus initial
        HealthStatus failed
        HealthStatus recovered
        JsonSchemaRegistryFailFastStrategy strategy
        withContext(["micronaut.jsonschema.registry.enabled": "true"]) { ApplicationContext context ->
            JsonSchemaRegistryConfiguration configuration = context.getBean(JsonSchemaRegistryConfiguration)
            JsonSchemaRegistryState state = context.getBean(JsonSchemaRegistryState)
            JsonSchemaRegistryReadinessIndicator indicator = context.getBean(JsonSchemaRegistryReadinessIndicator)

            initial = healthResult(indicator).status
            state.completed(Duration.ofMillis(10), [JsonSchemaRegistryOutcome.failure(
                    new LogicalSchema("com.acme.Order", "com.acme.Order", null),
                    "csr",
                    JsonSchemaRegistryOutcomeStatus.FAILED,
                    "failure"
            )])
            failed = healthResult(indicator).status
            state.completed(Duration.ofMillis(10), [JsonSchemaRegistryOutcome.ok(
                    new LogicalSchema("com.acme.Order", "com.acme.Order", null),
                    "csr",
                    JsonSchemaRegistryOutcomeStatus.EQUIVALENT,
                    "ok"
            )])
            strategy = configuration.failFastStrategy
            recovered = healthResult(indicator).status
        }

        then:
        initial == HealthStatus.DOWN
        failed == HealthStatus.DOWN
        strategy == JsonSchemaRegistryFailFastStrategy.READINESS_GATE
        recovered == HealthStatus.UP
    }

    void "readiness indicator stays up when failure strategy is none"() {
        expect:
        withContext([
                "micronaut.jsonschema.registry.enabled"            : "true",
                "micronaut.jsonschema.registry.fail-fast-strategy" : "none"
        ]) { ApplicationContext context ->
            JsonSchemaRegistryState state = context.getBean(JsonSchemaRegistryState)
            JsonSchemaRegistryReadinessIndicator indicator = context.getBean(JsonSchemaRegistryReadinessIndicator)
            state.completed(Duration.ofMillis(10), [JsonSchemaRegistryOutcome.failure(
                    new LogicalSchema("com.acme.Order", "com.acme.Order", null),
                    "csr",
                    JsonSchemaRegistryOutcomeStatus.FAILED,
                    "failure"
            )])
            healthResult(indicator).status
        } == HealthStatus.UP
    }

    void "readiness indicator stays up when failure strategy is startup abort after startup"() {
        expect:
        withContext([
                "micronaut.jsonschema.registry.enabled"            : "true",
                "micronaut.jsonschema.registry.fail-fast-strategy" : "startup_abort"
        ]) { ApplicationContext context ->
            JsonSchemaRegistryState state = context.getBean(JsonSchemaRegistryState)
            JsonSchemaRegistryReadinessIndicator indicator = context.getBean(JsonSchemaRegistryReadinessIndicator)
            state.completed(Duration.ofMillis(10), [JsonSchemaRegistryOutcome.failure(
                    new LogicalSchema("com.acme.Order", "com.acme.Order", null),
                    "csr",
                    JsonSchemaRegistryOutcomeStatus.FAILED,
                    "failure"
            )])
            healthResult(indicator).status
        } == HealthStatus.UP
    }

    void "startup abort strategy throws from startup listener when startup reconciliation fails"() {
        given:
        JsonSchemaRegistryConfiguration configuration = new JsonSchemaRegistryConfiguration(failFastStrategy: JsonSchemaRegistryFailFastStrategy.STARTUP_ABORT)
        JsonSchemaRegistryStartupListener listener = new JsonSchemaRegistryStartupListener(
                configuration,
                () -> [JsonSchemaRegistryOutcome.failure(
                        new LogicalSchema("com.acme.Order", "com.acme.Order", null),
                        "csr",
                        JsonSchemaRegistryOutcomeStatus.FAILED,
                        "failure"
                )]
        )

        when:
        listener.onApplicationEvent(null)

        then:
        JsonSchemaRegistryException exception = thrown()
        exception.message == "JSON Schema Registry startup reconciliation failed"
    }

    void "resync service updates state and observability"() {
        when:
        List<JsonSchemaRegistryOutcome> outcomes
        JsonSchemaRegistryState.RunStatus status
        int records
        withContext(["spec.name": "service-reconciler"]) { ApplicationContext context ->
            JsonSchemaRegistryService service = context.getBean(JsonSchemaRegistryService)
            JsonSchemaRegistryState state = context.getBean(JsonSchemaRegistryState)
            CountingObservability observability = context.getBean(CountingObservability)
            outcomes = service.resync()
            status = state.snapshot().status()
            records = observability.records.get()
        }

        then:
        outcomes.size() == 1
        outcomes[0].status() == JsonSchemaRegistryOutcomeStatus.EQUIVALENT
        status == JsonSchemaRegistryState.RunStatus.SUCCESS
        records == 1
    }

    void "resync service records exception as failed outcome"() {
        when:
        List<JsonSchemaRegistryOutcome> outcomes
        JsonSchemaRegistryState.RunStatus status
        int records
        String runId
        withContext(["spec.name": "throwing-reconciler"]) { ApplicationContext context ->
            JsonSchemaRegistryService service = context.getBean(JsonSchemaRegistryService)
            JsonSchemaRegistryState state = context.getBean(JsonSchemaRegistryState)
            CountingObservability observability = context.getBean(CountingObservability)
            outcomes = service.resync()
            status = state.snapshot().status()
            records = observability.records.get()
            runId = observability.lastRunId
        }

        then:
        outcomes.size() == 1
        outcomes[0].failure()
        outcomes[0].status() == JsonSchemaRegistryOutcomeStatus.FAILED
        status == JsonSchemaRegistryState.RunStatus.FAILED
        records == 1
        runId != "none"
    }

    void "resync service rejects concurrent runs"() {
        given:
        SlowReconciler.entered = new CountDownLatch(1)
        SlowReconciler.release = new CountDownLatch(1)

        when:
        JsonSchemaRegistryException exception
        JsonSchemaRegistryOutcomeStatus firstStatus
        withContext(["spec.name": "slow-reconciler"]) { ApplicationContext context ->
            JsonSchemaRegistryService service = context.getBean(JsonSchemaRegistryService)
            CompletableFuture<List<JsonSchemaRegistryOutcome>> first = CompletableFuture.supplyAsync(() -> service.resync())
            assert SlowReconciler.entered.await(5, TimeUnit.SECONDS)
            exception = thrownBy(JsonSchemaRegistryException) { service.resync() }
            SlowReconciler.release.countDown()
            firstStatus = first.get(5, TimeUnit.SECONDS)[0].status()
        }

        then:
        exception.message == "JSON Schema Registry reconciliation is already running"
        firstStatus == JsonSchemaRegistryOutcomeStatus.EQUIVALENT

        cleanup:
        SlowReconciler.release?.countDown()
    }

    private static HealthResult healthResult(JsonSchemaRegistryReadinessIndicator indicator) {
        CompletableFuture<HealthResult> result = new CompletableFuture<>()
        indicator.result.subscribe(new Subscriber<HealthResult>() {
            @Override
            void onSubscribe(Subscription subscription) {
                subscription.request(1)
            }

            @Override
            void onNext(HealthResult healthResult) {
                result.complete(healthResult)
            }

            @Override
            void onError(Throwable throwable) {
                result.completeExceptionally(throwable)
            }

            @Override
            void onComplete() {
            }
        })
        result.get(5, TimeUnit.SECONDS)
    }

    private static <T extends Throwable> T thrownBy(Class<T> type, Closure<?> callback) {
        try {
            callback()
        } catch (Throwable e) {
            if (type.isInstance(e)) {
                return type.cast(e)
            }
            throw e
        }
        throw new AssertionError("Expected exception of type ${type.name}")
    }

    private static <T> T withContext(Map<String, Object> properties, Closure<T> callback) {
        ApplicationContext context = ApplicationContext.run(properties)
        try {
            callback(context)
        } finally {
            context.close()
        }
    }

    @Singleton
    @Primary
    @Requires(property = "spec.name", pattern = "service-reconciler|throwing-reconciler|slow-reconciler")
    static final class CountingObservability implements JsonSchemaRegistryObservability {
        final AtomicInteger records = new AtomicInteger()
        volatile String lastRunId

        @Override
        void record(JsonSchemaRegistryConfiguration configuration,
                    Duration duration,
                    List<JsonSchemaRegistryOutcome> outcomes) {
            records.incrementAndGet()
            lastRunId = JsonSchemaRegistryRunContext.runId()
        }
    }

    @Singleton
    @Primary
    @Requires(property = "spec.name", value = "service-reconciler")
    static final class ServiceReconciler implements JsonSchemaRegistryReconciler {
        @Override
        List<JsonSchemaRegistryOutcome> reconcile() {
            [JsonSchemaRegistryOutcome.ok(
                    new LogicalSchema("com.acme.Order", "com.acme.Order", null),
                    "csr",
                    JsonSchemaRegistryOutcomeStatus.EQUIVALENT,
                    "ok"
            )]
        }
    }

    @Singleton
    @Primary
    @Requires(property = "spec.name", value = "throwing-reconciler")
    static final class ThrowingReconciler implements JsonSchemaRegistryReconciler {
        @Override
        List<JsonSchemaRegistryOutcome> reconcile() {
            throw new JsonSchemaRegistryException("boom")
        }
    }

    @Singleton
    @Primary
    @Requires(property = "spec.name", value = "slow-reconciler")
    static final class SlowReconciler implements JsonSchemaRegistryReconciler {
        static CountDownLatch entered = new CountDownLatch(1)
        static CountDownLatch release = new CountDownLatch(1)

        @Override
        List<JsonSchemaRegistryOutcome> reconcile() {
            entered.countDown()
            try {
                release.await(5, TimeUnit.SECONDS)
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt()
                throw new JsonSchemaRegistryException("Interrupted while waiting", e)
            }
            [JsonSchemaRegistryOutcome.ok(
                    new LogicalSchema("com.acme.Order", "com.acme.Order", null),
                    "csr",
                    JsonSchemaRegistryOutcomeStatus.EQUIVALENT,
                    "ok"
            )]
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "bean-discovery-provider")
    static final class BeanDiscoveryProvider implements SchemaDiscoveryProvider {
        @Override
        String providerId() { "bean-discovery-provider" }

        @Override
        DiscoveryResult discover(SchemaDiscoveryContext context, SourceSpec source) {
            new DiscoveryResult([], [], [])
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "option-discovery-provider")
    static final class OptionDiscoveryProvider implements SchemaDiscoveryProvider {
        @Override
        String providerId() { "option-discovery-provider" }

        @Override
        DiscoveryResult discover(SchemaDiscoveryContext context, SourceSpec source) {
            new DiscoveryResult([
                    new DiscoveredSchema(OracleDiscoveryScope.DUALITY_VIEW.name(), "ORDER_DV", '{"type":"object"}', "test")
            ], [], [])
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "domain-discovery-provider")
    static final class DomainDiscoveryProvider implements SchemaDiscoveryProvider {
        @Override
        String providerId() { "domain-discovery-provider" }

        @Override
        DiscoveryResult discover(SchemaDiscoveryContext context, SourceSpec source) {
            new DiscoveryResult([
                    new DiscoveredSchema(
                            OracleDiscoveryScope.DOMAIN.name(),
                            source.option("domain") ?: "APP_COM_ACME_ORDER",
                            '{"type":"object"}',
                            "test"
                    )
            ], [], [])
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "generator-bean-discovery-provider")
    static final class GeneratorBeanDiscoveryProvider implements SchemaDiscoveryProvider {
        @Override
        String providerId() { "generator-bean-discovery-provider" }

        @Override
        DiscoveryResult discover(SchemaDiscoveryContext context, SourceSpec source) {
            new DiscoveryResult([
                    new DiscoveredSchema(
                            OracleDiscoveryScope.CUSTOM.name(),
                            "ORDER_DV",
                            '{"type":"object"}',
                            "test"
                    )
            ], [], [])
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "bean-materializer")
    static final class BeanMaterializer implements OracleSchemaMaterializer {
        @Override
        JsonSchemaRegistryOutcome reconcile(Connection connection, OracleMaterializationRequest request) throws Exception {
            String artifactName = request.recordOperation("introspection", () -> request.artifactName())
            JsonSchemaRegistryOutcome.ok(
                    request.candidate().logicalSchema(),
                    "oracle.duality-view",
                    JsonSchemaRegistryOutcomeStatus.EQUIVALENT,
                    artifactName
            )
        }
    }

    static final class NullDataSource implements DataSource {
        @Override
        Connection getConnection() {
            null
        }

        @Override
        Connection getConnection(String username, String password) {
            null
        }

        @Override
        PrintWriter getLogWriter() {
            null
        }

        @Override
        void setLogWriter(PrintWriter out) {
        }

        @Override
        void setLoginTimeout(int seconds) {
        }

        @Override
        int getLoginTimeout() {
            0
        }

        @Override
        Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException()
        }

        @Override
        def <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("Not a wrapper")
        }

        @Override
        boolean isWrapperFor(Class<?> iface) {
            false
        }
    }
}
