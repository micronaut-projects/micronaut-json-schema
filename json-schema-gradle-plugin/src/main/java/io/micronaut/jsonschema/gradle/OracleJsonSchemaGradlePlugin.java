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
import org.gradle.api.provider.Provider;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSetContainer;

import java.util.List;

/**
 * Gradle plugin that adds opt-in Oracle discovery and generation support.
 *
 * @since 2.0.0
 */
public final class OracleJsonSchemaGradlePlugin implements Plugin<Project> {

    /**
     * Apply the plugin.
     * @param project The target project
     */
    @Override
    public void apply(Project project) {
        OracleJsonSchemaExtension extension = project.getExtensions().create("oracleJsonSchema", OracleJsonSchemaExtension.class);
        extension.getJdbcUrl().convention(gradlePropertyOrEnv(project, "oracleJsonSchema.jdbcUrl", "ORACLE_JSON_SCHEMA_JDBC_URL"));
        extension.getUsername().convention(gradlePropertyOrEnv(project, "oracleJsonSchema.username", "ORACLE_JSON_SCHEMA_USERNAME"));
        extension.getPassword().convention(gradlePropertyOrEnv(project, "oracleJsonSchema.password", "ORACLE_JSON_SCHEMA_PASSWORD"));
        extension.getOwner().convention(gradlePropertyOrEnv(project, "oracleJsonSchema.owner", "ORACLE_JSON_SCHEMA_OWNER"));
        extension.getSchemaCacheDir().convention(project.getLayout().getBuildDirectory().dir("oracle-jsonschema-cache"));
        extension.getOutputDir().convention(project.getLayout().getBuildDirectory().dir("generated/sources/oracle-jsonschema"));
        extension.getSkipOnError().convention(false);
        extension.getFailOnMissingDb().convention(true);
        extension.getIncludeDomains().convention(List.of());
        extension.getIncludeViews().convention(List.of());
        extension.getSources().convention(List.of());

        var taskProvider = project.getTasks().register("generateFromOracleJsonSchema", OracleJsonSchemaGenerateTask.class, task -> {
            task.getJdbcUrl().convention(extension.getJdbcUrl());
            task.getUsername().convention(extension.getUsername());
            task.getPassword().convention(extension.getPassword());
            task.getOwner().convention(extension.getOwner());
            task.getTargetPackage().convention(extension.getTargetPackage());
            task.getSchemaCacheDir().convention(extension.getSchemaCacheDir());
            task.getOutputDir().convention(extension.getOutputDir());
            task.getIncludeDomains().convention(extension.getIncludeDomains());
            task.getIncludeViews().convention(extension.getIncludeViews());
            task.getSources().convention(extension.getSources());
            task.getJdbcClasspath().from(extension.getJdbcClasspath());
            task.getSkipOnError().convention(extension.getSkipOnError());
            task.getFailOnMissingDb().convention(extension.getFailOnMissingDb());
        });

        project.getPlugins().withType(JavaPlugin.class, ignored -> {
            SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
            sourceSets.named("main", sourceSet -> sourceSet.getJava().srcDir(taskProvider.map(OracleJsonSchemaGenerateTask::getGeneratedSourcesDirectory)));
            project.getTasks().named("compileJava").configure(task -> task.mustRunAfter(taskProvider));
        });
    }

    private Provider<String> gradlePropertyOrEnv(Project project, String gradleProperty, String environmentVariable) {
        return project.getProviders().gradleProperty(gradleProperty)
            .orElse(project.getProviders().environmentVariable(environmentVariable));
    }
}
