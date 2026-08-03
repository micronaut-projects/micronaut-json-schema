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
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.io.Readable;
import io.micronaut.core.naming.conventions.StringConvention;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.PropertyCatalog;
import io.micronaut.json.JsonMapper;
import io.micronaut.jsonschema.configuration.validator.model.ConfigurationSchema;
import io.micronaut.jsonschema.configuration.validator.model.ConfigurationSchemaProperty;
import io.micronaut.jsonschema.utils.JsonSchemaClassPathResourceLoader;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Validates Micronaut configuration ({@link Environment}) against JSON schemas on the classpath.
 */
public final class ConfigurationJsonSchemaValidator implements ConfigurationValidator {
    private static final Argument<ConfigurationSchema> CONFIGURATION_SCHEMA_ARGUMENT = Argument.of(ConfigurationSchema.class);
    private static final List<SuppressionMatcher> DEFAULT_SUPPRESSION_MATCHERS =
        SuppressionMatcher.compileAll(ConfigurationValidatorConfiguration.DEFAULT_SUPPRESSIONS);

    private final SchemaValidationEngine engine = new SchemaValidationEngine();

    private final AtomicReference<JsonMapper> jsonMapper = new AtomicReference<>();
    private boolean failOnNotPresent = true;
    private final AtomicReference<List<String>> suppressionPatterns = new AtomicReference<>(ConfigurationValidatorConfiguration.DEFAULT_SUPPRESSIONS);
    private final AtomicReference<List<String>> customSuppressionPatterns = new AtomicReference<>(List.of());

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
     * Patterns used to suppress validation errors. User-provided matches are downgraded to warnings;
     * built-in suppressions are silently ignored.
     *
     * @return The suppression patterns
     */
    public List<String> getSuppressionPatterns() {
        return Objects.requireNonNull(suppressionPatterns.get());
    }

