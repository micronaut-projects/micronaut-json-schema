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

import org.jspecify.annotations.NonNull;

import java.util.Set;

/**
 * Service Provider Interface (SPI) that allows modules to extend configuration validation with
 * custom logic that goes beyond JSON Schema keywords.
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader}.
 */
public interface ConfigurationRule {

    /**
     * Whether this rule supports the given configuration prefix.
     * <p>
     * This is used to avoid executing rules for unrelated prefixes.
     *
     * @param prefix The prefix being validated
     * @return True if this rule should be executed for the prefix
     */
    boolean supportsPrefix(@NonNull String prefix);

    /**
     * Perform custom validation for a configuration prefix (or a single {@code @EachProperty}
     * entry) and return any additional {@link ConfigurationError}s.
     *
     * @param context The validation context
     * @return A set of additional validation errors/warnings (may be empty)
     */
    @NonNull
    Set<ConfigurationError> validate(@NonNull ConfigurationValidationContext context);
}
