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
package io.micronaut.jsonschema.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.annotation.JsonSerialize;
import io.micronaut.core.annotation.Internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * A JSON schema.
 */
@Internal
public final class Schema {

    /**
     * A true schema is a schema that would be valid for any instance.
     * This is also equivalent to just an empty schema, as it has no restrictions then.
     */
    public static final Schema TRUE = new Schema();

    /**
     * A false schema is a schema that would be invalid for any instance.
     * This is also equivalent to not true.
     */
    public static final Schema FALSE = new Schema().setNot(TRUE);

    public static final String THIS_SCHEMA_REF = "#";
    public static final String DEF_SCHEMA_REF_PREFIX = "#/$defs/";
    public static final String ONE_OF_SCHEMA_REF_PREFIX = "#/oneOf/";

    @JsonProperty("$schema")
    private @Nullable String $schema;
    @JsonProperty("$id")
    private @Nullable String $id;
    @JsonProperty("$ref")
    private @Nullable String $ref;

    private @Nullable SchemaDiscriminator discriminator;

    @JsonProperty("$defs")
    @JsonAlias("definitions")
    private @Nullable Map<String, Schema> $defs;

    private @Nullable String title;
    private @Nullable String description;

    /**
     * The supported types of the schema.
     */
    @JsonSerialize(using = TypeListSerializer.class)
    private @Nullable List<Type> type;

    private @Nullable String format;
    @JsonProperty("const")
    private @Nullable Object constValue;
    @JsonProperty("enum")
    private @Nullable List<Object> enumValues;
    private @Nullable Schema items;
    private @Nullable Map<String, Schema> properties;

    @JsonProperty("defaultValue")
    @JsonAlias("default")
    private @Nullable Object defaultValue;
    private @Nullable Boolean nullable;
    private @Nullable Boolean deprecated;
    private @Nullable Boolean readOnly;
    private @Nullable Boolean writeOnly;
    private @Nullable List<Object> examples;

    private @Nullable Object multipleOf;
    private @Nullable Object maximum;
    private @Nullable Object minimum;
    private @Nullable Object exclusiveMaximum;
    private @Nullable Object exclusiveMinimum;

    private @Nullable Integer maxLength;
    private @Nullable Integer minLength;
    private @Nullable String pattern;

    private @Nullable Integer maxItems;
    private @Nullable Integer minItems;
    private @Nullable Boolean uniqueItems;

    /**
     * The "contains" keyword ensures that at least one element in an array is valid
     * against the specified sub-schema. If "minContains" is 0, the array is valid
     * even if no elements match.
     *
     * @see <a href="https://json-schema.org/understanding-json-schema/reference/array#contains/">JSON Schema</a> for more details.
     */
    private @Nullable Schema contains;
    private @Nullable Integer maxContains;
    private @Nullable Integer minContains;

    private @Nullable List<String> required;

    private @Nullable Schema additionalProperties;

    private @Nullable List<Schema> oneOf;
    private @Nullable List<Schema> allOf;
    private @Nullable List<Schema> anyOf;

    private @Nullable Schema not;

    @Nullable
    public String getTitle() {
        return title;
    }

    public Schema setTitle(@Nullable String title) {
        this.title = title;
        return this;
    }

