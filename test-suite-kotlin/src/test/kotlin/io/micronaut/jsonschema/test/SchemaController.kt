package io.micronaut.jsonschema.test

import io.micronaut.core.io.scan.ClassPathResourceLoader
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.server.types.files.StreamedFile

const val SCHEMAS_PATH: String = "classpath:META-INF/schemas/" // <2>

/**
 * A controller for serving schemas.
 */
@Controller("/schemas") // <1>
class SchemaController(
        private var resourceLoader: ClassPathResourceLoader,
) {

    @Get("/{schemaName}")
    fun getSchema (schemaName: String): StreamedFile {
        var schemaFile = schemaName
        if (!schemaFile.endsWith(".schema.json")) {
            schemaFile += ".schema.json"
        }
        val url = this.resourceLoader.getResource(SCHEMAS_PATH + schemaFile) // <3>
        return url.map { StreamedFile(it) }.orElse(null)
    }

}
