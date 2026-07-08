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
package io.micronaut.jsonschema.generator.records;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.jsonschema.generator.discovery.DiscoveryStep;
import io.micronaut.core.annotation.Internal;

import java.util.List;
import java.util.Map;

/**
 * Manifest written by the schema records pipeline.
 *
 * @param generator Generator identity
 * @param createdAt Creation timestamp
 * @param sourceMetadata Sanitized provider source metadata
 * @param parameters Effective parameters
 * @param discovery Discovery result
 * @param warnings Warnings emitted during discovery or generation
 * @param skipped Skipped objects when {@code skipOnError=true}
 * @param emittedSchemaFiles Relative schema file paths
 * @param generatedSourceFiles Relative generated source file paths
 * @since 2.1.0
 */
@Internal
public record JsonSchemaRecordsManifest(
    Generator generator,
    String createdAt,
    List<SourceMetadata> sourceMetadata,
    Parameters parameters,
    Discovery discovery,
    List<Warning> warnings,
    List<Skipped> skipped,
    List<String> emittedSchemaFiles,
    List<String> generatedSourceFiles
) {
    /**
     * Generator metadata.
     *
     * @param name Generator name
     * @param version Generator version
     */
    public record Generator(String name, String version) {
    }

    /**
     * Source metadata with sanitized values only.
     *
     * @param sourceName Configured source name
     * @param provider Discovery provider id
     * @param metadata Sanitized metadata
     */
    public record SourceMetadata(
        String sourceName,
        String provider,
        Map<String, Object> metadata
    ) {
    }

    /**
     * Effective pipeline parameters.
     *
     * @param targetPackage Target package
     * @param language Generated source language
     * @param languageLevel Java language level used for generation
     * @param schemaCacheDir Schema cache directory
     * @param outputDir Output directory
     * @param sources Configured sources
     * @param skipOnError Skip toggle
     * @param failOnMissingSource Missing source toggle
     */
    public record Parameters(
        String targetPackage,
        String language,
        int languageLevel,
        String schemaCacheDir,
        String outputDir,
        List<ConfiguredSource> sources,
        boolean skipOnError,
        boolean failOnMissingSource
    ) {
    }

    /**
     * A configured source entry.
     *
     * @param name Stable source name
     * @param provider Discovery provider id
     * @param options Provider-specific options
     */
    public record ConfiguredSource(
        String name,
        String provider,
        Map<String, Object> options
    ) {
    }

    /**
     * Discovery section of the manifest.
     *
     * @param schemas Discovered schema entries
     */
    public record Discovery(List<SchemaFile> schemas) {
    }

    /**
     * A discovered schema file entry.
     *
     * @param sourceName Configured source name
     * @param provider Discovery provider id
     * @param scope Discovery scope
     * @param name Input name
     * @param schemaFile Relative schema file path
     * @param retrievalMode Retrieval mode
     */
    public record SchemaFile(
        String sourceName,
        String provider,
        String scope,
        String name,
        String schemaFile,
        String retrievalMode
    ) {
    }

    /**
     * A non-fatal warning entry.
     *
     * @param sourceName Configured source name
     * @param scope Warning scope
     * @param name Input name
     * @param step Pipeline step
     * @param code Warning code
     * @param message Warning message
     */
    public record Warning(
        String sourceName,
        String scope,
        @JsonInclude(JsonInclude.Include.ALWAYS)
        String name,
        DiscoveryStep step,
        String code,
        String message
    ) {
    }

    /**
     * A skipped input entry.
     *
     * @param sourceName Configured source name
     * @param scope Skip scope
     * @param name Input name
     * @param step Pipeline step
     * @param code Skip code
     * @param reason Skip reason
     * @param retrievalMode Retrieval mode
     */
    public record Skipped(
        String sourceName,
        String scope,
        String name,
        DiscoveryStep step,
        String code,
        String reason,
        @JsonInclude(JsonInclude.Include.ALWAYS)
        String retrievalMode
    ) {
    }
}
