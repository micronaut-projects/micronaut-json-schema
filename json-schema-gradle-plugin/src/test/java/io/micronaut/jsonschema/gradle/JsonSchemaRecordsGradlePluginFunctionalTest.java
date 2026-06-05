/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.jsonschema.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Functional tests for the Gradle jsonSchemaRecords plugin using Gradle TestKit.
 */
class JsonSchemaRecordsGradlePluginFunctionalTest {

    @Test
    void pluginIdRegistersTaskDefaultsAndJavaSourceSet(@TempDir Path projectDir) throws IOException {
        writeSettings(projectDir);
        Files.writeString(projectDir.resolve("build.gradle"), """
plugins {
    id 'java'
    id 'io.micronaut.jsonschema.records'
}

tasks.register('assertOraclePluginWiring') {
    doLast {
        def generateTask = tasks.named('generateFromJsonSchemaSources').get()
        assert generateTask.schemaCacheDir.get().asFile.toPath() == layout.buildDirectory.dir('jsonschema-cache').get().asFile.toPath()
        assert generateTask.outputDir.get().asFile.toPath() == layout.buildDirectory.dir('generated/sources/jsonschema').get().asFile.toPath()
        assert generateTask.languageLevel.get() == 21
        assert sourceSets.main.java.srcDirs.contains(generateTask.generatedSourcesDirectory.toFile())
        println('oracle-plugin-wiring-ok')
    }
}
""");

        BuildResult result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("assertOraclePluginWiring")
            .withPluginClasspath()
            .build();

        assertTrue(result.getOutput().contains("oracle-plugin-wiring-ok"));
    }

    @Test
    void extensionConfigurationFlowsIntoRegisteredTask(@TempDir Path projectDir) throws IOException {
        writeSettings(projectDir);
        Files.writeString(projectDir.resolve("build.gradle"), """
plugins {
    id 'java'
    id 'io.micronaut.jsonschema.records'
}

jsonSchemaRecords {
    jdbcUrl = 'jdbc:oracle:thin:@localhost:1521/FREEPDB1'
    username = 'app'
    password = 'secret'
    targetPackage = 'io.micronaut.jsonschema.oracle.generated'
    languageLevel = 17
    sources = [
        [name: 'domains', provider: 'oracle-domains', options: [owner: 'APP', include: 'APP_JSON']],
        [name: 'views', provider: 'oracle-duality-views', options: [owner: 'APP', include: 'APP_VIEW']]
    ]
    skipOnError = true
    failOnMissingSource = false
}

tasks.register('assertOracleExtensionMapping') {
    doLast {
        def generateTask = tasks.named('generateFromJsonSchemaSources').get()
        assert generateTask.jdbcUrl.get() == 'jdbc:oracle:thin:@localhost:1521/FREEPDB1'
        assert generateTask.username.get() == 'app'
        assert generateTask.password.get() == 'secret'
        assert generateTask.targetPackage.get() == 'io.micronaut.jsonschema.oracle.generated'
        assert generateTask.languageLevel.get() == 17
        assert generateTask.sources.get() == [
            [name: 'domains', provider: 'oracle-domains', options: [owner: 'APP', include: 'APP_JSON']],
            [name: 'views', provider: 'oracle-duality-views', options: [owner: 'APP', include: 'APP_VIEW']]
        ]
        assert generateTask.skipOnError.get()
        assert !generateTask.failOnMissingSource.get()
        println('oracle-extension-mapping-ok')
    }
}
""");

        BuildResult result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("assertOracleExtensionMapping")
            .withPluginClasspath()
            .build();

        assertTrue(result.getOutput().contains("oracle-extension-mapping-ok"));
    }

