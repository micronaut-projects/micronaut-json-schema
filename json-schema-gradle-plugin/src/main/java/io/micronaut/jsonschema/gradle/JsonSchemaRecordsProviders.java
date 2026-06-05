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

import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;

/**
 * Provider-family configuration for schema records generation.
 *
 * @since 2.0.0
 */
public abstract class JsonSchemaRecordsProviders {

    private final OracleProviderConfiguration oracle;

    /**
     * @param objects Gradle object factory
     */
    @Inject
    public JsonSchemaRecordsProviders(ObjectFactory objects) {
        oracle = objects.newInstance(OracleProviderConfiguration.class);
    }

    /**
     * @return Oracle provider-family configuration.
     */
    public OracleProviderConfiguration getOracle() {
        return oracle;
    }

    /**
     * Configure Oracle provider-family settings.
     *
     * @param action The configuration action
     */
    public void oracle(Action<? super OracleProviderConfiguration> action) {
        action.execute(oracle);
    }
}
