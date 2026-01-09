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
import tools.jackson.core.JacksonException;

import java.io.File;

/**
 * Loads a JSON schema from a file.
 * This class implements the {@link SchemaLoader} interface and provides the functionality
 * to load a JSON schema from a local file.
 *
 * @author Elif Kurtay
 * @version 1.3
 */
public class FileLoader implements SchemaLoader {
    private final File file;

    public FileLoader(File file) {
        this.file = file;
    }

    @Override
    public Schema load() {
            return JSON_MAPPER.readValue(file, Schema.class);
    }
}
