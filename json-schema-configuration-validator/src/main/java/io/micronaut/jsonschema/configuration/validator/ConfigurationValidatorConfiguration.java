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

import io.micronaut.context.annotation.ConfigurationProperties;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Runtime configuration for configuration validation support.
 * <p>
 * All configuration is under the {@value #PREFIX} prefix.
 * <p>
 * Supported keys:
 * <ul>
 *     <li>{@code micronaut.jsonschema.configuration.validator.cache}</li>
 *     <li>{@code micronaut.jsonschema.configuration.validator.fail-on-not-present}</li>
 *     <li>{@code micronaut.jsonschema.configuration.validator.suppressions}</li>
 *     <li>{@code micronaut.jsonschema.configuration.validator.endpoint.enabled}</li>
 * </ul>
 */
@ConfigurationProperties(ConfigurationValidatorConfiguration.PREFIX)
public final class ConfigurationValidatorConfiguration {
    /**
     * Configuration prefix.
     */
    public static final String PREFIX = "micronaut.jsonschema.configuration.validator";
    public static final boolean DEFAULT_CACHE = true;
    public static final boolean DEFAULT_FAIL_ON_NOT_PRESENT = true;
    private boolean cache = DEFAULT_CACHE;
    private boolean failOnNotPresent = DEFAULT_FAIL_ON_NOT_PRESENT;
    private List<String> suppressions = List.of();

    /**
     * Whether the validation result should be cached.
     * <p>
     * When enabled, the first computed validation result is cached and returned for subsequent
     * invocations.
     *
     * @return Whether the validation result should be cached. Default value {@value #DEFAULT_CACHE}
     */
    public boolean isCache() {
        return cache;
    }

    /**
     * @param cache Whether the validation result should be cached.
     */
    public void setCache(boolean cache) {
        this.cache = cache;
    }

    /**
     * Whether to fail when configuration contains keys not present in schema.
     * <p>
     * Note that some schemas may also enforce this via {@code additionalProperties: false}.
     *
     * @return Whether to fail when configuration contains keys not present in schema. Default value {@value #DEFAULT_FAIL_ON_NOT_PRESENT}
     */
    public boolean isFailOnNotPresent() {
        return failOnNotPresent;
    }

    /**
     * @param failOnNotPresent Whether to fail when configuration contains keys not present in schema
     */
    public void setFailOnNotPresent(boolean failOnNotPresent) {
        this.failOnNotPresent = failOnNotPresent;
    }

    /**
     * Patterns used to suppress validation errors.
     * <p>
     * Matching errors are downgraded to warnings.
     *
     * @return Patterns used to suppress validation errors
     */
    public List<String> getSuppressions() {
        return suppressions;
    }

    /**
     * @param suppressions Patterns used to suppress validation errors
     */
    public void setSuppressions(@Nullable List<String> suppressions) {
        this.suppressions = suppressions != null ? List.copyOf(suppressions) : List.of();
    }
}
