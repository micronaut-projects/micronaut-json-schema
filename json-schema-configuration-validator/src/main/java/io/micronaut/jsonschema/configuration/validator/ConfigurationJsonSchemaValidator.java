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
package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.env.Environment;
import io.micronaut.core.io.Readable;
import io.micronaut.core.naming.conventions.StringConvention;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.PropertyCatalog;
import io.micronaut.json.JsonMapper;
import io.micronaut.jsonschema.configuration.validator.model.JsonSchema;
import io.micronaut.jsonschema.configuration.validator.model.JsonSchemaProperty;
import io.micronaut.jsonschema.utils.JsonSchemaClassPathResourceLoader;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates Micronaut configuration ({@link Environment}) against JSON schemas on the classpath.
 */
public final class ConfigurationJsonSchemaValidator {
    private static final Argument<JsonSchema> JSON_SCHEMA_ARGUMENT = Argument.of(JsonSchema.class);

    private final SchemaValidationEngine engine = new SchemaValidationEngine();

    private volatile @Nullable JsonMapper jsonMapper;
    private boolean failOnNotPresent = true;

    /**
     * @return Whether to fail when configuration contains keys not present in schema.
     */
    public boolean isFailOnNotPresent() {
        return failOnNotPresent;
    }

    /**
     * @param failOnNotPresent Whether to fail when configuration contains keys not present in schema.
     */
    public void setFailOnNotPresent(boolean failOnNotPresent) {
        this.failOnNotPresent = failOnNotPresent;
    }

    /**
     * @param jsonMapper A mapper used to deserialize JSON schemas.
     */
    public void setJsonMapper(@Nullable JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * Validate the given environment against schemas resolvable from the given classloader.
     *
     * @param classLoader The classloader used to discover JSON schemas
     * @param environment The Micronaut environment
     * @return A set of validation errors (empty if valid)
     */
    @NonNull
    public Set<ConfigurationError> validate(@NonNull ClassLoader classLoader, @NonNull Environment environment) {
        JsonSchemaClassPathResourceLoader loader = JsonSchemaClassPathResourceLoader.createDefault(classLoader);
        Map<String, Readable> schemaResources = loader.jsonSchemas();

        Map<String, List<JsonSchema>> schemasByPrefix = new LinkedHashMap<>();
        Set<ConfigurationError> errors = new LinkedHashSet<>();
        JsonMapper mapper = jsonMapper();

        for (Map.Entry<String, Readable> entry : schemaResources.entrySet()) {
            String schemaName = entry.getKey();
            Readable readable = entry.getValue();
            if (readable == null || !readable.exists()) {
                continue;
            }
            try {
                JsonSchema schema = readSchema(mapper, readable);
                String prefix = schema.micronaut() != null ? schema.micronaut().prefix() : null;
                if (StringUtils.isEmpty(prefix)) {
                    continue;
                }
                schemasByPrefix.computeIfAbsent(prefix, p -> new ArrayList<>(1)).add(schema);
            } catch (Exception e) {
                errors.add(new ConfigurationError(
                    schemaName,
                    "Failed to parse JSON schema: " + e.getMessage(),
                    null,
                    null,
                    null
                ));
            }
        }

        for (Map.Entry<String, List<JsonSchema>> entry : schemasByPrefix.entrySet()) {
            String prefix = entry.getKey();
            for (JsonSchema schema : entry.getValue()) {
                engine.validateSchema(prefix, schema, classLoader, environment, mapper, failOnNotPresent, errors);
            }
        }

        return errors;
    }

    private JsonMapper jsonMapper() {
        JsonMapper mapper = jsonMapper;
        if (mapper == null) {
            mapper = JsonMapper.createDefault();
            this.jsonMapper = mapper;
        }
        return mapper;
    }

    private static JsonSchema readSchema(JsonMapper jsonMapper, Readable readable) throws IOException {
        try (InputStream inputStream = readable.asInputStream()) {
            String schema = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return jsonMapper.readValue(schema, JSON_SCHEMA_ARGUMENT);
        }
    }

    /**
     * Internal validation implementation.
     */
    static final class SchemaValidationEngine {
        void validateSchema(
            String prefix,
            JsonSchema schema,
            ClassLoader classLoader,
            Environment environment,
            JsonMapper jsonMapper,
            boolean failOnNotPresent,
            Set<ConfigurationError> errors
        ) {
            String kind = schema.micronaut() != null ? schema.micronaut().kind() : null;
            String container = schema.micronaut() != null ? schema.micronaut().container() : null;

            if ("each-property".equals(kind) && "map".equals(container)) {
                validateEachProperty(prefix, schema, classLoader, environment, jsonMapper, failOnNotPresent, errors);
            } else {
                validateConfigurationProperties(prefix, schema, classLoader, environment, jsonMapper, failOnNotPresent, errors);
            }
        }

        private void validateConfigurationProperties(
            String prefix,
            JsonSchema schema,
            ClassLoader classLoader,
            Environment environment,
            JsonMapper jsonMapper,
            boolean failOnNotPresent,
            Set<ConfigurationError> errors
        ) {
            if (!environment.containsProperties(prefix)) {
                return;
            }
            Map<String, Object> flat = environment.getProperties(prefix, StringConvention.HYPHENATED);
            Map<String, Object> instance = NestedPropertyMapBuilder.nest(flat);
            JsonSchemaProperty root = JsonSchemaPropertyAdapter.fromRoot(schema);
            SchemaContext ctx = new SchemaContext(schema, classLoader, environment, jsonMapper, failOnNotPresent);
            SchemaValidator.validateObject(ctx, root, instance, prefix, null, errors);
        }

        private void validateEachProperty(
            String prefix,
            JsonSchema schema,
            ClassLoader classLoader,
            Environment environment,
            JsonMapper jsonMapper,
            boolean failOnNotPresent,
            Set<ConfigurationError> errors
        ) {
            SchemaContext ctx = new SchemaContext(schema, classLoader, environment, jsonMapper, failOnNotPresent);
            JsonSchemaProperty entrySchema = ctx.refResolver().resolveAdditionalPropertiesSchema(JsonSchemaPropertyAdapter.fromRoot(schema));
            if (entrySchema == null) {
                errors.add(new ConfigurationError(prefix, "EachProperty schema missing additionalProperties entry schema", null, null, null));
                return;
            }

            Set<String> entries = new LinkedHashSet<>(environment.getPropertyEntries(prefix, PropertyCatalog.NORMALIZED));
            if (entries.isEmpty() && !environment.containsProperties(prefix)) {
                return;
            }
            Integer minProperties = schema.minProperties();
            if (minProperties != null && entries.size() < minProperties) {
                errors.add(ctx.error(prefix, "Expected at least " + minProperties + " entries but found " + entries.size()));
            }

            for (String entry : entries) {
                String entryPrefix = prefix + "." + entry;
                Map<String, Object> flat = environment.getProperties(entryPrefix, StringConvention.HYPHENATED);
                Map<String, Object> instance = NestedPropertyMapBuilder.nest(flat);

                SchemaValidator.validateObject(ctx, entrySchema, instance, entryPrefix, entry, errors);
            }
        }
    }
}
