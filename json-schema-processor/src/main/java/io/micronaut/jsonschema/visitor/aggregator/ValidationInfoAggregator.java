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

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.jsonschema.visitor.context.JsonSchemaContext;
import io.micronaut.jsonschema.visitor.model.Schema;
import io.micronaut.jsonschema.visitor.model.Schema.Type;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * An aggregator for adding information from the validation annotations.
 */
@Internal
public class ValidationInfoAggregator implements SchemaInfoAggregator {

    private static final String JAKARTA_ANNOTATION_PREFIX = "jakarta.annotation.";
    private static final String JAKARTA_VALIDATION_PREFIX = "jakarta.validation.constraints.";
    private static final String NULLABLE_ANN = JAKARTA_ANNOTATION_PREFIX + "Nullable";
    private static final String NON_NULL_ANN = JAKARTA_ANNOTATION_PREFIX + "Nonnull";
    private static final String NULL_ANN = JAKARTA_VALIDATION_PREFIX + "Null";
    private static final String ASSERT_FALSE_ANN = JAKARTA_VALIDATION_PREFIX + "AssertFalse";
    private static final String ASSERT_TRUE_ANN = JAKARTA_VALIDATION_PREFIX + "AssertTrue";
    private static final String NOT_EMPTY_ANN = JAKARTA_VALIDATION_PREFIX + "NotEmpty";
    private static final String NOT_NULL_ANN = JAKARTA_VALIDATION_PREFIX + "NotNull";
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
    private static final String DIGITS_ANN = JAKARTA_VALIDATION_PREFIX + "Digits";
    private static final String FUTURE_ANN = JAKARTA_VALIDATION_PREFIX + "Future";
    private static final String FUTURE_OR_PRESENT_ANN = JAKARTA_VALIDATION_PREFIX + "FutureOrPresent";
    private static final String PAST_ANN = JAKARTA_VALIDATION_PREFIX + "Past";
    private static final String PAST_OR_PRESENT_ANN = JAKARTA_VALIDATION_PREFIX + "PastOrPresent";

    private static final String LIST_SUFFIX = "$List";

    public static final List<String> UNSUPPORTED_ANNOTATIONS = List.of(
        FUTURE_ANN, FUTURE_OR_PRESENT_ANN, PAST_ANN, PAST_OR_PRESENT_ANN
    );

    @Override
    public Schema addInfo(TypedElement element, Schema schema, VisitorContext visitorContext, JsonSchemaContext context) {
        UNSUPPORTED_ANNOTATIONS.stream().filter(ann -> element.hasAnnotation(ann + LIST_SUFFIX)).forEach(ann ->
            visitorContext.warn("Could not add annotation " + ann + " to schema as it is not supported by the JacksonInfoAggregator", element)
        );

        addRequiredPropertiesInfo(element.getGenericType(), schema, context);

        ClassElement type = element.getGenericType();
        if (element.hasAnnotation(NULL_ANN + LIST_SUFFIX)) {
            schema.setType(List.of(Schema.Type.NULL));
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

            element.getAnnotationValuesByName(DIGITS_ANN).forEach(ann -> {
                ann.intValue("integer").ifPresent(integer -> {
                    BigDecimal value = BigDecimal.valueOf(10).pow(integer);
                    schema.setExclusiveMaximum(value);
                    schema.setExclusiveMinimum(value.negate());
                });
                ann.intValue("fraction").ifPresent(fraction -> {
                    if (fraction > 0) {
                        BigDecimal value = BigDecimal.ONE.divide(BigDecimal.TEN).pow(fraction);
                        schema.setMultipleOf(value);
                    }
                });
            });
        }
        return schema;
    }

    private void addRequiredPropertiesInfo(ClassElement element, Schema schema, JsonSchemaContext context) {
        if (schema.getProperties() != null) {
            for (Entry<String, Schema> property: schema.getProperties().entrySet()) {
                TypedElement sourceElement = property.getValue().getSourceElement();
                if (context.strictMode() && !sourceElement.hasAnnotation(NON_NULL_ANN)) {
                    schema.addRequired(property.getKey());
                } else if (sourceElement.isPrimitive()
                        || sourceElement.hasAnnotation(NOT_NULL_ANN + LIST_SUFFIX)
                        || sourceElement.hasAnnotation(NON_NULL_ANN)
                ) {
                    schema.addRequired(property.getKey());
                }
            }
        }
    }

}
