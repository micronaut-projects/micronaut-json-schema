package io.micronaut.jsonschema.visitor

import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.jsonschema.visitor.context.JsonSchemaContext
import io.micronaut.jsonschema.visitor.model.Schema
import io.micronaut.jsonschema.visitor.serialization.JsonSchemaMapperFactory
import org.intellij.lang.annotations.Language
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class AbstractJsonSchemaSpec extends AbstractTypeElementSpec {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractJsonSchemaSpec.class)

    protected Schema buildJsonSchema(String className, String schemaName, @Language("java") String cls, String... parameters) {
        return buildJsonSchema(className, schemaName, cls.formatted(parameters), null)
    }

    protected Schema buildJsonSchema(String className, String schemaName, @Language("java") String cls, Map<String, String> contextOptions) {
        for (String parameter: JsonSchemaContext.getParameters()) {
            System.clearProperty(parameter)
        }
        if (contextOptions != null) {
            contextOptions.each{ System.setProperty(JsonSchemaContext.PARAMETER_PREFIX +  it.key, it.value) }
        }
        ClassLoader classLoader = buildClassLoader(className, cls)
        String json = readResource(classLoader, "META-INF/schemas/" + schemaName + ".schema.json")
        LOGGER.info("Read JSON schema: ")
        LOGGER.info(json)
        ObjectMapper objectMapper = JsonSchemaMapperFactory.createMapper()
        Schema jsonSchema = objectMapper.readValue(json, Schema)
        return jsonSchema
    }

    protected String readResource(ClassLoader classLoader, String resourcePath) {
        Iterator<URL> specs = classLoader.getResources(resourcePath).asIterator()
        if (!specs.hasNext()) {
            throw new IllegalArgumentException("Could not find resource " + resourcePath)
        }
        URL spec = specs.next()
        BufferedReader reader = new BufferedReader(new InputStreamReader(spec.openStream()))
        StringBuilder result = new StringBuilder()
        String inputLine
        while ((inputLine = reader.readLine()) != null) {
            result.append(inputLine).append("\n")
        }
        reader.close()
        return result.toString()
    }

}
