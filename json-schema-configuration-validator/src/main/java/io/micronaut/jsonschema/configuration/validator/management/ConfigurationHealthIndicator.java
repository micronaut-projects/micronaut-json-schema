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
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.util.StringUtils;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import io.micronaut.jsonschema.configuration.validator.ConfigurationError;
import io.micronaut.jsonschema.configuration.validator.ConfigurationErrors;
import io.micronaut.jsonschema.configuration.validator.HealthConfiguration;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A health indicator that reports {@link HealthStatus#DOWN} when configuration validation produces
 * any {@link ConfigurationError.Type#ERROR} entries.
 * <p>
 * This bean is only loaded when the optional {@code micronaut-management} dependency is present.
 */
@Requires(classes = HealthIndicator.class)
@Requires(property = HealthConfiguration.PREFIX + ".enabled", value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
@Singleton
final class ConfigurationHealthIndicator implements HealthIndicator {
    private static final String NAME = "configuration";
    private static final int MAX_INCLUDED_ERRORS = 10;

    private final ConfigurationErrors configurationErrors;

    ConfigurationHealthIndicator(ConfigurationErrors configurationErrors) {
        this.configurationErrors = configurationErrors;
    }

    @Override
    public Publisher<HealthResult> getResult() {
        Set<ConfigurationError> errors = configurationErrors.getCurrentErrors();

        List<ConfigurationError> errorList = errors.stream()
            .filter(e -> e.type() == ConfigurationError.Type.ERROR)
            .sorted(Comparator.comparing(ConfigurationError::property))
            .toList();
        long errorCount = errorList.size();
        long warningCount = errors.size() - errorCount;

        HealthResult.Builder builder = HealthResult.builder(NAME);
        Map<String, Object> details = new LinkedHashMap<>(4);
        details.put("errorCount", errorCount);
        details.put("warningCount", warningCount);
        if (errorCount > 0) {
            builder.status(HealthStatus.DOWN);

            List<Map<String, Object>> included = new ArrayList<>(Math.min(MAX_INCLUDED_ERRORS, errorList.size()));
            for (int i = 0; i < errorList.size() && i < MAX_INCLUDED_ERRORS; i++) {
                included.add(errorToDetails(errorList.get(i)));
            }
            details.put("errors", included);
            if (errorList.size() > MAX_INCLUDED_ERRORS) {
                details.put("truncated", true);
            }
        } else {
            builder.status(HealthStatus.UP);
        }
        builder.details(details);
        return Publishers.just(builder.build());
    }

    private static Map<String, Object> errorToDetails(ConfigurationError error) {
        Map<String, Object> details = new LinkedHashMap<>(6);
        details.put("property", error.property());
        details.put("message", error.message());

        if (error.originLocation() != null) {
            details.put("originLocation", error.originLocation());
        }
        if (error.rawPropertyName() != null) {
            details.put("rawPropertyName", error.rawPropertyName());
        }
        if (error.lineNumber() > 0) {
            details.put("lineNumber", error.lineNumber());
        }
        return details;
    }
}
