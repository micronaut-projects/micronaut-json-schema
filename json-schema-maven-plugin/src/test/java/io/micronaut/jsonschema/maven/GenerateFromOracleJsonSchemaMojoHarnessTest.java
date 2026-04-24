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
package io.micronaut.jsonschema.maven;

import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Harness-backed tests that load the mojo configuration from a test POM.
 */
@MojoTest
class GenerateFromOracleJsonSchemaMojoHarnessTest {

    @Test
    @InjectMojo(goal = "generate-from-oracle-json-schema", pom = "src/test/resources/unit/generate-from-oracle-json-schema/pom.xml")
    void loadsConfiguredMojoFromPom(GenerateFromOracleJsonSchemaMojo mojo) {
        assertNotNull(mojo);
        assertEquals("jdbc:oracle:thin:@localhost:1521/FREEPDB1", mojo.getJdbcUrl());
        assertEquals("app", mojo.getUsername());
        assertEquals("secret", mojo.getPassword());
        assertEquals("io.micronaut.jsonschema.oracle.generated", mojo.getTargetPackage());
        assertEquals(2, mojo.getSources().size());
        assertEquals("domains", mojo.getSources().get(0).getName());
        assertEquals("io.micronaut.jsonschema.generator.oracle.OracleDomainDiscoveryProvider", mojo.getSources().get(0).getProviderClassName());
        assertEquals("APP", mojo.getSources().get(0).getOwner());
        assertEquals("APP_JSON", mojo.getSources().get(0).getOptions().get("include"));
        assertEquals("views", mojo.getSources().get(1).getName());
        assertEquals("io.micronaut.jsonschema.generator.oracle.OracleDualityJsonViewDiscoveryProvider", mojo.getSources().get(1).getProviderClassName());
        assertEquals("APP_VIEW", mojo.getSources().get(1).getOptions().get("include"));
        assertTrue(mojo.isSkipOnError());
        assertFalse(mojo.isFailOnMissingDb());
        assertTrue(mojo.isSkipped());
        assertNotNull(mojo.getSchemaCacheDir());
        assertNotNull(mojo.getOutputDir());
        assertTrue(mojo.getSchemaCacheDir().toPath().endsWith("target/oracle-jsonschema-cache"));
        assertTrue(mojo.getOutputDir().toPath().endsWith("target/generated-sources/oracle-jsonschema"));
    }
}
