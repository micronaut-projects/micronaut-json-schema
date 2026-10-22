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
package io.micronaut.jsonschema.validation.engine;

import io.micronaut.json.tree.JsonNode;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * The dialect and the vocabularies that are enabled for a schema resource.
 *
 * @param dialect The dialect
 * @param applicator Whether the applicator vocabulary is enabled
 * @param validation Whether the validation vocabulary is enabled
 * @param unevaluated Whether the unevaluated vocabulary is enabled
 * @param formatAssertion Whether the format-assertion vocabulary is enabled
 * @author Graeme Rocher
 * @since 2.3.2
 */
record Vocabularies(Dialect dialect, boolean applicator, boolean validation, boolean unevaluated, boolean formatAssertion) {

    private static final String VOCABULARY_2019_09 = "https://json-schema.org/draft/2019-09/vocab/";
    private static final String VOCABULARY_2020_12 = "https://json-schema.org/draft/2020-12/vocab/";

    /**
     * @param dialect The dialect
     * @return The default vocabularies of the dialect
     */
    static Vocabularies of(Dialect dialect) {
        return new Vocabularies(dialect, true, true, true, false);
    }

    /**
     * Determines the dialect of a meta-schema from the vocabularies it declares.
     *
     * @param vocabulary The {@code $vocabulary} object, or null
     * @return The dialect, or null if the vocabularies are not those of a known dialect
     */
    static @Nullable Dialect dialectOf(@Nullable JsonNode vocabulary) {
        if (vocabulary == null || !vocabulary.isObject()) {
            return null;
        }
        for (Map.Entry<String, JsonNode> entry : vocabulary.entries()) {
            if (entry.getKey().startsWith(VOCABULARY_2020_12)) {
                return Dialect.DRAFT_2020_12;
            }
            if (entry.getKey().startsWith(VOCABULARY_2019_09)) {
                return Dialect.DRAFT_2019_09;
            }
        }
        return null;
    }

    /**
     * Computes the vocabularies declared by a meta-schema's {@code $vocabulary} keyword.
     *
     * @param dialect The base dialect of the meta-schema
     * @param vocabulary The {@code $vocabulary} object
     * @return The vocabularies
     * @throws IllegalArgumentException If a required vocabulary is not supported
     */
    static Vocabularies of(Dialect dialect, JsonNode vocabulary) {
        String prefix = dialect == Dialect.DRAFT_2019_09 ? VOCABULARY_2019_09 : VOCABULARY_2020_12;
        boolean applicator = false;
        boolean validation = false;
        boolean unevaluated = false;
        boolean formatAssertion = false;
        for (Map.Entry<String, JsonNode> entry : vocabulary.entries()) {
            String uri = entry.getKey();
            String name = uri.startsWith(prefix) ? uri.substring(prefix.length()) : uri;
            switch (name) {
                case "applicator" -> {
                    applicator = true;
                    unevaluated |= dialect == Dialect.DRAFT_2019_09;
                }
                case "validation" -> validation = true;
                case "unevaluated" -> unevaluated = true;
                case "format-assertion" -> formatAssertion = true;
                case "core", "meta-data", "content", "format", "format-annotation" -> {
                    // these vocabularies do not add assertions
                }
                default -> {
                    JsonNode required = entry.getValue();
                    if (required.isBoolean() && required.getBooleanValue()) {
                        // https://json-schema.org/draft/2020-12/json-schema-core#section-8.1.2
                        throw new IllegalArgumentException("Unsupported required vocabulary: " + uri);
                    }
                }
            }
        }
        return new Vocabularies(dialect, applicator, validation, unevaluated, formatAssertion);
    }
}
