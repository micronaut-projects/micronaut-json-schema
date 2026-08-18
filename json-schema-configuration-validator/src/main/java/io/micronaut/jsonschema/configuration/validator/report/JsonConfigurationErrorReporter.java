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

import io.micronaut.core.type.Argument;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.json.JsonMapper;
import io.micronaut.jsonschema.configuration.validator.ConfigurationError;
import io.micronaut.jsonschema.configuration.validator.DependencyInjectionError;
import io.micronaut.serde.annotation.Serdeable;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reports errors as JSON.
 */
public final class JsonConfigurationErrorReporter implements ConfigurationErrorReporter {
    private final JsonMapper jsonMapper;
    private final OutputStream output;

    /**
     * @param jsonMapper The JSON mapper
     * @param output The output stream
     */
    public JsonConfigurationErrorReporter(JsonMapper jsonMapper, OutputStream output) {
        this.jsonMapper = jsonMapper;
        this.output = output;
    }

    @Override
    public void report(Set<ConfigurationError> errors, Set<DependencyInjectionError> dependencyInjectionErrors) throws IOException {
        List<JsonConfigurationError> view = new ArrayList<>(errors.size());
        for (ConfigurationError error : errors) {
            view.add(new JsonConfigurationError(
                error.property(),
                error.type(),
                error.message(),
                error.originLocation(),
                error.rawPropertyName(),
                error.rawValue(),
                error.lineNumber()
            ));
        }
        List<JsonDependencyInjectionError> diView = new ArrayList<>(dependencyInjectionErrors.size());
        for (DependencyInjectionError error : dependencyInjectionErrors) {
            diView.add(new JsonDependencyInjectionError(
                error.injectionPoint(),
                shortName(error.bean()),
                formatDetails(error.message(), error.disabledReason()),
                error.failingPath(),
                error.snippet(),
                error.snippetLanguage()
            ));
        }
        String json = jsonMapper.writeValueAsString(Argument.of(JsonReport.class), new JsonReport(view, diView));
        output.write(json.getBytes(StandardCharsets.UTF_8));
        output.write('\n');
        output.flush();
    }

    private static String shortName(@Nullable String typeName) {
        return typeName == null ? "" : NameUtils.getShortenedName(typeName);
    }

    private static String formatDetails(String message, @Nullable String disabledReason) {
        if (disabledReason == null || disabledReason.isBlank()) {
            return message;
        }
        return message + " | Disabled: " + disabledReason;
    }

    @Serdeable
    private record JsonReport(
        List<JsonConfigurationError> configurationErrors,
        List<JsonDependencyInjectionError> dependencyInjectionErrors
    ) {
    }

    @Serdeable
    private record JsonConfigurationError(
        String property,
        ConfigurationError.Type type,
        String message,
        @Nullable String originLocation,
        @Nullable String rawPropertyName,
        @Nullable Object rawValue,
        int lineNumber
    ) {
    }

    @Serdeable
    private record JsonDependencyInjectionError(
        @Nullable String injectionPoint,
        String bean,
        String details,
        List<String> failingPath,
        @Nullable String snippet,
        @Nullable String snippetLanguage
    ) {
    }
}
