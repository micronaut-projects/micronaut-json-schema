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

import io.micronaut.jsonschema.generator.oracle.JsonSchemaRecordsGeneratorConfig;
import io.micronaut.jsonschema.generator.oracle.JsonSchemaRecordsLogger;
import io.micronaut.jsonschema.generator.oracle.JsonSchemaRecordsPipeline;
import io.micronaut.jsonschema.generator.oracle.SourceSpec;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Opt-in task that discovers JSON Schemas from configured sources and generates Java records.
 *
 * @since 2.0.0
 */
public abstract class GenerateFromJsonSchemaSourcesTask extends DefaultTask {

    /**
     * @return The JDBC URL.
     */
    @Input
    @Optional
    public abstract Property<String> getJdbcUrl();

    /**
     * @return The database username.
     */
    @Input
    @Optional
    public abstract Property<String> getUsername();

    /**
     * @return The database password.
     */
    @Input
    @Optional
    public abstract Property<String> getPassword();

    /**
     * @return The target package.
     */
    @Input
    public abstract Property<String> getTargetPackage();

    /**
     * @return Java language level used for generation.
     */
    @Input
    public abstract Property<Integer> getLanguageLevel();

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
     * @return The configured discovery sources. Each map supports the keys
     * {@code name}, {@code providerClassName}, and {@code options}.
     */
    @Input
    public abstract ListProperty<Map<String, Object>> getSources();

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
     * @return Whether to skip individual failures.
     */
    @Input
    public abstract Property<Boolean> getSkipOnError();

    /**
     * @return Whether unavailable configured sources should fail the build.
     */
    @Input
    public abstract Property<Boolean> getFailOnMissingSource();

    /**
     * Execute the pipeline.
     *
     * @throws Exception If execution fails
     */
    @TaskAction
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
        executePipeline(logger, new JsonSchemaRecordsGeneratorConfig(
            getJdbcUrl().getOrNull(),
            getUsername().getOrNull(),
            getPassword().getOrNull(),
            getTargetPackage().get(),
            getLanguageLevel().getOrElse(21),
            getSchemaCacheDir().get().getAsFile().toPath(),
            getOutputDir().get().getAsFile().toPath(),
            toSourceSpecs(getSources().getOrElse(List.of())),
            getSkipOnError().getOrElse(false),
            getFailOnMissingSource().getOrElse(true)
        ), providerClassLoader);
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

    private List<SourceSpec> toSourceSpecs(List<Map<String, Object>> configuredSources) {
        return configuredSources.stream()
            .map(this::toSourceSpec)
            .toList();
    }

    private SourceSpec toSourceSpec(Map<String, Object> sourceMap) {
        Map<String, Object> safeMap = sourceMap == null ? Map.of() : sourceMap;
        return new SourceSpec(
            toStringValue(safeMap.get("name")),
            requiredStringValue("providerClassName", safeMap.get("providerClassName")),
            toStringMap(safeMap.get("options"))
        );
    }

    private Map<String, String> toStringMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> options)) {
            throw new IllegalArgumentException("Schema source options must be configured as a map.");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : options.entrySet()) {
            result.put(String.valueOf(entry.getKey()), toOptionValue(entry.getValue()));
        }
        return result;
    }

    private String toOptionValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Iterable<?> iterable) {
            StringBuilder builder = new StringBuilder();
            for (Object element : iterable) {
                if (!builder.isEmpty()) {
                    builder.append(',');
                }
                builder.append(element);
            }
            return builder.toString();
        }
        if (value.getClass().isArray()) {
            StringBuilder builder = new StringBuilder();
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                if (!builder.isEmpty()) {
                    builder.append(',');
                }
                builder.append(java.lang.reflect.Array.get(value, i));
            }
            return builder.toString();
        }
        return String.valueOf(value);
    }

    private String requiredStringValue(String name, Object value) {
        String stringValue = toStringValue(value);
        if (stringValue == null || stringValue.isBlank()) {
            throw new IllegalArgumentException("Missing required schema source field: " + name);
        }
        return stringValue;
    }

    private String toStringValue(Object value) {
        return value == null ? null : String.valueOf(value);
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
