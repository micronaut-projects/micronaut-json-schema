/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.jsonschema.registry.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * The configuration of the registry.
 *
 * @param alias The alias of the registry
 * @param normalize Whether to normalize the schema
 * @param compatibility The compatibility level
 * @param compatibilityGroup The compatibility group
 *                           (used for compatibility checks)
 * @param defaultMetadata The default metadata
 * @param overrideMetadata The override metadata
 * @param defaultRuleSet The default rule set
 * @param overrideRuleSet The override rule set
 */
@Introspected
@Serdeable
public record ConfigResponse(
    String alias,
    boolean normalize,
    @JsonAlias("compatibilityLevel") CompatibilityLevel compatibility,
    String compatibilityGroup,
    Object defaultMetadata,
    Object overrideMetadata,
    Object defaultRuleSet,
    Object overrideRuleSet
) {
}
