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
package io.micronaut.jsonschema.visitor.aggregator;

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.jsonschema.visitor.context.JsonSchemaContext;
import io.micronaut.jsonschema.visitor.model.Schema;

/**
 * An interface for objects responsible for aggregating JSON schema info.
 *
 * @since 1.0.0
 * @author Andriy Dmytruk
 */
@Internal
public interface SchemaInfoAggregator {

    /**
     * A method that is called for adding JSON schema info.
     *
     * @param element The type
     * @param schema The current schema
     * @param visitorContext The visitor context
     * @param context The JSON schema visitor configuration
     * @return The new or modified schema
     */
    Schema addInfo(TypedElement element, Schema schema, VisitorContext visitorContext, JsonSchemaContext context);

}
