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
package io.micronaut.jsonschema.registry.auth;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.jsonschema.registry.SchemaRegistryConfig;
import jakarta.inject.Singleton;

/**
 * A client filter that adds basic auth to all requests.
 *
 * @since 1.4.0
 * @author Elif Kurtay
 */
@Requires(beans = SchemaRegistryConfig.class)
@Requires(property = SchemaRegistryConfig.BASIC_AUTH_ENABLED)
@SchemaRegistryAuth
@Singleton
@ClientFilter("/**")
public class SchemaRegistryClientBasicAuthFilter {
    private final SchemaRegistryConfig config;

    public SchemaRegistryClientBasicAuthFilter(SchemaRegistryConfig config) {
        this.config = config;
    }

    /**
     * Do basic auth on all requests.
     *
     * @param request The request
     */
    @RequestFilter
    public void doFilter(MutableHttpRequest<?> request) {
        if (config.isBasicAuthEnabled()) {
            request.basicAuth(config.getUsername(), config.getPassword());
        }
    }
}
