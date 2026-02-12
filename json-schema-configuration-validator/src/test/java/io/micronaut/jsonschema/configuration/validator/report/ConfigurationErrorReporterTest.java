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
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationErrorReporterTest {

    @Test
    void systemErrReporterPrintsToSystemErr() throws Exception {
        Set<ConfigurationError> errors = new LinkedHashSet<>();
        errors.add(new ConfigurationError("a.b", "msg", "origin", "raw", "<bad>"));

        PrintStream original = System.err;
        String out;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8)) {
            System.setErr(ps);
            new SystemErrConfigurationErrorReporter().report(errors);
            out = baos.toString(StandardCharsets.UTF_8);
        } finally {
            System.setErr(original);
        }

        assertTrue(out.contains("a.b"));
        assertTrue(out.contains("Configuration Validation Errors Present"));
        assertTrue(out.contains("Property"));
        assertTrue(out.contains("Type"));
        assertTrue(out.contains("Message"));
        assertTrue(out.contains("Origin"));
        assertTrue(out.contains("msg"));
        assertTrue(out.contains("origin"));
        assertFalse(out.contains("origin:-1"), () -> "Unexpected origin line number formatting: " + out);
        assertTrue(out.contains("<bad>"));
    }

    @Test
    void jsonReporterProducesValidJson() throws Exception {
        Set<ConfigurationError> errors = Set.of(new ConfigurationError("a.b", "msg", "origin", "raw", "v"));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        new JsonConfigurationErrorReporter(JsonMapper.createDefault(), baos).report(errors);
        String json = baos.toString(StandardCharsets.UTF_8);

        Object decoded = JsonMapper.createDefault().readValue(json, io.micronaut.core.type.Argument.of(Object.class));
        assertNotNull(decoded);
        assertTrue(json.contains("\"property\""));
        assertTrue(json.contains("\"a.b\""));
        assertTrue(json.contains("\"lineNumber\""));
        assertFalse(json.contains("snippet"));
    }

    @Test
    void htmlReporterEscapesContent() throws Exception {
        Set<ConfigurationError> errors = Set.of(new ConfigurationError(
            "a<b",
            "m&g",
            "o\"r",
            "r'aw",
            "<bad>",
            12,
            "k=v<bad>",
            "properties"
        ));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        new HtmlConfigurationErrorReporter(baos).report(errors);
        String html = baos.toString(StandardCharsets.UTF_8);

        assertTrue(html.contains("<html"));
        assertTrue(html.contains("bootstrap"));
        assertTrue(html.contains("highlight"));
        assertTrue(html.contains("micronaut-logo-white.svg"));
        assertTrue(html.contains("Configuration validation errors"));
        assertTrue(html.contains("a&lt;b"));
        assertTrue(html.contains("m&amp;g"));
        assertTrue(html.contains("o&quot;r"));
        assertTrue(html.contains("r&#39;aw"));
        assertTrue(html.contains("&lt;bad&gt;"));
        assertTrue(html.contains("language-properties"));
        assertTrue(html.contains("<details>"));
    }

    @Test
    void htmlReporterRendersSuccessForNoErrors() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        new HtmlConfigurationErrorReporter(baos).report(Set.of());
        String html = baos.toString(StandardCharsets.UTF_8);

        assertTrue(html.contains("No configuration validation errors"));
    }
}
