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
package io.micronaut.jsonschema.generator.aggregator;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.jsonschema.generator.SourceGenerator;
import io.micronaut.jsonschema.generator.utils.GeneratorContext;
import io.micronaut.jsonschema.model.Schema;
import io.micronaut.sourcegen.model.AnnotationDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.TypeDef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.micronaut.jsonschema.generator.SourceGenerator.getInputFileName;

/**
 * An aggregator for adding annotation information from json schema.
 *
 * @author Elif Kurtay
 * @since 1.3
 */
@Internal
public class AnnotationsAggregator {
    public static final String SERDEABLE_ANN = "io.micronaut.serde.annotation.Serdeable";
    private static final String JACKSON_VALIDATION_PREFIX = "com.fasterxml.jackson.annotation.";
    public static final String JSON_ANY_GETTER_ANN = JACKSON_VALIDATION_PREFIX + "JsonAnyGetter";
    public static final String JSON_ANY_SETTER_ANN = JACKSON_VALIDATION_PREFIX + "JsonAnySetter";
    public static final String JSON_CREATOR_ANN = JACKSON_VALIDATION_PREFIX + "JsonCreator";
    public static final String JSON_VALUE_ANN = JACKSON_VALIDATION_PREFIX + "JsonValue";
    private static final String JSON_PROPERTY_ANN = JACKSON_VALIDATION_PREFIX + "JsonProperty";
    private static final String JSON_SUB_TYPES_ANN = JACKSON_VALIDATION_PREFIX + "JsonSubTypes";
    private static final String JSON_SUB_TYPES_TYPE_ANN = JSON_SUB_TYPES_ANN + ".Type";
    private static final String JSON_TYPE_INFO_ANN = JACKSON_VALIDATION_PREFIX + "JsonTypeInfo";

    private static final String NULLABLE_ANN = "jakarta.annotation.Nullable";
    private static final String JAKARTA_VALIDATION_PREFIX = "jakarta.validation.constraints.";
    public static final String NOT_NULL_ANN = JAKARTA_VALIDATION_PREFIX + "NotNull";
    private static final String ASSERT_FALSE_ANN = JAKARTA_VALIDATION_PREFIX + "AssertFalse";
    private static final String ASSERT_TRUE_ANN = JAKARTA_VALIDATION_PREFIX + "AssertTrue";
    private static final String SIZE_ANN = JAKARTA_VALIDATION_PREFIX + "Size";
    private static final String MIN_ANN = JAKARTA_VALIDATION_PREFIX + "Min";
    private static final String MAX_ANN = JAKARTA_VALIDATION_PREFIX + "Max";
    private static final String DECIMAL_MIN_ANN = JAKARTA_VALIDATION_PREFIX + "DecimalMin";
    private static final String DECIMAL_MAX_ANN = JAKARTA_VALIDATION_PREFIX + "DecimalMax";
    private static final String PATTERN_ANN = JAKARTA_VALIDATION_PREFIX + "Pattern";
    private static final String EMAIL_ANN = JAKARTA_VALIDATION_PREFIX + "Email";
    private static final int EXCLUSIVE_DELTA_INT = 1;
    private static final double EXCLUSIVE_DELTA_DOUBLE = 0.001;

    public static AnnotationDef getJsonTypeInfoAnn(String propertyName) {
        return AnnotationDef.builder(ClassTypeDef.of(JSON_TYPE_INFO_ANN))
            .addMember("use", JsonTypeInfo.Id.NAME)
            .addMember("property", propertyName)
            .build();
    }

    public static AnnotationDef getJsonPropertyAnn(String propertyName) {
        return AnnotationDef.builder(ClassTypeDef.of(JSON_PROPERTY_ANN))
            .addMember("value", propertyName)
            .build();
    }

    public static AnnotationDef getJsonSubTypesAnn(Map<String, String> mapping, GeneratorContext context) {
        List<AnnotationDef> subTypeList = mapping.entrySet()
            .stream()
            .map(entry -> AnnotationDef
                .builder(ClassTypeDef.of(JSON_SUB_TYPES_TYPE_ANN))
                .addMember("value", context.getDefinitionType(getInputFileName() + entry.getValue()))
                .addMember("name", entry.getKey())
                .build())
            .toList();
        return AnnotationDef.builder(ClassTypeDef.of(JSON_SUB_TYPES_ANN))
            .addMember("value", subTypeList)
            .build();
    }

