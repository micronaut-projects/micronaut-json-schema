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
package io.micronaut.jsonschema.gradle;

import io.micronaut.sourcegen.annotations.GenerateGradlePlugin;

/**
 * Triggers sourcegen for the low-level Gradle task backing the JSON Schema records plugin.
 */
@GenerateGradlePlugin(
    namePrefix = "JsonSchemaRecords",
    micronautPlugin = false,
    types = GenerateGradlePlugin.Type.GRADLE_TASK,
    tasks = @GenerateGradlePlugin.GenerateGradleTask(
        namePrefix = "AbstractGenerateFromJsonSchemaSources",
        source = "io.micronaut.jsonschema.generator.records.JsonSchemaRecordsGeneration"
    )
)
final class JsonSchemaRecordsGradleSourcegenTrigger {
}
