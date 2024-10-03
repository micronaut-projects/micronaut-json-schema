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
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.sourcegen.generator.SourceGenerator;
import io.micronaut.sourcegen.generator.SourceGenerators;
import io.micronaut.sourcegen.model.*;

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ...
 *
 * @author Elif Kurtay
 * @since 1.2
 */

@Internal
public final class JsonRecordCreator {

    private static final Map<String, TypeDef> TYPE_MAP = CollectionUtils.mapOf(new Object[]{
        "integer", TypeDef.Primitive.INT, "boolean", TypeDef.Primitive.BOOLEAN,
        "void", TypeDef.VOID, "string", TypeDef.STRING, "object", TypeDef.OBJECT,
        "number", TypeDef.Primitive.FLOAT, "null", TypeDef.OBJECT});

    ResourceLoader resourceLoader;

    public JsonRecordCreator(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public boolean generate(File fileLocation) throws IOException {
        try {
            var objectDef = build(fileLocation);

            // TODO: why is it returning null??
            SourceGenerator sourceGenerator = SourceGenerators
                .findByLanguage(VisitorContext.Language.JAVA).orElse(null);
            if (sourceGenerator == null) {
                return false;
            }
            sourceGenerator.write(objectDef, new FileWriter(fileLocation));
            return true;
        } catch (ProcessingException | IOException e) {
            throw e;
        }
    }

    public RecordDef build(File fileLocation) throws IOException {
        var jsonSchema = getJsonSchema(fileLocation.getPath());

        String objectName = jsonSchema.get("title").toString() + "Record";
        String builderClassName = "com.example." + objectName;

        // For now, only record def
        // TODO:
        //      - decide between record vs class
        RecordDef.RecordDefBuilder objectBuilder = RecordDef.builder(builderClassName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Serdeable.class);

        addFields(jsonSchema, objectBuilder);
        return objectBuilder.build();
    }

    private Map<String, ?> getJsonSchema(String path) throws IOException {
        JsonMapper jsonMapper = new JsonMapper();

        Optional<InputStream> jsonOptional = resourceLoader.getResourceAsStream(path);
        if (jsonOptional.isEmpty()) {
            throw new FileNotFoundException("Resource file is not found.");
        }
        String jsonString = new String(jsonOptional.get().readAllBytes(), StandardCharsets.UTF_8);
        return (Map<String, ?>) jsonMapper.readValue(jsonString, Map.class);
    }

    private static void addFields(Map<String, ?> jsonSchema, RecordDef.RecordDefBuilder objectBuilder) {
        Map<String, ?> properties = (Map<String, ?>) jsonSchema.get("properties");
        for (Map.Entry<String, ?> entry : properties.entrySet()) {
            String key = entry.getKey();
            Map<String, Object> value = (Map<String, Object>) entry.getValue();
            PropertyDef field = createField(key, value);
            objectBuilder.addProperty(field);
        }
    }

    private static PropertyDef createField(String propertyName, Map<String, Object> description) {
        ArrayList<String> typeName = (ArrayList<String>) description.getOrDefault("type", List.of("object"));
        TypeDef propertyType;
        if  (typeName.get(0).equals("array")) {
            // TODO: check for multidimensional arrays
            ArrayList<String> arrayTypeName = (ArrayList<String>) ((Map<String, ?>) description.get("items")).get("type");
            propertyType = new TypeDef.Array(TYPE_MAP.get(arrayTypeName.get(0)), 1, true);
        } else {
            propertyType = TYPE_MAP.get(typeName.get(0));
        }

        // TODO: add annotations
        PropertyDef.PropertyDefBuilder propertyDef = PropertyDef.builder(propertyName)
            .ofType(propertyType);

        /* TODO: default value
        try {
            beanProperty.stringValue(Bindable.class, "defaultValue").ifPresent(defaultValue ->
                fieldDef.initializer(ExpressionDef.constant(beanProperty.getType(), fieldType, defaultValue))
            );
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid or unsupported default value specified: " + beanProperty.stringValue(Bindable.class, "defaultValue").orElse(null));
        }
         */
        return propertyDef.build();
    }
}
