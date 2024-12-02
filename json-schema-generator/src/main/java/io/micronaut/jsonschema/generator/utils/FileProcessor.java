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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.jsonschema.model.Schema;
import io.micronaut.jsonschema.serialization.JsonSchemaMapperFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class FileProcessor {

    // configurable by user
    private static List<String> allowedUrlPatterns = List.of(
        "^https://.*/.*.json"
    );

    public static Schema getJsonSchema(SourceGeneratorConfig config) {
        ObjectMapper jsonMapper = JsonSchemaMapperFactory.createMapper();
        try {
            if ((config.jsonUrl() != null && !config.jsonUrl().isBlank()) || config.inputStream() != null) {
                var inputStream = config.inputStream();
                if (inputStream == null) {
                    inputStream = downloadAsStream(config.jsonUrl());
                }
                String jsonString = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                return jsonMapper.readValue(jsonString, Schema.class);
            } else if (config.jsonFile() != null) {
                return jsonMapper.readValue(config.jsonFile(), Schema.class);
            } else {
                throw new RuntimeException("Missing required config.jsonUrl(), config.inputStream(), or config.jsonFile().");
            }
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Schema getJsonSchema(File schemaFile) {
        ObjectMapper jsonMapper = JsonSchemaMapperFactory.createMapper();
        try {
            if (schemaFile != null) {
                return jsonMapper.readValue(schemaFile, Schema.class);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    /**
     * Downloads a file from the given URL as an InputStream, verifying the URL against
     * a list of allowed patterns before attempting the download. The URL must end with ".json".
     *
     * @param fileUrl The URL to download the file from.
     * @return An InputStream containing the file content.
     * @throws IOException If the URL does not match any of the allowed patterns or does not end with ".json",
     *                   or if an error occurs during download.
     */
    public static InputStream downloadAsStream(String fileUrl) throws IOException, InterruptedException {
        // Verify that the URL matches at least one of the allowed patterns and ends with ".json"
        if (!isValidUrl(fileUrl)) {
            throw new IllegalArgumentException("URL does not match any of the allowed patterns or does not starts with https:// and end with .json.");
        }

        // Create HTTP client and request
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(fileUrl))
            .build();

        // Send the request and return the body as an InputStream
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        return response.body();
    }

    /**
     * Validates if the provided URL matches any of the allowed patterns and ends with ".json".
     *
     * @param fileUrl The URL to validate.
     * @return true if the URL matches at least one allowed pattern and ends with ".json", false otherwise.
     */
    public static boolean isValidUrl(String fileUrl) {
        // Check if the URL ends with .json
        if (!fileUrl.endsWith(".json")) {
            return false;
        }

        // Check if the URL starts with http safe
        if (!fileUrl.startsWith("https://")) {
            return false;
        }

        // Check if the URL matches any of the allowed patterns
        for (String pattern : allowedUrlPatterns) {
            Pattern compiledPattern = Pattern.compile(pattern);
            Matcher matcher = compiledPattern.matcher(fileUrl);
            if (matcher.matches()) {
                return true;
            }
        }
        return false;
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

    public static List<String> getAllowedUrlPatterns() {
        return allowedUrlPatterns;
    }

    public static void setAllowedUrlPatterns(List<String> allowedUrlPatterns) {
        FileProcessor.allowedUrlPatterns = allowedUrlPatterns;
    }
}

