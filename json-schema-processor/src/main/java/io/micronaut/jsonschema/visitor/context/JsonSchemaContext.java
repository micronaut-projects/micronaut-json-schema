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
package io.micronaut.jsonschema.visitor.context;

import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.jsonschema.visitor.model.Schema;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A context with configuration for the JSON schema.
 *
 * @param outputLocation The location where JSON schemas will be generated inside the build {@code META-INF/} directory.
 * @param baseUrl The base URI to be used for schemas.
 * @param binaryAsArray Whether to encode byte array as a JSON array.
 *                      The default and preferred behavior is to encode it as a Base64 string.
 * @param draft An enum for JSON Schema draft versions.
 *              Currently only 2020-12 draft is supported.
 * @param strictMode Whether to generate schemas in strict mode.
 *                   In strict mode unresolved properties in JSON will cause an error.
 * @param createdSchemasByType A cache of crated schemas
 */
public record JsonSchemaContext(
    String outputLocation,
    String baseUrl,
    boolean binaryAsArray,
    JsonSchemaDraft draft,
    boolean strictMode,
    Map<String, Schema> createdSchemasByType
) {

    public static final String JSON_SCHEMA_CONTEXT_PROPERTY = "io.micronaut.jsonschema";

    public static final String PARAMETER_PREFIX = VisitorContext.MICRONAUT_BASE_OPTION_NAME + ".jsonschema.";
    public static final String OUTPUT_LOCATION_PARAMETER = PARAMETER_PREFIX + "outputLocation";
    public static final String BASE_URI_PARAMETER = PARAMETER_PREFIX + "baseUri";
    public static final String BINARY_AS_ARRAY_PARAMETER = PARAMETER_PREFIX + "binaryAsArray";
    public static final String JSON_SCHEMA_DRAFT_PARAMETER = PARAMETER_PREFIX + "draft";
    public static final String STRICT_MODE_PARAMETER = PARAMETER_PREFIX + "strictMode";

    public static final String DEFAULT_OUTPUT_LOCATION = "schemas";
    public static final boolean DEFAULT_BINARY_AS_ARRAY = false;
    private static final String DEFAULT_BASE_URL = "http://localhost:8080/schemas";
    private static final JsonSchemaDraft DEFAULT_DRAFT = JsonSchemaDraft.DRAFT_2020_12;
    private static final boolean DEFAULT_STRICT_MODE = false;

    public static Set<String> getParameters() {
        return Set.of(OUTPUT_LOCATION_PARAMETER, BASE_URI_PARAMETER, BINARY_AS_ARRAY_PARAMETER,
            JSON_SCHEMA_DRAFT_PARAMETER, STRICT_MODE_PARAMETER);
    }

    public static JsonSchemaContext createDefault(Map<String, String> options) {
        String outputLocation = options.getOrDefault(OUTPUT_LOCATION_PARAMETER, DEFAULT_OUTPUT_LOCATION);
        String baseUrl = options.getOrDefault(BASE_URI_PARAMETER, DEFAULT_BASE_URL);
        boolean binaryAsArray = options.getOrDefault(BINARY_AS_ARRAY_PARAMETER, String.valueOf(DEFAULT_BINARY_AS_ARRAY)).equals(StringUtils.TRUE);
        JsonSchemaDraft draft = options.get(JSON_SCHEMA_DRAFT_PARAMETER) == null ?
            DEFAULT_DRAFT : JsonSchemaDraft.valueOf(JSON_SCHEMA_DRAFT_PARAMETER);
        boolean strictMode = options.getOrDefault(STRICT_MODE_PARAMETER, String.valueOf(DEFAULT_STRICT_MODE)).equals(StringUtils.TRUE);
        return new JsonSchemaContext(outputLocation, baseUrl, binaryAsArray, draft, strictMode, new HashMap<>());
    }

    /**
     * An enum for JSON Schema draft versions.
     * Currently only 2020-12 draft is supported.
     */
    public enum JsonSchemaDraft {
        DRAFT_2020_12("https://json-schema.org/draft/2020-12/schema");

        private final String draftUrl;

        JsonSchemaDraft(String draftUrl) {
            this.draftUrl = draftUrl;
        }

        /**
         * Get the URL referencing the schema of the draft.
         *
         * @return The schema URL.
         */
        public String getDraftUrl() {
            return draftUrl;
        }
    }
}
