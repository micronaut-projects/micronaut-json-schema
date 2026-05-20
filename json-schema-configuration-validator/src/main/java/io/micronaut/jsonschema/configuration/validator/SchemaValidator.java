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

import io.micronaut.core.annotation.Internal;
import io.micronaut.jsonschema.configuration.validator.model.ConfigurationSchemaProperty;
import io.micronaut.jsonschema.configuration.validator.model.ConfigurationSchemaType;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Internal
final class SchemaValidator {
    private SchemaValidator() {
    }

    static void validateObject(
        SchemaContext ctx,
        ConfigurationSchemaProperty schema,
        Object instance,
        String computedPropertyName,
        @Nullable String wildcardReplacement,
        Set<ConfigurationError> errors
    ) {
        ConfigurationSchemaProperty resolved = ctx.refResolver().resolveRef(schema);
        if (resolved == null) {
            errors.add(ctx.error(computedPropertyName, "Unable to resolve schema reference"));
            return;
        }

        if (!(instance instanceof Map)) {
            errors.add(ctx.error(computedPropertyName, "Expected object"));
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> instanceMap = (Map<String, Object>) instance;

        validateObjectSchema(ctx, resolved, instanceMap, computedPropertyName, wildcardReplacement, errors);
    }

    @SuppressWarnings("java:S3776")
    private static void validateObjectSchema(
        SchemaContext ctx,
        ConfigurationSchemaProperty schema,
        Map<String, Object> instance,
        String computedPropertyName,
        @Nullable String wildcardReplacement,
        Set<ConfigurationError> errors
    ) {
        Map<String, ConfigurationSchemaProperty> properties = schema.properties();
        if (properties == null) {
            properties = Map.of();
        }

        Integer minProperties = schema.minProperties();
        if (minProperties != null && instance.size() < minProperties) {
            errors.add(ctx.error(computedPropertyName, "Expected at least " + minProperties + " properties"));
        }
        Integer maxProperties = schema.maxProperties();
        if (maxProperties != null && instance.size() > maxProperties) {
            errors.add(ctx.error(computedPropertyName, "Expected at most " + maxProperties + " properties"));
        }

        // required
        List<String> required = schema.required();
        if (required != null) {
            for (String req : required) {
                ConfigurationSchemaProperty requiredProperty = properties.get(req);
                if (requiredProperty != null) {
                    ConfigurationSchemaProperty resolvedRequiredProperty = ctx.refResolver().resolveRef(requiredProperty);
                    if ((resolvedRequiredProperty != null && resolvedRequiredProperty.defaultValue() != null) || requiredProperty.defaultValue() != null) {
                        continue;
                    }
                }
                if (!instance.containsKey(req)) {
                    String missingComputed = computedPropertyName + "." + req;
                    String missingResolved = ctx.resolvedPropertyName(missingComputed, null, wildcardReplacement);
                    errors.add(ctx.error(missingResolved, "Missing required property"));
                }
            }
        }

        // validate declared properties
        for (Map.Entry<String, ConfigurationSchemaProperty> entry : properties.entrySet()) {
            String key = entry.getKey();
            ConfigurationSchemaProperty propSchema = entry.getValue();
            if (!instance.containsKey(key)) {
                continue;
            }
            Object rawValue = instance.get(key);

            String nextComputed = computedPropertyName + "." + key;
            String nextResolved = ctx.resolvedPropertyName(nextComputed, propSchema.micronautPath(), wildcardReplacement);
            Object coerced = ValueCoercer.coerce(ctx, propSchema, nextResolved, wildcardReplacement, rawValue, errors);
            validateNode(ctx, propSchema, coerced, nextComputed, nextResolved, wildcardReplacement, errors);
        }

        // unknown keys
        Object additionalProperties = schema.additionalProperties();
        boolean additionalPropertiesTrue = additionalProperties instanceof Boolean b && b;
        boolean additionalPropertiesFalse = additionalProperties instanceof Boolean b && !b;
        ConfigurationSchemaProperty additionalSchema = ctx.refResolver().resolveAdditionalPropertiesSchema(schema);
        if (ctx.failOnNotPresent() || additionalPropertiesFalse || additionalSchema != null) {
            for (Map.Entry<String, Object> entry : instance.entrySet()) {
                String key = entry.getKey();
                if (properties.containsKey(key)) {
                    continue;
                }

                String unknownComputed = computedPropertyName + "." + key;
                String unknownResolved = ctx.resolvedPropertyName(unknownComputed, null, wildcardReplacement);

                if (additionalSchema != null) {
                    Object coerced = ValueCoercer.coerce(ctx, additionalSchema, unknownResolved, wildcardReplacement, entry.getValue(), errors);
                    validateNode(ctx, additionalSchema, coerced, unknownComputed, unknownResolved, wildcardReplacement, errors);
                    continue;
                }

                if (additionalPropertiesTrue && !ctx.failOnNotPresent()) {
                    continue;
                }

                if (ctx.failOnNotPresent() || additionalPropertiesFalse) {
                    List<String> suggestions = PropertySuggester.suggest(key, properties.keySet(), 3, 0.35d);
                    if (!suggestions.isEmpty()) {
                        StringBuilder didYouMean = new StringBuilder(64);
                        for (int i = 0; i < suggestions.size(); i++) {
                            if (i > 0) {
                                didYouMean.append(", ");
                            }
                            String suggestionComputed = computedPropertyName + "." + suggestions.get(i);
                            didYouMean.append(ctx.resolvedPropertyName(suggestionComputed, null, wildcardReplacement));
                        }
                        errors.add(ctx.error(unknownResolved, "Property not present in schema. Did you mean: " + didYouMean + "?"));
                    } else {
                        errors.add(ctx.error(unknownResolved, "Property not present in schema"));
                    }
                }
            }
        }
    }

    @SuppressWarnings("java:S3776")
    private static void validateNode(
        SchemaContext ctx,
        ConfigurationSchemaProperty schema,
        @Nullable Object value,
        String computedPropertyName,
        String resolvedPropertyName,
        @Nullable String wildcardReplacement,
        Set<ConfigurationError> errors
    ) {
        ConfigurationSchemaProperty resolved = ctx.refResolver().resolveRef(schema);
        if (resolved == null) {
            errors.add(ctx.error(resolvedPropertyName, "Unable to resolve schema reference"));
            return;
        }

        if (Boolean.TRUE.equals(resolved.deprecated())) {
            errors.add(ctx.warning(resolvedPropertyName, "Deprecated property"));
        }

        ConfigurationSchemaType type = SchemaTypes.toType(resolved.type());
        if (type == null) {
            if (resolved.properties() != null || resolved.additionalProperties() != null) {
                type = ConfigurationSchemaType.OBJECT;
            }
        }

        if (type == ConfigurationSchemaType.OBJECT) {
            if (!(value instanceof Map)) {
                errors.add(ctx.error(resolvedPropertyName, "Expected object"));
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> valueMap = (Map<String, Object>) value;
            validateObjectSchema(ctx, resolved, valueMap, computedPropertyName, wildcardReplacement, errors);
            return;
        }

        if (type == ConfigurationSchemaType.ARRAY) {
            if (!(value instanceof List)) {
                errors.add(ctx.error(resolvedPropertyName, "Expected array"));
                return;
            }
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) value;

            Integer minItems = resolved.minItems();
            if (minItems != null && list.size() < minItems) {
                errors.add(ctx.error(resolvedPropertyName, "Expected at least " + minItems + " items"));
            }
            Integer maxItems = resolved.maxItems();
            if (maxItems != null && list.size() > maxItems) {
                errors.add(ctx.error(resolvedPropertyName, "Expected at most " + maxItems + " items"));
            }
            if (Boolean.TRUE.equals(resolved.uniqueItems())) {
                if (!isUnique(list)) {
                    errors.add(ctx.error(resolvedPropertyName, "Expected unique items"));
                }
            }

            ConfigurationSchemaProperty items = resolved.items();
            if (items != null) {
                for (int i = 0; i < list.size(); i++) {
                    String elementComputed = computedPropertyName + "[" + i + "]";
                    String elementResolved = resolvedPropertyName + "[" + i + "]";
                    Object elementValue = list.get(i);
                    Object coerced = ValueCoercer.coerce(ctx, items, elementResolved, wildcardReplacement, elementValue, errors);
                    validateNode(ctx, items, coerced, elementComputed, elementResolved, wildcardReplacement, errors);
                }
            }
            return;
        }

        // enums
        if (resolved.enumValues() != null && value != null) {
            if (!enumContains(resolved.enumValues(), value)) {
                errors.add(ctx.error(resolvedPropertyName, "Value not in enum. Expected one of: " + formatEnumValues(resolved.enumValues(), 10)));
                return;
            }
        }

        Object constValue = resolved.constValue();
        if (constValue != null) {
            if (value == null || !(Objects.equals(constValue, value) || constValue.toString().equals(value.toString()))) {
                errors.add(ctx.error(resolvedPropertyName, "Value must be equal to const"));
                return;
            }
        }

        if (type == ConfigurationSchemaType.STRING) {
            if (!(value instanceof String)) {
                errors.add(ctx.error(resolvedPropertyName, "Expected string"));
                return;
            }
            String s = (String) value;

            Integer minLength = resolved.minLength();
            if (minLength != null && s.length() < minLength) {
                errors.add(ctx.error(resolvedPropertyName, "Length must be >= " + minLength));
            }
            Integer maxLength = resolved.maxLength();
            if (maxLength != null && s.length() > maxLength) {
                errors.add(ctx.error(resolvedPropertyName, "Length must be <= " + maxLength));
            }
            String pattern = resolved.pattern();
            if (pattern != null) {
                try {
                    if (!Pattern.compile(pattern).matcher(s).matches()) {
                        errors.add(ctx.error(resolvedPropertyName, "Value does not match pattern"));
                    }
                } catch (Exception e) {
                    // Ignore invalid patterns
                }
            }

            String format = resolved.format();
            if (format != null) {
                switch (format) {
                    case "duration" -> {
                        if (ctx.environment().getConversionService().convert(s, Duration.class).isEmpty()) {
                            errors.add(ctx.error(resolvedPropertyName, "Invalid duration"));
                        }
                    }
                    case "uri" -> {
                        if (ctx.environment().getConversionService().convert(s, URI.class).isEmpty()) {
                            errors.add(ctx.error(resolvedPropertyName, "Invalid URI"));
                        }
                    }
                    default -> {
                        // ignore unknown formats
                    }
                }
            }

            String javaType = resolved.javaType();
            if (javaType != null) {
                switch (javaType) {
                    case "java.util.regex.Pattern" -> {
                        try {
                            Pattern.compile(s);
                        } catch (Exception e) {
                            errors.add(ctx.error(resolvedPropertyName, "Invalid regex pattern"));
                        }
                    }
                    case "java.net.URL" -> {
                        if (ctx.environment().getConversionService().convert(s, URL.class).isEmpty()) {
                            errors.add(ctx.error(resolvedPropertyName, "Invalid URL"));
                        }
                    }
                    case "java.net.URI" -> {
                        if (ctx.environment().getConversionService().convert(s, URI.class).isEmpty()) {
                            errors.add(ctx.error(resolvedPropertyName, "Invalid URI"));
                        }
                    }
                    default -> {
                        // ignore other java types
                    }
                }
            }
            return;
        }

        if (type == ConfigurationSchemaType.BOOLEAN) {
            if (!(value instanceof Boolean)) {
                errors.add(ctx.error(resolvedPropertyName, "Expected boolean"));
            }
            return;
        }

        if (type == ConfigurationSchemaType.INTEGER) {
            BigDecimal number = toBigDecimal(value);
            if (number == null || number.stripTrailingZeros().scale() > 0) {
                errors.add(ctx.error(resolvedPropertyName, "Expected integer"));
                return;
            }
            BigDecimal multipleOf = resolved.multipleOf();
            if (multipleOf != null && !isMultipleOf(number, multipleOf)) {
                errors.add(ctx.error(resolvedPropertyName, "Value must be a multiple of " + multipleOf));
            }
            validateNumericConstraints(ctx, resolved, number, resolvedPropertyName, errors);
            return;
        }

        if (type == ConfigurationSchemaType.NUMBER) {
            BigDecimal number = toBigDecimal(value);
            if (number == null) {
                errors.add(ctx.error(resolvedPropertyName, "Expected number"));
                return;
            }
            BigDecimal multipleOf = resolved.multipleOf();
            if (multipleOf != null && !isMultipleOf(number, multipleOf)) {
                errors.add(ctx.error(resolvedPropertyName, "Value must be a multiple of " + multipleOf));
            }
            validateNumericConstraints(ctx, resolved, number, resolvedPropertyName, errors);
        }
    }

    private static boolean isMultipleOf(BigDecimal value, BigDecimal multipleOf) {
        try {
            return value.remainder(multipleOf).compareTo(BigDecimal.ZERO) == 0;
        } catch (ArithmeticException e) {
            return false;
        }
    }

    private static boolean isUnique(List<Object> list) {
        // Use string form to avoid deep-comparison for nested structures.
        Set<String> seen = new HashSet<>(list.size());
        for (Object o : list) {
            String s = String.valueOf(o);
            if (!seen.add(s)) {
                return false;
            }
        }
        return true;
    }

    private static void validateNumericConstraints(
        SchemaContext ctx,
        ConfigurationSchemaProperty schema,
        BigDecimal value,
        String property,
        Set<ConfigurationError> errors
    ) {
        BigDecimal min = schema.minimum();
        if (min != null && value.compareTo(min) < 0) {
            errors.add(ctx.error(property, "Value must be >= " + min));
        }
        BigDecimal exMin = schema.exclusiveMinimum();
        if (exMin != null && value.compareTo(exMin) <= 0) {
            errors.add(ctx.error(property, "Value must be > " + exMin));
        }
        BigDecimal max = schema.maximum();
        if (max != null && value.compareTo(max) > 0) {
            errors.add(ctx.error(property, "Value must be <= " + max));
        }
        BigDecimal exMax = schema.exclusiveMaximum();
        if (exMax != null && value.compareTo(exMax) >= 0) {
            errors.add(ctx.error(property, "Value must be < " + exMax));
        }
    }

    private static boolean enumContains(Collection<Object> enumValues, Object value) {
        // schema values are typically strings/numbers/booleans.
        for (Object enumValue : enumValues) {
            if (Objects.equals(enumValue, value)) {
                return true;
            }
            if (enumValue != null && value != null) {
                if (enumValue.toString().equalsIgnoreCase(value.toString())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String formatEnumValues(Collection<Object> values, int limit) {
        List<String> formatted = new ArrayList<>(Math.min(values.size(), limit));
        int i = 0;
        for (Object v : values) {
            if (i++ >= limit) {
                break;
            }
            if (v == null) {
                formatted.add("null");
            } else if (v instanceof String s) {
                formatted.add('\'' + s + '\'');
            } else {
                formatted.add(String.valueOf(v));
            }
        }
        String joined = String.join(", ", formatted);
        if (values.size() > limit) {
            joined += " … (" + values.size() + " total)";
        }
        return joined;
    }

    @Nullable
    private static BigDecimal toBigDecimal(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof BigInteger bigInteger) {
            return new BigDecimal(bigInteger);
        }
        if (value instanceof Number number) {
            if (value instanceof Double || value instanceof Float) {
                return BigDecimal.valueOf(number.doubleValue());
            }
            return BigDecimal.valueOf(number.longValue());
        }
        if (value instanceof String s) {
            try {
                return new BigDecimal(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static final class PropertySuggester {
        private PropertySuggester() {
        }

        static List<String> suggest(String unknownKey, Collection<String> candidates, int maxSuggestions, double minScore) {
            if (unknownKey == null || unknownKey.isBlank() || candidates.isEmpty() || maxSuggestions <= 0) {
                return List.of();
            }

            String unknown = normalize(unknownKey);
            Map<String, Integer> unknownVec = trigramVector(unknown);
            double unknownNorm = vectorNorm(unknownVec);
            if (unknownNorm == 0.0d) {
                return List.of();
            }

            List<ScoredCandidate> scored = new ArrayList<>(candidates.size());
            for (String candidate : candidates) {
                if (candidate == null || candidate.isBlank()) {
                    continue;
                }
                String normalized = normalize(candidate);
                Map<String, Integer> vec = trigramVector(normalized);
                double norm = vectorNorm(vec);
                if (norm == 0.0d) {
                    continue;
                }
                double score = cosineSimilarity(unknownVec, unknownNorm, vec, norm);
                if (score >= minScore) {
                    scored.add(new ScoredCandidate(candidate, score));
                }
            }

            if (scored.isEmpty()) {
                return List.of();
            }

            scored.sort(Comparator
                .comparingDouble(ScoredCandidate::score).reversed()
                .thenComparing(ScoredCandidate::candidate));

            List<String> result = new ArrayList<>(Math.min(maxSuggestions, scored.size()));
            for (int i = 0; i < scored.size() && result.size() < maxSuggestions; i++) {
                result.add(scored.get(i).candidate());
            }
            return result;
        }

        private static String normalize(String s) {
            return s.toLowerCase(Locale.ENGLISH);
        }

        private static Map<String, Integer> trigramVector(String s) {
            String padded = "  " + s + "  ";
            Map<String, Integer> counts = new HashMap<>(padded.length());
            for (int i = 0; i < padded.length() - 2; i++) {
                String gram = padded.substring(i, i + 3);
                counts.merge(gram, 1, Integer::sum);
            }
            return counts;
        }

        private static double vectorNorm(Map<String, Integer> vec) {
            long sumSq = 0;
            for (int v : vec.values()) {
                sumSq += (long) v * (long) v;
            }
            return Math.sqrt(sumSq);
        }

        private static double cosineSimilarity(
            Map<String, Integer> a,
            double aNorm,
            Map<String, Integer> b,
            double bNorm
        ) {
            if (a.isEmpty() || b.isEmpty()) {
                return 0.0d;
            }
            Map<String, Integer> smaller = a.size() <= b.size() ? a : b;
            Map<String, Integer> larger = smaller == a ? b : a;

            long dot = 0;
            for (Map.Entry<String, Integer> e : smaller.entrySet()) {
                Integer bv = larger.get(e.getKey());
                if (bv != null) {
                    dot += (long) e.getValue() * (long) bv;
                }
            }

            return dot / (aNorm * bNorm);
        }

        private record ScoredCandidate(String candidate, double score) {
        }
    }
}
