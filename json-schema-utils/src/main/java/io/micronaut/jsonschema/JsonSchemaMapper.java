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
package io.micronaut.jsonschema;

import io.micronaut.jsonschema.utils.JsonSchemaClassPathResourceLoader;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.Optional;

/**
 * Runtime API for resolving a JSON Schema for a Java type.
 *
 * @since 2.0.0
 */
public final class JsonSchemaMapper {
    private final JsonSchemaClassPathResourceLoader loader;

    private JsonSchemaMapper(JsonSchemaClassPathResourceLoader loader) {
        this.loader = loader;
    }

    /**
     * Create a mapper using the default classpath schema loaders.
     *
     * @param classLoader The classloader
     * @return The mapper
     */
    @NonNull
    public static JsonSchemaMapper create(@NonNull ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, JsonSchemaClassPathResourceLoader.CLASS_LOADER_REQUIRED);
        return new JsonSchemaMapper(JsonSchemaClassPathResourceLoader.createDefault(classLoader));
    }

    /**
     * Resolve the JSON Schema for the given class.
     *
     * @param type The type
     * @param <T> The type parameter
     * @return The generated schema, if available
     */
    @NonNull
    public <T> Optional<String> generateSchemaFor(@NonNull Class<T> type) {
        Objects.requireNonNull(type, "type");
        return loader.jsonSchemaStringForClass(type);
    }
}
