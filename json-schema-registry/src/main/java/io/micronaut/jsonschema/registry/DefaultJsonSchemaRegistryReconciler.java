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
import io.micronaut.core.io.Readable;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jsonschema.JsonSchema;
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
import io.micronaut.jsonschema.registry.oracle.OracleSchemaMaterializerResolver;
import io.micronaut.jsonschema.utils.JsonSchemaClassPathResourceLoader;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        JsonSchemaClassPathResourceLoader loader = JsonSchemaClassPathResourceLoader.createDefault(classLoader);
        for (Class<?> type : BeanIntrospector.forClassLoader(classLoader)
            .findIntrospectedTypes(reference -> reference.getAnnotationMetadata().hasAnnotation(JsonSchema.class))) {
            String logicalName = type.getName();
            LogicalSchema logicalSchema = new LogicalSchema(
                logicalName,
                configuration.getNaming().getSubjectPrefix() + logicalName,
                null
            );
            try {
                Optional<String> schema = loader.jsonSchemaStringForClass(type);
                if (schema.isEmpty()) {
                    outcomes.add(JsonSchemaRegistryOutcome.failure(
                        logicalSchema,
                        "application.authority",
                        JsonSchemaRegistryOutcomeStatus.UNREADABLE_AUTHORITY,
                        "No generated JSON Schema resource found for " + logicalName
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
        if (candidates.isEmpty() && outcomes.isEmpty()) {
            candidates.addAll(discoverApplicationResourceCandidates(loader, outcomes));
        }
        return candidates;
    }

    private List<JsonSchemaCandidate> discoverApplicationResourceCandidates(JsonSchemaClassPathResourceLoader loader,
                                                                            List<JsonSchemaRegistryOutcome> outcomes) {
        List<JsonSchemaCandidate> candidates = new ArrayList<>();
        for (Map.Entry<String, Readable> entry : loader.jsonSchemas().entrySet()) {
            String logicalName = logicalNameFromSchemaResource(entry.getKey());
            LogicalSchema logicalSchema = new LogicalSchema(
                logicalName,
                configuration.getNaming().getSubjectPrefix() + logicalName,
                null
            );
            try (InputStream inputStream = entry.getValue().asInputStream()) {
                String schema = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                normalizer.normalize(schema);
                candidates.add(new JsonSchemaCandidate(logicalSchema, schema, "application:" + entry.getKey()));
                outcomes.add(JsonSchemaRegistryOutcome.ok(
                    logicalSchema,
                    "application.authority",
                    JsonSchemaRegistryOutcomeStatus.EQUIVALENT,
                    "Read application authority schema from " + entry.getKey()
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
                Optional<String> latest = srClient.latestSchema(subject);
                LogicalSchema logicalSchema = logicalSchemaFromSubject(subject);
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
        DataSource dataSource = resolveDataSource();
        try (Connection connection = dataSource.getConnection()) {
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
                        configuration.isDryRun()
                    );
                    outcomes.add(materializer.reconcile(connection, request));
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
                    "Unable to derive Schema Registry subject; configure json-schema.registry.mappings for this logical schema"
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
                            "Dry run: would register Schema Registry subject " + subject
                        ));
                    } else {
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
                        "Dry run: would register new Schema Registry version for " + subject
                    ));
                } else {
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
            return new LogicalSchema(domainName, null, domainName);
        }
        String logicalName = schema.name();
        return new LogicalSchema(logicalName, configuration.getNaming().getSubjectPrefix() + logicalName, schema.name());
    }

    private LogicalSchema logicalSchemaFromSubject(String subject) {
        JsonSchemaRegistryConfiguration.Mapping mapping = configuration.mappingsBySubject().get(subject);
        if (mapping != null) {
            return new LogicalSchema(subject, subject, mapping.getDomain());
        }
        String prefix = configuration.getNaming().getSubjectPrefix();
        String logicalName = subject;
        if (!prefix.isBlank() && subject.startsWith(prefix)) {
            logicalName = subject.substring(prefix.length());
        }
        return new LogicalSchema(logicalName, subject, null);
    }

    private String resolveSrSubject(JsonSchemaCandidate candidate) {
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

    private boolean isDomainProvider(String providerClassName, OracleDiscoveredSchema schema) {
        return schema.scope() == OracleDiscoveryScope.DOMAIN
            || OracleDomainDiscoveryProvider.class.getName().equals(providerClassName)
            || GENERATOR_DOMAIN_PROVIDER.equals(providerClassName);
    }

    private boolean isDomainMaterializer(String providerClassName) {
        return OracleDomainMaterializer.class.getName().equals(providerClassName);
    }

    private String domainNameFromLogicalName(String logicalName) {
        return configuration.getNaming().getDomainPrefix() + logicalName.replace('.', '_').toUpperCase(Locale.ENGLISH);
    }

    private ClassLoader resolveClassLoader() {
        ClassLoader classLoader = beanContext.getClassLoader();
        if (classLoader != null) {
            return classLoader;
        }
        classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader == null ? getClass().getClassLoader() : classLoader;
    }

    private static String logicalNameFromSchemaResource(String path) {
        String value = path;
        if (value.endsWith(".schema.json")) {
            value = value.substring(0, value.length() - ".schema.json".length());
        }
        return value.replace('/', '.');
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
