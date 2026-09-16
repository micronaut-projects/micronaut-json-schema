# Python Docs Disabled Test Inventory

This file tracks the Python docs examples of `test-suite-python` and `test-suite-generator-python` that are present
but disabled because the direct port currently fails with the Python compiler shipped with Micronaut core, and the
Python compiler gaps the ports had to work around. Use it as the bug-fixing task list.

## Reconciliation

- Last generated command: `rg -n "@Disabled\(" test-suite-python/src/test/python test-suite-generator-python/src/test/python`.
- Last full-suite command: `./gradlew :test-suite-python:test :test-suite-generator-python:test -Ppython-ci`.

## Migration Rules

- Do not define local copies of Micronaut annotation helpers or custom annotation shims in docs snippets.
  Standard Micronaut annotations are generated from imports.
- Do not add Java-style getters or setters to Python docs models. Prefer `@dataclass` models with idiomatic
  Python attributes; attribute docstrings become the JSON schema property descriptions like Javadoc does.
- Prefer `@MicronautTest` with injected beans over `ApplicationContext.run()`. Every Python test class needs
  `@MicronautTest` (`startApplication=False` when no server is needed): a plain class has no GraalPy context.
- Prefer normal imports over `java.type(...)` for Java classes (`from tools.jackson.databind.json import JsonMapper`,
  `from micronaut.jsonschema.generator.ref import Achievement`) and pass the imported Python dataclass where Java expects
  a `Class<T>` (`json_schema_validator.validate(Llama("John", 12), Llama)`). `java.type` is used only where the import
  form fails; every remaining call carries a `# TODO(python)` comment and is listed under "java.type usages" below.

## Active `@Disabled` Tests

None.

## Commented Unsupported Snippet Ports

None.

## java.type usages

| File | Classes | Reason |
| --- | --- | --- |
| `test-suite-generator-python/.../generator/animals/AnimalTest.py` | `Animal`, `Cat`, `Dog`, `Fish`, `Human` | Importing `io.micronaut.jsonschema.generator.animals` (any form: `from micronaut....`, `from io.micronaut....`) writes the package shim to `micronaut/jsonschema/generator/animals/__init__.py`, the source package `AnimalTest.py` must live in for the `snippet::` macro; the compilation fails with "Failed to write Python code to [.../animals/__init__.py]: Output stream or writer has already been opened" (same gap as the runtime generation example below). |
| `test-suite-generator-python/.../serialization/SerializationTest.py` | `Animal`, `Cat`, `Dog` | Same package shim collision (the whole test source set is one compilation, so the collision also hits imports from other modules). `Achievement` (`io.micronaut.jsonschema.generator.ref`) and Jackson's `JsonMapper` import fine. |

## the Python compiler Gaps Worked Around

| Gap | Workaround |
| --- | --- |
| `PythonVisitorContext.getOptions()` is empty: neither `-A` annotation processor options nor `micronaut.*` system properties reach type element visitors, so `micronaut.jsonschema.baseUri` cannot be configured for the Python compilation. | `test-suite-python` keeps the processor's default `http://localhost:8080/schemas` base URI; `application.properties` and `expected-llama.schema.json` match it instead of `https://example.com/schemas` used by the other suites. |
| `org.junit.jupiter.params` (`@ParameterizedTest`, `@ValueSource`) is not importable from Python (`ModuleNotFoundError`). | `SchemaGenerationTest.test_build_json_schema` loops over the schema names in one `@Test`. |
| A class docstring is copied verbatim (indentation and line breaks included) into the schema description, whereas attribute docstrings are parsed and trimmed. | `Llama` uses a one-line class docstring with the `<4>` callout in a trailing comment. |
| Importing a Java package that is also a Python source package (`from micronaut.jsonschema.generator import SourceGenerator` next to `micronaut/jsonschema/generator/*.py`) fails with "Failed to write Python code to [.../__init__.py]: Output stream or writer has already been opened". | The runtime generation example lives in the `io.micronaut.jsonschema.generator.usage` package in all four languages; the generated `io.micronaut.jsonschema.generator.animals` beans, whose package is the source package of `AnimalTest`, are loaded with `java.type(...)` (see "java.type usages"). |
