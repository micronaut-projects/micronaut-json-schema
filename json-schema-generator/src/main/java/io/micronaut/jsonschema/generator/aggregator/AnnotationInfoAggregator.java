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
import io.micronaut.sourcegen.model.AnnotationDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.PropertyDef;
import io.micronaut.sourcegen.model.TypeDef;

import java.util.Map;

/**
 * An aggregator for adding annotation information from json schema
 */
@Internal
public class AnnotationInfoAggregator {

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

    public static void addAnnotations(PropertyDef.PropertyDefBuilder propertyDef, Map<String, Object> schemaMap, TypeDef propertyType) {
        schemaMap.forEach((key, value) -> {
            AnnotationDef.AnnotationDefBuilder annBuilder = null;
            switch (key) {
                // check annotation related to numbers
                case "minimum":
                    annBuilder = AnnotationDef
                        .builder(ClassTypeDef.of(
                            (propertyType == TypeDef.Primitive.FLOAT) ? DECIMAL_MIN_ANN : MIN_ANN)
                        )
                        .addMember("value", value);
                    break;
                case "maximum":
                    annBuilder = AnnotationDef
                        .builder(ClassTypeDef.of(
                            (propertyType == TypeDef.Primitive.FLOAT) ? DECIMAL_MAX_ANN : MAX_ANN)
                        )
                        .addMember("value", value);
                    break;
                case "maxLength":
                    annBuilder = AnnotationDef
                        .builder(ClassTypeDef.of(SIZE_ANN))
                        .addMember("max", value);
                    break;
                case "minLength":
                    annBuilder = AnnotationDef
                        .builder(ClassTypeDef.of(SIZE_ANN))
                        .addMember("min", value);
                    break;
                case "pattern":
                    annBuilder = AnnotationDef
                        .builder(ClassTypeDef.of(PATTERN_ANN))
                        .addMember("regexp", value);
                    break;
                case "email": annBuilder = AnnotationDef
                    .builder(ClassTypeDef.of(EMAIL_ANN));
                case "const":
                    break;
                default:
                    break;
            }
            if (annBuilder != null) {
                propertyDef.addAnnotation(annBuilder.build());
            }
        });
    }
}
