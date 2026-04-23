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

import io.micronaut.jsonschema.generator.oracle.OracleJsonSchemaGeneratorConfig;
import io.micronaut.jsonschema.generator.oracle.OracleJsonSchemaGeneratorConfig.DiscoverySource;
import io.micronaut.jsonschema.generator.oracle.OracleJsonSchemaLogger;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the Gradle Oracle JSON Schema plugin.
 */
class OracleJsonSchemaGradlePluginTest {

    @Test
    void pluginRegistersTaskDefaultsAndJavaSourceSet(@TempDir Path tempDir) {
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
        Path projectDir = project.getProjectDir().toPath();

        project.getPluginManager().apply(OracleJsonSchemaGradlePlugin.class);
        project.getPluginManager().apply(JavaPlugin.class);

        OracleJsonSchemaExtension extension = project.getExtensions().getByType(OracleJsonSchemaExtension.class);
        OracleJsonSchemaGenerateTask task = (OracleJsonSchemaGenerateTask) project.getTasks().getByName("generateFromOracleJsonSchema");
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);

        assertNotNull(extension);
        assertEquals(projectDir.resolve("build/oracle-jsonschema-cache"), task.getSchemaCacheDir().get().getAsFile().toPath());
        assertEquals(projectDir.resolve("build/generated/sources/oracle-jsonschema"), task.getOutputDir().get().getAsFile().toPath());
        assertFalse(task.getSkipOnError().get());
        assertTrue(task.getFailOnMissingDb().get());
        assertTrue(task.getIncludeDomains().get().isEmpty());
        assertTrue(task.getIncludeViews().get().isEmpty());
        assertTrue(task.getSources().get().isEmpty());
        assertTrue(sourceSets.getByName("main").getJava().getSrcDirs().contains(task.getGeneratedSourcesDirectory().toFile()));
    }

    @Test
    void generatePassesTaskConfigurationToPipeline(@TempDir Path tempDir) throws Exception {
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
        RecordingOracleJsonSchemaGenerateTask task = project.getTasks().create(
            "recordingGenerateFromOracleJsonSchema",
            RecordingOracleJsonSchemaGenerateTask.class
        );

        task.getJdbcUrl().set("jdbc:oracle:thin:@localhost:1521/FREEPDB1");
        task.getUsername().set("app");
        task.getPassword().set("secret");
        task.getOwner().set("APP");
        task.getTargetPackage().set("io.micronaut.jsonschema.oracle.generated");
        task.getSchemaCacheDir().set(tempDir.resolve("schema-cache").toFile());
        task.getOutputDir().set(tempDir.resolve("generated-sources").toFile());
        task.getIncludeDomains().set(List.of("APP_JSON"));
        task.getIncludeViews().set(List.of("APP_VIEW"));
        task.getSources().set(List.of("OracleDomain", "OracleJsonView"));
        task.getSkipOnError().set(true);
        task.getFailOnMissingDb().set(false);

        task.generate();

        OracleJsonSchemaGeneratorConfig config = task.getCapturedConfig();
        assertNotNull(config);
        assertEquals("jdbc:oracle:thin:@localhost:1521/FREEPDB1", config.jdbcUrl());
        assertEquals("app", config.username());
        assertEquals("secret", config.password());
        assertEquals("APP", config.owner());
        assertEquals("io.micronaut.jsonschema.oracle.generated", config.targetPackage());
        assertEquals(tempDir.resolve("schema-cache"), config.schemaCacheDir());
        assertEquals(tempDir.resolve("generated-sources"), config.outputDir());
        assertEquals(List.of("APP_JSON"), config.includeDomains());
        assertEquals(List.of("APP_VIEW"), config.includeViews());
        assertEquals(List.of(DiscoverySource.ORACLE_DOMAIN, DiscoverySource.ORACLE_JSON_VIEW), config.sources());
        assertTrue(config.skipOnError());
        assertFalse(config.failOnMissingDb());
    }

    /**
     * Task variant that captures the resolved generator config without touching Oracle.
     */
    public abstract static class RecordingOracleJsonSchemaGenerateTask extends OracleJsonSchemaGenerateTask {
        private OracleJsonSchemaGeneratorConfig capturedConfig;

        @Override
        void executePipeline(OracleJsonSchemaLogger logger, OracleJsonSchemaGeneratorConfig config) {
            this.capturedConfig = config;
        }

        OracleJsonSchemaGeneratorConfig getCapturedConfig() {
            return capturedConfig;
        }
    }
}
