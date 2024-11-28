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
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.jsonschema.generator.SourceGenerator;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.TypeDef;

import javax.lang.model.SourceVersion;
import io.micronaut.jsonschema.model.Schema;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static io.micronaut.core.util.StringUtils.capitalize;
import static io.micronaut.jsonschema.generator.utils.GeneratorContext.getDefinitionType;
import static io.micronaut.jsonschema.model.Schema.THIS_SCHEMA_REF;
import static java.lang.String.join;

/**
 * An aggregator for deducing type information from json schema.
 *
 * @author Elif Kurtay
 * @since 1.2
 */
@Internal
public class TypeAggregator {

    private static final Map<String, TypeDef> TYPE_MAP = CollectionUtils.mapOf(new Object[]{
        "integer", TypeDef.Primitive.INT, "boolean", TypeDef.Primitive.BOOLEAN, "array", TypeDef.of(List.class),
        "void", TypeDef.VOID, "string", TypeDef.STRING, "object", TypeDef.OBJECT,
        "number", TypeDef.Primitive.FLOAT, "null", TypeDef.OBJECT});

    public static TypeDef getTypeDefFromJson(Schema schema) {
        var type = schema.getType() != null ? schema.getType().get(0) : Schema.Type.OBJECT;
        TypeDef typeDef;
        if (type.equals(Schema.Type.STRING) && schema.getFormat() != null) {
            var format = schema.getFormat();
            switch (format) {
                case "date": typeDef = ClassTypeDef.of(LocalDate.class); break;
                case "date-time", "time": typeDef = ClassTypeDef.of(ZonedDateTime.class); break;
                case "duration": typeDef = ClassTypeDef.of(Duration.class); break;
                case "ipv4": typeDef = ClassTypeDef.of(java.net.Inet4Address.class); break;
                case "ipv6": typeDef = ClassTypeDef.of(java.net.Inet6Address.class); break;
                case "uuid": typeDef = ClassTypeDef.of(UUID.class); break;
                case "uri", "iri": typeDef = ClassTypeDef.of(URI.class); break;
                case "json-pointer": typeDef = ClassTypeDef.of(JsonPointer.class); break;
                // missing: web hostname, uri-reference, uri-template, regex
                default: typeDef = TypeDef.STRING;
            }
        } else if (schema.has$ref()) {
            String ref = schema.get$ref();
            if (ref.equals(THIS_SCHEMA_REF)) {
                return TypeDef.THIS;
            } else if (ref.indexOf("#") == 0) {
                ref = SourceGenerator.getInputFileName() + ref;
            }
            typeDef = getDefinitionType(ref);
        } else if (type.equals(Schema.Type.NUMBER) && schema.getPattern() != null) {
            if (schema.getPattern().contains(".")) {
                typeDef = ClassTypeDef.of(Float.class);
            } else {
                typeDef = ClassTypeDef.of(Integer.class);
            }
        } else {
            typeDef = TYPE_MAP.get(type.toString().toLowerCase(Locale.ENGLISH));
        }
        if (typeDef == null) {
            throw new IllegalArgumentException("Unsupported type: " + type);
        }
        return typeDef;
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
        if (SourceVersion.isKeyword(input)) {
            return input + "_json";
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

    public static String unicodeToString(String input) {
        StringBuilder newName = new StringBuilder();
        for (Character c : input.toCharArray()) {
            if (!Character.isLetter(c)) {
                String charName = Character.getName(c);
                newName.append(' ')
                    .append(charName, 0, charName.lastIndexOf(' ') != -1 ? charName.lastIndexOf(' ') : charName.length())
                    .append(' ');
            } else {
                newName.append(Character.toUpperCase(c));
            }
        }
        String cleanedInput = newName.toString().replaceAll("[-_]", " ")
            .replaceAll("[^a-zA-Z0-9 ]", "")
            .trim();

        return cleanedInput.replaceAll(" ", "_");
    }

    public static boolean isOnlyLetters(String input) {
        boolean isLetters = true;
        for (Character c : input.toCharArray()) {
            if (!Character.isLetter(c) && !Character.isWhitespace(c)) {
                isLetters = false;
            }
        }
        return isLetters;
    }

    public static String getFileName(Schema schema, Optional<String> topLevelName, VisitorContext.Language language) {
        String fileName;
        if (topLevelName.isPresent() && !topLevelName.get().isEmpty()) {
            fileName = topLevelName.get();
        } else if (schema.hasTitle()) {
            fileName = capitalize(getCamelCaseName(schema.getTitle()));
        } else {
            fileName = "SchemaFile"; // default
        }

        switch (language) {
            case KOTLIN: fileName += ".kt"; break;
            case GROOVY: fileName += ".groovy"; break;
            default: fileName += ".java"; break;
        }
        return fileName;
    }
}
