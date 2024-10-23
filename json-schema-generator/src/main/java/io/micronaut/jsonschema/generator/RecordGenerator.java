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
import io.micronaut.core.io.ResourceLoader;
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
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static io.micronaut.core.util.StringUtils.capitalize;

/**
 * A generator to create Java Beans from Json Schema
 *
 * @author Elif Kurtay
 * @since 1.2
 */

@Internal
@Singleton
public final class RecordGenerator {

    private static final Map<String, TypeDef> TYPE_MAP = CollectionUtils.mapOf(new Object[]{
        "integer", TypeDef.Primitive.INT, "boolean", TypeDef.Primitive.BOOLEAN,
        "void", TypeDef.VOID, "string", TypeDef.STRING, "object", TypeDef.OBJECT,
        "number", TypeDef.Primitive.FLOAT, "null", TypeDef.OBJECT});

    private static final Map<String, Class> CLASS_MAP = CollectionUtils.mapOf(new Object[]{
        "integer", Integer.class, "boolean", Boolean.class, "string", String.class,
        "object", Object.class, "number", Float.class, "null", Object.class});

    private final ResourceLoader resourceLoader;
    private List<EnumDef> enums = new ArrayList<>();

    public RecordGenerator(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public boolean generate(File jsonFileLocation, Optional<File> outputFileLocation) throws IOException {
        try {
            SourceGenerator sourceGenerator = SourceGenerators
                .findByLanguage(VisitorContext.Language.JAVA).orElse(null);
            if (sourceGenerator == null) {
                return false;
            }

            var jsonSchema = getJsonSchema(jsonFileLocation.getPath());
            String objectName = jsonSchema.get("title").toString() + "Record";

            File outputFile = getOutputFile(outputFileLocation, objectName);
            try (FileWriter writer = new FileWriter(outputFile)) {
                var objectDef = build(jsonSchema, objectName);
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
            outputFile = new File( objectName + ".java");
        }
        if (!outputFile.exists() && !outputFile.createNewFile()) {
            throw new IOException("Could not create file " + outputFile.getAbsolutePath());
        }
        return outputFile;
    }

    private Map<String, ?> getJsonSchema(String path) throws IOException {
        JsonMapper jsonMapper = new JsonMapper();

        Optional<InputStream> jsonOptional = resourceLoader.getResourceAsStream(path);
        if (jsonOptional.isEmpty()) {
            throw new FileNotFoundException("Resource file is not found.");
        }
        String jsonString = new String(jsonOptional.get().readAllBytes(), StandardCharsets.UTF_8);
        return (Map<String, ?>) jsonMapper.readValue(jsonString, HashMap.class);
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
        String typeName = getPropertyType(description);
        boolean isEnum = description.containsKey("enum");
        TypeDef propertyType;
        if  (typeName.equals("array")) {
            // checking for multidimensional arrays
            var items = (Map<String, Object>) description.get("items");
            int dimensions = 1;
            var arrayTypeName = getPropertyType(items);
            while (arrayTypeName.equals("array")) {
                items = (Map<String, Object>) items.get("items");
                dimensions++;
                arrayTypeName = getPropertyType(items);
            }
            isEnum = items.containsKey("enum");
            description = items;

            // TODO: do the multiple dimensions
            if (description.containsKey("uniqueItems") && description.get("uniqueItems").toString().equals("true")) {
                propertyType = TypeDef.parameterized(Set.class, CLASS_MAP.get(arrayTypeName));
            } else {
                propertyType = TypeDef.parameterized(List.class, CLASS_MAP.get(arrayTypeName));
            }
            // propertyType = new TypeDef.Array(TYPE_MAP.get(arrayTypeName), dimensions, true);
        } else {
            propertyType = TYPE_MAP.get(typeName);
        }

        PropertyDef.PropertyDefBuilder propertyDef;
        if(isEnum) {
            EnumDef.EnumDefBuilder enumBuilder = EnumDef.builder(capitalize(propertyName));
            for (Object anEnum : ((List<?>) description.get("enum"))) {
                enumBuilder.addEnumConstant(anEnum.toString());
            }
            EnumDef enumDef = enumBuilder.build();
            this.enums.add(enumDef);
            propertyDef = PropertyDef.builder(propertyName)
                .ofType(enumDef.asTypeDef());
        } else {
            propertyDef = PropertyDef.builder(propertyName)
                .ofType(propertyType);
        }
        AnnotationInfoAggregator.addAnnotations(propertyDef, description, propertyType, isRequired);
        objectBuilder.addProperty(propertyDef.build());
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
