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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.Provider;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSetContainer;

import java.util.List;

/**
 * Gradle plugin that adds opt-in schema discovery and generation support.
 *
 * @since 2.1.0
 */
public final class JsonSchemaRecordsGradlePlugin implements Plugin<Project> {

    /**
     * Apply the plugin.
     * @param project The target project
     */
    @Override
    public void apply(Project project) {
        Configuration providerConfiguration = project.getConfigurations().maybeCreate("jsonSchemaRecordsProviders");
        providerConfiguration.setCanBeConsumed(false);
        providerConfiguration.setCanBeResolved(true);

        JsonSchemaRecordsExtension extension = project.getExtensions().create("jsonSchemaRecords", JsonSchemaRecordsExtension.class);
        extension.getJdbcUrl().convention(gradlePropertyOrEnv(project, "jsonSchemaRecords.jdbcUrl", "DB_URL", "JSON_SCHEMA_RECORDS_JDBC_URL"));
        extension.getUsername().convention(gradlePropertyOrEnv(project, "jsonSchemaRecords.username", "DB_USER", "JSON_SCHEMA_RECORDS_USERNAME"));
        extension.getPassword().convention(gradlePropertyOrEnv(project, "jsonSchemaRecords.password", "DB_PASSWORD", "JSON_SCHEMA_RECORDS_PASSWORD"));
        extension.getLanguageLevel().convention(21);
        extension.getSchemaCacheDir().convention(project.getLayout().getBuildDirectory().dir("jsonschema-cache"));
        extension.getOutputDir().convention(project.getLayout().getBuildDirectory().dir("generated/sources/jsonschema"));
        extension.getSkipOnError().convention(false);
        extension.getFailOnMissingSource().convention(true);
        extension.getSources().convention(List.of());
        extension.getProviderClasspath().from(providerConfiguration);

        var taskProvider = project.getTasks().register("generateFromJsonSchemaSources", GenerateFromJsonSchemaSourcesTask.class, task -> {
            task.getJdbcUrl().convention(extension.getProviders().getOracle().getJdbcUrl().orElse(extension.getJdbcUrl()));
            task.getUsername().convention(extension.getProviders().getOracle().getUsername().orElse(extension.getUsername()));
            task.getPassword().convention(extension.getProviders().getOracle().getPassword().orElse(extension.getPassword()));
            task.getTargetPackage().convention(extension.getTargetPackage());
            task.getLanguageLevel().convention(extension.getLanguageLevel());
            task.getSchemaCacheDir().convention(extension.getSchemaCacheDir());
            task.getOutputDir().convention(extension.getOutputDir());
            task.getSources().convention(extension.getSources());
            task.getJdbcClasspath().from(extension.getJdbcClasspath());
            task.getProviderClasspath().from(extension.getProviderClasspath());
            task.getSkipOnError().convention(extension.getSkipOnError());
            task.getFailOnMissingSource().convention(extension.getFailOnMissingSource());
        });

        project.getPlugins().withType(JavaPlugin.class, ignored -> {
            SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
            sourceSets.named("main", sourceSet -> sourceSet.getJava().srcDir(extension.getOutputDir()));
            project.getTasks().named("compileJava").configure(task -> task.mustRunAfter(taskProvider));
        });
    }

    private Provider<String> gradlePropertyOrEnv(Project project, String gradleProperty, String... environmentVariables) {
        Provider<String> provider = project.getProviders().gradleProperty(gradleProperty);
        for (String environmentVariable : environmentVariables) {
            provider = provider.orElse(project.getProviders().environmentVariable(environmentVariable));
        }
        return provider;
    }
}