    /**
     * Set patterns used to suppress validation errors. User-provided matches are downgraded to
     * warnings; built-in suppressions are silently ignored.
     * Patterns may include {@code *} wildcards (for example {@code micronaut.http.*}).
     *
     * @param suppressionPatterns The suppression patterns
     */
    public void setSuppressionPatterns(@Nullable List<String> suppressionPatterns) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(ConfigurationValidatorConfiguration.DEFAULT_SUPPRESSIONS);
        LinkedHashSet<String> custom = new LinkedHashSet<>();
        if (suppressionPatterns != null) {
            for (String suppressionPattern : suppressionPatterns) {
                if (ConfigurationValidatorConfiguration.DEFAULT_SUPPRESSIONS.contains(suppressionPattern)) {
                    continue;
                }
                merged.add(suppressionPattern);
                custom.add(suppressionPattern);
            }
        }
        this.suppressionPatterns.set(List.copyOf(merged));
        this.customSuppressionPatterns.set(List.copyOf(custom));
    }

    /**
     * Validate the given environment against schemas resolvable from the given classloader.
     *
     * @param classLoader The classloader used to discover JSON schemas
     * @param environment The Micronaut environment
     * @return A set of validation errors (empty if valid)
     */
    @Override
    public Set<ConfigurationError> validate(ClassLoader classLoader, Environment environment) {
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

        Map<String, Set<List<String>>> nestedSchemaPathsByPrefix = nestedSchemaPathsByPrefix(schemasByPrefix.keySet());

        for (Map.Entry<String, List<ConfigurationSchema>> entry : schemasByPrefix.entrySet()) {
            String prefix = entry.getKey();
            Set<String> overlappingPrefixKeys = overlappingPrefixKeys(entry.getValue());
            Set<String> overlappingEachPropertySchemaKeys = overlappingEachPropertySchemaKeys(
                entry.getValue(),
                classLoader,
                environment,
                mapper,
                failOnNotPresent
            );
            for (ConfigurationSchema schema : entry.getValue()) {
                engine.validateSchema(
                    prefix,
                    schema,
                    classLoader,
                    environment,
                    mapper,
                    failOnNotPresent,
                    rules,
                    errors,
                    nestedSchemaPathsByPrefix,
                    overlappingPrefixKeys,
                    overlappingEachPropertySchemaKeys
                );
            }
        }

        return applySuppressions(errors);
    }

    private Set<ConfigurationError> applySuppressions(Set<ConfigurationError> errors) {
        if (errors.isEmpty()) {
            return errors;
        }

        List<String> customPatterns = Objects.requireNonNull(customSuppressionPatterns.get());
        List<SuppressionMatcher> customMatchers = SuppressionMatcher.compileAll(customPatterns);
        if (DEFAULT_SUPPRESSION_MATCHERS.isEmpty() && customMatchers.isEmpty()) {
            return errors;
        }

        Set<ConfigurationError> result = new LinkedHashSet<>(errors.size());
        for (ConfigurationError error : errors) {
            if (error.type() != ConfigurationError.Type.ERROR) {
                result.add(error);
                continue;
            }
            if (matchesAny(DEFAULT_SUPPRESSION_MATCHERS, error.property())) {
                continue;
            }
            if (matchesAny(customMatchers, error.property())) {
                result.add(error.withType(ConfigurationError.Type.WARNING));
                continue;
            }
            result.add(error);
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
        return Objects.requireNonNull(jsonMapper.get());
    }

    private static ConfigurationSchema readSchema(JsonMapper jsonMapper, Readable readable) throws IOException {
        try (InputStream inputStream = readable.asInputStream()) {
            String schema = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            ConfigurationSchema decoded = jsonMapper.readValue(schema, CONFIGURATION_SCHEMA_ARGUMENT);
            if (decoded == null) {
                throw new IOException("Failed to decode configuration schema");
            }
            return decoded;
        }
    }

    private static Map<String, Set<List<String>>> nestedSchemaPathsByPrefix(Set<String> schemaPrefixes) {
        if (schemaPrefixes.isEmpty()) {
            return Map.of();
        }
        Map<String, Set<List<String>>> pathsByPrefix = new LinkedHashMap<>(schemaPrefixes.size());
        NavigableSet<String> prefixes = new TreeSet<>(schemaPrefixes);

        for (String prefix : prefixes) {
            String prefixWithDot = prefix + ".";
            for (String fullPrefix : prefixes.tailSet(prefixWithDot, true)) {
                if (!fullPrefix.startsWith(prefixWithDot)) {
                    break;
                }
                String nestedPath = fullPrefix.substring(prefixWithDot.length());
                if (StringUtils.isEmpty(nestedPath)) {
                    continue;
                }
                pathsByPrefix.computeIfAbsent(prefix, p -> new LinkedHashSet<>())
                    .add(Arrays.asList(nestedPath.split("\\.")));
            }
        }

        return pathsByPrefix;
    }

    private static Set<String> overlappingPrefixKeys(List<ConfigurationSchema> schemas) {
        if (schemas == null || schemas.size() < 2) {
            return Set.of();
        }
        Set<String> keys = new LinkedHashSet<>(8);
        for (ConfigurationSchema schema : schemas) {
            if (schema == null || schema.properties() == null || schema.properties().isEmpty()) {
                continue;
            }
            keys.addAll(schema.properties().keySet());
        }
        return keys.isEmpty() ? Set.of() : keys;
    }

    private static Set<String> overlappingEachPropertySchemaKeys(
        List<ConfigurationSchema> schemas,
        ClassLoader classLoader,
        Environment environment,
        JsonMapper jsonMapper,
        boolean failOnNotPresent
    ) {
        if (schemas == null || schemas.size() < 2) {
            return Set.of();
        }
        Set<String> keys = new LinkedHashSet<>(8);
        for (ConfigurationSchema schema : schemas) {
            if (schema == null || schema.micronaut() == null) {
                continue;
            }
            if (!"each-property".equals(schema.micronaut().kind()) || !"map".equals(schema.micronaut().container())) {
                continue;
            }
            SchemaContext ctx = new SchemaContext(schema, classLoader, environment, jsonMapper, failOnNotPresent);
            ConfigurationSchemaProperty entrySchema = ctx.refResolver().resolveAdditionalPropertiesSchema(ConfigurationSchemaPropertyAdapter.fromRoot(schema));
            if (entrySchema == null || entrySchema.properties() == null || entrySchema.properties().isEmpty()) {
                continue;
            }
            keys.addAll(entrySchema.properties().keySet());
        }
        return keys.isEmpty() ? Set.of() : keys;
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
            Set<ConfigurationError> errors,
            Map<String, Set<List<String>>> nestedSchemaPathsByPrefix,
            Set<String> overlappingPrefixKeys,
            Set<String> overlappingEachPropertySchemaKeys
        ) {
            String kind = schema.micronaut() != null ? schema.micronaut().kind() : null;
            String container = schema.micronaut() != null ? schema.micronaut().container() : null;

            if ("each-property".equals(kind) && "map".equals(container)) {
                validateEachProperty(prefix, schema, classLoader, environment, jsonMapper, failOnNotPresent, rules, errors, nestedSchemaPathsByPrefix, overlappingEachPropertySchemaKeys);
            } else {
                validateConfigurationProperties(prefix, schema, classLoader, environment, jsonMapper, failOnNotPresent, rules, errors, nestedSchemaPathsByPrefix, overlappingPrefixKeys);
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
            Set<ConfigurationError> errors,
            Map<String, Set<List<String>>> nestedSchemaPathsByPrefix,
            Set<String> overlappingPrefixKeys
        ) {
            if (!environment.containsProperties(prefix)) {
                return;
            }
            Map<String, Object> flat;
            try {
                flat = environment.getProperties(prefix, StringConvention.HYPHENATED);
            } catch (ConfigurationException e) {
                String message = e.getMessage();
                if (message == null) {
                    message = "Failed to read configuration properties for prefix '" + prefix + "': " + e.getClass().getName();
                }
                errors.add(ConfigurationError.builder(prefix, message)
                    .type(ConfigurationError.Type.WARNING)
                    .build());
                return;
            }
            Map<String, Object> instance = NestedPropertyMapBuilder.nest(flat);
            ConfigurationSchemaProperty root = ConfigurationSchemaPropertyAdapter.fromRoot(schema);
            SchemaContext ctx = new SchemaContext(schema, classLoader, environment, jsonMapper, failOnNotPresent);

            Map<String, Object> effectiveInstance = removeNestedSchemaKeys(prefix, instance, root, nestedSchemaPathsByPrefix, ctx);
            effectiveInstance = removeOverlappingSchemaKeys(effectiveInstance, root, overlappingPrefixKeys);
            SchemaValidator.validateObject(ctx, root, effectiveInstance, prefix, null, errors);

            applyRules(rules, new ConfigurationValidationContext(environment, prefix, schema, root, instance), errors);
            applyRulesForNestedObjects(rules, environment, prefix, schema, root, instance, errors);
        }

        private void validateEachProperty(
            String prefix,
            ConfigurationSchema schema,
            ClassLoader classLoader,
            Environment environment,
            JsonMapper jsonMapper,
            boolean failOnNotPresent,
            List<ConfigurationRule> rules,
            Set<ConfigurationError> errors,
            Map<String, Set<List<String>>> nestedSchemaPathsByPrefix,
            Set<String> overlappingEachPropertySchemaKeys
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
                Map<String, Object> flat;
                try {
                    flat = environment.getProperties(entryPrefix, StringConvention.HYPHENATED);
                } catch (ConfigurationException e) {
                    String message = e.getMessage() != null
                        ? e.getMessage()
                        : "Configuration error while reading properties for prefix '" + entryPrefix + "'";
                    errors.add(ctx.warning(entryPrefix, message));
                    continue;
                }
                Map<String, Object> instance = NestedPropertyMapBuilder.nest(flat);
                instance = unwrapRepeatedEachPropertyEntry(instance, entry, entrySchema);
                Map<String, Object> effectiveInstance = removeOverlappingEachPropertySchemaKeys(instance, entrySchema, overlappingEachPropertySchemaKeys);

                SchemaValidator.validateObject(ctx, entrySchema, effectiveInstance, entryPrefix, entry, errors);

                applyRules(rules, new ConfigurationValidationContext(environment, entryPrefix, schema, entrySchema, instance), errors);
                applyRulesForNestedObjects(rules, environment, entryPrefix, schema, entrySchema, instance, errors);
            }
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> unwrapRepeatedEachPropertyEntry(
            Map<String, Object> instance,
            String entry,
            ConfigurationSchemaProperty entrySchema
        ) {
            if (instance.size() != 1 || !instance.containsKey(entry)) {
                return instance;
            }
            if (!(instance.get(entry) instanceof Map<?, ?> nested)) {
                return instance;
            }
            Map<String, ConfigurationSchemaProperty> properties = entrySchema.properties();
            if (properties != null && properties.containsKey(entry)) {
                return instance;
            }
            return (Map<String, Object>) nested;
        }

        private static Map<String, Object> removeOverlappingEachPropertySchemaKeys(
            Map<String, Object> instance,
            ConfigurationSchemaProperty entrySchema,
            Set<String> overlappingEachPropertySchemaKeys
        ) {
            if (instance.isEmpty() || overlappingEachPropertySchemaKeys.isEmpty()) {
                return instance;
            }
            Map<String, ConfigurationSchemaProperty> properties = entrySchema.properties();
            Map<String, Object> filtered = null;
            for (String key : overlappingEachPropertySchemaKeys) {
                if (!instance.containsKey(key)) {
                    continue;
                }
                if (properties != null && properties.containsKey(key)) {
                    continue;
                }
                if (filtered == null) {
                    filtered = new LinkedHashMap<>(instance);
                }
                filtered.remove(key);
            }
            return filtered != null ? filtered : instance;
        }

        private static void applyRulesForNestedObjects(
            List<ConfigurationRule> rules,
            Environment environment,
            String parentPrefix,
            ConfigurationSchema schema,
            ConfigurationSchemaProperty parentProperty,
            Map<String, Object> parentInstance,
            Set<ConfigurationError> errors
        ) {
            if (rules.isEmpty()) {
                return;
            }
            Map<String, ConfigurationSchemaProperty> properties = parentProperty.properties();
            if (properties == null || properties.isEmpty() || parentInstance.isEmpty()) {
                return;
            }

            for (Map.Entry<String, Object> entry : parentInstance.entrySet()) {
                Object value = entry.getValue();
                if (!(value instanceof Map)) {
                    continue;
                }
                String key = entry.getKey();
                ConfigurationSchemaProperty property = properties.get(key);
                if (property == null) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> instanceMap = (Map<String, Object>) value;

                String prefix = parentPrefix + "." + key;
                applyRules(rules, new ConfigurationValidationContext(environment, prefix, schema, property, instanceMap), errors);
            }
        }

        private static void applyRules(List<ConfigurationRule> rules, ConfigurationValidationContext context, Set<ConfigurationError> errors) {
            if (rules.isEmpty()) {
                return;
            }
            for (ConfigurationRule rule : rules) {
                if (!rule.supportsPrefix(context.prefix())) {
                    continue;
                }
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

        private static Map<String, Object> removeNestedSchemaKeys(
            String prefix,
            Map<String, Object> instance,
            ConfigurationSchemaProperty root,
            Map<String, Set<List<String>>> nestedSchemaPathsByPrefix,
            SchemaContext ctx
        ) {
            if (instance.isEmpty() || nestedSchemaPathsByPrefix.isEmpty()) {
                return instance;
            }
            Set<List<String>> nestedPaths = nestedSchemaPathsByPrefix.get(prefix);
            if (nestedPaths == null || nestedPaths.isEmpty()) {
                return instance;
            }

            Map<String, Object> filtered = null;
            for (List<String> nestedPath : nestedPaths) {
                if (nestedPath.isEmpty()) {
                    continue;
                }
                if (filtered == null) {
                    filtered = new LinkedHashMap<>(instance);
                }
                removeNestedSchemaPath(filtered, root, nestedPath, 0, ctx);
            }
            return filtered != null ? filtered : instance;
        }

        @SuppressWarnings("unchecked")
        private static void removeNestedSchemaPath(
            Map<String, Object> instance,
            ConfigurationSchemaProperty schema,
            List<String> path,
            int index,
            SchemaContext ctx
        ) {
            String segment = path.get(index);
            if (!instance.containsKey(segment)) {
                return;
            }

            ConfigurationSchemaProperty resolvedSchema = ctx.refResolver().resolveRef(schema);
            Map<String, ConfigurationSchemaProperty> properties = resolvedSchema != null ? resolvedSchema.properties() : null;
            ConfigurationSchemaProperty childSchema = properties != null ? properties.get(segment) : null;
            if (childSchema == null) {
                instance.remove(segment);
                return;
            }

            if (index == path.size() - 1) {
                return;
            }

            Object childValue = instance.get(segment);
            if (childValue instanceof Map<?, ?> childMap) {
                removeNestedSchemaPath((Map<String, Object>) childMap, childSchema, path, index + 1, ctx);
            }
        }

        private static Map<String, Object> removeOverlappingSchemaKeys(
            Map<String, Object> instance,
            ConfigurationSchemaProperty root,
            Set<String> overlappingPrefixKeys
        ) {
            if (instance.isEmpty() || overlappingPrefixKeys.isEmpty()) {
                return instance;
            }
            Map<String, ConfigurationSchemaProperty> properties = root.properties();
            if (properties == null || properties.isEmpty()) {
                return instance;
            }

            Map<String, Object> filtered = null;
            for (String key : overlappingPrefixKeys) {
                if (properties.containsKey(key)) {
                    continue;
                }
                if (!instance.containsKey(key)) {
                    continue;
                }
                if (filtered == null) {
                    filtered = new LinkedHashMap<>(instance);
                }
                filtered.remove(key);
            }
            return filtered != null ? filtered : instance;
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
