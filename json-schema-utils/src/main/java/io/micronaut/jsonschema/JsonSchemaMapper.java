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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.jsonschema.utils.JsonSchemaClassPathResourceLoader;

import java.util.Objects;
import java.util.Optional;

/**
 * Runtime access to JSON Schema documents for application types.
 *
 * @since 2.0.0
 */
public final class JsonSchemaMapper {

    private JsonSchemaMapper() {
    }

    /**
     * Resolve the JSON Schema for the given type using the default runtime loader.
     *
     * @param type The type
     * @return The schema JSON, when available
     */
    @NonNull
    public static Optional<String> generateSchemaFor(@NonNull Class<?> type) {
        Objects.requireNonNull(type, "type");
        ClassLoader classLoader = type.getClassLoader();
        if (classLoader == null) {
            classLoader = Thread.currentThread().getContextClassLoader();
        }
        if (classLoader == null) {
            classLoader = JsonSchemaMapper.class.getClassLoader();
        }
        return generateSchemaFor(type, JsonSchemaClassPathResourceLoader.createDefault(classLoader));
    }

    /**
     * Resolve the JSON Schema for the given type using the provided runtime loader.
     *
     * @param type The type
     * @param loader The runtime loader
     * @return The schema JSON, when available
     */
    @NonNull
    public static Optional<String> generateSchemaFor(@NonNull Class<?> type,
                                                     @NonNull JsonSchemaClassPathResourceLoader loader) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(loader, "loader");
        return loader.jsonSchemaStringForClass(type);
    }
}
