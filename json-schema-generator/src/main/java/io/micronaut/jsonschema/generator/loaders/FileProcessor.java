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
package io.micronaut.jsonschema.generator.loaders;

import io.micronaut.jsonschema.generator.SourceGenerator;
import io.micronaut.jsonschema.generator.utils.SourceGeneratorConfig;
import io.micronaut.jsonschema.model.Schema;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import static io.micronaut.core.util.StringUtils.capitalize;
import static io.micronaut.jsonschema.generator.aggregator.TypeAggregator.getCamelCaseName;

public class FileProcessor {

    /**
     * Loads a JSON schema based on the configuration.
     * This method determines the appropriate {@link SchemaLoader} based on the configuration settings
     * and delegates the task of loading the JSON schema from the correct source (URL, file, or input stream).
     *
     * @param config the configuration that provides the source for the JSON schema (URL, file, or input stream)
     * @return a {@link Schema} object representing the loaded JSON schema
     * @throws RuntimeException if no valid source is found in the configuration or if there is an error loading the schema
     */
    public static Schema getJsonSchema(SourceGeneratorConfig config) {
        try {
            SchemaLoader loader;
            if (config.inputStream() != null) {
                loader = new StreamLoader(config.inputStream());
            } else if (config.jsonUrl() != null && !config.jsonUrl().isBlank()) {
                loader = new UrlLoader(config.jsonUrl());
            } else if (config.jsonFile() != null) {
                loader = new FileLoader(config.jsonFile());
            } else {
                throw new RuntimeException("Missing required config.jsonUrl(), config.inputStream(), or config.jsonFile().");
            }
            return loader.load();
        } catch (RuntimeException e) {
            throw new RuntimeException("Error loading JSON schema", e);
        }
    }

    /**
     * Creates or retrieves a file at the specified output path, package name, and file name.
     * The method resolves the full file path by combining the output path, the package name (which is converted to a directory structure),
     * and the file name. It ensures that the necessary directories exist and creates the file if it doesn't already exist.
     * <p>
     * The directories leading to the file are created if they do not exist. If the file cannot be created, an {@link IOException} is thrown.
     * </p>
     *
     * @param outputPath The base directory where the file should be created. This should be the root directory where the file structure starts.
     * @param packageName The package name (e.g., "com.example.project"), which will be converted to a directory path (e.g., "com/example/project").
     * @param fileName The name of the file to create or retrieve, including the file extension (e.g., "MyClass.java").
     * @return The {@link File} object representing the output file, which will be created if it doesn't exist.
     * @throws IOException If the file cannot be created, or if an I/O error occurs during file or directory creation.
     */
    public static File getOutputFile(Path outputPath, String packageName, String fileName) throws IOException {
        // Create full path
        String packagePath = packageName.replace('.', File.separatorChar);
        Path fullPath = outputPath.resolve(packagePath).resolve(fileName);

        // Create directories if they do not exist
        File outputFile = fullPath.toFile();
        if (!outputFile.getParentFile().exists()) {
            outputFile.getParentFile().mkdirs();
        }
        if (!outputFile.exists() && !outputFile.createNewFile()) {
            throw new IOException("Could not create file " + outputFile.getAbsolutePath());
        }
        return outputFile;
    }

    public static String getFileName(Schema schema, Optional<String> topLevelName) {
        String fileName;
        if (topLevelName.isPresent() && !topLevelName.get().isEmpty()) {
            fileName = topLevelName.get();
        } else if (schema.hasTitle()) {
            fileName = capitalize(getCamelCaseName(schema.getTitle()));
        } else {
            fileName = "SchemaFile"; // default
        }

        switch (SourceGenerator.getLanguage()) {
            case KOTLIN: fileName += ".kt"; break;
            case GROOVY: fileName += ".groovy"; break;
            default: fileName += ".java"; break;
        }
        return fileName;
    }
}

