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
package io.micronaut.jsonschema;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * An annotation that signals that the object was generated from json schema.
 *
 * @since 1.5.0
 * @author Elif Kurtay
 */
@Target({ ElementType.TYPE })
public @interface GeneratedFromSchema {

    /**
     * The title of the JSON schema file used to generate the object.
     *
     * @return The fileName
     */
    String fileName() default "";

}
