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
package io.micronaut.jsonschema.generator.oracle;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A configured schema discovery source.
 *
 * @param name Stable source name used in diagnostics and cache layout
 * @param providerClassName Discovery provider class name
 * @param options Provider-specific options
 * @since 2.0.0
 */
public record SourceSpec(
    String name,
    String providerClassName,
    Map<String, String> options
) {

    /**
     * Create an immutable source specification.
     *
     * @param name Stable source name used in diagnostics and cache layout
     * @param providerClassName Discovery provider class name
     * @param options Provider-specific options
     */
    public SourceSpec {
        options = options == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(options));
    }

    /**
     * Resolve a provider option as a string.
     *
     * @param key The option key
     * @return The configured value or {@code null}
     */
    public String option(String key) {
        return options.get(key);
    }
}
