package io.micronaut.jsonschema.generator

import io.micronaut.json.JsonMapper
import io.micronaut.json.tree.JsonNode
import io.micronaut.jsonschema.generator.records.JsonSchemaRecordsGeneratorConfig
import io.micronaut.jsonschema.generator.records.JsonSchemaRecordsPipeline
import io.micronaut.jsonschema.generator.discovery.SourceSpec
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
        PreparedStatement constraintProbeStatement = Mock()
        PreparedStatement constraintStatement = Mock()
        ResultSet domainListResult = Mock()
        ResultSet constraintResult = Mock()
        Path schemaCacheDir = Files.createTempDirectory("oracle-mock-schema-cache")
        Path outputDir = Files.createTempDirectory("oracle-mock-output")
        List<String> logs = []
        Driver driver = driverReturning(connection)

        1 * connection.prepareStatement("SELECT name FROM USER_DOMAINS WHERE name IN (?)") >> domainListStatement
        1 * domainListStatement.setString(1, "MOONPHASE")
        1 * domainListStatement.executeQuery() >> domainListResult
        2 * domainListResult.next() >>> [true, false]
        1 * domainListResult.getString(1) >> "MOONPHASE"

        1 * connection.prepareStatement("SELECT dbms_metadata.get_ddl('SQL_DOMAIN', ?) FROM dual") >> ddlStatement
        1 * ddlStatement.setString(1, "MOONPHASE")
        1 * ddlStatement.executeQuery() >> { throw new SQLException("metadata access denied") }

        1 * connection.prepareStatement("SELECT 1 FROM USER_DOMAIN_CONSTRAINTS FETCH FIRST 1 ROWS ONLY") >> constraintProbeStatement
        1 * constraintProbeStatement.executeQuery()

        1 * connection.prepareStatement("SELECT search_condition FROM USER_DOMAIN_CONSTRAINTS WHERE domain_name = ?") >> constraintStatement
        1 * constraintStatement.setString(1, "MOONPHASE")
        1 * constraintStatement.executeQuery() >> constraintResult
        1 * constraintResult.next() >> true
        1 * constraintResult.getString(1) >> """CHECK (VALUE IS JSON VALIDATE USING '{"type":"object","properties":{"phase":{"type":"string","minLength":1}},"required":["phase"]}')"""

        when:
        def result = withRegisteredDriver(driver) {
            new JsonSchemaRecordsPipeline({ String message -> logs.add(message) }).execute(
                new JsonSchemaRecordsGeneratorConfig(
                    "jdbc:mockoracle:test",
                    "test",
                    "test",
                    "io.micronaut.jsonschema.oracle.generated",
                    21,
                    schemaCacheDir,
                    outputDir,
                    [new SourceSpec("domains", "oracle-domains", [include: "MOONPHASE"])],
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
        jsonAt(manifest, "parameters", "languageLevel").getIntValue() == 21
        jsonAt(manifest, "discovery", "schemas", 0, "sourceName").getStringValue() == "domains"
        jsonAt(manifest, "discovery", "schemas", 0, "name").getStringValue() == "MOONPHASE"
        jsonAt(manifest, "discovery", "schemas", 0, "retrievalMode").getStringValue() == "DOMAIN_CONSTRAINTS"
        jsonAt(manifest, "generatedJavaFiles", 0).getStringValue() == "io/micronaut/jsonschema/oracle/generated/Moonphase.java"
        jsonAt(manifest, "warnings", 0, "sourceName").getStringValue() == "domains"
        jsonAt(manifest, "warnings", 0, "code").getStringValue() == "GET_DDL_FAILED"

        and:
        Files.walk(outputDir)
            .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".java") }
            .findFirst()
            .present
        logs.any { it.contains("languageLevel=21") && it.contains("schemaCacheDir=") && it.contains("outputDir=") }
        logs.any { it.contains("dictionaryView=USER_DOMAINS") }
        logs.any { it.contains("name=MOONPHASE") && it.contains("retrievalMode=DOMAIN_CONSTRAINTS") }
        logs.any { it.contains("discoveredSchemas=1") }
        logs.any { it.contains("generatedTypes=1") }
    }

    void "pipeline falls back to domain constraints when get ddl cannot be parsed"() {
        given:
        Connection connection = Mock()
        PreparedStatement domainListStatement = Mock()
        PreparedStatement ddlStatement = Mock()
        PreparedStatement constraintProbeStatement = Mock()
        PreparedStatement constraintStatement = Mock()
        ResultSet domainListResult = Mock()
        ResultSet ddlResult = Mock()
        ResultSet constraintResult = Mock()
        Driver driver = driverReturning(connection)

        1 * connection.prepareStatement("SELECT name FROM USER_DOMAINS WHERE name IN (?)") >> domainListStatement
        1 * domainListStatement.setString(1, "MOONPHASE")
        1 * domainListStatement.executeQuery() >> domainListResult
        2 * domainListResult.next() >>> [true, false]
        1 * domainListResult.getString(1) >> "MOONPHASE"

        1 * connection.prepareStatement("SELECT dbms_metadata.get_ddl('SQL_DOMAIN', ?) FROM dual") >> ddlStatement
        1 * ddlStatement.setString(1, "MOONPHASE")
        1 * ddlStatement.executeQuery() >> ddlResult
        1 * ddlResult.next() >> true
        1 * ddlResult.getString(1) >> "CREATE DOMAIN MOONPHASE AS JSON"

        1 * connection.prepareStatement("SELECT 1 FROM USER_DOMAIN_CONSTRAINTS FETCH FIRST 1 ROWS ONLY") >> constraintProbeStatement
        1 * constraintProbeStatement.executeQuery()

        1 * connection.prepareStatement("SELECT search_condition FROM USER_DOMAIN_CONSTRAINTS WHERE domain_name = ?") >> constraintStatement
        1 * constraintStatement.setString(1, "MOONPHASE")
        1 * constraintStatement.executeQuery() >> constraintResult
        1 * constraintResult.next() >> true
        1 * constraintResult.getString(1) >> """CHECK (VALUE IS JSON VALIDATE USING '{"type":"object","properties":{"phase":{"type":"string"}},"required":["phase"]}')"""

        when:
        def result = withRegisteredDriver(driver) {
            new JsonSchemaRecordsPipeline({ }).execute(
                new JsonSchemaRecordsGeneratorConfig(
                    "jdbc:mockoracle:test",
                    "test",
                    "test",
                    "io.micronaut.jsonschema.oracle.generated",
                    21,
                    Files.createTempDirectory("oracle-mock-schema-cache"),
                    Files.createTempDirectory("oracle-mock-output"),
                    [new SourceSpec("domains", "oracle-domains", [include: "MOONPHASE"])],
                    false,
                    true
                )
            )
        }

        then:
        result.generatedTypes() == 1
        def manifest = readJson(result.manifestPath())
        jsonAt(manifest, "discovery", "schemas", 0, "retrievalMode").getStringValue() == "DOMAIN_CONSTRAINTS"
        jsonAt(manifest, "warnings", 0, "code").getStringValue() == "GET_DDL_FAILED"
    }

    void "pipeline falls back to owner scoped domain constraints when get ddl cannot be parsed"() {
        given:
        Connection connection = Mock()
        PreparedStatement scopeProbeStatement = Mock()
        PreparedStatement domainListStatement = Mock()
        PreparedStatement ddlStatement = Mock()
        PreparedStatement constraintProbeStatement = Mock()
        PreparedStatement constraintStatement = Mock()
        ResultSet scopeProbeResult = Mock()
        ResultSet domainListResult = Mock()
        ResultSet ddlResult = Mock()
        ResultSet constraintProbeResult = Mock()
        ResultSet constraintResult = Mock()
        Driver driver = driverReturning(connection)

        1 * connection.prepareStatement("SELECT 1 FROM ALL_DOMAINS WHERE owner = ? FETCH FIRST 1 ROWS ONLY") >> scopeProbeStatement
        1 * scopeProbeStatement.setString(1, "HR")
        1 * scopeProbeStatement.executeQuery() >> scopeProbeResult

        1 * connection.prepareStatement("SELECT name FROM ALL_DOMAINS WHERE owner = ? AND name IN (?)") >> domainListStatement
        1 * domainListStatement.setString(1, "HR")
        1 * domainListStatement.setString(2, "MOONPHASE")
        1 * domainListStatement.executeQuery() >> domainListResult
        2 * domainListResult.next() >>> [true, false]
        1 * domainListResult.getString(1) >> "MOONPHASE"

        1 * connection.prepareStatement("SELECT dbms_metadata.get_ddl('SQL_DOMAIN', ?, ?) FROM dual") >> ddlStatement
        1 * ddlStatement.setString(1, "MOONPHASE")
        1 * ddlStatement.setString(2, "HR")
        1 * ddlStatement.executeQuery() >> ddlResult
        1 * ddlResult.next() >> true
        1 * ddlResult.getString(1) >> "CREATE DOMAIN MOONPHASE AS JSON"

        1 * connection.prepareStatement("SELECT 1 FROM ALL_DOMAIN_CONSTRAINTS WHERE domain_owner = ? FETCH FIRST 1 ROWS ONLY") >> constraintProbeStatement
        1 * constraintProbeStatement.setString(1, "HR")
        1 * constraintProbeStatement.executeQuery() >> constraintProbeResult

        1 * connection.prepareStatement("SELECT search_condition FROM ALL_DOMAIN_CONSTRAINTS WHERE domain_owner = ? AND domain_name = ?") >> constraintStatement
        1 * constraintStatement.setString(1, "HR")
        1 * constraintStatement.setString(2, "MOONPHASE")
        1 * constraintStatement.executeQuery() >> constraintResult
        1 * constraintResult.next() >> true
        1 * constraintResult.getString(1) >> """CHECK (VALUE IS JSON VALIDATE USING '{"type":"object","properties":{"phase":{"type":"string"}},"required":["phase"]}')"""

        when:
        def result = withRegisteredDriver(driver) {
            new JsonSchemaRecordsPipeline({ }).execute(
                new JsonSchemaRecordsGeneratorConfig(
                    "jdbc:mockoracle:test",
                    "test",
                    "test",
                    "io.micronaut.jsonschema.oracle.generated",
                    21,
                    Files.createTempDirectory("oracle-mock-schema-cache"),
                    Files.createTempDirectory("oracle-mock-output"),
                    [new SourceSpec("domains", "oracle-domains", [owner: "hr", include: "MOONPHASE"])],
                    false,
                    true
                )
            )
        }

        then:
        result.generatedTypes() == 1
        def manifest = readJson(result.manifestPath())
        jsonAt(manifest, "discovery", "schemas", 0, "retrievalMode").getStringValue() == "DOMAIN_CONSTRAINTS"
        jsonAt(manifest, "warnings", 0, "code").getStringValue() == "GET_DDL_FAILED"
    }

    void "pipeline skips domain when domain constraints fallback view is unavailable"() {
        given:
        Connection connection = Mock()
        PreparedStatement domainListStatement = Mock()
        PreparedStatement ddlStatement = Mock()
        PreparedStatement constraintProbeStatement = Mock()
        ResultSet domainListResult = Mock()
        Driver driver = driverReturning(connection)

        1 * connection.prepareStatement("SELECT name FROM USER_DOMAINS WHERE name IN (?)") >> domainListStatement
        1 * domainListStatement.setString(1, "MOONPHASE")
        1 * domainListStatement.executeQuery() >> domainListResult
        2 * domainListResult.next() >>> [true, false]
        1 * domainListResult.getString(1) >> "MOONPHASE"

        1 * connection.prepareStatement("SELECT dbms_metadata.get_ddl('SQL_DOMAIN', ?) FROM dual") >> ddlStatement
        1 * ddlStatement.setString(1, "MOONPHASE")
        1 * ddlStatement.executeQuery() >> { throw new SQLException("metadata access denied") }

        1 * connection.prepareStatement("SELECT 1 FROM USER_DOMAIN_CONSTRAINTS FETCH FIRST 1 ROWS ONLY") >> constraintProbeStatement
        1 * constraintProbeStatement.executeQuery() >> { throw new SQLException("view missing") }

        when:
        def result = withRegisteredDriver(driver) {
            new JsonSchemaRecordsPipeline({ }).execute(
                new JsonSchemaRecordsGeneratorConfig(
                    "jdbc:mockoracle:test",
                    "test",
                    "test",
                    "io.micronaut.jsonschema.oracle.generated",
                    21,
                    Files.createTempDirectory("oracle-mock-schema-cache"),
                    Files.createTempDirectory("oracle-mock-output"),
                    [new SourceSpec("domains", "oracle-domains", [include: "MOONPHASE"])],
                    true,
                    true
                )
            )
        }

        then:
        result.generatedTypes() == 0
        def manifest = readJson(result.manifestPath())
        jsonAt(manifest, "warnings", 0, "code").getStringValue() == "GET_DDL_FAILED"
        jsonAt(manifest, "warnings", 1, "code").getStringValue() == "DICTIONARY_VIEW_UNAVAILABLE"
        jsonAt(manifest, "skipped", 0, "code").getStringValue() == "DICTIONARY_VIEW_UNAVAILABLE"
        jsonAt(manifest, "skipped", 0, "retrievalMode").isNull()
    }

    void "pipeline skips unusable duality view schemas when skip on error is enabled"() {
        given:
        Connection connection = Mock()
        PreparedStatement dualityStatement = Mock()
        ResultSet dualityResult = Mock()
        Path schemaCacheDir = Files.createTempDirectory("oracle-mock-schema-cache")
        Path outputDir = Files.createTempDirectory("oracle-mock-output")
        Driver driver = driverReturning(connection)

        1 * connection.prepareStatement("SELECT view_name, json_schema FROM USER_JSON_DUALITY_VIEWS WHERE view_name IN (?)") >> dualityStatement
        1 * dualityStatement.setString(1, "APARTMENT_VIEW")
        1 * dualityStatement.executeQuery() >> dualityResult
        2 * dualityResult.next() >>> [true, false]
        1 * dualityResult.getString(1) >> "APARTMENT_VIEW"
        1 * dualityResult.getString(2) >> null

        when:
        def result = withRegisteredDriver(driver) {
            new JsonSchemaRecordsPipeline({ }).execute(
                new JsonSchemaRecordsGeneratorConfig(
                    "jdbc:mockoracle:test",
                    "test",
                    "test",
                    "io.micronaut.jsonschema.oracle.generated",
                    21,
                    schemaCacheDir,
                    outputDir,
                    [new SourceSpec("duality_views", "oracle-duality-views", [include: "APARTMENT_VIEW"])],
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
        jsonAt(manifest, "skipped", 0, "sourceName").getStringValue() == "duality_views"
        jsonAt(manifest, "skipped", 0, "name").getStringValue() == "APARTMENT_VIEW"
        jsonAt(manifest, "skipped", 0, "code").getStringValue() == "MISSING_JSON_SCHEMA"
        jsonAt(manifest, "emittedSchemaFiles").size() == 0
    }

    void "pipeline skips malformed duality view schemas when skip on error is enabled"() {
        given:
        Connection connection = Mock()
        PreparedStatement dualityStatement = Mock()
        ResultSet dualityResult = Mock()
        Driver driver = driverReturning(connection)

        1 * connection.prepareStatement("SELECT view_name, json_schema FROM USER_JSON_DUALITY_VIEWS WHERE view_name IN (?)") >> dualityStatement
        1 * dualityStatement.setString(1, "BROKEN_DV")
        1 * dualityStatement.executeQuery() >> dualityResult
        2 * dualityResult.next() >>> [true, false]
        1 * dualityResult.getString(1) >> "BROKEN_DV"
        1 * dualityResult.getString(2) >> "{broken"

        when:
        def result = withRegisteredDriver(driver) {
            new JsonSchemaRecordsPipeline({ }).execute(
                new JsonSchemaRecordsGeneratorConfig(
                    "jdbc:mockoracle:test",
                    "test",
                    "test",
                    "io.micronaut.jsonschema.oracle.generated",
                    21,
                    Files.createTempDirectory("oracle-mock-schema-cache"),
                    Files.createTempDirectory("oracle-mock-output"),
                    [new SourceSpec("duality_views", "oracle-duality-views", [include: "BROKEN_DV"])],
                    true,
                    true
                )
            )
        }

        then:
        result.generatedTypes() == 0
        def manifest = readJson(result.manifestPath())
        jsonAt(manifest, "skipped", 0, "code").getStringValue() == "MALFORMED_JSON"
        jsonAt(manifest, "skipped", 0, "retrievalMode").getStringValue() == "DUALITY_DB_PROVIDED"
        jsonAt(manifest, "warnings", 0, "code").getStringValue() == "MALFORMED_JSON"
    }

    void "pipeline falls back to user duality view scope when owner matches session user and cross schema views are unavailable"() {
        given:
        Connection connection = Mock()
        PreparedStatement allStatement = Mock()
        PreparedStatement dbaStatement = Mock()
        PreparedStatement sessionUserStatement = Mock()
        PreparedStatement dualityStatement = Mock()
        ResultSet sessionUserResult = Mock()
        ResultSet dualityResult = Mock()
        Path schemaCacheDir = Files.createTempDirectory("oracle-mock-schema-cache")
        Path outputDir = Files.createTempDirectory("oracle-mock-output")
        Driver driver = driverReturning(connection)

        1 * connection.prepareStatement("SELECT 1 FROM ALL_JSON_DUALITY_VIEWS WHERE owner = ? FETCH FIRST 1 ROWS ONLY") >> allStatement
        1 * allStatement.setString(1, "HR")
        1 * allStatement.executeQuery() >> { throw new SQLException("ALL_JSON_DUALITY_VIEWS denied") }
        1 * connection.prepareStatement("SELECT 1 FROM DBA_JSON_DUALITY_VIEWS WHERE owner = ? FETCH FIRST 1 ROWS ONLY") >> dbaStatement
        1 * dbaStatement.setString(1, "HR")
        1 * dbaStatement.executeQuery() >> { throw new SQLException("DBA_JSON_DUALITY_VIEWS denied") }
        1 * connection.prepareStatement("SELECT SYS_CONTEXT('USERENV', 'SESSION_USER') FROM dual") >> sessionUserStatement
        1 * sessionUserStatement.executeQuery() >> sessionUserResult
        1 * sessionUserResult.next() >> true
        1 * sessionUserResult.getString(1) >> "HR"

        1 * connection.prepareStatement("SELECT view_name, json_schema FROM USER_JSON_DUALITY_VIEWS WHERE view_name IN (?)") >> dualityStatement
        1 * dualityStatement.setString(1, "PRODUCT_DV")
        1 * dualityStatement.executeQuery() >> dualityResult
        2 * dualityResult.next() >>> [true, false]
        1 * dualityResult.getString(1) >> "PRODUCT_DV"
        1 * dualityResult.getString(2) >> '{"type":"object","properties":{"id":{"type":"integer"}},"required":["id"]}'

        when:
        def result = withRegisteredDriver(driver) {
            new JsonSchemaRecordsPipeline({ }).execute(
                new JsonSchemaRecordsGeneratorConfig(
                    "jdbc:mockoracle:test",
                    "test",
                    "test",
                    "io.micronaut.jsonschema.oracle.generated",
                    21,
                    schemaCacheDir,
                    outputDir,
                    [new SourceSpec("duality_views", "oracle-duality-views", [owner: " hr ", include: "PRODUCT_DV"])],
                    false,
                    true
                )
            )
        }

        then:
        result.generatedTypes() == 1
        def manifest = readJson(result.manifestPath())
        jsonAt(manifest, "warnings", 0, "sourceName").getStringValue() == "duality_views"
        jsonAt(manifest, "warnings", 0, "scope").getStringValue() == "DUALITY_VIEW"
        jsonAt(manifest, "warnings", 0, "name").isNull()
        jsonAt(manifest, "warnings", 0, "code").getStringValue() == "OWNER_SCOPE_FALLBACK"
        jsonAt(manifest, "discovery", "schemas", 0, "name").getStringValue() == "PRODUCT_DV"
        jsonAt(manifest, "discovery", "schemas", 0, "schemaFile").getStringValue() == "sources/duality_views/HR_PRODUCT_DV.schema.json"
    }

    void "pipeline records no inputs discovered as source-level warning when fail on missing source is disabled"() {
        given:
        Connection connection = Mock()
        PreparedStatement domainListStatement = Mock()
        ResultSet domainListResult = Mock()
        List<String> logs = []
        Driver driver = driverReturning(connection)

        1 * connection.prepareStatement("SELECT name FROM USER_DOMAINS WHERE name IN (?)") >> domainListStatement
        1 * domainListStatement.setString(1, "MARS")
        1 * domainListStatement.executeQuery() >> domainListResult
        1 * domainListResult.next() >> false

        when:
        def result = withRegisteredDriver(driver) {
            new JsonSchemaRecordsPipeline({ String message -> logs.add(message) }).execute(
                new JsonSchemaRecordsGeneratorConfig(
                    "jdbc:mockoracle:test",
                    "test",
                    "test",
                    "io.micronaut.jsonschema.oracle.generated",
                    21,
                    Files.createTempDirectory("oracle-mock-schema-cache"),
                    Files.createTempDirectory("oracle-mock-output"),
                    [new SourceSpec("domains", "oracle-domains", [include: "MARS"])],
                    false,
                    false
                )
            )
        }

        then:
        result.generatedTypes() == 0
        logs.any { it.contains("name=-") && it.contains("NO_INPUTS_DISCOVERED") }

        and:
        def manifest = readJson(result.manifestPath())
        jsonAt(manifest, "warnings", 0, "scope").getStringValue() == "DOMAIN"
        jsonAt(manifest, "warnings", 0, "name").isNull()
        jsonAt(manifest, "warnings", 0, "step").getStringValue() == "DISCOVERY"
        jsonAt(manifest, "warnings", 0, "code").getStringValue() == "NO_INPUTS_DISCOVERED"
        jsonAt(manifest, "skipped").size() == 0
    }

    void "pipeline normalizes Oracle include filters and discovered identifiers"() {
        given:
        Connection connection = Mock()
        PreparedStatement domainListStatement = Mock()
        PreparedStatement ddlStatement = Mock()
        ResultSet domainListResult = Mock()
        ResultSet ddlResult = Mock()
        Path schemaCacheDir = Files.createTempDirectory("oracle-mock-schema-cache")
        Path outputDir = Files.createTempDirectory("oracle-mock-output")
        Driver driver = driverReturning(connection)

        1 * connection.prepareStatement("SELECT name FROM USER_DOMAINS WHERE name IN (?)") >> domainListStatement
        1 * domainListStatement.setString(1, "MOONPHASE")
        1 * domainListStatement.executeQuery() >> domainListResult
        2 * domainListResult.next() >>> [true, false]
        1 * domainListResult.getString(1) >> "MoonPhase"

        1 * connection.prepareStatement("SELECT dbms_metadata.get_ddl('SQL_DOMAIN', ?) FROM dual") >> ddlStatement
        1 * ddlStatement.setString(1, "MOONPHASE")
        1 * ddlStatement.executeQuery() >> ddlResult
        1 * ddlResult.next() >> true
        1 * ddlResult.getString(1) >> """CREATE DOMAIN MOONPHASE AS JSON CHECK (VALUE IS JSON VALIDATE USING '{"type":"object","properties":{"phase":{"type":"string"}},"required":["phase"]}')"""

        when:
        def result = withRegisteredDriver(driver) {
            new JsonSchemaRecordsPipeline({ }).execute(
                new JsonSchemaRecordsGeneratorConfig(
                    "jdbc:mockoracle:test",
                    "test",
                    "test",
                    "io.micronaut.jsonschema.oracle.generated",
                    21,
                    schemaCacheDir,
                    outputDir,
                    [new SourceSpec("domains", "oracle-domains", [include: "MoonPhase"])],
                    false,
                    true
                )
            )
        }

        then:
        result.generatedTypes() == 1
        def manifest = readJson(result.manifestPath())
        jsonAt(manifest, "discovery", "schemas", 0, "name").getStringValue() == "MOONPHASE"
    }

    void "pipeline accepts Oracle include and exclude filters as list options"() {
        given:
        Connection connection = Mock()
        PreparedStatement domainListStatement = Mock()
        PreparedStatement ddlStatement = Mock()
        ResultSet domainListResult = Mock()
        ResultSet ddlResult = Mock()
        Path schemaCacheDir = Files.createTempDirectory("oracle-mock-schema-cache")
        Path outputDir = Files.createTempDirectory("oracle-mock-output")
        Driver driver = driverReturning(connection)

        1 * connection.prepareStatement("SELECT name FROM USER_DOMAINS WHERE name IN (?, ?) AND name NOT IN (?)") >> domainListStatement
        1 * domainListStatement.setString(1, "MARS")
        1 * domainListStatement.setString(2, "MOONPHASE")
        1 * domainListStatement.setString(3, "MARS")
        1 * domainListStatement.executeQuery() >> domainListResult
        2 * domainListResult.next() >>> [true, false]
        1 * domainListResult.getString(1) >> "MoonPhase"

        1 * connection.prepareStatement("SELECT dbms_metadata.get_ddl('SQL_DOMAIN', ?) FROM dual") >> ddlStatement
        1 * ddlStatement.setString(1, "MOONPHASE")
        1 * ddlStatement.executeQuery() >> ddlResult
        1 * ddlResult.next() >> true
        1 * ddlResult.getString(1) >> """CREATE DOMAIN MOONPHASE AS JSON CHECK (VALUE IS JSON VALIDATE USING '{"type":"object","properties":{"phase":{"type":"string"}},"required":["phase"]}')"""

        when:
        def result = withRegisteredDriver(driver) {
            new JsonSchemaRecordsPipeline({ }).execute(
                new JsonSchemaRecordsGeneratorConfig(
                    "jdbc:mockoracle:test",
                    "test",
                    "test",
                    "io.micronaut.jsonschema.oracle.generated",
                    21,
                    schemaCacheDir,
                    outputDir,
                    [new SourceSpec("domains", "oracle-domains", [
                        include: [" mars ", "moonphase"],
                        exclude: ["MARS"]
                    ])],
                    false,
                    true
                )
            )
        }

        then:
        result.generatedTypes() == 1
        def manifest = readJson(result.manifestPath())
        jsonAt(manifest, "discovery", "schemas", 0, "name").getStringValue() == "MOONPHASE"
        jsonAt(manifest, "parameters", "sources", 0, "options", "include").isArray()
        jsonAt(manifest, "parameters", "sources", 0, "options", "include").size() == 2
    }

    void "pipeline writes manifest and warning when source is unavailable and fail on missing source is disabled"() {
        given:
        List<String> logs = []
        Driver driver = driverThrowing(new SQLException("database unavailable"))

        when:
        def result = withRegisteredDriver(driver) {
            new JsonSchemaRecordsPipeline({ String message -> logs.add(message) }).execute(
                new JsonSchemaRecordsGeneratorConfig(
                    "jdbc:mockoracle:test",
                    "test",
                    "test",
                    "io.micronaut.jsonschema.oracle.generated",
                    21,
                    Files.createTempDirectory("oracle-mock-schema-cache"),
                    Files.createTempDirectory("oracle-mock-output"),
                    [new SourceSpec("domains", "oracle-domains", [include: "*"])],
                    false,
                    false
                )
            )
        }

        then:
        result.manifestPath() != null
        result.generatedTypes() == 0
        logs.any { it.contains("SOURCE_UNAVAILABLE") }

        and:
        def manifest = readJson(result.manifestPath())
        jsonAt(manifest, "warnings", 0, "sourceName").getStringValue() == "domains"
        jsonAt(manifest, "warnings", 0, "scope").getStringValue() == "DOMAIN"
        jsonAt(manifest, "warnings", 0, "code").getStringValue() == "SOURCE_UNAVAILABLE"
    }

    void "pipeline records privilege denied when owner scope dictionary views are unavailable and fail on missing source is disabled"() {
        given:
        Connection connection = Mock()
        PreparedStatement allStatement = Mock()
        PreparedStatement dbaStatement = Mock()
        PreparedStatement sessionUserStatement = Mock()
        ResultSet sessionUserResult = Mock()
        Path schemaCacheDir = Files.createTempDirectory("oracle-mock-schema-cache")
        Path outputDir = Files.createTempDirectory("oracle-mock-output")
        Driver driver = driverReturning(connection)

        1 * connection.prepareStatement("SELECT 1 FROM ALL_DOMAINS WHERE owner = ? FETCH FIRST 1 ROWS ONLY") >> allStatement
        1 * allStatement.setString(1, "HR")
        1 * allStatement.executeQuery() >> { throw new SQLException("ALL_DOMAINS denied") }
        1 * connection.prepareStatement("SELECT 1 FROM DBA_DOMAINS WHERE owner = ? FETCH FIRST 1 ROWS ONLY") >> dbaStatement
        1 * dbaStatement.setString(1, "HR")
        1 * dbaStatement.executeQuery() >> { throw new SQLException("DBA_DOMAINS denied") }
        1 * connection.prepareStatement("SELECT SYS_CONTEXT('USERENV', 'SESSION_USER') FROM dual") >> sessionUserStatement
        1 * sessionUserStatement.executeQuery() >> sessionUserResult
        1 * sessionUserResult.next() >> true
        1 * sessionUserResult.getString(1) >> "APP"

        when:
        def result = withRegisteredDriver(driver) {
            new JsonSchemaRecordsPipeline({ }).execute(
                new JsonSchemaRecordsGeneratorConfig(
                    "jdbc:mockoracle:test",
                    "test",
                    "test",
                    "io.micronaut.jsonschema.oracle.generated",
                    21,
                    schemaCacheDir,
                    outputDir,
                    [new SourceSpec("domains", "oracle-domains", [owner: "HR", include: "*"])],
                    false,
                    false
                )
            )
        }

        then:
        result.generatedTypes() == 0
        def manifest = readJson(result.manifestPath())
        jsonAt(manifest, "warnings", 0, "sourceName").getStringValue() == "domains"
        jsonAt(manifest, "warnings", 0, "scope").getStringValue() == "DOMAIN"
        jsonAt(manifest, "warnings", 0, "name").isNull()
        jsonAt(manifest, "warnings", 0, "code").getStringValue() == "PRIVILEGE_DENIED"
    }

    void "pipeline falls back to user scope when owner matches session user and cross schema views are unavailable"() {
        given:
        Connection connection = Mock()
        PreparedStatement allStatement = Mock()
        PreparedStatement dbaStatement = Mock()
        PreparedStatement sessionUserStatement = Mock()
        PreparedStatement domainListStatement = Mock()
        PreparedStatement ddlStatement = Mock()
        ResultSet sessionUserResult = Mock()
        ResultSet domainListResult = Mock()
        ResultSet ddlResult = Mock()
        Path schemaCacheDir = Files.createTempDirectory("oracle-mock-schema-cache")
        Path outputDir = Files.createTempDirectory("oracle-mock-output")
        Driver driver = driverReturning(connection)

        1 * connection.prepareStatement("SELECT 1 FROM ALL_DOMAINS WHERE owner = ? FETCH FIRST 1 ROWS ONLY") >> allStatement
        1 * allStatement.setString(1, "HR")
        1 * allStatement.executeQuery() >> { throw new SQLException("ALL_DOMAINS denied") }
        1 * connection.prepareStatement("SELECT 1 FROM DBA_DOMAINS WHERE owner = ? FETCH FIRST 1 ROWS ONLY") >> dbaStatement
        1 * dbaStatement.setString(1, "HR")
        1 * dbaStatement.executeQuery() >> { throw new SQLException("DBA_DOMAINS denied") }
        1 * connection.prepareStatement("SELECT SYS_CONTEXT('USERENV', 'SESSION_USER') FROM dual") >> sessionUserStatement
        1 * sessionUserStatement.executeQuery() >> sessionUserResult
        1 * sessionUserResult.next() >> true
        1 * sessionUserResult.getString(1) >> "HR"

        1 * connection.prepareStatement("SELECT name FROM USER_DOMAINS WHERE name IN (?)") >> domainListStatement
        1 * domainListStatement.setString(1, "MOONPHASE")
        1 * domainListStatement.executeQuery() >> domainListResult
        2 * domainListResult.next() >>> [true, false]
        1 * domainListResult.getString(1) >> "MOONPHASE"

        1 * connection.prepareStatement("SELECT dbms_metadata.get_ddl('SQL_DOMAIN', ?) FROM dual") >> ddlStatement
        1 * ddlStatement.setString(1, "MOONPHASE")
        1 * ddlStatement.executeQuery() >> ddlResult
        1 * ddlResult.next() >> true
        1 * ddlResult.getString(1) >> """CREATE DOMAIN MOONPHASE AS JSON CHECK (VALUE IS JSON VALIDATE USING '{"type":"object","properties":{"phase":{"type":"string"}},"required":["phase"]}')"""

        when:
        def result = withRegisteredDriver(driver) {
            new JsonSchemaRecordsPipeline({ }).execute(
                new JsonSchemaRecordsGeneratorConfig(
                    "jdbc:mockoracle:test",
                    "test",
                    "test",
                    "io.micronaut.jsonschema.oracle.generated",
                    21,
                    schemaCacheDir,
                    outputDir,
                    [new SourceSpec("domains", "oracle-domains", [owner: " hr ", include: "MOONPHASE"])],
                    false,
                    true
                )
            )
        }

        then:
        result.generatedTypes() == 1
        def manifest = readJson(result.manifestPath())
        jsonAt(manifest, "warnings", 0, "sourceName").getStringValue() == "domains"
        jsonAt(manifest, "warnings", 0, "scope").getStringValue() == "DOMAIN"
        jsonAt(manifest, "warnings", 0, "name").isNull()
        jsonAt(manifest, "warnings", 0, "code").getStringValue() == "OWNER_SCOPE_FALLBACK"
        jsonAt(manifest, "discovery", "schemas", 0, "name").getStringValue() == "MOONPHASE"
        jsonAt(manifest, "discovery", "schemas", 0, "schemaFile").getStringValue() == "sources/domains/HR_MOONPHASE.schema.json"
    }

    void "pipeline fails before writing generated sources when discovered schemas collide"() {
        when:
        new JsonSchemaRecordsPipeline({ }).execute(
            new JsonSchemaRecordsGeneratorConfig(
                null,
                null,
                null,
                "io.micronaut.jsonschema.custom.generated",
                21,
                Files.createTempDirectory("custom-schema-cache"),
                Files.createTempDirectory("custom-output"),
                [new SourceSpec("custom", "test-colliding", [:])],
                false,
                true
            )
        )

        then:
        IOException exception = thrown()
        exception.message.contains("NAME_COLLISION")
        exception.message.contains("CUSTOMER")
        exception.message.contains("customer")
    }

    void "pipeline skips colliding schemas and keeps cache filenames unique when skip on error is enabled"() {
        given:
        Path schemaCacheDir = Files.createTempDirectory("custom-schema-cache")

        when:
        def result = new JsonSchemaRecordsPipeline({ }).execute(
            new JsonSchemaRecordsGeneratorConfig(
                null,
                null,
                null,
                "io.micronaut.jsonschema.custom.generated",
                21,
                schemaCacheDir,
                Files.createTempDirectory("custom-output"),
                [new SourceSpec("custom", "test-colliding", [:])],
                true,
                true
            )
        )

        then:
        result.generatedTypes() == 0
        def manifest = readJson(result.manifestPath())
        jsonAt(manifest, "skipped").size() == 2
        jsonAt(manifest, "skipped", 0, "code").getStringValue() == "NAME_COLLISION"
        jsonAt(manifest, "skipped", 1, "code").getStringValue() == "NAME_COLLISION"
        jsonAt(manifest, "emittedSchemaFiles", 0).getStringValue() == "sources/custom/CUSTOMER.schema.json"
        jsonAt(manifest, "emittedSchemaFiles", 1).getStringValue() == "sources/custom/CUSTOMER_2.schema.json"
    }

    void "pipeline skips schema with sanitized member name collision when skip on error is enabled"() {
        when:
        def result = new JsonSchemaRecordsPipeline({ }).execute(
            new JsonSchemaRecordsGeneratorConfig(
                null,
                null,
                null,
                "io.micronaut.jsonschema.custom.generated",
                21,
                Files.createTempDirectory("custom-schema-cache"),
                Files.createTempDirectory("custom-output"),
                [new SourceSpec("custom", "test-edge-cases", [
                    schemaName: "SANITIZED_COLLISION",
                    schema: '''
                    {
                      "type":"object",
                      "properties":{
                        "#bikes":{"type":"integer"},
                        "9bikes":{"type":"integer"}
                      },
                      "additionalProperties": false
                    }
                    '''
                ])],
                true,
                true
            )
        )

        then:
        result.generatedTypes() == 0
        def manifest = readJson(result.manifestPath())
        jsonAt(manifest, "warnings", 0, "code").getStringValue() == "NAME_COLLISION"
        jsonAt(manifest, "warnings", 0, "name").getStringValue() == "SANITIZED_COLLISION"
        jsonAt(manifest, "warnings", 0, "message").getStringValue().contains("#bikes")
        jsonAt(manifest, "warnings", 0, "message").getStringValue().contains("9bikes")
        jsonAt(manifest, "skipped", 0, "code").getStringValue() == "NAME_COLLISION"
        jsonAt(manifest, "generatedJavaFiles").size() == 0
    }

    void "pipeline delegates root oneOf to existing generator behavior"() {
        when:
        Path outputDir = Files.createTempDirectory("custom-output")
        def result = new JsonSchemaRecordsPipeline({ }).execute(
            new JsonSchemaRecordsGeneratorConfig(
                null,
                null,
                null,
                "io.micronaut.jsonschema.custom.generated",
                21,
                Files.createTempDirectory("custom-schema-cache"),
                outputDir,
                [new SourceSpec("custom", "test-edge-cases", [
                    schemaName: "POLY_ROOT",
                    schema: '{"oneOf":[{"title":"Alpha","type":"object","properties":{"a":{"type":"string"}}},{"title":"Beta","type":"object","properties":{"b":{"type":"string"}}}]}'
                ])],
                true,
                true
            )
        )

        then:
        result.generatedTypes() == 1
        def manifest = readJson(result.manifestPath())
        jsonAt(manifest, "warnings").size() == 0
        jsonAt(manifest, "skipped").size() == 0
        jsonAt(manifest, "generatedJavaFiles").size() == 3
        outputDir.resolve("io/micronaut/jsonschema/custom/generated/PolyRoot.java").toFile().text.contains("public interface PolyRoot")
        outputDir.resolve("io/micronaut/jsonschema/custom/generated/Alpha.java").toFile().text.contains("public class Alpha implements PolyRoot")
        outputDir.resolve("io/micronaut/jsonschema/custom/generated/Beta.java").toFile().text.contains("public class Beta implements PolyRoot")
    }

    void "pipeline delegates root anyOf to existing polymorphic generator behavior"() {
        when:
        Path outputDir = Files.createTempDirectory("custom-output")
        def result = new JsonSchemaRecordsPipeline({ }).execute(
            new JsonSchemaRecordsGeneratorConfig(
                null,
                null,
                null,
                "io.micronaut.jsonschema.custom.generated",
                21,
                Files.createTempDirectory("custom-schema-cache"),
                outputDir,
                [new SourceSpec("custom", "test-edge-cases", [
                    schemaName: "POLY_ROOT",
                    schema: '{"anyOf":[{"title":"Alpha","type":"object","properties":{"a":{"type":"string"}}},{"title":"Beta","type":"object","properties":{"b":{"type":"string"}}}]}'
                ])],
                true,
                true
            )
        )

        then:
        result.generatedTypes() == 1
        def manifest = readJson(result.manifestPath())
        jsonAt(manifest, "warnings").size() == 0
        jsonAt(manifest, "skipped").size() == 0
        jsonAt(manifest, "generatedJavaFiles").size() == 3
        outputDir.resolve("io/micronaut/jsonschema/custom/generated/PolyRoot.java").toFile().text.contains("public interface PolyRoot")
        outputDir.resolve("io/micronaut/jsonschema/custom/generated/Alpha.java").toFile().text.contains("public class Alpha implements PolyRoot")
        outputDir.resolve("io/micronaut/jsonschema/custom/generated/Beta.java").toFile().text.contains("public class Beta implements PolyRoot")
    }

    void "pipeline warns and continues for non default schema dialect"() {
        when:
        def result = new JsonSchemaRecordsPipeline({ }).execute(
            new JsonSchemaRecordsGeneratorConfig(
                null,
                null,
                null,
                "io.micronaut.jsonschema.custom.generated",
                21,
                Files.createTempDirectory("custom-schema-cache"),
                Files.createTempDirectory("custom-output"),
                [new SourceSpec("custom", "test-edge-cases", [
                    schemaName: "DRAFT_SEVEN",
                    schema: '{"$schema":"http://json-schema.org/draft-07/schema#","type":"object","additionalProperties":false}'
                ])],
                false,
                true
            )
        )

        then:
        result.generatedTypes() == 1
        def manifest = readJson(result.manifestPath())
        jsonAt(manifest, "warnings", 0, "code").getStringValue() == "SCHEMA_DIALECT"
        jsonAt(manifest, "warnings", 0, "step").getStringValue() == "GENERATION"
    }

    void "pipeline skips root incompatible allOf when skip on error is enabled"() {
        when:
        def result = new JsonSchemaRecordsPipeline({ }).execute(
            new JsonSchemaRecordsGeneratorConfig(
                null,
                null,
                null,
                "io.micronaut.jsonschema.custom.generated",
                21,
                Files.createTempDirectory("custom-schema-cache"),
                Files.createTempDirectory("custom-output"),
                [new SourceSpec("custom", "test-edge-cases", [
                    schemaName: "BROKEN_ALLOF",
                    schema: '{"allOf":[{"type":"object","properties":{"value":{"type":"string"}}},{"type":"object","properties":{"value":{"type":"integer"}}}]}'
                ])],
                true,
                true
            )
        )

        then:
        result.generatedTypes() == 0
        def manifest = readJson(result.manifestPath())
        jsonAt(manifest, "warnings", 0, "code").getStringValue() == "UNSUPPORTED_KEYWORD"
        jsonAt(manifest, "skipped", 0, "code").getStringValue() == "UNSUPPORTED_KEYWORD"
    }

    void "pipeline skips root incompatible allOf local ref when skip on error is enabled"() {
        when:
        def result = new JsonSchemaRecordsPipeline({ }).execute(
            new JsonSchemaRecordsGeneratorConfig(
                null,
                null,
                null,
                "io.micronaut.jsonschema.custom.generated",
                21,
                Files.createTempDirectory("custom-schema-cache"),
                Files.createTempDirectory("custom-output"),
                [new SourceSpec("custom", "test-edge-cases", [
                    schemaName: "BROKEN_ALLOF_REF",
                    schema: '{"allOf":[{"$ref":"#/$defs/Base"},{"type":"object","properties":{"value":{"type":"integer"}}}],"$defs":{"Base":{"type":"object","properties":{"value":{"type":"string"}}}}}'
                ])],
                true,
                true
            )
        )

        then:
        result.generatedTypes() == 0
        def manifest = readJson(result.manifestPath())
        jsonAt(manifest, "warnings", 0, "code").getStringValue() == "UNSUPPORTED_KEYWORD"
        jsonAt(manifest, "skipped", 0, "code").getStringValue() == "UNSUPPORTED_KEYWORD"
    }

    void "pipeline generates root local ref schema"() {
        given:
        Path outputDir = Files.createTempDirectory("custom-output")

        when:
        def result = new JsonSchemaRecordsPipeline({ }).execute(
            new JsonSchemaRecordsGeneratorConfig(
                null,
                null,
                null,
                "io.micronaut.jsonschema.custom.generated",
                21,
                Files.createTempDirectory("custom-schema-cache"),
                outputDir,
                [new SourceSpec("custom", "test-edge-cases", [
                    schemaName: "ROOT_REF",
                    schema: '{"$ref":"#/$defs/Base","$defs":{"Base":{"type":"object","properties":{"id":{"type":"integer"}},"required":["id"]}}}'
                ])],
                false,
                true
            )
        )

        then:
        result.generatedTypes() == 1
        Files.readString(outputDir.resolve("io/micronaut/jsonschema/custom/generated/RootRef.java")).contains("@NotNull int id")
    }

    void "pipeline skips root local ref outside defs when skip on error is enabled"() {
        when:
        def result = new JsonSchemaRecordsPipeline({ }).execute(
            new JsonSchemaRecordsGeneratorConfig(
                null,
                null,
                null,
                "io.micronaut.jsonschema.custom.generated",
                21,
                Files.createTempDirectory("custom-schema-cache"),
                Files.createTempDirectory("custom-output"),
                [new SourceSpec("custom", "test-edge-cases", [
                    schemaName: "UNSUPPORTED_LOCAL_REF",
                    schema: '{"$ref":"#/properties/value","properties":{"value":{"type":"string"}}}'
                ])],
                true,
                true
            )
        )

        then:
        result.generatedTypes() == 0
        def manifest = readJson(result.manifestPath())
        jsonAt(manifest, "warnings", 0, "code").getStringValue() == "UNSUPPORTED_KEYWORD"
        jsonAt(manifest, "skipped", 0, "code").getStringValue() == "UNSUPPORTED_KEYWORD"
    }

    void "pipeline sanitizes schema cache paths deterministically"() {
        given:
        String schemaName = "A---B" + ("C" * 140)
        Path schemaCacheDir = Files.createTempDirectory("custom-schema-cache")

        when:
        def result = new JsonSchemaRecordsPipeline({ }).execute(
            new JsonSchemaRecordsGeneratorConfig(
                null,
                null,
                null,
                "io.micronaut.jsonschema.custom.generated",
                21,
                schemaCacheDir,
                Files.createTempDirectory("custom-output"),
                [new SourceSpec("custom source", "test-edge-cases", [
                    schemaName: schemaName,
                    schema: '{"type":"object","additionalProperties":false}'
                ])],
                false,
                true
            )
        )

        then:
        result.generatedTypes() == 1
        def manifest = readJson(result.manifestPath())
        String schemaFile = jsonAt(manifest, "discovery", "schemas", 0, "schemaFile").getStringValue()
        schemaFile.startsWith("sources/custom_source/A_B")
        !schemaFile.contains("__")
        schemaFile.substring(schemaFile.lastIndexOf("/") + 1, schemaFile.length() - ".schema.json".length()).length() <= 128
        Files.exists(schemaCacheDir.resolve(schemaFile))
    }

    void "pipeline fails root external ref by default"() {
        when:
        new JsonSchemaRecordsPipeline({ }).execute(
            new JsonSchemaRecordsGeneratorConfig(
                null,
                null,
                null,
                "io.micronaut.jsonschema.custom.generated",
                21,
                Files.createTempDirectory("custom-schema-cache"),
                Files.createTempDirectory("custom-output"),
                [new SourceSpec("custom", "test-edge-cases", [
                    schemaName: "EXTERNAL_REF",
                    schema: '{"$ref":"https://example.com/schema.json"}'
                ])],
                false,
                true
            )
        )

        then:
        IOException exception = thrown()
        exception.message.contains("UNSUPPORTED_KEYWORD")
    }

    void "pipeline can run non jdbc discovery providers without connection settings"() {
        given:
        Path schemaCacheDir = Files.createTempDirectory("custom-schema-cache")
        Path outputDir = Files.createTempDirectory("custom-output")

        when:
        def result = new JsonSchemaRecordsPipeline({ }).execute(
            new JsonSchemaRecordsGeneratorConfig(
                null,
                null,
                null,
                "io.micronaut.jsonschema.custom.generated",
                21,
                schemaCacheDir,
                outputDir,
                [new SourceSpec("custom", "test", [apiToken: "secret", include: "SAFE"])],
                false,
                true
            )
        )

        then:
        result.manifestPath() != null
        result.generatedTypes() == 0

        and:
        def manifest = readJson(result.manifestPath())
        jsonAt(manifest, "sourceMetadata", 0, "sourceName").getStringValue() == "custom"
        jsonAt(manifest, "sourceMetadata", 0, "metadata", "provider").getStringValue() == "test"
        jsonAt(manifest, "sourceMetadata", 0, "metadata", "schemaCacheDir").getStringValue() == schemaCacheDir.toString()
        jsonAt(manifest, "sourceMetadata", 0, "metadata", "outputDir").getStringValue() == outputDir.toString()
        jsonAt(manifest, "sourceMetadata", 0, "metadata", "apiToken").getStringValue() == "<redacted>"
        jsonAt(manifest, "parameters", "sources", 0, "options", "apiToken").getStringValue() == "<redacted>"
        jsonAt(manifest, "parameters", "sources", 0, "options", "include").getStringValue() == "SAFE"
        jsonAt(manifest, "discovery", "schemas").size() == 0
    }

    void "pipeline uses language level below records to generate classes"() {
        given:
        Path schemaCacheDir = Files.createTempDirectory("custom-schema-cache")
        Path outputDir = Files.createTempDirectory("custom-output")

        when:
        def result = new JsonSchemaRecordsPipeline({ }).execute(
            new JsonSchemaRecordsGeneratorConfig(
                null,
                null,
                null,
                "io.micronaut.jsonschema.custom.generated",
                11,
                schemaCacheDir,
                outputDir,
                [new SourceSpec("custom", "test-edge-cases", [
                    schemaName: "LEGACY_TYPE",
                    schema: '{"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}'
                ])],
                false,
                true
            )
        )

        then:
        result.generatedTypes() == 1
        Path generated = outputDir.resolve("io/micronaut/jsonschema/custom/generated/LegacyType.java")
        Files.readString(generated).contains("public class LegacyType")

        and:
        def manifest = readJson(result.manifestPath())
        jsonAt(manifest, "parameters", "languageLevel").getIntValue() == 11
        jsonAt(manifest, "generatedJavaFiles", 0).getStringValue() == "io/micronaut/jsonschema/custom/generated/LegacyType.java"
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
