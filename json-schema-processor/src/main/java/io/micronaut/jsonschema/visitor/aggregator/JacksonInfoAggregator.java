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

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonKey;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeId;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonView;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.jsonschema.model.Schema;
import io.micronaut.jsonschema.visitor.JsonSchemaVisitor;
import io.micronaut.jsonschema.visitor.context.JsonSchemaContext;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.PropertyNamingStrategy;
import tools.jackson.databind.annotation.JsonNaming;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An aggregator for adding information from the jackson serialization annotations.
 */
@Internal
public class JacksonInfoAggregator implements SchemaInfoAggregator {

    public static final List<Class<? extends Annotation>> UNSUPPORTED_ANNOTATIONS = List.of(
        JsonAlias.class, JsonAutoDetect.class, JsonBackReference.class, JsonCreator.class,
        JsonEnumDefaultValue.class, JsonFormat.class, JsonIdentityInfo.class,
        JsonIdentityReference.class, JsonKey.class, JsonManagedReference.class, JsonRawValue.class,
        JsonRootName.class, JsonTypeId.class, JsonValue.class, JsonView.class,
        JsonFilter.class
    );

    /**
     * The default of {@code @JsonNaming}, which is not a naming strategy by itself.
     */
    private static final String NO_NAMING_STRATEGY = PropertyNamingStrategy.class.getName();

    /**
     * The type declaring the standard naming strategies as nested types.
     */
    private static final String STANDARD_NAMING_STRATEGIES = PropertyNamingStrategies.class.getName();

    private static final Map<String, PropertyNamingStrategy> NAMING_STRATEGIES = Map.of(
        "LowerCamelCaseStrategy", PropertyNamingStrategies.LOWER_CAMEL_CASE,
        "UpperCamelCaseStrategy", PropertyNamingStrategies.UPPER_CAMEL_CASE,
        "SnakeCaseStrategy", PropertyNamingStrategies.SNAKE_CASE,
        "UpperSnakeCaseStrategy", PropertyNamingStrategies.UPPER_SNAKE_CASE,
        "LowerCaseStrategy", PropertyNamingStrategies.LOWER_CASE,
        "KebabCaseStrategy", PropertyNamingStrategies.KEBAB_CASE,
        "LowerDotCaseStrategy", PropertyNamingStrategies.LOWER_DOT_CASE
    );

    /**
     * Resolves the name of a property in JSON. As in Jackson, a name set on the property wins over the naming
     * strategy set with {@code @JsonNaming} on the type or a supertype.
     *
     * @param type The type declaring the property
     * @param property The property
     * @return The name of the property in JSON
     */
    public static String getPropertyName(ClassElement type, PropertyElement property) {
        return getPropertyName(property, getNamingStrategy(findNamingStrategyType(type).orElse(null)));
    }

    private static String getPropertyName(PropertyElement property, @Nullable PropertyNamingStrategy namingStrategy) {
        Optional<String> name = property.stringValue(JsonProperty.class)
            .or(() -> property.stringValue(JsonGetter.class))
            .or(() -> property.stringValue(JsonSetter.class));
        if (name.isPresent() || namingStrategy == null) {
            return name.orElse(property.getName());
        }
        // the standard strategies only translate the name
        return namingStrategy.nameForField(null, null, property.getName());
    }

    private static Optional<String> findNamingStrategyType(ClassElement element) {
        for (ClassElement type = element; type != null; type = type.getSuperType().orElse(null)) {
            Optional<String> strategyType = type.stringValue(JsonNaming.class);
            if (strategyType.isPresent()) {
                return strategyType.map(name -> name.replace('$', '.'));
            }
        }
        return Optional.empty();
    }

    private static @Nullable PropertyNamingStrategy getNamingStrategy(@Nullable String strategyType) {
        if (strategyType == null) {
            return null;
        }
        int nested = strategyType.lastIndexOf('.');
        if (nested < 0 || !STANDARD_NAMING_STRATEGIES.equals(strategyType.substring(0, nested))) {
            return null;
        }
        return NAMING_STRATEGIES.get(strategyType.substring(nested + 1));
    }

