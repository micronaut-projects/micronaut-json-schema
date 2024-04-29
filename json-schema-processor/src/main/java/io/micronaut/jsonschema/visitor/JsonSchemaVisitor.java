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
import io.micronaut.jsonschema.visitor.aggregator.DocumentationInfoAggregator;
import io.micronaut.jsonschema.visitor.aggregator.JacksonInfoAggregator;
import io.micronaut.jsonschema.visitor.aggregator.SchemaInfoAggregator;
import io.micronaut.jsonschema.visitor.aggregator.ValidationInfoAggregator;
import io.micronaut.jsonschema.visitor.context.JsonSchemaContext;
import io.micronaut.jsonschema.visitor.model.Schema;
import io.micronaut.jsonschema.visitor.model.Schema.Type;
import io.micronaut.jsonschema.visitor.serialization.JsonSchemaMapperFactory;

import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.util.*;

import static io.micronaut.jsonschema.visitor.context.JsonSchemaContext.JSON_SCHEMA_CONTEXT_PROPERTY;

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
    private static final String SUFFIX = ".schema.json";
    private static final String SLASH = "/";

    @Override
    public @NonNull TypeElementVisitor.VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public Set<String> getSupportedOptions() {
        return JsonSchemaContext.getParameters();
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext visitorContext) {
        if (element.hasAnnotation(JsonSchema.class)) {
            JsonSchemaContext context = visitorContext.get(JSON_SCHEMA_CONTEXT_PROPERTY, JsonSchemaContext.class, null);
            if (context == null) {
                context = JsonSchemaContext.createDefault(visitorContext.getOptions());
                visitorContext.put(JSON_SCHEMA_CONTEXT_PROPERTY, context);
            }
            context.currentOriginatingElements().clear();
            Schema schema = createTopLevelSchema(element, visitorContext, context);
            writeSchema(schema, element.getGenericType(), visitorContext, context);
        }
    }

    /**
     * A method for creating a JSON schema. The schema will always a top-level schema and
     * never a reference.
     *
     * @param element The element
     * @param visitorContext The visitor context
     * @param context The JSON schema creation context
     * @return The schema
     */
    public static Schema createTopLevelSchema(TypedElement element, VisitorContext visitorContext, JsonSchemaContext context) {
        Schema schema = context.createdSchemasByType().get(element.getGenericType().getName());
        if (schema != null) {
            context.currentOriginatingElements().add(element.getGenericType());
            return schema;
        }
        schema = new Schema();

        AnnotationValue<JsonSchema> schemaAnn = element.getGenericType().getDeclaredAnnotation(JsonSchema.class);
        if (schemaAnn != null) {
            schema.setTitle(schemaAnn.stringValue("title")
                .orElse(element.getGenericType().getSimpleName().replace('$', '.')));
            schemaAnn.stringValue("description").ifPresent(schema::setDescription);
            schema.set$id(createSchemaId(element, schemaAnn, visitorContext, context));
            schema.set$schema(context.draft().getDraftUrl());
        }

        setSchemaType(element, visitorContext, context, schema);

        for (SchemaInfoAggregator aggregator: SCHEMA_INFO_AGGREGATORS) {
            schema = aggregator.addInfo(element, schema, visitorContext, context);
        }

        if (schemaAnn != null) {
            context.createdSchemasByType().put(element.getGenericType().getName(), schema);
        }
        return schema;
    }

    /**
     * A method for creating a property of the schema. In case the property references
     * another schema, it will create a reference.
     *
     * @param element The element
     * @param visitorContext The visitor context
     * @param context The JSON schema creation context
     * @return The schema
     */
    public static Schema createSchema(TypedElement element, VisitorContext visitorContext, JsonSchemaContext context) {
        if (!(element instanceof ClassElement) && element.hasAnnotation(JsonSchema.class)) {
            // The annotation is on a property
            String ref = createSchemaId(element, element.getAnnotation(JsonSchema.class), visitorContext, context);
            return Schema.reference(ref);
        } else if (element.getGenericType().hasAnnotation(JsonSchema.class)) {
            // The annotation is on the type
            String ref;
            if (context.createdSchemasByType().containsKey(element.getGenericType().getName())) {
                ref = context.createdSchemasByType().get(element.getGenericType().getName()).get$id();
            } else {
                ref = createSchemaId(element, element.getGenericType().getAnnotation(JsonSchema.class), visitorContext, context);
            }
            context.currentOriginatingElements().add(element.getGenericType());
            return Schema.reference(ref);
        } else {
            return createTopLevelSchema(element, visitorContext, context);
        }
    }

    private static String createSchemaId(
        TypedElement element, AnnotationValue<JsonSchema> schemaAnn,
        VisitorContext visitorContext, JsonSchemaContext context
    ) {
        String title = schemaAnn.stringValue("title")
            .orElse(element.getGenericType().getSimpleName().replace('$', '.'));

        Optional<String> uriOptional = schemaAnn.stringValue("uri");
        String uri;
        if (uriOptional.isPresent()) {
            uri = uriOptional.get();
            if (!uri.contains("://")) {
                uri = uri + SUFFIX;
            }
        } else {
            uri = SLASH + NameUtils.camelCaseToKebabCase(title) + SUFFIX;
        }
        if (!uri.contains("://")) {
            if (context.baseUrl() != null) {
                uri = context.baseUrl() + uri;
            } else {
                visitorContext.warn("The JSON schema for type " + element.getName()
                    + " does not have a resolvable URI", element);
            }
        }
        return uri;
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
        } else if (type.isAssignable(Collection.class)) {
            schema.addType(Type.ARRAY).setItems(createSchema(type.getTypeArguments().get("E"), visitorContext, context));
            if (type.isAssignable(Set.class)) {
                schema.setUniqueItems(true);
            }
        } else if (!type.isPrimitive() && type.getRawClassElement() instanceof EnumElement enumElement) {
            // Enum values must be camel case
            schema.addType(Type.STRING)
                .setEnumValues(enumElement.values().stream().map(v -> (Object) v).toList());
            context.currentOriginatingElements().add(enumElement);
        } else {
            switch (type.getName()) {
                case "boolean", "java.lang.Boolean" -> schema.addType(Type.BOOLEAN);
                case "int", "java.lang.Integer", "long", "java.lang.Long",
                    "short", "java.lang.Short", "byte", "java.lang.Byte" -> schema.addType(Type.INTEGER);
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
        context.currentOriginatingElements().add(element);
        if (schema.getTitle() == null) {
            schema.setTitle(element.getSimpleName().replace('$', '.'));
        }
        if (context.strictMode()) {
            schema.setAdditionalProperties(Schema.FALSE);
        }
        context.createdSchemasByType().put(element.getGenericType().getName(), schema);
        for (PropertyElement property: element.getBeanProperties()) {
            schema.putProperty(property.getName(), createSchema(property, visitorContext, context));
        }
    }

    public static void writeSchema(Schema schema, ClassElement originatingElement, VisitorContext visitorContext, JsonSchemaContext context) {
        String fileName = getFileName(schema, context);
        String path = context.outputLocation() + SLASH + fileName;
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
        } else if (id.contains(":" + SLASH + SLASH)) {
            id = URI.create(id).getPath().substring(1);
            if (id.startsWith(context.outputLocation())) {
                id = id.substring(context.outputLocation().length());
            }
        }
        if (id.startsWith(SLASH)) {
            id = id.substring(1);
        }
        return id;
    }

}