    public boolean hasTitle() {
        return title != null;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public Schema setDescription(@Nullable String description) {
        this.description = description;
        return this;
    }

    public boolean hasDescription() {
        return description != null;
    }

    @Nullable
    public List<Type> getType() {
        return type;
    }

    public Schema setType(@Nullable List<Type> type) {
        this.type = type;
        return this;
    }

    public Schema addType(Type type) {
        if (this.type == null) {
            this.type = new ArrayList<>();
        }
        this.type.add(type);
        return this;
    }

    public boolean hasType() {
        return type != null && !type.isEmpty();
    }

    @Nullable
    public String getFormat() {
        return format;
    }

    public Schema setFormat(@Nullable String format) {
        this.format = format;
        return this;
    }

    @Nullable
    public Object getConstValue() {
        return constValue;
    }

    public Schema setConstValue(@Nullable Object constValue) {
        this.constValue = constValue;
        return this;
    }

    public boolean hasConstValue() {
        return constValue != null;
    }

    @Nullable
    public List<Object> getEnumValues() {
        return enumValues;
    }

    public Schema setEnumValues(@Nullable List<Object> enumValues) {
        this.enumValues = enumValues;
        return this;
    }

    public boolean isEnum() {
        return enumValues != null;
    }

    @Nullable
    public Schema getItems() {
        return items;
    }

    public Schema setItems(@Nullable Schema items) {
        this.items = items;
        return this;
    }

    @Nullable
    public Map<String, Schema> getProperties() {
        return properties;
    }

    public Schema setProperties(@Nullable Map<String, Schema> properties) {
        this.properties = properties;
        return this;
    }

    public Schema putProperty(String name, Schema property) {
        if (properties == null) {
            properties = new LinkedHashMap<>();
        }
        properties.put(name, property);
        return this;
    }

    public boolean hasProperties() {
        return properties != null;
    }

    @Nullable
    public Object getDefaultValue() {
        return defaultValue;
    }

    public Schema setDefaultValue(@Nullable Object defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    public boolean hasDefaultValue() {
        return defaultValue != null;
    }

    @Nullable
    public Boolean isNullable() {
        return nullable;
    }

    public Schema setNullable(boolean nullable) {
        this.nullable = nullable;
        return this;
    }

    @Nullable
    public Boolean isDeprecated() {
        return deprecated;
    }

    public Schema setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
        return this;
    }

    @Nullable
    public Boolean isReadOnly() {
        return readOnly;
    }

    public Schema setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
        return this;
    }

    @Nullable
    public Boolean isWriteOnly() {
        return writeOnly;
    }

    public Schema setWriteOnly(boolean writeOnly) {
        this.writeOnly = writeOnly;
        return this;
    }

    @Nullable
    public List<Object> getExamples() {
        return examples;
    }

    public Schema setExamples(@Nullable List<Object> examples) {
        this.examples = examples;
        return this;
    }

    @Nullable
    public Object getMultipleOf() {
        return multipleOf;
    }

    public Schema setMultipleOf(@Nullable Object multipleOf) {
        this.multipleOf = multipleOf;
        return this;
    }

    @Nullable
    public Object getMaximum() {
        return maximum;
    }

    public Schema setMaximum(@Nullable Object maximum) {
        this.maximum = maximum;
        return this;
    }

    @Nullable
    public Object getMinimum() {
        return minimum;
    }

    public Schema setMinimum(@Nullable Object minimum) {
        this.minimum = minimum;
        return this;
    }

    @Nullable
    public Object getExclusiveMaximum() {
        return exclusiveMaximum;
    }

    public Schema setExclusiveMaximum(@Nullable Object exclusiveMaximum) {
        this.exclusiveMaximum = exclusiveMaximum;
        return this;
    }

    @Nullable
    public Object getExclusiveMinimum() {
        return exclusiveMinimum;
    }

    public Schema setExclusiveMinimum(@Nullable Object exclusiveMinimum) {
        this.exclusiveMinimum = exclusiveMinimum;
        return this;
    }

    @Nullable
    public Integer getMaxLength() {
        return maxLength;
    }

    public Schema setMaxLength(@Nullable Integer maxLength) {
        this.maxLength = maxLength;
        return this;
    }

    @Nullable
    public Integer getMinLength() {
        return minLength;
    }

    public Schema setMinLength(@Nullable Integer minLength) {
        this.minLength = minLength;
        return this;
    }

    @Nullable
    public String getPattern() {
        return pattern;
    }

    public Schema setPattern(@Nullable String pattern) {
        this.pattern = pattern;
        return this;
    }

    @Nullable
    public Integer getMaxItems() {
        return maxItems;
    }

    public Schema setMaxItems(@Nullable Integer maxItems) {
        this.maxItems = maxItems;
        return this;
    }

    @Nullable
    public Integer getMinItems() {
        return minItems;
    }

    public Schema setMinItems(@Nullable Integer minItems) {
        this.minItems = minItems;
        return this;
    }

    @Nullable
    public Boolean isUniqueItems() {
        return uniqueItems;
    }

    public Schema setUniqueItems(boolean uniqueItems) {
        this.uniqueItems = uniqueItems;
        return this;
    }

    @Nullable
    public Integer getMaxContains() {
        return maxContains;
    }

    public Schema setMaxContains(@Nullable Integer maxContains) {
        this.maxContains = maxContains;
        return this;
    }

    @Nullable
    public Integer getMinContains() {
        return minContains;
    }

    public Schema setMinContains(@Nullable Integer minContains) {
        this.minContains = minContains;
        return this;
    }

