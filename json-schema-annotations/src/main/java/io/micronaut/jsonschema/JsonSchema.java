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

/**
 * An annotation that signifies that json schema should be created for the object.
 * The JSON schema will attempt to mimic the way this object would be serialized.
 *
 * @since 1.0.0
 * @author Andriy Dmytruk
 */
public @interface JsonSchema {

    /**
     * The title of the JSON schema.
     * By default, the class name will be used.
     *
     * @return The title
     */
    String title() default "";

    /**
     * The description of the JSON schema.
     * By default, javadoc of the object will be used.
     *
     * @return The description
     */
    String description() default "";

    /**
     * The schema's relative or absolute URI.
     * The default will create the URI based on class name and configured base URI.
     *
     * @return The URI
     */
    String uri() default "";

}
