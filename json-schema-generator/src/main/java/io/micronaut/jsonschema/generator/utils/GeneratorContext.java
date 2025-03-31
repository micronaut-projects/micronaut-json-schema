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
package io.micronaut.jsonschema.generator.utils;

import io.micronaut.core.annotation.Internal;
import io.micronaut.jsonschema.generator.SourceGenerator;
import io.micronaut.jsonschema.generator.aggregator.AnnotationsAggregator;
import io.micronaut.jsonschema.model.Schema;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.TypeDef;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.micronaut.jsonschema.generator.SourceGenerator.getInputFileName;
import static io.micronaut.jsonschema.generator.aggregator.TypeAggregator.getClassName;
import static io.micronaut.jsonschema.generator.aggregator.TypeAggregator.getTypeDefFromJson;
import static io.micronaut.jsonschema.model.Schema.DEF_SCHEMA_REF_PREFIX;
import static io.micronaut.jsonschema.model.Schema.ONE_OF_SCHEMA_REF_PREFIX;

/**
 * An aggregator for storing and accessing definitions and oneOf relations from json schema.
 * Saves a map of definition reference to TypeDef, Boolean (boolean value is true if the type is a class/interface object).
 * Saves a map of oneOf objects to keep in track on inheriting objects.
 *
 * @author Elif Kurtay
 * @since 1.3
 */
@Internal
public final class GeneratorContext {
    private final HashMap<String, Map.Entry<TypeDef, Boolean>> DEFINITIONS = new HashMap<>();
    private final HashMap<String, LinkedList<String>> TEMP_DEFINITIONS = new HashMap<>();
    private final HashMap<String, Schema> ONE_OF_SET = new HashMap<>();
    private SourceGeneratorConfig configuration;

    public boolean isDefinitionClass(String key) {
        return getDefinition(key).getValue();
    }

    public TypeDef getDefinitionType(String key) {
        return getDefinition(key).getKey();
    }

    private Map.Entry<TypeDef, Boolean> getDefinition(String key) {
        String defKey = unifyKey(key);
        if (hasDefinition(defKey)) {
            return DEFINITIONS.get(defKey);
        }
        throw new IllegalArgumentException("Definition not found: " + key);
    }

    public List<Map.Entry<String, Schema>> getOneOfsToGenerate() {
        return ONE_OF_SET.entrySet().stream().filter(entry -> entry.getValue() != null).toList();
    }

    public boolean hasDefinition(String key) {
        return DEFINITIONS.containsKey(key);
    }

    public boolean isInheriting(String className) {
        String key = getInputFileName() + ONE_OF_SCHEMA_REF_PREFIX + className;
        String keyRef = getInputFileName() + DEF_SCHEMA_REF_PREFIX + className;
        return ONE_OF_SET.containsKey(key) || ONE_OF_SET.containsKey(keyRef);
    }

    public void addDefinition(String key, Schema definition) {
        String unifiedKey = unifyKey(key);
        if (definition.hasOneOf()) {
            // inner oneOf's are treated as objects
            // assuming single inheritance at the top level, skips any other oneOf
            addDefinition(unifiedKey, TypeDef.OBJECT, definition.hasProperties());
        } else if (definition.has$ref()) {
            var referredKey = unifyKey(getInputFileName() + definition.get$ref());
            if (!hasDefinition(referredKey)) {
                addTempDefinition(unifiedKey, referredKey);
            } else {
                var entry = getDefinition(referredKey);
                addDefinition(unifiedKey, entry.getKey(), entry.getValue());
            }
        } else {
            var typeDef = getTypeDefFromJson(definition, this);
            assert typeDef != null;
            boolean isClass = !typeDef.isPrimitive() && !typeDef.equals(TypeDef.STRING)
                && !typeDef.equals(ClassTypeDef.of(Float.class))
                && !typeDef.equals(ClassTypeDef.of(Integer.class))
                && !typeDef.equals(TypeDef.of(List.class));
            if (isClass) {
                // classes get generated later
                typeDef = ClassTypeDef.of(getClassName(unifiedKey.substring(unifiedKey.lastIndexOf('/') + 1)));
            } else if (typeDef.equals(TypeDef.of(List.class))) {
                typeDef = getListTypeDef(definition, typeDef);
            }
            var annotations = AnnotationsAggregator.getAnnotations(definition, typeDef, false);
            if (!annotations.isEmpty()) {
                typeDef = typeDef.annotated(annotations);
            }
            addDefinition(unifiedKey, typeDef, isClass);
        }
    }

