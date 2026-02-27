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

import java.util.Set;

/**
 * Validates dependency-injection wiring using {@link io.micronaut.inject.BeanDefinition} metadata.
 * <p>
 * Implementations must not require the context to be started and should avoid bean instantiation.
 */
public interface DependencyInjectionValidator {
    /**
     * Validates dependency-injection relationships available in the given bean context.
     *
     * @param beanContext The configurable bean context to inspect
     * @return The set of detected dependency-injection errors. Empty when no issues are found.
     */
    Set<DependencyInjectionError> validate(ConfigurableBeanContext beanContext);
}