    @Override
    public Schema addInfo(TypedElement element, Schema schema, VisitorContext visitorContext, JsonSchemaContext context) {
        ClassElement type = element.getGenericType();

        UNSUPPORTED_ANNOTATIONS.stream().filter(element::hasAnnotation).forEach(ann ->
            visitorContext.warn("Could not add annotation " + ann + " to schema as it is not supported by the JacksonInfoAggregator", element)
        );
        addSubtypeInfo(type, schema, visitorContext, context);
        addPropertyInfo(type, schema, visitorContext, context);

        return schema;
    }

    private void addPropertyInfo(ClassElement element, Schema schema, VisitorContext visitorContext, JsonSchemaContext context) {
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

        String namingStrategyType = findNamingStrategyType(element).orElse(null);
        PropertyNamingStrategy namingStrategy = getNamingStrategy(namingStrategyType);
        if (namingStrategy == null && namingStrategyType != null && !NO_NAMING_STRATEGY.equals(namingStrategyType)) {
            visitorContext.warn("Could not apply the naming strategy " + namingStrategyType + " of @JsonNaming to the schema, "
                + "as only the strategies of PropertyNamingStrategies are supported", element);
        }

        if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
            // a property may be renamed to the name another one had: the schemas are read as they were declared,
            // and the renamed ones are only added once every property left its former name
            Map<String, Schema> declaredProperties = new LinkedHashMap<>(schema.getProperties());
            Map<String, Schema> renamedProperties = new LinkedHashMap<>();
            for (PropertyElement property : element.getBeanProperties()) {
                Schema propertySchema = declaredProperties.get(property.getName());
                if (propertySchema == null) {
                    continue;
                }
                String name = getPropertyName(property, namingStrategy);
                if (property.hasAnnotation(JsonIgnore.class)
                    || property.getGenericType().hasAnnotation(JsonIgnoreType.class)
                    || (ignoreProperties != null && ignoreProperties.contains(name))
                ) {
                    schema.getProperties().remove(property.getName());
                    continue;
                }
                if (includeProperties != null && !includeProperties.contains(name)) {
                    if (!property.hasAnnotation(JsonInclude.class)) {
                        schema.getProperties().remove(property.getName());
                        continue;
                    }
                }

                if (property.hasAnnotation(JsonAnyGetter.class) || property.hasAnnotation(JsonAnySetter.class)) {
                    if (!property.getType().isAssignable(Map.class)) {
                        visitorContext.fail("Property annotated with @JsonAnyGetter must be of type Map", property);
                    }
                    schema.getProperties().remove(property.getName());
                    schema.setAdditionalProperties(propertySchema.getAdditionalProperties());
                }
                property.stringValue(JsonPropertyDescription.class)
                    .ifPresent(propertySchema::setDescription);
                if (property.hasAnnotation(JsonUnwrapped.class)) {
                    schema.getProperties().remove(property.getName());
                    Map<String, Schema> unwrappedProperties = propertySchema.getProperties();
                    if (unwrappedProperties == null && propertySchema.has$ref()) {
                        Schema resolvedSchema = context.createdSchemasByType().get(property.getGenericType().getName());
                        if (resolvedSchema == null) {
                            resolvedSchema = JsonSchemaVisitor.createTopLevelSchema(property, visitorContext, context);
                        }
                        if (resolvedSchema != null) {
                            unwrappedProperties = resolvedSchema.getProperties();
                        }
                    }
                    if (unwrappedProperties != null) {
                        renamedProperties.putAll(unwrappedProperties);
                    }
                } else if (!name.equals(property.getName())) {
                    schema.getProperties().remove(property.getName());
                    renamedProperties.put(name, propertySchema);
                }

                ValidationInfoAggregator.addRequiredPropertyInfo(name, property, schema, context);
            }
            schema.getProperties().putAll(renamedProperties);
        }

        for (MethodElement method : element.getMethods()) {
            if (method.hasAnnotation(JsonAnySetter.class) && method.getParameters().length == 2) {
                schema.setAdditionalProperties(JsonSchemaVisitor.createSchema(method.getParameters()[1], visitorContext, context));
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
        JsonTypeInfo.As as = typeInfoAnn.enumValue("include", JsonTypeInfo.As.class).orElse(As.PROPERTY);
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
                            subTypeSchema.putProperty(discriminatorName, Schema.string().setConstValue(discriminatorValue))
                                .addRequired(discriminatorName);
                        } else if (as == As.WRAPPER_OBJECT) {
                            subTypeSchema = Schema.object().putProperty(discriminatorValue, subTypeSchema);
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