    @Nullable
    public Schema getContains() {
        return contains;
    }

    public Schema setContains(@Nullable Schema contains) {
        this.contains = contains;
        return this;
    }

    @Nullable
    public List<String> getRequired() {
        return required;
    }

    public Schema setRequired(@Nullable List<String> required) {
        if (this.required != null && !this.required.isEmpty()) {
            this.required.addAll(Objects.requireNonNull(required));
        } else {
            this.required = required;
        }
        return this;
    }

    public Schema addRequired(String requiredProperty) {
        if (required == null) {
            required = new ArrayList<>();
        }
        this.required.add(requiredProperty);
        return this;
    }

    public boolean hasRequired() {
        return required != null && !required.isEmpty();
    }

    @Nullable
    public Schema getAdditionalProperties() {
        return additionalProperties;
    }

    public Schema setAdditionalProperties(@Nullable Schema additionalProperties) {
        this.additionalProperties = additionalProperties;
        return this;
    }

    public boolean hasAdditionalProperties() {
        return additionalProperties != null && !additionalProperties.equals(FALSE);
    }

    @Nullable
    public List<Schema> getOneOf() {
        return oneOf;
    }

    public Schema setOneOf(@Nullable List<Schema> oneOf) {
        this.oneOf = oneOf;
        return this;
    }

    public Schema addOneOf(Schema one) {
        if (oneOf == null) {
            oneOf = new ArrayList<>();
        }
        oneOf.add(one);
        return this;
    }

    public boolean hasOneOf() {
        return oneOf != null;
    }

    @Nullable
    public List<Schema> getAllOf() {
        return allOf;
    }

    public Schema setAllOf(@Nullable List<Schema> allOf) {
        this.allOf = allOf;
        mergeAllOf();
        return this;
    }

    public Schema addAllOf(Schema one) {
        if (allOf == null) {
            allOf = new ArrayList<>();
        }
        allOf.add(one);
        return this;
    }

    public boolean hasAllOf() {
        return allOf != null;
    }

    public void mergeAllOf() {
        var thisAllOff = this.allOf;
        Objects.requireNonNull(thisAllOff).forEach(this::merge);
    }

    @Nullable
    public List<Schema> getAnyOf() {
        return anyOf;
    }

    public Schema setAnyOf(@Nullable List<Schema> anyOf) {
        this.anyOf = anyOf;
        return this;
    }

    public Schema addAnyOf(Schema one) {
        if (anyOf == null) {
            anyOf = new ArrayList<>();
        }
        anyOf.add(one);
        return this;
    }

    public boolean hasAnyOf() {
        return anyOf != null;
    }

    @SuppressWarnings("MethodName")
    @Nullable
    public String get$schema() {
        return $schema;
    }

    @SuppressWarnings({"MethodName", "ParameterName"})
    public Schema set$schema(@Nullable String $schema) {
        this.$schema = $schema;
        return this;
    }

    @SuppressWarnings("MethodName")
    @Nullable
    public String get$id() {
        return $id;
    }

    @SuppressWarnings({"MethodName", "ParameterName"})
    public Schema set$id(@Nullable String $id) {
        this.$id = $id;
        return this;
    }

    @SuppressWarnings("MethodName")
    @Nullable
    public String get$ref() {
        return $ref;
    }

    @SuppressWarnings({"MethodName", "ParameterName"})
    public Schema set$ref(@Nullable String $ref) {
        this.$ref = $ref;
        return this;
    }

    @SuppressWarnings("MethodName")
    public boolean has$ref() {
        return $ref != null;
    }

    @SuppressWarnings("MethodName")
    @Nullable
    public Map<String, Schema> get$defs() {
        return $defs;
    }

    @SuppressWarnings({"MethodName", "ParameterName"})
    public Schema set$defs(@Nullable Map<String, Schema> $defs) {
        this.$defs = $defs;
        return this;
    }

    @SuppressWarnings({"MethodName", "ParameterName"})
    public Schema put$def(String key, Schema $def) {
        Objects.requireNonNull($defs).put(key, $def);
        return this;
    }

    @SuppressWarnings("MethodName")
    public boolean has$defs() {
        return $defs != null;
    }

    @Nullable
    public SchemaDiscriminator getDiscriminator() {
        return discriminator;
    }

    public void setDiscriminator(@Nullable SchemaDiscriminator discriminator) {
        this.discriminator = discriminator;
    }

