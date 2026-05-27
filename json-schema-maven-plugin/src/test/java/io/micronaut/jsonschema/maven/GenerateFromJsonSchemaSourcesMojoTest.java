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

import io.micronaut.jsonschema.generator.oracle.JsonSchemaRecordsGeneratorConfig;
import io.micronaut.jsonschema.generator.oracle.JsonSchemaRecordsLogger;
import io.micronaut.jsonschema.generator.oracle.SourceSpec;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the Maven jsonSchemaRecords mojo.
 */
class GenerateFromJsonSchemaSourcesMojoTest {

    @Test
    void executeSkipsWhenOptInFlagIsDisabled(@TempDir Path tempDir) throws Exception {
        TestGenerateFromJsonSchemaSourcesMojo mojo = new TestGenerateFromJsonSchemaSourcesMojo();
        mojo.skip = true;
        mojo.project = new MavenProject();
        mojo.outputDir = tempDir.resolve("generated-sources").toFile();

        mojo.execute();

        assertNull(mojo.capturedConfig);
        assertTrue(mojo.project.getCompileSourceRoots().isEmpty());
    }

    @Test
    void executePassesMojoConfigurationToPipelineAndRegistersSources(@TempDir Path tempDir) throws Exception {
        TestGenerateFromJsonSchemaSourcesMojo mojo = new TestGenerateFromJsonSchemaSourcesMojo();
        mojo.skip = false;
        mojo.jdbcUrl = "jdbc:oracle:thin:@localhost:1521/FREEPDB1";
        mojo.username = "app";
        mojo.password = "secret";
        mojo.targetPackage = "io.micronaut.jsonschema.oracle.generated";
        mojo.languageLevel = 17;
        mojo.schemaCacheDir = tempDir.resolve("schema-cache").toFile();
        mojo.outputDir = tempDir.resolve("generated-sources").toFile();
        mojo.sources = List.of(
            source("domains", "io.micronaut.jsonschema.generator.oracle.OracleDomainSchemaDiscoveryProvider", Map.of("owner", "APP", "include", "APP_JSON")),
            source("views", "io.micronaut.jsonschema.generator.oracle.OracleDualityViewSchemaDiscoveryProvider", Map.of("owner", "APP", "include", "APP_VIEW"))
        );
        mojo.skipOnError = true;
        mojo.failOnMissingSource = false;
        mojo.project = new MavenProject();

        mojo.execute();

        JsonSchemaRecordsGeneratorConfig config = mojo.capturedConfig;
        assertNotNull(config);
        assertEquals("jdbc:oracle:thin:@localhost:1521/FREEPDB1", config.jdbcUrl());
        assertEquals("app", config.username());
        assertEquals("secret", config.password());
        assertEquals("io.micronaut.jsonschema.oracle.generated", config.targetPackage());
        assertEquals(17, config.languageLevel());
        assertEquals(tempDir.resolve("schema-cache"), config.schemaCacheDir());
        assertEquals(tempDir.resolve("generated-sources"), config.outputDir());
        assertEquals(List.of(
            new SourceSpec("domains", "io.micronaut.jsonschema.generator.oracle.OracleDomainSchemaDiscoveryProvider", Map.of("owner", "APP", "include", "APP_JSON")),
            new SourceSpec("views", "io.micronaut.jsonschema.generator.oracle.OracleDualityViewSchemaDiscoveryProvider", Map.of("owner", "APP", "include", "APP_VIEW"))
        ), config.sources());
        assertTrue(config.skipOnError());
        assertFalse(config.failOnMissingSource());
        assertEquals(List.of(tempDir.resolve("generated-sources").toFile().getAbsolutePath()), mojo.project.getCompileSourceRoots());
    }

    @Test
    void executeWrapsPipelineFailure(@TempDir Path tempDir) {
        TestGenerateFromJsonSchemaSourcesMojo mojo = new TestGenerateFromJsonSchemaSourcesMojo();
        IOException failure = new IOException("boom");
        mojo.skip = false;
        mojo.jdbcUrl = "jdbc:oracle:thin:@localhost:1521/FREEPDB1";
        mojo.username = "app";
        mojo.password = "secret";
        mojo.targetPackage = "io.micronaut.jsonschema.oracle.generated";
        mojo.schemaCacheDir = tempDir.resolve("schema-cache").toFile();
        mojo.outputDir = tempDir.resolve("generated-sources").toFile();
        mojo.sources = List.of(source("domains", "io.micronaut.jsonschema.generator.oracle.OracleDomainSchemaDiscoveryProvider", Map.of("include", "APP_JSON")));
        mojo.project = new MavenProject();
        mojo.failure = failure;

        MojoExecutionException exception = assertThrows(MojoExecutionException.class, mojo::execute);

        assertEquals("jsonSchemaRecords generation failed", exception.getMessage());
        assertSame(failure, exception.getCause());
    }

