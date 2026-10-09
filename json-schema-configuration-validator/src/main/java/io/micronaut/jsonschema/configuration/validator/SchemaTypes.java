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
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.format.ReadableBytesTypeConverter;
import io.micronaut.core.type.Argument;
import io.micronaut.jsonschema.configuration.validator.model.ConfigurationSchemaProperty;
import io.micronaut.jsonschema.configuration.validator.model.ConfigurationSchemaType;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Internal
final class SchemaTypes {
    /**
     * The format emitted for properties bound through {@code @ReadableBytes}, such as {@code 10MB}.
     */
    static final String READABLE_BYTES_FORMAT = "readable-bytes";

    private static final ReadableBytesTypeConverter READABLE_BYTES_CONVERTER = new ReadableBytesTypeConverter();

    private SchemaTypes() {
    }

    /**
     * Resolves the type used to validate a property.
     *
     * <p>A {@code readable-bytes} property is declared as {@code ["integer", "string"]} so editors accept values such
     * as {@code 10MB}. Such values are converted to a byte count before validation, so the property is validated as
     * an integer and arbitrary strings are not accepted through the {@code string} member of the type union.</p>
     *
     * @param property The schema property
     * @return The schema type, or {@code null} if it cannot be determined
     */
    @Nullable
    static ConfigurationSchemaType typeOf(ConfigurationSchemaProperty property) {
        Object typeValue = property.type();
        if (isReadableBytes(property) && allowsInteger(typeValue)) {
            return ConfigurationSchemaType.INTEGER;
        }
        return toType(typeValue);
    }

    /**
     * @param property The schema property
     * @return Whether the property uses the {@code readable-bytes} format
     */
    static boolean isReadableBytes(ConfigurationSchemaProperty property) {
        return READABLE_BYTES_FORMAT.equals(property.format());
    }

    /**
     * Converts a readable byte size using the same rules Micronaut applies to {@code @ReadableBytes} properties: a
     * whole number with an optional, case-insensitive {@code KB}, {@code MB} or {@code GB} suffix.
     *
     * @param value The value
     * @return The number of bytes, or {@code null} if the value is not a valid byte size
     */
    @Nullable
    static Number parseReadableBytes(String value) {
        return READABLE_BYTES_CONVERTER.convert(value, Number.class, ConversionContext.of(Argument.LONG)).orElse(null);
    }

    private static boolean allowsInteger(@Nullable Object typeValue) {
        if (typeValue instanceof String s) {
            return ConfigurationSchemaType.of(s) == ConfigurationSchemaType.INTEGER;
        }
        if (typeValue instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof String s && ConfigurationSchemaType.of(s) == ConfigurationSchemaType.INTEGER) {
                    return true;
                }
            }
        }
        return false;
    }

    @Nullable
    static ConfigurationSchemaType toType(@Nullable Object typeValue) {
        if (typeValue == null) {
            return null;
        }
        if (typeValue instanceof String s) {
            return ConfigurationSchemaType.of(s);
        }
        if (typeValue instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof String s) {
            // Micronaut schema model commonly uses a single type.
            return ConfigurationSchemaType.of(s);
        }
        return null;
    }
}
