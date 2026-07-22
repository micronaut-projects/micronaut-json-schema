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
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jsonschema.JsonSchema;
import io.micronaut.jsonschema.JsonSchemaMapper;
import io.micronaut.jsonschema.generator.oracle.OracleDiscoveredSchema;
import io.micronaut.jsonschema.generator.oracle.OracleDiscoveryResult;
import io.micronaut.jsonschema.generator.oracle.OracleDiscoveryScope;
import io.micronaut.jsonschema.generator.oracle.OracleDiscoverySkipped;
import io.micronaut.jsonschema.generator.oracle.OracleSchemaDiscoveryProvider;
import io.micronaut.jsonschema.generator.oracle.OracleSourceSpec;
import io.micronaut.jsonschema.registry.oracle.OracleDomainDiscoveryProvider;
import io.micronaut.jsonschema.registry.oracle.OracleDomainMaterializer;
import io.micronaut.jsonschema.registry.oracle.OracleMaterializationRequest;
import io.micronaut.jsonschema.registry.oracle.OracleOperationRecorder;
import io.micronaut.jsonschema.registry.oracle.OracleSchemaDiscoveryProviderResolver;
import io.micronaut.jsonschema.registry.oracle.OracleSchemaMaterializer;
import io.micronaut.jsonschema.registry.oracle.OracleSchemaMaterializerResolver;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Default registry reconciler.
 *
 * @since 2.2.0
 */
