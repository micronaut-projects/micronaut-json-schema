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
 * API to validate the configuration of the current {@link io.micronaut.context.env.Environment}.
 * @since 2.0
 */
public interface EnvironmentConfigurationValidator {
    /**
     * Validates the current {@link io.micronaut.context.env.Environment} and returns a set of {@link ConfigurationError} if any.
     * @return a set of configuration erros.
     */
    @NonNull Set<@NonNull ConfigurationError> validate();
}