    @Test
    void gradlePropertiesProvideConnectionDefaults(@TempDir Path projectDir) throws IOException {
        writeSettings(projectDir);
        Files.writeString(projectDir.resolve("gradle.properties"), """
jsonSchemaRecords.jdbcUrl=jdbc:oracle:thin:@localhost:1521/FREEPDB1
jsonSchemaRecords.username=app
jsonSchemaRecords.password=secret
""");
        Files.writeString(projectDir.resolve("build.gradle"), """
plugins {
    id 'java'
    id 'io.micronaut.jsonschema.records'
}

jsonSchemaRecords {
    targetPackage = 'io.micronaut.jsonschema.oracle.generated'
}

tasks.register('assertOraclePropertyDefaults') {
    doLast {
        def generateTask = tasks.named('generateFromJsonSchemaSources').get()
        assert generateTask.jdbcUrl.get() == 'jdbc:oracle:thin:@localhost:1521/FREEPDB1'
        assert generateTask.username.get() == 'app'
        assert generateTask.password.get() == 'secret'
        println('oracle-property-defaults-ok')
    }
}
""");

        BuildResult result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("assertOraclePropertyDefaults")
            .withPluginClasspath()
            .build();

        assertTrue(result.getOutput().contains("oracle-property-defaults-ok"));
    }

    @Test
    void providerFamilyConfigurationFlowsIntoRegisteredTask(@TempDir Path projectDir) throws IOException {
        writeSettings(projectDir);
        Files.writeString(projectDir.resolve("build.gradle"), """
plugins {
    id 'java'
    id 'io.micronaut.jsonschema.records'
}

jsonSchemaRecords {
    jdbcUrl = 'jdbc:oracle:thin:@localhost:1521/IGNORED'
    username = 'ignored'
    password = 'ignored'
    providers {
        oracle {
            jdbcUrl = 'jdbc:oracle:thin:@localhost:1521/FREEPDB1'
            username = 'app'
            password = 'secret'
        }
    }
    targetPackage = 'io.micronaut.jsonschema.oracle.generated'
}

tasks.register('assertOracleProviderFamilyMapping') {
    doLast {
        def generateTask = tasks.named('generateFromJsonSchemaSources').get()
        assert generateTask.jdbcUrl.get() == 'jdbc:oracle:thin:@localhost:1521/FREEPDB1'
        assert generateTask.username.get() == 'app'
        assert generateTask.password.get() == 'secret'
        println('oracle-provider-family-mapping-ok')
    }
}
""");

        BuildResult result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("assertOracleProviderFamilyMapping")
            .withPluginClasspath()
            .build();

        assertTrue(result.getOutput().contains("oracle-provider-family-mapping-ok"));
    }

    @Test
    void compileJavaSeesGeneratedSourcesWhenGenerationTaskIsInSameGraph(@TempDir Path projectDir) throws Exception {
        writeSettings(projectDir);
        compileStaticProvider(projectDir);
        Path appSource = projectDir.resolve("src/main/java/example/app/UsesGeneratedRecord.java");
        Files.createDirectories(appSource.getParent());
        Files.writeString(appSource, """
package example.app;

import example.generated.MoonPhase;

public class UsesGeneratedRecord {
    private final MoonPhase value;

    public UsesGeneratedRecord(MoonPhase value) {
        this.value = value;
    }

    public MoonPhase value() {
        return value;
    }
}
""");
        Files.writeString(projectDir.resolve("build.gradle"), """
plugins {
    id 'java'
    id 'io.micronaut.jsonschema.records'
}

jsonSchemaRecords {
    targetPackage = 'example.generated'
    providerClasspath.from(files('provider-classes'))
    sources = [
        [name: 'static', provider: 'static-test']
    ]
}

dependencies {
    compileOnly files(%s)
}
""".formatted(compileOnlyFilesForGeneratedAnnotations()));

        BuildResult result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("compileJava", "generateFromJsonSchemaSources", "--stacktrace")
            .withPluginClasspath()
            .build();

        assertTrue(result.getOutput().contains(":generateFromJsonSchemaSources"));
        assertTrue(result.getOutput().contains(":compileJava"));
        assertTrue(Files.exists(projectDir.resolve("build/generated/sources/jsonschema/example/generated/MoonPhase.java")));
    }

