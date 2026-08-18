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
package io.micronaut.jsonschema.registry.oracle;

import java.util.Locale;

/**
 * Oracle JSON Schema validation mode.
 *
 * @since 2.2.0
 */
public enum OracleJsonSchemaCastMode {
    /** Require input values to already be Oracle JSON scalar values. */
    STRICT,
    /** Allow Oracle to cast compatible input values to extended JSON scalar values. */
    CAST;

    /**
     * Parse a materializer option.
     *
     * @param value Configured value
     * @return The configured mode
     */
    public static OracleJsonSchemaCastMode parse(String value) {
        if (value == null || value.isBlank()) {
            return STRICT;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Oracle JSON Schema cast mode must be 'strict' or 'cast': " + value, e);
        }
    }

    /**
     * @return Configuration value
     */
    public String configurationValue() {
        return name().toLowerCase(Locale.ENGLISH);
    }
}