    @Test
    void executeResolvesCredentialsFromSettingsServer(@TempDir Path tempDir) throws Exception {
        SettingsBackedGenerateFromJsonSchemaSourcesMojo mojo = new SettingsBackedGenerateFromJsonSchemaSourcesMojo();
        Settings settings = new Settings();
        Server server = new Server();
        server.setId("jsonschema");
        server.setUsername("settings-user");
        server.setPassword("settings-secret");
        settings.addServer(server);
        mojo.skip = false;
        mojo.jdbcUrl = "jdbc:oracle:thin:@localhost:1521/FREEPDB1";
        mojo.serverId = "jsonschema";
        mojo.settings = settings;
        mojo.targetPackage = "io.micronaut.jsonschema.oracle.generated";
        mojo.schemaCacheDir = tempDir.resolve("schema-cache").toFile();
        mojo.outputDir = tempDir.resolve("generated-sources").toFile();
        mojo.sources = List.of(source("domains", "io.micronaut.jsonschema.generator.oracle.OracleDomainSchemaDiscoveryProvider", Map.of("owner", "APP", "include", "APP_JSON")));
        mojo.project = new MavenProject();

        mojo.execute();

        JsonSchemaRecordsGeneratorConfig config = mojo.capturedConfig;
        assertNotNull(config);
        assertEquals("settings-user", config.username());
        assertEquals("settings-secret", config.password());
    }

    @Test
    void executeAllowsCredentialsToBeOmittedForNonCredentialedSources(@TempDir Path tempDir) throws Exception {
        TestGenerateFromJsonSchemaSourcesMojo mojo = new TestGenerateFromJsonSchemaSourcesMojo();
        mojo.skip = false;
        mojo.jdbcUrl = "jdbc:oracle:thin:@localhost:1521/FREEPDB1";
        mojo.targetPackage = "io.micronaut.jsonschema.oracle.generated";
        mojo.schemaCacheDir = tempDir.resolve("schema-cache").toFile();
        mojo.outputDir = tempDir.resolve("generated-sources").toFile();
        mojo.sources = List.of(source("domains", "io.micronaut.jsonschema.generator.oracle.OracleDomainSchemaDiscoveryProvider", Map.of("owner", "APP", "include", "APP_JSON")));
        mojo.project = new MavenProject();

        mojo.execute();

        JsonSchemaRecordsGeneratorConfig config = mojo.capturedConfig;
        assertNotNull(config);
        assertNull(config.username());
        assertNull(config.password());
    }

    @Test
    void executeResolvesCredentialsFromProviderFamilyConfiguration(@TempDir Path tempDir) throws Exception {
        ProviderBackedGenerateFromJsonSchemaSourcesMojo mojo = new ProviderBackedGenerateFromJsonSchemaSourcesMojo();
        GenerateFromJsonSchemaSourcesMojo.ProvidersConfiguration providers = new GenerateFromJsonSchemaSourcesMojo.ProvidersConfiguration();
        GenerateFromJsonSchemaSourcesMojo.OracleProviderConfiguration oracle = new GenerateFromJsonSchemaSourcesMojo.OracleProviderConfiguration();
        setField(oracle, "jdbcUrl", "jdbc:oracle:thin:@localhost:1521/FREEPDB1");
        setField(oracle, "username", "app");
        setField(oracle, "password", "secret");
        setField(providers, "oracle", oracle);
        setField(mojo, "skip", false);
        setField(mojo, "providers", providers);
        setField(mojo, "targetPackage", "io.micronaut.jsonschema.oracle.generated");
        setField(mojo, "schemaCacheDir", tempDir.resolve("schema-cache").toFile());
        setField(mojo, "outputDir", tempDir.resolve("generated-sources").toFile());
        setField(mojo, "sources", List.of(source("domains", "io.micronaut.jsonschema.generator.oracle.OracleDomainSchemaDiscoveryProvider", Map.of("include", "APP_JSON"))));

        mojo.execute();

        JsonSchemaRecordsGeneratorConfig config = mojo.capturedConfig;
        assertNotNull(config);
        assertEquals("jdbc:oracle:thin:@localhost:1521/FREEPDB1", config.jdbcUrl());
        assertEquals("app", config.username());
        assertEquals("secret", config.password());
    }

