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
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.jsonschema.generator.oracle.OracleDiscoveryResult
import io.micronaut.jsonschema.generator.oracle.OracleDiscoveryScope
import io.micronaut.jsonschema.generator.oracle.OracleDiscoverySkipped
import io.micronaut.jsonschema.generator.oracle.OracleDiscoveryStep
import io.micronaut.jsonschema.generator.oracle.OracleJsonSchemaLogger
import io.micronaut.jsonschema.generator.oracle.OracleSchemaDiscoveryProvider
import io.micronaut.jsonschema.generator.oracle.OracleSourceSpec
import io.micronaut.jsonschema.registry.oracle.OracleDomainDiscoveryProvider
import io.micronaut.jsonschema.registry.oracle.OracleDomainMaterializer
import io.micronaut.jsonschema.registry.oracle.OracleMaterializationRequest
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
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger

final class JsonSchemaRegistryConfigurationSpec extends Specification {

    void "binds registry configuration"() {
        when:
        JsonSchemaRegistryConfiguration configuration = withContext([
                "json-schema.registry.enabled"                                      : "false",
                "json-schema.registry.authority"                                    : "oracle",
                "json-schema.registry.fail-fast-strategy"                           : "startup_abort",
                "json-schema.registry.oracle.policy.mode"                           : "observe_only",
                "json-schema.registry.oracle.drift.mode"                            : "fail",
                "json-schema.registry.oracle.datasource"                            : "orders",
                "json-schema.registry.oracle.domains[0]"                            : "APP_COM_ACME_ORDER",
                "json-schema.registry.oracle.authority.providers[0].name"           : "duality-views",
                "json-schema.registry.oracle.authority.providers[0].providerClassName": "example.DualityProvider",
                "json-schema.registry.oracle.authority.providers[0].owner"          : "HR",
                "json-schema.registry.oracle.authority.providers[0].options.include": "ORDER_DV",
                "json-schema.registry.oracle.materializers[0].name"                 : "duality-materializer",
                "json-schema.registry.oracle.materializers[0].providerClassName"    : "example.DualityMaterializer",
                "json-schema.registry.oracle.materializers[0].owner"                : "HR",
                "json-schema.registry.oracle.materializers[0].options.viewName"     : "ORDER_DV",
                "json-schema.registry.mappings[0].subject"                         : "com.acme.Order",
                "json-schema.registry.mappings[0].domain"                          : "APP_COM_ACME_ORDER"
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
        configuration.resolveOracleAuthorityProviders()[0].providerClassName == OracleDomainDiscoveryProvider.name
        configuration.resolveOracleAuthorityProviders()[0].options.prefix == "APP_"
        configuration.resolveOracleMaterializers()[0].providerClassName == OracleDomainMaterializer.name
    }

    void "configured Oracle domains become built-in provider selected set"() {
        given:
        JsonSchemaRegistryConfiguration configuration = new JsonSchemaRegistryConfiguration()
        configuration.oracle.domains = ["APP_COM_ACME_ORDER", "APP_COM_ACME_INVOICE"]

        when:
        JsonSchemaRegistryConfiguration.ProviderConfiguration provider = configuration.resolveOracleAuthorityProviders()[0]

        then:
        provider.providerClassName == OracleDomainDiscoveryProvider.name
        provider.options.include == "APP_COM_ACME_ORDER,APP_COM_ACME_INVOICE"
        !provider.options.containsKey("prefix")
    }

    void "configured Oracle domains narrow explicitly configured built-in provider only"() {
        given:
        JsonSchemaRegistryConfiguration configuration = new JsonSchemaRegistryConfiguration()
        configuration.oracle.domains = ["APP_COM_ACME_ORDER"]
        JsonSchemaRegistryConfiguration.ProviderConfiguration domainProvider = new JsonSchemaRegistryConfiguration.ProviderConfiguration(
                name: "domains",
                providerClassName: OracleDomainDiscoveryProvider.name,
                options: [exclude: "APP_OLD"]
        )
        JsonSchemaRegistryConfiguration.ProviderConfiguration customProvider = new JsonSchemaRegistryConfiguration.ProviderConfiguration(
                name: "duality-views",
                providerClassName: "example.oracle.OrderDualityViewDiscoveryProvider",
                options: [include: "ORDER_DV"]
        )
        configuration.oracle.authority.providers = [domainProvider, customProvider]

        when:
        List<JsonSchemaRegistryConfiguration.ProviderConfiguration> providers = configuration.resolveOracleAuthorityProviders()

        then:
        providers[0].options.include == "APP_COM_ACME_ORDER"
        providers[0].options.exclude == "APP_OLD"
        providers[1].options == [include: "ORDER_DV"]
    }

    void "built-in domain authority discovery is constrained by naming prefix"() {
        given:
        JsonSchemaRegistryConfiguration configuration = new JsonSchemaRegistryConfiguration()
        JsonSchemaRegistryConfiguration.ProviderConfiguration domainProvider = new JsonSchemaRegistryConfiguration.ProviderConfiguration(
                name: "domains",
                providerClassName: OracleDomainDiscoveryProvider.name
        )
        JsonSchemaRegistryConfiguration.ProviderConfiguration customProvider = new JsonSchemaRegistryConfiguration.ProviderConfiguration(
                name: "duality-views",
                providerClassName: "example.oracle.OrderDualityViewDiscoveryProvider"
        )
        configuration.oracle.authority.providers = [domainProvider, customProvider]
        configuration.naming.domainPrefix = "ORD_"

        when:
        List<JsonSchemaRegistryConfiguration.ProviderConfiguration> providers = configuration.resolveOracleAuthorityProviders()

        then:
        providers[0].options.prefix == "ORD_"
        providers[1].options.isEmpty()
    }

    void "explicit built-in domain include is not additionally narrowed by naming prefix"() {
        given:
        JsonSchemaRegistryConfiguration configuration = new JsonSchemaRegistryConfiguration()
        JsonSchemaRegistryConfiguration.ProviderConfiguration domainProvider = new JsonSchemaRegistryConfiguration.ProviderConfiguration(
                name: "domains",
                providerClassName: OracleDomainDiscoveryProvider.name,
                options: [include: "CUSTOM_DOMAIN"]
        )
        configuration.oracle.authority.providers = [domainProvider]
        configuration.naming.domainPrefix = "APP_"

        expect:
        configuration.resolveOracleAuthorityProviders()[0].options == [include: "CUSTOM_DOMAIN"]
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

    void "Oracle authority skipped schema is reported as unreadable authority"() {
        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "spec.name"                                                   : "skipped-authority-provider",
                "json-schema.registry.enabled"                                : "true",
                "json-schema.registry.authority"                              : "oracle",
                "json-schema.registry.oracle.enabled"                         : "true",
                "json-schema.registry.oracle.authority.providers[0].name"     : "domains",
                "json-schema.registry.oracle.authority.providers[0].providerClassName": SkippedAuthorityProvider.name,
                "json-schema.registry.sr.enabled"                             : "false"
        ]) { ApplicationContext context ->
            context.registerSingleton(DataSource, new NullDataSource(), Qualifiers.byName("default"), false)
            context.getBean(JsonSchemaRegistryReconciler).reconcile()
        }

        then:
        outcomes.size() == 1
        outcomes[0].target() == "oracle.authority"
        outcomes[0].status() == JsonSchemaRegistryOutcomeStatus.UNREADABLE_AUTHORITY
        outcomes[0].logicalSchema().oracleArtifactName() == "APP_ORDER"
        outcomes[0].message() == "bad schema"
    }

    void "Oracle domain authority derives subject from reversible domain name"() {
        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "spec.name"                                                   : "reversible-domain-provider",
                "json-schema.registry.enabled"                                : "true",
                "json-schema.registry.authority"                              : "oracle",
                "json-schema.registry.oracle.enabled"                         : "true",
                "json-schema.registry.oracle.authority.providers[0].name"     : "domains",
                "json-schema.registry.oracle.authority.providers[0].providerClassName": ReversibleDomainProvider.name,
                "json-schema.registry.naming.domain.prefix"                   : "APP_",
                "json-schema.registry.sr.enabled"                             : "false"
        ]) { ApplicationContext context ->
            context.registerSingleton(DataSource, new NullDataSource(), Qualifiers.byName("default"), false)
            context.getBean(JsonSchemaRegistryReconciler).reconcile()
        }

