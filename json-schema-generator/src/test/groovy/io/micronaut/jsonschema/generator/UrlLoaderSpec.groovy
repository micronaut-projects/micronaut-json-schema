package io.micronaut.jsonschema.generator

import io.micronaut.jsonschema.generator.loaders.UrlLoader
import spock.lang.Specification

import static io.micronaut.jsonschema.generator.loaders.UrlLoader.isValidUrl

class UrlLoaderSpec extends Specification {

    void testUrlValidation() {
        when:
        var urlLocal = "http://localhost:8001/animal/schema"
        var urlExternal = "https://json.schemastore.org/github-workflow.json"
        UrlLoader.addAllowedUrlPattern("^http://localhost:.*")

        then:
        isValidUrl(urlExternal)
        isValidUrl(urlLocal)
    }

}
