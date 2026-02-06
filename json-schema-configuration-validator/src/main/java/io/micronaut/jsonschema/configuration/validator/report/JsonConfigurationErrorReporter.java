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

import io.micronaut.json.JsonMapper;
import io.micronaut.jsonschema.configuration.validator.ConfigurationError;
import io.micronaut.serde.annotation.Serdeable;
import org.jspecify.annotations.NonNull;

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
    public JsonConfigurationErrorReporter(@NonNull JsonMapper jsonMapper, @NonNull OutputStream output) {
        this.jsonMapper = jsonMapper;
        this.output = output;
    }

    @Override
    public void report(Set<ConfigurationError> errors) throws IOException {
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
        String json = jsonMapper.writeValueAsString(view);
        output.write(json.getBytes(StandardCharsets.UTF_8));
        output.write('\n');
        output.flush();
    }

    @Serdeable
    private record JsonConfigurationError(
        String property,
        ConfigurationError.Type type,
        String message,
        String originLocation,
        String rawPropertyName,
        Object rawValue,
        int lineNumber
    ) {
    }
}
