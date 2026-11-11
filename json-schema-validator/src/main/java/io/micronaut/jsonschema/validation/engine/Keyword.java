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

import java.util.function.Consumer;

/**
 * A compiled keyword of a schema object.
 *
 * @author Graeme Rocher
 * @since 2.3.2
 */
abstract class Keyword {
    final String name;
    final String location;

    Keyword(Schema owner, String name) {
        this.name = name;
        this.location = owner.location + "/" + name;
    }

    /**
     * Evaluates the keyword against an instance.
     *
     * @param instance The instance
     * @param ctx The evaluation context
     * @param annotations The annotations of the current instance location, or null if not collected
     * @return true if valid
     */
    abstract boolean evaluate(JsonNode instance, EvaluationContext ctx, @Nullable Annotations annotations);

    /**
     * Visits the subschemas referenced by this keyword.
     *
     * @param consumer The consumer
     */
    void forEachSchema(Consumer<Schema> consumer) {
        // no subschemas by default
    }

    @Override
    public String toString() {
        return location;
    }
}
