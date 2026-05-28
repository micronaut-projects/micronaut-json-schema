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
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jsonschema.JsonSchema;
import io.micronaut.jsonschema.JsonSchemaMapper;
import io.micronaut.jsonschema.generator.oracle.OracleDiscoveredSchema;
import io.micronaut.jsonschema.generator.oracle.OracleDiscoveryResult;
import io.micronaut.jsonschema.generator.oracle.OracleDiscoveryScope;
import io.micronaut.jsonschema.generator.oracle.OracleDiscoverySkipped;
import io.micronaut.jsonschema.generator.oracle.OracleJsonSchemaLogger;
import io.micronaut.jsonschema.generator.oracle.OracleSchemaDiscoveryProvider;
import io.micronaut.jsonschema.generator.oracle.OracleSourceSpec;
import io.micronaut.jsonschema.registry.oracle.OracleDomainDiscoveryProvider;
import io.micronaut.jsonschema.registry.oracle.OracleDomainMaterializer;
import io.micronaut.jsonschema.registry.oracle.OracleMaterializationRequest;
import io.micronaut.jsonschema.registry.oracle.OracleSchemaDiscoveryProviderResolver;
import io.micronaut.jsonschema.registry.oracle.OracleSchemaMaterializer;
import io.micronaut.jsonschema.registry.oracle.OracleSchemaMaterializerResolver;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Default registry reconciler.
 *
 * @since 2.0.0
 */
