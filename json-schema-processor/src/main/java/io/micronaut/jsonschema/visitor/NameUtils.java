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
package io.micronaut.jsonschema.visitor;

import io.micronaut.core.annotation.Internal;

/**
 * A utility class for name conversions.
 */
@Internal
public class NameUtils {

    /**
     * Convert from camel case to kebab case.
     *
     * @param value The value
     * @return The converted value
     */
    public static String camelCaseToKebabCase(String value) {
        StringBuilder result = new StringBuilder();
        boolean prevNewWord = true;
        for (int i = 0; i < value.length(); ++i) {
            if (value.charAt(i) == '.') {
                continue;
            }
            if (Character.isUpperCase(value.charAt(i))) {
                if (!prevNewWord) {
                    result.append("-");
                }
                prevNewWord = true;
            } else {
                prevNewWord = false;
            }
            result.append(Character.toLowerCase(value.charAt(i)));
        }
        return result.toString();
    }

}
