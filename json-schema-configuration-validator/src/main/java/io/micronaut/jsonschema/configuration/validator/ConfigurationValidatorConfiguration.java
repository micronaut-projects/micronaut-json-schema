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
 *     <li>{@code micronaut.jsonschema.configuration.validator.health.enabled}</li>
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
    private HealthConfiguration health = new HealthConfiguration();
    private EndpointConfiguration endpoint = new EndpointConfiguration();

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

    /**
     * @return Health indicator configuration
     */
    public HealthConfiguration getHealth() {
        return health;
    }

    /**
     * @param health Health indicator configuration
     */
    public void setHealth(HealthConfiguration health) {
        this.health = health;
    }

    /**
     * @return Endpoint configuration
     */
    public EndpointConfiguration getEndpoint() {
        return endpoint;
    }

    /**
     * @param endpoint Endpoint configuration
     */
    public void setEndpoint(EndpointConfiguration endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * Health indicator configuration.
     */
    @ConfigurationProperties("health")
    public static final class HealthConfiguration {
        public static final boolean DEFAULT_HEALTH = true;
        private boolean enabled = DEFAULT_HEALTH;

        /**
         * @return Whether the configuration validation health indicator is enabled. Default Value {@value #DEFAULT_HEALTH}
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * @param enabled Whether the configuration validation health indicator is enabled
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * Management endpoint configuration.
     */
    @ConfigurationProperties("endpoint")
    public static final class EndpointConfiguration {
        public static final boolean DEFAULT_ENDPOINT = true;
        private boolean enabled = DEFAULT_CACHE;

        /**
         * @return Whether the configuration validation management endpoint is enabled. Default Value {@value #DEFAULT_ENDPOINT}
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * @param enabled Whether the configuration validation management endpoint is enabled
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
