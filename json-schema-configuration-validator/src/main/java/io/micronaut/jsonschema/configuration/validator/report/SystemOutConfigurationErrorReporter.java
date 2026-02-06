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
package io.micronaut.jsonschema.configuration.validator.report;

import io.micronaut.jsonschema.configuration.validator.ConfigurationError;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Set;

/**
 * Reports errors to {@code System.err}.
 */
public final class SystemOutConfigurationErrorReporter implements ConfigurationErrorReporter {
    @Override
    public void report(Set<ConfigurationError> errors) throws IOException {
        PrintWriter writer = new PrintWriter(System.err);
        for (ConfigurationError error : errors) {
            writer.println(error.property());
            writer.println("  " + error.message());
            if (error.originLocation() != null) {
                writer.println("  origin: " + error.originLocation());
            }
            if (error.rawPropertyName() != null) {
                writer.println("  raw: " + error.rawPropertyName());
            }
            if (error.rawValue() != null) {
                writer.println("  value: " + error.rawValue());
            }
            writer.println();
        }
        writer.flush();
    }
}
