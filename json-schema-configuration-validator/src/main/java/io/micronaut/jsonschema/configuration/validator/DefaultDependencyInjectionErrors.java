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

import io.micronaut.context.ConfigurableBeanContext;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Singleton
@Requires(property = ConfigurationValidatorConfiguration.PREFIX + ".dependency-injection.enabled", value = "true")
final class DefaultDependencyInjectionErrors implements DependencyInjectionErrors {
    private final ConfigurableBeanContext beanContext;
    private final ConfigurationValidatorConfiguration configuration;
    private final DependencyInjectionValidator validator;
    private final AtomicReference<Set<DependencyInjectionError>> cached = new AtomicReference<>();

    DefaultDependencyInjectionErrors(
        ConfigurableBeanContext beanContext,
        ConfigurationValidatorConfiguration configuration,
        DependencyInjectionValidator validator
    ) {
        this.beanContext = beanContext;
        this.configuration = configuration;
        this.validator = validator;
    }

    @Override
    public Set<DependencyInjectionError> getCurrentErrors() {
        if (!configuration.isCache()) {
            return validateNow();
        }
        Set<DependencyInjectionError> existing = cached.get();
        if (existing != null) {
            return existing;
        }
        Set<DependencyInjectionError> computed = validateNow();
        if (cached.compareAndSet(null, computed)) {
            return computed;
        }
        return cached.get();
    }

    private Set<DependencyInjectionError> validateNow() {
        return Set.copyOf(validator.validate(beanContext));
    }
}
