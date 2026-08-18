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

import io.micronaut.jsonschema.generator.records.JsonSchemaRecordsGeneratorConfig;
import io.micronaut.jsonschema.generator.discovery.JsonSchemaRecordsLogger;
import io.micronaut.jsonschema.generator.discovery.SourceSpec;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the Gradle jsonSchemaRecords plugin.
 */
class JsonSchemaRecordsGradlePluginTest {

    @Test
    void pluginRegistersTaskDefaultsAndJavaSourceSet(@TempDir Path tempDir) {
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
        Path projectDir = project.getProjectDir().toPath();

        project.getPluginManager().apply(JsonSchemaRecordsGradlePlugin.class);
        project.getPluginManager().apply(JavaPlugin.class);

        JsonSchemaRecordsExtension extension = project.getExtensions().getByType(JsonSchemaRecordsExtension.class);
        GenerateFromJsonSchemaSourcesTask task = (GenerateFromJsonSchemaSourcesTask) project.getTasks().getByName("generateFromJsonSchemaSources");
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);

        assertNotNull(extension);
        assertEquals(projectDir.resolve("build/jsonschema-cache"), task.getSchemaCacheDir().get().getAsFile().toPath());
        assertEquals(projectDir.resolve("build/generated/sources/jsonschema"), task.getOutputDir().get().getAsFile().toPath());
        assertEquals("JAVA", task.getLanguage().get());
        assertEquals(21, task.getLanguageLevel().get());
        assertFalse(task.getSkipOnError().get());
        assertTrue(task.getFailOnMissingSource().get());
        assertTrue(task.getSources().get().isEmpty());
        assertTrue(sourceSets.getByName("main").getJava().getSrcDirs().contains(task.getGeneratedSourcesDirectory().toFile()));
    }

    @Test
    void generatePassesTaskConfigurationToPipeline(@TempDir Path tempDir) throws Exception {
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
        RecordingGenerateFromJsonSchemaSourcesTask task = project.getTasks().create(
            "recordingGenerateFromJsonSchemaSources",
            RecordingGenerateFromJsonSchemaSourcesTask.class
        );

        task.getJdbcUrl().set("jdbc:oracle:thin:@localhost:1521/FREEPDB1");
        task.getUsername().set("app");
        task.getPassword().set("secret");
        task.getTargetPackage().set("io.micronaut.jsonschema.oracle.generated");
        task.getLanguage().set("KOTLIN");
        task.getLanguageLevel().set(17);
        task.getSchemaCacheDir().set(tempDir.resolve("schema-cache").toFile());
        task.getOutputDir().set(tempDir.resolve("generated-sources").toFile());
        task.getSources().set(List.of(
            Map.of("name", "domains", "provider", "oracle-domains", "options", Map.of("owner", "APP", "include", List.of("APP_JSON", "ALT_JSON"))),
            Map.of("name", "views", "provider", "oracle-duality-views", "options", Map.of("owner", "APP", "include", "APP_VIEW"))
        ));
        task.getSkipOnError().set(true);
        task.getFailOnMissingSource().set(false);

        task.generate();

        JsonSchemaRecordsGeneratorConfig config = task.getCapturedConfig();
        assertNotNull(config);
        assertEquals("jdbc:oracle:thin:@localhost:1521/FREEPDB1", config.jdbcUrl());
        assertEquals("app", config.username());
        assertEquals("secret", config.password());
        assertEquals("io.micronaut.jsonschema.oracle.generated", config.targetPackage());
        assertEquals("KOTLIN", config.language());
        assertEquals(17, config.languageLevel());
        assertEquals(tempDir.resolve("schema-cache"), config.schemaCacheDir());
        assertEquals(tempDir.resolve("generated-sources"), config.outputDir());
        assertEquals(List.of(
            new SourceSpec("domains", "oracle-domains", Map.of("owner", "APP", "include", List.of("APP_JSON", "ALT_JSON"))),
            new SourceSpec("views", "oracle-duality-views", Map.of("owner", "APP", "include", "APP_VIEW"))
        ), config.sources());
        assertTrue(config.skipOnError());
        assertFalse(config.failOnMissingSource());
    }

    @Test
    void generateRegistersAndDeregistersJdbcDrivers(@TempDir Path tempDir) throws Exception {
        Path driverClasspath = tempDir.resolve("driver-classpath");
        Path serviceFile = driverClasspath.resolve("META-INF/services/java.sql.Driver");
        Files.createDirectories(serviceFile.getParent());
        Files.writeString(serviceFile, TestJdbcDriver.class.getName() + "\n");
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
        RecordingGenerateFromJsonSchemaSourcesTask task = project.getTasks().create(
            "recordingGenerateWithJdbcDriver",
            RecordingGenerateFromJsonSchemaSourcesTask.class
        );
        configureRequiredProperties(task, tempDir);
        task.getJdbcClasspath().from(driverClasspath.toFile());
        task.inspectJdbcDriver = true;

        task.generate();

        assertTrue(task.jdbcDriverWasAvailable);
        assertThrows(SQLException.class, () -> DriverManager.getDriver("jdbc:test-driver:example"));
    }

    @Test
    void generateAllowsCredentialsToBeOmitted(@TempDir Path tempDir) throws Exception {
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
        RecordingGenerateFromJsonSchemaSourcesTask task = project.getTasks().create(
            "recordingGenerateWithoutCredentials",
            RecordingGenerateFromJsonSchemaSourcesTask.class
        );
        configureRequiredProperties(task, tempDir);

        task.generate();

        JsonSchemaRecordsGeneratorConfig config = task.getCapturedConfig();
        assertNotNull(config);
        assertNull(config.jdbcUrl());
        assertNull(config.username());
        assertNull(config.password());
    }

    @Test
    void executeWrapsPipelineFailure(@TempDir Path tempDir) {
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
        RecordingGenerateFromJsonSchemaSourcesTask task = project.getTasks().create(
            "failingGenerateFromJsonSchemaSources",
            RecordingGenerateFromJsonSchemaSourcesTask.class
        );
        configureRequiredProperties(task, tempDir);
        Exception failure = new IOException("boom");
        task.pipelineFailure = failure;

        IllegalStateException exception = assertThrows(IllegalStateException.class, task::execute);

        assertEquals("jsonSchemaRecords generation failed", exception.getMessage());
        assertSame(failure, exception.getCause());
    }

    private static void configureRequiredProperties(GenerateFromJsonSchemaSourcesTask task, Path tempDir) {
        task.getTargetPackage().set("example.generated");
        task.getSchemaCacheDir().set(tempDir.resolve("schema-cache").toFile());
        task.getOutputDir().set(tempDir.resolve("generated-sources").toFile());
    }

    /**
     * Task variant that captures the resolved generator config without touching Oracle.
     */
    public abstract static class RecordingGenerateFromJsonSchemaSourcesTask extends GenerateFromJsonSchemaSourcesTask {
        private JsonSchemaRecordsGeneratorConfig capturedConfig;
        private Exception pipelineFailure;
        private boolean inspectJdbcDriver;
        private boolean jdbcDriverWasAvailable;

        @Override
        void executePipeline(JsonSchemaRecordsLogger logger,
                             JsonSchemaRecordsGeneratorConfig config,
                             ClassLoader providerClassLoader) throws Exception {
            if (pipelineFailure != null) {
                throw pipelineFailure;
            }
            this.capturedConfig = config;
            if (inspectJdbcDriver) {
                Driver driver = DriverManager.getDriver("jdbc:test-driver:example");
                jdbcDriverWasAvailable = driver.acceptsURL("jdbc:test-driver:example")
                    && driver.connect("jdbc:test-driver:example", new Properties()) == null
                    && driver.getPropertyInfo("jdbc:test-driver:example", new Properties()).length == 0
                    && driver.getMajorVersion() == 1
                    && driver.getMinorVersion() == 2
                    && driver.jdbcCompliant()
                    && driver.getParentLogger() == Logger.getGlobal();
            }
        }

        JsonSchemaRecordsGeneratorConfig getCapturedConfig() {
            return capturedConfig;
        }
    }

    /**
     * JDBC driver exposed through a temporary service descriptor to exercise Gradle's JDBC classpath support.
     */
    public static final class TestJdbcDriver implements Driver {
        @Override
        public Connection connect(String url, Properties info) {
            return null;
        }

        @Override
        public boolean acceptsURL(String url) {
            return url.startsWith("jdbc:test-driver:");
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 2;
        }

        @Override
        public boolean jdbcCompliant() {
            return true;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }
    }
}
