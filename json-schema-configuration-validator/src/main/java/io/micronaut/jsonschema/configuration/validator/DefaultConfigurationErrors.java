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

import io.micronaut.context.env.Environment;
import jakarta.inject.Singleton;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default {@link ConfigurationErrors} implementation.
 * <p>
 * The validator is configured from {@link ConfigurationValidatorConfiguration} and validates the
 * injected {@link Environment}. When caching is enabled, the first validation result is cached and
 * reused for the lifetime of the application context.
 */
@Singleton
final class DefaultConfigurationErrors implements ConfigurationErrors {
    private final Environment environment;
    private final ConfigurationValidator validator;
    private final ConfigurationValidatorConfiguration configuration;
    private final AtomicReference<Set<ConfigurationError>> cached = new AtomicReference<>();

    DefaultConfigurationErrors(
        Environment environment,
        ConfigurationValidatorConfiguration configuration,
        ConfigurationValidator validator) {
        this.environment = environment;
        this.configuration = configuration;
        this.validator = validator;
    }

    @Override
    public Set<ConfigurationError> getCurrentErrors() {
        if (!configuration.isCache()) {
            return validateNow();
        }
        Set<ConfigurationError> existing = cached.get();
        if (existing != null) {
            return existing;
        }

        Set<ConfigurationError> computed = validateNow();
        if (cached.compareAndSet(null, computed)) {
            return computed;
        }
        return Objects.requireNonNull(cached.get());
    }

    /**
     * Perform a validation pass against the current {@link Environment}.
     *
     * @return An immutable snapshot of the current validation errors
     */
    private Set<ConfigurationError> validateNow() {
        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), environment);
        return Set.copyOf(errors);
    }
}
