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

import io.micronaut.core.annotation.Introspected;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Describes one dependency-injection validation failure.
 *
 * @param rootBean The root reachable bean where validation started
 * @param bean The missing, disabled, or circular bean type involved in the failure
 * @param message Human-readable error detail
 * @param injectionPoint Injection point description (constructor, field, or method)
 * @param disabledReason Optional reason reported by Micronaut for disabled beans
 * @param failingPath Graph path from root bean to the failure
 * @param snippet Best-effort snippet representing the failing injection point
 * @param snippetLanguage Snippet language identifier
 */
@Introspected
public record DependencyInjectionError(
    String rootBean,
    String bean,
    String message,
    @Nullable String injectionPoint,
    @Nullable String disabledReason,
    List<String> failingPath,
    @Nullable String snippet,
    @Nullable String snippetLanguage
) {
    /**
     * Creates an immutable dependency-injection error.
     */
    public DependencyInjectionError {
        failingPath = failingPath == null ? List.of() : List.copyOf(failingPath);
    }
}
