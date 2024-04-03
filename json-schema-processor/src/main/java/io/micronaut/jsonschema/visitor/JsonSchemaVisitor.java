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
package io.micronaut.jsonschema.visitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.EnumElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.GeneratedFile;
import io.micronaut.jsonschema.JsonSchema;
import io.micronaut.jsonschema.visitor.JsonSchemaConfigurationVisitor.JsonSchemaContext;
import io.micronaut.jsonschema.visitor.aggregator.DocumentationInfoAggregator;
import io.micronaut.jsonschema.visitor.aggregator.JacksonInfoAggregator;
import io.micronaut.jsonschema.visitor.aggregator.SchemaInfoAggregator;
import io.micronaut.jsonschema.visitor.aggregator.ValidationInfoAggregator;
import io.micronaut.jsonschema.visitor.model.Schema;
import io.micronaut.jsonschema.visitor.model.Schema.Type;
import io.micronaut.jsonschema.visitor.serialization.JsonSchemaMapperFactory;

import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.micronaut.jsonschema.visitor.JsonSchemaConfigurationVisitor.JSON_SCHEMA_CONFIGURATION_PROPERTY;

/**
 * A visitor for creating JSON schemas for beans.
 * The bean must have a {@link JsonSchema} annotation.
 *
 * @since 1.0.0
 * @author Andriy Dmytruk
 */
@Internal
public final class JsonSchemaVisitor implements TypeElementVisitor<JsonSchema, Object> {

    private static final List<SchemaInfoAggregator> SCHEMA_INFO_AGGREGATORS = List.of(
        new JacksonInfoAggregator(),
        new ValidationInfoAggregator(),
        new DocumentationInfoAggregator()
    );

    @Override
    public @NonNull TypeElementVisitor.VisitorKind getVisitorKind() {
        return VisitorKind.AGGREGATING;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext visitorContext) {
        if (element.hasAnnotation(JsonSchema.class)) {
            JsonSchemaContext context = visitorContext.get(JSON_SCHEMA_CONFIGURATION_PROPERTY, JsonSchemaContext.class, null);
            if (context == null) {
                context = JsonSchemaContext.createDefault();
                visitorContext.put(JSON_SCHEMA_CONFIGURATION_PROPERTY, context);
            }
            createTopLevelSchema(element, visitorContext, context);
        }
    }

    public static Schema createTopLevelSchema(TypedElement element, VisitorContext visitorContext, JsonSchemaContext context) {
        Schema schema = context.createdSchemasByType().get(element.getGenericType().getName());
        if (schema != null) {
            return schema;
        }
        schema = new Schema();

        AnnotationValue<JsonSchema> schemaAnn = element.getGenericType().getDeclaredAnnotation(JsonSchema.class);
        if (schemaAnn != null) {
            schema.setTitle(schemaAnn.stringValue("title")
                .orElse(element.getGenericType().getSimpleName().replace('$', '.')));
            schemaAnn.stringValue("description").ifPresent(schema::setDescription);

            String uri = schemaAnn.stringValue("uri")
                .orElse("/" + NameUtils.camelCaseToKebabCase(schema.getTitle()));
            if (!uri.contains("://")) {
                if (context.baseUrl() != null) {
                    uri = context.baseUrl() + uri;
                } else {
                    visitorContext.warn("The JSON schema for type " + element.getName()
                        + " does not have a resolvable URI", element);
                }
            }

            schema.set$id(uri);
            schema.set$schema(Schema.SCHEMA_DRAFT_2022_12);
        }

        setSchemaType(element, visitorContext, context, schema);

        for (SchemaInfoAggregator aggregator: SCHEMA_INFO_AGGREGATORS) {
            schema = aggregator.addInfo(element, schema, visitorContext, context);
        }

        if (schemaAnn != null) {
            context.createdSchemasByType().put(element.getGenericType().getName(), schema);
            writeSchema(schema, element.getGenericType(), visitorContext, context);
        }
        return schema;
    }

    public static Schema createSchema(TypedElement element, VisitorContext visitorContext, JsonSchemaContext context) {
        Schema topLevelSchema = createTopLevelSchema(element, visitorContext, context);
        if (topLevelSchema.get$id() != null) {
            return Schema.reference(topLevelSchema.get$id());
        }
        return topLevelSchema;
    }

