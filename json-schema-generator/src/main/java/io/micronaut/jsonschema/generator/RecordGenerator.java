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
package io.micronaut.jsonschema.generator;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.jsonschema.generator.aggregator.AnnotationInfoAggregator;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.sourcegen.generator.SourceGenerator;
import io.micronaut.sourcegen.generator.SourceGenerators;
import io.micronaut.sourcegen.model.*;
import jakarta.inject.Singleton;

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.micronaut.core.util.StringUtils.capitalize;

/**
 * A generator to create Java Beans from Json Schema.
 *
 * @author Elif Kurtay
 * @since 1.2
 */

@Internal
@Singleton
public final class RecordGenerator {

    private static final Map<String, TypeDef> TYPE_MAP = CollectionUtils.mapOf(new Object[]{
        "integer", TypeDef.Primitive.INT, "boolean", TypeDef.Primitive.BOOLEAN, "array", TypeDef.of(List.class),
        "void", TypeDef.VOID, "string", TypeDef.STRING, "object", TypeDef.OBJECT,
        "number", TypeDef.Primitive.FLOAT, "null", TypeDef.OBJECT});
    private static final Map<String, TypeDef> GENERIC_TYPE_MAP = CollectionUtils.mapOf(new Object[]{
        "integer", TypeDef.of(Integer.class), "boolean", TypeDef.of(Boolean.class),
        "number", TypeDef.of(Float.class), "string", TypeDef.STRING, "object", TypeDef.OBJECT});

    private List<EnumDef> enums = new ArrayList<>();

    // TODO objectName and fileName should match. Perhaps we should just take output directory as argument. The argument does not need to be optional then
    // TODO take language as argument.
    public boolean generate(InputStream inputStream, Optional<File> outputFileLocation) throws IOException {
        var jsonSchema = getJsonSchema(inputStream, null);
        return generateFromSchemaMap(jsonSchema, outputFileLocation);
    }

    public boolean generate(File jsonFileLocation, Optional<File> outputFileLocation) throws IOException {
        var jsonSchema = getJsonSchema(null, jsonFileLocation);
        return generateFromSchemaMap(jsonSchema, outputFileLocation);
    }

    private Map<String, ?> getJsonSchema(InputStream inputStream, File schemaFile) throws IOException {
        JsonMapper jsonMapper = new JsonMapper();
        if (inputStream != null) {
            String jsonString = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return (Map<String, ?>) jsonMapper.readValue(jsonString, HashMap.class);
        } else if (schemaFile != null) {
            return (Map<String, ?>) jsonMapper.readValue(schemaFile, HashMap.class);
        }
        return null;
    }

    public boolean generateFromSchemaMap(Map<String, ?> jsonSchema, Optional<File> outputFileLocation) throws IOException {
        try {
            SourceGenerator sourceGenerator = SourceGenerators
                .findByLanguage(VisitorContext.Language.JAVA).orElse(null);
            if (sourceGenerator == null) {
                return false;
            }

            // TODO configure package as argument
            String packageName = "test";
            // TODO do not add the 'Record' in the end.
            String objectName = jsonSchema.get("title").toString() + "Record";

            File outputFile = getOutputFile(outputFileLocation,
                (packageName + ".").replace('.', File.separatorChar) + objectName);
            try (FileWriter writer = new FileWriter(outputFile)) {
                var objectDef = build(jsonSchema, packageName + "." + objectName);
                for (EnumDef enumDef : enums) {
                    sourceGenerator.write(enumDef, writer);
                }
                sourceGenerator.write(objectDef, writer);
            }
            return true;
        } catch (ProcessingException | IOException e) {
            throw e;
        }
    }

    private static File getOutputFile(Optional<File> outputFileLocation, String objectName) throws IOException {
        File outputFile = outputFileLocation.orElse(null);
        if (outputFile == null) { // default file
            outputFile = new File(objectName + ".java");
            outputFile.getParentFile().mkdirs();
        }
        if (!outputFile.exists() && !outputFile.createNewFile()) {
            throw new IOException("Could not create file " + outputFile.getAbsolutePath());
        }
        return outputFile;
    }

