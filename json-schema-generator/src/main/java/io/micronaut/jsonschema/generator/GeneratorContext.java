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
package io.micronaut.jsonschema.generator;

import io.micronaut.core.annotation.Internal;
import io.micronaut.jsonschema.generator.aggregator.AnnotationsAggregator;
import io.micronaut.jsonschema.model.Schema;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.TypeDef;
import jakarta.inject.Singleton;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.micronaut.core.util.StringUtils.capitalize;
import static io.micronaut.jsonschema.generator.aggregator.TypeAggregator.getCamelCaseName;
import static io.micronaut.jsonschema.generator.aggregator.TypeAggregator.getTypeDefFromJson;

/**
 * An aggregator for storing and accessing definitions and oneOf relations from json schema.
 * Saves a map of definition reference to TypeDef, Boolean (boolean value is true if the type is a class/interface object.
 * Saves a map of oneOf objects to keep in track on inheriting objects.
 *
 * @author Elif Kurtay
 * @since 1.2
 */
@Internal
@Singleton
public class GeneratorContext {
    private static final HashMap<String, Map.Entry<TypeDef, Boolean>> DEFINITIONS = new HashMap<>();
    private static final HashMap<String, Schema> ONE_OF_SET = new HashMap<>();

    public static TypeDef getDefinitionType(String key) {
        String defKey = unifyKey(key);
        if (hasDefinition(defKey)) {
            return DEFINITIONS.get(defKey).getKey();
        }
        return null;
    }

    public static Map.Entry<TypeDef, Boolean> getDefinition(String key) {
        String defKey = unifyKey(key);
        if (hasDefinition(defKey)) {
            return DEFINITIONS.get(defKey);
        }
        return null;
    }

    public static List<Map.Entry<String, Schema>> getOneOfsToGenerate() {
        return ONE_OF_SET.entrySet().stream().filter(entry -> entry.getValue() != null).toList();
    }

    public static boolean hasDefinition(String key) {
        return DEFINITIONS.containsKey(key);
    }

    public static boolean isInheriting(String className) {
        String key = SourceGenerator.getInputFileName() + "#/oneOf/" + className;
        String keyRef = SourceGenerator.getInputFileName() + "#/definitions/" + className;
        return ONE_OF_SET.containsKey(key) || ONE_OF_SET.containsKey(keyRef);
    }

    public static void addDefinition(String key, Schema definition) {
        var typeDef = getTypeDefFromJson(definition);
        assert typeDef != null;
        boolean isClass = !typeDef.isPrimitive() && !typeDef.equals(TypeDef.STRING)
            && !typeDef.equals(ClassTypeDef.of(Float.class))
            && !typeDef.equals(ClassTypeDef.of(Integer.class));
        if (isClass) {
            typeDef = ClassTypeDef.of(capitalize(key.substring(key.lastIndexOf('/') + 1)));
        }
        // add annotations to type
        var annotations = AnnotationsAggregator.getAnnotations(definition, typeDef);
        if (!annotations.isEmpty()) {
            typeDef = typeDef.annotated(annotations);
        }
        addDefinition(unifyKey(key), typeDef, isClass);
    }

    public static void addDefinition(String key, TypeDef classDef, boolean isClass) {
        AbstractMap.SimpleEntry<TypeDef, Boolean> newDef = new AbstractMap.SimpleEntry<>(classDef, isClass);
        String defKey = unifyKey(key);
        if (!hasDefinition(defKey)) {
            DEFINITIONS.put(defKey, newDef);
        } else {
            DEFINITIONS.replace(defKey, newDef);
        }
    }

    public static void addOneOf(String key) {
        ONE_OF_SET.put(unifyKey(key), null);
    }

    public static void addOneOf(Schema oneOf) {
        String fileName = SourceGenerator.getInputFileName();
        String className = (oneOf.hasTitle()) ? capitalize(getCamelCaseName(oneOf.getTitle())) : "Option" + ONE_OF_SET.size();

        ONE_OF_SET.put(fileName + "#/oneOf/" + className, oneOf);
        addDefinition(fileName + "#/oneOf/" + className, ClassTypeDef.of(capitalize(className)), true);
    }

    public static void clearAllDefinitions() {
        DEFINITIONS.clear();
        ONE_OF_SET.clear();
    }

    private static String unifyKey(String key) {
        if (!key.contains("$defs")) {
            return key;
        }
        return key.replace("$defs", "definitions");
    }
}
