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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.jsonschema.GeneratedFromSchema;
import io.micronaut.jsonschema.registry.model.SchemaType;
import io.micronaut.jsonschema.registry.model.SubjectRequestBody;
import jakarta.inject.Inject;

import java.io.InputStream;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A manager for the Confluent SchemaJson Registry Client.
 *
 * This class is responsible for keeping the registry updated.
 * When the application starts,
 *  - it will check for generated files from a local schema,
 *  - check if the local schema is different from the one in the registry
 *      (schema filename and registry subject name needs to be the same),
 *  - if yes, will push the new local schemas to the registry.
 *
 * @author Elif Kurtay
 * @since 1.5.0
 */
@Requires(beans = SchemaRegistryClient.class)
@Requires(beans = SchemaRegistryConfig.class)
@Requires(beans = ResourceLoader.class)
@Context
public class SchemaRegistryManager {
    SchemaRegistryClient client;

    @Inject
    public SchemaRegistryManager(SchemaRegistryClient client, SchemaRegistryConfig config) {
        this.client = client;
        if (config.isPushToRegistryEnabled()) {
            pushToRegistry();
        }
    }

    private void pushToRegistry() {
        // push all schemas to registry
        Set<String> uniqueFileNames = BeanIntrospector.SHARED.findIntrospections(GeneratedFromSchema.class)
            .stream()
            .map(i -> i.getAnnotation(GeneratedFromSchema.class).stringValue("fileName").orElse(null))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        // for each unique filename, get schema from file
        for (String filename: uniqueFileNames) {
            String schemaString = readSchemaFromFile(filename);
            if (schemaString == null) {
                continue;
            }

            var subjectName = getSubjectName(filename);
            String responseSchemaString = client.getSchemaWithSubjectAndVersion(subjectName, "latest");
            // compare local vs registry, if different, push to registry
            if (!schemaString.equals(responseSchemaString)) {
                var response = client.registerNewVersion(
                    subjectName,
                    new SubjectRequestBody(schemaString, SchemaType.JSON, null, null, null));
                if (response.id() == -1) {
                    System.err.println("Error pushing schema to registry: " + filename);
                }
            }
        }
    }

    private @NonNull String getSubjectName(@NonNull String filename) {
        return filename.substring(
            filename.contains("/") ? filename.lastIndexOf('/') : 0,
            filename.contains(".") ? filename.indexOf('.') : filename.length());
    }

    private @Nullable String readSchemaFromFile(@NonNull String filename) {
        try (InputStream inputStream = getClass().getResourceAsStream("/" + filename)) {
            // get local schema file
            if (inputStream == null) {
                System.err.println("Resource not found: " + filename);
                return null;
            }
            return new String(inputStream.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException("Error loading resource " + filename, e);
        }
    }
}