    private RecordDef build(Map<String, ?> jsonSchema, String builderClassName) throws IOException {
        /* TODO: decide between record vs class
        *       For now, only record def
         */
        RecordDef.RecordDefBuilder objectBuilder = RecordDef.builder(builderClassName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Serdeable.class);

        if (jsonSchema.containsKey("properties")) {
            Map<String, ?> properties = (Map<String, ?>) jsonSchema.get("properties");
            List<String> requiredProperties;
            if (jsonSchema.containsKey("required")) {
                requiredProperties = (List<String>) jsonSchema.get("required");
            } else {
                requiredProperties = new ArrayList<>();
            }
            properties.entrySet().forEach(entry ->
                addField(objectBuilder, entry.getKey(), (Map<String, Object>) entry.getValue(), requiredProperties.contains(entry.getKey())));
        }
        return objectBuilder.build();
    }

    private void addField(RecordDef.RecordDefBuilder objectBuilder, String propertyName, Map<String, Object> description, boolean isRequired) {
        TypeDef propertyType = TYPE_MAP.get(getPropertyType(description));
        if (description.containsKey("enum")) {
            propertyType = getEnumType(propertyName, description);
        }
        PropertyDef.PropertyDefBuilder propertyDef;

        if  (propertyType.equals(TypeDef.of(List.class))) {
            List<AnnotationDef> annotations = new ArrayList<>();
            propertyType = getTypeVariable(propertyName, description, annotations);
            propertyDef = PropertyDef.builder(propertyName).ofType(propertyType);

            AnnotationInfoAggregator.addAnnotations(propertyDef, annotations, isRequired);
        } else {
            propertyDef = PropertyDef.builder(propertyName).ofType(propertyType);
            AnnotationInfoAggregator.addAnnotations(propertyDef, description, propertyType, isRequired);
        }
        objectBuilder.addProperty(propertyDef.build());
    }

    private TypeDef getEnumType(String propertyName, Map<String, Object> description) {
        EnumDef.EnumDefBuilder enumBuilder = EnumDef.builder(capitalize(propertyName));
        for (Object anEnum : ((List<?>) description.get("enum"))) {
            enumBuilder.addEnumConstant(anEnum.toString());
        }
        EnumDef enumDef = enumBuilder.build();
        this.enums.add(enumDef);
        return enumDef.asTypeDef();
    }

    private TypeDef getTypeVariable(String propertyName, Map<String, Object> description, List<AnnotationDef> annotations) {
        var items = (Map<String, Object>) description.get("items");
        Class listClass = List.class;
        if (description.containsKey("uniqueItems") && description.get("uniqueItems").toString().equals("true")) {
            listClass = Set.class;
        }

        TypeDef propertyType = TYPE_MAP.get(getPropertyType(items));
        if (propertyType.equals(TypeDef.of(List.class))) {
            annotations.addAll(AnnotationInfoAggregator.getAnnotations(items, propertyType));
            propertyType = getTypeVariable(propertyName, items, annotations);
        } else {
            if (items.containsKey("enum")) {
                propertyType = getEnumType(propertyName, items);
            } else {
                propertyType = GENERIC_TYPE_MAP.get(getPropertyType(items));
            }
            annotations.addAll(AnnotationInfoAggregator.getAnnotations(items, propertyType));
        }
        // TODO: add a new implementation that would return a typedef with annotations
        return TypeDef.parameterized(listClass, propertyType);
    }

    private static String getPropertyType(Map<String, Object> description) {
        var type = description.getOrDefault("type", "object");
        String typeName;
        if (type.getClass() == ArrayList.class) {
            typeName = ((ArrayList<?>) type).get(0).toString();
        } else {
            typeName = type.toString();
        }
        return typeName;
    }
}
