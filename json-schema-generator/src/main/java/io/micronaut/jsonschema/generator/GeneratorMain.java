/*
 * Copyright 2017-2024 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.jsonschema.generator.loaders.UrlLoader;
import io.micronaut.jsonschema.generator.utils.SourceGeneratorConfig;
import io.micronaut.jsonschema.generator.utils.SourceGeneratorConfig.JavadocConfig;
import io.micronaut.jsonschema.generator.utils.SourceGeneratorConfigBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * An entry point to be used in tests, to simulate
 * what the Source Generation from Json Schema plugin would do.
 *
 * @version 1.3
 * @author Elif Kurtay
 */
@Internal
public class GeneratorMain {

    /**
     * The main executable.
     *
     * @param args The argument array, consisting of:
     *             <ol>
     *                 <li>URL of input jsonfile.</li>
     *                 <li>Input jsonfile location.</li>
     *                 <li>Input Path to folder of json schema.</li>
     *                 <li>The generation language.</li>
     *                 <li>The output path and package name.</li>
     *                 <li>An optional file name in case there is only one output file desired.</li>
     *                 <li>An optional List of String that has allowed URL patterns that are accepted for the references inside the schema</li>
     *             </ol>
     * @throws IOException In case definition file path is incorrect.
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 8) {
            throw new IllegalStateException("Invalid number of arguments.");
        }
        String jsonURL = args[0];
        File jsonFile = null;
        if (!args[1].isBlank()) {
            jsonFile = new File(args[1].substring(5));
        }
        Path inputFolder = null;
        if (!args[2].isBlank()) {
            inputFolder = Paths.get(args[2]);
        }
        var lang = args[3].toUpperCase();
        Path outputPath = Paths.get(args[4]);
        String outputPackageName = args[5];
        String outputFileName = args[6];
        var config = new SourceGeneratorConfigBuilder()
            .withJsonUrl(jsonURL)
            .withJsonFile(jsonFile)
            .withInputFolder(inputFolder)
            .withOutputFolder(outputPath)
            .withOutputPackageName(outputPackageName)
            .withOutputFileName(outputFileName)
            .build();

        var allowedUrlPatterns = parseListOfAllowedUrlPatterns(args[7]);
        if (!allowedUrlPatterns.isEmpty()) {
            UrlLoader.addAllowedUrlPatterns(allowedUrlPatterns);
        }
        var generator = new SourceGenerator(lang);
        generator.generate(config);
    }

    private static List<String> parseListOfAllowedUrlPatterns(String input) {
        // Remove the square brackets and extra spaces
        input = input.trim();
        if (input.startsWith("[") && input.endsWith("]")) {
            input = input.substring(1, input.length() - 1).trim();
        }

        // Handle empty list (i.e., "[]")
        if (input.isEmpty()) {
            return List.of();
        }

        // Split the string by commas and remove extra spaces around each element
        String[] elements = input.split("\\s*,\\s*");
        return List.of(elements);
    }
}

