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
package io.micronaut.jsonschema.maven;

import io.micronaut.jsonschema.generator.oracle.OracleJsonSchemaGeneratorConfig;
import io.micronaut.jsonschema.generator.oracle.OracleJsonSchemaGeneratorConfig.DiscoverySource;
import io.micronaut.jsonschema.generator.oracle.OracleJsonSchemaLogger;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the Maven Oracle JSON Schema mojo.
 */
class GenerateFromOracleJsonSchemaMojoTest {

    @Test
    void executeSkipsWhenOptInFlagIsDisabled(@TempDir Path tempDir) throws Exception {
        TestGenerateFromOracleJsonSchemaMojo mojo = new TestGenerateFromOracleJsonSchemaMojo();
        mojo.skip = true;
        mojo.project = new MavenProject();
        mojo.outputDir = tempDir.resolve("generated-sources").toFile();

        mojo.execute();

        assertNull(mojo.capturedConfig);
        assertTrue(mojo.project.getCompileSourceRoots().isEmpty());
    }

    @Test
    void executePassesMojoConfigurationToPipelineAndRegistersSources(@TempDir Path tempDir) throws Exception {
        TestGenerateFromOracleJsonSchemaMojo mojo = new TestGenerateFromOracleJsonSchemaMojo();
        mojo.skip = false;
        mojo.jdbcUrl = "jdbc:oracle:thin:@localhost:1521/FREEPDB1";
        mojo.username = "app";
        mojo.password = "secret";
        mojo.owner = "APP";
        mojo.targetPackage = "io.micronaut.jsonschema.oracle.generated";
        mojo.schemaCacheDir = tempDir.resolve("schema-cache").toFile();
        mojo.outputDir = tempDir.resolve("generated-sources").toFile();
        mojo.includeDomains = List.of("APP_JSON");
        mojo.includeViews = List.of("APP_VIEW");
        mojo.sources = List.of("OracleDomain", "OracleJsonView");
        mojo.skipOnError = true;
        mojo.failOnMissingDb = false;
        mojo.project = new MavenProject();

        mojo.execute();

        OracleJsonSchemaGeneratorConfig config = mojo.capturedConfig;
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
        assertEquals(List.of(tempDir.resolve("generated-sources").toFile().getAbsolutePath()), mojo.project.getCompileSourceRoots());
    }

    @Test
    void executeWrapsPipelineFailure(@TempDir Path tempDir) {
        TestGenerateFromOracleJsonSchemaMojo mojo = new TestGenerateFromOracleJsonSchemaMojo();
        IOException failure = new IOException("boom");
        mojo.skip = false;
        mojo.jdbcUrl = "jdbc:oracle:thin:@localhost:1521/FREEPDB1";
        mojo.username = "app";
        mojo.password = "secret";
        mojo.targetPackage = "io.micronaut.jsonschema.oracle.generated";
        mojo.schemaCacheDir = tempDir.resolve("schema-cache").toFile();
        mojo.outputDir = tempDir.resolve("generated-sources").toFile();
        mojo.project = new MavenProject();
        mojo.failure = failure;

        MojoExecutionException exception = assertThrows(MojoExecutionException.class, mojo::execute);

        assertEquals("Oracle JSON Schema generation failed", exception.getMessage());
        assertSame(failure, exception.getCause());
    }

    @Test
    void executeResolvesCredentialsFromSettingsServer(@TempDir Path tempDir) throws Exception {
        SettingsBackedGenerateFromOracleJsonSchemaMojo mojo = new SettingsBackedGenerateFromOracleJsonSchemaMojo();
        Settings settings = new Settings();
        Server server = new Server();
        server.setId("oracle-jsonschema");
        server.setUsername("settings-user");
        server.setPassword("settings-secret");
        settings.addServer(server);
        mojo.skip = false;
        mojo.jdbcUrl = "jdbc:oracle:thin:@localhost:1521/FREEPDB1";
        mojo.serverId = "oracle-jsonschema";
        mojo.settings = settings;
        mojo.targetPackage = "io.micronaut.jsonschema.oracle.generated";
        mojo.schemaCacheDir = tempDir.resolve("schema-cache").toFile();
        mojo.outputDir = tempDir.resolve("generated-sources").toFile();
        mojo.project = new MavenProject();

        mojo.execute();

        OracleJsonSchemaGeneratorConfig config = mojo.capturedConfig;
        assertNotNull(config);
        assertEquals("settings-user", config.username());
        assertEquals("settings-secret", config.password());
    }

