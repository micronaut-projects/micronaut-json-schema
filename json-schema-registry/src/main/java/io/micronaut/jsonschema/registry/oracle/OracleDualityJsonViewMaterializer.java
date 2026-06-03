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
package io.micronaut.jsonschema.registry.oracle;

import io.micronaut.jsonschema.generator.oracle.OracleDiscoveredSchema;
import io.micronaut.jsonschema.generator.oracle.OracleDiscoveryResult;
import io.micronaut.jsonschema.generator.oracle.OracleDiscoverySkipped;
import io.micronaut.jsonschema.generator.oracle.OracleSourceSpec;
import io.micronaut.jsonschema.registry.JsonSchemaNormalizer;
import io.micronaut.jsonschema.registry.JsonSchemaRegistryDriftMode;
import io.micronaut.jsonschema.registry.JsonSchemaRegistryOutcome;
import io.micronaut.jsonschema.registry.JsonSchemaRegistryOutcomeStatus;
import io.micronaut.jsonschema.registry.JsonSchemaRegistryPolicyMode;
import io.micronaut.jsonschema.serialization.JsonSchemaMapperFactory;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Oracle JSON relational duality view materializer.
 *
 * @since 2.0.0
 */
@Singleton
public final class OracleDualityJsonViewMaterializer implements OracleSchemaMaterializer {
    private static final Logger LOG = LoggerFactory.getLogger(OracleDualityJsonViewMaterializer.class);
    private static final Pattern SIMPLE_IDENTIFIER = Pattern.compile("[A-Z][A-Z0-9_$#]{0,127}");
    private static final String TARGET = "oracle.duality-view";

    private final JsonSchemaNormalizer normalizer;
    private final OracleDualityJsonViewDiscoveryProvider discoveryProvider;
    private final ObjectMapper objectMapper;

    /**
     * @param normalizer Schema normalizer
     */
    public OracleDualityJsonViewMaterializer(JsonSchemaNormalizer normalizer) {
        this.normalizer = normalizer;
        this.discoveryProvider = new OracleDualityJsonViewDiscoveryProvider();
        this.objectMapper = JsonSchemaMapperFactory.createMapper();
    }

    @Override
    public String representationDescription() {
        return "Configured Oracle JSON Relational Duality View schema; explicit view DDL is required for create-missing actions";
    }

