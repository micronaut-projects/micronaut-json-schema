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
package io.micronaut.jsonschema.generator.aggregator;

import io.micronaut.core.annotation.Internal;
import io.micronaut.sourcegen.model.TypeDef;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.Map;

import static io.micronaut.jsonschema.generator.aggregator.TypeAggregator.getTypeDefFromJson;

/**
 * An aggregator for storing and accessing definitions from json schema.
 */
@Internal
@Singleton
public class DefinitionsAggregator {
    private static HashMap<String, TypeDef> definitions = new HashMap<>();

    public static void addDefinition(String key, Map<String, Object> definition) {
        if (!hasDefinition(key)) {
            var typeDef = getTypeDefFromJson(definition);
            var annotations = AnnotationsAggregator.getAnnotations(definition, typeDef);
            definitions.put(key, typeDef.annotated(annotations));
        }
    }

    public static void addDefinition(String key, TypeDef classDef) {
        if (!hasDefinition(key)) {
            definitions.put(key, classDef);
        }
    }

    public static boolean hasDefinition(String key) {
        return definitions.containsKey(key);
    }

    public static TypeDef getDefinitionType(String key, Map<String, Object> definition) {
        addDefinition(key, definition);
        return definitions.get(key);
    }

    public static TypeDef getDefinitionType(String key) {
        return definitions.get(key);
    }

    public static Map<String, TypeDef> getDefinitions() {
        return definitions;
    }
}
