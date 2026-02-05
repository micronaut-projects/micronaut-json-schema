/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.jsonschema.utils;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.Readable;
import io.micronaut.core.io.ResourceLoader;
import org.jspecify.annotations.NonNull;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

@Internal
final class LazyJsonSchemaReadable implements Readable {
    private final ResourceLoader resourceLoader;
    private final String name;
    private final String resourcePath;

    LazyJsonSchemaReadable(ResourceLoader resourceLoader, String name, String resourcePath) {
        this.resourceLoader = resourceLoader;
        this.name = name;
        this.resourcePath = resourcePath;
    }

    @Override
    @NonNull
    public InputStream asInputStream() throws IOException {
        return resourceLoader.getResourceAsStream(resourcePath)
            .orElseThrow(() -> new FileNotFoundException("Resource does not exist: " + resourcePath));
    }

    @Override
    public boolean exists() {
        return resourceLoader.getResource(resourcePath).isPresent();
    }

    @Override
    public String getName() {
        return name;
    }
}
