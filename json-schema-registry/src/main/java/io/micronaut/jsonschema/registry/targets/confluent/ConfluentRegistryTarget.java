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
package io.micronaut.jsonschema.registry.targets.confluent;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.jsonschema.registry.api.RegistryTarget;
import io.micronaut.jsonschema.registry.api.RegistryTargetDiff;
import io.micronaut.jsonschema.registry.model.GeneratedSchema;
import jakarta.inject.Singleton;

/**
 * Confluent Schema Registry target (stub).
 * A future implementation will use a declarative Micronaut HTTP client to call the REST API.
 *
 * @since 1.0.0
 */
@Internal
@Singleton
final class ConfluentRegistryTarget implements RegistryTarget {

    @Override
    @NonNull
    public String id() {
        return "confluent";
    }

    @Override
    @NonNull
    public RegistryTargetDiff diff(@NonNull GeneratedSchema schema) {
        // Stub: assume up-to-date
        return RegistryTargetDiff.none();
    }

    @Override
    public void apply(@NonNull GeneratedSchema schema) {
        // Stub: no-op
    }
}