    @Test
    void executeFailsWhenCredentialsCannotBeResolved(@TempDir Path tempDir) {
        SettingsBackedGenerateFromOracleJsonSchemaMojo mojo = new SettingsBackedGenerateFromOracleJsonSchemaMojo();
        mojo.skip = false;
        mojo.jdbcUrl = "jdbc:oracle:thin:@localhost:1521/FREEPDB1";
        mojo.targetPackage = "io.micronaut.jsonschema.oracle.generated";
        mojo.schemaCacheDir = tempDir.resolve("schema-cache").toFile();
        mojo.outputDir = tempDir.resolve("generated-sources").toFile();
        mojo.project = new MavenProject();

        MojoExecutionException exception = assertThrows(MojoExecutionException.class, mojo::execute);

        assertEquals("Missing required Oracle JSON Schema parameter: username", exception.getMessage());
    }

    /**
     * Mojo variant that captures pipeline execution without reaching Oracle.
     */
    static final class TestGenerateFromOracleJsonSchemaMojo extends GenerateFromOracleJsonSchemaMojo {
        private String jdbcUrl;
        private String username;
        private String password;
        private String owner;
        private String targetPackage;
        private File schemaCacheDir;
        private File outputDir;
        private List<String> includeDomains = List.of();
        private List<String> includeViews = List.of();
        private List<String> sources = List.of();
        private boolean skipOnError;
        private boolean failOnMissingDb = true;
        private boolean skip = true;
        private MavenProject project = new MavenProject();
        private OracleJsonSchemaGeneratorConfig capturedConfig;
        private Exception failure;

        @Override
        protected String getJdbcUrl() {
            return jdbcUrl;
        }

        @Override
        protected String getUsername() {
            return username;
        }

        @Override
        protected String getPassword() {
            return password;
        }

        @Override
        protected String getOwner() {
            return owner;
        }

        @Override
        protected String getTargetPackage() {
            return targetPackage;
        }

        @Override
        protected File getSchemaCacheDir() {
            return schemaCacheDir;
        }

        @Override
        protected File getOutputDir() {
            return outputDir;
        }

        @Override
        protected List<String> getIncludeDomains() {
            return includeDomains;
        }

        @Override
        protected List<String> getIncludeViews() {
            return includeViews;
        }

        @Override
        protected List<String> getSources() {
            return sources;
        }

        @Override
        protected boolean isSkipOnError() {
            return skipOnError;
        }

        @Override
        protected boolean isFailOnMissingDb() {
            return failOnMissingDb;
        }

        @Override
        boolean isSkipped() {
            return skip;
        }

        @Override
        void executePipeline(OracleJsonSchemaLogger logger, OracleJsonSchemaGeneratorConfig config) throws Exception {
            if (failure != null) {
                throw failure;
            }
            this.capturedConfig = config;
        }

        @Override
        protected MavenProject getProject() {
            return project;
        }
    }

    /**
     * Mojo variant that exercises base credential resolution against Maven settings.
     */
    static final class SettingsBackedGenerateFromOracleJsonSchemaMojo extends GenerateFromOracleJsonSchemaMojo {
        private String jdbcUrl;
        private String owner;
        private String targetPackage;
        private File schemaCacheDir;
        private File outputDir;
        private List<String> includeDomains = List.of();
        private List<String> includeViews = List.of();
        private List<String> sources = List.of();
        private boolean skipOnError;
        private boolean failOnMissingDb = true;
        private boolean skip = true;
        private String serverId;
        private Settings settings;
        private MavenProject project = new MavenProject();
        private OracleJsonSchemaGeneratorConfig capturedConfig;

        @Override
        protected String getJdbcUrl() {
            return jdbcUrl;
        }

        @Override
        protected String getOwner() {
            return owner;
        }

        @Override
        protected String getTargetPackage() {
            return targetPackage;
        }

        @Override
        protected File getSchemaCacheDir() {
            return schemaCacheDir;
        }

        @Override
        protected File getOutputDir() {
            return outputDir;
        }

        @Override
        protected List<String> getIncludeDomains() {
            return includeDomains;
        }

        @Override
        protected List<String> getIncludeViews() {
            return includeViews;
        }

        @Override
        protected List<String> getSources() {
            return sources;
        }

        @Override
        protected boolean isSkipOnError() {
            return skipOnError;
        }

        @Override
        protected boolean isFailOnMissingDb() {
            return failOnMissingDb;
        }

        @Override
        protected String getServerId() {
            return serverId;
        }

        @Override
        protected Settings getSettings() {
            return settings;
        }

        @Override
        boolean isSkipped() {
            return skip;
        }

        @Override
        void executePipeline(OracleJsonSchemaLogger logger, OracleJsonSchemaGeneratorConfig config) {
            this.capturedConfig = config;
        }

        @Override
        protected MavenProject getProject() {
            return project;
        }
    }
}
