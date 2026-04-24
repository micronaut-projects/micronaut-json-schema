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

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import java.util.Map;

/**
 * Gradle extension backing Oracle JSON Schema discovery and record generation.
 *
 * @since 2.0.0
 */
public abstract class OracleJsonSchemaExtension {

    /**
     * @return The JDBC URL.
     */
    public abstract Property<String> getJdbcUrl();

    /**
     * @return The database username.
     */
    public abstract Property<String> getUsername();

    /**
     * @return The database password.
     */
    public abstract Property<String> getPassword();

    /**
     * @return The target package for generated Java sources.
     */
    public abstract Property<String> getTargetPackage();

    /**
     * @return The schema cache directory.
     */
    public abstract DirectoryProperty getSchemaCacheDir();

    /**
     * @return The generated sources output directory.
     */
    public abstract DirectoryProperty getOutputDir();

    /**
     * @return The configured discovery sources. Each map supports the keys
     * {@code name}, {@code providerClassName}, {@code owner}, and {@code options}.
     */
    public abstract ListProperty<Map<String, Object>> getSources();

    /**
     * @return The optional JDBC driver classpath.
     */
    public abstract ConfigurableFileCollection getJdbcClasspath();

    /**
     * @return The optional discovery provider classpath.
     */
    public abstract ConfigurableFileCollection getProviderClasspath();

    /**
     * @return Whether per-object failures should be skipped.
     */
    public abstract Property<Boolean> getSkipOnError();

    /**
     * @return Whether DB connection failures should fail the build.
     */
    public abstract Property<Boolean> getFailOnMissingDb();
}
