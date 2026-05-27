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
import io.micronaut.jsonschema.generator.oracle.JsonSchemaRecordsPipeline;
import io.micronaut.jsonschema.generator.oracle.SourceSpec;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maven goal that discovers JSON Schemas and generates Java records.
 *
 * @since 2.0.0
 */
@Mojo(name = "generate-from-json-schema-sources", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class GenerateFromJsonSchemaSourcesMojo extends AbstractMojo {

    /**
     * JDBC URL.
     */
    @Parameter(property = "jsonSchemaRecords.jdbcUrl")
    private String jdbcUrl;

    /**
     * DB username.
     */
    @Parameter(property = "jsonSchemaRecords.username")
    private String username;

    /**
     * DB password.
     */
    @Parameter(property = "jsonSchemaRecords.password")
    private String password;

    /**
     * Optional Maven settings server identifier for credential lookup.
     */
    @Parameter(property = "jsonSchemaRecords.serverId")
    private String serverId;

    /**
     * Provider-family configuration.
     */
    @Parameter
    private ProvidersConfiguration providers;

    /**
     * Target package for generated types.
     */
    @Parameter(property = "jsonSchemaRecords.targetPackage")
    private String targetPackage;

    /**
     * Java language level used for generation.
     */
    @Parameter(property = "jsonSchemaRecords.languageLevel", defaultValue = "21")
    private int languageLevel = 21;

    /**
     * Discovery cache directory.
     */
    @Parameter(defaultValue = "${project.build.directory}/jsonschema-cache")
    private File schemaCacheDir;

    /**
     * Generated sources directory.
     */
    @Parameter(defaultValue = "${project.build.directory}/generated-sources/jsonschema")
    private File outputDir;

    /**
     * Configured discovery sources.
     */
    @Parameter
    private List<SourceConfiguration> sources = List.of();

    /**
     * Skip individual failures.
     */
    @Parameter(property = "jsonSchemaRecords.skipOnError", defaultValue = "false")
    private boolean skipOnError;

    /**
     * Fail when a configured source is unavailable.
     */
    @Parameter(property = "jsonSchemaRecords.failOnMissingSource", defaultValue = "true")
    private boolean failOnMissingSource = true;

    /**
     * Opt-in switch. The goal is inert unless this is set to {@code false}.
     */
    @Parameter(property = "jsonSchemaRecords.skip", defaultValue = "true")
    private boolean skip = true;

    /**
     * Maven project for source-root registration.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Maven settings for resolving server credentials.
     */
    @Parameter(defaultValue = "${settings}", readonly = true)
    private Settings settings;

    /**
     * Execute the goal.
     *
     * @throws MojoExecutionException If execution fails
     * @throws MojoFailureException If generation should fail the build
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (isSkipped()) {
            getLog().info("Skipping jsonSchemaRecords generation.");
            return;
        }
        try {
            String resolvedTargetPackage = requireValue("targetPackage", getTargetPackage());
            JsonSchemaRecordsLogger logger = new JsonSchemaRecordsLogger() {
                @Override
                public void info(String message) {
                    getLog().info(message);
                }

                @Override
                public void warn(String message) {
                    getLog().warn(message);
                }
            };
            executePipeline(logger, new JsonSchemaRecordsGeneratorConfig(
                getJdbcUrl(),
                getUsername(),
                getPassword(),
                resolvedTargetPackage,
                getLanguageLevel(),
                getSchemaCacheDir().toPath(),
                getOutputDir().toPath(),
                getSources().stream().map(SourceConfiguration::toSourceSpec).toList(),
                isSkipOnError(),
                isFailOnMissingSource()
            ));
            getProject().addCompileSourceRoot(getOutputDir().getAbsolutePath());
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("jsonSchemaRecords generation failed", e);
        }
    }

    /**
     * @return The JDBC URL.
     */
    protected String getJdbcUrl() {
        String configuredJdbcUrl = blankToNull(jdbcUrl);
        if (configuredJdbcUrl != null) {
            return configuredJdbcUrl;
        }
        String oracleJdbcUrl = getOracleProviderValue(OracleProviderConfiguration::getJdbcUrl);
        return oracleJdbcUrl == null ? blankToNull(System.getenv("DB_URL")) : oracleJdbcUrl;
    }

    /**
     * @return The database username.
     */
    protected String getUsername() {
        String configuredUsername = blankToNull(username);
        if (configuredUsername != null) {
            return configuredUsername;
        }
        String oracleUsername = getOracleProviderValue(OracleProviderConfiguration::getUsername);
        if (oracleUsername != null) {
            return oracleUsername;
        }
        Server server = getConfiguredServer();
        String settingsUsername = server == null ? null : blankToNull(server.getUsername());
        return settingsUsername == null ? blankToNull(System.getenv("DB_USER")) : settingsUsername;
    }

    /**
     * @return The database password.
     */
    protected String getPassword() {
        String configuredPassword = blankToNull(password);
        if (configuredPassword != null) {
            return configuredPassword;
        }
        String oraclePassword = getOracleProviderValue(OracleProviderConfiguration::getPassword);
        if (oraclePassword != null) {
            return oraclePassword;
        }
        Server server = getConfiguredServer();
        String settingsPassword = server == null ? null : blankToNull(server.getPassword());
        return settingsPassword == null ? blankToNull(System.getenv("DB_PASSWORD")) : settingsPassword;
    }

    /**
     * @return The optional Maven settings server identifier.
     */
    protected String getServerId() {
        return serverId;
    }

    /**
     * @return Provider-family configuration.
     */
    protected ProvidersConfiguration getProviders() {
        return providers;
    }

    /**
     * @return The target package.
     */
    protected String getTargetPackage() {
        return targetPackage;
    }

    /**
     * @return Java language level used for generation.
     */
    protected int getLanguageLevel() {
        return languageLevel;
    }

    /**
     * @return The schema cache directory.
     */
    protected File getSchemaCacheDir() {
        return schemaCacheDir;
    }

    /**
     * @return The output directory.
     */
    protected File getOutputDir() {
        return outputDir;
    }

    /**
     * @return Configured source definitions.
     */
    protected List<SourceConfiguration> getSources() {
        return sources;
    }

    /**
     * @return Whether skip-on-error is enabled.
     */
    protected boolean isSkipOnError() {
        return skipOnError;
    }

    /**
     * @return Whether missing source should fail the build.
     */
    protected boolean isFailOnMissingSource() {
        return failOnMissingSource;
    }

    /**
     * Whether the goal should skip execution.
     *
     * @return True if execution should be skipped
     */
    boolean isSkipped() {
        return skip;
    }

    /**
     * Execute the resolved schema records pipeline configuration.
     *
     * @param logger The logger to use for pipeline diagnostics
     * @param config The resolved generator configuration
     * @throws Exception If execution fails
     */
    void executePipeline(JsonSchemaRecordsLogger logger, JsonSchemaRecordsGeneratorConfig config) throws Exception {
        new JsonSchemaRecordsPipeline(logger, getClass().getClassLoader()).execute(config);
    }

    /**
     * @return The Maven project.
     */
    protected MavenProject getProject() {
        return project;
    }

    /**
     * @return Maven settings for credential lookup.
     */
    protected Settings getSettings() {
        return settings;
    }

    private Server getConfiguredServer() {
        String configuredServerId = blankToNull(getServerId());
        Settings currentSettings = getSettings();
        if (configuredServerId == null || currentSettings == null) {
            return null;
        }
        return currentSettings.getServer(configuredServerId);
    }

    private String getOracleProviderValue(java.util.function.Function<OracleProviderConfiguration, String> accessor) {
        ProvidersConfiguration configuredProviders = getProviders();
        if (configuredProviders == null || configuredProviders.getOracle() == null) {
            return null;
        }
        return blankToNull(accessor.apply(configuredProviders.getOracle()));
    }

    private String requireValue(String name, String value) throws MojoExecutionException {
        String resolved = blankToNull(value);
        if (resolved == null) {
            throw new MojoExecutionException("Missing required jsonSchemaRecords parameter: " + name);
        }
        return resolved;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * Maven-bound source configuration.
     */
    public static final class SourceConfiguration {
        /**
         * Stable source name.
         */
        @Parameter
        private String name;

        /**
         * Discovery provider class name.
         */
        @Parameter(required = true)
        private String providerClassName;

        /**
         * Provider-specific options.
         */
        @Parameter
        private Map<String, String> options = Map.of();

        /**
         * @return The configured source name.
         */
        public String getName() {
            return name;
        }

        /**
         * @return The discovery provider class name.
         */
        public String getProviderClassName() {
            return providerClassName;
        }

        /**
         * @return Provider-specific options.
         */
        public Map<String, String> getOptions() {
            return options;
        }

        SourceSpec toSourceSpec() {
            return new SourceSpec(name, providerClassName, options == null ? Map.of() : new LinkedHashMap<>(options));
        }
    }

    /**
     * Maven-bound provider-family configuration.
     */
    public static final class ProvidersConfiguration {
        /**
         * Oracle provider-family configuration.
         */
        @Parameter
        private OracleProviderConfiguration oracle;

        /**
         * @return Oracle provider-family configuration.
         */
        public OracleProviderConfiguration getOracle() {
            return oracle;
        }
    }

    /**
     * Maven-bound Oracle provider-family configuration.
     */
    public static final class OracleProviderConfiguration {
        /**
         * JDBC URL.
         */
        @Parameter
        private String jdbcUrl;

        /**
         * DB username.
         */
        @Parameter
        private String username;

        /**
         * DB password.
         */
        @Parameter
        private String password;

        /**
         * @return JDBC URL.
         */
        public String getJdbcUrl() {
            return jdbcUrl;
        }

        /**
         * @return DB username.
         */
        public String getUsername() {
            return username;
        }

        /**
         * @return DB password.
         */
        public String getPassword() {
            return password;
        }
    }
}