    private static GenerateFromJsonSchemaSourcesMojo.SourceConfiguration source(String name,
                                                                               String providerClassName,
                                                                               Map<String, String> options) {
        GenerateFromJsonSchemaSourcesMojo.SourceConfiguration source = new GenerateFromJsonSchemaSourcesMojo.SourceConfiguration();
        setField(source, "name", name);
        setField(source, "providerClassName", providerClassName);
        setField(source, "options", new LinkedHashMap<>(options));
        return source;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            java.lang.reflect.Field field = findField(target.getClass(), name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static java.lang.reflect.Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> currentType = type;
        while (currentType != null) {
            try {
                return currentType.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                currentType = currentType.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    /**
     * Mojo variant that captures pipeline execution without reaching Oracle.
     */
    static final class TestGenerateFromJsonSchemaSourcesMojo extends GenerateFromJsonSchemaSourcesMojo {
        private String jdbcUrl;
        private String username;
        private String password;
        private String targetPackage;
        private int languageLevel = 21;
        private File schemaCacheDir;
        private File outputDir;
        private List<SourceConfiguration> sources = List.of();
        private boolean skipOnError;
        private boolean failOnMissingSource = true;
        private boolean skip = true;
        private MavenProject project = new MavenProject();
        private JsonSchemaRecordsGeneratorConfig capturedConfig;
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
        protected String getTargetPackage() {
            return targetPackage;
        }

        @Override
        protected int getLanguageLevel() {
            return languageLevel;
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
        protected List<SourceConfiguration> getSources() {
            return sources;
        }

        @Override
        protected boolean isSkipOnError() {
            return skipOnError;
        }

        @Override
        protected boolean isFailOnMissingSource() {
            return failOnMissingSource;
        }

        @Override
        boolean isSkipped() {
            return skip;
        }

        @Override
        void executePipeline(JsonSchemaRecordsLogger logger, JsonSchemaRecordsGeneratorConfig config) throws Exception {
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
    static final class SettingsBackedGenerateFromJsonSchemaSourcesMojo extends GenerateFromJsonSchemaSourcesMojo {
        private String jdbcUrl;
        private String targetPackage;
        private int languageLevel = 21;
        private File schemaCacheDir;
        private File outputDir;
        private List<SourceConfiguration> sources = List.of();
        private boolean skipOnError;
        private boolean failOnMissingSource = true;
        private boolean skip = true;
        private String serverId;
        private Settings settings;
        private MavenProject project = new MavenProject();
        private JsonSchemaRecordsGeneratorConfig capturedConfig;

        @Override
        protected String getJdbcUrl() {
            return jdbcUrl;
        }

        @Override
        protected String getTargetPackage() {
            return targetPackage;
        }

        @Override
        protected int getLanguageLevel() {
            return languageLevel;
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
        protected List<SourceConfiguration> getSources() {
            return sources;
        }

        @Override
        protected boolean isSkipOnError() {
            return skipOnError;
        }

        @Override
        protected boolean isFailOnMissingSource() {
            return failOnMissingSource;
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
        void executePipeline(JsonSchemaRecordsLogger logger, JsonSchemaRecordsGeneratorConfig config) {
            this.capturedConfig = config;
        }

        @Override
        protected MavenProject getProject() {
            return project;
        }
    }

    /**
     * Mojo variant that uses base configuration resolution and captures pipeline execution.
     */
    static final class ProviderBackedGenerateFromJsonSchemaSourcesMojo extends GenerateFromJsonSchemaSourcesMojo {
        private final MavenProject project = new MavenProject();
        private JsonSchemaRecordsGeneratorConfig capturedConfig;

        @Override
        void executePipeline(JsonSchemaRecordsLogger logger, JsonSchemaRecordsGeneratorConfig config) {
            this.capturedConfig = config;
        }

        @Override
        protected MavenProject getProject() {
            return project;
        }
    }
}
