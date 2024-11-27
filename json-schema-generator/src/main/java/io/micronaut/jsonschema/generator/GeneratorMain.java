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

import io.micronaut.inject.visitor.VisitorContext;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

/**
 * An entry point to be used in tests, to simulate
 * what the Source Generation from Json Schema plugin would do.
 *
 * @version 1.3
 * @author Elif Kurtay
 */
public class GeneratorMain {

    /**
     * The main executable.
     *
     * @param args The argument array, consisting of:
     *     <ol>
     *         <li>Input jsonfile location.</li>
 *             <li>Input Path to folder of json schema.</li>
     *         <li>The generation language.</li>
     *         <li>The output path and package name.</li>
     *         <li>An optional file name in case there is only one output file desired.</li>
     *     </ol>
     *
     * @throws IOException In case definition file path is incorrect.
     */
    public static void main(String[] args) throws IOException {
        var jsonFileName = args[0];
        var inputFolder = args[1];
        var lang = VisitorContext.Language.valueOf(args[2].toUpperCase());
        var outputPath = Paths.get(args[3]);
        var outputPackageName = args[4];
        var config = new SourceGeneratorConfig(outputPath, outputPackageName);
        var fileName = args[5];

        var generator = new SourceGenerator(lang);
        if (inputFolder.isEmpty()) {
            var jsonFile = new File(jsonFileName.substring(5));
            if (fileName.isEmpty()) {
                generator.generate(config, jsonFile);
            } else {
                generator.generate(config, jsonFile, fileName);
            }
        } else {
            generator.generate(config, Paths.get(inputFolder));
        }
    }
}
