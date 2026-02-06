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

import io.micronaut.json.JsonMapper;
import io.micronaut.core.type.Argument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationJsonSchemaValidatorCliTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void cleanupSystemProperties() {
        System.clearProperty("test.config.enabled");
        System.clearProperty("test.config.count");
        System.clearProperty("test.config.extra");
        System.clearProperty("micronaut.http.client.unknown");
    }

    @Test
    void mainRunsFullLifecycleAndWritesDefaultReports() throws Exception {
        System.setProperty("test.config.enabled", "not-a-bool");
        System.setProperty("test.config.count", "1");

        Path out = tempDir.resolve("out-default");

        int exit = ConfigurationJsonSchemaValidatorCli.run(new String[] {
            "--classpath", System.getProperty("java.class.path"),
            "--environments", "test",
            "--out", out.toString()
        }, System.out, System.err);

        assertEquals(1, exit);
        assertTrue(Files.exists(out.resolve("configuration-errors.json")));
        assertTrue(Files.exists(out.resolve("configuration-errors.html")));
    }

    @Test
    void canSelectJsonOnlyFormat() throws Exception {
        System.setProperty("test.config.enabled", "not-a-bool");
        System.setProperty("test.config.count", "1");

        Path out = tempDir.resolve("out-json");

        int exit = ConfigurationJsonSchemaValidatorCli.run(new String[] {
            "--classpath", System.getProperty("java.class.path"),
            "--environments", "test",
            "--out", out.toString(),
            "--format", "json"
        }, System.out, System.err);

        assertEquals(1, exit);
        assertTrue(Files.exists(out.resolve("configuration-errors.json")));
        assertFalse(Files.exists(out.resolve("configuration-errors.html")));
    }

    @Test
    void suppressionPatternsDowngradeErrorsToWarnings() throws Exception {
        System.setProperty("micronaut.http.client.unknown", "x");

        Path out = tempDir.resolve("out-suppress");
        int exit = ConfigurationJsonSchemaValidatorCli.run(new String[] {
            "--classpath", System.getProperty("java.class.path"),
            "--environments", "test",
            "--out", out.toString(),
            "--fail-on-not-present", "true",
            "--suppress", "micronaut.http.*",
            "--format", "json"
        }, System.out, System.err);

        assertEquals(0, exit);

        String json = Files.readString(out.resolve("configuration-errors.json"), StandardCharsets.UTF_8);
        Object decoded = JsonMapper.createDefault().readValue(json, Argument.of(Object.class));
        assertNotNull(decoded);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) decoded;
        assertFalse(list.isEmpty());
        assertTrue(list.stream().anyMatch(m -> "micronaut.http.client.unknown".equals(m.get("property"))));
        assertTrue(list.stream().anyMatch(m -> "WARNING".equals(m.get("type"))));
        assertFalse(list.stream().anyMatch(m -> "ERROR".equals(m.get("type"))));
    }
}
