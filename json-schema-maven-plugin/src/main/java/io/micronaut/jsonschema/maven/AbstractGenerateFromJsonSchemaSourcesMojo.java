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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Shared Maven mojo state for JSON Schema discovery and source generation.
 *
 * @since 2.2.0
 */
public abstract class AbstractGenerateFromJsonSchemaSourcesMojo extends AbstractMojo {

    /**
     * Maven project.
     */
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    protected MavenProject project;

    /**
     * @return JDBC URL
     */
    protected abstract String getJdbcUrl();

    /**
     * @return Database username
     */
    protected abstract String getUsername();

    /**
     * @return Database password
     */
    protected abstract String getPassword();

    /**
     * @return Target package for generated sources
     */
    protected abstract String getTargetPackage();

    /**
     * @return Generated source language
     */
    protected abstract String getLanguage();

    /**
     * @return Java language level used for generated Java sources
     */
    protected abstract Integer getLanguageLevel();

    /**
     * @return Directory where discovered schemas are cached
     */
    protected abstract File getSchemaCacheDir();

    /**
     * @return Directory where sources are generated
     */
    protected abstract File getOutputDir();

    /**
     * @return Configured schema discovery sources
     */
    protected abstract List<Map<String, Object>> getSources();

    /**
     * @return Whether per-object discovery and generation failures should be skipped
     */
    protected abstract Boolean getSkipOnError();

    /**
     * @return Whether unavailable configured sources should fail the build
     */
    protected abstract Boolean getFailOnMissingSource();
}
