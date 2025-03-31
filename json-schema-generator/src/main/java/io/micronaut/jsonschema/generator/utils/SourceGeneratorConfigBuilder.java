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

import io.micronaut.jsonschema.generator.utils.SourceGeneratorConfig.JavadocConfig;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * A builder class for the {@link SourceGeneratorConfig}; encapsulating the configuration
 * properties related to the input and output paths; file names; and URL.
 *
 * @author Elif Kurtay
 * @version 1.3
 */
public class SourceGeneratorConfigBuilder {
    InputStream inputStream = null;
    String jsonUrl = null;
    File jsonFile = null;
    Path inputFolder = null;
    Path outputPath = null;
    String outputPackageName = "";
    String outputFileName = "";
    JavadocConfig javadocConfig = new JavadocConfig();

    /**
     * @return {@link SourceGeneratorConfig}
     */
    public SourceGeneratorConfig build() {
        return new SourceGeneratorConfig(
            inputStream,
            jsonUrl,
            jsonFile,
            inputFolder,
            outputPath,
            outputPackageName,
            outputFileName,
            javadocConfig
        );
    }

    /**
     * Adds the input stream config.
     * @param inputStream Input stream of a json schema
     * @return SourceGeneratorConfigBuilder
     */
    public SourceGeneratorConfigBuilder withInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
        return this;
    }

    /**
     * Adds the URL of a json schema.
     * @param jsonUrl URL of a json schema
     * @return SourceGeneratorConfigBuilder
     */
    public SourceGeneratorConfigBuilder withJsonUrl(String jsonUrl) {
        this.jsonUrl = jsonUrl;
        return this;
    }

    /**
     * Adds the File of a json schema.
     * @param jsonFile File of a json schema
     * @return SourceGeneratorConfigBuilder
     */
    public SourceGeneratorConfigBuilder withJsonFile(File jsonFile) {
        this.jsonFile = jsonFile;
        return this;
    }

    /**
     * Adds the input folder of json schema.
     * @param inputFolder Input folder of json schema
     * @return SourceGeneratorConfigBuilder
     */
    public SourceGeneratorConfigBuilder withInputFolder(Path inputFolder) {
        this.inputFolder = inputFolder;
        return this;
    }

    /**
     * Adds the Output path for the generated files.
     * @param outputFolder Output path for the generated files
     * @return SourceGeneratorConfigBuilder
     */
    public SourceGeneratorConfigBuilder withOutputFolder(Path outputFolder) {
        this.outputPath = outputFolder;
        return this;
    }

    /**
     * Adds the Package name for generated files.
     * @param outputPackageName Package name for generated files
     * @return SourceGeneratorConfigBuilder
     */
    public SourceGeneratorConfigBuilder withOutputPackageName(String outputPackageName) {
        this.outputPackageName = outputPackageName;
        return this;
    }

    /**
     * Adds the desired file name for single generations.
     * @param outputFileName desired file name for single generations
     * @return SourceGeneratorConfigBuilder
     */
    public SourceGeneratorConfigBuilder withOutputFileName(String outputFileName) {
        this.outputFileName = outputFileName;
        return this;
    }

    /**
     * Sets the Javadoc-specific configuration.
     * @param javadoc The configuration
     * @return This
     */
    public SourceGeneratorConfigBuilder withJavadoc(JavadocConfig javadoc) {
        this.javadocConfig = javadoc;
        return this;
    }

}

