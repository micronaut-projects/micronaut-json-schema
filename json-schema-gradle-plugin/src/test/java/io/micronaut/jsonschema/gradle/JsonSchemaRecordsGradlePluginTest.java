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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    /**
     * Task variant that captures the resolved generator config without touching Oracle.
     */
    public abstract static class RecordingGenerateFromJsonSchemaSourcesTask extends GenerateFromJsonSchemaSourcesTask {
        private JsonSchemaRecordsGeneratorConfig capturedConfig;

        @Override
        void executePipeline(JsonSchemaRecordsLogger logger, JsonSchemaRecordsGeneratorConfig config, ClassLoader providerClassLoader) {
            this.capturedConfig = config;
        }

        JsonSchemaRecordsGeneratorConfig getCapturedConfig() {
            return capturedConfig;
        }
    }
}
