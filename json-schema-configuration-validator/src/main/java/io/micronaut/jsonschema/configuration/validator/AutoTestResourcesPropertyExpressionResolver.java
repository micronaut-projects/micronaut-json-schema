/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.env.PropertyExpressionResolver;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.value.PropertyResolver;

import java.util.Optional;

/**
 * Provides placeholder fallback values for Micronaut Test Resources expressions.
 */
@Internal
public final class AutoTestResourcesPropertyExpressionResolver implements PropertyExpressionResolver {
    static final String PREFIX = "auto.test.resources.";
    private static final String DUMMY_TEXT_VALUE = "test-resource";

    @Override
    public <T> Optional<T> resolve(
        PropertyResolver propertyResolver,
        ConversionService conversionService,
        String expression,
        Class<T> requiredType
    ) {
        if (!expression.startsWith(PREFIX)) {
            return Optional.empty();
        }
        if (requiredType.isAssignableFrom(String.class)) {
            return Optional.of(requiredType.cast(DUMMY_TEXT_VALUE));
        }

        Optional<T> converted = conversionService.convert(DUMMY_TEXT_VALUE, requiredType);
        if (converted.isPresent()) {
            return converted;
        }

        converted = conversionService.convert("0", requiredType);
        if (converted.isPresent()) {
            return converted;
        }

        return conversionService.convert("false", requiredType);
    }
}
