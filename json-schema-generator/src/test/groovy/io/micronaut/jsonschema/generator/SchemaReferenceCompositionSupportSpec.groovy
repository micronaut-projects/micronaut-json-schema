package io.micronaut.jsonschema.generator

import io.micronaut.jsonschema.model.Schema
import io.micronaut.jsonschema.serialization.JsonSchemaMapperFactory
import spock.lang.Specification
import tools.jackson.databind.ObjectMapper

class SchemaReferenceCompositionSupportSpec extends Specification {

    private final ObjectMapper mapper = JsonSchemaMapperFactory.createMapper()

    void "classifies supported local and external references"() {
        expect:
        SchemaReferenceCompositionSupport.isSupportedLocalRef("#")
        SchemaReferenceCompositionSupport.isSupportedLocalRef("#/\$defs/Base")
        SchemaReferenceCompositionSupport.isSupportedLocalRef("#/definitions/Base")
        !SchemaReferenceCompositionSupport.isSupportedLocalRef("#/properties/name")
        !SchemaReferenceCompositionSupport.isSupportedLocalRef("https://example.com/schema.json#/\$defs/Base")

        and:
        !SchemaReferenceCompositionSupport.isExternalRef("#/\$defs/Base")
        SchemaReferenceCompositionSupport.isExternalRef("https://example.com/schema.json#/\$defs/Base")
    }

    void "prepares root local definition reference without losing local title"() {
        given:
        Schema schema = readSchema('''
        {
          "title": "Alias",
          "$ref": "#/$defs/Base",
          "$defs": {
            "Base": {
              "title": "Base",
              "type": "object",
              "properties": {
                "id": { "type": "integer" }
              },
              "required": ["id"]
            }
          }
        }
        ''')

        when:
        SchemaReferenceCompositionSupport.prepareLocalCompositionReferences(schema)

        then:
        !schema.has$ref()
        schema.title == "Alias"
        schema.properties.keySet() == ["id"] as Set
        schema.required == ["id"]
    }

    void "flattens allOf branches that reference defs"() {
        given:
        Schema schema = readSchema('''
        {
          "title": "Composed",
          "allOf": [
            { "$ref": "#/$defs/Base" },
            {
              "type": "object",
              "properties": {
                "name": { "type": "string" }
              }
            }
          ],
          "$defs": {
            "Base": {
              "type": "object",
              "properties": {
                "id": { "type": "integer" }
              }
            }
          }
        }
        ''')

        when:
        SchemaReferenceCompositionSupport.prepareLocalCompositionReferences(schema)

        then:
        !SchemaReferenceCompositionSupport.hasUnsupportedAllOf(schema)
        schema.properties.keySet().containsAll(["id", "name"])
    }

    void "flattens allOf branches that reference legacy definitions"() {
        given:
        Schema schema = readSchema('''
        {
          "title": "Composed",
          "allOf": [
            { "$ref": "#/definitions/Base" },
            {
              "type": "object",
              "properties": {
                "name": { "type": "string" }
              }
            }
          ],
          "definitions": {
            "Base": {
              "type": "object",
              "properties": {
                "id": { "type": "integer" }
              }
            }
          }
        }
        ''')

        when:
        SchemaReferenceCompositionSupport.prepareLocalCompositionReferences(schema)

        then:
        !SchemaReferenceCompositionSupport.hasUnsupportedAllOf(schema)
        schema.properties.keySet().containsAll(["id", "name"])
    }

    void "merges compatible duplicate property constraints during composition preparation"() {
        given:
        Schema schema = readSchema('''
        {
          "allOf": [
            {
              "type": "object",
              "properties": {
                "name": { "type": "string", "minLength": 2 }
              }
            },
            {
              "type": "object",
              "properties": {
                "name": { "type": "string", "maxLength": 20 }
              }
            }
          ]
        }
        ''')

        when:
        SchemaReferenceCompositionSupport.prepareLocalCompositionReferences(schema)

        then:
        !SchemaReferenceCompositionSupport.hasUnsupportedAllOf(schema)
        schema.properties.name.minLength == 2
        schema.properties.name.maxLength == 20
    }

    void "detects incompatible duplicate allOf properties"() {
        given:
        Schema schema = readSchema('''
        {
          "allOf": [
            {
              "type": "object",
              "properties": {
                "value": { "type": "string" }
              }
            },
            {
              "type": "object",
              "properties": {
                "value": { "type": "integer" }
              }
            }
          ]
        }
        ''')

        when:
        SchemaReferenceCompositionSupport.prepareLocalCompositionReferences(schema)

        then:
        SchemaReferenceCompositionSupport.hasUnsupportedAllOf(schema)
    }

    void "detects contradictory additionalProperties in allOf branches"() {
        given:
        Schema schema = readSchema('''
        {
          "allOf": [
            {
              "type": "object",
              "additionalProperties": false
            },
            {
              "type": "object",
              "additionalProperties": true
            }
          ]
        }
        ''')

        when:
        SchemaReferenceCompositionSupport.prepareLocalCompositionReferences(schema)

        then:
        SchemaReferenceCompositionSupport.hasUnsupportedAllOf(schema)
    }

    void "detects unsupported allOf branch types and union-style branches"() {
        expect:
        SchemaReferenceCompositionSupport.hasUnsupportedAllOf(readSchema(schemaJson))

        where:
        schemaJson << [
            '{"allOf":[{"type":"string"},{"type":"object"}]}',
            '{"allOf":[{"type":["object","string"]}]}',
            '{"allOf":[{"oneOf":[{"type":"string"},{"type":"integer"}]}]}',
            '{"allOf":[{"anyOf":[{"type":"string"},{"type":"integer"}]}]}'
        ]
    }

    void "leaves unsupported local ref outside definitions detectable"() {
        given:
        Schema schema = readSchema('''
        {
          "allOf": [
            { "$ref": "#/properties/template" }
          ],
          "properties": {
            "template": { "type": "string" }
          }
        }
        ''')

        when:
        SchemaReferenceCompositionSupport.prepareLocalCompositionReferences(schema)

        then:
        schema.allOf[0].$ref == "#/properties/template"
        SchemaReferenceCompositionSupport.hasUnsupportedAllOf(schema)
    }

    void "does not loop on recursive local definition references"() {
        given:
        Schema schema = readSchema('''
        {
          "$ref": "#/$defs/Node",
          "$defs": {
            "Node": {
              "type": "object",
              "properties": {
                "child": { "$ref": "#/$defs/Node" }
              }
            }
          }
        }
        ''')

        when:
        SchemaReferenceCompositionSupport.prepareLocalCompositionReferences(schema)

        then:
        !schema.has$ref()
        schema.properties.child.get$ref() == "#/\$defs/Node"
    }

    private Schema readSchema(String json) {
        mapper.readValue(json, Schema.class)
    }
}
