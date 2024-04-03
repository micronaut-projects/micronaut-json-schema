package io.micronaut.jsonschema.visitor

import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.jsonschema.visitor.model.Schema
import io.micronaut.jsonschema.visitor.serialization.JsonSchemaMapperFactory
import org.intellij.lang.annotations.Language
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class AbstractJsonSchemaSpec extends AbstractTypeElementSpec {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractJsonSchemaSpec.class)

    protected Schema buildJsonSchema(String className, String schemaName, @Language("java") String cls, String... parameters) {
        ClassLoader classLoader = buildClassLoader(className, cls.formatted(parameters))
        String json = readResource(classLoader, "META-INF/schemas/" + schemaName + ".schema.json")
        LOGGER.info("Read JSON schema: ")
        LOGGER.info(json)
        ObjectMapper objectMapper = JsonSchemaMapperFactory.createMapper()
        Schema swagger = objectMapper.readValue(json, Schema)
        return swagger
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
