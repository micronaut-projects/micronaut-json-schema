package io.micronaut.jsonschema.generator

import io.micronaut.context.ApplicationContext
import io.micronaut.json.JsonMapper
import io.micronaut.json.tree.JsonNode
import io.micronaut.jsonschema.generator.oracle.OracleJsonSchemaGeneratorConfig
import io.micronaut.jsonschema.generator.oracle.OracleJsonSchemaPipeline
import io.micronaut.jsonschema.generator.oracle.OracleSourceSpec
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

class OraclePipelineIntegrationSpec extends Specification {

    private static final JsonMapper JSON_MAPPER = JsonMapper.createDefault()
    private static final Map<String, Object> TEST_PROPERTIES = [
        "datasources.default.dialect"                        : "oracle"
    ].asImmutable()

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(TEST_PROPERTIES)

    @Shared
    String jdbcUrl = context.getRequiredProperty("datasources.default.url", String)

    @Shared
    String username = context.getRequiredProperty("datasources.default.username", String)

    @Shared
    String password = context.getRequiredProperty("datasources.default.password", String)

    @Shared
    String suffix = Long.toString(System.currentTimeMillis(), 36).toUpperCase(Locale.ENGLISH)

    @Shared
    String domainName = "MOONPHASE_${suffix}"

    @Shared
    String tableName = "TBL_APARTMENT_${suffix}"

    @Shared
    String viewName = "APARTMENT_VIEW_${suffix}"

    void setupSpec() {
        withConnection { Connection connection ->
            connection.createStatement().withCloseable { statement ->
                statement.execute("""
                    CREATE DOMAIN ${domainName}
                    AS JSON VALIDATE USING '{"type":"object","properties":{"phase":{"type":"string","minLength":1},"observedAt":{"type":"string","format":"date-time"}},"required":["phase"]}'
                    """.stripIndent())
                statement.execute("""
                    CREATE TABLE ${tableName} (
                        BUILDING_ID NUMBER(19) NOT NULL,
                        FLAT_ID NUMBER(19) NOT NULL,
                        UNIT_NAME VARCHAR2(100) NOT NULL,
                        FLOOR_NO NUMBER(10),
                        STATUS VARCHAR2(20),
                        CREATED_AT TIMESTAMP WITH TIME ZONE,
                        CONSTRAINT ${tableName}_PK PRIMARY KEY (BUILDING_ID, FLAT_ID)
                    )
                    """.stripIndent())
                statement.execute("""
                    CREATE OR REPLACE JSON RELATIONAL DUALITY VIEW ${viewName} AS
                    SELECT JSON {
                        '_id': {'buildingId': ap.building_id, 'flatId': ap.flat_id},
                        'unitName': ap.unit_name,
                        'floorNo': ap.floor_no,
                        'status': ap.status,
                        'createdAt': ap.created_at
                    }
                    FROM ${tableName} ap WITH UPDATE INSERT DELETE
                    """.stripIndent())
            }
        }
    }

    void "oracle free test resources backs pipeline discovery for domains and duality views"() {
        given:
        Path schemaCacheDir = Files.createTempDirectory("oracle-jsonschema-cache")
        Path outputDir = Files.createTempDirectory("oracle-jsonschema-output")
        List<String> logs = []

        when:
        def result = new OracleJsonSchemaPipeline({ String message -> logs.add(message) }).execute(
            new OracleJsonSchemaGeneratorConfig(
                jdbcUrl,
                username,
                password,
                "io.micronaut.jsonschema.oracle.generated",
                schemaCacheDir,
                outputDir,
                [
                    new OracleSourceSpec("domains", "io.micronaut.jsonschema.generator.oracle.OracleDomainDiscoveryProvider", null, [include: domainName]),
                    new OracleSourceSpec("views", "io.micronaut.jsonschema.generator.oracle.OracleJsonViewDiscoveryProvider", null, [include: viewName])
                ],
                false,
                true
            )
        )

        then:
        result.generatedTypes() == 2
        result.manifestPath() != null
        Files.exists(result.manifestPath())

        and:
        def manifest = readJson(result.manifestPath())
        jsonAt(manifest, "discovery", "schemas", 0, "name").getStringValue() == domainName
        jsonAt(manifest, "discovery", "schemas", 0, "source").getStringValue() == "DOMAIN_DDL"
        jsonAt(manifest, "discovery", "schemas", 1, "name").getStringValue() == viewName
        jsonAt(manifest, "discovery", "schemas", 1, "source").getStringValue() == "DUALITY_DB_PROVIDED"
        jsonAt(manifest, "emittedSchemaFiles").size() == 2

        and:
        def generatedFiles = Files.walk(outputDir)
            .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".java") }
            .toList()
        generatedFiles.size() == 2
        generatedFiles.any { file ->
            def content = Files.readString(file)
            content.contains("@JsonSchema") && content.contains("record Moonphase")
        }
        generatedFiles.any { file ->
            def content = Files.readString(file)
            content.contains("@JsonSchema") && content.contains("record ApartmentView")
                && content.contains("unitName")
                && content.contains("floorNo")
                && content.contains("status")
                && content.contains("createdAt")
        }

        and:
        logs.any { it.contains("generatedTypes=2") }
    }

    private void withConnection(Closure<?> closure) {
        DriverManager.getConnection(jdbcUrl, username, password).withCloseable { Connection connection ->
            closure.call(connection)
        }
    }

    private static JsonNode readJson(Path path) {
        return JSON_MAPPER.readValue(Files.readString(path), JsonNode)
    }

    private static JsonNode jsonAt(JsonNode node, Object... path) {
        JsonNode current = node
        for (Object segment : path) {
            current = segment instanceof Number ? current.get(((Number) segment).intValue()) : current.get(segment.toString())
        }
        return current
    }
}
