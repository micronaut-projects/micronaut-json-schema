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

/**
 * A compiled, immutable and thread-safe JSON Schema produced by {@link JsonSchemaEngine#compile}.
 *
 * @author Graeme Rocher
 * @since 2.3.2
 */
@Internal
public final class CompiledJsonSchema {
    final Schema root;
    final boolean collectAnnotations;

    CompiledJsonSchema(Schema root, boolean collectAnnotations) {
        this.root = root;
        this.collectAnnotations = collectAnnotations;
    }

    /**
     * Returns the location of the schema.
     *
     * @return The absolute location of the schema
     */
    public String getLocation() {
        return root.location;
    }

    /**
     * Returns the dialect of the schema.
     *
     * @return The dialect of the schema
     */
    public Dialect getDialect() {
        return root.resource.dialect();
    }

    @Override
    public String toString() {
        return "CompiledJsonSchema{" + root.location + "}";
    }
}
