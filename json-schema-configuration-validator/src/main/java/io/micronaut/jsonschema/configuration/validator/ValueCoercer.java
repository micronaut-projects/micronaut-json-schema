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
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.jsonschema.configuration.validator.model.ConfigurationSchemaProperty;
import io.micronaut.jsonschema.configuration.validator.model.ConfigurationSchemaType;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Internal
final class ValueCoercer {
    private ValueCoercer() {
    }

    @SuppressWarnings("java:S3776")
    static Object coerce(
        SchemaContext ctx,
        ConfigurationSchemaProperty schema,
        String resolvedPropertyName,
        @Nullable String wildcardReplacement,
        @Nullable Object value,
        Set<ConfigurationError> errors
    ) {
        if (value == null) {
            return null;
        }

        ConfigurationSchemaType type = SchemaTypes.toType(schema.type());
        if (type == ConfigurationSchemaType.OBJECT) {
            // Keep objects as maps; don't attempt to instantiate configuration classes.
            return value;
        }

        if (value instanceof String s) {
            // Some conversion services may coerce arbitrary strings to boolean/number; only attempt coercion for recognized literals.
            if (type == ConfigurationSchemaType.BOOLEAN && !isBooleanLiteral(s)) {
                return value;
            }
            if ((type == ConfigurationSchemaType.INTEGER || type == ConfigurationSchemaType.NUMBER) && !isNumericLiteral(s)) {
                return value;
            }
        }

        ConversionService conversionService = ctx.environment().getConversionService();

        // 1) Try conversion based on schema type.
        if (type != null) {
            Optional<?> converted = convertBySchemaType(conversionService, type, value);
            if (converted.isPresent()) {
                return converted.get();
            }
        }

        // 2) Try x-micronaut-javaType conversion if it's compatible with schema type.
        String javaType = schema.javaType();
        if (javaType != null && type != ConfigurationSchemaType.OBJECT) {
            Class<?> target = loadJavaType(ctx, javaType);
            if (target != null) {
                Optional<?> converted = conversionService.convert(value, Argument.of(target));
                if (converted.isPresent()) {
                    Object convertedValue = converted.get();
                    if (type == null || isCompatibleWithSchemaType(type, convertedValue)) {
                        return convertedValue;
                    }
                }
            }
        }

        // 3) Fallback manual conversions.
        if (type == ConfigurationSchemaType.ARRAY && value instanceof String s) {
            return splitCommaSeparated(s);
        }
        if (type == ConfigurationSchemaType.BOOLEAN && value instanceof String s) {
            String normalized = s.trim().toLowerCase(Locale.ENGLISH);
            if (StringUtils.TRUE.equals(normalized)) {
                return Boolean.TRUE;
            }
            if (StringUtils.FALSE.equals(normalized)) {
                return Boolean.FALSE;
            }
            return value;
        }
        if ((type == ConfigurationSchemaType.INTEGER || type == ConfigurationSchemaType.NUMBER) && value instanceof String s) {
            try {
                BigDecimal number = new BigDecimal(s.trim());
                if (type == ConfigurationSchemaType.INTEGER) {
                    return number.stripTrailingZeros().scale() <= 0 ? number.toBigInteger() : value;
                }
                return number;
            } catch (NumberFormatException ignored) {
                return value;
            }
        }

        return value;
    }

    private static boolean isCompatibleWithSchemaType(ConfigurationSchemaType type, Object value) {
        return switch (type) {
            case STRING -> value instanceof String;
            case BOOLEAN -> value instanceof Boolean;
            case INTEGER, NUMBER -> value instanceof Number;
            case ARRAY -> value instanceof List<?> || value instanceof Object[] || value instanceof Iterable<?>;
            case OBJECT -> value instanceof java.util.Map<?, ?>;
        };
    }

    private static boolean isBooleanLiteral(String value) {
        String normalized = value.trim().toLowerCase(Locale.ENGLISH);
        return StringUtils.TRUE.equals(normalized)
            || StringUtils.FALSE.equals(normalized);
    }

    private static boolean isNumericLiteral(String value) {
        try {
            new BigDecimal(value.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static Optional<?> convertBySchemaType(ConversionService conversionService, ConfigurationSchemaType type, Object value) {
        return switch (type) {
            case STRING -> conversionService.convert(value, String.class);
            case BOOLEAN -> conversionService.convert(value, Boolean.class);
            case INTEGER -> conversionService.convert(value, Long.class);
            case NUMBER -> conversionService.convert(value, BigDecimal.class);
            case ARRAY -> conversionService.convert(value, Argument.listOf(Object.class));
            case OBJECT -> Optional.of(value);
        };
    }

    @Nullable
    private static Class<?> loadJavaType(SchemaContext ctx, String javaType) {
        return switch (javaType) {
            case "boolean" -> Boolean.TYPE;
            case "byte" -> Byte.TYPE;
            case "short" -> Short.TYPE;
            case "int" -> Integer.TYPE;
            case "long" -> Long.TYPE;
            case "float" -> Float.TYPE;
            case "double" -> Double.TYPE;
            case "char" -> Character.TYPE;
            default -> {
                try {
                    yield Class.forName(javaType, false, ctx.classLoader());
                } catch (ClassNotFoundException e) {
                    yield null;
                }
            }
        };
    }

    private static List<String> splitCommaSeparated(String value) {
        String[] parts = value.split(",");
        List<String> result = new ArrayList<>(parts.length);
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
