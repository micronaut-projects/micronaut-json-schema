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
    @Nullable Object rawValue,
    int lineNumber,
    @Nullable String snippet,
    @Nullable String snippetLanguage
) {

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

    /**
     * Create a builder for a {@link ConfigurationError}.
     * <p>
     * The returned builder defaults {@link Type} to {@link Type#ERROR}.
     *
     * @param property The full configuration property name
     * @param message The error message
     * @return A builder
     */
    public static Builder builder(String property, String message) {
        return new Builder(property, message);
    }

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
     * Builder for {@link ConfigurationError}.
     */
    public static final class Builder {
        private final String property;
        private final String message;

        private Type type = Type.ERROR;
        private @Nullable String originLocation;
        private @Nullable String rawPropertyName;
        private @Nullable Object rawValue;
        private int lineNumber = -1;
        private @Nullable String snippet;
        private @Nullable String snippetLanguage;

        private Builder(String property, String message) {
            this.property = property;
            this.message = message;
        }

        /**
         * Set the error {@link Type}.
         *
         * @param type The type
         * @return This builder
         */
        public Builder type(Type type) {
            this.type = type;
            return this;
        }

        /**
         * Set the origin location for this error.
         *
         * @param originLocation The origin location (for example a file path or classpath resource)
         * @return This builder
         */
        public Builder originLocation(@Nullable String originLocation) {
            this.originLocation = originLocation;
            return this;
        }

        /**
         * Set the raw (non-normalized) property name.
         *
         * @param rawPropertyName The raw property name
         * @return This builder
         */
        public Builder rawPropertyName(@Nullable String rawPropertyName) {
            this.rawPropertyName = rawPropertyName;
            return this;
        }

        /**
         * Set the raw value.
         *
         * @param rawValue The raw value
         * @return This builder
         */
        public Builder rawValue(@Nullable Object rawValue) {
            this.rawValue = rawValue;
            return this;
        }

        /**
         * Set the 1-based line number in the origin file.
         *
         * @param lineNumber The line number, or {@code -1} if unknown
         * @return This builder
         */
        public Builder lineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
            return this;
        }

        /**
         * Set a best-effort snippet showing the invalid definition.
         *
         * @param snippet The snippet
         * @return This builder
         */
        public Builder snippet(@Nullable String snippet) {
            this.snippet = snippet;
            return this;
        }

        /**
         * Set the snippet language.
         *
         * @param snippetLanguage The snippet language (for example {@code properties}, {@code yaml}, {@code toml})
         * @return This builder
         */
        public Builder snippetLanguage(@Nullable String snippetLanguage) {
            this.snippetLanguage = snippetLanguage;
            return this;
        }

        /**
         * @return A new {@link ConfigurationError} instance.
         */
        public ConfigurationError build() {
            return new ConfigurationError(property, type, message, originLocation, rawPropertyName, rawValue, lineNumber, snippet, snippetLanguage);
        }
    }
}
