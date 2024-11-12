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
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.TypeDef;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static io.micronaut.jsonschema.generator.aggregator.TypeAggregator.getTypeDefFromJson;

/**
 * An aggregator for storing and accessing definitions from json schema.
 * Saves a map of definition reference to TypeDef.
 *
 * @author Elif Kurtay
 * @since 1.2
 */
@Internal
@Singleton
public class DefinitionsAggregator {
    private static HashMap<String, TypeDef> definitions = new HashMap<>();
    private static HashSet<String> oneOfSet = new HashSet<>();

    public static TypeDef getDefinitionType(String key, Map<String, Object> definition) {
        addDefinition(key, definition);
        return definitions.get(key);
    }

    public static TypeDef getDefinitionType(String key) {
        if (hasDefinition(key)) {
            return definitions.get(key);
        }
        if (key.equals("#/definitions/ResourceList")) {
            return ClassTypeDef.of("ResourceList");
        }
        return null;
    }

    public static boolean hasDefinition(String key) {
        return definitions.containsKey(key);
    }

    public static boolean isInheriting(String key) {
        return oneOfSet.contains(key);
    }

    public static void addDefinition(String key, Map<String, Object> definition) {
        if (!hasDefinition(key)) {
            var typeDef = getTypeDefFromJson(definition);
            definitions.put(key, typeDef);
        }
    }

    public static void addDefinition(String key, TypeDef classDef) {
        if (!hasDefinition(key)) {
            definitions.put(key, classDef);
        } else {
            definitions.replace(key, classDef);
        }
    }

    public static void addOneOf(String key) {
        oneOfSet.add(key);
    }

    public static void clearAllDefinitions() {
        definitions.clear();
        oneOfSet.clear();
    }
}