    @Override
    public Optional<JsonSchemaRegistryOutcome> projectionCompatibility(OracleMaterializationRequest request) {
        try {
            Object schema = objectMapper.readValue(request.candidate().schemaJson(), Object.class);
            Optional<String> remoteRef = firstRemoteReference(schema);
            if (remoteRef.isPresent()) {
                return Optional.of(JsonSchemaRegistryOutcome.failure(
                    request.candidate().logicalSchema(),
                    TARGET,
                    JsonSchemaRegistryOutcomeStatus.PROJECTION_INCOMPATIBILITY,
                    "Oracle duality view projection does not support remote JSON Schema $ref: " + remoteRef.get()
                ));
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of(JsonSchemaRegistryOutcome.failure(
                request.candidate().logicalSchema(),
                TARGET,
                JsonSchemaRegistryOutcomeStatus.PROJECTION_INCOMPATIBILITY,
                e.getMessage()
            ));
        }
    }

    @Override
    public JsonSchemaRegistryOutcome reconcile(Connection connection, OracleMaterializationRequest request) throws Exception {
        String viewName = normalizeIdentifier(resolveViewName(request));
        OracleDiscoveryResult result = request.recordOperation("introspection", () -> discoverView(connection, request.owner(), viewName));
        result.warnings().forEach(warning -> LOG.warn("Oracle duality view materializer warning: {}", warning));
        if (!result.skipped().isEmpty()) {
            OracleDiscoverySkipped skipped = result.skipped().get(0);
            if (isMissingSchema(skipped)) {
                return reconcileMissingView(connection, request, viewName);
            }
            return driftOutcome(request, "Oracle duality view schema could not be read: " + viewName + "; " + skipped.reason());
        }
        if (result.schemas().isEmpty()) {
            return reconcileMissingView(connection, request, viewName);
        }
        OracleDiscoveredSchema current = result.schemas().get(0);
        if (normalizer.equivalent(request.candidate().schemaJson(), current.schemaJson())) {
            return JsonSchemaRegistryOutcome.ok(
                request.candidate().logicalSchema(),
                TARGET,
                JsonSchemaRegistryOutcomeStatus.EQUIVALENT,
                "Oracle duality view is equivalent: " + viewName
            );
        }
        return driftOutcome(request, "Oracle duality view drift detected: " + viewName);
    }

    private JsonSchemaRegistryOutcome reconcileMissingView(Connection connection,
                                                          OracleMaterializationRequest request,
                                                          String viewName) throws Exception {
        Optional<String> ddl = resolveDdl(request.options());
        if (request.policyMode() == JsonSchemaRegistryPolicyMode.OBSERVE_ONLY || ddl.isEmpty()) {
            return JsonSchemaRegistryOutcome.ok(
                request.candidate().logicalSchema(),
                TARGET,
                JsonSchemaRegistryOutcomeStatus.MISSING_TARGET,
                "Oracle duality view is missing: " + viewName
            );
        }
        if (request.dryRun()) {
            return JsonSchemaRegistryOutcome.ok(
                request.candidate().logicalSchema(),
                TARGET,
                JsonSchemaRegistryOutcomeStatus.CREATED,
                "[DRY-RUN] would create Oracle duality view " + viewName
            );
        }
        request.recordOperation("ddl", () -> {
            executeDdl(connection, ddl.get());
            return null;
        });
        OracleDiscoveryResult created = request.recordOperation("introspection", () -> discoverView(connection, request.owner(), viewName));
        created.warnings().forEach(warning -> LOG.warn("Oracle duality view materializer warning: {}", warning));
        if (!created.skipped().isEmpty()) {
            OracleDiscoverySkipped skipped = created.skipped().get(0);
            if (isMissingSchema(skipped)) {
                return driftOutcome(request, "Created Oracle duality view but schema is missing: " + viewName);
            }
            return driftOutcome(request, "Created Oracle duality view but schema could not be read: " + viewName + "; " + skipped.reason());
        }
        if (created.schemas().isEmpty()) {
            return driftOutcome(request, "Created Oracle duality view but schema is missing: " + viewName);
        }
        if (normalizer.equivalent(request.candidate().schemaJson(), created.schemas().get(0).schemaJson())) {
            return JsonSchemaRegistryOutcome.ok(
                request.candidate().logicalSchema(),
                TARGET,
                JsonSchemaRegistryOutcomeStatus.CREATED,
                "Created and verified Oracle duality view " + viewName
            );
        }
        return driftOutcome(request, "Oracle duality view was created but exposed schema is not equivalent: " + viewName);
    }

    private OracleDiscoveryResult discoverView(Connection connection, String owner, String viewName) throws Exception {
        return discoveryProvider.discover(
            connection,
            new OracleSourceSpec("duality-views", OracleDualityJsonViewDiscoveryProvider.class.getName(), owner, Map.of("include", viewName)),
            true,
            LOG::info
        );
    }

    private static void executeDdl(Connection connection, String ddl) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(ddl)) {
            statement.execute();
        }
    }

    private static boolean isMissingSchema(OracleDiscoverySkipped skipped) {
        return "MISSING_SCHEMA".equals(skipped.reason());
    }

    private static Optional<String> resolveDdl(Map<String, String> options) throws IOException {
        String ddl = options.get("viewDdl");
        if (ddl != null && !ddl.isBlank()) {
            return Optional.of(ddl);
        }
        String resource = options.get("viewDdlResource");
        if (resource == null || resource.isBlank()) {
            return Optional.empty();
        }
        String classpathResource = resource.startsWith("classpath:")
            ? resource.substring("classpath:".length())
            : resource;
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader != null) {
            try (InputStream inputStream = classLoader.getResourceAsStream(classpathResource)) {
                if (inputStream != null) {
                    return Optional.of(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
        if (resource.startsWith("classpath:")) {
            throw new IOException("Oracle duality view DDL resource not found: " + resource);
        }
        Path path = Path.of(resource);
        if (Files.exists(path)) {
            return Optional.of(Files.readString(path));
        }
        throw new IOException("Oracle duality view DDL resource not found: " + resource);
    }

    private static String resolveViewName(OracleMaterializationRequest request) {
        String configured = request.options().get("viewName");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        if (request.artifactName() != null && !request.artifactName().isBlank()) {
            return request.artifactName();
        }
        throw new IllegalArgumentException("Oracle duality view materializer requires viewName or artifactName");
    }

    private static String normalizeIdentifier(String identifier) {
        String value = identifier.toUpperCase(Locale.ENGLISH);
        if (!SIMPLE_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Oracle duality view name must be a simple unquoted identifier: " + identifier);
        }
        return value;
    }

    private static JsonSchemaRegistryOutcome driftOutcome(OracleMaterializationRequest request, String message) {
        boolean failure = request.driftMode() == JsonSchemaRegistryDriftMode.FAIL;
        return failure
            ? JsonSchemaRegistryOutcome.failure(request.candidate().logicalSchema(), TARGET, JsonSchemaRegistryOutcomeStatus.DRIFT, message)
            : JsonSchemaRegistryOutcome.ok(request.candidate().logicalSchema(), TARGET, JsonSchemaRegistryOutcomeStatus.DRIFT, message);
    }

    private static Optional<String> firstRemoteReference(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object ref = map.get("$ref");
            if (ref instanceof String refValue && !refValue.startsWith("#")) {
                return Optional.of(refValue);
            }
            for (Object nested : map.values()) {
                Optional<String> remote = firstRemoteReference(nested);
                if (remote.isPresent()) {
                    return remote;
                }
            }
        } else if (value instanceof List<?> list) {
            for (Object nested : list) {
                Optional<String> remote = firstRemoteReference(nested);
                if (remote.isPresent()) {
                    return remote;
                }
            }
        }
        return Optional.empty();
    }
}
