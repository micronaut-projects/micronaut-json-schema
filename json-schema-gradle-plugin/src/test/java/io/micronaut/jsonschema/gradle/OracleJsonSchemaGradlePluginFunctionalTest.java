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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Functional tests for the Gradle Oracle JSON Schema plugin using Gradle TestKit.
 */
class OracleJsonSchemaGradlePluginFunctionalTest {

    @Test
    void pluginIdRegistersTaskDefaultsAndJavaSourceSet(@TempDir Path projectDir) throws IOException {
        writeSettings(projectDir);
        Files.writeString(projectDir.resolve("build.gradle"), """
plugins {
    id 'java'
    id 'io.micronaut.jsonschema.oracle'
}

tasks.register('assertOraclePluginWiring') {
    doLast {
        def generateTask = tasks.named('generateFromOracleJsonSchema').get()
        assert generateTask.schemaCacheDir.get().asFile.toPath() == layout.buildDirectory.dir('oracle-jsonschema-cache').get().asFile.toPath()
        assert generateTask.outputDir.get().asFile.toPath() == layout.buildDirectory.dir('generated/sources/oracle-jsonschema').get().asFile.toPath()
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
    id 'io.micronaut.jsonschema.oracle'
}

oracleJsonSchema {
    jdbcUrl = 'jdbc:oracle:thin:@localhost:1521/FREEPDB1'
    username = 'app'
    password = 'secret'
    targetPackage = 'io.micronaut.jsonschema.oracle.generated'
    sources = [
        [name: 'domains', providerClassName: 'io.micronaut.jsonschema.generator.oracle.OracleDomainDiscoveryProvider', owner: 'APP', options: [include: 'APP_JSON']],
        [name: 'views', providerClassName: 'io.micronaut.jsonschema.generator.oracle.OracleJsonViewDiscoveryProvider', owner: 'APP', options: [include: 'APP_VIEW']]
    ]
    skipOnError = true
    failOnMissingDb = false
}

tasks.register('assertOracleExtensionMapping') {
    doLast {
        def generateTask = tasks.named('generateFromOracleJsonSchema').get()
        assert generateTask.jdbcUrl.get() == 'jdbc:oracle:thin:@localhost:1521/FREEPDB1'
        assert generateTask.username.get() == 'app'
        assert generateTask.password.get() == 'secret'
        assert generateTask.targetPackage.get() == 'io.micronaut.jsonschema.oracle.generated'
        assert generateTask.sources.get() == [
            [name: 'domains', providerClassName: 'io.micronaut.jsonschema.generator.oracle.OracleDomainDiscoveryProvider', owner: 'APP', options: [include: 'APP_JSON']],
            [name: 'views', providerClassName: 'io.micronaut.jsonschema.generator.oracle.OracleJsonViewDiscoveryProvider', owner: 'APP', options: [include: 'APP_VIEW']]
        ]
        assert generateTask.skipOnError.get()
        assert !generateTask.failOnMissingDb.get()
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
oracleJsonSchema.jdbcUrl=jdbc:oracle:thin:@localhost:1521/FREEPDB1
oracleJsonSchema.username=app
oracleJsonSchema.password=secret
""");
        Files.writeString(projectDir.resolve("build.gradle"), """
plugins {
    id 'java'
    id 'io.micronaut.jsonschema.oracle'
}

oracleJsonSchema {
    targetPackage = 'io.micronaut.jsonschema.oracle.generated'
}

tasks.register('assertOraclePropertyDefaults') {
    doLast {
        def generateTask = tasks.named('generateFromOracleJsonSchema').get()
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

    private void writeSettings(Path projectDir) throws IOException {
        Files.writeString(projectDir.resolve("settings.gradle"), "rootProject.name = 'oracle-jsonschema-testkit'\n");
    }
}
