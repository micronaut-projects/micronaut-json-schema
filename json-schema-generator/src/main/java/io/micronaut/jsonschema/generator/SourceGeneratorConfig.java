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
package io.micronaut.jsonschema.generator;

import java.nio.file.Path;

/**
 * A configuration class for the json schema source generator, encapsulating the output path
 * and the output package name.
 * <p>
 * This class is used to specify where the generated source files should be stored
 * and what package name should be applied to those files.
 * </p>
 *
 * @param outputPath The path where generated source files will be saved. This is a required field.
 *                   The path should be a valid, writable directory path.
 * @param outputPackageName The package name to be applied to the generated source files.
 *                          This field is optional and can be {@code null} if no package name is needed.
 * @author Elif Kurtay
 * @version 1.3
 */
public record SourceGeneratorConfig(
    Path outputPath,
    String outputPackageName) {
}
