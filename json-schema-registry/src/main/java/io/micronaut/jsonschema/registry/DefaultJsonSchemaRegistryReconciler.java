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

import io.micronaut.context.BeanContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jsonschema.generator.oracle.OracleDiscoveredSchema;
import io.micronaut.jsonschema.generator.oracle.OracleDiscoveryResult;
import io.micronaut.jsonschema.generator.oracle.OracleDiscoveryScope;
import io.micronaut.jsonschema.generator.oracle.OracleSchemaDiscoveryProvider;
import io.micronaut.jsonschema.generator.oracle.OracleSourceSpec;
import io.micronaut.jsonschema.registry.oracle.OracleDomainDiscoveryProvider;
import io.micronaut.jsonschema.registry.oracle.OracleDomainMaterializer;
import io.micronaut.jsonschema.registry.oracle.OracleMaterializationRequest;
import io.micronaut.jsonschema.registry.oracle.OracleSchemaDiscoveryProviderResolver;
import io.micronaut.jsonschema.registry.oracle.OracleSchemaMaterializer;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Default registry reconciler.
 *
 * @since 2.0.0
 */
@Singleton
public final class DefaultJsonSchemaRegistryReconciler implements JsonSchemaRegistryReconciler {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultJsonSchemaRegistryReconciler.class);
    private static final String GENERATOR_DOMAIN_PROVIDER = "io.micronaut.jsonschema.generator.oracle.OracleDomainDiscoveryProvider";

    private final JsonSchemaRegistryConfiguration configuration;
    private final BeanContext beanContext;
    private final OracleSchemaDiscoveryProviderResolver providerResolver;
    private final List<OracleSchemaMaterializer> oracleMaterializers;

    /**
     * @param configuration Registry configuration
     * @param beanContext Bean context
     * @param providerResolver Oracle discovery provider resolver
     * @param oracleMaterializers Oracle materializers
     */
    public DefaultJsonSchemaRegistryReconciler(JsonSchemaRegistryConfiguration configuration,
                                               BeanContext beanContext,
                                               OracleSchemaDiscoveryProviderResolver providerResolver,
                                               List<OracleSchemaMaterializer> oracleMaterializers) {
        this.configuration = configuration;
        this.beanContext = beanContext;
        this.providerResolver = providerResolver;
        this.oracleMaterializers = List.copyOf(oracleMaterializers);
    }

    @Override
    public List<JsonSchemaRegistryOutcome> reconcile() {
        if (!configuration.isEnabled()) {
            return List.of();
        }
        return switch (configuration.getAuthority()) {
            case ORACLE -> reconcileOracleAuthority();
            case APPLICATION -> unsupportedAuthority(JsonSchemaRegistryAuthority.APPLICATION);
            case SR -> unsupportedAuthority(JsonSchemaRegistryAuthority.SR);
        };
    }

    private List<JsonSchemaRegistryOutcome> reconcileOracleAuthority() {
        if (!configuration.getOracle().isEnabled()) {
            return List.of(JsonSchemaRegistryOutcome.failure(
                new LogicalSchema("oracle", null, null),
                "oracle.authority",
                JsonSchemaRegistryOutcomeStatus.MISSING_AUTHORITY,
                "json-schema.registry.authority=oracle requires json-schema.registry.oracle.enabled=true"
            ));
        }
        List<JsonSchemaRegistryOutcome> outcomes = new ArrayList<>();
        DataSource dataSource = resolveDataSource();
        try (Connection connection = dataSource.getConnection()) {
            List<JsonSchemaCandidate> candidates = discoverOracleCandidates(connection, outcomes);
            if (candidates.isEmpty()) {
                LOG.info("No Oracle JSON Schema authority objects discovered");
            }
            for (JsonSchemaCandidate candidate : candidates) {
                outcomes.add(JsonSchemaRegistryOutcome.ok(
                    candidate.logicalSchema(),
                    "oracle.authority",
                    JsonSchemaRegistryOutcomeStatus.EQUIVALENT,
                    "Read Oracle authority schema from " + candidate.authoritySource()
                ));
                if (configuration.getSr().isEnabled()) {
                    outcomes.add(JsonSchemaRegistryOutcome.failure(
                        candidate.logicalSchema(),
                        "sr",
                        JsonSchemaRegistryOutcomeStatus.FAILED,
                        "Schema Registry target reconciliation is not implemented in this POC"
                    ));
                }
            }
        } catch (Exception e) {
            outcomes.add(JsonSchemaRegistryOutcome.failure(
                new LogicalSchema("oracle", null, null),
                "oracle.authority",
                JsonSchemaRegistryOutcomeStatus.FAILED,
                e.getMessage()
            ));
        }
        return outcomes;
    }

    private List<JsonSchemaCandidate> discoverOracleCandidates(Connection connection,
                                                               List<JsonSchemaRegistryOutcome> outcomes) throws Exception {
        List<JsonSchemaCandidate> candidates = new ArrayList<>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = getClass().getClassLoader();
        }
        for (JsonSchemaRegistryConfiguration.ProviderConfiguration providerConfiguration : configuration.resolveOracleAuthorityProviders()) {
            String providerClassName = providerClassName(providerConfiguration, OracleDomainDiscoveryProvider.class.getName());
            OracleSchemaDiscoveryProvider provider = providerResolver.resolve(providerClassName, classLoader);
            OracleSourceSpec sourceSpec = new OracleSourceSpec(
                providerName(providerConfiguration, "oracle-authority"),
                providerClassName,
                providerConfiguration.getOwner(),
                providerConfiguration.getOptions()
            );
            OracleDiscoveryResult result = provider.discover(connection, sourceSpec, false, LOG::info);
            result.warnings().forEach(warning -> LOG.warn("Oracle authority discovery warning: {}", warning));
            result.skipped().forEach(skipped -> LOG.warn("Oracle authority discovery skipped: {}", skipped));
            for (OracleDiscoveredSchema schema : result.schemas()) {
                try {
                    candidates.add(toOracleCandidate(providerClassName, schema));
                } catch (Exception e) {
                    outcomes.add(JsonSchemaRegistryOutcome.failure(
                        new LogicalSchema(schema.name(), null, schema.name()),
                        "oracle.authority",
                        JsonSchemaRegistryOutcomeStatus.UNREADABLE_AUTHORITY,
                        e.getMessage()
                    ));
                }
            }
        }
        return candidates;
    }

    /**
     * Reconcile externally supplied candidates to the configured Oracle target.
     * This is useful for tests and for the next POC increment that wires application/SR authority.
     *
     * @param candidates Candidate schemas
     * @return Reconciliation outcomes
     */
    public List<JsonSchemaRegistryOutcome> reconcileOracleTarget(List<JsonSchemaCandidate> candidates) {
        if (!configuration.getOracle().isEnabled()) {
            return List.of();
        }
        List<JsonSchemaRegistryOutcome> outcomes = new ArrayList<>();
        DataSource dataSource = resolveDataSource();
        try (Connection connection = dataSource.getConnection()) {
            for (JsonSchemaRegistryConfiguration.ProviderConfiguration materializerConfiguration : configuration.resolveOracleMaterializers()) {
                String providerClassName = providerClassName(materializerConfiguration, OracleDomainMaterializer.class.getName());
                Optional<OracleSchemaMaterializer> materializer = findMaterializer(providerClassName);
                if (materializer.isEmpty()) {
                    outcomes.add(JsonSchemaRegistryOutcome.failure(
                        new LogicalSchema(providerName(materializerConfiguration, providerClassName), null, null),
                        "oracle",
                        JsonSchemaRegistryOutcomeStatus.FAILED,
                        "Unable to locate Oracle materializer bean: " + providerClassName
                    ));
                    continue;
                }
                for (JsonSchemaCandidate candidate : candidates) {
                    String artifactName = resolveOracleArtifactName(
                        candidate,
                        materializerConfiguration,
                        providerClassName
                    );
                    OracleMaterializationRequest request = new OracleMaterializationRequest(
                        candidate,
                        artifactName,
                        materializerConfiguration.getOwner(),
                        materializerConfiguration.getOptions(),
                        configuration.getOracle().getPolicy().getMode(),
                        configuration.getOracle().getDrift().getMode(),
                        configuration.isDryRun()
                    );
                    outcomes.add(materializer.get().reconcile(connection, request));
                }
            }
        } catch (Exception e) {
            outcomes.add(JsonSchemaRegistryOutcome.failure(
                new LogicalSchema("oracle", null, null),
                "oracle",
                JsonSchemaRegistryOutcomeStatus.FAILED,
                e.getMessage()
            ));
        }
        return outcomes;
    }

    private JsonSchemaCandidate toOracleCandidate(String providerClassName, OracleDiscoveredSchema schema) {
        LogicalSchema logicalSchema = logicalSchemaFromOracle(providerClassName, schema);
        return new JsonSchemaCandidate(logicalSchema, schema.schemaJson(), schema.scope() + ":" + schema.name());
    }

    private LogicalSchema logicalSchemaFromOracle(String providerClassName, OracleDiscoveredSchema schema) {
        if (isDomainProvider(providerClassName, schema)) {
            String domainName = schema.name().toUpperCase(Locale.ENGLISH);
            JsonSchemaRegistryConfiguration.Mapping mapping = configuration.mappingsByDomain().get(domainName);
            if (mapping != null) {
                return new LogicalSchema(mapping.getSubject() == null ? domainName : mapping.getSubject(), mapping.getSubject(), domainName);
            }
            String logicalName = logicalNameFromDomain(domainName);
            return new LogicalSchema(logicalName, configuration.getNaming().getSubjectPrefix() + logicalName, domainName);
        }
        String logicalName = schema.name();
        return new LogicalSchema(logicalName, configuration.getNaming().getSubjectPrefix() + logicalName, schema.name());
    }

    private String resolveOracleArtifactName(
        JsonSchemaCandidate candidate,
        JsonSchemaRegistryConfiguration.ProviderConfiguration materializerConfiguration,
        String providerClassName) {
        String candidateArtifact = candidate.logicalSchema().oracleArtifactName();
        if (!isDomainMaterializer(providerClassName)) {
            String artifactName = materializerConfiguration.getOptions().get("artifactName");
            if (artifactName != null && !artifactName.isBlank()) {
                return artifactName;
            }
            if (candidateArtifact != null && !candidateArtifact.isBlank()) {
                return candidateArtifact;
            }
            return candidate.logicalSchema().name();
        }
        JsonSchemaRegistryConfiguration.Mapping mapping = configuration.mappingsBySubject().get(candidate.logicalSchema().subject());
        if (mapping != null && mapping.getDomain() != null) {
            return mapping.getDomain();
        }
        String configured = materializerConfiguration.getOptions().get("domain");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        if (candidateArtifact != null && !candidateArtifact.isBlank()) {
            return candidateArtifact;
        }
        return domainNameFromLogicalName(candidate.logicalSchema().name());
    }

    private DataSource resolveDataSource() {
        String datasourceName = configuration.getOracle().getDatasource();
        Optional<DataSource> named = beanContext.findBean(DataSource.class, Qualifiers.byName(datasourceName));
        if (named.isPresent()) {
            return named.get();
        }
        if ("default".equals(datasourceName)) {
            return beanContext.findBean(DataSource.class)
                .orElseThrow(() -> new JsonSchemaRegistryException("No DataSource bean available for JSON Schema Registry"));
        }
        throw new JsonSchemaRegistryException("No DataSource bean named '" + datasourceName + "' available for JSON Schema Registry");
    }

    private Optional<OracleSchemaMaterializer> findMaterializer(String providerClassName) {
        return oracleMaterializers.stream()
            .filter(materializer -> materializer.providerClassName().equals(providerClassName) || materializer.getClass().getName().equals(providerClassName))
            .findFirst();
    }

    private List<JsonSchemaRegistryOutcome> unsupportedAuthority(JsonSchemaRegistryAuthority authority) {
        return List.of(JsonSchemaRegistryOutcome.failure(
            new LogicalSchema(authority.name().toLowerCase(Locale.ENGLISH), null, null),
            "registry",
            JsonSchemaRegistryOutcomeStatus.FAILED,
            "Authority mode " + authority + " is not implemented in this POC"
        ));
    }

    private boolean isDomainProvider(String providerClassName, OracleDiscoveredSchema schema) {
        return schema.scope() == OracleDiscoveryScope.DOMAIN
            || OracleDomainDiscoveryProvider.class.getName().equals(providerClassName)
            || GENERATOR_DOMAIN_PROVIDER.equals(providerClassName);
    }

    private boolean isDomainMaterializer(String providerClassName) {
        return OracleDomainMaterializer.class.getName().equals(providerClassName);
    }

    private String logicalNameFromDomain(String domainName) {
        String prefix = configuration.getNaming().getDomainPrefix().toUpperCase(Locale.ENGLISH);
        String value = domainName;
        if (!prefix.isBlank() && value.startsWith(prefix)) {
            value = value.substring(prefix.length());
        }
        return value.toLowerCase(Locale.ENGLISH).replace('_', '.');
    }

    private String domainNameFromLogicalName(String logicalName) {
        return configuration.getNaming().getDomainPrefix() + logicalName.replace('.', '_').toUpperCase(Locale.ENGLISH);
    }

    private static String providerClassName(JsonSchemaRegistryConfiguration.ProviderConfiguration configuration,
                                            String defaultClassName) {
        String providerClassName = configuration.getProviderClassName();
        return providerClassName == null || providerClassName.isBlank() ? defaultClassName : providerClassName;
    }

    private static String providerName(JsonSchemaRegistryConfiguration.ProviderConfiguration configuration,
                                       String defaultName) {
        String name = configuration.getName();
        return name == null || name.isBlank() ? defaultName : name;
    }
}
