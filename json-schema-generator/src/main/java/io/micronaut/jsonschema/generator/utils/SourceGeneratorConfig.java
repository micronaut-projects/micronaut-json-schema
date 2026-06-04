/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.jsonschema.generator.utils;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * A configuration class for the JSON schema source generator, encapsulating the configuration
 * properties related to the input and output paths, file names, and URL.
 *
 * This class is used to specify:
 * <ol>
 *   <li>The URL of the JSON schema file to be processed ({@code jsonUrl})</li>
 *   <li>The name of the JSON schema file ({@code jsonFileName})</li>
 *   <li>The input folder where schema files might be located ({@code inputFolder})</li>
 *   <li>The output directory for generated source files ({@code outputPath})</li>
 *   <li>The package name to be applied to the generated source files ({@code outputPackageName})</li>
 *   <li>The output file name for the generated source ({@code outputFileName})</li>
 * </ol>
 *
 * @param inputStream The Input Stream of the JSON schema to be processed.
 * @param jsonUrl The URL of the JSON schema file to be downloaded or processed.
 * @param jsonFile The JSON schema file, typically ending in {@code .schema.json}.
 * @param inputFolder The path to the folder where the input files (JSON schemas) are located.
 * @param outputPath The path where generated source files will be saved. This is a required field.
 *                   The path should be a valid, writable directory path.
 * @param outputPackageName The package name to be applied to the generated source files.
 *                          This field is optional and can be {@code null} if no package name is needed.
 * @param outputFileName The name of the file where the generated source code will be written. This field is optional.
 * @param javadoc Configuration specific to Javadoc.
 * @param recordAdoptionStrategy Strategy specifying when to generate records vs classes. Defaults to preferring records.
 * @author Elif Kurtay
 * @version 1.3
 */
public record SourceGeneratorConfig(
    InputStream inputStream,
    String jsonUrl,
    File jsonFile,
    Path inputFolder,
    Path outputPath,
    String outputPackageName,
    String outputFileName,
    JavadocConfig javadoc,
    RecordAdoptionStrategy recordAdoptionStrategy
) {
    public String getInputName() {
        if (jsonFile != null) {
            return jsonFile.getName();
        } else if (jsonUrl != null && !jsonUrl.isBlank()) {
            return jsonUrl.substring(jsonUrl.lastIndexOf('/') + 1);
        } else {
            return "InputStream.schema.json";
        }
    }

    /**
     * Convert this configuration to builder.
     * @return The builder
     */
    public SourceGeneratorConfigBuilder toBuilder() {
        return new SourceGeneratorConfigBuilder()
            .withInputStream(inputStream)
            .withJsonUrl(jsonUrl)
            .withInputFolder(inputFolder)
            .withJsonFile(jsonFile)
            .withOutputFolder(outputPath)
            .withOutputPackageName(outputPackageName)
            .withOutputFileName(outputFileName)
            .withJavadoc(javadoc)
            .withRecordAdoptionStrategy(recordAdoptionStrategy);

    }

    /**
     * A sub-configuration used for generated Javadoc.
     * The configuration has single parameter, but is expected to be extended with more properties.
     *
     * @param replaceHTML Whether to replace HTML characters, e.g. {@code >} to {@code &gt;}.
     */
    public record JavadocConfig(
        boolean replaceHTML
    ) {
        /**
         * Initialize the configuration with defaults.
         */
        public JavadocConfig() {
            this(true);
        }
    }

    /**
     * Strategy enum that specifies when to generate records vs classes.
     */
    public enum RecordAdoptionStrategy {
        /**
         * Will generate record when possible.
         */
        PREFER_RECORD,
        /**
         * Will always generate classes.
         */
        ALWAYS_CLASS
    }
}
