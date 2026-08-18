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

import io.micronaut.jsonschema.serialization.JsonSchemaMapperFactory;
import jakarta.inject.Singleton;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Jackson-backed schema normalizer.
 *
 * @since 2.2.0
 */
@Singleton
public final class DefaultJsonSchemaNormalizer implements JsonSchemaNormalizer {
    private final ObjectMapper objectMapper = JsonSchemaMapperFactory.createMapper();

    @Override
    public String normalize(String jsonSchema) throws IOException {
        // Use a tree so explicit JSON null values are retained during canonicalization.
        JsonNode value = objectMapper.readValue(jsonSchema, JsonNode.class);
        return objectMapper.writeValueAsString(sortObjects(value));
    }

    private JsonNode sortObjects(JsonNode value) {
        if (value instanceof ObjectNode object) {
            List<Map.Entry<String, JsonNode>> fields = new ArrayList<>();
            fields.addAll(object.properties());
            fields.sort(Comparator.comparing(Map.Entry::getKey));
            ObjectNode sorted = objectMapper.createObjectNode();
            for (Map.Entry<String, JsonNode> field : fields) {
                sorted.set(field.getKey(), sortObjects(field.getValue()));
            }
            return sorted;
        }
        if (value instanceof ArrayNode array) {
            ArrayNode sorted = objectMapper.createArrayNode();
            for (JsonNode element : array) {
                sorted.add(sortObjects(element));
            }
            return sorted;
        }
        return value;
    }
}
