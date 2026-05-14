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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DefaultJsonSchemaNormalizerTest {

    private final JsonSchemaNormalizer normalizer = new DefaultJsonSchemaNormalizer();

    @Test
    void normalizesObjectKeysRecursively() throws Exception {
        String normalized = normalizer.normalize("""
            {
              "required": ["id", "name"],
              "properties": {
                "name": { "type": "string" },
                "id": { "type": "integer" }
              },
              "type": "object"
            }
            """);

        assertEquals("""
            {"properties":{"id":{"type":"integer"},"name":{"type":"string"}},"required":["id","name"],"type":"object"}\
            """, normalized);
    }

    @Test
    void comparesEquivalentSchemasAfterNormalization() throws Exception {
        assertTrue(normalizer.equivalent(
            "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"}}}",
            "{\"properties\":{\"id\":{\"type\":\"integer\"}},\"type\":\"object\"}"
        ));
    }
}
