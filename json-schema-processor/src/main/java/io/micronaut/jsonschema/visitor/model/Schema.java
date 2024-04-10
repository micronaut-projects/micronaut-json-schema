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
package io.micronaut.jsonschema.visitor.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.core.annotation.Internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A JSON schema.
 */
@Internal
public final class Schema {

    public static final String SCHEMA_DRAFT_2022_12 = "https://json-schema.org/draft/2020-12/schema";

    public static final String THIS_SCHEMA_REF = "#";
    public static final String DEF_SCHEMA_REF_PREFIX = "#/$defs/";

    private String $schema;
    private String $id;
    private String $ref;

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
    private List<Object> contains;

    private List<String> required;

    private Schema additionalProperties;

    private List<Schema> oneOf;

    public String getTitle() {
        return title;
    }

    public Schema setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public Schema setDescription(String description) {
        this.description = description;
        return this;
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

    public List<Object> getEnumValues() {
        return enumValues;
    }

    public Schema setEnumValues(List<Object> enumValues) {
        this.enumValues = enumValues;
        return this;
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

    public Object getDefaultValue() {
        return defaultValue;
    }

    public Schema setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
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

    public List<Object> getContains() {
        return contains;
    }

    public Schema setContains(List<Object> contains) {
        this.contains = contains;
        return this;
    }

    public List<String> getRequired() {
        return required;
    }

    public Schema setRequired(List<String> required) {
        this.required = required;
        return this;
    }

    public Schema getAdditionalProperties() {
        return additionalProperties;
    }

    public Schema setAdditionalProperties(Schema additionalProperties) {
        this.additionalProperties = additionalProperties;
        return this;
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
