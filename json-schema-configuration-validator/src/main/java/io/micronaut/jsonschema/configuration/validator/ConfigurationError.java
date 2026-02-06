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
package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.core.annotation.Introspected;
import org.jspecify.annotations.Nullable;

/**
 * A configuration validation error.
 *
 * @param property The full configuration property name (e.g. {@code micronaut.server.ssl.enabled})
 * @param type The error type
 * @param message The error message
 * @param originLocation Where the property originated from (if available)
 * @param rawPropertyName The raw property name prior to normalization (if available)
 * @param rawValue The raw value (if available)
 * @param lineNumber The 1-based line number in the origin file, or {@code -1} if unknown
 * @param snippet A best-effort snippet showing the invalid definition (if available)
 * @param snippetLanguage The snippet language (for example {@code properties}, {@code yaml}, {@code toml})
 */
@Introspected
public record ConfigurationError(
    String property,
    Type type,
    String message,
    @Nullable String originLocation,
    @Nullable String rawPropertyName,
    @Nullable Object rawValue
    ,
    int lineNumber,
    @Nullable String snippet,
    @Nullable String snippetLanguage
) {

    /**
     * The configuration validation error type.
     */
    public enum Type {
        /**
         * A validation error.
         */
        ERROR,

        /**
         * A validation warning.
         */
        WARNING
    }

    /**
     * Constructs a configuration error with type {@link Type#ERROR}.
     *
     * @param property The full configuration property name
     * @param message The error message
     * @param originLocation Where the property originated from (if available)
     * @param rawPropertyName The raw property name prior to normalization (if available)
     * @param rawValue The raw value (if available)
     */
    public ConfigurationError(
        String property,
        String message,
        @Nullable String originLocation,
        @Nullable String rawPropertyName,
        @Nullable Object rawValue
    ) {
        this(property, Type.ERROR, message, originLocation, rawPropertyName, rawValue, -1, null, null);
    }

    /**
     * Constructs a configuration error with type {@link Type#ERROR} and origin information.
     *
     * @param property The full configuration property name
     * @param message The error message
     * @param originLocation Where the property originated from (if available)
     * @param rawPropertyName The raw property name prior to normalization (if available)
     * @param rawValue The raw value (if available)
     * @param lineNumber The 1-based line number in the origin file, or {@code -1} if unknown
     * @param snippet A best-effort snippet showing the invalid definition (if available)
     * @param snippetLanguage The snippet language
     */
    public ConfigurationError(
        String property,
        String message,
        @Nullable String originLocation,
        @Nullable String rawPropertyName,
        @Nullable Object rawValue,
        int lineNumber,
        @Nullable String snippet,
        @Nullable String snippetLanguage
    ) {
        this(property, Type.ERROR, message, originLocation, rawPropertyName, rawValue, lineNumber, snippet, snippetLanguage);
    }

    /**
     * @param type The new type
     * @return A copy of this error with the given type
     */
    public ConfigurationError withType(Type type) {
        return new ConfigurationError(property, type, message, originLocation, rawPropertyName, rawValue, lineNumber, snippet, snippetLanguage);
    }
}
