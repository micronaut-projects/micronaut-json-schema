/*
 * Copyright 2017-2023 original authors
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

import com.fasterxml.jackson.core.JsonPointer;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.EnumDef;
import io.micronaut.sourcegen.model.ObjectDefBuilder;
import io.micronaut.sourcegen.model.TypeDef;

import javax.lang.model.SourceVersion;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.micronaut.jsonschema.generator.CodeGenerator.buildEnum;
import static java.lang.String.join;

/**
 * An aggregator for deducing type information from json schema.
 */
@Internal
public class TypeAggregator {

    private static final Map<String, TypeDef> TYPE_MAP = CollectionUtils.mapOf(new Object[]{
        "integer", TypeDef.Primitive.INT, "boolean", TypeDef.Primitive.BOOLEAN, "array", TypeDef.of(List.class),
        "void", TypeDef.VOID, "string", TypeDef.STRING, "object", TypeDef.OBJECT,
        "number", TypeDef.Primitive.FLOAT, "null", TypeDef.OBJECT});

    public static TypeDef getTypeDef(ObjectDefBuilder objectBuilder, String propertyName, Map<String, Object> description) {
        var items = (Map<String, Object>) description.get("items");
        Class listClass = List.class;
        if (description.containsKey("uniqueItems") && description.get("uniqueItems").toString().equals("true")) {
            listClass = Set.class;
        }

        TypeDef propertyType = getTypeDefFromJson(items);
        if (propertyType.equals(TypeDef.of(List.class))) {
            propertyType = getTypeDef(objectBuilder, propertyName, items);
        } else if (items.containsKey("enum")) {
            propertyType = getEnumType(objectBuilder, propertyName, items);
        } else if (propertyType instanceof TypeDef.Primitive primitive) {
            propertyType = primitive.wrapperType();
        }

        var annotations = AnnotationsAggregator.getAnnotations(items, propertyType);
        return TypeDef.parameterized(listClass, propertyType.annotated(annotations));
    }

    public static TypeDef getEnumType(ObjectDefBuilder objectBuilder, String propertyName, Map<String, Object> description) {
        EnumDef enumDef = buildEnum(description, propertyName);
        objectBuilder.addInnerType(enumDef);
        return enumDef.asTypeDef();
    }

    public static TypeDef getTypeDefFromJson(Map<String, Object> description) {
        var type = description.getOrDefault("type", "object");
        String typeName;
        if (type.getClass() == ArrayList.class) {
            typeName = ((ArrayList<?>) type).get(0).toString();
        } else {
            typeName = type.toString();
        }
        if (typeName.equals("string") && description.containsKey("format")) {
            var format = description.get("format").toString();
            switch (format) {
                case "date": return ClassTypeDef.of(LocalDate.class);
                case "date-time", "time": return ClassTypeDef.of(ZonedDateTime.class);
                case "duration": return ClassTypeDef.of(Duration.class);
                case "ipv4": return ClassTypeDef.of(java.net.Inet4Address.class);
                case "ipv6": return ClassTypeDef.of(java.net.Inet6Address.class);
                case "uuid": return ClassTypeDef.of(UUID.class);
                case "uri", "iri": return ClassTypeDef.of(URI.class);
                case "json-pointer": return ClassTypeDef.of(JsonPointer.class);
                // missing: email, web hostname, uri-reference, uri-template, regex
                default: return TYPE_MAP.get(typeName);
            }
        }
        return TYPE_MAP.get(typeName);
    }

    public static String getConstantName(String input) {
        if (input.equals(input.toUpperCase())) {
            return input;
        }
        String cleanedInput = input.replaceAll("[-_]", " ")
            .replaceAll("(?<!^)(?=[A-Z])", " ")
            .replaceAll("[^a-zA-Z0-9 ]", "")
            .trim();

        while (!Character.isJavaIdentifierStart(cleanedInput.charAt(0))) {
            cleanedInput = cleanedInput.substring(1);
        }

        // Split into words
        String[] words = cleanedInput.split("\\s+");
        try {
            // Check if the input is acceptable
            if (words.length == 0 || words[0].isEmpty()) {
                throw new IllegalArgumentException("The enum constant name is not an acceptable identifier name.");
            }
            for (int i = 0; i < words.length; i++) {
                words[i] = words[i].toUpperCase();
            }
            return join("_", words);
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }

    public static String getCamelCaseName(String input) {
        if (SourceVersion.isName(input)) {
            return input;
        }
        String cleanedInput = input.replaceAll("[-_]", " ")
            .replaceAll("[^a-zA-Z0-9 ]", "")
            .trim();

        while (!Character.isJavaIdentifierStart(cleanedInput.charAt(0))) {
            cleanedInput = cleanedInput.substring(1);
        }

        // Split into words
        String[] words = cleanedInput.split("\\s+");
        StringBuilder camelCaseString = new StringBuilder();

        // Check if the input is acceptable
        if (words.length == 0 || words[0].isEmpty()) {
            throw new IllegalArgumentException("Property name is not an acceptable variable name");
        }

        for (int i = 0; i < words.length; i++) {
            String word = words[i].trim();
            if (!word.isEmpty()) {
                if (i == 0) {
                    camelCaseString.append(word.toLowerCase());
                } else {
                    camelCaseString.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase());
                }
            }
        }
        return camelCaseString.toString();
    }
}
