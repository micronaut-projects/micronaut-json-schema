package io.micronaut.jsonschema.generator

import com.github.javaparser.JavaParser
import com.github.javaparser.ParseResult
import com.github.javaparser.ParserConfiguration
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.RecordDeclaration
import spock.lang.Specification

class AbstractGeneratorSpec extends Specification {

    RecordDeclaration generateRecord(String className, String schemaFileName) {
        RecordGenerator generator = new RecordGenerator()

        File dir = File.createTempDir()
        File generated = dir.toPath().resolve(className +".java").toFile()
        generator.generate(new File("src/test/resources/" + schemaFileName), Optional.of(generated))

        ParserConfiguration configuration = new ParserConfiguration()
        configuration.languageLevel = ParserConfiguration.LanguageLevel.JAVA_17
        ParseResult<CompilationUnit> parsed = new JavaParser(configuration).parse(generated.text)
        return parsed.getResult().get().getRecordByName(className).get()
    }

    String generateRecordAndGetContent(String className, String schemaFileName) {
        return generateRecord(className, schemaFileName).getTokenRange().get().toString()
    }

}
