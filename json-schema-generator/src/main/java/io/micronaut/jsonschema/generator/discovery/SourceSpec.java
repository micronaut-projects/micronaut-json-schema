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
package io.micronaut.jsonschema.generator.discovery;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A configured schema discovery source.
 *
 * @param name Stable source name used in diagnostics and cache layout
 * @param provider Discovery provider id
 * @param options Provider-specific options
 * @since 2.1.0
 */
public record SourceSpec(
    String name,
    String provider,
    Map<String, Object> options
) {

    /**
     * Create an immutable source specification.
     *
     * @param name Stable source name used in diagnostics and cache layout
     * @param provider Discovery provider id
     * @param options Provider-specific options
     */
    public SourceSpec {
        options = options == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(options));
    }

    /**
     * Resolve a provider option in its configured form.
     *
     * @param key The option key
     * @return The configured value or {@code null}
     */
    public Object optionValue(String key) {
        return options.get(key);
    }

    /**
     * Resolve a provider option as a string.
     *
     * @param key The option key
     * @return The configured value or {@code null}
     */
    public String option(String key) {
        return optionToString(options.get(key));
    }

    /**
     * Resolve a provider option as one or more string values.
     *
     * @param key The option key
     * @return The configured values
     */
    public List<String> optionValues(String key) {
        Object value = options.get(key);
        if (value == null) {
            return List.of();
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> values = new ArrayList<>();
            for (Object element : iterable) {
                values.add(optionToString(element));
            }
            return values;
        }
        if (value.getClass().isArray()) {
            List<String> values = new ArrayList<>();
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                values.add(optionToString(Array.get(value, i)));
            }
            return values;
        }
        return List.of(optionToString(value));
    }

    private static String optionToString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
