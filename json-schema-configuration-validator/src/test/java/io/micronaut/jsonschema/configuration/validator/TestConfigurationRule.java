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

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Test-only {@link ConfigurationRule} used to verify that rules are executed and receive the
 * expected context.
 */
public final class TestConfigurationRule implements ConfigurationRule {
    static final String ENABLED_PROP = "jsonschema.test.configuration.rule.enabled";

    @Override
    public Set<ConfigurationError> validate(ConfigurationValidationContext context) {
        if (!Boolean.getBoolean(ENABLED_PROP)) {
            return Set.of();
        }

        Set<ConfigurationError> errors = new LinkedHashSet<>();

        // Example: dependent configuration.
        if (context.environment().containsProperties("jpa.default.properties")
            && !context.environment().containsProperty("datasources.default.url")) {
            errors.add(new ConfigurationError(
                "datasources.default.url",
                "Required when jpa.default.properties is set",
                null,
                null,
                null
            ));
        }

        // Prove each-property context uses the fully qualified entry prefix.
        if (context.prefix().startsWith("test.executors.") && context.prefix().endsWith(".alpha")) {
            Object nThreads = context.instanceMap().get("n-threads");
            errors.add(new ConfigurationError(
                context.prefix() + ".rule",
                "Rule ran for prefix=" + context.prefix() + ", n-threads=" + nThreads,
                null,
                null,
                null
            ));
        }

        return errors;
    }
}
