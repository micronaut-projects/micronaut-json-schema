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

import io.micronaut.jsonschema.model.Schema;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads a JSON schema from a URL.
 * This class implements the {@link SchemaLoader} interface and provides the functionality
 * to load a JSON schema from a given URL by opening an HTTP connection and reading the content.
 *
 * @author Elif Kurtay
 * @version 1.3
 */
public class UrlLoader implements SchemaLoader {
    // configurable by user
    private static List<String> allowedUrlPatterns = List.of(
        "^https://.*/.*.json"
    );

    private final String url;

    public UrlLoader(String url) {
        this.url = url;
    }

    @Override
    public Schema load() {
        try {
            try (InputStream inputStream = downloadAsStream(this.url)) {
                String jsonString = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                return JSON_MAPPER.readValue(jsonString, Schema.class);
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
        for (String pattern : UrlLoader.allowedUrlPatterns) {
            Pattern compiledPattern = Pattern.compile(pattern);
            Matcher matcher = compiledPattern.matcher(fileUrl);
            if (matcher.matches()) {
                return true;
            }
        }
        return false;
    }

    public static List<String> getAllowedUrlPatterns() {
        return allowedUrlPatterns;
    }

    public static void setAllowedUrlPatterns(List<String> allowedUrlPatterns) {
        UrlLoader.allowedUrlPatterns = allowedUrlPatterns;
    }

    public static void addAllowedUrlPatterns(String allowedUrlPattern) {
        UrlLoader.allowedUrlPatterns.add(allowedUrlPattern);
    }
}
