import tempfile

from java.io import File
from java.nio.file import Files, Paths
from micronaut.jsonschema.generator import SourceGenerator
from micronaut.jsonschema.generator.loaders import UrlLoader
from micronaut.jsonschema.generator.utils import SourceGeneratorConfigBuilder
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


@MicronautTest(startApplication=False)
class RuntimeGenerationTest:

    @Test
    def test_generate_from_schema(self) -> None:
        schema_file = File("../test-suite-generator-java/src/test/resources/animal.schema.json")
        output_path = Paths.get(tempfile.mkdtemp())
        # tag::generate[]
        # create a generator with your chosen language
        java_generator = SourceGenerator("JAVA")

        # Optional: can configure accepted URL references
        UrlLoader.setAllowedUrlPatterns(["^https://.*/.*.schema.json$"])  # <1>
        UrlLoader.addAllowedUrlPattern("^http://localhost:.*")

        # Optional: set up input file name
        schema_file_name = "example.schema.json"
        SourceGenerator.setInputFileName(schema_file_name)  # <2>

        package_name = "com.example.temp"  # Example package name
        config = (SourceGeneratorConfigBuilder()
                  .withOutputFolder(output_path)  # Define the base output path
                  .withOutputPackageName(package_name)
                  .withJsonFile(schema_file)
                  .build())

        generated = java_generator.generate(config)  # <3>
        # end::generate[]

        assert generated is not None
        assert generated.getName() == "Animal.java"
        assert Files.exists(output_path.resolve("com/example/temp/Animal.java"))
        assert Files.exists(output_path.resolve("com/example/temp/Cat.java"))
