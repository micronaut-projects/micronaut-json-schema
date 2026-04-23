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
import io.micronaut.jsonschema.generator.oracle.OracleJsonSchemaPipeline;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

/**
 * Opt-in task that discovers JSON Schemas from Oracle and generates Java records.
 *
 * @since 2.0.0
 */
public abstract class OracleJsonSchemaGenerateTask extends DefaultTask {

    /**
     * @return The JDBC URL.
     */
    @Input
    public abstract Property<String> getJdbcUrl();

    /**
     * @return The database username.
     */
    @Input
    public abstract Property<String> getUsername();

    /**
     * @return The database password.
     */
    @Input
    public abstract Property<String> getPassword();

    /**
     * @return The optional owner.
     */
    @Input
    @Optional
    public abstract Property<String> getOwner();

    /**
     * @return The target package.
     */
    @Input
    public abstract Property<String> getTargetPackage();

    /**
     * @return The schema cache directory.
     */
    @OutputDirectory
    public abstract DirectoryProperty getSchemaCacheDir();

    /**
     * @return The generated sources directory.
     */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    /**
     * @return The included domains.
     */
    @Input
    public abstract ListProperty<String> getIncludeDomains();

    /**
     * @return The included duality views.
     */
    @Input
    public abstract ListProperty<String> getIncludeViews();

    /**
     * @return The explicitly enabled sources.
     */
    @Input
    public abstract ListProperty<String> getSources();

    /**
     * @return The optional JDBC driver classpath.
     */
    @Classpath
    public abstract ConfigurableFileCollection getJdbcClasspath();

    /**
     * @return Whether to skip individual failures.
     */
    @Input
    public abstract Property<Boolean> getSkipOnError();

    /**
     * @return Whether missing DB connectivity should fail the build.
     */
    @Input
    public abstract Property<Boolean> getFailOnMissingDb();

    /**
     * Execute the pipeline.
     * @throws Exception If execution fails
     */
    @TaskAction
    public void generate() throws Exception {
        registerJdbcDrivers();
        OracleJsonSchemaLogger logger = new OracleJsonSchemaLogger() {
            @Override
            public void info(String message) {
                getLogger().lifecycle(message);
            }

            @Override
            public void warn(String message) {
                getLogger().warn(message);
            }
        };
        executePipeline(logger, new OracleJsonSchemaGeneratorConfig(
            getJdbcUrl().get(),
            getUsername().get(),
            getPassword().get(),
            getOwner().getOrNull(),
            getTargetPackage().get(),
            getSchemaCacheDir().get().getAsFile().toPath(),
            getOutputDir().get().getAsFile().toPath(),
            getIncludeDomains().getOrElse(List.of()),
            getIncludeViews().getOrElse(List.of()),
            getSources().getOrElse(List.of()).stream().map(DiscoverySource::fromExternalName).toList(),
            getSkipOnError().getOrElse(false),
            getFailOnMissingDb().getOrElse(true)
        ));
    }

    /**
     * @return The output directory as a path for source-set registration.
     */
    @Internal
    public Path getGeneratedSourcesDirectory() {
        return getOutputDir().get().getAsFile().toPath();
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

    private void registerJdbcDrivers() throws SQLException {
        if (getJdbcClasspath().isEmpty()) {
            return;
        }
        URL[] urls = getJdbcClasspath().getFiles().stream()
            .map(file -> {
                try {
                    return file.toURI().toURL();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            })
            .toArray(URL[]::new);
        URLClassLoader classLoader = new URLClassLoader(urls, getClass().getClassLoader());
        try {
            Enumeration<URL> resources = classLoader.getResources("META-INF/services/java.sql.Driver");
            while (resources.hasMoreElements()) {
                resources.nextElement();
            }
            for (Driver driver : java.util.ServiceLoader.load(Driver.class, classLoader)) {
                DriverManager.registerDriver(new DriverShim(driver));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load JDBC drivers from jdbcClasspath", e);
        }
    }

    private record DriverShim(Driver delegate) implements Driver {
        @Override
        public java.sql.Connection connect(String url, Properties info) throws SQLException {
            return delegate.connect(url, info);
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return delegate.acceptsURL(url);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
            return delegate.getPropertyInfo(url, info);
        }

        @Override
        public int getMajorVersion() {
            return delegate.getMajorVersion();
        }

        @Override
        public int getMinorVersion() {
            return delegate.getMinorVersion();
        }

        @Override
        public boolean jdbcCompliant() {
            return delegate.jdbcCompliant();
        }

        @Override
        public java.util.logging.Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }
    }
}
