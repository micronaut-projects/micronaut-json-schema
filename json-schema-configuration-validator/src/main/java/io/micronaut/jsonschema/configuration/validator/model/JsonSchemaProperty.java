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
package io.micronaut.jsonschema.configuration.validator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.annotation.Serdeable;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * JSON Schema property model used by the configuration validator.
 * <p>
 * This represents schema nodes under {@code properties}, {@code additionalProperties}, {@code items},
 * and {@code $defs}. It also contains Micronaut-specific extensions used to bind a schema node to a
 * configuration property.
 *
 * @param type The JSON Schema type (string or array)
 * @param description Optional description
 * @param format Optional string format
 * @param deprecated Whether this property is deprecated
 * @param pattern Optional regex pattern for strings
 * @param minLength Minimum length for string values
 * @param maxLength Maximum length for string values
 * @param minItems Minimum number of items for array values
 * @param maxItems Maximum number of items for array values
 * @param uniqueItems Whether array items must be unique
 * @param multipleOf Numeric multiple-of constraint
 * @param constValue Constant value constraint
 * @param javaType Micronaut extension for the Java type ({@code x-micronaut-javaType})
 * @param sourceType Micronaut extension for the source type ({@code x-micronaut-sourceType})
 * @param micronautPath Micronaut extension for the resolved config path ({@code x-micronaut-path})
 * @param properties Object properties mapped from {@code properties}
 * @param required Required property names mapped from {@code required}
 * @param minProperties Minimum number of properties for object values
 * @param maxProperties Maximum number of properties for object values
 * @param enumValues Allowed values mapped from {@code enum}
 * @param minimum Minimum numeric value
 * @param exclusiveMinimum Exclusive minimum numeric value
 * @param maximum Maximum numeric value
 * @param exclusiveMaximum Exclusive maximum numeric value
 * @param items Schema for array items
 * @param additionalProperties Schema for additional properties (boolean or object)
 * @param defs Local schema definitions mapped from {@code $defs}
 * @param ref Reference to another schema mapped from {@code $ref}
 */
@Serdeable
@Internal
public record JsonSchemaProperty(
    /**
     * The JSON Schema type for this node.
     * <p>
     * Modeled as {@link Object} because schemas may use a single string or an array.
     */
    @Nullable Object type,

    /**
     * Optional description for this property.
     */
    @Nullable String description,

    /**
     * Optional string format.
     */
    @Nullable String format,

    /**
     * Whether this property is deprecated.
     */
    @Nullable Boolean deprecated,

    /**
     * Optional regular expression pattern for string validation.
     */
    @Nullable String pattern,

    /**
     * Minimum length for string values.
     */
    @Nullable @JsonProperty("minLength") Integer minLength,

    /**
     * Maximum length for string values.
     */
    @Nullable @JsonProperty("maxLength") Integer maxLength,

    /**
     * Minimum number of items for array values.
     */
    @Nullable @JsonProperty("minItems") Integer minItems,

    /**
     * Maximum number of items for array values.
     */
    @Nullable @JsonProperty("maxItems") Integer maxItems,

    /**
     * Whether array items must be unique.
     */
    @Nullable @JsonProperty("uniqueItems") Boolean uniqueItems,

    /**
     * Numeric multiple-of constraint.
     */
    @Nullable @JsonProperty("multipleOf") BigDecimal multipleOf,

    /**
     * Constant value constraint.
     */
    @Nullable @JsonProperty("const") Object constValue,

    /**
     * Micronaut extension that indicates the Java type for conversion/validation.
     */
    @Nullable @JsonProperty("x-micronaut-javaType") String javaType,

    /**
     * Micronaut extension indicating the source type.
     */
    @Nullable @JsonProperty("x-micronaut-sourceType") String sourceType,

    /**
     * Micronaut extension that stores the resolved configuration path for this schema node.
     */
    @Nullable @JsonProperty("x-micronaut-path") String micronautPath,

    /**
     * Object properties mapped from {@code properties}.
     */
    @Nullable Map<String, JsonSchemaProperty> properties,

    /**
     * Required property names mapped from {@code required}.
     */
    @Nullable List<String> required,

    /**
     * Minimum number of properties for object values.
     */
    @Nullable @JsonProperty("minProperties") Integer minProperties,

    /**
     * Maximum number of properties for object values.
     */
    @Nullable @JsonProperty("maxProperties") Integer maxProperties,

    /**
     * Allowed values mapped from {@code enum}.
     */
    @Nullable @JsonProperty("enum") List<Object> enumValues,

    /**
     * Minimum numeric value.
     */
    @Nullable BigDecimal minimum,

    /**
     * Exclusive minimum numeric value.
     */
    @Nullable BigDecimal exclusiveMinimum,

    /**
     * Maximum numeric value.
     */
    @Nullable BigDecimal maximum,

    /**
     * Exclusive maximum numeric value.
     */
    @Nullable BigDecimal exclusiveMaximum,

    /**
     * Schema for array items.
     */
    @Nullable JsonSchemaProperty items,

    /**
     * Schema for additional properties.
     * <p>
     * JSON Schema supports either a boolean or a schema object here; this is modeled as
     * {@link Object} and normalized in code.
     */
    @Nullable Object additionalProperties,

    /**
     * Local schema definitions mapped from {@code $defs}.
     */
    @Nullable @JsonProperty("$defs") Map<String, JsonSchemaProperty> defs,

    /**
     * Reference to another schema mapped from {@code $ref}.
     */
    @Nullable @JsonProperty("$ref") String ref
) {
}
