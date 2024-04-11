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
package io.micronaut.jsonschema.visitor.aggregator;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.jsonschema.visitor.JsonSchemaConfigurationVisitor.JsonSchemaContext;
import io.micronaut.jsonschema.visitor.JsonSchemaVisitor;
import io.micronaut.jsonschema.visitor.model.Schema;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An aggregator for adding information from the jackson serialization annotations.
 */
@Internal
public class JacksonInfoAggregator implements SchemaInfoAggregator {

    @Override
    public Schema addInfo(TypedElement element, Schema schema, VisitorContext visitorContext, JsonSchemaContext context) {
        ClassElement type = element.getGenericType();

        addSubtypeInfo(type, schema, visitorContext, context);
        addPropertyInfo(type, schema, visitorContext);

        return schema;
    }

    private void addPropertyInfo(ClassElement element, Schema schema, VisitorContext visitorContext) {
        if (element.hasAnnotation(JsonClassDescription.class)) {
            schema.setDescription(element.stringValue(JsonClassDescription.class).orElse(null));
        }

        Set<String> includeProperties = null;
        Set<String> ignoreProperties = null;
        if (element.hasAnnotation(JsonIncludeProperties.class)) {
            includeProperties = Arrays.stream(element.stringValues(JsonIncludeProperties.class))
                    .collect(Collectors.toSet());
        }
        if (element.hasAnnotation(JsonIgnoreProperties.class)) {
            ignoreProperties = Arrays.stream(element.stringValues(JsonIgnoreProperties.class))
                .collect(Collectors.toSet());
        }

        if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
            for (PropertyElement property : element.getBeanProperties()) {
                Schema propertySchema = schema.getProperties().get(property.getName());
                if (propertySchema == null) {
                    continue;
                }
                String name = property.stringValue(JsonProperty.class).orElse(property.getName());
                if (property.hasAnnotation(JsonIgnore.class)
                        || (ignoreProperties != null && ignoreProperties.contains(name))
                ) {
                    schema.getProperties().remove(property.getName());
                    continue;
                }
                if (includeProperties != null && !includeProperties.contains(name)) {
                    if (!element.hasAnnotation(JsonInclude.class)) {
                        schema.getProperties().remove(property.getName());
                        continue;
                    }
                }

                element.stringValue(JsonPropertyDescription.class)
                    .ifPresent(schema::setDescription);
                if (property.hasAnnotation(JsonUnwrapped.class)) {
                    schema.getProperties().remove(property.getName());
                    schema.getProperties().putAll(propertySchema.getProperties());
                } else {
                    property.stringValue(JsonProperty.class).ifPresent(newName -> {
                        schema.getProperties().remove(property.getName());
                        schema.putProperty(newName, propertySchema);
                    });
                }
            }
        }
    }

    private void addSubtypeInfo(ClassElement element, Schema schema, VisitorContext visitorContext, JsonSchemaContext context) {
        AnnotationValue<?> subTypesAnn = element.getAnnotation(JsonSubTypes.class);
        AnnotationValue<?> typeInfoAnn = element.getAnnotation(JsonTypeInfo.class);
        if (subTypesAnn == null || typeInfoAnn == null) {
            return;
        }
        JsonTypeInfo.Id id = typeInfoAnn.enumValue("use", JsonTypeInfo.Id.class).orElse(Id.NAME);
        JsonTypeInfo.As as = typeInfoAnn.enumValue("as", JsonTypeInfo.As.class).orElse(As.PROPERTY);
        String discriminatorName = typeInfoAnn.stringValue("property")
            .orElse(id.getDefaultPropertyName());

        for (AnnotationValue<?> subTypeAnn : subTypesAnn.getAnnotations("value", JsonSubTypes.Type.class)) {
            ClassElement subType = subTypeAnn.stringValue()
                .flatMap(visitorContext::getClassElement).orElse(null);
            if (subType != null) {
                Schema subTypeSchema = JsonSchemaVisitor.createSchema(subType, visitorContext, context);

                if (discriminatorName != null) {
                    String discriminatorValue = null;
                    if (id == Id.MINIMAL_CLASS) {
                        discriminatorValue = getMinimalClassName(element.getPackageName(), subType.getName());
                    } else if (id == Id.NAME) {
                        if (subTypeAnn.stringValues("names").length != 0) {
                            subTypeSchema.putProperty(discriminatorName, Schema.string().setEnumValues(
                                Arrays.stream(subTypeAnn.stringValues("names")).map(v -> (Object) v).toList()
                            ));
                        } else {
                            discriminatorValue = subTypeAnn.stringValue("name")
                                .orElse(subType.stringValue(JsonTypeName.class).orElse(subType.getSimpleName()));
                        }
                    } else {
                        discriminatorValue = subType.getName();
                    }

                    if (discriminatorValue != null) {
                        if (as == As.PROPERTY || as == As.EXISTING_PROPERTY) {
                            subTypeSchema.putProperty(discriminatorName, Schema.string().setConstValue(discriminatorValue));
                        } else if (as == As.WRAPPER_OBJECT) {
                            subTypeSchema = Schema.object().putProperty(discriminatorName, subTypeSchema);
                        } else {
                            visitorContext.warn("@JsonTypeInfo(include = " + as + ") is not supported", element);
                        }
                    }
                }

                schema.addOneOf(subTypeSchema);
            }
        }
    }

    private String getMinimalClassName(String parentClassPackage, String className) {
        if (className.startsWith(parentClassPackage)) {
            return className.substring(parentClassPackage.length());
        }
        return className;
    }

}
