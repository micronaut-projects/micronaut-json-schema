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

    /**
     * @param dialect The dialect
     * @return The default vocabularies of the dialect
     */
    static Vocabularies of(Dialect dialect) {
        return new Vocabularies(dialect, true, true, true, false);
    }

    /**
     * Computes the vocabularies declared by a meta-schema's {@code $vocabulary} keyword.
     *
     * @param dialect The base dialect of the meta-schema
     * @param vocabulary The {@code $vocabulary} object
     * @return The vocabularies
     */
    static Vocabularies of(Dialect dialect, JsonNode vocabulary) {
        boolean applicator = false;
        boolean validation = false;
        boolean unevaluated = false;
        boolean formatAssertion = false;
        for (Map.Entry<String, JsonNode> entry : vocabulary.entries()) {
            String uri = entry.getKey();
            int slash = uri.lastIndexOf('/');
            String name = slash >= 0 ? uri.substring(slash + 1) : uri;
            switch (name) {
                case "applicator" -> {
                    applicator = true;
                    if (dialect == Dialect.DRAFT_2019_09) {
                        unevaluated = true;
                    }
                }
                case "validation" -> validation = true;
                case "unevaluated" -> unevaluated = true;
                case "format-assertion" -> formatAssertion = true;
                default -> {
                    // other vocabularies (core, meta-data, content) do not change assertions
                }
            }
        }
        return new Vocabularies(dialect, applicator, validation, unevaluated, formatAssertion);
    }
}
