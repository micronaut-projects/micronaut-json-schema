/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.jsonschema.registry;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.jsonschema.registry.types.ConfigKeys;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

/**
 * Configuration for the schema registry.
 *
 * @since 1.5.0
 * @author Elif Kurtay
 */
@Serdeable
@ConfigurationProperties(ConfigKeys.CONFIG_NAME)
public class SchemaRegistryConfig {
    @NotBlank
    private String origin;
    @NotBlank
    private String username;
    @NotBlank
    private String password;
    private boolean pushToRegistryEnabled = true;
    private boolean basicAuthEnabled = false;

    public SchemaRegistryConfig() {
    }

    /**
     * @return The URL of the schema registry
     */
    public @NotBlank String getOrigin() {
        return origin;
    }

    /**
     * @param origin The URL of the schema registry
     */
    public void setOrigin(@NotBlank String origin) {
        this.origin = origin;
    }

    /**
     * @return The username to authenticate with
     */
    public String getUsername() {
        return username;
    }

    /**
     * @param username The username to authenticate with
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * @return The password to authenticate with
     */
    public String getPassword() {
        return password;
    }

    /**
     * @param password The password to authenticate with
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * @return Whether to push to the registry
     */
    public boolean isPushToRegistryEnabled() {
        return pushToRegistryEnabled;
    }

    /**
     * @param pushToRegistryEnabled Whether to push to the registry
     */
    public void setPushToRegistryEnabled(boolean pushToRegistryEnabled) {
        this.pushToRegistryEnabled = pushToRegistryEnabled;
    }

    /**
     * @return Whether basic auth is enabled
     */
    public boolean isBasicAuthEnabled() {
        return basicAuthEnabled;
    }

    /**
     * @param basicAuthEnabled Whether basic auth will be enabled
     */
    public void setBasicAuthEnabled(boolean basicAuthEnabled) {
        this.basicAuthEnabled = basicAuthEnabled;
    }
}
