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
package io.micronaut.jsonschema.registry;

import java.io.IOException;

/**
 * Normalizes JSON Schema text for equivalence checks.
 *
 * @since 2.2.0
 */
public interface JsonSchemaNormalizer {

    /**
     * Normalize JSON Schema text.
     *
     * @param jsonSchema JSON Schema text
     * @return Normalized JSON text
     * @throws IOException If the schema cannot be parsed
     */
    String normalize(String jsonSchema) throws IOException;

    /**
     * Compare two JSON Schema documents after normalization.
     *
     * @param first First JSON Schema text
     * @param second Second JSON Schema text
     * @return Whether the documents are equivalent
     * @throws IOException If either document cannot be parsed
     */
    default boolean equivalent(String first, String second) throws IOException {
        return normalize(first).equals(normalize(second));
    }
}
