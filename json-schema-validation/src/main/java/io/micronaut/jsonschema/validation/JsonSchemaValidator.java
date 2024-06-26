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
import io.micronaut.core.annotation.NonNull;

import java.io.IOException;
import java.util.Set;

/**
 * JSON Schema Validator.
 * It is configurable with {@link JsonSchemaValidatorConfiguration}.
 *
 * @author Sergio del Amo
 * @since 1.0.0
 */
@DefaultImplementation(DefaultJsonSchemaValidator.class)
public interface JsonSchemaValidator {

    /**
     * Validate JSON based on a types JSON schema.
     *
     * @param value JSON value to validate
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

}
