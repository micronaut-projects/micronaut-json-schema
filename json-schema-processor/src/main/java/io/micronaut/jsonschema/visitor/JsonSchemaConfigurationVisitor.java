/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.jsonschema.visitor;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.jsonschema.JsonSchemaConfiguration;
import io.micronaut.jsonschema.visitor.model.Schema;

import java.util.HashMap;
import java.util.Map;

/**
 * A visitor for reading the JSON schema configuration.
 * It must be defined with a {@link JsonSchemaConfiguration} annotation on a bean.
 *
 * @since 1.0.0
 * @author Andriy Dmytruk
 */
@Internal
public final class JsonSchemaConfigurationVisitor implements TypeElementVisitor<JsonSchemaConfiguration, Object> {

    public static final String JSON_SCHEMA_CONFIGURATION_PROPERTY = "io.micronaut.jsonschema.config";

    @Override
    public int getOrder() {
        return 1; // Run before the JSON Schema visitor
    }

    @Override
    public @NonNull TypeElementVisitor.VisitorKind getVisitorKind() {
        return VisitorKind.AGGREGATING;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext visitorContext) {
        AnnotationValue<?> annotation = element.getAnnotation(JsonSchemaConfiguration.class);
        if (annotation != null) {
            String outputLocation = annotation.stringValue("outputLocation")
                .orElse(JsonSchemaContext.DEFAULT_OUTPUT_LOCATION);
            String baseUri = annotation.getRequiredValue("baseUri", String.class);
            if (baseUri.endsWith("/")) {
                baseUri = baseUri.substring(0, baseUri.length() - 1);
            }
            boolean binaryAsArray = annotation.booleanValue("binaryAsArray")
                .orElse(JsonSchemaContext.DEFAULT_BINARY_AS_ARRAY);
            JsonSchemaContext context = new JsonSchemaContext(outputLocation, baseUri, binaryAsArray, new HashMap<>());
            visitorContext.put(JSON_SCHEMA_CONFIGURATION_PROPERTY, context);
        }
    }

    /**
     * A configuration for the JSON schema.
     *
     * @param outputLocation The output location for schemas
     * @param baseUrl The base URL of the schemas
     * @param binaryAsArray Whether to represent byte arrays as arrays instead of base 64 string
     * @param createdSchemasByType A cache of crated schemas
     */
    public record JsonSchemaContext(
        String outputLocation,
        String baseUrl,
        boolean binaryAsArray,
        Map<String, Schema> createdSchemasByType
    ) {
        public static final String DEFAULT_OUTPUT_LOCATION = "schemas";
        public static final boolean DEFAULT_BINARY_AS_ARRAY = false;
        private static final String DEFAULT_BASE_URL = "http://localhost:8080/schemas";

        public static JsonSchemaContext createDefault() {
            return new JsonSchemaContext(
                DEFAULT_OUTPUT_LOCATION, DEFAULT_BASE_URL, DEFAULT_BINARY_AS_ARRAY, new HashMap<>()
            );
        }
    }

}
