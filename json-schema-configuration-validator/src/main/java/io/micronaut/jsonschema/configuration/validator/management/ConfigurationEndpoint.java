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
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import io.micronaut.jsonschema.configuration.validator.ConfigurationErrors;
import io.micronaut.jsonschema.configuration.validator.ConfigurationValidatorConfiguration;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NonNull;

import java.util.Map;

/**
 * Management endpoint that exposes configuration validation errors.
 * <p>
 * This endpoint requires the optional {@code micronaut-management} dependency.
 * The endpoint is registered under id {@code configurationerrors}.
 * <p>
 * The JSON response is of the form:
 *
 * <pre>
 * { "errors": [ ... ] }
 * </pre>
 */
@Requires(classes = Endpoint.class)
@Requires(property = ConfigurationValidatorConfiguration.PREFIX + ".endpoint.enabled", value = "true", defaultValue = "true")
@Endpoint(id = "configurationerrors")
@Singleton
final class ConfigurationEndpoint {
    private final ConfigurationErrors configurationErrors;

    ConfigurationEndpoint(ConfigurationErrors configurationErrors) {
        this.configurationErrors = configurationErrors;
    }

    /**
     * @return A map containing the {@code errors} namespace
     */
    @Read
    @NonNull
    Map<String, Object> getErrors() {
        return Map.of("errors", configurationErrors.getCurrentErrors());
    }
}
