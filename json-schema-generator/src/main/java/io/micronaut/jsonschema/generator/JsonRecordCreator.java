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
import java.util.HashMap;
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
            var jsonSchema = getJsonSchema(fileLocation.getPath());
            String objectName = jsonSchema.get("title").toString() + "Record";

            var objectDef = build(jsonSchema, objectName);

            SourceGenerator sourceGenerator = SourceGenerators
                .findByLanguage(VisitorContext.Language.JAVA).orElse(null);
            if (sourceGenerator == null) {
                return false;
            }
            File newFile = new File( "com.example." + objectName + ".java");
            if (!newFile.exists() && !newFile.createNewFile()) {
                throw new IOException("Could not create file " + newFile.getAbsolutePath());
            }
            try (FileWriter writer = new FileWriter(newFile)) {
                sourceGenerator.write(objectDef, writer);
            }
            return true;
        } catch (ProcessingException | IOException e) {
            throw e;
        }
    }

    public RecordDef build(Map<String, ?> jsonSchema, String builderClassName) throws IOException {
        // For now, only record def
        // TODO: decide between record vs class
        RecordDef.RecordDefBuilder objectBuilder = RecordDef.builder(builderClassName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Serdeable.class);

        addFields(jsonSchema, objectBuilder);
        return objectBuilder.build();
    }

    public Map<String, ?> getJsonSchema(String path) throws IOException {
        JsonMapper jsonMapper = new JsonMapper();

        Optional<InputStream> jsonOptional = resourceLoader.getResourceAsStream(path);
        if (jsonOptional.isEmpty()) {
            throw new FileNotFoundException("Resource file is not found.");
        }
        String jsonString = new String(jsonOptional.get().readAllBytes(), StandardCharsets.UTF_8);
        return (Map<String, ?>) jsonMapper.readValue(jsonString, HashMap.class);
    }

    private static void addFields(Map<String, ?> jsonSchema, RecordDef.RecordDefBuilder objectBuilder) {
        Map<String, ?> properties = (Map<String, ?>) jsonSchema.get("properties");
        properties.entrySet().stream()
            .forEach(entry -> {
                PropertyDef field = createField(entry.getKey(), (Map<String, Object>) entry.getValue());
                objectBuilder.addProperty(field);
            });
    }

    private static PropertyDef createField(String propertyName, Map<String, Object> description) {
        String typeName = getPropertyType(description);
        TypeDef propertyType;
        // TODO: handle enum
        if  (typeName.equals("array")) {
            // check for multidimensional arrays
            var items = (Map<String, Object>) description.get("items");
            int dimensions = 1;
            var arrayTypeName = getPropertyType(items);
            while (arrayTypeName.equals("array")) {
                items = (Map<String, Object>) items.get("items");
                dimensions++;
                arrayTypeName = getPropertyType(items);
            }
            propertyType = new TypeDef.Array(TYPE_MAP.get(arrayTypeName), dimensions, true);
        } else {
            propertyType = TYPE_MAP.get(typeName);
        }

        // TODO: add annotations and default value
        PropertyDef.PropertyDefBuilder propertyDef = PropertyDef.builder(propertyName)
            .ofType(propertyType);
        return propertyDef.build();
    }

    private static String getPropertyType(Map<String, Object> description) {
        var type = description.getOrDefault("type", "enum");
        String typeName;
        if (type.getClass() == ArrayList.class) {
            typeName = ((ArrayList<?>) type).get(0).toString();
        } else {
            typeName = type.toString();
        }
        return typeName;
    }
}
