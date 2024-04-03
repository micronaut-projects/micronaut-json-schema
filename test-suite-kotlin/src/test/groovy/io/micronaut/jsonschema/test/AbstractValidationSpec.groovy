package io.micronaut.jsonschema.test

import com.networknt.schema.*
import io.micronaut.serde.ObjectMapper
import jakarta.inject.Inject
import spock.lang.Specification

abstract class AbstractValidationSpec extends Specification {

    @Inject
    ObjectMapper objectMapper

    public static final String URL_PREFIX = "https://example.com/schemas/"
    public static final String CLASSPATH_PREFIX = "classpath:META-INF/schemas/"

    protected Set<ValidationMessage> validateJsonWithSchema(Object value, String schemaName) {
        String input = objectMapper.writeValueAsString(value)
        println input
        return validateJsonWithSchema(input, schemaName)
    }

    protected Set<ValidationMessage> validateJsonWithSchema(String input, String schemaName) {
        var jsonSchemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012, builder ->
                // This creates a mapping from $id which starts with https://example.com/ to the retrieval URI classpath:schema/
                builder.schemaMappers(schemaMappers ->
                        schemaMappers.mapPrefix(URL_PREFIX, CLASSPATH_PREFIX)
                            .mappings(v -> v.endsWith(".schema.json") ? v : v + ".schema.json")
                )
        )

        var config = new SchemaValidatorsConfig()
        // By default JSON Path is used for reporting the instance location and evaluation path
        config.setPathType(PathType.JSON_POINTER)

        var schema = jsonSchemaFactory.getSchema(SchemaLocation.of(URL_PREFIX + schemaName + ".schema.json"), config)

        ExecutionContextCustomizer contextCustomizer = new ExecutionContextCustomizer() {
            @Override
            void customize(ExecutionContext executionContext, ValidationContext validationContext) {
                // By default since Draft 2019-09 the format keyword only generates annotations and not assertions
                validationContext.getConfig().setFormatAssertionsEnabled(true)
            }
        }
        return schema.validate(input, InputFormat.JSON, contextCustomizer)
    }

}
