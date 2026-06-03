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

import io.micronaut.jsonschema.generator.records.JsonSchemaRecordsGeneration;
import io.micronaut.jsonschema.generator.records.JsonSchemaRecordsGeneratorConfig;
import io.micronaut.jsonschema.generator.discovery.JsonSchemaRecordsLogger;
import io.micronaut.jsonschema.generator.records.JsonSchemaRecordsPipeline;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Internal;
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
import java.util.Map;
import java.util.Properties;

/**
 * Opt-in task that discovers JSON Schemas from configured sources and generates Java records.
 *
 * @since 2.0.0
 */
public abstract class GenerateFromJsonSchemaSourcesTask extends AbstractGenerateFromJsonSchemaSourcesTask {

    /**
     * @return The optional JDBC driver classpath.
     */
    @Classpath
    public abstract ConfigurableFileCollection getJdbcClasspath();

    /**
     * @return The optional provider classpath.
     */
    @Classpath
    public abstract ConfigurableFileCollection getProviderClasspath();

    /**
     * Execute the pipeline.
     */
    @TaskAction
    @Override
    public void execute() {
        try {
            generate();
        } catch (Exception e) {
            throw new IllegalStateException("jsonSchemaRecords generation failed", e);
        }
    }

    /**
     * Execute the pipeline.
     *
     * @throws Exception If execution fails
     */
    public void generate() throws Exception {
        registerJdbcDrivers();
        ClassLoader providerClassLoader = createClassLoader(getProviderClasspath(), getClass().getClassLoader());
        JsonSchemaRecordsLogger logger = new JsonSchemaRecordsLogger() {
            @Override
            public void info(String message) {
                getLogger().lifecycle(message);
            }

            @Override
            public void warn(String message) {
                getLogger().warn(message);
            }
        };
        JsonSchemaRecordsGeneration generation = new JsonSchemaRecordsGeneration(
            getJdbcUrl().getOrNull(),
            getUsername().getOrNull(),
            getPassword().getOrNull(),
            getTnsAdmin().getOrNull(),
            getWalletLocation().getOrNull(),
            getJdbcProperties().getOrElse(Map.of()),
            getTargetPackage().get(),
            getLanguageLevel().getOrElse(21),
            getSchemaCacheDir().get().getAsFile(),
            getOutputDir().get().getAsFile(),
            getSources().getOrElse(List.of()),
            getSkipOnError().getOrElse(false),
            getFailOnMissingSource().getOrElse(true)
        );
        executePipeline(logger, generation.toGeneratorConfig(), providerClassLoader);
    }

    /**
     * @return The output directory as a path for source-set registration.
     */
    @Internal
    public Path getGeneratedSourcesDirectory() {
        return getOutputDir().get().getAsFile().toPath();
    }

    /**
     * Execute the resolved schema records pipeline configuration.
     *
     * @param logger The logger to use for pipeline diagnostics
     * @param config The resolved generator configuration
     * @param providerClassLoader The classloader used to resolve discovery providers
     * @throws Exception If execution fails
     */
    void executePipeline(JsonSchemaRecordsLogger logger,
                         JsonSchemaRecordsGeneratorConfig config,
                         ClassLoader providerClassLoader) throws Exception {
        new JsonSchemaRecordsPipeline(logger, providerClassLoader).execute(config);
    }

    private void registerJdbcDrivers() throws SQLException {
        if (getJdbcClasspath().isEmpty()) {
            return;
        }
        try {
            ClassLoader classLoader = createClassLoader(getJdbcClasspath(), getClass().getClassLoader());
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

    private ClassLoader createClassLoader(ConfigurableFileCollection files, ClassLoader parent) {
        if (files.isEmpty()) {
            return parent;
        }
        URL[] urls = files.getFiles().stream()
            .map(file -> {
                try {
                    return file.toURI().toURL();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            })
            .toArray(URL[]::new);
        return new URLClassLoader(urls, parent);
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
