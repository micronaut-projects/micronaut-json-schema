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
import io.micronaut.context.env.EnvironmentPropertySource;
import io.micronaut.context.env.PropertyEntry;
import io.micronaut.core.annotation.Internal;
import io.micronaut.json.JsonMapper;
import io.micronaut.jsonschema.configuration.validator.model.ConfigurationSchema;
import io.micronaut.jsonschema.configuration.validator.model.ConfigurationSchemaProperty;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Groups the property names Micronaut derives from a single environment variable.
 *
 * <p>An environment variable such as {@code MICRONAUT_SERVER_PORT} is exposed under every
 * candidate property name ({@code micronaut.server.port}, {@code micronaut.server-port},
 * {@code micronaut-server.port}, ...). Only one of them is usually meant. This filter keeps the
 * candidates that match a schema and drops the others. When no candidate of a variable matches
 * any schema, only the fully dotted candidate is kept, so the variable is still reported once as
 * not present in the schema.</p>
 */
@Internal
final class EnvironmentVariableCandidateFilter {
    /**
     * Upper bound on the partial candidates explored for a single variable. When it is exceeded the
     * variable is treated as matching no schema, so it is reported rather than silently ignored.
     */
    private static final int MAX_SEARCH_STEPS = 100_000;
    private static final String ENV_ORIGIN = EnvironmentPropertySource.ORIGIN.location();

    private final Environment environment;
    private final List<SchemaTarget> targets;
    private final Map<String, Boolean> variableMatches = new HashMap<>();
    private final Map<String, Boolean> propertyMatches = new HashMap<>();

    EnvironmentVariableCandidateFilter(
        Environment environment,
        Map<String, List<ConfigurationSchema>> schemasByPrefix,
        ClassLoader classLoader,
        JsonMapper jsonMapper,
        boolean failOnNotPresent
    ) {
        this.environment = environment;
        List<SchemaTarget> schemaTargets = new ArrayList<>();
        for (Map.Entry<String, List<ConfigurationSchema>> entry : schemasByPrefix.entrySet()) {
            for (ConfigurationSchema schema : entry.getValue()) {
                SchemaContext ctx = new SchemaContext(schema, classLoader, environment, jsonMapper, failOnNotPresent);
                schemaTargets.add(new SchemaTarget(entry.getKey() + ".", ctx, ConfigurationSchemaPropertyAdapter.fromRoot(schema)));
            }
        }
        this.targets = schemaTargets;
    }

    /**
     * Remove the redundant environment variable candidates from the given flat properties.
     *
     * @param prefix The prefix the properties were read for
     * @param flat The flat properties, keyed relative to the prefix
     * @return The filtered properties
     */
    Map<String, Object> filter(String prefix, Map<String, Object> flat) {
        Map<String, Object> filtered = null;
        for (String key : flat.keySet()) {
            String property = prefix + "." + key;
            Optional<PropertyEntry> entry = environment.getPropertyEntry(property);
            if (entry.isEmpty()) {
                continue;
            }
            PropertyEntry propertyEntry = entry.get();
            String variable = propertyEntry.raw();
            if (variable == null || !ENV_ORIGIN.equals(propertyEntry.origin().location())) {
                continue;
            }
            if (keep(property, variable)) {
                continue;
            }
            if (filtered == null) {
                filtered = new LinkedHashMap<>(flat);
            }
            filtered.remove(key);
        }
        return filtered != null ? filtered : flat;
    }

    private boolean keep(String property, String variable) {
        if (matchesSchema(property)) {
            return true;
        }
        if (variableMatches.computeIfAbsent(variable, this::anyCandidateMatches)) {
            // another candidate of the same variable is the intended property
            return false;
        }
        return property.equals(variable.toLowerCase(Locale.ENGLISH).replace('_', '.'));
    }

    /**
     * Search the candidates of a variable (each {@code _} becomes {@code .} or {@code -}) for one that
     * matches a schema. Rather than enumerating all 2^n candidates, partial candidates that cannot
     * lead to a schema property are pruned.
     */
    private boolean anyCandidateMatches(String variable) {
        String lower = variable.toLowerCase(Locale.ENGLISH);
        String[] tokens = lower.split("_", -1);
        int[] budget = {MAX_SEARCH_STEPS};
        return anyCandidateMatches(tokens, 1, new StringBuilder(lower.length()).append(tokens[0]), budget);
    }

