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
import io.micronaut.jsonschema.configuration.validator.ConfigurationValidatorConfiguration;
import io.micronaut.jsonschema.configuration.validator.DependencyInjectionError;
import io.micronaut.jsonschema.configuration.validator.DependencyInjectionErrors;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Requires(classes = HealthIndicator.class)
@Requires(property = ConfigurationValidatorConfiguration.PREFIX + ".dependency-injection.enabled", value = StringUtils.TRUE)
@Requires(property = ConfigurationValidatorConfiguration.ENDPOINT_PREFIX + ".enabled", value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
@Singleton
final class InjectErrorsHealthIndicator implements HealthIndicator {
    private static final String NAME = "injecterrors";
    private static final int MAX_INCLUDED_ERRORS = 10;
    private final DependencyInjectionErrors dependencyInjectionErrors;

    InjectErrorsHealthIndicator(DependencyInjectionErrors dependencyInjectionErrors) {
        this.dependencyInjectionErrors = dependencyInjectionErrors;
    }

    @Override
    public Publisher<HealthResult> getResult() {
        Set<DependencyInjectionError> errors = dependencyInjectionErrors.getCurrentErrors();
        List<DependencyInjectionError> errorList = errors.stream()
            .sorted(Comparator.comparing(DependencyInjectionError::rootBean).thenComparing(DependencyInjectionError::bean))
            .toList();

        HealthResult.Builder builder = HealthResult.builder(NAME);
        Map<String, Object> details = new LinkedHashMap<>(3);
        details.put("errorCount", errorList.size());

        if (!errorList.isEmpty()) {
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

    private static Map<String, Object> errorToDetails(DependencyInjectionError error) {
        Map<String, Object> details = new LinkedHashMap<>(8);
        details.put("rootBean", error.rootBean());
        details.put("bean", error.bean());
        details.put("message", error.message());
        if (error.injectionPoint() != null) {
            details.put("injectionPoint", error.injectionPoint());
        }
        if (error.disabledReason() != null) {
            details.put("disabledReason", error.disabledReason());
        }
        if (!error.failingPath().isEmpty()) {
            details.put("failingPath", error.failingPath());
        }
        return details;
    }
}
