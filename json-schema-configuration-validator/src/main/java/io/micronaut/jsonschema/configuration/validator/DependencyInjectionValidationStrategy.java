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

/**
 * Strategy used to choose which bean definitions are validated for dependency injection.
 */
public enum DependencyInjectionValidationStrategy {
    /**
     * Validate only reachable roots (for example {@code @Context}, controllers, startup listeners,
     * and beans with executable entry points) and their traversed dependencies.
     */
    REACHABLE,
    /**
     * Validate only bean definitions that originate from application classes generated in the
     * current project (typically classes loaded from directories, not dependency JARs).
     */
    APPLICATION_BEANS,
    /**
     * Validate all bean definitions available on the classpath.
     * <p>
     * This strategy can be significantly slower than {@link #REACHABLE}.
     */
    ALL_BEANS
}
