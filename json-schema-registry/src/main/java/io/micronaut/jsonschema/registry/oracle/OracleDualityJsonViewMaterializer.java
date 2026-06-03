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

import io.micronaut.jsonschema.generator.oracle.OracleDiscoveryResult;
import io.micronaut.jsonschema.generator.oracle.OracleDiscoveredSchema;
import io.micronaut.jsonschema.generator.oracle.OracleSourceSpec;
import io.micronaut.jsonschema.registry.JsonSchemaNormalizer;
import io.micronaut.jsonschema.registry.JsonSchemaRegistryDriftMode;
import io.micronaut.jsonschema.registry.JsonSchemaRegistryOutcome;
import io.micronaut.jsonschema.registry.JsonSchemaRegistryOutcomeStatus;
import io.micronaut.jsonschema.registry.JsonSchemaRegistryPolicyMode;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Opt-in Oracle JSON relational duality view materializer.
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

    /**
     * @param normalizer Schema normalizer
     */
    public OracleDualityJsonViewMaterializer(JsonSchemaNormalizer normalizer) {
        this.normalizer = normalizer;
        this.discoveryProvider = new OracleDualityJsonViewDiscoveryProvider();
    }

    @Override
    public JsonSchemaRegistryOutcome reconcile(Connection connection, OracleMaterializationRequest request) throws Exception {
        String viewName = normalizeIdentifier(resolveViewName(request));
        OracleDiscoveryResult result = discoverView(connection, request, viewName);
        result.warnings().forEach(warning -> LOG.warn("Oracle duality view materializer warning: {}", warning));
        if (!result.skipped().isEmpty()) {
            return driftOutcome(request, "Oracle duality view schema could not be read: " + viewName + "; " + result.skipped().get(0).reason());
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
        if (request.policyMode() == JsonSchemaRegistryPolicyMode.OBSERVE_ONLY) {
            return JsonSchemaRegistryOutcome.ok(
                request.candidate().logicalSchema(),
                TARGET,
                JsonSchemaRegistryOutcomeStatus.MISSING_TARGET,
                "Oracle duality view is missing: " + viewName
            );
        }
        String ddl = resolveDdl(request);
        if (ddl == null || ddl.isBlank()) {
            return JsonSchemaRegistryOutcome.ok(
                request.candidate().logicalSchema(),
                TARGET,
                JsonSchemaRegistryOutcomeStatus.MISSING_TARGET,
                "Oracle duality view is missing and no explicit create DDL was configured: " + viewName
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
        try (PreparedStatement statement = connection.prepareStatement(ddl)) {
            statement.executeUpdate();
        }
        OracleDiscoveryResult verification = discoverView(connection, request, viewName);
        verification.warnings().forEach(warning -> LOG.warn("Oracle duality view materializer warning: {}", warning));
        if (!verification.skipped().isEmpty()) {
            return driftOutcome(request, "Created Oracle duality view but schema could not be read: " + viewName + "; " + verification.skipped().get(0).reason());
        }
        if (verification.schemas().isEmpty()) {
            return driftOutcome(request, "Created Oracle duality view but schema is missing: " + viewName);
        }
        OracleDiscoveredSchema current = verification.schemas().get(0);
        if (!normalizer.equivalent(request.candidate().schemaJson(), current.schemaJson())) {
            return driftOutcome(request, "Created Oracle duality view but schema drift was detected: " + viewName);
        }
        return JsonSchemaRegistryOutcome.ok(
            request.candidate().logicalSchema(),
            TARGET,
            JsonSchemaRegistryOutcomeStatus.CREATED,
            "Created and verified Oracle duality view " + viewName
        );
    }

    private OracleDiscoveryResult discoverView(Connection connection, OracleMaterializationRequest request, String viewName) throws Exception {
        return discoveryProvider.discover(
            connection,
            new OracleSourceSpec("duality-views", OracleDualityJsonViewDiscoveryProvider.class.getName(), request.owner(), Map.of("include", viewName)),
            true,
            oracleLogger()
        );
    }

    private static JsonSchemaRegistryOutcome driftOutcome(OracleMaterializationRequest request, String message) {
        boolean failure = request.driftMode() == JsonSchemaRegistryDriftMode.FAIL;
        return failure
            ? JsonSchemaRegistryOutcome.failure(request.candidate().logicalSchema(), TARGET, JsonSchemaRegistryOutcomeStatus.DRIFT, message)
            : JsonSchemaRegistryOutcome.ok(request.candidate().logicalSchema(), TARGET, JsonSchemaRegistryOutcomeStatus.DRIFT, message);
    }

    private static String resolveViewName(OracleMaterializationRequest request) {
        if (request.artifactName() != null && !request.artifactName().isBlank()) {
            return request.artifactName();
        }
        String viewName = request.options().get("viewName");
        return Objects.requireNonNull(viewName, "Oracle duality view materializer requires an artifactName or options.viewName");
    }

    private static String resolveDdl(OracleMaterializationRequest request) throws Exception {
        String ddl = request.options().get("viewDdl");
        if (ddl != null && !ddl.isBlank()) {
            return ddl;
        }
        String resource = request.options().get("viewDdlResource");
        if (resource == null || resource.isBlank()) {
            return null;
        }
        if (resource.startsWith("classpath:")) {
            String resourceName = resource.substring("classpath:".length());
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader == null) {
                classLoader = OracleDualityJsonViewMaterializer.class.getClassLoader();
            }
            try (InputStream inputStream = classLoader.getResourceAsStream(resourceName)) {
                if (inputStream == null) {
                    throw new IllegalArgumentException("Unable to locate duality view DDL resource: " + resource);
                }
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return Files.readString(Path.of(resource), StandardCharsets.UTF_8);
    }

    private static String normalizeIdentifier(String identifier) {
        String value = identifier.toUpperCase(Locale.ENGLISH);
        if (!SIMPLE_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Oracle duality view name must be a simple unquoted identifier: " + identifier);
        }
        return value;
    }

    private static io.micronaut.jsonschema.generator.oracle.OracleJsonSchemaLogger oracleLogger() {
        return new io.micronaut.jsonschema.generator.oracle.OracleJsonSchemaLogger() {
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