@Singleton
public final class DefaultJsonSchemaRegistryReconciler implements JsonSchemaRegistryReconciler {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultJsonSchemaRegistryReconciler.class);
    private static final String GENERATOR_DOMAIN_PROVIDER = "io.micronaut.jsonschema.generator.oracle.OracleDomainDiscoveryProvider";
    private static final int MAX_ORACLE_IDENTIFIER_BYTES = 128;
    private static final int DOMAIN_HASH_HEX_LENGTH = 8;

    private final JsonSchemaRegistryConfiguration configuration;
    private final BeanContext beanContext;
    private final OracleSchemaDiscoveryProviderResolver providerResolver;
    private final OracleSchemaMaterializerResolver materializerResolver;
    private final JsonSchemaNormalizer normalizer;

    /**
     * @param configuration Registry configuration
     * @param beanContext Bean context
     * @param providerResolver Oracle discovery provider resolver
     * @param materializerResolver Oracle materializer resolver
     * @param normalizer Schema normalizer
     */
    public DefaultJsonSchemaRegistryReconciler(JsonSchemaRegistryConfiguration configuration,
                                               BeanContext beanContext,
                                               OracleSchemaDiscoveryProviderResolver providerResolver,
                                               OracleSchemaMaterializerResolver materializerResolver,
                                               JsonSchemaNormalizer normalizer) {
        this.configuration = configuration;
        this.beanContext = beanContext;
        this.providerResolver = providerResolver;
        this.materializerResolver = materializerResolver;
        this.normalizer = normalizer;
    }

    @Override
    public List<JsonSchemaRegistryOutcome> reconcile() {
        if (!configuration.isEnabled()) {
            return List.of();
        }
        return switch (configuration.getAuthority()) {
            case ORACLE -> reconcileOracleAuthority();
            case APPLICATION -> reconcileApplicationAuthority();
            case SR -> reconcileSrAuthority();
        };
    }

    private List<JsonSchemaRegistryOutcome> reconcileApplicationAuthority() {
        List<JsonSchemaRegistryOutcome> outcomes = new ArrayList<>();
        List<JsonSchemaCandidate> candidates = discoverApplicationCandidates(outcomes);
        if (candidates.isEmpty()) {
            LOG.info("No application JSON Schema authority objects discovered");
        }
        outcomes.addAll(reconcileSrTarget(candidates));
        outcomes.addAll(reconcileOracleTarget(candidates));
        return outcomes;
    }

    private List<JsonSchemaCandidate> discoverApplicationCandidates(List<JsonSchemaRegistryOutcome> outcomes) {
        List<JsonSchemaCandidate> candidates = new ArrayList<>();
        ClassLoader classLoader = resolveClassLoader();
        for (BeanIntrospection<Object> introspection : BeanIntrospector.forClassLoader(classLoader).findIntrospections(JsonSchema.class)) {
            Class<?> type = introspection.getBeanType();
            String logicalName = type.getName();
            LogicalSchema logicalSchema = new LogicalSchema(
                logicalName,
                configuration.getNaming().getSubjectPrefix() + logicalName,
                null
            );
            try {
                String schema = JsonSchemaMapper.generateSchemaFor(type)
                    .orElseThrow(() -> new JsonSchemaRegistryException("No generated JSON Schema found for " + logicalName));
                normalizer.normalize(schema);
                candidates.add(new JsonSchemaCandidate(logicalSchema, schema, "application:" + logicalName));
                outcomes.add(JsonSchemaRegistryOutcome.ok(
                    logicalSchema,
                    "application.authority",
                    JsonSchemaRegistryOutcomeStatus.EQUIVALENT,
                    "Loaded generated application authority schema for " + logicalName
                ));
            } catch (Exception e) {
                outcomes.add(JsonSchemaRegistryOutcome.failure(
                    logicalSchema,
                    "application.authority",
                    JsonSchemaRegistryOutcomeStatus.UNREADABLE_AUTHORITY,
                    e.getMessage()
                ));
            }
        }
        return candidates;
    }

    private List<JsonSchemaRegistryOutcome> reconcileSrAuthority() {
        if (!configuration.getSr().isEnabled()) {
            return List.of(JsonSchemaRegistryOutcome.failure(
                new LogicalSchema("sr", null, null),
                "sr.authority",
                JsonSchemaRegistryOutcomeStatus.MISSING_AUTHORITY,
                "json-schema.registry.authority=sr requires json-schema.registry.sr.enabled=true"
            ));
        }
        List<JsonSchemaRegistryOutcome> outcomes = new ArrayList<>();
        List<JsonSchemaCandidate> candidates = new ArrayList<>();
        ConfluentSchemaRegistryClient srClient = new ConfluentSchemaRegistryClient(configuration.getSr().getUrl());
        try {
            List<String> subjects = configuration.getSr().getSubjects().isEmpty()
                ? srClient.subjects(configuration.getNaming().getSubjectPrefix())
                : configuration.getSr().getSubjects();
            if (subjects.isEmpty()) {
                LOG.info("No Schema Registry authority subjects discovered");
            }
            for (String subject : subjects) {
                LogicalSchema logicalSchema = logicalSchemaFromSubject(subject);
                Optional<String> latest;
                try {
                    latest = srClient.latestSchema(subject);
                } catch (ConfluentSchemaRegistryClient.UnreadableSchemaException e) {
                    outcomes.add(JsonSchemaRegistryOutcome.failure(
                        logicalSchema,
                        "sr.authority",
                        JsonSchemaRegistryOutcomeStatus.UNREADABLE_AUTHORITY,
                        e.getMessage()
                    ));
                    continue;
                } catch (Exception e) {
                    outcomes.add(JsonSchemaRegistryOutcome.failure(
                        logicalSchema,
                        "sr.authority",
                        JsonSchemaRegistryOutcomeStatus.FAILED,
                        e.getMessage()
                    ));
                    continue;
                }
                if (latest.isEmpty()) {
                    outcomes.add(JsonSchemaRegistryOutcome.failure(
                        logicalSchema,
                        "sr.authority",
                        JsonSchemaRegistryOutcomeStatus.MISSING_AUTHORITY,
                        "No latest Schema Registry schema found for subject " + subject
                    ));
                    continue;
                }
                try {
                    normalizer.normalize(latest.get());
                    candidates.add(new JsonSchemaCandidate(logicalSchema, latest.get(), "sr:" + subject));
                    outcomes.add(JsonSchemaRegistryOutcome.ok(
                        logicalSchema,
                        "sr.authority",
                        JsonSchemaRegistryOutcomeStatus.EQUIVALENT,
                        "Read Schema Registry authority schema from " + subject
                    ));
                } catch (Exception e) {
                    outcomes.add(JsonSchemaRegistryOutcome.failure(
                        logicalSchema,
                        "sr.authority",
                        JsonSchemaRegistryOutcomeStatus.UNREADABLE_AUTHORITY,
                        e.getMessage()
                    ));
                }
            }
        } catch (Exception e) {
            outcomes.add(JsonSchemaRegistryOutcome.failure(
                new LogicalSchema("sr", null, null),
                "sr.authority",
                JsonSchemaRegistryOutcomeStatus.FAILED,
                e.getMessage()
            ));
        }
        outcomes.addAll(reconcileOracleTarget(candidates));
        return outcomes;
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
        try {
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
                        outcomes.addAll(reconcileSrTarget(List.of(candidate)));
                    }
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
        for (JsonSchemaRegistryConfiguration.ProviderConfiguration providerConfiguration
            : configuration.resolveOracleAuthorityProviders()) {
            String providerClassName = providerClassName(providerConfiguration, OracleDomainDiscoveryProvider.class.getName());
            OracleSchemaDiscoveryProvider provider = providerResolver.resolve(providerClassName);
            OracleSourceSpec sourceSpec = new OracleSourceSpec(
                providerName(providerConfiguration, "oracle-authority"),
                providerClassName,
                providerConfiguration.getOwner(),
                providerConfiguration.getOptions()
            );
            OracleDiscoveryResult result = provider.discover(connection, sourceSpec, true, oracleLogger());
            result.warnings().forEach(warning -> LOG.warn("Oracle authority discovery warning: {}", warning));
            result.skipped().forEach(skipped -> LOG.warn("Oracle authority discovery skipped: {}", skipped));
            Set<String> discoveredDomains = new LinkedHashSet<>();
            for (OracleDiscoveredSchema schema : result.schemas()) {
                try {
                    if (isDomainProvider(providerClassName, schema)) {
                        discoveredDomains.add(schema.name().toUpperCase(Locale.ENGLISH));
                    }
                    candidates.add(toOracleCandidate(providerConfiguration, providerClassName, schema));
                } catch (Exception e) {
                    outcomes.add(JsonSchemaRegistryOutcome.failure(
                        new LogicalSchema(schema.name(), null, schema.name()),
                        "oracle.authority",
                        JsonSchemaRegistryOutcomeStatus.UNREADABLE_AUTHORITY,
                        e.getMessage()
                    ));
                }
            }
            for (OracleDiscoverySkipped skipped : result.skipped()) {
                if (isDomainProviderClass(providerClassName) || skipped.scope() == OracleDiscoveryScope.DOMAIN) {
                    discoveredDomains.add(skipped.name().toUpperCase(Locale.ENGLISH));
                }
                outcomes.add(JsonSchemaRegistryOutcome.failure(
                    logicalSchemaFromOracleSkipped(providerConfiguration, providerClassName, skipped),
                    "oracle.authority",
                    JsonSchemaRegistryOutcomeStatus.UNREADABLE_AUTHORITY,
                    skipped.reason()
                ));
            }
            reportMissingConfiguredDomains(providerClassName, discoveredDomains, outcomes);
        }
        return candidates;
    }

    /**
     * Reconcile externally supplied candidates to the configured Oracle target.
     * This is useful for tests and authority modes that resolve candidates outside Oracle.
     *
     * @param candidates Candidate schemas
     * @return Reconciliation outcomes
     */
    public List<JsonSchemaRegistryOutcome> reconcileOracleTarget(List<JsonSchemaCandidate> candidates) {
        if (!configuration.getOracle().isEnabled()) {
            return List.of();
        }
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<JsonSchemaRegistryOutcome> outcomes = new ArrayList<>();
        try {
            DataSource dataSource = resolveDataSource();
            try (Connection connection = dataSource.getConnection()) {
                for (JsonSchemaRegistryConfiguration.ProviderConfiguration materializerConfiguration
                    : configuration.resolveOracleMaterializers()) {
                    String providerClassName = providerClassName(materializerConfiguration, OracleDomainMaterializer.class.getName());
                    OracleSchemaMaterializer materializer;
                    try {
                        materializer = materializerResolver.resolve(providerClassName);
                    } catch (Exception e) {
                        outcomes.add(JsonSchemaRegistryOutcome.failure(
                            new LogicalSchema(providerName(materializerConfiguration, providerClassName), null, null),
                            "oracle",
                            JsonSchemaRegistryOutcomeStatus.FAILED,
                            e.getMessage()
                        ));
                        continue;
                    }
                    for (JsonSchemaCandidate candidate : candidates) {
                        JsonSchemaCandidate resolvedCandidate = resolveOracleCandidate(
                            candidate,
                            materializerConfiguration,
                            providerClassName
                        );
                        if (resolvedCandidate == null) {
                            outcomes.add(JsonSchemaRegistryOutcome.failure(
                                candidate.logicalSchema(),
                                materializerTargetName(materializerConfiguration, providerClassName),
                                JsonSchemaRegistryOutcomeStatus.FAILED,
                                oracleMappingFailureMessage(providerClassName)
                            ));
                            continue;
                        }
                        String artifactName = resolveOracleArtifactName(
                            resolvedCandidate,
                            materializerConfiguration,
                            providerClassName
                        );
                        if (isDomainMaterializer(providerClassName) && (artifactName == null || artifactName.isBlank())) {
                            outcomes.add(JsonSchemaRegistryOutcome.failure(
                                resolvedCandidate.logicalSchema(),
                                "oracle.domain",
                                JsonSchemaRegistryOutcomeStatus.FAILED,
                                "Unable to derive Oracle domain name; missing_mapping; "
                                    + "configure json-schema.registry.mappings for this logical schema"
                            ));
                            continue;
                        }
                        OracleMaterializationRequest request = new OracleMaterializationRequest(
                            resolvedCandidate,
                            artifactName,
                            materializerConfiguration.getOwner(),
                            materializerConfiguration.getOptions(),
                            configuration.getOracle().getPolicy().getMode(),
                            configuration.getOracle().getDrift().getMode(),
                            configuration.isDryRun()
                        );
                        try {
                            Optional<JsonSchemaRegistryOutcome> projection = materializer.projectionCompatibility(request);
                            if (projection.isPresent()) {
                                outcomes.add(enforceProjectionFailure(projection.get()));
                                continue;
                            }
                            outcomes.add(materializer.reconcile(connection, request));
                        } catch (Exception e) {
                            outcomes.add(JsonSchemaRegistryOutcome.failure(
                                resolvedCandidate.logicalSchema(),
                                materializerTargetName(materializerConfiguration, providerClassName),
                                JsonSchemaRegistryOutcomeStatus.FAILED,
                                e.getMessage()
                            ));
                        }
                    }
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

    private List<JsonSchemaRegistryOutcome> reconcileSrTarget(List<JsonSchemaCandidate> candidates) {
        if (!configuration.getSr().isEnabled()) {
            return List.of();
        }
        List<JsonSchemaRegistryOutcome> outcomes = new ArrayList<>();
        ConfluentSchemaRegistryClient srClient = new ConfluentSchemaRegistryClient(configuration.getSr().getUrl());
        for (JsonSchemaCandidate candidate : candidates) {
            String subject = resolveSrSubject(candidate);
            if (subject == null || subject.isBlank()) {
                outcomes.add(JsonSchemaRegistryOutcome.failure(
                    candidate.logicalSchema(),
                    "sr",
                    JsonSchemaRegistryOutcomeStatus.FAILED,
                    "Unable to derive Schema Registry subject; missing_mapping; "
                        + "configure json-schema.registry.mappings for this logical schema"
                ));
                continue;
            }
            try {
                Optional<String> latest = srClient.latestSchema(subject);
                if (latest.isEmpty()) {
                    if (configuration.getSr().getPolicy().getMode() == JsonSchemaRegistryPolicyMode.OBSERVE_ONLY) {
                        outcomes.add(JsonSchemaRegistryOutcome.ok(
                            candidate.logicalSchema(),
                            "sr",
                            JsonSchemaRegistryOutcomeStatus.MISSING_TARGET,
                            "Schema Registry subject is missing: " + subject
                        ));
                    } else if (configuration.isDryRun()) {
                        outcomes.add(JsonSchemaRegistryOutcome.ok(
                            candidate.logicalSchema(),
                            "sr",
                            JsonSchemaRegistryOutcomeStatus.CREATED,
                            "[DRY-RUN] would register Schema Registry subject " + subject
                        ));
                    } else {
                        ensureSchemaRegistryWritable(srClient, subject);
                        srClient.register(subject, candidate.schemaJson());
                        outcomes.add(JsonSchemaRegistryOutcome.ok(
                            candidate.logicalSchema(),
                            "sr",
                            JsonSchemaRegistryOutcomeStatus.CREATED,
                            "Registered Schema Registry subject " + subject
                        ));
                    }
                    continue;
                }
                if (normalizer.equivalent(latest.get(), candidate.schemaJson())) {
                    outcomes.add(JsonSchemaRegistryOutcome.ok(
                        candidate.logicalSchema(),
                        "sr",
                        JsonSchemaRegistryOutcomeStatus.EQUIVALENT,
                        "Schema Registry subject is equivalent: " + subject
                    ));
                } else if (configuration.getSr().getPolicy().getMode() == JsonSchemaRegistryPolicyMode.OBSERVE_ONLY) {
                    outcomes.add(JsonSchemaRegistryOutcome.ok(
                        candidate.logicalSchema(),
                        "sr",
                        JsonSchemaRegistryOutcomeStatus.DRIFT,
                        "Schema Registry subject drift detected: " + subject
                    ));
                } else if (configuration.isDryRun()) {
                    outcomes.add(JsonSchemaRegistryOutcome.ok(
                        candidate.logicalSchema(),
                        "sr",
                        JsonSchemaRegistryOutcomeStatus.CREATED,
                        "[DRY-RUN] would register new Schema Registry version for " + subject
                    ));
                } else {
                    ensureSchemaRegistryWritable(srClient, subject);
                    srClient.register(subject, candidate.schemaJson());
                    outcomes.add(JsonSchemaRegistryOutcome.ok(
                        candidate.logicalSchema(),
                        "sr",
                        JsonSchemaRegistryOutcomeStatus.CREATED,
                        "Registered new Schema Registry version for " + subject
                    ));
                }
            } catch (Exception e) {
                outcomes.add(JsonSchemaRegistryOutcome.failure(
                    candidate.logicalSchema(),
                    "sr",
                    JsonSchemaRegistryOutcomeStatus.FAILED,
                    e.getMessage()
                ));
            }
        }
        return outcomes;
    }

    private static void ensureSchemaRegistryWritable(ConfluentSchemaRegistryClient srClient,
                                                     String subject) throws Exception {
        String mode = srClient.mode(subject);
        if (!"READWRITE".equalsIgnoreCase(mode)) {
            throw new JsonSchemaRegistryException(
                "Schema Registry mode is not READWRITE for subject " + subject + ": " + mode
            );
        }
    }

    private JsonSchemaCandidate toOracleCandidate(JsonSchemaRegistryConfiguration.ProviderConfiguration providerConfiguration,
                                                  String providerClassName,
                                                  OracleDiscoveredSchema schema) throws Exception {
        normalizer.normalize(schema.schemaJson());
        LogicalSchema logicalSchema = logicalSchemaFromOracle(providerConfiguration, providerClassName, schema);
        return new JsonSchemaCandidate(logicalSchema, schema.schemaJson(), schema.scope() + ":" + schema.name());
    }

    private LogicalSchema logicalSchemaFromOracle(JsonSchemaRegistryConfiguration.ProviderConfiguration providerConfiguration,
                                                  String providerClassName,
                                                  OracleDiscoveredSchema schema) {
        if (isDomainProvider(providerClassName, schema)) {
            String domainName = schema.name().toUpperCase(Locale.ENGLISH);
            JsonSchemaRegistryConfiguration.Mapping mapping = configuration.mappingsByDomain().get(domainName);
            if (mapping != null) {
                String logicalName = mapping.getSubject() == null ? domainName : logicalNameFromSubject(mapping.getSubject());
                return new LogicalSchema(logicalName, mapping.getSubject(), domainName);
            }
            return new LogicalSchema(domainName, null, domainName);
        }
        String logicalName = option(providerConfiguration, "logicalFqcn").orElse(schema.name());
        String subject = option(providerConfiguration, "subject")
            .orElse(configuration.getNaming().getSubjectPrefix() + logicalName);
        String artifactName = option(providerConfiguration, "artifactName")
            .or(() -> option(providerConfiguration, "viewName"))
            .orElse(schema.name());
        return new LogicalSchema(logicalName, subject, artifactName);
    }

    private LogicalSchema logicalSchemaFromOracleSkipped(JsonSchemaRegistryConfiguration.ProviderConfiguration providerConfiguration,
                                                        String providerClassName,
                                                        OracleDiscoverySkipped skipped) {
        if (isDomainProviderClass(providerClassName) || skipped.scope() == OracleDiscoveryScope.DOMAIN) {
            String domainName = skipped.name().toUpperCase(Locale.ENGLISH);
            JsonSchemaRegistryConfiguration.Mapping mapping = configuration.mappingsByDomain().get(domainName);
            if (mapping != null) {
                String logicalName = mapping.getSubject() == null ? domainName : logicalNameFromSubject(mapping.getSubject());
                return new LogicalSchema(logicalName, mapping.getSubject(), domainName);
            }
            return new LogicalSchema(domainName, null, domainName);
        }
        String logicalName = option(providerConfiguration, "logicalFqcn").orElse(skipped.name());
        String subject = option(providerConfiguration, "subject")
            .orElse(configuration.getNaming().getSubjectPrefix() + logicalName);
        String artifactName = option(providerConfiguration, "artifactName")
            .or(() -> option(providerConfiguration, "viewName"))
            .orElse(skipped.name());
        return new LogicalSchema(logicalName, subject, artifactName);
    }

    private LogicalSchema logicalSchemaFromSubject(String subject) {
        JsonSchemaRegistryConfiguration.Mapping mapping = configuration.mappingsBySubject().get(subject);
        if (mapping != null) {
            return new LogicalSchema(logicalNameFromSubject(subject), subject, mapping.getDomain());
        }
        return new LogicalSchema(logicalNameFromSubject(subject), subject, null);
    }

    private JsonSchemaCandidate resolveOracleCandidate(
        JsonSchemaCandidate candidate,
        JsonSchemaRegistryConfiguration.ProviderConfiguration materializerConfiguration,
        String providerClassName) {
        if (configuration.getAuthority() != JsonSchemaRegistryAuthority.SR) {
            return candidate;
        }
        String logicalName = candidate.logicalSchema().logicalFqcn();
        if (logicalName != null && !logicalName.isBlank()) {
            return candidate;
        }
        if (isDomainMaterializer(providerClassName)) {
            JsonSchemaRegistryConfiguration.Mapping mapping = configuration.mappingsBySubject().get(candidate.logicalSchema().subject());
            if (mapping != null && mapping.getDomain() != null) {
                return candidate;
            }
            return null;
        }
        String configuredSubject = materializerConfiguration.getOptions().get("subject");
        String configuredLogicalFqcn = materializerConfiguration.getOptions().get("logicalFqcn");
        String subject = candidate.logicalSchema().subject();
        if (subject == null || subject.isBlank()
            || configuredSubject == null || configuredSubject.isBlank()
            || !subject.equals(configuredSubject)
            || configuredLogicalFqcn == null || configuredLogicalFqcn.isBlank()) {
            return null;
        }
        return new JsonSchemaCandidate(
            new LogicalSchema(
                configuredLogicalFqcn,
                subject,
                candidate.logicalSchema().oracleArtifactName()
            ),
            candidate.schemaJson(),
            candidate.authoritySource()
        );
    }

    private String resolveSrSubject(JsonSchemaCandidate candidate) {
        String subject = candidate.logicalSchema().subject();
        if (subject != null && !subject.isBlank()) {
            return subject;
        }
        String oracleArtifactName = candidate.logicalSchema().oracleArtifactName();
        if (oracleArtifactName != null) {
            JsonSchemaRegistryConfiguration.Mapping mapping = configuration.mappingsByDomain()
                .get(oracleArtifactName.toUpperCase(Locale.ENGLISH));
            if (mapping != null) {
                return mapping.getSubject();
            }
        }
        return null;
    }

    private static JsonSchemaRegistryOutcome enforceProjectionFailure(JsonSchemaRegistryOutcome outcome) {
        if (outcome.status() == JsonSchemaRegistryOutcomeStatus.PROJECTION_INCOMPATIBILITY && !outcome.failure()) {
            return JsonSchemaRegistryOutcome.failure(
                outcome.logicalSchema(),
                outcome.target(),
                outcome.status(),
                outcome.message()
            );
        }
        return outcome;
    }

    private String resolveOracleArtifactName(
        JsonSchemaCandidate candidate,
        JsonSchemaRegistryConfiguration.ProviderConfiguration materializerConfiguration,
        String providerClassName) {
        String candidateArtifact = candidate.logicalSchema().oracleArtifactName();
        if (!isDomainMaterializer(providerClassName)) {
            String artifactName = materializerConfiguration.getOptions().get("artifactName");
            if (artifactName == null || artifactName.isBlank()) {
                artifactName = materializerConfiguration.getOptions().get("viewName");
            }
            if (artifactName != null && !artifactName.isBlank()) {
                return artifactName;
            }
            if (candidateArtifact != null && !candidateArtifact.isBlank()) {
                return candidateArtifact;
            }
            return null;
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
        if (!canDeriveOracleArtifactName(candidate.logicalSchema())) {
            return null;
        }
        return domainNameFromLogicalName(candidate.logicalSchema().logicalFqcn());
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

    private boolean isDomainProvider(String providerClassName, OracleDiscoveredSchema schema) {
        return schema.scope() == OracleDiscoveryScope.DOMAIN
            || isDomainProviderClass(providerClassName);
    }

    private boolean isDomainProviderClass(String providerClassName) {
        return OracleDomainDiscoveryProvider.class.getName().equals(providerClassName)
            || GENERATOR_DOMAIN_PROVIDER.equals(providerClassName);
    }

    private boolean isDomainMaterializer(String providerClassName) {
        return OracleDomainMaterializer.class.getName().equals(providerClassName);
    }

    private String materializerTargetName(JsonSchemaRegistryConfiguration.ProviderConfiguration configuration,
                                          String providerClassName) {
        if (isDomainMaterializer(providerClassName)) {
            return "oracle.domain";
        }
        return "oracle." + providerName(configuration, "materializer");
    }

    private String domainNameFromLogicalName(String logicalName) {
        return domainNameFromLogicalName(configuration.getNaming().getDomainPrefix(), logicalName);
    }

    private String logicalNameFromSubject(String subject) {
        String prefix = configuration.getNaming().getSubjectPrefix();
        if (prefix.isBlank()) {
            return subject;
        }
        if (subject.startsWith(prefix)) {
            return subject.substring(prefix.length());
        }
        return null;
    }

    private boolean canDeriveOracleArtifactName(LogicalSchema logicalSchema) {
        String logicalName = logicalSchema.logicalFqcn();
        if (logicalName == null || logicalName.isBlank()) {
            return false;
        }
        String subject = logicalSchema.subject();
        String prefix = configuration.getNaming().getSubjectPrefix();
        return subject == null || prefix.isBlank() || subject.startsWith(prefix);
    }

    private String oracleMappingFailureMessage(String providerClassName) {
        if (isDomainMaterializer(providerClassName)) {
            return "Unable to derive Oracle domain name; missing_mapping; "
                + "configure json-schema.registry.mappings for this logical schema";
        }
        return "Unable to derive Oracle logical schema identity; missing_mapping; "
            + "configure provider-specific options.subject/options.logicalFqcn for this materializer";
    }

    private void reportMissingConfiguredDomains(String providerClassName,
                                                Set<String> discoveredDomains,
                                                List<JsonSchemaRegistryOutcome> outcomes) {
        if (configuration.getOracle().getDomains().isEmpty()
            || !isDomainProviderClass(providerClassName)) {
            return;
        }
        for (String configuredDomain : configuration.getOracle().getDomains()) {
            String domainName = configuredDomain.toUpperCase(Locale.ENGLISH);
            if (discoveredDomains.contains(domainName)) {
                continue;
            }
            JsonSchemaRegistryConfiguration.Mapping mapping = configuration.mappingsByDomain().get(domainName);
            LogicalSchema logicalSchema = mapping == null
                ? new LogicalSchema(domainName, null, domainName)
                : new LogicalSchema(
                    mapping.getSubject() == null ? domainName : logicalNameFromSubject(mapping.getSubject()),
                    mapping.getSubject(),
                    domainName
                );
            outcomes.add(JsonSchemaRegistryOutcome.failure(
                logicalSchema,
                "oracle.authority",
                JsonSchemaRegistryOutcomeStatus.MISSING_AUTHORITY,
                "No Oracle authority domain found for " + domainName
            ));
        }
    }

    static String domainNameFromLogicalName(String domainPrefix, String logicalName) {
        String computed = (domainPrefix + logicalName.replace('.', '_')).toUpperCase(Locale.ENGLISH);
        if (computed.getBytes(StandardCharsets.UTF_8).length <= MAX_ORACLE_IDENTIFIER_BYTES) {
            return computed;
        }
        String suffix = "_" + HexFormat.of().formatHex(sha256(computed))
            .substring(0, DOMAIN_HASH_HEX_LENGTH)
            .toUpperCase(Locale.ENGLISH);
        int prefixByteLimit = MAX_ORACLE_IDENTIFIER_BYTES - suffix.getBytes(StandardCharsets.UTF_8).length;
        return utf8Prefix(computed, prefixByteLimit) + suffix;
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }

    private static String utf8Prefix(String value, int maxBytes) {
        int end = value.length();
        while (end > 0 && value.substring(0, end).getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            end -= Character.charCount(value.codePointBefore(end));
        }
        return value.substring(0, end);
    }

    private ClassLoader resolveClassLoader() {
        ClassLoader classLoader = beanContext.getClassLoader();
        if (classLoader != null) {
            return classLoader;
        }
        classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader == null ? getClass().getClassLoader() : classLoader;
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

    private static Optional<String> option(JsonSchemaRegistryConfiguration.ProviderConfiguration configuration,
                                           String name) {
        String value = configuration.getOptions().get(name);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private static OracleJsonSchemaLogger oracleLogger() {
        return new OracleJsonSchemaLogger() {
            @Override
            public void info(String message) {
                LOG.info(message);
            }

            @Override
            public void warn(String message) {
                LOG.warn(message);
            }
        };
    }
}
