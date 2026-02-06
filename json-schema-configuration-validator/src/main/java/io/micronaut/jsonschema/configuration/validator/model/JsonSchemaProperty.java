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

@Serdeable
@Internal
public record JsonSchemaProperty(
    @Nullable Object type,
    @Nullable String description,

    @Nullable String format,
    @Nullable String pattern,
    @Nullable @JsonProperty("minLength") Integer minLength,
    @Nullable @JsonProperty("maxLength") Integer maxLength,
    @Nullable @JsonProperty("minItems") Integer minItems,
    @Nullable @JsonProperty("maxItems") Integer maxItems,
    @Nullable @JsonProperty("uniqueItems") Boolean uniqueItems,
    @Nullable @JsonProperty("multipleOf") BigDecimal multipleOf,
    @Nullable @JsonProperty("const") Object constValue,

    @Nullable @JsonProperty("x-micronaut-javaType") String javaType,
    @Nullable @JsonProperty("x-micronaut-sourceType") String sourceType,
    @Nullable @JsonProperty("x-micronaut-path") String micronautPath,

    @Nullable Map<String, JsonSchemaProperty> properties,
    @Nullable List<String> required,

    @Nullable @JsonProperty("minProperties") Integer minProperties,
    @Nullable @JsonProperty("maxProperties") Integer maxProperties,

    @Nullable @JsonProperty("enum") List<Object> enumValues,
    @Nullable BigDecimal minimum,
    @Nullable BigDecimal exclusiveMinimum,
    @Nullable BigDecimal maximum,
    @Nullable BigDecimal exclusiveMaximum,

    @Nullable JsonSchemaProperty items,
    @Nullable Object additionalProperties,

    @Nullable @JsonProperty("$defs") Map<String, JsonSchemaProperty> defs,
    @Nullable @JsonProperty("$ref") String ref
) {
}
