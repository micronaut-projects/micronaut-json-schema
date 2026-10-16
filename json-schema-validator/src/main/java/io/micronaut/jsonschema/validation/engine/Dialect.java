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
import org.jspecify.annotations.Nullable;

/**
 * The JSON Schema dialects (specification drafts) supported by the engine.
 *
 * @author Graeme Rocher
 * @since 2.3.2
 */
@Internal
public enum Dialect {
    /**
     * JSON Schema draft-04.
     */
    DRAFT_4("http://json-schema.org/draft-04/schema"),
    /**
     * JSON Schema draft-06.
     */
    DRAFT_6("http://json-schema.org/draft-06/schema"),
    /**
     * JSON Schema draft-07.
     */
    DRAFT_7("http://json-schema.org/draft-07/schema"),
    /**
     * JSON Schema 2019-09.
     */
    DRAFT_2019_09("https://json-schema.org/draft/2019-09/schema"),
    /**
     * JSON Schema 2020-12.
     */
    DRAFT_2020_12("https://json-schema.org/draft/2020-12/schema");

    private final String metaSchemaUri;

    Dialect(String metaSchemaUri) {
        this.metaSchemaUri = metaSchemaUri;
    }

    /**
     * Returns the meta-schema URI.
     *
     * @return The URI of the dialect's meta-schema
     */
    public String getMetaSchemaUri() {
        return metaSchemaUri;
    }

    /**
     * Resolves a dialect from the value of a {@code $schema} keyword.
     *
     * @param uri The meta-schema URI
     * @return The dialect or null if the URI is not a known meta-schema
     */
    public static @Nullable Dialect fromMetaSchemaUri(String uri) {
        String normalized = uri.endsWith("#") ? uri.substring(0, uri.length() - 1) : uri;
        if (normalized.startsWith("https://json-schema.org/draft-")) {
            normalized = "http://" + normalized.substring("https://".length());
        } else if (normalized.startsWith("http://json-schema.org/draft/")) {
            normalized = "https://" + normalized.substring("http://".length());
        }
        for (Dialect dialect : values()) {
            if (dialect.metaSchemaUri.equals(normalized)) {
                return dialect;
            }
        }
        return null;
    }

    boolean atLeast(Dialect other) {
        return compareTo(other) >= 0;
    }

    boolean refOverridesSiblings() {
        return compareTo(DRAFT_7) <= 0;
    }

    String idKeyword() {
        return this == DRAFT_4 ? "id" : "$id";
    }
}
