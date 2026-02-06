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
import io.micronaut.jsonschema.configuration.validator.model.JsonSchemaProperty;
import io.micronaut.jsonschema.configuration.validator.model.JsonSchemaType;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Internal
final class SchemaValidator {
    private SchemaValidator() {
    }

    static void validateObject(
        SchemaContext ctx,
        JsonSchemaProperty schema,
        Object instance,
        String computedPropertyName,
        @Nullable String wildcardReplacement,
        Set<ConfigurationError> errors
    ) {
        JsonSchemaProperty resolved = ctx.refResolver().resolveRef(schema);
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

    private static void validateObjectSchema(
        SchemaContext ctx,
        JsonSchemaProperty schema,
        Map<String, Object> instance,
        String computedPropertyName,
        @Nullable String wildcardReplacement,
        Set<ConfigurationError> errors
    ) {
        Map<String, JsonSchemaProperty> properties = schema.properties();
        if (properties == null) {
            properties = Map.of();
        }

        // required
        List<String> required = schema.required();
        if (required != null) {
            for (String req : required) {
                if (!instance.containsKey(req)) {
                    String missingComputed = computedPropertyName + "." + req;
                    String missingResolved = ctx.resolvedPropertyName(missingComputed, null, wildcardReplacement);
                    errors.add(ctx.error(missingResolved, "Missing required property"));
                }
            }
        }

        // validate declared properties
        for (Map.Entry<String, JsonSchemaProperty> entry : properties.entrySet()) {
            String key = entry.getKey();
            JsonSchemaProperty propSchema = entry.getValue();
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
        if (ctx.failOnNotPresent()) {
            for (Map.Entry<String, Object> entry : instance.entrySet()) {
                String key = entry.getKey();
                if (properties.containsKey(key)) {
                    continue;
                }

                Object additionalProperties = schema.additionalProperties();
                if (additionalProperties instanceof Boolean b && b) {
                    continue;
                }

                String unknownComputed = computedPropertyName + "." + key;
                String unknownResolved = ctx.resolvedPropertyName(unknownComputed, null, wildcardReplacement);

                JsonSchemaProperty additionalSchema = ctx.refResolver().resolveAdditionalPropertiesSchema(schema);
                if (additionalSchema != null) {
                    Object coerced = ValueCoercer.coerce(ctx, additionalSchema, unknownResolved, wildcardReplacement, entry.getValue(), errors);
                    validateNode(ctx, additionalSchema, coerced, unknownComputed, unknownResolved, wildcardReplacement, errors);
                } else {
                    errors.add(ctx.error(unknownResolved, "Property not present in schema"));
                }
            }
        }
    }

    private static void validateNode(
        SchemaContext ctx,
        JsonSchemaProperty schema,
        @Nullable Object value,
        String computedPropertyName,
        String resolvedPropertyName,
        @Nullable String wildcardReplacement,
        Set<ConfigurationError> errors
    ) {
        JsonSchemaProperty resolved = ctx.refResolver().resolveRef(schema);
        if (resolved == null) {
            errors.add(ctx.error(resolvedPropertyName, "Unable to resolve schema reference"));
            return;
        }

        JsonSchemaType type = SchemaTypes.toType(resolved.type());
        if (type == null) {
            if (resolved.properties() != null || resolved.additionalProperties() != null) {
                type = JsonSchemaType.OBJECT;
            }
        }

        if (type == JsonSchemaType.OBJECT) {
            if (!(value instanceof Map)) {
                errors.add(ctx.error(resolvedPropertyName, "Expected object"));
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> valueMap = (Map<String, Object>) value;
            validateObjectSchema(ctx, resolved, valueMap, computedPropertyName, wildcardReplacement, errors);
            return;
        }

        if (type == JsonSchemaType.ARRAY) {
            if (!(value instanceof List)) {
                errors.add(ctx.error(resolvedPropertyName, "Expected array"));
                return;
            }
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) value;
            JsonSchemaProperty items = resolved.items();
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
                errors.add(ctx.error(resolvedPropertyName, "Value not in enum"));
                return;
            }
        }

        if (type == JsonSchemaType.STRING) {
            if (!(value instanceof String)) {
                errors.add(ctx.error(resolvedPropertyName, "Expected string"));
            }
            return;
        }

        if (type == JsonSchemaType.BOOLEAN) {
            if (!(value instanceof Boolean)) {
                errors.add(ctx.error(resolvedPropertyName, "Expected boolean"));
            }
            return;
        }

        if (type == JsonSchemaType.INTEGER) {
            BigDecimal number = toBigDecimal(value);
            if (number == null || number.stripTrailingZeros().scale() > 0) {
                errors.add(ctx.error(resolvedPropertyName, "Expected integer"));
                return;
            }
            validateNumericConstraints(ctx, resolved, number, resolvedPropertyName, errors);
            return;
        }

        if (type == JsonSchemaType.NUMBER) {
            BigDecimal number = toBigDecimal(value);
            if (number == null) {
                errors.add(ctx.error(resolvedPropertyName, "Expected number"));
                return;
            }
            validateNumericConstraints(ctx, resolved, number, resolvedPropertyName, errors);
        }
    }

    private static void validateNumericConstraints(
        SchemaContext ctx,
        JsonSchemaProperty schema,
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
                if (enumValue.toString().equals(value.toString())) {
                    return true;
                }
            }
        }
        return false;
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
}