    public boolean hasDiscriminator() {
        return discriminator != null;
    }

    public static Schema string() {
        return new Schema().addType(Type.STRING);
    }

    public static Schema number() {
        return new Schema().addType(Type.NUMBER);
    }

    public static Schema integer() {
        return new Schema().addType(Type.INTEGER);
    }

    public static Schema object() {
        return new Schema().addType(Type.OBJECT);
    }

    public static Schema array() {
        return new Schema().addType(Type.ARRAY);
    }

    public static Schema bool() {
        return new Schema().addType(Type.BOOLEAN);
    }

    public static Schema reference(String id) {
        return new Schema().set$ref(id);
    }

    @Nullable
    public Schema getNot() {
        return not;
    }

    public Schema setNot(@Nullable Schema not) {
        this.not = not;
        return this;
    }

    /**
     * Merges the properties of the current schema with those of another schema.
     * This method combines various attributes such as `$schema`, `$id`, `$ref`,
     * discriminator, $defs, titles, types, constraints, and validation rules
     * (e.g., `allOf`, `anyOf`, `oneOf`) from the provided schema into the current schema.
     *
     * If a property exists in both schemas, the value from the provided schema (`other`)
     * is merged or replaces the existing value in the current schema based on the type
     * of the property. In cases where collections are involved, unique values are added.
     *
     * The method is mainly used for merging the schemas inside an `allOf` into one.
     *
     * @param other the schema to merge with the current schema
     * @return the current schema with merged properties
     */
    public Schema merge(@Nullable Schema other) {
        if (other == null) {
            return this;
        }

        // Merge basic properties
        if (other.$schema != null) {
            this.$schema = other.$schema;
        }
        if (other.$id != null) {
            this.$id = other.$id;
        }
        if (other.$ref != null) {
            this.$ref = other.$ref;
        }

        // Merge discriminator
        if (other.discriminator != null) {
            if (this.discriminator == null) {
                this.discriminator = other.discriminator;
            } else {
                this.discriminator = this.discriminator.merge(other.discriminator);
            }
        }

        // Merge $defs
        if (other.$defs != null) {
            if (this.$defs == null) {
                this.$defs = new HashMap<>();
            }
            this.$defs.putAll(other.$defs);
        }

        // Merge title
        if (other.title != null) {
            this.title = other.title;
        }

        // Merge description
        if (other.description != null) {
            this.description = other.description;
        }

        // Merge types
        if (other.type != null) {
            if (this.type == null) {
                this.type = new ArrayList<>();
            }
            for (Type typeItem : other.type) {
                if (!this.type.contains(typeItem)) {
                    this.type.add(typeItem);
                }
            }
        }

        // Merge format
        if (other.format != null) {
            this.format = other.format;
        }

        // Merge constValue
        if (other.constValue != null) {
            this.constValue = other.constValue;
        }

        // Merge enumValues
        if (other.enumValues != null) {
            if (this.enumValues == null) {
                this.enumValues = new ArrayList<>();
            }
            for (Object enumItem : other.enumValues) {
                if (!this.enumValues.contains(enumItem)) {
                    this.enumValues.add(enumItem);
                }
            }
        }

        // Merge items
        if (other.items != null) {
            if (this.items == null) {
                this.items = other.items;
            } else {
                this.items.merge(other.items);
            }
        }

        // Merge properties
        if (other.properties != null) {
            other.properties.forEach(this::putProperty);
        }

        // Merge defaultValue
        if (other.defaultValue != null) {
            this.defaultValue = other.defaultValue;
        }

        // Merge nullable
        if (other.nullable != null) {
            this.nullable = other.nullable;
        }

        // Merge deprecated
        if (other.deprecated != null) {
            this.deprecated = other.deprecated;
        }

        // Merge readOnly
        if (other.readOnly != null) {
            this.readOnly = other.readOnly;
        }

        // Merge writeOnly
        if (other.writeOnly != null) {
            this.writeOnly = other.writeOnly;
        }

        // Merge examples
        if (other.examples != null) {
            if (this.examples == null) {
                this.examples = new ArrayList<>();
            }
            for (Object example : other.examples) {
                if (!this.examples.contains(example)) {
                    this.examples.add(example);
                }
            }
        }

        // Merge numerical constraints
        if (other.multipleOf != null) {
            this.multipleOf = other.multipleOf;
        }
        if (other.maximum != null) {
            this.maximum = other.maximum;
        }
        if (other.minimum != null) {
            this.minimum = other.minimum;
        }
        if (other.exclusiveMaximum != null) {
            this.exclusiveMaximum = other.exclusiveMaximum;
        }
        if (other.exclusiveMinimum != null) {
            this.exclusiveMinimum = other.exclusiveMinimum;
        }

        // Merge length constraints
        if (other.maxLength != null) {
            this.maxLength = other.maxLength;
        }
        if (other.minLength != null) {
            this.minLength = other.minLength;
        }
        if (other.pattern != null) {
            this.pattern = other.pattern;
        }

        // Merge item count constraints
        if (other.maxItems != null) {
            this.maxItems = other.maxItems;
        }
        if (other.minItems != null) {
            this.minItems = other.minItems;
        }
        if (other.uniqueItems != null) {
            this.uniqueItems = other.uniqueItems;
        }
        if (other.maxContains != null) {
            this.maxContains = other.maxContains;
        }
        if (other.minContains != null) {
            this.minContains = other.minContains;
        }

        // Merge contains
        if (other.contains != null) {
            if (this.contains == null) {
                this.contains = other.contains;
            } else {
                this.contains.merge(other.contains);
            }
        }

        // Merge required
        if (other.required != null) {
            if (this.required == null) {
                this.required = new ArrayList<>();
            }
            for (String requiredItem : other.required) {
                if (!this.required.contains(requiredItem)) {
                    this.addRequired(requiredItem);
                }
            }
        }

        // Merge additionalProperties
        if (other.additionalProperties != null) {
            if (this.additionalProperties == null) {
                this.additionalProperties = other.additionalProperties;
            } else {
                this.additionalProperties.merge(other.additionalProperties);
            }
        }

        // Merge oneOf
        if (other.oneOf != null) {
            if (this.oneOf == null) {
                this.oneOf = new ArrayList<>();
            }
            this.oneOf.addAll(other.oneOf);
        }

        // Merge allOf
        if (other.allOf != null) {
            if (this.allOf == null) {
                this.allOf = new ArrayList<>();
            }
            this.allOf.addAll(other.allOf);
        }

        // Merge anyOf
        if (other.anyOf != null) {
            if (this.anyOf == null) {
                this.anyOf = new ArrayList<>();
            }
            this.anyOf.addAll(other.anyOf);
        }

        // Merge not
        if (other.not != null) {
            if (this.not == null) {
                this.not = other.not;
            } else {
                this.not.merge(other.not);
            }
        }
        return this;
    }


