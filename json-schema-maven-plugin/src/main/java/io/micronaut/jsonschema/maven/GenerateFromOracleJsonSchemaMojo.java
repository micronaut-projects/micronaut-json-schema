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
import io.micronaut.jsonschema.generator.oracle.OracleJsonSchemaPipeline;
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
import java.util.List;

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
     * Optional owner for cross-schema discovery.
     */
    @Parameter(property = "oracleJsonSchema.owner")
    private String owner;

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
     * Included domain names.
     */
    @Parameter
    private List<String> includeDomains = List.of();

    /**
     * Included duality view names.
     */
    @Parameter
    private List<String> includeViews = List.of();

    /**
     * Explicitly enabled discovery sources.
     */
    @Parameter
    private List<String> sources = List.of();

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
                getOwner(),
                resolvedTargetPackage,
                getSchemaCacheDir().toPath(),
                getOutputDir().toPath(),
                getIncludeDomains(),
                getIncludeViews(),
                getSources().stream().map(DiscoverySource::fromExternalName).toList(),
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
     * @return The optional owner.
     */
    protected String getOwner() {
        return owner;
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
     * @return Included domain names.
     */
    protected List<String> getIncludeDomains() {
        return includeDomains;
    }

    /**
     * @return Included duality view names.
     */
    protected List<String> getIncludeViews() {
        return includeViews;
    }

    /**
     * @return Enabled source names.
     */
    protected List<String> getSources() {
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
     * <p>
     * This hook exists for tests and specialized subclasses that need to override the opt-in
     * behavior without mutating the bound Maven parameter directly.
     *
     * @return True if execution should be skipped
     */
    boolean isSkipped() {
        return skip;
    }

    /**
     * Execute the resolved Oracle pipeline configuration.
     * <p>
     * This method exists so tests can replace the live pipeline invocation. Production subclasses
     * should preserve the contract of executing the supplied config exactly once.
     *
     * @param logger The logger to use for pipeline diagnostics
     * @param config The resolved generator configuration
     * @throws Exception If execution fails
     */
    void executePipeline(OracleJsonSchemaLogger logger, OracleJsonSchemaGeneratorConfig config) throws Exception {
        new OracleJsonSchemaPipeline(logger).execute(config);
    }

    /**
     * @return The Maven project.
     */
    protected MavenProject getProject() {
        return project;
    }

    /**
     * @return The Maven settings instance.
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
        if (blankToNull(value) == null) {
            throw new MojoExecutionException("Missing required Oracle JSON Schema parameter: " + name);
        }
        return value;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
