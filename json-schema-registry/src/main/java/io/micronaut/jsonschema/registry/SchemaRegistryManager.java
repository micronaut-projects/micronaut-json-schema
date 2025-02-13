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

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.jsonschema.GeneratedFromSchema;
import io.micronaut.jsonschema.registry.types.Responses;
import io.micronaut.jsonschema.registry.types.SubjectRequestBody;
import jakarta.inject.Inject;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * A manager for the Confluent SchemaJson Registry Client.
 *
 * @author Elif Kurtay
 * @since 1.5.0
 */
@Requires(beans = SchemaRegistryClient.class)
@Requires(beans = SchemaRegistryConfig.class)
@Context
public class SchemaRegistryManager {
    @Inject
    private final SchemaRegistryClient client;

    public SchemaRegistryManager(SchemaRegistryClient client, SchemaRegistryConfig config) {
        this.client = client;
        if (config.isPushToRegistryEnabled()) {
            pushToRegistry();
        }
    }

    private void pushToRegistry() {
        // push all schemas to registry
        Set<String> uniqueFileNames = new HashSet<>();
        BeanIntrospector.SHARED.findIntrospections(GeneratedFromSchema.class).forEach(beanIntrospection -> {
            beanIntrospection.getAnnotation(GeneratedFromSchema.class)
                .stringValue("fileName")
                .ifPresent(uniqueFileNames::add);
        });

        // for each unique filename, get schema from file
        uniqueFileNames.forEach(filename -> {
            try (InputStream inputStream = getClass().getResourceAsStream(filename)) {
                // get local schema file
                assert inputStream != null;
                var schemaString = new String(inputStream.readAllBytes());

                // get schema from registry
                var subjectName = filename.substring(
                    filename.contains("/") ? filename.lastIndexOf('/') : 0,
                    filename.contains(".") ? filename.indexOf('.') : filename.length());
                var responseSchemaString = client.getSchemaWithSubjectAndVersion(subjectName, "latest");
                // compare local vs registry, if different, push to registry
                if (!schemaString.equals(responseSchemaString)) {
                    var response = client.registerNewVersion(
                        subjectName,
                        new SubjectRequestBody(schemaString, Responses.SchemaType.JSON, null, null, null));
                    if (response.id() == -1) {
                        throw new RuntimeException("Error pushing schema to registry");
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Error pushing schema to registry", e);
            }
        });
    }
}
