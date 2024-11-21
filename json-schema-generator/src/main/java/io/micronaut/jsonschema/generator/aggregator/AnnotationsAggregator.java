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

import io.micronaut.core.annotation.Internal;
import io.micronaut.jsonschema.model.Schema;
import io.micronaut.sourcegen.model.AnnotationDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.PropertyDef;
import io.micronaut.sourcegen.model.TypeDef;

import java.util.ArrayList;
import java.util.List;

/**
 * An aggregator for adding annotation information from json schema.
 *
 * @author Elif Kurtay
 * @since 1.2
 */
@Internal
public class AnnotationsAggregator {

    private static final String NULLABLE_ANN = "jakarta.annotation.Nullable";
    private static final String JAKARTA_VALIDATION_PREFIX = "jakarta.validation.constraints.";
    private static final String NOT_NULL_ANN = JAKARTA_VALIDATION_PREFIX + "NotNull";
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

    public static void addAnnotations(PropertyDef.PropertyDefBuilder propertyDef, Schema schema, TypeDef propertyType, boolean isRequired) {
        if (isRequired) {
            propertyDef.addAnnotation(NOT_NULL_ANN);
        }
        getAnnotations(schema, propertyType).forEach(propertyDef::addAnnotation);
    }

    public static List<AnnotationDef> getAnnotations(Schema schema, TypeDef propertyType) {
        List<AnnotationDef> annotations = new ArrayList<>();
        boolean isFloat = propertyType.equals(TypeDef.Primitive.FLOAT) || propertyType.equals(ClassTypeDef.of(Float.class));
        var minAnn = isFloat ? DECIMAL_MIN_ANN : MIN_ANN;
        var maxAnn = isFloat ? DECIMAL_MAX_ANN : MAX_ANN;

        if (schema.isNullable() != null) {
            var nullableAnn = schema.isNullable() ? NULLABLE_ANN : NOT_NULL_ANN;
            annotations.add(AnnotationDef.builder(ClassTypeDef.of(nullableAnn)).build());
        }
        if (schema.getMinimum() != null) {
            var value = schema.getMinimum();
            annotations.add(AnnotationDef
                .builder(ClassTypeDef.of(minAnn))
                .addMember("value", isFloat ? value + "" : value)
                .build());
        }
        if (schema.getMaximum() != null) {
            var value = schema.getMaximum();
            annotations.add(AnnotationDef
                .builder(ClassTypeDef.of(maxAnn))
                .addMember("value", isFloat ? value + "" : value)
                .build());
        }
        if (schema.getExclusiveMinimum() != null) {
            var value = schema.getExclusiveMinimum();
            annotations.add(AnnotationDef
                .builder(ClassTypeDef.of(minAnn))
                .addMember("value", isFloat ?
                    "" + (((double) value) + EXCLUSIVE_DELTA_DOUBLE) :
                    ((int) value) + EXCLUSIVE_DELTA_INT)
                .build());
        }
        if (schema.getExclusiveMaximum() != null) {
            var value = schema.getExclusiveMaximum();
            annotations.add(AnnotationDef
                .builder(ClassTypeDef.of(maxAnn))
                .addMember("value", isFloat ?
                    "" + (((double) value) - EXCLUSIVE_DELTA_DOUBLE) :
                    ((int) value) - EXCLUSIVE_DELTA_INT)
                .build());
        }
        if (schema.getMaxLength() != null || schema.getMaxItems() != null) {
            var value = schema.getMaxLength() != null ? schema.getMaxLength() : schema.getMaxItems();
            annotations.add(AnnotationDef
                .builder(ClassTypeDef.of(SIZE_ANN))
                .addMember("max", value).build());
        }
        if (schema.getMinLength() != null || schema.getMinItems() != null) {
            var value = schema.getMinLength() != null ? schema.getMinLength() : schema.getMinItems();
            annotations.add(AnnotationDef
                .builder(ClassTypeDef.of(SIZE_ANN))
                .addMember("min", value).build());
        }
        if (schema.getPattern() != null && propertyType.equals(TypeDef.STRING)) {
            var value = schema.getPattern();
            annotations.add(AnnotationDef
                .builder(ClassTypeDef.of(PATTERN_ANN))
                .addMember("regexp", value).build());
        }
        if (schema.getPattern() != null &&
            (propertyType.equals(ClassTypeDef.of(Float.class))
                || propertyType.equals(ClassTypeDef.of(Integer.class)))) {
            var pattern = schema.getPattern();
            switch (pattern) {
                case "^[1-9][0-9]*$" -> // positive int
                    annotations.add(AnnotationDef
                        .builder(ClassTypeDef.of(MIN_ANN))
                        .addMember("value", 1)
                        .build());
                case "^\\d*\\.?\\d+$" -> // positive decimal
                    annotations.add(AnnotationDef
                        .builder(ClassTypeDef.of(DECIMAL_MIN_ANN))
                        .addMember("value", "" + EXCLUSIVE_DELTA_DOUBLE)
                        .build());
                case "^[0-9]*$", "^[0]|([1-9][0-9]*)$" -> // positive or zero int
                    annotations.add(AnnotationDef
                        .builder(ClassTypeDef.of(MIN_ANN))
                        .addMember("value", 0)
                        .build());
                case "^-\\d+$", "^-[1-9][0-9]*$" -> // negative int
                    annotations.add(AnnotationDef
                        .builder(ClassTypeDef.of(MAX_ANN))
                        .addMember("value", 0)
                        .build());
                case "^-\\d*\\.?\\d+$", "^-[1-9][0-9]*\\.?[0-9]+$" -> // negative decimal
                    annotations.add(AnnotationDef
                        .builder(ClassTypeDef.of(DECIMAL_MAX_ANN))
                        .addMember("value", "" + (0.0 -  EXCLUSIVE_DELTA_DOUBLE))
                        .build());
                case "^(-\\d+(\\.\\d+)?|0(\\.0+)?)$", "^-?(0|[1-9][0-9]{0,17})(\\.[0-9]{1,17})?([eE][+-]?[0-9]{1,9}})?$" -> // negative or zero
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
        if (schema.getFormat() != null && schema.getFormat().equals("email")) {
            annotations.add(AnnotationDef.builder(ClassTypeDef.of(EMAIL_ANN)).build());
        }
        if (schema.getConstValue() != null) {
            if (schema.getConstValue().equals("true")) {
                annotations.add(AnnotationDef.builder(ClassTypeDef.of(ASSERT_TRUE_ANN)).build());
            } else if (schema.getConstValue().equals("false")) {
                annotations.add(AnnotationDef.builder(ClassTypeDef.of(ASSERT_FALSE_ANN)).build());
            }
        }
        return annotations;
    }
}
