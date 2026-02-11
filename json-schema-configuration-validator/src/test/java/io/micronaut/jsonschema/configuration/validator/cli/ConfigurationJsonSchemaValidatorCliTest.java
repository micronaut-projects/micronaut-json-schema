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
package io.micronaut.jsonschema.configuration.validator.cli;

import io.micronaut.core.util.StringUtils;
import io.micronaut.json.JsonMapper;
import io.micronaut.core.type.Argument;
import io.micronaut.jsonschema.configuration.validator.ConfigurationJsonSchemaValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.io.ByteArrayOutputStream;

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
        System.clearProperty("micronaut.environments");
    }

    @Test
    void mainRunsFullLifecycleAndWritesDefaultReports() throws Exception {
        System.setProperty("test.config.enabled", "not-a-bool");
        System.setProperty("test.config.count", "1");

        Path out = tempDir.resolve("out-default");

        ByteArrayOutputStream errCapture = new ByteArrayOutputStream();

        int exit = ConfigurationJsonSchemaValidatorCli.run(new String[] {
            "--classpath", System.getProperty("java.class.path"),
            "--environments", "test",
            "--out", out.toString()
        }, System.out, new PrintStream(errCapture, true, StandardCharsets.UTF_8));

        assertEquals(1, exit);
        assertTrue(Files.exists(out.resolve("configuration-errors.json")));
        assertTrue(Files.exists(out.resolve("configuration-errors.html")));

        String stderr = errCapture.toString(StandardCharsets.UTF_8);
        assertTrue(stderr.contains("report: file:"), () -> "Unexpected stderr:\n" + stderr);
        assertTrue(stderr.contains("configuration-errors.html"), () -> "Unexpected stderr:\n" + stderr);
    }

    @Test
    void canSelectJsonOnlyFormat() throws Exception {
        System.setProperty("test.config.enabled", "not-a-bool");
        System.setProperty("test.config.count", "1");

        Path out = tempDir.resolve("out-json");

        ByteArrayOutputStream errCapture = new ByteArrayOutputStream();

        int exit = ConfigurationJsonSchemaValidatorCli.run(new String[] {
            "--classpath", System.getProperty("java.class.path"),
            "--environments", "test",
            "--out", out.toString(),
            "--format", "json"
        }, System.out, new PrintStream(errCapture, true, StandardCharsets.UTF_8));

        assertEquals(1, exit);
        assertTrue(Files.exists(out.resolve("configuration-errors.json")));
        assertFalse(Files.exists(out.resolve("configuration-errors.html")));

        String stderr = errCapture.toString(StandardCharsets.UTF_8);
        assertTrue(stderr.contains("report: file:"), () -> "Unexpected stderr:\n" + stderr);
        assertTrue(stderr.contains("configuration-errors.json"), () -> "Unexpected stderr:\n" + stderr);
    }

    @Test
    void optionsDefaultDoesNotDeduceEnvironments() {
        ConfigurationJsonSchemaValidatorCli.Options options = ConfigurationJsonSchemaValidatorCli.Options.parse(new String[] {
            "--classpath", "cp",
            "--out", tempDir.resolve("out").toString()
        });

        assertFalse(options.deduceEnvironments());
    }

    @Test
    void optionsCanEnableDeduceEnvironments() {
        ConfigurationJsonSchemaValidatorCli.Options options = ConfigurationJsonSchemaValidatorCli.Options.parse(new String[] {
            "--classpath", "cp",
            "--out", tempDir.resolve("out").toString(),
            "--deduce-environments", StringUtils.TRUE
        });

        assertTrue(options.deduceEnvironments());
    }

    @Test
    void suppressionPatternsDowngradeErrorsToWarnings() throws Exception {
        System.setProperty("micronaut.http.client.unknown", "x");

        Path out = tempDir.resolve("out-suppress");
        int exit = ConfigurationJsonSchemaValidatorCli.run(new String[] {
            "--classpath", System.getProperty("java.class.path"),
            "--environments", "test",
            "--out", out.toString(),
            "--fail-on-not-present", StringUtils.TRUE,
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

    @Test
    void bootstrapCliCanRunWithMinimalProcessClasspath() throws Exception {
        Path cpDir = tempDir.resolve("cp-bootstrap");
        Files.createDirectories(cpDir);
        Files.writeString(cpDir.resolve("application.properties"), String.join("\n",
            "test.config.enabled=not-a-bool",
            "test.config.count=1",
            ""
        ), StandardCharsets.UTF_8);

        Path out = tempDir.resolve("out-bootstrap");

        String fullRuntimeClasspath = cpDir + File.pathSeparator + System.getProperty("java.class.path");

        String minimalClasspath = String.join(File.pathSeparator,
            Path.of("build/resources/main").toAbsolutePath().toString(),
            Path.of("build/classes/java/main").toAbsolutePath().toString()
        );

        List<String> cmd = List.of(
            javaBin(),
            "-cp",
            minimalClasspath,
            ConfigurationJsonSchemaValidatorCliBootstrap.class.getName(),
            "--classpath",
            fullRuntimeClasspath,
            "--environments",
            "test",
            "--out",
            out.toString(),
            "--format",
            "html"
        );

        Process process = new ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start();

        String output;
        try (InputStream is = process.getInputStream()) {
            output = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        boolean finished = process.waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS);
        assertTrue(finished, () -> "Process did not finish. Output so far:\n" + output);
        assertEquals(1, process.exitValue(), () -> "Unexpected exit. Output:\n" + output);

        assertTrue(Files.exists(out.resolve("configuration-errors.html")), () -> "No report written. Output:\n" + output);
    }

    @Test
    void bootstrapCliSupportsClasspathEqualsSyntax() throws Exception {
        Path cpDir = tempDir.resolve("cp-bootstrap-eq");
        Files.createDirectories(cpDir);
        Files.writeString(cpDir.resolve("application.properties"), String.join("\n",
            "test.config.enabled=not-a-bool",
            "test.config.count=1",
            ""
        ), StandardCharsets.UTF_8);

        Path out = tempDir.resolve("out-bootstrap-eq");

        String fullRuntimeClasspath = cpDir + File.pathSeparator + System.getProperty("java.class.path");
        String minimalClasspath = String.join(File.pathSeparator,
            Path.of("build/resources/main").toAbsolutePath().toString(),
            Path.of("build/classes/java/main").toAbsolutePath().toString()
        );

        List<String> cmd = List.of(
            javaBin(),
            "-cp",
            minimalClasspath,
            ConfigurationJsonSchemaValidatorCliBootstrap.class.getName(),
            "--classpath=" + fullRuntimeClasspath,
            "--environments",
            "test",
            "--out",
            out.toString(),
            "--format",
            "html"
        );

        Process process = new ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start();

        String output;
        try (InputStream is = process.getInputStream()) {
            output = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        boolean finished = process.waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS);
        assertTrue(finished, () -> "Process did not finish. Output so far:\n" + output);
        assertEquals(1, process.exitValue(), () -> "Unexpected exit. Output:\n" + output);

        assertTrue(Files.exists(out.resolve("configuration-errors.html")), () -> "No report written. Output:\n" + output);
    }

    @Test
    void bootstrapCliErrorsWhenClasspathIsMissing() throws Exception {
        String minimalClasspath = String.join(File.pathSeparator,
            Path.of("build/resources/main").toAbsolutePath().toString(),
            Path.of("build/classes/java/main").toAbsolutePath().toString()
        );

        List<String> cmd = List.of(
            javaBin(),
            "-cp",
            minimalClasspath,
            ConfigurationJsonSchemaValidatorCliBootstrap.class.getName(),
            "--out",
            tempDir.resolve("out-missing").toString()
        );

        Process process = new ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start();

        String output;
        try (InputStream is = process.getInputStream()) {
            output = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        boolean finished = process.waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS);
        assertTrue(finished, () -> "Process did not finish. Output so far:\n" + output);

        assertTrue(output.contains("Missing required argument: --classpath"), () -> "Unexpected output:\n" + output);
    }

    @Test
    void bootstrapCliErrorsWhenClasspathHasNoValue() throws Exception {
        String minimalClasspath = String.join(File.pathSeparator,
            Path.of("build/resources/main").toAbsolutePath().toString(),
            Path.of("build/classes/java/main").toAbsolutePath().toString()
        );

        List<String> cmd = List.of(
            javaBin(),
            "-cp",
            minimalClasspath,
            ConfigurationJsonSchemaValidatorCliBootstrap.class.getName(),
            "--classpath"
        );

        Process process = new ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start();

        String output;
        try (InputStream is = process.getInputStream()) {
            output = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        boolean finished = process.waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS);
        assertTrue(finished, () -> "Process did not finish. Output so far:\n" + output);

        assertTrue(output.contains("Missing required argument: --classpath"), () -> "Unexpected output:\n" + output);
    }

    @Test
    void bootstrapCliErrorsWhenClasspathValueIsAnotherFlag() throws Exception {
        String minimalClasspath = String.join(File.pathSeparator,
            Path.of("build/resources/main").toAbsolutePath().toString(),
            Path.of("build/classes/java/main").toAbsolutePath().toString()
        );

        List<String> cmd = List.of(
            javaBin(),
            "-cp",
            minimalClasspath,
            ConfigurationJsonSchemaValidatorCliBootstrap.class.getName(),
            "--classpath",
            "--out",
            tempDir.resolve("out-missing").toString()
        );

        Process process = new ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start();

        String output;
        try (InputStream is = process.getInputStream()) {
            output = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        boolean finished = process.waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS);
        assertTrue(finished, () -> "Process did not finish. Output so far:\n" + output);

        assertTrue(output.contains("Missing required argument: --classpath"), () -> "Unexpected output:\n" + output);
    }

    @Test
    void cliHelpPrintsUsage() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = ConfigurationJsonSchemaValidatorCli.run(new String[] {
            "--help"
        }, new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));

        assertEquals(0, exit);
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Usage:"));
    }

    @Test
    void cliInvalidFormatReturnsUsageExitCode() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = ConfigurationJsonSchemaValidatorCli.run(new String[] {
            "--classpath", System.getProperty("java.class.path"),
            "--environments", "test",
            "--out", tempDir.resolve("out-invalid-format").toString(),
            "--format", "nope"
        }, new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));

        assertEquals(2, exit);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("Invalid format"));
    }

    private static String javaBin() {
        String home = System.getProperty("java.home");
        return Path.of(home, "bin", "java").toAbsolutePath().toString();
    }

    @Test
    void htmlReportIncludesSnippetsForApplicationProperties() throws Exception {
        Path cp = tempDir.resolve("cp-props");
        Files.createDirectories(cp);
        Files.writeString(cp.resolve("application.properties"), String.join("\n",
            "test.config.enabled=not-a-bool",
            "test.config.count=1",
            ""
        ), StandardCharsets.UTF_8);

        Path out = tempDir.resolve("out-props");

        String classpath = cp + File.pathSeparator + System.getProperty("java.class.path");
        int exit = ConfigurationJsonSchemaValidatorCli.run(new String[] {
            "--classpath", classpath,
            "--environments", "test",
            "--out", out.toString(),
            "--format", "html"
        }, System.out, System.err);

        assertEquals(1, exit);
        String html = Files.readString(out.resolve("configuration-errors.html"), StandardCharsets.UTF_8);
        assertTrue(html.contains("test.config.enabled"));
        assertTrue(html.contains("language-properties"));
        assertTrue(html.contains("test.config.enabled=not-a-bool"));
        assertTrue(html.contains("<details>"));

        assertTrue(Pattern.compile("(?s)test\\.config\\.enabled.*?<td>1</td>").matcher(html).find());
    }

    @Test
    void htmlReportIncludesSnippetsForApplicationYaml() throws Exception {
        Path cp = tempDir.resolve("cp-yaml");
        Files.createDirectories(cp);
        Files.writeString(cp.resolve("application.yml"), String.join("\n",
            "test:",
            "  config:",
            "    enabled: not-a-bool",
            "    count: 1",
            ""
        ), StandardCharsets.UTF_8);

        Path out = tempDir.resolve("out-yaml");

        String classpath = cp + File.pathSeparator + System.getProperty("java.class.path");
        int exit = ConfigurationJsonSchemaValidatorCli.run(new String[] {
            "--classpath", classpath,
            "--environments", "test",
            "--out", out.toString(),
            "--format", "html"
        }, System.out, System.err);

        assertEquals(1, exit);
        String html = Files.readString(out.resolve("configuration-errors.html"), StandardCharsets.UTF_8);
        assertTrue(html.contains("test.config.enabled"));
        assertTrue(html.contains("language-yaml"));
        assertTrue(html.contains("enabled: not-a-bool"));
        assertTrue(html.contains("<details>"));

        assertTrue(Pattern.compile("(?s)test\\.config\\.enabled.*?<td>3</td>").matcher(html).find());
    }

    @Test
    void htmlReportUsesPathAwareYamlLocatorForDuplicateKeys() throws Exception {
        Path cp = tempDir.resolve("cp-yaml-dup");
        Files.createDirectories(cp);
        Files.writeString(cp.resolve("application.yml"), String.join("\n",
            "other:",
            "  enabled: true",
            "test:",
            "  config:",
            "    enabled: not-a-bool",
            "    count: 1",
            ""
        ), StandardCharsets.UTF_8);

        Path out = tempDir.resolve("out-yaml-dup");

        String classpath = cp + File.pathSeparator + System.getProperty("java.class.path");
        int exit = ConfigurationJsonSchemaValidatorCli.run(new String[] {
            "--classpath", classpath,
            "--environments", "test",
            "--out", out.toString(),
            "--format", "html"
        }, System.out, System.err);

        assertEquals(1, exit);
        String html = Files.readString(out.resolve("configuration-errors.html"), StandardCharsets.UTF_8);

        // Ensure we point at test.config.enabled (line 5), not other.enabled (line 2)
        assertTrue(Pattern.compile("(?s)test\\.config\\.enabled.*?<td>5</td>").matcher(html).find());
        assertFalse(Pattern.compile("(?s)test\\.config\\.enabled.*?<td>2</td>").matcher(html).find());
    }

    @Test
    void htmlReportUsesPathAwareTomlLocatorForDuplicateKeys() throws Exception {
        Path cp = tempDir.resolve("cp-toml-dup");
        Files.createDirectories(cp);
        Files.writeString(cp.resolve("application.toml"), String.join("\n",
            "[other]",
            "enabled = true",
            "",
            "[test.config]",
            "enabled = \"not-a-bool\"",
            "count = 1",
            ""
        ), StandardCharsets.UTF_8);

        Path out = tempDir.resolve("out-toml-dup");

        String classpath = cp + File.pathSeparator + System.getProperty("java.class.path");
        int exit = ConfigurationJsonSchemaValidatorCli.run(new String[] {
            "--classpath", classpath,
            "--environments", "test",
            "--out", out.toString(),
            "--format", "html"
        }, System.out, System.err);

        assertEquals(1, exit);
        String html = Files.readString(out.resolve("configuration-errors.html"), StandardCharsets.UTF_8);

        assertTrue(html.contains("language-toml"));
        assertTrue(html.contains("enabled = &quot;not-a-bool&quot;"));

        // Ensure we point at test.config.enabled (line 5), not other.enabled (line 2)
        assertTrue(Pattern.compile("(?s)test\\.config\\.enabled.*?<td>5</td>").matcher(html).find());
        assertFalse(Pattern.compile("(?s)test\\.config\\.enabled.*?<td>2</td>").matcher(html).find());
    }

    @Test
    void cliReportsYamlSyntaxErrorsWithLocation() throws Exception {
        Path cp = tempDir.resolve("cp-yaml-syntax");
        Files.createDirectories(cp);
        Files.writeString(cp.resolve("application.yml"), String.join("\n",
            "test:",
            "\tconfig:",
            "    enabled: not-a-bool",
            ""
        ), StandardCharsets.UTF_8);

        Path out = tempDir.resolve("out-yaml-syntax");
        ByteArrayOutputStream errCapture = new ByteArrayOutputStream();
        PrintStream err = new PrintStream(errCapture, true, StandardCharsets.UTF_8);

        String classpath = cp + File.pathSeparator + System.getProperty("java.class.path");
        int exit = ConfigurationJsonSchemaValidatorCli.run(new String[] {
            "--classpath", classpath,
            "--environments", "test",
            "--out", out.toString(),
            "--format", "both"
        }, System.out, err);

        assertEquals(2, exit);
        assertFalse(Files.exists(out.resolve("configuration-errors.json")));
        assertFalse(Files.exists(out.resolve("configuration-errors.html")));

        String stderr = errCapture.toString(StandardCharsets.UTF_8);
        assertTrue(stderr.contains("Validation failed while loading configuration"), () -> "Unexpected stderr:\n" + stderr);
        assertTrue(stderr.toLowerCase().contains("application.yml"), () -> "Unexpected stderr:\n" + stderr);
        assertTrue(stderr.toLowerCase().contains("line"), () -> "Unexpected stderr:\n" + stderr);
        assertTrue(stderr.toLowerCase().contains("column"), () -> "Unexpected stderr:\n" + stderr);
    }
}
