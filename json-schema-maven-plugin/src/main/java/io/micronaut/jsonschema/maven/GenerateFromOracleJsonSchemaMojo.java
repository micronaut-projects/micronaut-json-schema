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
import io.micronaut.jsonschema.generator.oracle.OracleJsonSchemaLogger;
import io.micronaut.jsonschema.generator.oracle.OracleJsonSchemaPipeline;
import io.micronaut.jsonschema.generator.oracle.OracleSourceSpec;
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
 * Maven goal that discovers Oracle-backed JSON Schemas and generates Java records.
 *
 * @since 2.0.0
 */
@Mojo(name = "generate-from-oracle-json-schema", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class GenerateFromOracleJsonSchemaMojo extends AbstractMojo {

    /**
     * JDBC URL.
     */
    @Parameter(property = "oracleJsonSchema.jdbcUrl")
    private String jdbcUrl;

    /**
     * DB username.
     */
    @Parameter(property = "oracleJsonSchema.username")
    private String username;

    /**
     * DB password.
     */
    @Parameter(property = "oracleJsonSchema.password")
    private String password;

    /**
     * Optional Maven settings server identifier for credential lookup.
     */
    @Parameter(property = "oracleJsonSchema.serverId")
    private String serverId;

    /**
     * Target package for generated types.
     */
    @Parameter(property = "oracleJsonSchema.targetPackage")
    private String targetPackage;

    /**
     * Discovery cache directory.
     */
    @Parameter(defaultValue = "${project.build.directory}/oracle-jsonschema-cache")
    private File schemaCacheDir;

    /**
     * Generated sources directory.
     */
    @Parameter(defaultValue = "${project.build.directory}/generated/sources/oracle-jsonschema")
    private File outputDir;

    /**
     * Configured discovery sources.
     */
    @Parameter
    private List<SourceConfiguration> sources = List.of();

    /**
     * Skip individual failures.
     */
    @Parameter(defaultValue = "false")
    private boolean skipOnError;

    /**
     * Fail when the DB is unavailable.
     */
    @Parameter(defaultValue = "true")
    private boolean failOnMissingDb = true;

    /**
     * Opt-in switch. The goal is inert unless this is set to {@code false}.
     */
    @Parameter(property = "oracleJsonSchema.skip", defaultValue = "true")
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
            getLog().info("Skipping Oracle JSON Schema generation.");
            return;
        }
        try {
            String resolvedJdbcUrl = requireValue("jdbcUrl", getJdbcUrl());
            String resolvedUsername = requireValue("username", getUsername());
            String resolvedPassword = requireValue("password", getPassword());
            String resolvedTargetPackage = requireValue("targetPackage", getTargetPackage());
            OracleJsonSchemaLogger logger = new OracleJsonSchemaLogger() {
                @Override
                public void info(String message) {
                    getLog().info(message);
                }

                @Override
                public void warn(String message) {
                    getLog().warn(message);
                }
            };
            executePipeline(logger, new OracleJsonSchemaGeneratorConfig(
                resolvedJdbcUrl,
                resolvedUsername,
                resolvedPassword,
                resolvedTargetPackage,
                getSchemaCacheDir().toPath(),
                getOutputDir().toPath(),
                getSources().stream().map(SourceConfiguration::toSourceSpec).toList(),
                isSkipOnError(),
                isFailOnMissingDb()
            ));
            getProject().addCompileSourceRoot(getOutputDir().getAbsolutePath());
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Oracle JSON Schema generation failed", e);
        }
    }

    /**
     * @return The JDBC URL.
     */
    protected String getJdbcUrl() {
        return jdbcUrl;
    }

    /**
     * @return The database username.
     */
    protected String getUsername() {
        String configuredUsername = blankToNull(username);
        if (configuredUsername != null) {
            return configuredUsername;
        }
        Server server = getConfiguredServer();
        return server == null ? null : blankToNull(server.getUsername());
    }

    /**
     * @return The database password.
     */
    protected String getPassword() {
        String configuredPassword = blankToNull(password);
        if (configuredPassword != null) {
            return configuredPassword;
        }
        Server server = getConfiguredServer();
        return server == null ? null : blankToNull(server.getPassword());
    }

    /**
     * @return The optional Maven settings server identifier.
     */
    protected String getServerId() {
        return serverId;
    }

    /**
     * @return The target package.
     */
    protected String getTargetPackage() {
        return targetPackage;
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
     * @return Whether missing DB should fail the build.
     */
    protected boolean isFailOnMissingDb() {
        return failOnMissingDb;
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
     * Execute the resolved Oracle pipeline configuration.
     *
     * @param logger The logger to use for pipeline diagnostics
     * @param config The resolved generator configuration
     * @throws Exception If execution fails
     */
    void executePipeline(OracleJsonSchemaLogger logger, OracleJsonSchemaGeneratorConfig config) throws Exception {
        new OracleJsonSchemaPipeline(logger, getClass().getClassLoader()).execute(config);
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

    private String requireValue(String name, String value) throws MojoExecutionException {
        String resolved = blankToNull(value);
        if (resolved == null) {
            throw new MojoExecutionException("Missing required Oracle JSON Schema parameter: " + name);
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
         * Optional owner.
         */
        @Parameter
        private String owner;

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
         * @return The optional owner.
         */
        public String getOwner() {
            return owner;
        }

        /**
         * @return Provider-specific options.
         */
        public Map<String, String> getOptions() {
            return options;
        }

        OracleSourceSpec toSourceSpec() {
            return new OracleSourceSpec(name, providerClassName, owner, options == null ? Map.of() : new LinkedHashMap<>(options));
        }
    }
}
