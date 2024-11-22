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
import io.micronaut.core.annotation.Internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    private String $schema;
    private String $id;
    private String $ref;

    private Schema discriminator;
    private String propertyName;
    private HashMap<String, String> mapping;

    @JsonProperty("$defs")
    @JsonAlias("definitions")
    private Map<String, Schema> $defs;

    private String title;
    private String description;

    /**
     * The supported types of the schema.
     */
    private List<Type> type;
    private String format;
    @JsonProperty("const")
    private Object constValue;
    @JsonProperty("enum")
    private List<Object> enumValues;
    private Schema items;
    private Map<String, Schema> properties;

    private Object defaultValue;
    private Boolean nullable;
    private Boolean deprecated;
    private Boolean readOnly;
    private Boolean writeOnly;
    private List<Object> examples;

    private Object multipleOf;
    private Object maximum;
    private Object minimum;
    private Object exclusiveMaximum;
    private Object exclusiveMinimum;

    private Integer maxLength;
    private Integer minLength;
    private String pattern;

    private Integer maxItems;
    private Integer minItems;
    private Boolean uniqueItems;
    private Integer maxContains;
    private Integer minContains;
    private Schema contains;

    private List<String> required;

    private Schema additionalProperties;

    private List<Schema> oneOf;
    private List<Schema> allOf;
    private List<Schema> anyOf;

    private Schema not;

    public String getTitle() {
        return title;
    }

    public Schema setTitle(String title) {
        this.title = title;
        return this;
    }

    public boolean hasTitle() {
        return title != null;
    }

    public String getDescription() {
        return description;
    }

    public Schema setDescription(String description) {
        this.description = description;
        return this;
    }

    public boolean hasDescription() {
        return description != null;
    }

    public List<Type> getType() {
        return type;
    }

    public Schema setType(List<Type> type) {
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
        return type != null;
    }

    public String getFormat() {
        return format;
    }

    public Schema setFormat(String format) {
        this.format = format;
        return this;
    }

    public Object getConstValue() {
        return constValue;
    }

    public Schema setConstValue(Object constValue) {
        this.constValue = constValue;
        return this;
    }

    public boolean hasConstValue() {
        return constValue != null;
    }

    public List<Object> getEnumValues() {
        return enumValues;
    }

    public Schema setEnumValues(List<Object> enumValues) {
        this.enumValues = enumValues;
        return this;
    }

    public boolean isEnum() {
        return enumValues != null;
    }

    public Schema getItems() {
        return items;
    }

    public Schema setItems(Schema items) {
        this.items = items;
        return this;
    }

    public Map<String, Schema> getProperties() {
        return properties;
    }

    public Schema setProperties(Map<String, Schema> properties) {
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

    public Object getDefaultValue() {
        return defaultValue;
    }

    public Schema setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    public Boolean isNullable() {
        return nullable;
    }

    public Schema setNullable(boolean nullable) {
        this.nullable = nullable;
        return this;
    }

    public Boolean isDeprecated() {
        return deprecated;
    }

    public Schema setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
        return this;
    }

    public Boolean isReadOnly() {
        return readOnly;
    }

    public Schema setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
        return this;
    }

    public Boolean isWriteOnly() {
        return writeOnly;
    }

    public Schema setWriteOnly(boolean writeOnly) {
        this.writeOnly = writeOnly;
        return this;
    }

    public List<Object> getExamples() {
        return examples;
    }

    public Schema setExamples(List<Object> examples) {
        this.examples = examples;
        return this;
    }

    public Object getMultipleOf() {
        return multipleOf;
    }

    public Schema setMultipleOf(Object multipleOf) {
        this.multipleOf = multipleOf;
        return this;
    }

    public Object getMaximum() {
        return maximum;
    }

    public Schema setMaximum(Object maximum) {
        this.maximum = maximum;
        return this;
    }

    public Object getMinimum() {
        return minimum;
    }

    public Schema setMinimum(Object minimum) {
        this.minimum = minimum;
        return this;
    }

    public Object getExclusiveMaximum() {
        return exclusiveMaximum;
    }

    public Schema setExclusiveMaximum(Object exclusiveMaximum) {
        this.exclusiveMaximum = exclusiveMaximum;
        return this;
    }

    public Object getExclusiveMinimum() {
        return exclusiveMinimum;
    }

    public Schema setExclusiveMinimum(Object exclusiveMinimum) {
        this.exclusiveMinimum = exclusiveMinimum;
        return this;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public Schema setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
        return this;
    }

    public Integer getMinLength() {
        return minLength;
    }

    public Schema setMinLength(Integer minLength) {
        this.minLength = minLength;
        return this;
    }

    public String getPattern() {
        return pattern;
    }

    public Schema setPattern(String pattern) {
        this.pattern = pattern;
        return this;
    }

    public Integer getMaxItems() {
        return maxItems;
    }

    public Schema setMaxItems(Integer maxItems) {
        this.maxItems = maxItems;
        return this;
    }

    public Integer getMinItems() {
        return minItems;
    }

    public Schema setMinItems(Integer minItems) {
        this.minItems = minItems;
        return this;
    }

    public Boolean isUniqueItems() {
        return uniqueItems;
    }

    public Schema setUniqueItems(boolean uniqueItems) {
        this.uniqueItems = uniqueItems;
        return this;
    }

    public Integer getMaxContains() {
        return maxContains;
    }

    public Schema setMaxContains(Integer maxContains) {
        this.maxContains = maxContains;
        return this;
    }

    public Integer getMinContains() {
        return minContains;
    }

    public Schema setMinContains(Integer minContains) {
        this.minContains = minContains;
        return this;
    }

    public Schema getContains() {
        return contains;
    }

    public Schema setContains(Schema contains) {
        this.contains = contains;
        return this;
    }

    public List<String> getRequired() {
        return required;
    }

    public Schema setRequired(List<String> required) {
        if (this.required != null && !this.required.isEmpty()) {
            this.required.addAll(required);
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

    public Schema getAdditionalProperties() {
        return additionalProperties;
    }

    public Schema setAdditionalProperties(Schema additionalProperties) {
        this.additionalProperties = additionalProperties;
        return this;
    }

    public boolean hasAdditionalProperties() {
        return additionalProperties != null;
    }

    public List<Schema> getOneOf() {
        return oneOf;
    }

    public Schema setOneOf(List<Schema> oneOf) {
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

    public List<Schema> getAllOf() {
        return allOf;
    }

    public Schema setAllOf(List<Schema> allOf) {
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
        thisAllOff.forEach(this::merge);
    }

    public List<Schema> getAnyOf() {
        return anyOf;
    }

    public Schema setAnyOf(List<Schema> anyOf) {
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

    public String get$schema() {
        return $schema;
    }

    public Schema set$schema(String $schema) {
        this.$schema = $schema;
        return this;
    }

    public String get$id() {
        return $id;
    }

    public Schema set$id(String $id) {
        this.$id = $id;
        return this;
    }

    public String get$ref() {
        return $ref;
    }

    public Schema set$ref(String $ref) {
        this.$ref = $ref;
        return this;
    }

    public boolean has$ref() {
        return $ref != null;
    }

    public Map<String, Schema> get$defs() {
        return $defs;
    }

    public Schema set$defs(Map<String, Schema> $defs) {
        this.$defs = $defs;
        return this;
    }

    public Schema put$def(String key, Schema $def) {
        $defs.put(key, $def);
        return this;
    }

    public boolean has$defs() {
        return $defs != null;
    }

    public Schema getDiscriminator() {
        return discriminator;
    }

    public void setDiscriminator(Schema discriminator) {
        this.discriminator = discriminator;
    }

    public boolean hasDiscriminator() {
        return discriminator != null;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    public HashMap<String, String> getMapping() {
        return mapping;
    }

    public void setMapping(HashMap<String, String> mapping) {
        this.mapping = mapping;
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

    public Schema getNot() {
        return not;
    }

    public Schema setNot(Schema not) {
        this.not = not;
        return this;
    }

    public Schema merge(Schema other) {
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
                this.discriminator.merge(other.discriminator);
            }
        }

        // Merge propertyName
        if (other.propertyName != null) {
            this.propertyName = other.propertyName;
        }

        // Merge mapping
        if (other.mapping != null) {
            if (this.mapping == null) {
                this.mapping = new HashMap<>();
            }
            this.mapping.putAll(other.mapping);
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

}
