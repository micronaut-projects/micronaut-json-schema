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
import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Root JSON Schema model used by the configuration validator.
 * <p>
 * This record is intentionally permissive and maps only the subset of JSON Schema keywords
 * that are needed by the validator and commonly present in Micronaut configuration schemas.
 * <p>
 * Note: Some JSON Schema keywords (for example {@code type} and {@code additionalProperties})
 * may appear in multiple shapes (string vs array, boolean vs object). Those fields are modeled
 * as {@link Object} and normalized by validator helpers.
 *
 * @param schema The schema dialect URI, mapped from {@code $schema}
 * @param id The schema identifier, mapped from {@code $id}
 * @param title Optional human-readable title
 * @param type The JSON Schema type (string or array)
 * @param description Optional schema description
 * @param micronaut Micronaut-specific metadata, mapped from {@code x-micronaut}
 * @param format Optional string format
 * @param pattern Optional regex pattern for strings
 * @param minLength Minimum length for string values
 * @param maxLength Maximum length for string values
 * @param minItems Minimum number of items for array values
 * @param maxItems Maximum number of items for array values
 * @param uniqueItems Whether array items must be unique
 * @param multipleOf Numeric multiple-of constraint
 * @param constValue Constant value constraint
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
public record JsonSchema(
    /**
     * The schema dialect URI (e.g. draft 2020-12), mapped from {@code $schema}.
     */
    @Nullable @JsonProperty("$schema") URI schema,

    /**
     * The schema identifier mapped from {@code $id}.
     */
    @Nullable @JsonProperty("$id") String id,

    /**
     * Optional human-readable title.
     */
    @Nullable String title,

    /**
     * The JSON Schema type for the root.
     * <p>
     * Modeled as {@link Object} because schemas may use a single string or an array.
     */
    @Nullable Object type,

    /**
     * Optional schema description.
     */
    @Nullable String description,

    /**
     * Micronaut-specific metadata mapped from {@code x-micronaut}.
     * <p>
     * In particular, {@code x-micronaut.prefix} determines which configuration prefix the schema
     * validates.
     */
    @Nullable @JsonProperty("x-micronaut") MicronautMetadata micronaut,

    /**
     * Optional string format.
     */
    @Nullable String format,

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