    private boolean anyCandidateMatches(String[] tokens, int index, StringBuilder candidate, int[] budget) {
        if (index == tokens.length) {
            return matchesSchema(candidate.toString());
        }
        if (--budget[0] < 0 || !couldMatchSchema(candidate.toString())) {
            return false;
        }
        int length = candidate.length();
        for (char separator : new char[] {'.', '-'}) {
            candidate.setLength(length);
            candidate.append(separator).append(tokens[index]);
            if (anyCandidateMatches(tokens, index + 1, candidate, budget)) {
                return true;
            }
        }
        candidate.setLength(length);
        return false;
    }

    /**
     * @param partial A candidate whose last segment may still be extended
     * @return Whether extending the partial candidate may lead to a schema property
     */
    private boolean couldMatchSchema(String partial) {
        for (SchemaTarget target : targets) {
            String prefixWithDot = target.prefixWithDot();
            if (prefixWithDot.startsWith(partial)) {
                return true;
            }
            if (partial.startsWith(prefixWithDot)
                && matchesPartially(target.ctx(), target.root(), partial.substring(prefixWithDot.length()).split("\\.", -1), 0)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesSchema(String property) {
        return propertyMatches.computeIfAbsent(property, p -> {
            for (SchemaTarget target : targets) {
                if (p.startsWith(target.prefixWithDot())
                    && matches(target.ctx(), target.root(), p.substring(target.prefixWithDot().length()).split("\\."), 0)) {
                    return true;
                }
            }
            return false;
        });
    }

    private static boolean matches(SchemaContext ctx, ConfigurationSchemaProperty schema, String[] segments, int index) {
        ConfigurationSchemaProperty resolved = ctx.refResolver().resolveRef(schema);
        if (resolved == null) {
            return false;
        }
        if (index == segments.length) {
            return true;
        }
        ConfigurationSchemaProperty next = step(ctx, resolved, segments[index]);
        return next != null && matches(ctx, next, segments, index + 1);
    }

    private static boolean matchesPartially(SchemaContext ctx, ConfigurationSchemaProperty schema, String[] segments, int index) {
        ConfigurationSchemaProperty resolved = ctx.refResolver().resolveRef(schema);
        if (resolved == null) {
            return false;
        }
        String segment = segments[index];
        if (index < segments.length - 1) {
            ConfigurationSchemaProperty next = step(ctx, resolved, segment);
            return next != null && matchesPartially(ctx, next, segments, index + 1);
        }
        // the last segment may still be extended with '-'
        if (segment.indexOf('[') > -1) {
            return step(ctx, resolved, segment) != null;
        }
        Map<String, ConfigurationSchemaProperty> properties = resolved.properties();
        if (properties != null) {
            if (properties.containsKey("*")) {
                return true;
            }
            for (String name : properties.keySet()) {
                if (name.startsWith(segment)) {
                    return true;
                }
            }
        }
        return ctx.refResolver().resolveAdditionalPropertiesSchema(resolved) != null;
    }

    private static @Nullable ConfigurationSchemaProperty step(SchemaContext ctx, ConfigurationSchemaProperty resolved, String segment) {
        int bracket = segment.indexOf('[');
        String name = bracket > -1 ? segment.substring(0, bracket) : segment;
        if (name.isEmpty()) {
            return null;
        }
        ConfigurationSchemaProperty next = next(ctx, resolved, name);
        if (next == null || bracket == -1) {
            return next;
        }
        ConfigurationSchemaProperty resolvedNext = ctx.refResolver().resolveRef(next);
        return resolvedNext != null ? resolvedNext.items() : null;
    }

    private static @Nullable ConfigurationSchemaProperty next(SchemaContext ctx, ConfigurationSchemaProperty schema, String name) {
        Map<String, ConfigurationSchemaProperty> properties = schema.properties();
        if (properties != null) {
            ConfigurationSchemaProperty property = properties.get(name);
            if (property == null) {
                property = properties.get("*");
            }
            if (property != null) {
                return property;
            }
        }
        return ctx.refResolver().resolveAdditionalPropertiesSchema(schema);
    }

    private record SchemaTarget(String prefixWithDot, SchemaContext ctx, ConfigurationSchemaProperty root) {
    }
}