    private TypeDef getListTypeDef(Schema definition, TypeDef typeDef) {
        Schema items = definition.getItems() != null ? definition.getItems() : definition.getContains();
        TypeDef innerType;
        if (items == null) {
            return TypeDef.OBJECT;
        } else {
            innerType = getTypeDefFromJson(items, this);
            if (innerType instanceof TypeDef.Primitive primitive) {
                innerType = primitive.wrapperType();
            } else if (innerType.equals(TypeDef.of(List.class))) {
                innerType = getListTypeDef(items, innerType);
            }
        }
        var annotations = AnnotationsAggregator.getAnnotations(items, innerType, false);
        if (!annotations.isEmpty()) {
            innerType = innerType.annotated(annotations);
        }
        typeDef = TypeDef.parameterized(
            (definition.isUniqueItems() != null && definition.isUniqueItems()) ? Set.class : List.class,
            innerType);
        return typeDef;
    }

    public void addDefinition(String key, TypeDef classDef, boolean isClass) {
        AbstractMap.SimpleEntry<TypeDef, Boolean> newDef = new AbstractMap.SimpleEntry<>(classDef, isClass);
        String defKey = unifyKey(key);
        if (!hasDefinition(defKey)) {
            DEFINITIONS.put(defKey, newDef);
        } else {
            DEFINITIONS.replace(defKey, newDef);
        }
        // update previous definitions that pointed to the current reference
        if (TEMP_DEFINITIONS.containsKey(defKey)) {
            TEMP_DEFINITIONS.get(defKey).forEach(ref -> {
                DEFINITIONS.put(ref, newDef);
            });
        }
    }

    public void addTempDefinition(String referringDef, String ref) {
        String referringKey = unifyKey(referringDef);
        String referredKey = unifyKey(ref);
        if (TEMP_DEFINITIONS.containsKey(referredKey)) {
            var tempList = TEMP_DEFINITIONS.get(referredKey);
            tempList.add(referringKey);
            TEMP_DEFINITIONS.replace(referredKey, tempList);
        } else {
            TEMP_DEFINITIONS.put(referredKey, new LinkedList<>(List.of(referringKey)));
        }
    }

    public void addOneOf(String key) {
        ONE_OF_SET.put(unifyKey(key), null);
    }

    public void addOneOf(Schema oneOf) {
        String fileName = getInputFileName();
        String className = (oneOf.hasTitle()) ? getClassName(oneOf.getTitle()) : "Option" + ONE_OF_SET.size();

        ONE_OF_SET.put(fileName + ONE_OF_SCHEMA_REF_PREFIX + className, oneOf);
        addDefinition(fileName + ONE_OF_SCHEMA_REF_PREFIX + className, ClassTypeDef.of(getClassName(className)), true);
    }

    public void clearAll() {
        DEFINITIONS.clear();
        ONE_OF_SET.clear();
        TEMP_DEFINITIONS.clear();
    }

    private String unifyKey(String key) {
        if (!key.contains("#/definitions/")) {
            return key;
        }
        return key.replace("#/definitions/", DEF_SCHEMA_REF_PREFIX);
    }

    /**
     * Set the current configuration.
     * @param configuration The configuration
     */
    public void setConfiguration(SourceGeneratorConfig configuration) {
        this.configuration = configuration;
    }

    /**
     * Get the current configuration.
     * @return The configuration
     */
    public SourceGeneratorConfig getConfiguration() {
        return configuration;
    }
}
