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

import io.micronaut.jsonschema.registry.types.SubjectResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Base64;

/**
 * A controller for the Confluent Schema Registry Client.
 *
 * @author Elif Kurtay
 * @since 1.5.0
 */
@Singleton
public class SchemaRegistryController {
    private final SchemaRegistryClient client;
    private final SchemaRegistryConfig config;
    private final String basicAuth;

    @Inject
    public SchemaRegistryController(SchemaRegistryClient client, SchemaRegistryConfig config) {
        this.client = client;
        this.config = config;
        basicAuth = basicAuth();
    }

    private String basicAuth() {
        return "Basic " + Base64.getEncoder().encodeToString((config.getUsername() + ":" + config.getPassword()).getBytes());
    }

    /**
     * Get a specific version of the schema registered under this subject.
     *
     * @param subject The subject (topic name, e.g., user-data)
     * @param version  The version number or 'latest' as a string
     * @return The requested schema
     */
    public SubjectResponse getSubjectWithVersion(String subject, String version) {
        return client.getSubjectWithVersion(subject, version);
    }


}
