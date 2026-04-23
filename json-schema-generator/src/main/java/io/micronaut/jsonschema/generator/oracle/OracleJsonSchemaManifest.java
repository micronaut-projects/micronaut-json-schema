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
package io.micronaut.jsonschema.generator.oracle;

import io.micronaut.core.annotation.Internal;
import io.micronaut.jsonschema.generator.oracle.OracleJsonSchemaGeneratorConfig.Scope;
import io.micronaut.jsonschema.generator.oracle.OracleJsonSchemaGeneratorConfig.Step;

import java.util.List;

/**
 * Manifest written by the Oracle discovery pipeline.
 *
 * @param generator Generator identity
 * @param createdAt Creation timestamp
 * @param connection Connection metadata
 * @param parameters Effective parameters
 * @param discovery Discovery result
 * @param warnings Warnings emitted during discovery or generation
 * @param skipped Skipped objects when {@code skipOnError=true}
 * @param emittedSchemaFiles Relative schema file paths
 * @since 2.0.0
 */
@Internal
public record OracleJsonSchemaManifest(
    Generator generator,
    String createdAt,
    Connection connection,
    Parameters parameters,
    Discovery discovery,
    List<Warning> warnings,
    List<Skipped> skipped,
    List<String> emittedSchemaFiles
) {
    /**
     * Generator metadata.
     * @param name Generator name
     * @param version Generator version
     */
    public record Generator(String name, String version) {
    }

    /**
     * Connection metadata with sanitized values only.
     * @param jdbcUrlSanitized Sanitized JDBC URL
     * @param owner Optional owner
     */
    public record Connection(String jdbcUrlSanitized, String owner) {
    }

    /**
     * Effective pipeline parameters.
     * @param targetPackage Target package
     * @param schemaCacheDir Schema cache directory
     * @param outputDir Output directory
     * @param includeDomains Domain filter
     * @param includeViews View filter
     * @param sources Enabled sources
     * @param skipOnError Skip toggle
     * @param failOnMissingDb Missing DB toggle
     */
    public record Parameters(
        String targetPackage,
        String schemaCacheDir,
        String outputDir,
        List<String> includeDomains,
        List<String> includeViews,
        List<String> sources,
        boolean skipOnError,
        boolean failOnMissingDb
    ) {
    }

    /**
     * Discovery section of the manifest.
     * @param domains Discovered domains
     * @param dualityViews Discovered duality views
     */
    public record Discovery(
        List<SchemaFile> domains,
        List<SchemaFile> dualityViews
    ) {
    }

    /**
     * A discovered schema file entry.
     * @param name Input name
     * @param schemaFile Relative schema file path
     * @param source Retrieval source
     */
    public record SchemaFile(
        String name,
        String schemaFile,
        String source
    ) {
    }

    /**
     * A non-fatal warning entry.
     * @param scope Warning scope
     * @param name Input name
     * @param step Pipeline step
     * @param code Warning code
     * @param message Warning message
     */
    public record Warning(
        Scope scope,
        String name,
        Step step,
        String code,
        String message
    ) {
    }

    /**
     * A skipped input entry.
     * @param scope Skip scope
     * @param name Input name
     * @param step Pipeline step
     * @param code Skip code
     * @param reason Skip reason
     * @param source Retrieval source
     */
    public record Skipped(
        Scope scope,
        String name,
        Step step,
        String code,
        String reason,
        String source
    ) {
    }
}
