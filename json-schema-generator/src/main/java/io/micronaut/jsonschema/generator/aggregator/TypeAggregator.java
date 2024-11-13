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
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.TypeDef;

import javax.lang.model.SourceVersion;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static io.micronaut.core.util.StringUtils.capitalize;
import static io.micronaut.jsonschema.generator.aggregator.DefinitionsAggregator.getDefinitionType;
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

    public static TypeDef getTypeDefFromJson(Map<String, Object> description) {
        var type = description.getOrDefault("type", "object");
        String typeName;
        if (type.getClass() == ArrayList.class) {
            typeName = ((ArrayList<?>) type).get(0).toString();
        } else {
            typeName = type.toString();
        }

        TypeDef typeDef;
        if (typeName.equals("string") && description.containsKey("format")) {
            var format = description.get("format").toString();
            switch (format) {
                case "date": typeDef = ClassTypeDef.of(LocalDate.class); break;
                case "date-time", "time": typeDef = ClassTypeDef.of(ZonedDateTime.class); break;
                case "duration": typeDef = ClassTypeDef.of(Duration.class); break;
                case "ipv4": typeDef = ClassTypeDef.of(java.net.Inet4Address.class); break;
                case "ipv6": typeDef = ClassTypeDef.of(java.net.Inet6Address.class); break;
                case "uuid": typeDef = ClassTypeDef.of(UUID.class); break;
                case "uri", "iri": typeDef = ClassTypeDef.of(URI.class); break;
                case "json-pointer": typeDef = ClassTypeDef.of(JsonPointer.class); break;
                // missing: email, web hostname, uri-reference, uri-template, regex
                default: typeDef = TypeDef.STRING;
            }
        } else if (description.containsKey("$ref")) {
            typeDef = getDefinitionType(description.get("$ref").toString());
        } else {
            typeDef = TYPE_MAP.get(typeName);
        }
        return typeDef;
    }

    public static Map<String, ?> getJsonSchema(InputStream inputStream, File schemaFile) throws IOException {
        JsonMapper jsonMapper = new JsonMapper();
        if (inputStream != null) {
            String jsonString = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return (Map<String, ?>) jsonMapper.readValue(jsonString, HashMap.class);
        } else if (schemaFile != null) {
            return (Map<String, ?>) jsonMapper.readValue(schemaFile, HashMap.class);
        }
        return null;
    }

    public static File getOutputFile(Path outputPath, String packageName, String fileName) throws IOException {
        // Create full path
        String packagePath = packageName.replace('.', File.separatorChar);
        Path fullPath = outputPath.resolve(packagePath).resolve(fileName);

        // Create directories if they do not exist
        File outputFile = fullPath.toFile();
        if (!outputFile.getParentFile().exists()) {
            outputFile.getParentFile().mkdirs();
        }
        if (!outputFile.exists() && !outputFile.createNewFile()) {
            throw new IOException("Could not create file " + outputFile.getAbsolutePath());
        }
        return outputFile;
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

    public static String getFileName(Map<String, ?> schema, Optional<String> topLevelName, VisitorContext.Language language) {
        String fileName = null;
        if (topLevelName.isPresent()) {
            fileName = topLevelName.get();
        } else if (schema.containsKey("title")) {
            fileName = capitalize(getCamelCaseName(schema.get("title").toString()));
        } else if (schema.keySet().size() == 1) {
            fileName = schema.keySet().toArray()[0].toString();
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
