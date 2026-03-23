# Issue #322 investigation notes

Context: <https://github.com/micronaut-projects/micronaut-json-schema/issues/322>

## What was fixed here

This branch fixes the missing-diagnostics problem in `json-schema-configuration-validator`.

Before this change, the validator could fail during configuration loading or dependency-injection discovery and exit without writing `configuration-errors.json` / `configuration-errors.html`.

After this change:

- fatal configuration-loading failures emit synthetic configuration diagnostics and still write reports
- dependency-injection bean-definition discovery failures emit a synthetic `DependencyInjectionError`
- the synthetic DI error now names the triggering runtime condition when it can be extracted from the throwable stack trace

## Reproducer used

Application:

`/Users/graemerocher/dev/micronaut/apps/test-aot7963871478975428797`

To force the sample app to use this local patched validator, the app was wired as a composite build via:

```groovy
includeBuild('/Users/graemerocher/dev/micronaut/json-schema.issue-322')
```

in `apps/test-aot7963871478975428797/settings.gradle`.

## Verified current behavior

Running:

```bash
./gradlew assemble
```

in the reproducer app now writes:

- `build/reports/micronaut/config-validation/production/configuration-errors.json`
- `build/reports/micronaut/config-validation/production/configuration-errors.html`
- `build/reports/micronaut/config-validation/production/result.properties`

The current JSON report contains:

```json
{
  "dependencyInjectionErrors": [
    {
      "bean": "<bean-definition-discovery>",
      "details": "Dependency injection bean-definition discovery failed before the context was running. This usually indicates a runtime-only condition was evaluated during metadata validation, not necessarily an application misconfiguration. Failure: Cannot resolve beans until the context is running. Triggering condition: io.micronaut.security.oauth2.proxy.WellKnownProxyFilterCondition",
      "failingPath": [
        "<bean-definition-discovery>",
        "io.micronaut.security.oauth2.proxy.WellKnownProxyFilterCondition",
        "failed: Cannot resolve beans until the context is running"
      ]
    }
  ]
}
```

## Root cause found

The remaining failure is not a silent validator crash anymore. It is a real limitation of metadata-only DI validation when bean-definition discovery triggers runtime-only conditions.

Observed validator-side path:

```text
DefaultDependencyInjectionValidator.validate(...)
  -> ensureConfigured(beanContext)
  -> beanContext.getAllBeanDefinitions()
  -> Micronaut internal bean discovery / condition evaluation
  -> io.micronaut.security.oauth2.proxy.WellKnownProxyFilterCondition.matches(...)
  -> condition calls context.getBeansOfType(OauthClientConfiguration.class)
  -> "Cannot resolve beans until the context is running"
```

## Why this branch stops here

We considered trying to bypass or special-case `WellKnownProxyFilterCondition` inside the validator.

That was rejected for three reasons:

1. `getAllBeanDefinitions()` fails before bean metadata is available to the validator, so there is nothing reliable to filter locally.
2. `WellKnownProxyFilterCondition` intentionally depends on runtime bean state by querying `OauthClientConfiguration` beans and inspecting issuer/proxy flags.
3. A validator-side special case for one Micronaut Security condition would set a bad precedent and could hide legitimate future failures.

## Recommended upstream follow-up

The next fix, if desired, should happen outside this validator module.

Most likely candidates:

- Micronaut Security: make `WellKnownProxyFilterCondition` safe when evaluated in non-running metadata/discovery scenarios, or
- Micronaut framework / validation model: provide a supported way to enumerate bean definitions for metadata validation without evaluating runtime-only conditions that require instantiated beans.

## Useful evidence collected

- reproducer app report now clearly identifies `io.micronaut.security.oauth2.proxy.WellKnownProxyFilterCondition`
- validator regression covers synthetic discovery failure reporting
- full validator module test suite passes after the diagnostics changes

## Commands used during verification

```bash
./gradlew :micronaut-json-schema-configuration-validator:test
./gradlew :micronaut-json-schema-configuration-validator:test --tests 'io.micronaut.jsonschema.configuration.validator.DependencyInjectionValidatorTest.discoveryFailureReturnsSyntheticError'
./gradlew assemble
```
