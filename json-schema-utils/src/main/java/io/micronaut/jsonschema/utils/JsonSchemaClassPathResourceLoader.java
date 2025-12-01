/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.jsonschema.utils;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * API to retrieve a JSON Schema for a class.
 * @since 1.7.0
 */
public interface JsonSchemaClassPathResourceLoader {
    /**
     * This method retrieves the JSON Schema for a class for which a JSON Schema was generated.
     * Micronaut JSON Schema annotation processor generates a JSON Schema at compilation-time for classes annotated with {@link io.micronaut.jsonschema.JsonSchema}.
     * The generated JSON Schema, by default, is saved to META/schemas.
     * @param type the class for which a JSON Schema was generated at compilation-time.
     * @return A JSON Schema
     * @param <T> Type used to generate the JSON Schema
     */
    @Nullable
    <T> Optional<String> jsonSchemaStringForClass(@NonNull Class<T> type);
}
