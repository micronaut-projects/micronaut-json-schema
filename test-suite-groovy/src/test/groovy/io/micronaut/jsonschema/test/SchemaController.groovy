package io.micronaut.jsonschema.test

import io.micronaut.core.io.scan.ClassPathResourceLoader
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.server.types.files.StreamedFile

/**
 * A controller for serving schemas.
 */
@Controller("/schemas") // <1>
class SchemaController {

    public static final String SCHEMAS_PATH = "classpath:META-INF/schemas/" // <2>

    private final ClassPathResourceLoader resourceLoader

    SchemaController(ClassPathResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader
    }

    @Get("/{schemaName}")
    StreamedFile getSchema(String schemaName) {
        if (!schemaName.endsWith(".schema.json")) {
            schemaName = schemaName + ".schema.json"
        }
        Optional<URL> url = resourceLoader.getResource(SCHEMAS_PATH + schemaName) // <3>
        return url.map(StreamedFile::new).orElse(null)
    }

}