    public static List<AnnotationDef> getAnnotations(Schema schema, TypeDef propertyType, boolean required) {
        List<AnnotationDef> annotations = new ArrayList<>();
        boolean isFloat = propertyType.equals(TypeDef.Primitive.FLOAT) || propertyType.equals(ClassTypeDef.of(Float.class));
        var minAnn = isFloat ? DECIMAL_MIN_ANN : MIN_ANN;
        var maxAnn = isFloat ? DECIMAL_MAX_ANN : MAX_ANN;

        Boolean nullable = schema.isNullable();
        if (nullable != null) {
            var nullableAnn = nullable ? NULLABLE_ANN : NOT_NULL_ANN;
            annotations.add(AnnotationDef.builder(ClassTypeDef.of(nullableAnn)).build());
        } else if (required) {
            annotations.add(AnnotationDef.builder(ClassTypeDef.of(NOT_NULL_ANN)).build());
        }
        Object minimum = schema.getMinimum();
        if (minimum != null) {
            annotations.add(AnnotationDef
                .builder(ClassTypeDef.of(minAnn))
                .addMember("value", isFloat ? minimum + "" : minimum)
                .build());
        }
        Object maximum = schema.getMaximum();
        if (maximum != null) {
            annotations.add(AnnotationDef
                .builder(ClassTypeDef.of(maxAnn))
                .addMember("value", isFloat ? maximum + "" : maximum)
                .build());
        }
        Object exclusiveMinimum = schema.getExclusiveMinimum();
        if (exclusiveMinimum instanceof Number exclusiveMinimumNumber) {
            annotations.add(AnnotationDef
                .builder(ClassTypeDef.of(minAnn))
                .addMember("value", isFloat ?
                    "" + (exclusiveMinimumNumber.doubleValue() + EXCLUSIVE_DELTA_DOUBLE) :
                    exclusiveMinimumNumber.intValue() + EXCLUSIVE_DELTA_INT)
                .build());
        }
        Object exclusiveMaximum = schema.getExclusiveMaximum();
        if (exclusiveMaximum instanceof Number exclusiveMaximumNumber) {
            annotations.add(AnnotationDef
                .builder(ClassTypeDef.of(maxAnn))
                .addMember("value", isFloat ?
                    "" + (exclusiveMaximumNumber.doubleValue() - EXCLUSIVE_DELTA_DOUBLE) :
                    exclusiveMaximumNumber.intValue() - EXCLUSIVE_DELTA_INT)
                .build());
        }
        if (schema.getMaxLength() != null || schema.getMaxItems() != null || schema.getMaxContains() != null) {
            Integer value = schema.getMaxLength() != null ? schema.getMaxLength() : schema.getMaxItems();
            value = value == null ? schema.getMaxContains() : value;
            annotations.add(AnnotationDef
                .builder(ClassTypeDef.of(SIZE_ANN))
                .addMember("max", Objects.requireNonNull(value)).build());
        }
        if (schema.getMinLength() != null || schema.getMinItems() != null || schema.getMinContains() != null) {
            Integer value = schema.getMinLength() != null ? schema.getMinLength() : schema.getMinItems();
            value = value == null ? schema.getMinContains() : value;
            annotations.add(AnnotationDef
                .builder(ClassTypeDef.of(SIZE_ANN))
                .addMember("min", Objects.requireNonNull(value)).build());
        }
        String pattern = schema.getPattern();
        if (pattern != null && propertyType.equals(TypeDef.STRING)) {
            var value = pattern;
            if (SourceGenerator.getLanguage().equals(VisitorContext.Language.GROOVY)) {
                value = value.replaceAll("\\$", "");
            }
            annotations.add(AnnotationDef
                .builder(ClassTypeDef.of(PATTERN_ANN))
                .addMember("regexp", value).build());
        }
        if (pattern != null &&
            (propertyType.equals(ClassTypeDef.of(Float.class))
                || propertyType.equals(ClassTypeDef.of(Integer.class))
                || propertyType.equals(TypeDef.Primitive.INT)
                || propertyType.equals(TypeDef.Primitive.FLOAT))) {
            switch (pattern) {
                case "^[1-9][0-9]*$" -> // positive int
                    annotations.add(AnnotationDef
                        .builder(ClassTypeDef.of(MIN_ANN))
                        .addMember("value", 1)
                        .build());
                case "^\\d*.?\\d+$", "^[1-9][0-9]*.?[0-9]+$" -> // positive decimal
                    annotations.add(AnnotationDef
                        .builder(ClassTypeDef.of(DECIMAL_MIN_ANN))
                        .addMember("value", "" + EXCLUSIVE_DELTA_DOUBLE)
                        .build());
                case "^[0-9]*$", "^[0]|([1-9][0-9]*)$" -> // positive or zero int
                    annotations.add(AnnotationDef
                        .builder(ClassTypeDef.of(MIN_ANN))
                        .addMember("value", 0)
                        .build());
                case "^-d+$", "^-[1-9][0-9]*$" -> // negative int
                    annotations.add(AnnotationDef
                        .builder(ClassTypeDef.of(MAX_ANN))
                        .addMember("value", 0)
                        .build());
                case "^-d*.?d+$", "^-[1-9][0-9]*.?[0-9]+$" -> // negative decimal
                    annotations.add(AnnotationDef
                        .builder(ClassTypeDef.of(DECIMAL_MAX_ANN))
                        .addMember("value", "" + (0.0 -  EXCLUSIVE_DELTA_DOUBLE))
                        .build());
                case "^(-d+(.d+)?|0(.0+)?)$", "^-?(0|[1-9][0-9]{0,17})(.[0-9]{1,17})?([eE][+-]?[0-9]{1,9}})?$" -> // negative or zero
                    annotations.add(AnnotationDef
                        .builder(ClassTypeDef.of(MAX_ANN))
                        .addMember("value", 0)
                        .build());
                case "^[0]|[-+]?[1-9][0-9]*$" -> {
                    break;
                }
                default -> System.err.println("Unsupported validation pattern for number: " + pattern);
            }
        }
        String format = schema.getFormat();
        if ("email".equals(format)) {
            annotations.add(AnnotationDef.builder(ClassTypeDef.of(EMAIL_ANN)).build());
        }
        Object constValue = schema.getConstValue();
        if (constValue != null && propertyType.equals(TypeDef.Primitive.BOOLEAN)) {
            if (constValue.toString().equals(StringUtils.TRUE)) {
                annotations.add(AnnotationDef.builder(ClassTypeDef.of(ASSERT_TRUE_ANN)).build());
            } else if (constValue.toString().equals(StringUtils.FALSE)) {
                annotations.add(AnnotationDef.builder(ClassTypeDef.of(ASSERT_FALSE_ANN)).build());
            }
        }
        return annotations;
    }
}
