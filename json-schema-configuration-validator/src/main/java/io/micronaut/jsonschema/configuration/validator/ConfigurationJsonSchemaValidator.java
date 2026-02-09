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
import io.micronaut.jsonschema.configuration.validator.model.ConfigurationSchema;
import io.micronaut.jsonschema.configuration.validator.model.ConfigurationSchemaProperty;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Validates Micronaut configuration ({@link Environment}) against JSON schemas on the classpath.
 */
public final class ConfigurationJsonSchemaValidator {
    private static final Argument<ConfigurationSchema> CONFIGURATION_SCHEMA_ARGUMENT = Argument.of(ConfigurationSchema.class);

    private final SchemaValidationEngine engine = new SchemaValidationEngine();

    private final AtomicReference<JsonMapper> jsonMapper = new AtomicReference<>();
    private boolean failOnNotPresent = true;
    private final AtomicReference<List<String>> suppressionPatterns = new AtomicReference<>(List.of());

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
        this.jsonMapper.set(jsonMapper);
    }

    /**
     * Patterns used to suppress validation errors. Matching errors are downgraded to warnings.
     *
     * @return The suppression patterns
     */
    @NonNull
    public List<String> getSuppressionPatterns() {
        return suppressionPatterns.get();
    }

    /**
     * Set patterns used to suppress validation errors. Matching errors are downgraded to warnings.
     * Patterns may include {@code *} wildcards (for example {@code micronaut.http.*}).
     *
     * @param suppressionPatterns The suppression patterns
     */
    public void setSuppressionPatterns(@Nullable List<String> suppressionPatterns) {
        this.suppressionPatterns.set(suppressionPatterns != null ? List.copyOf(suppressionPatterns) : List.of());
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

        List<ConfigurationRule> rules = ConfigurationRules.load(classLoader);

        Map<String, List<ConfigurationSchema>> schemasByPrefix = new LinkedHashMap<>();
        Set<ConfigurationError> errors = new LinkedHashSet<>();
        JsonMapper mapper = jsonMapper();

        for (Map.Entry<String, Readable> entry : schemaResources.entrySet()) {
            String schemaName = entry.getKey();
            Readable readable = entry.getValue();
            if (readable == null || !readable.exists()) {
                continue;
            }
            try {
                ConfigurationSchema schema = readSchema(mapper, readable);
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

        for (Map.Entry<String, List<ConfigurationSchema>> entry : schemasByPrefix.entrySet()) {
            String prefix = entry.getKey();
            for (ConfigurationSchema schema : entry.getValue()) {
                engine.validateSchema(prefix, schema, classLoader, environment, mapper, failOnNotPresent, rules, errors);
            }
        }

        return applySuppressions(errors);
    }

    private Set<ConfigurationError> applySuppressions(Set<ConfigurationError> errors) {
        List<String> patterns = suppressionPatterns.get();
        if (patterns.isEmpty() || errors.isEmpty()) {
            return errors;
        }

        List<SuppressionMatcher> matchers = SuppressionMatcher.compileAll(patterns);
        if (matchers.isEmpty()) {
            return errors;
        }

        Set<ConfigurationError> result = new LinkedHashSet<>(errors.size());
        for (ConfigurationError error : errors) {
            if (error.type() == ConfigurationError.Type.ERROR && matchesAny(matchers, error.property())) {
                result.add(error.withType(ConfigurationError.Type.WARNING));
            } else {
                result.add(error);
            }
        }
        return result;
    }

    private static boolean matchesAny(List<SuppressionMatcher> matchers, String property) {
        for (SuppressionMatcher matcher : matchers) {
            if (matcher.matches(property)) {
                return true;
            }
        }
        return false;
    }

    private JsonMapper jsonMapper() {
        JsonMapper mapper = jsonMapper.get();
        if (mapper != null) {
            return mapper;
        }

        JsonMapper created = JsonMapper.createDefault();
        if (jsonMapper.compareAndSet(null, created)) {
            return created;
        }
        return jsonMapper.get();
    }

    private static ConfigurationSchema readSchema(JsonMapper jsonMapper, Readable readable) throws IOException {
        try (InputStream inputStream = readable.asInputStream()) {
            String schema = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return jsonMapper.readValue(schema, CONFIGURATION_SCHEMA_ARGUMENT);
        }
    }

    /**
     * Internal validation implementation.
     */
    private static final class SchemaValidationEngine {
        void validateSchema(
            String prefix,
            ConfigurationSchema schema,
            ClassLoader classLoader,
            Environment environment,
            JsonMapper jsonMapper,
            boolean failOnNotPresent,
            List<ConfigurationRule> rules,
            Set<ConfigurationError> errors
        ) {
            String kind = schema.micronaut() != null ? schema.micronaut().kind() : null;
            String container = schema.micronaut() != null ? schema.micronaut().container() : null;

            if ("each-property".equals(kind) && "map".equals(container)) {
                validateEachProperty(prefix, schema, classLoader, environment, jsonMapper, failOnNotPresent, rules, errors);
            } else {
                validateConfigurationProperties(prefix, schema, classLoader, environment, jsonMapper, failOnNotPresent, rules, errors);
            }
        }

        private void validateConfigurationProperties(
            String prefix,
            ConfigurationSchema schema,
            ClassLoader classLoader,
            Environment environment,
            JsonMapper jsonMapper,
            boolean failOnNotPresent,
            List<ConfigurationRule> rules,
            Set<ConfigurationError> errors
        ) {
            if (!environment.containsProperties(prefix)) {
                return;
            }
            Map<String, Object> flat = environment.getProperties(prefix, StringConvention.HYPHENATED);
            Map<String, Object> instance = NestedPropertyMapBuilder.nest(flat);
            ConfigurationSchemaProperty root = ConfigurationSchemaPropertyAdapter.fromRoot(schema);
            SchemaContext ctx = new SchemaContext(schema, classLoader, environment, jsonMapper, failOnNotPresent);
            SchemaValidator.validateObject(ctx, root, instance, prefix, null, errors);

            applyRules(rules, new ConfigurationValidationContext(environment, prefix, schema, root, instance), errors);
        }

        private void validateEachProperty(
            String prefix,
            ConfigurationSchema schema,
            ClassLoader classLoader,
            Environment environment,
            JsonMapper jsonMapper,
            boolean failOnNotPresent,
            List<ConfigurationRule> rules,
            Set<ConfigurationError> errors
        ) {
            SchemaContext ctx = new SchemaContext(schema, classLoader, environment, jsonMapper, failOnNotPresent);
            ConfigurationSchemaProperty entrySchema = ctx.refResolver().resolveAdditionalPropertiesSchema(ConfigurationSchemaPropertyAdapter.fromRoot(schema));
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

                applyRules(rules, new ConfigurationValidationContext(environment, entryPrefix, schema, entrySchema, instance), errors);
            }
        }

        private static void applyRules(List<ConfigurationRule> rules, ConfigurationValidationContext context, Set<ConfigurationError> errors) {
            if (rules.isEmpty()) {
                return;
            }
            for (ConfigurationRule rule : rules) {
                try {
                    Set<ConfigurationError> additional = rule.validate(context);
                    if (additional != null && !additional.isEmpty()) {
                        errors.addAll(additional);
                    }
                } catch (Exception e) {
                    errors.add(new ConfigurationError(context.prefix(), "ConfigurationRule failed: " + e.getMessage(), null, null, null));
                }
            }
        }
    }

    private interface SuppressionMatcher {
        boolean matches(String property);

        static List<SuppressionMatcher> compileAll(List<String> patterns) {
            List<SuppressionMatcher> matchers = new ArrayList<>(patterns.size());
            for (String pattern : patterns) {
                if (StringUtils.isEmpty(pattern)) {
                    continue;
                }
                matchers.add(compile(pattern));
            }
            return matchers;
        }

        static SuppressionMatcher compile(String pattern) {
            if (pattern.indexOf('*') > -1) {
                Pattern regex = Pattern.compile("^" + toRegex(pattern) + "$");
                return property -> regex.matcher(property).matches();
            }
            return property -> property.equals(pattern)
                || property.startsWith(pattern + '.')
                || property.startsWith(pattern + '[');
        }

        private static String toRegex(String wildcardPattern) {
            StringBuilder regex = new StringBuilder(wildcardPattern.length() * 2);
            for (int i = 0; i < wildcardPattern.length(); i++) {
                char c = wildcardPattern.charAt(i);
                if (c == '*') {
                    regex.append(".*");
                } else {
                    // Escape regex meta characters
                    if ("\\.^$|?+()[]{}".indexOf(c) != -1) {
                        regex.append('\\');
                    }
                    regex.append(c);
                }
            }
            return regex.toString();
        }
    }
}
