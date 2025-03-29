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
package io.micronaut.jsonschema.generator.plugin;

import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.jsonschema.generator.SourceGenerator;
import io.micronaut.jsonschema.generator.loaders.UrlLoader;
import io.micronaut.jsonschema.generator.utils.GeneratorContext;
import io.micronaut.jsonschema.generator.utils.SourceGeneratorConfig;
import io.micronaut.sourcegen.annotations.PluginTask;
import io.micronaut.sourcegen.annotations.PluginTaskExecutable;
import io.micronaut.sourcegen.annotations.PluginTaskParameter;
import io.micronaut.sourcegen.annotations.PluginTaskParameter.OutputType;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * A configuration class for the JSON schema source generator, encapsulating the configuration
 * properties related to the input and output paths, file names, and URL.
 *
 * @param inputURL The URL of the JSON schema file to be downloaded and processed
 * @param inputFile The JSON schema file, typically ending in {@code .schema.json}
 * @param inputDirectory The path to the folder where the input files (JSON schemas) are located.
 *                    Each of the files in the directory will be used for generation
 * @param language The programming language to generate the output types in.
 * @param outputPackageName The package name to be applied to the generated source files
 *                    This field is optional and can be {@code null} if no package name is needed
 * @param outputFileName The name of the file where the generated source code will be written
 *                       if there is a single output source.
 * @param acceptedUrlPatterns The URL patterns that are allowed.
 *                           Used to filter or validate input resources specified by URL.
 *                           URLs matching at least one pattern will be accepted. The default
 *                           value is {@code "^https://.* /.*.json"}
 * @param outputDirectory The path where generated source files will be saved
 *                   The path should be a valid, writable directory path
 * @param javaOutputDirectory Internal output path for Java sources
 * @param groovyOutputDirectory Internal output path for Groovy sources
 * @param kotlinOutputDirectory Internal output path for Kotlin sources
 * @author Andriy Dmytruk
 * @version 1.5
 */
@PluginTask
public record JsonSchemaGeneratorTask(
    String inputURL,
    File inputFile,
    @PluginTaskParameter(directory = true)
    File inputDirectory,
    @PluginTaskParameter(defaultValue = "io.micronaut.jsonschema")
    String outputPackageName,
    String outputFileName,
    @PluginTaskParameter(defaultValue = "JAVA")
    Language language,
    @PluginTaskParameter
    List<String> acceptedUrlPatterns,
    @PluginTaskParameter(directory = true, output = OutputType.CUSTOM, required = true)
    File outputDirectory,
    @PluginTaskParameter(directory = true, output = OutputType.JAVA_SOURCES, internal = true)
    File javaOutputDirectory,
    @PluginTaskParameter(directory = true, output = OutputType.GROOVY_SOURCES, internal = true)
    File groovyOutputDirectory,
    @PluginTaskParameter(directory = true, output = OutputType.KOTLIN_SOURCES, internal = true)
    File kotlinOutputDirectory
) {

    @PluginTaskExecutable
    public void generate() {
        if (inputDirectory == null && inputURL == null && inputFile == null) {
            throw new IllegalArgumentException("Must provide oneOf: inputDirectory, inputURL, inputFile");
        }
        if (acceptedUrlPatterns != null) {
            UrlLoader.setAllowedUrlPatterns(acceptedUrlPatterns);
        }

        File languageDir = switch (language) {
            case JAVA -> javaOutputDirectory;
            case GROOVY -> groovyOutputDirectory;
            case KOTLIN -> kotlinOutputDirectory;
        };
        SourceGenerator generator = new SourceGenerator(VisitorContext.Language.valueOf(language.name()), new GeneratorContext());
        try {
            generator.generate(new SourceGeneratorConfig(
                null,
                inputURL,
                    inputFile,
                inputDirectory == null ? null : inputDirectory.toPath(),
                languageDir.toPath(),
                outputPackageName,
                outputFileName
            ));
        } catch (IOException e) {
            throw new RuntimeException("Could not generate based on JSON schema", e);
        }
    }

    /**
     * A language to generated sources in.
     */
    public enum Language {
        /** The Java language. */
        JAVA,
        /** The Groovy language. */
        GROOVY,
        /** The Kotlin language. */
        KOTLIN
    }
}
