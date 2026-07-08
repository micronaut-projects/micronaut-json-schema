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

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;

import java.util.Map;

/**
 * Shared Gradle task properties for JSON Schema discovery and source generation.
 *
 * @since 2.1.0
 */
@DisableCachingByDefault(because = "Schema discovery reads live provider metadata such as database state.")
public abstract class AbstractGenerateFromJsonSchemaSourcesTask extends DefaultTask {

    /**
     * @return JDBC URL
     */
    @Input
    @Optional
    public abstract Property<String> getJdbcUrl();

    /**
     * @return Database username
     */
    @Input
    @Optional
    public abstract Property<String> getUsername();

    /**
     * @return Database password
     */
    @Input
    @Optional
    public abstract Property<String> getPassword();

    /**
     * @return Target package for generated sources
     */
    @Input
    public abstract Property<String> getTargetPackage();

    /**
     * @return Generated source language
     */
    @Input
    @Optional
    public abstract Property<String> getLanguage();

    /**
     * @return Java language level used for generated Java sources
     */
    @Input
    @Optional
    public abstract Property<Integer> getLanguageLevel();

    /**
     * @return Directory where discovered schemas are cached
     */
    @OutputDirectory
    public abstract DirectoryProperty getSchemaCacheDir();

    /**
     * @return Directory where sources are generated
     */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    /**
     * @return Configured schema discovery sources
     */
    @Input
    public abstract ListProperty<Map<String, Object>> getSources();

    /**
     * @return Whether per-object discovery and generation failures should be skipped
     */
    @Input
    @Optional
    public abstract Property<Boolean> getSkipOnError();

    /**
     * @return Whether unavailable configured sources should fail the build
     */
    @Input
    @Optional
    public abstract Property<Boolean> getFailOnMissingSource();
}
