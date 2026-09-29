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
package io.micronaut.jsonschema;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A marker annotation for types generated from a JSON schema by the Micronaut JSON Schema generator.
 *
 * <p>The annotation is retained in the class files so that code coverage tools which skip types annotated
 * with an annotation whose simple name contains {@code Generated}, such as JaCoCo, exclude the generated
 * types from coverage reports.</p>
 *
 * <p>Unlike {@code io.micronaut.core.annotation.Generated}, this annotation does not stop Micronaut
 * annotation processors from visiting the type, so bean introspections are still created for the generated
 * {@code @Serdeable} types.</p>
 *
 * @since 2.3.0
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface GeneratedFromJsonSchema {
}
