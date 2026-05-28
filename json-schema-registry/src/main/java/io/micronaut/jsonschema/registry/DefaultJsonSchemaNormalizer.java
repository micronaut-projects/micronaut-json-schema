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

import jakarta.inject.Singleton;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Jackson-backed schema normalizer.
 *
 * @since 2.0.0
 */
@Singleton
public final class DefaultJsonSchemaNormalizer implements JsonSchemaNormalizer {
    private final ObjectMapper objectMapper = JsonMapper.builder()
        .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
        .build();

    @Override
    public String normalize(String jsonSchema) throws IOException {
        Object value = normalizeValue(objectMapper.readValue(jsonSchema, Object.class));
        return objectMapper.writeValueAsString(value);
    }

    private static Object normalizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>(DefaultJsonSchemaNormalizer::compareCodePointOrder);
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sorted.put((String) entry.getKey(), normalizeValue(entry.getValue()));
            }
            return new LinkedHashMap<>(sorted);
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            for (Object entry : list) {
                normalized.add(normalizeValue(entry));
            }
            return normalized;
        }
        return value;
    }

    private static int compareCodePointOrder(String left, String right) {
        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < left.length() && rightIndex < right.length()) {
            int leftCodePoint = left.codePointAt(leftIndex);
            int rightCodePoint = right.codePointAt(rightIndex);
            if (leftCodePoint != rightCodePoint) {
                return Integer.compare(leftCodePoint, rightCodePoint);
            }
            leftIndex += Character.charCount(leftCodePoint);
            rightIndex += Character.charCount(rightCodePoint);
        }
        return Integer.compare(left.length(), right.length());
    }
}