    private static void setSchemaType(TypedElement element, VisitorContext visitorContext, JsonSchemaContext context, Schema schema) {
        ClassElement type = element.getGenericType();
        if ((type.getName().equals("byte") || type.getName().equals("java.lang.Byte")) && type.isArray()) {
            // By default, it is a base 64 encoded string
            schema.addType(Type.STRING);
        } else if (type.isAssignable(Map.class)) {
            ClassElement valueType = type.getTypeArguments().get("V");
            if (valueType.getName().equals("java.lang.Object")) {
                schema.addType(Type.OBJECT);
            } else {
                schema.addType(Type.OBJECT).setAdditionalProperties(createSchema(valueType, visitorContext, context));
            }
        } else if (type.isAssignable(Set.class)) {
            schema.addType(Type.ARRAY).setItems(createSchema(type.getTypeArguments().get("E"), visitorContext, context));
        } else if (type.isAssignable(Collection.class)) {
            schema.addType(Type.ARRAY).setItems(createSchema(type.getTypeArguments().get("E"), visitorContext, context));
        } else if (!type.isPrimitive() && type.getRawClassElement() instanceof EnumElement enumElement) {
            // Enum values must be camel case
            schema.addType(Type.STRING)
                .setEnumValues(enumElement.values().stream().map(v -> (Object) v).toList());
        } else {
            switch (type.getName()) {
                case "boolean", "java.lang.Boolean" -> schema.addType(Type.BOOLEAN);
                case "int", "java.lang.Integer", "long", "java.lang.Long" -> schema.addType(Type.INTEGER);
                case "java.math.BigDecimal", "float", "java.lang.Float",
                    "double", "java.lang.Double" -> schema.addType(Type.NUMBER);
                case "java.lang.String" -> schema.addType(Type.STRING);
                case "java.time.Instant" -> schema.addType(Type.STRING);
                case "java.util.UUID" -> schema.addType(Type.STRING).setFormat("uuid");
                default -> setBeanSchemaProperties(type, visitorContext, context, schema);
            }
        }
    }

    public static void setBeanSchemaProperties(ClassElement element, VisitorContext visitorContext, JsonSchemaContext context, Schema schema) {
        schema.addType(Type.OBJECT);
        if (schema.getTitle() == null) {
            schema.setTitle(element.getSimpleName().replace('$', '.'));
        }
        context.createdSchemasByType().put(element.getGenericType().getName(), schema);
        for (PropertyElement property: element.getBeanProperties()) {
            schema.putProperty(property.getName(), createSchema(property, visitorContext, context));
        }
    }

    public static void writeSchema(Schema schema, ClassElement originatingElement, VisitorContext visitorContext, JsonSchemaContext context) {
        String fileName = getFileName(schema, context);
        String path = context.outputLocation() + "/" + fileName + ".schema.json";
        GeneratedFile specFile = visitorContext.visitMetaInfFile(path, originatingElement).orElse(null);
        if (specFile == null) {
            visitorContext.warn("Unable to get [\" " + path + "\"] file to write JSON schema", null);
        } else {
            visitorContext.info("Generating JSON schema file: " + specFile.getName());
            try (Writer writer = specFile.openWriter()) {
                ObjectMapper mapper = JsonSchemaMapperFactory.createMapper();
                mapper.writeValue(writer, schema);
            } catch (IOException e) {
                throw new RuntimeException("Failed writing JSON schema " + specFile.getName() + " file: " + e, e);
            }
        }
    }

    private static String getFileName(Schema schema, JsonSchemaContext context) {
        String id = schema.get$id();
        if (context.baseUrl() != null && id.startsWith(context.baseUrl())) {
            id = id.substring(context.baseUrl().length());
        } else if (id.contains("://")) {
            id = URI.create(id).getPath().substring(1);
            if (id.startsWith(context.outputLocation())) {
                id = id.substring(context.outputLocation().length());
            }
        }
        if (id.startsWith("/")) {
            id = id.substring(1);
        }
        return id;
    }

}
