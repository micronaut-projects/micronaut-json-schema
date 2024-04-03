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

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.jsonschema.visitor.JsonSchemaConfigurationVisitor.JsonSchemaContext;
import io.micronaut.jsonschema.visitor.model.Schema;
import io.micronaut.jsonschema.visitor.model.Schema.Type;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * An aggregator for adding information from the validation annotations.
 */
public class ValidationInfoAggregator implements SchemaInfoAggregator {

    private static final String JAKARTA_VALIDATION_PREFIX = "jakarta.validation.constraints.";
    private static final String NULL_ANN = JAKARTA_VALIDATION_PREFIX + "Null";
    private static final String NULLABLE_ANN = "jakarta.annotation.Nullable";
    private static final String ASSERT_FALSE_ANN = JAKARTA_VALIDATION_PREFIX + "AssertFalse";
    private static final String ASSERT_TRUE_ANN = JAKARTA_VALIDATION_PREFIX + "AssertTrue";
    private static final String NOT_EMPTY_ANN = JAKARTA_VALIDATION_PREFIX + "NotEmpty";
    private static final String SIZE_ANN = JAKARTA_VALIDATION_PREFIX + "Size";
    private static final String NOT_BLANK_ANN = JAKARTA_VALIDATION_PREFIX + "NotBlank";
    private static final String NEGATIVE_ANN = JAKARTA_VALIDATION_PREFIX + "Negative";
    private static final String NEGATIVE_OR_ZERO_ANN = JAKARTA_VALIDATION_PREFIX + "NegativeOrZero";
    private static final String POSITIVE_ANN = JAKARTA_VALIDATION_PREFIX + "Positive";
    private static final String POSITIVE_OR_ZERO_ANN = JAKARTA_VALIDATION_PREFIX + "PositiveOrZero";
    private static final String MIN_ANN = JAKARTA_VALIDATION_PREFIX + "Min";
    private static final String MAX_ANN = JAKARTA_VALIDATION_PREFIX + "Max";
    private static final String DECIMAL_MIN_ANN = JAKARTA_VALIDATION_PREFIX + "DecimalMin";
    private static final String DECIMAL_MAX_ANN = JAKARTA_VALIDATION_PREFIX + "DecimalMax";
    private static final String PATTERN_ANN = JAKARTA_VALIDATION_PREFIX + "Pattern";
    private static final String EMAIL_ANN = JAKARTA_VALIDATION_PREFIX + "Email";
    private static final String LIST_SUFFIX = "$List";

    @Override
    public Schema addInfo(TypedElement element, Schema schema, VisitorContext visitorContext, JsonSchemaContext context) {
        ClassElement type = element.getGenericType();
        if (element.hasAnnotation(NULL_ANN + LIST_SUFFIX)) {
            schema.setType(List.of(Schema.Type.NULL));
        } else if (element.hasAnnotation(NULLABLE_ANN)) {
            schema.addType(Schema.Type.NULL);
        }

        if (schema.getType().contains(Type.BOOLEAN)) {
            if (element.hasAnnotation(ASSERT_FALSE_ANN + LIST_SUFFIX)) {
                schema.setConstValue(false);
            } else if (element.hasAnnotation(ASSERT_TRUE_ANN + LIST_SUFFIX)) {
                schema.setConstValue(true);
            }
        }

        if (type.isIterable() || type.isAssignable(Map.class)) {
            if (element.hasAnnotation(NOT_EMPTY_ANN + LIST_SUFFIX)) {
                schema.setMinItems(1);
            }
            element.getAnnotationValuesByName(SIZE_ANN).forEach(ann -> {
                ann.intValue("min").ifPresent(schema::setMinItems);
                ann.intValue("max").ifPresent(schema::setMaxItems);
            });
        } else {
            if (element.hasAnnotation(NOT_BLANK_ANN + LIST_SUFFIX)
                || element.hasAnnotation(NOT_EMPTY_ANN + LIST_SUFFIX)) {
                schema.setMinLength(1);
            }
            element.getAnnotationValuesByName(SIZE_ANN).forEach(ann -> {
                ann.intValue("min").ifPresent(schema::setMinLength);
                ann.intValue("max").ifPresent(schema::setMaxLength);
            });

            if (element.hasAnnotation(NEGATIVE_ANN + LIST_SUFFIX)) {
                schema.setExclusiveMaximum(0);
            }
            if (element.hasAnnotation(NEGATIVE_OR_ZERO_ANN + LIST_SUFFIX)) {
                schema.setMaximum(0);
            }
            if (element.hasAnnotation(POSITIVE_ANN + LIST_SUFFIX)) {
                schema.setExclusiveMinimum(0);
            }
            if (element.hasAnnotation(POSITIVE_OR_ZERO_ANN + LIST_SUFFIX)) {
                schema.setMinimum(0);
            }

            element.getAnnotationValuesByName(MIN_ANN).forEach(ann ->
                ann.intValue().ifPresent(v -> schema.setMinimum(BigDecimal.valueOf(v))));
            element.getAnnotationValuesByName(MAX_ANN).forEach(ann ->
                ann.intValue().ifPresent(v -> schema.setMaximum(BigDecimal.valueOf(v))));
            element.getAnnotationValuesByName(DECIMAL_MIN_ANN).forEach(ann -> {
                boolean exclusive = !ann.booleanValue("inclusive").orElse(true);
                BigDecimal min = ann.stringValue().map(BigDecimal::new).orElse(BigDecimal.ZERO);
                if (exclusive) {
                    schema.setExclusiveMinimum(min);
                } else {
                    schema.setMinimum(min);
                }
            });
            element.getAnnotationValuesByName(DECIMAL_MAX_ANN).forEach(ann -> {
                boolean exclusive = !ann.booleanValue("inclusive").orElse(true);
                BigDecimal max = ann.stringValue().map(BigDecimal::new).orElse(BigDecimal.ZERO);
                if (exclusive) {
                    schema.setExclusiveMaximum(max);
                } else {
                    schema.setMaximum(max);
                }
            });

            element.getAnnotationValuesByName(PATTERN_ANN).forEach(ann ->
                ann.stringValue("regexp").ifPresent(schema::setPattern));
            if (element.hasAnnotation(EMAIL_ANN + LIST_SUFFIX)) {
                schema.setFormat("idn-email");
                element.getAnnotationValuesByName(EMAIL_ANN).forEach(ann ->
                    ann.stringValue("regexp").ifPresent(schema::setPattern));
            }
        }
        return schema;
    }

}
