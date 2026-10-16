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

import io.micronaut.core.annotation.Internal;
import io.micronaut.json.tree.JsonNode;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

/**
 * Loads schema documents referenced by URI (for example through {@code $ref}).
 *
 * @author Graeme Rocher
 * @since 2.3.2
 */
@Internal
@FunctionalInterface
public interface SchemaRetriever {

    /**
     * A retriever that cannot load any document.
     */
    SchemaRetriever NONE = uri -> null;

    /**
     * Loads the schema document identified by the given absolute URI.
     *
     * @param uri The absolute URI, without fragment
     * @return The parsed document, or null if no document exists for the URI
     * @throws IOException If the document cannot be read
     */
    @Nullable JsonNode retrieve(String uri) throws IOException;
}
