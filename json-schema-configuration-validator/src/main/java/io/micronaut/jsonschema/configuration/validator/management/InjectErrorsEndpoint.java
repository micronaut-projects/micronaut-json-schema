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
package io.micronaut.jsonschema.configuration.validator.management;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.jsonschema.configuration.validator.ConfigurationValidatorConfiguration;
import io.micronaut.jsonschema.configuration.validator.DependencyInjectionErrors;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import jakarta.inject.Singleton;

import java.util.Map;

@Requires(classes = Endpoint.class)
@Requires(property = ConfigurationValidatorConfiguration.PREFIX + ".dependency-injection.enabled", value = StringUtils.TRUE)
@Requires(property = ConfigurationValidatorConfiguration.PREFIX + ".injecterrors.endpoint.enabled", value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
@Endpoint(id = "injecterrors")
@Singleton
final class InjectErrorsEndpoint {
    private final DependencyInjectionErrors dependencyInjectionErrors;

    InjectErrorsEndpoint(DependencyInjectionErrors dependencyInjectionErrors) {
        this.dependencyInjectionErrors = dependencyInjectionErrors;
    }

    @Read
    Map<String, Object> getErrors() {
        return Map.of("errors", dependencyInjectionErrors.getCurrentErrors());
    }
}