        then:
        outcomes.size() == 1
        outcomes[0].target() == "oracle.authority"
        outcomes[0].logicalSchema().logicalFqcn() == "COM.ACME.ORDER"
        outcomes[0].logicalSchema().subject() == "COM.ACME.ORDER"
        outcomes[0].logicalSchema().oracleArtifactName() == "APP_COM_ACME_ORDER"
    }

    void "custom Oracle authority provider can supply logical identity through options"() {
        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "spec.name"                                                   : "custom-identity-provider",
                "json-schema.registry.enabled"                                : "true",
                "json-schema.registry.authority"                              : "oracle",
                "json-schema.registry.oracle.enabled"                         : "true",
                "json-schema.registry.oracle.authority.providers[0].name"     : "duality-views",
                "json-schema.registry.oracle.authority.providers[0].providerClassName": CustomIdentityProvider.name,
                "json-schema.registry.oracle.authority.providers[0].options.logicalFqcn": "com.acme.OrderView",
                "json-schema.registry.oracle.authority.providers[0].options.subject": "orders-value",
                "json-schema.registry.oracle.authority.providers[0].options.viewName": "ORDER_DV",
                "json-schema.registry.sr.enabled"                             : "false"
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

    void "resolves Oracle materializer from Micronaut bean"() {
        expect:
        withContext(["spec.name": "bean-materializer"]) { ApplicationContext context ->
            context.getBean(OracleSchemaMaterializerResolver)
                    .resolve(BeanMaterializer.name, getClass().classLoader)
                    .class == BeanMaterializer
        }
    }

    void "configured Oracle materializer receives provider specific artifact name"() {
        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "spec.name"                                                   : "bean-materializer",
                "json-schema.registry.oracle.enabled"                         : "true",
                "json-schema.registry.oracle.materializers[0].name"           : "duality-views",
                "json-schema.registry.oracle.materializers[0].providerClassName": BeanMaterializer.name,
                "json-schema.registry.oracle.materializers[0].options.viewName": "ORDER_DV"
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
                "json-schema.registry.oracle.enabled"                         : "true",
                "json-schema.registry.oracle.materializers[0].name"           : "duality-views",
                "json-schema.registry.oracle.materializers[0].providerClassName": BeanMaterializer.name
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

    void "Oracle materializer exception records one failed outcome and continues"() {
        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "spec.name"                                                     : "continuing-materializers",
                "json-schema.registry.oracle.enabled"                           : "true",
                "json-schema.registry.oracle.materializers[0].name"             : "throwing",
                "json-schema.registry.oracle.materializers[0].providerClassName" : ThrowingMaterializer.name,
                "json-schema.registry.oracle.materializers[1].name"             : "second",
                "json-schema.registry.oracle.materializers[1].providerClassName" : SecondMaterializer.name
        ]) { ApplicationContext context ->
            context.registerSingleton(DataSource, new NullDataSource(), Qualifiers.byName("default"), false)
            context.getBean(DefaultJsonSchemaRegistryReconciler).reconcileOracleTarget([
                    new JsonSchemaCandidate(new LogicalSchema("com.acme.Order", "com.acme.Order", null), '{"type":"object"}', "test")
            ])
        }

        then:
        outcomes.size() == 2
        outcomes[0].failure()
        outcomes[0].target() == "oracle.throwing"
        outcomes[0].status() == JsonSchemaRegistryOutcomeStatus.FAILED
        outcomes[0].message() == "boom"
        !outcomes[1].failure()
        outcomes[1].target() == "oracle.second"
        outcomes[1].status() == JsonSchemaRegistryOutcomeStatus.EQUIVALENT
    }

    void "Oracle materializer projection outcome short circuits reconcile"() {
        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "spec.name"                                                     : "projection-materializer",
                "json-schema.registry.oracle.enabled"                           : "true",
                "json-schema.registry.oracle.materializers[0].name"             : "projection",
                "json-schema.registry.oracle.materializers[0].providerClassName" : ProjectionMaterializer.name
        ]) { ApplicationContext context ->
            context.registerSingleton(DataSource, new NullDataSource(), Qualifiers.byName("default"), false)
            context.getBean(DefaultJsonSchemaRegistryReconciler).reconcileOracleTarget([
                    new JsonSchemaCandidate(new LogicalSchema("com.acme.Order", "com.acme.Order", null), '{"type":"object"}', "test")
            ])
        }

        then:
        outcomes.size() == 1
        outcomes[0].failure()
        outcomes[0].target() == "oracle.projection"
        outcomes[0].status() == JsonSchemaRegistryOutcomeStatus.PROJECTION_INCOMPATIBILITY
        outcomes[0].message() == "unsupported projection"
    }

    void "Oracle projection incompatibility outcome is always a failure"() {
        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "spec.name"                                                     : "non-failing-projection-materializer",
                "json-schema.registry.oracle.enabled"                           : "true",
                "json-schema.registry.oracle.materializers[0].name"             : "projection",
                "json-schema.registry.oracle.materializers[0].providerClassName" : NonFailingProjectionMaterializer.name
        ]) { ApplicationContext context ->
            context.registerSingleton(DataSource, new NullDataSource(), Qualifiers.byName("default"), false)
            context.getBean(DefaultJsonSchemaRegistryReconciler).reconcileOracleTarget([
                    new JsonSchemaCandidate(new LogicalSchema("com.acme.Order", "com.acme.Order", null), '{"type":"object"}', "test")
            ])
        }

        then:
        outcomes.size() == 1
        outcomes[0].failure()
        outcomes[0].target() == "oracle.projection"
        outcomes[0].status() == JsonSchemaRegistryOutcomeStatus.PROJECTION_INCOMPATIBILITY
    }

    void "built-in domain materializer requires mapping when SR subject prefix is not reversible"() {
        when:
        List<JsonSchemaRegistryOutcome> outcomes = withContext([
                "json-schema.registry.oracle.enabled"                 : "true",
                "json-schema.registry.naming.subject.prefix"          : "com.acme.",
                "json-schema.registry.oracle.materializers[0].name"   : "domains",
                "json-schema.registry.oracle.materializers[0].providerClassName": OracleDomainMaterializer.name
        ]) { ApplicationContext context ->
            context.registerSingleton(DataSource, new NullDataSource(), Qualifiers.byName("default"), false)
            context.getBean(DefaultJsonSchemaRegistryReconciler).reconcileOracleTarget([
                    new JsonSchemaCandidate(new LogicalSchema("custom.order.subject", "custom.order.subject", null), '{"type":"object"}', "test")
            ])
        }

        then:
        outcomes.size() == 1
        outcomes[0].failure()
        outcomes[0].target() == "oracle.domain"
        outcomes[0].message().contains("missing_mapping")
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
            JsonSchemaRegistryConfiguration configuration = new JsonSchemaRegistryConfiguration(authority: JsonSchemaRegistryAuthority.SR)
            configuration.sr.policy.mode = JsonSchemaRegistryPolicyMode.OBSERVE_ONLY
            DefaultJsonSchemaRegistryObservability observability = new DefaultJsonSchemaRegistryObservability(context)
            observability.record(
                    configuration,
                    Duration.ofMillis(25),
                    [JsonSchemaRegistryOutcome.ok(
                            new LogicalSchema("com.acme.Order", "com.acme.Order", null),
                            "sr",
                            JsonSchemaRegistryOutcomeStatus.EQUIVALENT,
                            "ok"
                    )]
            )
        }

        then:
        meterRegistry.get("json.schema.registry.outcomes")
                .tag("target", "sr")
                .tag("authority", "sr")
                .tag("mode", "observe_only")
                .tag("result", "equivalent")
                .tag("failure", "false")
                .counter()
                .count() == 1.0d
        meterRegistry.get("json.schema.registry.reconcile.duration")
                .tag("authority", "sr")
                .tag("dry_run", "false")
                .tag("failure", "false")
                .timer()
                .count() == 1
    }

    void "readiness indicator reads last reconciliation state"() {
        when:
        HealthStatus initial
        HealthStatus failed
        HealthStatus recovered
        JsonSchemaRegistryFailFastStrategy strategy
        withContext(["json-schema.registry.enabled": "true"]) { ApplicationContext context ->
            JsonSchemaRegistryConfiguration configuration = context.getBean(JsonSchemaRegistryConfiguration)
            JsonSchemaRegistryState state = context.getBean(JsonSchemaRegistryState)
            JsonSchemaRegistryReadinessIndicator indicator = context.getBean(JsonSchemaRegistryReadinessIndicator)

            initial = healthResult(indicator).status
            state.completed(Duration.ofMillis(10), [JsonSchemaRegistryOutcome.failure(
                    new LogicalSchema("com.acme.Order", "com.acme.Order", null),
                    "sr",
                    JsonSchemaRegistryOutcomeStatus.FAILED,
                    "failure"
            )])
            failed = healthResult(indicator).status
            state.completed(Duration.ofMillis(10), [JsonSchemaRegistryOutcome.ok(
                    new LogicalSchema("com.acme.Order", "com.acme.Order", null),
                    "sr",
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
                "json-schema.registry.enabled"            : "true",
                "json-schema.registry.fail-fast-strategy" : "none"
        ]) { ApplicationContext context ->
            JsonSchemaRegistryState state = context.getBean(JsonSchemaRegistryState)
            JsonSchemaRegistryReadinessIndicator indicator = context.getBean(JsonSchemaRegistryReadinessIndicator)
            state.completed(Duration.ofMillis(10), [JsonSchemaRegistryOutcome.failure(
                    new LogicalSchema("com.acme.Order", "com.acme.Order", null),
                    "sr",
                    JsonSchemaRegistryOutcomeStatus.FAILED,
                    "failure"
            )])
            healthResult(indicator).status
        } == HealthStatus.UP
    }

    void "readiness indicator stays up when failure strategy is startup abort after startup"() {
        expect:
        withContext([
                "json-schema.registry.enabled"            : "true",
                "json-schema.registry.fail-fast-strategy" : "startup_abort"
        ]) { ApplicationContext context ->
            JsonSchemaRegistryState state = context.getBean(JsonSchemaRegistryState)
            JsonSchemaRegistryReadinessIndicator indicator = context.getBean(JsonSchemaRegistryReadinessIndicator)
            state.completed(Duration.ofMillis(10), [JsonSchemaRegistryOutcome.failure(
                    new LogicalSchema("com.acme.Order", "com.acme.Order", null),
                    "sr",
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
                        "sr",
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
        withContext(["spec.name": "throwing-reconciler"]) { ApplicationContext context ->
            JsonSchemaRegistryService service = context.getBean(JsonSchemaRegistryService)
            JsonSchemaRegistryState state = context.getBean(JsonSchemaRegistryState)
            CountingObservability observability = context.getBean(CountingObservability)
            outcomes = service.resync()
            status = state.snapshot().status()
            records = observability.records.get()
        }

        then:
        outcomes.size() == 1
        outcomes[0].failure()
        outcomes[0].status() == JsonSchemaRegistryOutcomeStatus.FAILED
        status == JsonSchemaRegistryState.RunStatus.FAILED
        records == 1
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

        @Override
        void record(JsonSchemaRegistryConfiguration configuration,
                    Duration duration,
                    List<JsonSchemaRegistryOutcome> outcomes) {
            records.incrementAndGet()
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
                    "sr",
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
                    "sr",
                    JsonSchemaRegistryOutcomeStatus.EQUIVALENT,
                    "ok"
            )]
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "bean-discovery-provider")
    static final class BeanDiscoveryProvider implements OracleSchemaDiscoveryProvider {
        @Override
        OracleDiscoveryResult discover(Connection connection,
                                       OracleSourceSpec source,
                                       boolean skipOnError,
                                       OracleJsonSchemaLogger logger) {
            new OracleDiscoveryResult([], [], [])
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "skipped-authority-provider")
    static final class SkippedAuthorityProvider implements OracleSchemaDiscoveryProvider {
        @Override
        OracleDiscoveryResult discover(Connection connection,
                                       OracleSourceSpec source,
                                       boolean skipOnError,
                                       OracleJsonSchemaLogger logger) {
            new OracleDiscoveryResult([], [], [
                    new OracleDiscoverySkipped(
                            OracleDiscoveryScope.DOMAIN,
                            "APP_ORDER",
                            OracleDiscoveryStep.SCHEMA_RETRIEVAL,
                            "MALFORMED_JSON",
                            "bad schema",
                            null
                    )
            ])
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "reversible-domain-provider")
    static final class ReversibleDomainProvider implements OracleSchemaDiscoveryProvider {
        @Override
        OracleDiscoveryResult discover(Connection connection,
                                       OracleSourceSpec source,
                                       boolean skipOnError,
                                       OracleJsonSchemaLogger logger) {
            new OracleDiscoveryResult([
                    new io.micronaut.jsonschema.generator.oracle.OracleDiscoveredSchema(
                            OracleDiscoveryScope.DOMAIN,
                            "APP_COM_ACME_ORDER",
                            '{"type":"object"}',
                            "test"
                    )
            ], [], [])
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "custom-identity-provider")
    static final class CustomIdentityProvider implements OracleSchemaDiscoveryProvider {
        @Override
        OracleDiscoveryResult discover(Connection connection,
                                       OracleSourceSpec source,
                                       boolean skipOnError,
                                       OracleJsonSchemaLogger logger) {
            new OracleDiscoveryResult([
                    new io.micronaut.jsonschema.generator.oracle.OracleDiscoveredSchema(
                            OracleDiscoveryScope.DUALITY_VIEW,
                            "IGNORED_DV",
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
        JsonSchemaRegistryOutcome reconcile(Connection connection, OracleMaterializationRequest request) {
            JsonSchemaRegistryOutcome.ok(
                    request.candidate().logicalSchema(),
                    "oracle.duality-view",
                    JsonSchemaRegistryOutcomeStatus.EQUIVALENT,
                    request.artifactName()
            )
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "continuing-materializers")
    static final class ThrowingMaterializer implements OracleSchemaMaterializer {
        @Override
        JsonSchemaRegistryOutcome reconcile(Connection connection, OracleMaterializationRequest request) {
            throw new IllegalStateException("boom")
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "continuing-materializers")
    static final class SecondMaterializer implements OracleSchemaMaterializer {
        @Override
        JsonSchemaRegistryOutcome reconcile(Connection connection, OracleMaterializationRequest request) {
            JsonSchemaRegistryOutcome.ok(
                    request.candidate().logicalSchema(),
                    "oracle.second",
                    JsonSchemaRegistryOutcomeStatus.EQUIVALENT,
                    "ok"
            )
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "projection-materializer")
    static final class ProjectionMaterializer implements OracleSchemaMaterializer {
        @Override
        Optional<JsonSchemaRegistryOutcome> projectionCompatibility(OracleMaterializationRequest request) {
            Optional.of(JsonSchemaRegistryOutcome.failure(
                    request.candidate().logicalSchema(),
                    "oracle.projection",
                    JsonSchemaRegistryOutcomeStatus.PROJECTION_INCOMPATIBILITY,
                    "unsupported projection"
            ))
        }

        @Override
        JsonSchemaRegistryOutcome reconcile(Connection connection, OracleMaterializationRequest request) {
            throw new IllegalStateException("reconcile should not be called")
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "non-failing-projection-materializer")
    static final class NonFailingProjectionMaterializer implements OracleSchemaMaterializer {
        @Override
        Optional<JsonSchemaRegistryOutcome> projectionCompatibility(OracleMaterializationRequest request) {
            Optional.of(JsonSchemaRegistryOutcome.ok(
                    request.candidate().logicalSchema(),
                    "oracle.projection",
                    JsonSchemaRegistryOutcomeStatus.PROJECTION_INCOMPATIBILITY,
                    "unsupported projection"
            ))
        }

        @Override
        JsonSchemaRegistryOutcome reconcile(Connection connection, OracleMaterializationRequest request) {
            throw new IllegalStateException("reconcile should not be called")
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
