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

/**
 * Configuration for enabling/disabling the configuration errors health indicator.
 * <p>
 * Controlled via {@code endpoints.health.configurationerrors.enabled}.
 */
@ConfigurationProperties(HealthConfiguration.PREFIX)
public final class HealthConfiguration {
    /**
     * Configuration prefix.
     */
    public static final String PREFIX = "endpoints.health.configurationerrors";
    public static final boolean DEFAULT_ENABLED = true;

    private boolean enabled = DEFAULT_ENABLED;

    /**
     * @return Whether the configuration validation health indicator is enabled. Default value {@value #DEFAULT_ENABLED}
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
