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
import java.nio.charset.StandardCharsets;

import static io.micronaut.jsonschema.generator.loaders.FileProcessor.downloadAsStream;

/**
 * Loads a JSON schema from a URL.
 * This class implements the {@link SchemaLoader} interface and provides the functionality
 * to load a JSON schema from a given URL by opening an HTTP connection and reading the content.
 *
 * @author Elif Kurtay
 * @version 1.3
 */
public class UrlLoader implements SchemaLoader {
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
}
