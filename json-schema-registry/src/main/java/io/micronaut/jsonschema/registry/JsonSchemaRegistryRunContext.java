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
package io.micronaut.jsonschema.registry;

/**
 * Per-thread reconciliation run diagnostics.
 *
 * @since 2.2.0
 */
final class JsonSchemaRegistryRunContext implements AutoCloseable {
    private static final ThreadLocal<String> RUN_ID = new ThreadLocal<>();

    private final String previousRunId;

    private JsonSchemaRegistryRunContext(String runId) {
        this.previousRunId = RUN_ID.get();
        RUN_ID.set(runId);
    }

    static JsonSchemaRegistryRunContext open(String runId) {
        return new JsonSchemaRegistryRunContext(runId);
    }

    static String runId() {
        String runId = RUN_ID.get();
        return runId == null ? "none" : runId;
    }

    @Override
    public void close() {
        if (previousRunId == null) {
            RUN_ID.remove();
        } else {
            RUN_ID.set(previousRunId);
        }
    }
}