    /**
     * The type of schema exactly matching a primitive JSON type.
     */
    public enum Type {
        /** An ordered list of instances. */
        ARRAY,
        /** A "true" or "false" value. */
        BOOLEAN,
        /** A JSON "null" value. */
        NULL,
        /** An integer. */
        INTEGER,
        /** An arbitrary-precision, base-10 decimal number value. */
        NUMBER,
        /** An unordered set of properties mapping a string to an instance. */
        OBJECT,
        /** A string of Unicode code points. */
        STRING;

        @JsonValue
        String value() {
            return name().toLowerCase(Locale.ENGLISH);
        }

        @JsonCreator
        static Type fromString(String value) {
            return valueOf(value.toUpperCase(Locale.ENGLISH));
        }
    }

    /**
     * Discriminator defines a property that can be used to distinguish between schemas that are defined as subtypes in code. It is not a standard JSON schema annotation, but is commonly used because it is defined in OpenAPI.
     *
     * @see <a href="https://swagger.io/specification/#discriminator-object">OpenAPI Discriminator Object</a>
     * @param propertyName The discriminator property name
     * @param mapping The mapping between property value and Java schemas, where the map value is a JSON Schema reference
     */
    public record SchemaDiscriminator(
        String propertyName,
        Map<String, String> mapping
    ) {
        // returns a new SchemaDiscriminator by merging two.
        public SchemaDiscriminator merge(SchemaDiscriminator other) {
            String mergedPropertyName = propertyName;
            // Merge propertyName
            if (other.propertyName != null) {
                mergedPropertyName = other.propertyName;
            }

            Map<String, String> mergedMapping = mapping;
            // Merge mapping
            if (other.mapping != null) {
                if (mapping == null) {
                    mergedMapping = new HashMap<>();
                }
                mergedMapping.putAll(other.mapping);
            }
            return new SchemaDiscriminator(mergedPropertyName, mergedMapping);
        }
    }

}
