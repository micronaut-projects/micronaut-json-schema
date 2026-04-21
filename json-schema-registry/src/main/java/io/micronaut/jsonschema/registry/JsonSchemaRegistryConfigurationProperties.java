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
package io.micronaut.jsonschema.registry;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;

/**
 * Default configuration properties for the JSON Schema Registry orchestration.
 *
 * @since 1.0.0
 */
@ConfigurationProperties(JsonSchemaRegistryConfiguration.PREFIX)
@Internal
final class JsonSchemaRegistryConfigurationProperties implements JsonSchemaRegistryConfiguration {

    private boolean enabled = false;
    private boolean dryRun = false;
    private boolean failFast = true;
    @NonNull
    private String subjectStrategy = "type-simple";
    @NonNull
    private String domainPrefix = "";

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enable or disable the orchestration.
     * @param enabled True to enable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean isDryRun() {
        return dryRun;
    }

    /**
     * If true the orchestrator will only compute actions and skip applying them.
     * @param dryRun True to enable dry-run mode
     */
    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    @Override
    public boolean isFailFast() {
        return failFast;
    }

    /**
     * If true the orchestrator will fail the startup on errors (when enabled).
     * @param failFast True to fail-fast
     */
    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    @Override
    @NonNull
    public String getSubjectStrategy() {
        return subjectStrategy;
    }

    /**
     * Subject naming strategy id (e.g. type-simple, type-fqn).
     * @param subjectStrategy Strategy id
     */
    public void setSubjectStrategy(@NonNull String subjectStrategy) {
        this.subjectStrategy = subjectStrategy;
    }

    @Override
    @NonNull
    public String getDomainPrefix() {
        return domainPrefix;
    }

    /**
     * Optional prefix to use for Oracle 23ai JSON domain names.
     * @param domainPrefix Prefix (may be empty)
     */
    public void setDomainPrefix(@NonNull String domainPrefix) {
        this.domainPrefix = domainPrefix;
    }
}