    @Test
    void compileJavaDoesNotRunGenerationTaskByDefault(@TempDir Path projectDir) throws IOException {
        writeSettings(projectDir);
        Path appSource = projectDir.resolve("src/main/java/example/app/App.java");
        Files.createDirectories(appSource.getParent());
        Files.writeString(appSource, """
package example.app;

public class App {
}
""");
        Files.writeString(projectDir.resolve("build.gradle"), """
plugins {
    id 'java'
    id 'io.micronaut.jsonschema.records'
}

jsonSchemaRecords {
    targetPackage = 'example.generated'
    sources = [
        [name: 'static', provider: 'missing-provider']
    ]
}
""");

        BuildResult result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("compileJava", "--stacktrace")
            .withPluginClasspath()
            .build();

        assertFalse(result.getOutput().contains(":generateFromJsonSchemaSources"));
        assertTrue(result.getOutput().contains(":compileJava"));
    }

    private void writeSettings(Path projectDir) throws IOException {
        Files.writeString(projectDir.resolve("settings.gradle"), "rootProject.name = 'jsonschema-testkit'\n");
    }

    private void compileStaticProvider(Path projectDir) throws IOException {
        Path sourceFile = projectDir.resolve("provider-src/test/provider/StaticSchemaDiscoveryProvider.java");
        Path classesDir = projectDir.resolve("provider-classes");
        Files.createDirectories(sourceFile.getParent());
        Files.createDirectories(classesDir);
        Files.writeString(sourceFile, """
package test.provider;

import io.micronaut.jsonschema.generator.discovery.DiscoveredSchema;
import io.micronaut.jsonschema.generator.discovery.DiscoveryResult;
import io.micronaut.jsonschema.generator.discovery.SchemaDiscoveryContext;
import io.micronaut.jsonschema.generator.discovery.SchemaDiscoveryProvider;
import io.micronaut.jsonschema.generator.discovery.SourceSpec;
import java.util.List;
import java.util.Map;

public final class StaticSchemaDiscoveryProvider implements SchemaDiscoveryProvider {
    @Override
    public String providerId() {
        return "static-test";
    }

    @Override
    public DiscoveryResult discover(SchemaDiscoveryContext context, SourceSpec source) {
        String schema = "{\\"type\\":\\"object\\",\\"properties\\":{\\"phase\\":{\\"type\\":\\"string\\"}},\\"additionalProperties\\":false}";
        return new DiscoveryResult(
            List.of(new DiscoveredSchema("CUSTOM", "moon_phase", schema, "STATIC")),
            List.of(),
            List.of(),
            Map.of()
        );
    }
}
""");
        Path serviceFile = classesDir.resolve("META-INF/services/io.micronaut.jsonschema.generator.discovery.SchemaDiscoveryProvider");
        Files.createDirectories(serviceFile.getParent());
        Files.writeString(serviceFile, "test.provider.StaticSchemaDiscoveryProvider\n");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertTrue(compiler != null, "JDK compiler is required for this test");
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            Boolean compiled = compiler.getTask(
                null,
                fileManager,
                null,
                List.of("-classpath", System.getProperty("java.class.path"), "-d", classesDir.toString()),
                null,
                fileManager.getJavaFileObjects(sourceFile.toFile())
            ).call();
            assertTrue(compiled, "Static schema provider should compile");
        }
    }

    private String compileOnlyFilesForGeneratedAnnotations() throws ReflectiveOperationException, URISyntaxException {
        return List.of(
                classPathEntry("io.micronaut.jsonschema.JsonSchema"),
                classPathEntry("io.micronaut.serde.annotation.Serdeable")
            )
            .stream()
            .map(path -> "'" + path.replace("\\", "\\\\").replace("'", "\\'") + "'")
            .reduce((first, second) -> first + ", " + second)
            .orElse("");
    }

    private String classPathEntry(String className) throws ReflectiveOperationException, URISyntaxException {
        return Path.of(Class.forName(className)
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI())
            .toString();
    }
}
