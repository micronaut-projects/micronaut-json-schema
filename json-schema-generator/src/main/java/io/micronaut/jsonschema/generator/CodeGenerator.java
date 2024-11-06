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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.jsonschema.generator.aggregator.AnnotationsAggregator;
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.micronaut.core.util.StringUtils.capitalize;
import static io.micronaut.jsonschema.generator.aggregator.TypeAggregator.getCamelCaseName;
import static io.micronaut.jsonschema.generator.aggregator.TypeAggregator.getConstantName;
import static io.micronaut.jsonschema.generator.aggregator.TypeAggregator.getEnumType;
import static io.micronaut.jsonschema.generator.aggregator.TypeAggregator.getTypeDef;
import static io.micronaut.jsonschema.generator.aggregator.TypeAggregator.getTypeDefFromJson;

/**
 * A generator to create Java Beans from Json Schema.
 *
 * @author Elif Kurtay
 * @since 1.2
 */

@Internal
@Singleton
public final class CodeGenerator {

    public File generate(InputStream inputStream, VisitorContext.Language language, Path outputPath, String packageName, String fileName) throws IOException {
        var jsonSchema = getJsonSchema(inputStream, null);
        return generateFromSchemaMap(jsonSchema, language, getOutputFile(outputPath, packageName, fileName));
    }

    public boolean generate(File jsonFileLocation, VisitorContext.Language language, Path outputPath, String packageName) throws IOException {
        var jsonSchema = getJsonSchema(null, jsonFileLocation);
        // TODO define file name
        String fileName = capitalize(getCamelCaseName(jsonSchema.get("title").toString())) + ".java";
        File output = generateFromSchemaMap(jsonSchema, language, getOutputFile(outputPath, packageName, fileName));
        return output.exists();
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

    private static File getOutputFile(Path outputPath, String packageName, String fileName) throws IOException {
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

    private File generateFromSchemaMap(Map<String, ?> jsonSchema, VisitorContext.Language language, File outputFile) throws IOException {
        try {
            SourceGenerator sourceGenerator = SourceGenerators
                .findByLanguage(language).orElse(null);
            if (sourceGenerator == null) {
                return null;
            }

            String className = outputFile.getName().substring(0, outputFile.getName().lastIndexOf('.'));

            try (FileWriter writer = new FileWriter(outputFile)) {
                if (jsonSchema.containsKey("enum")) {
                    var objectDef = buildEnum(jsonSchema, className);
                    sourceGenerator.write(objectDef, writer);
                } else {
                    var objectDef = buildRecord(jsonSchema, className);
                    sourceGenerator.write(objectDef, writer);
                }
            }
            return outputFile;
        } catch (ProcessingException | IOException e) {
            throw e;
        }
    }

    public static EnumDef buildEnum(Map<String, ?> jsonSchema, String builderClassName) {
        EnumDef.EnumDefBuilder enumBuilder = EnumDef.builder(capitalize(builderClassName))
            .addModifiers(Modifier.PUBLIC);
        boolean isComplexEnum = false;
        LinkedHashMap<ExpressionDef.Constant, ExpressionDef> cases = new LinkedHashMap<>();
        for (Object anEnum : ((List<?>) jsonSchema.get("enum"))) {
            String constName = getConstantName(anEnum.toString());
            if (constName.equals(anEnum.toString())) {
                enumBuilder.addEnumConstant(constName);
            } else {
                enumBuilder.addEnumConstant(constName, ExpressionDef.constant(anEnum.toString()));
                cases.put(ExpressionDef.constant(anEnum.toString()), new VariableDef.Constant(TypeDef.THIS, constName));
                isComplexEnum = true;
            }
        }
        if (isComplexEnum) {
            enumBuilder.addField(FieldDef.builder("name")
                    .ofType(TypeDef.STRING)
                    .addModifiers(Modifier.PUBLIC)
                    .build())
                .addAllFieldsConstructor(Modifier.PRIVATE)
                .addMethod(MethodDef.builder("getName")
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(JsonValue.class)
                    .returns(TypeDef.STRING)
                    .build((aThis, parameters) ->
                        aThis.field("name", TypeDef.STRING).returning()))
                .addMethod(MethodDef.builder("statusOf")
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(JsonCreator.class)
                    .returns(TypeDef.THIS)
                    .addParameter("name", TypeDef.STRING)
                    .build((aThis, parameters) ->
                        parameters.get(0).asExpressionSwitch(TypeDef.STRING, cases).returning()
                    ));
        }
        if (jsonSchema.containsKey("properties")) {
            Map<String, ?> properties = (Map<String, ?>) jsonSchema.get("properties");
            List<String> requiredProperties;
            if (jsonSchema.containsKey("required")) {
                requiredProperties = (List<String>) jsonSchema.get("required");
            } else {
                requiredProperties = new ArrayList<>();
            }
            properties.entrySet().forEach(entry ->
                addField(enumBuilder, entry.getKey(), (Map<String, Object>) entry.getValue(), requiredProperties.contains(entry.getKey())));
        }
        return enumBuilder.build();
    }

    private RecordDef buildRecord(Map<String, ?> jsonSchema, String builderClassName) throws IOException {
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

    private static void addField(ObjectDefBuilder objectBuilder, String propertyName, Map<String, Object> description, boolean isRequired) {
        String name = getCamelCaseName(propertyName);

        TypeDef propertyType = getTypeDefFromJson(description);
        if (description.containsKey("enum")) {
            propertyType = getEnumType(objectBuilder, name, description);
        }
        PropertyDef.PropertyDefBuilder propertyDef = PropertyDef.builder(name);
        if  (propertyType.equals(TypeDef.of(List.class))) {
            propertyType = getTypeDef(objectBuilder, propertyName, description);
            propertyDef.ofType(propertyType);
            AnnotationsAggregator.addAnnotations(propertyDef, description, TypeDef.of(List.class), isRequired);
        } else {
            propertyDef.ofType(propertyType);
            AnnotationsAggregator.addAnnotations(propertyDef, description, propertyType, isRequired);
        }

        if (!name.equals(propertyName)) {
            AnnotationDef annotationDef = AnnotationDef.builder(JsonProperty.class).addMember("value", propertyName).build();
            propertyDef.addAnnotation(annotationDef);
        }
        objectBuilder.addProperty(propertyDef.build());
    }
}