@Singleton
public final class DefaultJsonSchemaRegistryReconciler implements JsonSchemaRegistryReconciler {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultJsonSchemaRegistryReconciler.class);
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
            case CSR -> reconcileCsrAuthority();
        };
    }

    private List<JsonSchemaRegistryOutcome> reconcileApplicationAuthority() {
        List<JsonSchemaRegistryOutcome> outcomes = new ArrayList<>();
        List<JsonSchemaCandidate> candidates = discoverApplicationCandidates(outcomes);
        if (candidates.isEmpty()) {
            LOG.info("No application JSON Schema authority objects discovered");
        }
        outcomes.addAll(reconcileCsrTarget(candidates));
        outcomes.addAll(reconcileOracleTarget(candidates));
        return outcomes;
    }

    private List<JsonSchemaCandidate> discoverApplicationCandidates(List<JsonSchemaRegistryOutcome> outcomes) {
        List<JsonSchemaCandidate> candidates = new ArrayList<>();
        ClassLoader classLoader = resolveClassLoader();
        JsonSchemaMapper schemaMapper = JsonSchemaMapper.create(classLoader);
        for (Class<?> type : BeanIntrospector.forClassLoader(classLoader)
            .findIntrospectedTypes(reference -> reference.getAnnotationMetadata().hasAnnotation(JsonSchema.class))) {
            String logicalName = type.getName();
            LogicalSchema logicalSchema = new LogicalSchema(
                logicalName,
                configuration.getNaming().getSubjectPrefix() + logicalName,
                null
            );
            try {
                Optional<String> schema = schemaMapper.generateSchemaFor(type);
                if (schema.isEmpty()) {
                    outcomes.add(JsonSchemaRegistryOutcome.failure(
                        logicalSchema,
                        "application.authority",
                        JsonSchemaRegistryOutcomeStatus.UNREADABLE_AUTHORITY,
                        "Unable to generate JSON Schema for " + logicalName
                    ));
                    continue;
                }
                normalizer.normalize(schema.get());
                candidates.add(new JsonSchemaCandidate(logicalSchema, schema.get(), "application:" + logicalName));
                outcomes.add(JsonSchemaRegistryOutcome.ok(
                    logicalSchema,
                    "application.authority",
                    JsonSchemaRegistryOutcomeStatus.EQUIVALENT,
                    "Read application authority schema for " + logicalName
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

    private List<JsonSchemaRegistryOutcome> reconcileCsrAuthority() {
        if (!configuration.getCsr().isEnabled()) {
            return List.of(JsonSchemaRegistryOutcome.failure(
                new LogicalSchema("csr", null, null),
                "csr.authority",
                JsonSchemaRegistryOutcomeStatus.MISSING_AUTHORITY,
                "micronaut.jsonschema.registry.authority=csr requires micronaut.jsonschema.registry.csr.enabled=true"
            ));
        }
        List<JsonSchemaRegistryOutcome> outcomes = new ArrayList<>();
        List<JsonSchemaCandidate> candidates = new ArrayList<>();
        JsonSchemaRegistryConfiguration.CsrConfiguration csr = configuration.getCsr();
        try (ConfluentSchemaRegistryClient csrClient = new ConfluentSchemaRegistryClient(csr, meterRegistry())) {
            List<String> subjects = csr.getSubjects().isEmpty()
                ? csrClient.subjects(configuration.getNaming().getSubjectPrefix())
                : csr.getSubjects();
            if (subjects.isEmpty()) {
                LOG.info("No Schema Registry authority subjects discovered");
            }
            for (String subject : subjects) {
                Optional<String> latest = csrClient.latestSchema(subject);
                LogicalSchema logicalSchema = logicalSchemaFromSubject(subject);
                if (latest.isEmpty()) {
                    outcomes.add(JsonSchemaRegistryOutcome.failure(
                        logicalSchema,
                        "csr.authority",
                        JsonSchemaRegistryOutcomeStatus.MISSING_AUTHORITY,
                        "No latest Schema Registry schema found for subject " + subject
                    ));
                    continue;
                }
                try {
                    normalizer.normalize(latest.get());
                    outcomes.add(JsonSchemaRegistryOutcome.ok(
                        logicalSchema,
                        "csr.authority",
                        JsonSchemaRegistryOutcomeStatus.EQUIVALENT,
                        "Read Schema Registry authority schema from " + subject
                    ));
                    if (requiresExplicitCsrPairing(subject)) {
                        outcomes.add(JsonSchemaRegistryOutcome.failure(
                            logicalSchema,
                            "oracle",
                            JsonSchemaRegistryOutcomeStatus.FAILED,
                            "missing_mapping: Unable to derive logicalFqcn from Schema Registry subject " + subject
                                + "; configure an explicit mapping because subjectPrefix is "
                                + configuration.getNaming().getSubjectPrefix()
                        ));
                        continue;
                    }
                    candidates.add(new JsonSchemaCandidate(logicalSchema, latest.get(), "csr:" + subject));
                } catch (Exception e) {
                    outcomes.add(JsonSchemaRegistryOutcome.failure(
                        logicalSchema,
                        "csr.authority",
                        JsonSchemaRegistryOutcomeStatus.UNREADABLE_AUTHORITY,
                        e.getMessage()
                    ));
                }
            }
        } catch (Exception e) {
            outcomes.add(JsonSchemaRegistryOutcome.failure(
                new LogicalSchema("csr", null, null),
                "csr.authority",
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
                "micronaut.jsonschema.registry.authority=oracle requires micronaut.jsonschema.registry.oracle.enabled=true"
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
                if (configuration.getCsr().isEnabled()) {
                    outcomes.addAll(reconcileCsrTarget(List.of(candidate)));
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
            OracleDiscoveryResult result = recordOperation(
                "oracle",
                "introspection",
                () -> provider.discover(connection, sourceSpec, true, LOG::info)
            );
            result.warnings().forEach(warning -> LOG.warn("Oracle authority discovery warning: {}", warning));
            result.skipped().forEach(skipped -> {
                LOG.warn("Oracle authority discovery skipped: {}", skipped);
                outcomes.add(skippedOutcome(providerConfiguration, providerClassName, skipped));
            });
            for (OracleDiscoveredSchema schema : result.schemas()) {
                try {
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
        List<JsonSchemaRegistryOutcome> outcomes = new ArrayList<>();
        if (candidates.isEmpty()) {
            return outcomes;
        }
        try (Connection connection = resolveDataSource().getConnection()) {
            ClassLoader classLoader = resolveClassLoader();
            for (JsonSchemaRegistryConfiguration.ProviderConfiguration materializerConfiguration : configuration.resolveOracleMaterializers()) {
                String providerClassName = providerClassName(materializerConfiguration, OracleDomainMaterializer.class.getName());
                OracleSchemaMaterializer materializer;
                try {
                    materializer = materializerResolver.resolve(providerClassName, classLoader);
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
                        configuration.isDryRun(),
                        new OracleOperationRecorder() {
                            @Override
                            public <T> T record(String operationName, OracleOperation<T> operation) throws Exception {
                                return recordOperation("oracle", operationName, operation::execute);
                            }
                        }
                    );
                    Optional<JsonSchemaRegistryOutcome> projection = recordOperation(
                        "oracle",
                        "projection",
                        () -> materializer.projectionCompatibility(request)
                    );
                    outcomes.add(projection.orElseGet(() -> {
                        try {
                            return recordOperation(
                                "oracle",
                                "materialize",
                                () -> materializer.reconcile(connection, request)
                            );
                        } catch (Exception e) {
                            return JsonSchemaRegistryOutcome.failure(
                                candidate.logicalSchema(),
                                "oracle",
                                JsonSchemaRegistryOutcomeStatus.FAILED,
                                e.getMessage()
                            );
                        }
                    }));
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

    private List<JsonSchemaRegistryOutcome> reconcileCsrTarget(List<JsonSchemaCandidate> candidates) {
        if (!configuration.getCsr().isEnabled()) {
            return List.of();
        }
        List<JsonSchemaRegistryOutcome> outcomes = new ArrayList<>();
        JsonSchemaRegistryConfiguration.CsrConfiguration csr = configuration.getCsr();
        try (ConfluentSchemaRegistryClient csrClient = new ConfluentSchemaRegistryClient(csr, meterRegistry())) {
            for (JsonSchemaCandidate candidate : candidates) {
                String subject = resolveCsrSubject(candidate);
                if (subject == null || subject.isBlank()) {
                    outcomes.add(JsonSchemaRegistryOutcome.failure(
                        candidate.logicalSchema(),
                        "csr",
                        JsonSchemaRegistryOutcomeStatus.FAILED,
                        "missing_mapping: Unable to derive Schema Registry subject; configure micronaut.jsonschema.registry.mappings for this logical schema"
                    ));
                    continue;
                }
                try {
                    Optional<String> latest = csrClient.latestSchema(subject);
                    if (latest.isEmpty()) {
                        if (csr.getPolicy().getMode() == JsonSchemaRegistryPolicyMode.OBSERVE_ONLY) {
                            outcomes.add(JsonSchemaRegistryOutcome.ok(
                                candidate.logicalSchema(),
                                "csr",
                                JsonSchemaRegistryOutcomeStatus.MISSING_TARGET,
                                "Schema Registry subject is missing: " + subject
                            ));
                        } else if (configuration.isDryRun()) {
                            outcomes.add(JsonSchemaRegistryOutcome.ok(
                                candidate.logicalSchema(),
                                "csr",
                                JsonSchemaRegistryOutcomeStatus.CREATED,
                                "[DRY-RUN] would register Schema Registry subject " + subject
                            ));
                        } else {
                            ensureSchemaRegistryWritable(csrClient, subject);
                            csrClient.register(subject, candidate.schemaJson());
                            outcomes.add(JsonSchemaRegistryOutcome.ok(
                                candidate.logicalSchema(),
                                "csr",
                                JsonSchemaRegistryOutcomeStatus.CREATED,
                                "Registered Schema Registry subject " + subject
                            ));
                        }
                        continue;
                    }
                    if (normalizer.equivalent(latest.get(), candidate.schemaJson())) {
                        outcomes.add(JsonSchemaRegistryOutcome.ok(
                            candidate.logicalSchema(),
                            "csr",
                            JsonSchemaRegistryOutcomeStatus.EQUIVALENT,
                            "Schema Registry subject is equivalent: " + subject
                        ));
                    } else if (csr.getPolicy().getMode() == JsonSchemaRegistryPolicyMode.OBSERVE_ONLY) {
                        outcomes.add(JsonSchemaRegistryOutcome.ok(
                            candidate.logicalSchema(),
                            "csr",
                            JsonSchemaRegistryOutcomeStatus.DRIFT,
                            "Schema Registry subject drift detected: " + subject
                        ));
                    } else if (configuration.isDryRun()) {
                        outcomes.add(JsonSchemaRegistryOutcome.ok(
                            candidate.logicalSchema(),
                            "csr",
                            JsonSchemaRegistryOutcomeStatus.CREATED,
                            "[DRY-RUN] would register new Schema Registry version for " + subject
                        ));
                    } else {
                        ensureSchemaRegistryWritable(csrClient, subject);
                        Optional<String> compatibility = csrClient.compatibility(subject, candidate.schemaJson());
                        if (compatibility.isPresent() && !"true".equalsIgnoreCase(compatibility.get())) {
                            outcomes.add(JsonSchemaRegistryOutcome.failure(
                                candidate.logicalSchema(),
                                "csr",
                                JsonSchemaRegistryOutcomeStatus.FAILED,
                                "Schema Registry compatibility check failed for subject " + subject
                            ));
                            continue;
                        }
                        csrClient.register(subject, candidate.schemaJson());
                        outcomes.add(JsonSchemaRegistryOutcome.ok(
                            candidate.logicalSchema(),
                            "csr",
                            JsonSchemaRegistryOutcomeStatus.CREATED,
                            "Registered new Schema Registry version for " + subject
                        ));
                    }
                } catch (Exception e) {
                    outcomes.add(JsonSchemaRegistryOutcome.failure(
                        candidate.logicalSchema(),
                        "csr",
                        JsonSchemaRegistryOutcomeStatus.FAILED,
                        e.getMessage()
                    ));
                }
            }
        } catch (Exception e) {
            outcomes.add(JsonSchemaRegistryOutcome.failure(
                new LogicalSchema("csr", null, null),
                "csr",
                JsonSchemaRegistryOutcomeStatus.FAILED,
                e.getMessage()
            ));
        }
        return outcomes;
    }

    private static void ensureSchemaRegistryWritable(ConfluentSchemaRegistryClient csrClient, String subject) throws Exception {
        Optional<String> mode = csrClient.mode(subject);
        if (mode.isPresent() && !"READWRITE".equalsIgnoreCase(mode.get())) {
            throw new JsonSchemaRegistryException("Schema Registry is not writable for subject " + subject + ": " + mode.get());
        }
    }

    private JsonSchemaCandidate toOracleCandidate(JsonSchemaRegistryConfiguration.ProviderConfiguration providerConfiguration,
                                                  String providerClassName,
                                                  OracleDiscoveredSchema schema) {
        LogicalSchema logicalSchema = logicalSchemaFromOracle(providerConfiguration, providerClassName, schema);
        return new JsonSchemaCandidate(logicalSchema, schema.schemaJson(), schema.scope() + ":" + schema.name());
    }

    private JsonSchemaRegistryOutcome skippedOutcome(JsonSchemaRegistryConfiguration.ProviderConfiguration providerConfiguration,
                                                     String providerClassName,
                                                     OracleDiscoverySkipped skipped) {
        LogicalSchema logicalSchema = logicalSchemaFromOracle(
            providerConfiguration,
            providerClassName,
            new OracleDiscoveredSchema(skipped.scope(), skipped.name(), "{}", "skipped")
        );
        JsonSchemaRegistryOutcomeStatus status = skipped.reason() != null && skipped.reason().startsWith("MISSING")
            ? JsonSchemaRegistryOutcomeStatus.MISSING_AUTHORITY
            : JsonSchemaRegistryOutcomeStatus.UNREADABLE_AUTHORITY;
        return JsonSchemaRegistryOutcome.failure(logicalSchema, "oracle.authority", status, skipped.message());
    }

    private LogicalSchema logicalSchemaFromOracle(JsonSchemaRegistryConfiguration.ProviderConfiguration providerConfiguration,
                                                  String providerClassName,
                                                  OracleDiscoveredSchema schema) {
        if (isDomainProvider(providerClassName, schema)) {
            String domainName = schema.name().toUpperCase(Locale.ENGLISH);
            JsonSchemaRegistryConfiguration.Mapping mapping = configuration.mappingsByDomain().get(domainName);
            if (mapping != null && mapping.getSubject() != null && !mapping.getSubject().isBlank()) {
                String logicalName = logicalNameFromSubject(mapping.getSubject());
                return new LogicalSchema(logicalName, mapping.getSubject(), domainName);
            }
            Optional<String> logicalName = logicalNameFromDomainName(domainName);
            if (logicalName.isPresent()) {
                return new LogicalSchema(
                    logicalName.get(),
                    configuration.getNaming().getSubjectPrefix() + logicalName.get(),
                    domainName
                );
            }
            return new LogicalSchema(domainName, null, domainName);
        }
        Map<String, String> options = providerConfiguration.getOptions();
        String logicalName = firstNonBlank(options.get("logicalFqcn"), schema.name());
        String subject = firstNonBlank(options.get("subject"), configuration.getNaming().getSubjectPrefix() + logicalName);
        String artifactName = firstNonBlank(options.get("artifactName"), options.get("viewName"), schema.name());
        return new LogicalSchema(logicalName, subject, artifactName);
    }

    private LogicalSchema logicalSchemaFromSubject(String subject) {
        JsonSchemaRegistryConfiguration.Mapping mapping = configuration.mappingsBySubject().get(subject);
        if (mapping != null) {
            return new LogicalSchema(logicalNameFromSubject(subject), subject, mapping.getDomain());
        }
        return new LogicalSchema(logicalNameFromSubject(subject), subject, null);
    }

    private String logicalNameFromSubject(String subject) {
        String prefix = configuration.getNaming().getSubjectPrefix();
        String logicalName = subject;
        if (!prefix.isBlank() && subject.startsWith(prefix)) {
            logicalName = subject.substring(prefix.length());
        }
        return logicalName;
    }

    private Optional<String> logicalNameFromDomainName(String domainName) {
        String prefix = configuration.getNaming().getDomainPrefix().toUpperCase(Locale.ENGLISH);
        if (!prefix.isBlank() && !domainName.startsWith(prefix)) {
            return Optional.empty();
        }
        String logicalName = domainName.substring(prefix.length());
        if (logicalName.isBlank() || logicalName.contains("_")) {
            return Optional.empty();
        }
        return domainNameFromLogicalName(logicalName).equals(domainName)
            ? Optional.of(logicalName)
            : Optional.empty();
    }

    private boolean requiresExplicitCsrPairing(String subject) {
        String prefix = configuration.getNaming().getSubjectPrefix();
        return configuration.getOracle().isEnabled()
            && !prefix.isBlank()
            && !subject.startsWith(prefix)
            && !configuration.mappingsBySubject().containsKey(subject);
    }

    private String resolveCsrSubject(JsonSchemaCandidate candidate) {
        String subject = candidate.logicalSchema().subject();
        if (subject != null && !subject.isBlank()) {
            return subject;
        }
        String oracleArtifactName = candidate.logicalSchema().oracleArtifactName();
        if (oracleArtifactName != null) {
            JsonSchemaRegistryConfiguration.Mapping mapping = configuration.mappingsByDomain().get(oracleArtifactName);
            if (mapping != null) {
                return mapping.getSubject();
            }
        }
        return null;
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
            || OracleDomainDiscoveryProvider.class.getName().equals(providerClassName);
    }

    private boolean isDomainMaterializer(String providerClassName) {
        return OracleDomainMaterializer.class.getName().equals(providerClassName);
    }

    private String domainNameFromLogicalName(String logicalName) {
        return domainNameFromLogicalName(configuration.getNaming().getDomainPrefix(), logicalName);
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

    private Optional<MeterRegistry> meterRegistry() {
        return beanContext.findBean(MeterRegistry.class);
    }

    private <T> T recordOperation(String target, String operation, ThrowingSupplier<T> supplier) throws Exception {
        Optional<MeterRegistry> meterRegistry = meterRegistry();
        if (meterRegistry.isEmpty()) {
            return supplier.get();
        }
        Timer.Sample sample = Timer.start(meterRegistry.get());
        try {
            T result = supplier.get();
            sample.stop(operationTimer(meterRegistry.get(), target, operation, false));
            return result;
        } catch (Exception e) {
            sample.stop(operationTimer(meterRegistry.get(), target, operation, true));
            throw e;
        }
    }

    private static Timer operationTimer(MeterRegistry meterRegistry, String target, String operation, boolean failure) {
        return Timer.builder("json.schema.registry.operation.duration")
            .description("JSON Schema Registry target operation duration")
            .tag("target", target)
            .tag("operation", operation)
            .tag("failure", Boolean.toString(failure))
            .register(meterRegistry);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
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

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
