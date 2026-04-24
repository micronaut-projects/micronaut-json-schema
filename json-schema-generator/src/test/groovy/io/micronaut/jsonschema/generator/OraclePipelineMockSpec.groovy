package io.micronaut.jsonschema.generator

import io.micronaut.json.JsonMapper
import io.micronaut.json.tree.JsonNode
import io.micronaut.jsonschema.generator.oracle.OracleJsonSchemaGeneratorConfig
import io.micronaut.jsonschema.generator.oracle.OracleJsonSchemaPipeline
import io.micronaut.jsonschema.generator.oracle.OracleSourceSpec
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.Driver
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException

/**
 * Mock-based coverage for Oracle pipeline branches that are awkward to trigger with a live database.
 */
class OraclePipelineMockSpec extends Specification {

    private static final JsonMapper JSON_MAPPER = JsonMapper.createDefault()

    void "pipeline falls back to domain constraints when get ddl is unavailable"() {
        given:
        Connection connection = Mock()
        PreparedStatement domainListStatement = Mock()
        PreparedStatement ddlStatement = Mock()
        PreparedStatement constraintStatement = Mock()
        ResultSet domainListResult = Mock()
        ResultSet constraintResult = Mock()
        Path schemaCacheDir = Files.createTempDirectory("oracle-mock-schema-cache")
        Path outputDir = Files.createTempDirectory("oracle-mock-output")
        List<String> logs = []
        Driver driver = driverReturning(connection)

        1 * connection.prepareStatement("SELECT name FROM USER_DOMAINS") >> domainListStatement
        1 * domainListStatement.executeQuery() >> domainListResult
        2 * domainListResult.next() >>> [true, false]
        1 * domainListResult.getString(1) >> "MOONPHASE"

        1 * connection.prepareStatement("SELECT dbms_metadata.get_ddl('SQL_DOMAIN', ?) FROM dual") >> ddlStatement
        1 * ddlStatement.setString(1, "MOONPHASE")
        1 * ddlStatement.executeQuery() >> { throw new SQLException("metadata access denied") }

        1 * connection.prepareStatement("SELECT search_condition FROM USER_DOMAIN_CONSTRAINTS WHERE domain_name = ?") >> constraintStatement
        1 * constraintStatement.setString(1, "MOONPHASE")
        1 * constraintStatement.executeQuery() >> constraintResult
        1 * constraintResult.next() >> true
        1 * constraintResult.getString(1) >> """CHECK (VALUE IS JSON VALIDATE USING '{"type":"object","properties":{"phase":{"type":"string","minLength":1}},"required":["phase"]}')"""

        when:
        def result = withRegisteredDriver(driver) {
            new OracleJsonSchemaPipeline({ String message -> logs.add(message) }).execute(
                new OracleJsonSchemaGeneratorConfig(
                    "jdbc:mockoracle:test",
                    "test",
                    "test",
                    "io.micronaut.jsonschema.oracle.generated",
                    schemaCacheDir,
                    outputDir,
                    [new OracleSourceSpec("domains", "io.micronaut.jsonschema.generator.oracle.OracleDomainDiscoveryProvider", null, [include: "MOONPHASE"])],
                    false,
                    true
                )
            )
        }

        then:
        result.generatedTypes() == 1
        Files.exists(result.manifestPath())

        and:
        def manifest = readJson(result.manifestPath())
        jsonAt(manifest, "discovery", "schemas", 0, "sourceName").getStringValue() == "domains"
        jsonAt(manifest, "discovery", "schemas", 0, "name").getStringValue() == "MOONPHASE"
        jsonAt(manifest, "discovery", "schemas", 0, "source").getStringValue() == "DOMAIN_CONSTRAINTS"
        jsonAt(manifest, "warnings", 0, "sourceName").getStringValue() == "domains"
        jsonAt(manifest, "warnings", 0, "code").getStringValue() == "GET_DDL_FAILED"

        and:
        Files.walk(outputDir)
            .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".java") }
            .findFirst()
            .present
        logs.any { it.contains("generatedTypes=1") }
    }

    void "pipeline skips unusable duality view schemas when skip on error is enabled"() {
        given:
        Connection connection = Mock()
        PreparedStatement dualityStatement = Mock()
        ResultSet dualityResult = Mock()
        Path schemaCacheDir = Files.createTempDirectory("oracle-mock-schema-cache")
        Path outputDir = Files.createTempDirectory("oracle-mock-output")
        Driver driver = driverReturning(connection)

        1 * connection.prepareStatement("SELECT view_name, json_schema FROM USER_JSON_DUALITY_VIEWS") >> dualityStatement
        1 * dualityStatement.executeQuery() >> dualityResult
        2 * dualityResult.next() >>> [true, false]
        1 * dualityResult.getString(1) >> "APARTMENT_VIEW"
        1 * dualityResult.getString(2) >> null

        when:
        def result = withRegisteredDriver(driver) {
            new OracleJsonSchemaPipeline({ }).execute(
                new OracleJsonSchemaGeneratorConfig(
                    "jdbc:mockoracle:test",
                    "test",
                    "test",
                    "io.micronaut.jsonschema.oracle.generated",
                    schemaCacheDir,
                    outputDir,
                    [new OracleSourceSpec("views", "io.micronaut.jsonschema.generator.oracle.OracleJsonViewDiscoveryProvider", null, [include: "APARTMENT_VIEW"])],
                    true,
                    true
                )
            )
        }

        then:
        result.generatedTypes() == 0
        Files.exists(result.manifestPath())

        and:
        def manifest = readJson(result.manifestPath())
        jsonAt(manifest, "discovery", "schemas").isArray()
        jsonAt(manifest, "discovery", "schemas").size() == 0
        jsonAt(manifest, "skipped", 0, "sourceName").getStringValue() == "views"
        jsonAt(manifest, "skipped", 0, "name").getStringValue() == "APARTMENT_VIEW"
        jsonAt(manifest, "skipped", 0, "code").getStringValue() == "MISSING_JSON_SCHEMA"
        jsonAt(manifest, "emittedSchemaFiles").size() == 0
    }

    void "pipeline include filters support exact quoted style names"() {
        given:
        Connection connection = Mock()
        PreparedStatement domainListStatement = Mock()
        PreparedStatement ddlStatement = Mock()
        ResultSet domainListResult = Mock()
        ResultSet ddlResult = Mock()
        Path schemaCacheDir = Files.createTempDirectory("oracle-mock-schema-cache")
        Path outputDir = Files.createTempDirectory("oracle-mock-output")
        Driver driver = driverReturning(connection)

        1 * connection.prepareStatement("SELECT name FROM USER_DOMAINS") >> domainListStatement
        1 * domainListStatement.executeQuery() >> domainListResult
        2 * domainListResult.next() >>> [true, false]
        1 * domainListResult.getString(1) >> "MoonPhase"

        1 * connection.prepareStatement("SELECT dbms_metadata.get_ddl('SQL_DOMAIN', ?) FROM dual") >> ddlStatement
        1 * ddlStatement.setString(1, "MoonPhase")
        1 * ddlStatement.executeQuery() >> ddlResult
        1 * ddlResult.next() >> true
        1 * ddlResult.getString(1) >> """CREATE DOMAIN "MoonPhase" AS JSON CHECK (VALUE IS JSON VALIDATE USING '{"type":"object","properties":{"phase":{"type":"string"}},"required":["phase"]}')"""

        when:
        def result = withRegisteredDriver(driver) {
            new OracleJsonSchemaPipeline({ }).execute(
                new OracleJsonSchemaGeneratorConfig(
                    "jdbc:mockoracle:test",
                    "test",
                    "test",
                    "io.micronaut.jsonschema.oracle.generated",
                    schemaCacheDir,
                    outputDir,
                    [new OracleSourceSpec("domains", "io.micronaut.jsonschema.generator.oracle.OracleDomainDiscoveryProvider", null, [include: "MoonPhase"])],
                    false,
                    true
                )
            )
        }

        then:
        result.generatedTypes() == 1
        def manifest = readJson(result.manifestPath())
        jsonAt(manifest, "discovery", "schemas", 0, "name").getStringValue() == "MoonPhase"
    }

    void "pipeline returns empty result when database is unavailable and fail on missing db is disabled"() {
        given:
        List<String> logs = []
        Driver driver = driverThrowing(new SQLException("database unavailable"))

        when:
        def result = withRegisteredDriver(driver) {
            new OracleJsonSchemaPipeline({ String message -> logs.add(message) }).execute(
                new OracleJsonSchemaGeneratorConfig(
                    "jdbc:mockoracle:test",
                    "test",
                    "test",
                    "io.micronaut.jsonschema.oracle.generated",
                    Files.createTempDirectory("oracle-mock-schema-cache"),
                    Files.createTempDirectory("oracle-mock-output"),
                    [new OracleSourceSpec("domains", "io.micronaut.jsonschema.generator.oracle.OracleDomainDiscoveryProvider", null, [include: "*"])],
                    false,
                    false
                )
            )
        }

        then:
        result.manifestPath() == null
        result.generatedTypes() == 0
        logs.any { it.contains("DB_UNAVAILABLE") }
    }

    private Driver driverReturning(Connection connection) {
        Stub(Driver) {
            acceptsURL("jdbc:mockoracle:test") >> true
            connect("jdbc:mockoracle:test", _ as Properties) >> connection
        }
    }

    private Driver driverThrowing(SQLException exception) {
        Stub(Driver) {
            acceptsURL("jdbc:mockoracle:test") >> true
            connect("jdbc:mockoracle:test", _ as Properties) >> { throw exception }
        }
    }

    private <T> T withRegisteredDriver(Driver driver, Closure<T> closure) {
        DriverManager.registerDriver(driver)
        try {
            return closure.call()
        } finally {
            DriverManager.deregisterDriver(driver)
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
