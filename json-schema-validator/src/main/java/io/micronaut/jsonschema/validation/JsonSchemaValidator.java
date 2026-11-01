/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.jsonschema.validation;

import io.micronaut.context.annotation.DefaultImplementation;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * JSON Schema Validator.
 * It is configurable with {@link JsonSchemaValidatorConfiguration}.
 *
 * <p>The value to validate can be any object that the application's {@link io.micronaut.json.JsonMapper} can serialize, a
 * {@link io.micronaut.json.tree.JsonNode}, or a {@link String}, which is parsed as JSON text. Implementations
 * compile a schema when it is first used and keep it compiled (schemas given as a {@link String} or a {@link Map} are
 * kept in a bounded cache). The returned sets are mutable and keep the order in which the errors were found.</p>
 *
 * <p>Besides {@link IOException}s, implementations throw an {@link IllegalArgumentException} when no schema was
 * generated for a type, when a schema is invalid, or when a reference in a schema cannot be resolved.</p>
 *
 * <p>The built-in implementation is provided by {@code micronaut-json-schema-validator}; the module
 * {@code micronaut-json-schema-validator-networknt} replaces it with one based on the NetworkNT JSON Schema Validator.</p>
 *
 * @author Sergio del Amo
 * @since 1.0.0
 */
@DefaultImplementation(DefaultJsonSchemaValidator.class)
public interface JsonSchemaValidator {

    /**
     * Validate JSON based on a types JSON schema.
     *
     * @param value JSON text to validate
     * @param type The type used to generate the JSON Schema
     * @return A set of validation messages. Empty if valid.
     * @param <T> Type used to generate the JSON Schema
     * @throws IOException If an error occurs validating the JSON against the schema.
     */
    @NonNull
    <T> Set<? extends ValidationMessage> validate(@NonNull String value, @NonNull Class<T> type) throws IOException;

    /**
     * Validate Object based on types JSON schema.
     *
     * @param value Object to validate against a JSON schema
     * @param type The type used to generate the JSON Schema
     * @return A set of validation messages. Empty if valid.
     * @param <T> Type used to generate the JSON Schema
     * @throws IOException If an error occurs validating the JSON against the schema.
     */
    @NonNull
    <T> Set<? extends ValidationMessage> validate(@NonNull Object value, @NonNull Class<T> type) throws IOException;

    /**
     * Validate Object against a JSON schema.
     *
     * @param value Object to validate against a JSON schema
     * @param jsonSchema The JSON Schema expressed as a Map
     * @return A set of validation messages. Empty if valid.
     * @throws IOException If an error occurs validating the JSON against the schema.
     */
    Set<? extends ValidationMessage> validate(@NonNull Object value, @NonNull Map<String, Object> jsonSchema) throws IOException;

    /**
     * Validate Object against a JSON schema.
     *
     * @param value Object to validate against a JSON schema
     * @param jsonSchema The JSON Schema expressed as JSON text
     * @return A set of validation messages. Empty if valid.
     * @throws IOException If an error occurs validating the JSON against the schema.
     */
    Set<? extends ValidationMessage> validate(@NonNull Object value, @NonNull String jsonSchema) throws IOException;
}
