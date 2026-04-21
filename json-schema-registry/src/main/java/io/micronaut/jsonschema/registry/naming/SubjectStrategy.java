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
package io.micronaut.jsonschema.registry.naming;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.jsonschema.registry.model.GeneratedSchema;

/**
 * Determines the subject name used for a schema in a registry (e.g. Confluent subject).
 *
 * @since 1.0.0
 */
@Internal
public interface SubjectStrategy {

    /**
     * Compute a subject for a given generated schema.
     * @param schema The schema
     * @return non-null subject string
     */
    @NonNull
    String subjectFor(@NonNull GeneratedSchema schema);
}
