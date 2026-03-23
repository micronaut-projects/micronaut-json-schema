package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ConfigurableBeanContext;
import io.micronaut.context.ConfigurableApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.ConstructorInjectionPoint;
import io.micronaut.inject.FieldInjectionPoint;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DependencyInjectionValidatorTest {

    private final DependencyInjectionValidator validator = new DefaultDependencyInjectionValidator();

    @Test
    void fieldInjectionFailureIsReported() {
        Set<DependencyInjectionError> errors = validate("field", Map.of());
        assertFalse(errors.isEmpty());
        assertHasError(errors, "FixtureFieldInjectionBean", "FixtureMissingDependency", "field FixtureFieldInjectionBean.missingDependency");
    }

    @Test
    void valueInjectionFailureIsReported() {
        Set<DependencyInjectionError> errors = validate("value", Map.of());
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e ->
                e.rootBean().contains("FixtureValueInjectionBean")
                    && e.injectionPoint() != null
                    && e.injectionPoint().contains("field FixtureValueInjectionBean.value")
                    && e.message().contains("spec.missing.value")
            ), () -> "Expected missing @Value property error, got: " + errors);
    }

    @Test
    void propertyInjectionFailureIsReported() {
        Set<DependencyInjectionError> errors = validate("property", Map.of());
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e ->
                e.rootBean().contains("FixturePropertyInjectionBean")
                    && e.injectionPoint() != null
                    && e.injectionPoint().contains("field FixturePropertyInjectionBean.value")
                    && e.message().contains("Missing required property [spec.missing.property] for @Property injection")
            ), () -> "Expected missing @Property value error, got: " + errors);
    }

    @Test
    void configurationPropertiesConstructorArgumentsAreNotReportedAsDiFailures() {
        Set<DependencyInjectionError> errors = validate("configuration-properties-record", Map.of());
        assertFalse(errors.stream().anyMatch(e ->
                e.bean().contains("java.lang.Boolean")
                    && e.injectionPoint() != null
                    && (e.injectionPoint().contains("constructor FixtureConfigurationPropertiesRecord(")
                    || e.injectionPoint().contains("constructor ReactorConfiguration("))
            ), () -> "Did not expect missing Boolean DI failures for @ConfigurationProperties constructor args, got: " + errors);
    }

    @Test
    void validatorClassSuppressionSkipsMatchingRootsAndDependencies() {
        Set<DependencyInjectionError> errors = validate(
            "context",
            Map.of(),
            new DefaultDependencyInjectionValidator(List.of("io.micronaut.jsonschema.configuration.validator.FixtureContextBean"))
        );
        assertFalse(errors.isEmpty(), () -> "Expected other fixture DI errors to still be present, got: " + errors);
        assertFalse(errors.stream().anyMatch(e -> e.rootBean().contains("FixtureContextBean")),
            () -> "Expected FixtureContextBean root to be suppressed in validator, got: " + errors);
    }

    @Test
    void constructorInjectionFailureIsReported() {
        Set<DependencyInjectionError> errors = validate("constructor", Map.of());
        assertFalse(errors.isEmpty());
        assertHasError(errors, "FixtureConstructorInjectionBean", "FixtureMissingDependency", "constructor FixtureConstructorInjectionBean(missingDependency)");
    }

    @Test
    void methodInjectionFailureIsReported() {
        Set<DependencyInjectionError> errors = validate("method", Map.of());
        assertFalse(errors.isEmpty());
        assertHasError(errors, "FixtureMethodInjectionBean", "FixtureMissingDependency", "method FixtureMethodInjectionBean.inject(missingDependency)");
    }

    @Test
    void eachBeanFailureIsReported() {
        Set<DependencyInjectionError> errors = validate("eachbean", Map.of("spec.each.one.enabled", "true"));
        assertFalse(errors.isEmpty());
        assertHasError(errors, "FixtureEachBeanTarget", "FixtureMissingDependency", "constructor FixtureEachBeanTarget(missingDependency)");
    }

    @Test
    void scheduledFailureIsReported() {
        Set<DependencyInjectionError> errors = validate("scheduled", Map.of());
        assertFalse(errors.isEmpty());
        assertHasError(errors, "FixtureScheduledBean", "FixtureMissingDependency", "field FixtureScheduledBean.missingDependency");
    }

    @Test
    void controllerFailureIsReported() {
        Set<DependencyInjectionError> errors = validate("controller", Map.of());
        assertFalse(errors.isEmpty());
        assertHasError(errors, "FixtureControllerBean", "FixtureMissingDependency", "constructor FixtureControllerBean(missingDependency)");
    }

    @Test
    void contextFailureIsReported() {
        Set<DependencyInjectionError> errors = validate("context", Map.of());
        assertFalse(errors.isEmpty());
        assertHasError(errors, "FixtureContextBean", "FixtureMissingDependency", "constructor FixtureContextBean(missingDependency)");
    }

    @Test
    void validatorWorksWithConfiguredButNotRunningContext() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spec.name", "context");
        properties.put("spec.di.validator.test", "true");
        properties.put("micronaut.jsonschema.configuration.validator.endpoint.enabled", "false");
        properties.put("endpoints.enabled", "false");

        try (ConfigurableApplicationContext context = (ConfigurableApplicationContext) ApplicationContext.builder("di-validator-test")
            .properties(properties)
            .build()) {
            context.getEnvironment().start();
            context.configure();

            Set<DependencyInjectionError> errors = assertDoesNotThrow(() -> validator.validate(context));
            assertFalse(errors.isEmpty(), () -> "Expected metadata DI validation errors, got: " + errors);
            assertTrue(errors.stream().anyMatch(e -> e.rootBean().contains("FixtureContextBean")),
                () -> "Expected FixtureContextBean DI validation error, got: " + errors);
        }
    }

    @Test
    void discoveryFailureReturnsSyntheticError() {
        IllegalStateException cause = new IllegalStateException("Cannot resolve beans until the context is running");
        cause.setStackTrace(new StackTraceElement[] {
            new StackTraceElement(
                "com.example.runtime.CustomRuntimeCondition",
                "matches",
                "CustomRuntimeCondition.java",
                37
            )
        });
        ConfigurableBeanContext beanContext = beanContextProxy(new IllegalStateException("Dependency injection validation failed", cause));

        Set<DependencyInjectionError> errors = assertDoesNotThrow(() -> validator.validate(beanContext));

        assertEquals(1, errors.size(), () -> "Expected one synthetic discovery error, got: " + errors);
        DependencyInjectionError error = errors.iterator().next();
        assertEquals("<bean-definition-discovery>", error.rootBean());
        assertEquals("<bean-definition-discovery>", error.bean());
        assertTrue(error.message().contains("bean-definition discovery failed"), () -> "Unexpected error: " + error);
        assertTrue(error.message().contains("runtime-only condition was evaluated during metadata validation"), () -> "Unexpected error: " + error);
        assertTrue(error.message().contains("Cause chain:"), () -> "Unexpected error: " + error);
        assertTrue(error.message().contains("Triggering condition: com.example.runtime.CustomRuntimeCondition"), () -> "Unexpected error: " + error);
        assertTrue(error.message().contains("Cannot resolve beans until the context is running"), () -> "Unexpected error: " + error);
        assertTrue(error.failingPath().contains("<bean-definition-discovery>"), () -> "Unexpected error: " + error);
        assertTrue(error.failingPath().contains("com.example.runtime.CustomRuntimeCondition"), () -> "Unexpected error: " + error);
        assertEquals(null, error.injectionPoint());
        assertEquals(null, error.disabledReason());
        assertEquals(null, error.snippet());
        assertEquals(null, error.snippetLanguage());
    }

    @Test
    void defaultSuppressionSkipsMicronautSecurityDeclaringTypeDiscoveryFailure() {
        IllegalStateException cause = new IllegalStateException("Cannot resolve beans until the context is running");
        cause.setStackTrace(new StackTraceElement[] {
            new StackTraceElement(
                "io.micronaut.security.oauth2.proxy.WellKnownProxyFilter",
                "doFilter",
                "WellKnownProxyFilter.java",
                54
            )
        });
        ConfigurableBeanContext beanContext = beanContextProxy(new IllegalStateException("Dependency injection validation failed", cause));

        Set<DependencyInjectionError> errors = assertDoesNotThrow(() -> validator.validate(beanContext));
        assertTrue(errors.isEmpty(), () -> "Expected Micronaut Security declaring type discovery failure to be suppressed by default, got: " + errors);
    }

    @Test
    void nonSuppressedReferenceLoadFailureProducesSyntheticError() {
        ConfigurableBeanContext beanContext = beanContextProxy(List.of(
            beanDefinitionReferenceProxy(
                "com.example.runtime.BrokenBeanDefinition",
                Object.class,
                true,
                true,
                new IllegalStateException("Reference load failed")
            )
        ));

        Set<DependencyInjectionError> errors = assertDoesNotThrow(() -> validator.validate(beanContext));

        assertEquals(1, errors.size(), () -> "Expected one synthetic reference load error, got: " + errors);
        DependencyInjectionError error = errors.iterator().next();
        assertEquals("com.example.runtime.BrokenBeanDefinition", error.rootBean());
        assertEquals("com.example.runtime.BrokenBeanDefinition", error.bean());
        assertTrue(error.message().contains("bean-definition reference loading failed"), () -> "Unexpected error: " + error);
        assertTrue(error.failingPath().contains("failed: Reference load failed"), () -> "Unexpected error: " + error);
    }

    @Test
    void suppressedReferenceLoadFailureDoesNotHideNeighborReferenceFailure() {
        ConfigurableBeanContext beanContext = beanContextProxy(List.of(
            beanDefinitionReferenceProxy(
                "io.micronaut.security.oauth2.proxy.WellKnownProxyFilter",
                Object.class,
                true,
                true,
                new IllegalStateException("Security condition failed")
            ),
            beanDefinitionReferenceProxy(
                "com.example.runtime.UnsuppressedBeanDefinition",
                Object.class,
                true,
                true,
                new IllegalStateException("Neighbor load failed")
            )
        ));

        Set<DependencyInjectionError> errors = assertDoesNotThrow(() -> validator.validate(beanContext));

        assertEquals(1, errors.size(), () -> "Expected only non-suppressed failure to remain, got: " + errors);
        DependencyInjectionError error = errors.iterator().next();
        assertEquals("com.example.runtime.UnsuppressedBeanDefinition", error.bean());
        assertTrue(error.message().contains("Neighbor load failed"), () -> "Unexpected error: " + error);
        assertFalse(errors.stream().anyMatch(e -> e.bean().contains("WellKnownProxyFilter")),
            () -> "Expected suppressed Micronaut Security reference failure to be excluded, got: " + errors);
    }

    @Test
    void startupListenerFailureIsReported() {
        Set<DependencyInjectionError> errors = validate("startup-listener", Map.of());
        assertFalse(errors.isEmpty());
        assertHasError(errors, "FixtureStartupListenerBean", "FixtureMissingDependency", "constructor FixtureStartupListenerBean(missingDependency)");
    }

    @Test
    void reachableStrategyDoesNotValidateNonReachableApplicationBeans() {
        Set<DependencyInjectionError> errors = validate(
            "strategy-singleton-only",
            Map.of(),
            new DefaultDependencyInjectionValidator(List.of(), DependencyInjectionValidationStrategy.REACHABLE)
        );
        assertFalse(errors.stream().anyMatch(e -> e.rootBean().contains("FixtureStrategySingletonOnlyBean")),
            () -> "Did not expect non-reachable singleton bean to be validated with REACHABLE strategy, got: " + errors);
    }

    @Test
    void applicationBeansStrategyValidatesNonReachableApplicationBeans() {
        Set<DependencyInjectionError> errors = validate(
            "strategy-singleton-only",
            Map.of(),
            new DefaultDependencyInjectionValidator(List.of(), DependencyInjectionValidationStrategy.APPLICATION_BEANS)
        );
        assertHasError(
            errors,
            "FixtureStrategySingletonOnlyBean",
            "FixtureMissingDependency",
            "constructor FixtureStrategySingletonOnlyBean(missingDependency)"
        );
    }

    @Test
    void allBeansStrategyValidatesNonReachableApplicationBeans() {
        Set<DependencyInjectionError> errors = validate(
            "strategy-singleton-only",
            Map.of(),
            new DefaultDependencyInjectionValidator(List.of(), DependencyInjectionValidationStrategy.ALL_BEANS)
        );
        assertHasError(
            errors,
            "FixtureStrategySingletonOnlyBean",
            "FixtureMissingDependency",
            "constructor FixtureStrategySingletonOnlyBean(missingDependency)"
        );
    }

    @Test
    void eachBeanPrimaryInjectionReportsMissingConfigurationKey() {
        Set<DependencyInjectionError> errors = validate("eachbean-primary", Map.of());
        assertTrue(errors.stream().anyMatch(e ->
                e.rootBean().contains("FixtureEachBeanPrimaryConsumer")
                    && e.bean().contains("FixtureDataSource")
                    && e.message().contains("datasources.default")
            ), () -> "Expected missing datasources.default message, got: " + errors);
    }

    @Test
    void eachBeanPrimaryInjectionSucceedsWhenDefaultConfigured() {
        Set<DependencyInjectionError> errors = validate("eachbean-primary", Map.of("datasources.default.url", "jdbc:h2:mem:default"));
        assertFalse(errors.stream().anyMatch(e ->
            e.rootBean().contains("FixtureEachBeanPrimaryConsumer")
                && e.bean().contains("FixtureDataSource")
        ), () -> "Did not expect datasource injection error for configured default datasource, got: " + errors);
    }

    @Test
    void eachBeanNamedInjectionReportsMissingConfigurationKey() {
        Set<DependencyInjectionError> errors = validate("eachbean-named", Map.of());
        assertTrue(errors.stream().anyMatch(e ->
                e.rootBean().contains("FixtureEachBeanNamedConsumer")
                    && e.bean().contains("FixtureDataSource")
                    && e.message().contains("datasources.other")
            ), () -> "Expected missing datasources.other message, got: " + errors);
    }

    @Test
    void eachBeanNamedInjectionSucceedsWhenNamedConfigured() {
        Set<DependencyInjectionError> errors = validate("eachbean-named", Map.of("datasources.other.url", "jdbc:h2:mem:other"));
        assertFalse(errors.stream().anyMatch(e ->
            e.rootBean().contains("FixtureEachBeanNamedConsumer")
                && e.bean().contains("FixtureDataSource")
        ), () -> "Did not expect datasource injection error for configured named datasource, got: " + errors);
    }

    @Test
    void eachBeanPrimaryInjectionSucceedsWhenAnyNestedPropertyConfigured() {
        Set<DependencyInjectionError> errors = validate("eachbean-primary", Map.of("datasources.default.username", "sa"));
        assertFalse(errors.stream().anyMatch(e ->
            e.rootBean().contains("FixtureEachBeanPrimaryConsumer")
                && e.bean().contains("FixtureDataSource")
        ), () -> "Did not expect datasource injection error when datasources.default prefix is present, got: " + errors);
    }

    @Test
    void eachBeanNamedInjectionSucceedsWhenAnyNestedPropertyConfigured() {
        Set<DependencyInjectionError> errors = validate("eachbean-named", Map.of("datasources.other.username", "sa"));
        assertFalse(errors.stream().anyMatch(e ->
            e.rootBean().contains("FixtureEachBeanNamedConsumer")
                && e.bean().contains("FixtureDataSource")
        ), () -> "Did not expect datasource injection error when datasources.other prefix is present, got: " + errors);
    }

    @Test
    void eachBeanChainPrimaryInjectionReportsMissingConfigurationPrefix() {
        Set<DependencyInjectionError> errors = validate("eachbean-chain-primary", Map.of());
        assertTrue(errors.stream().anyMatch(e ->
                e.rootBean().contains("FixtureEachBeanChainPrimaryConsumer")
                    && e.bean().contains("FixtureDataSourceConfigurer")
                    && e.message().contains("datasources.default")
            ), () -> "Expected missing datasources.default message for each-bean chain, got: " + errors);
    }

    @Test
    void eachBeanChainPrimaryInjectionSucceedsWhenPrefixConfigured() {
        Set<DependencyInjectionError> errors = validate("eachbean-chain-primary", Map.of("datasources.default.username", "sa"));
        assertFalse(errors.stream().anyMatch(e ->
            e.rootBean().contains("FixtureEachBeanChainPrimaryConsumer")
                && e.bean().contains("FixtureDataSourceConfigurer")
        ), () -> "Did not expect each-bean chain injection error when datasources.default prefix is present, got: " + errors);
    }

    @Test
    void eachBeanChainNamedInjectionReportsMissingConfigurationPrefix() {
        Set<DependencyInjectionError> errors = validate("eachbean-chain-named", Map.of());
        assertTrue(errors.stream().anyMatch(e ->
                e.rootBean().contains("FixtureEachBeanChainNamedConsumer")
                    && e.bean().contains("FixtureDataSourceConfigurer")
                    && e.message().contains("datasources.other")
            ), () -> "Expected missing datasources.other message for each-bean chain, got: " + errors);
    }

    @Test
    void eachBeanChainNamedInjectionSucceedsWhenPrefixConfigured() {
        Set<DependencyInjectionError> errors = validate("eachbean-chain-named", Map.of("datasources.other.username", "sa"));
        assertFalse(errors.stream().anyMatch(e ->
            e.rootBean().contains("FixtureEachBeanChainNamedConsumer")
                && e.bean().contains("FixtureDataSourceConfigurer")
        ), () -> "Did not expect each-bean chain injection error when datasources.other prefix is present, got: " + errors);
    }

    @Test
    void circularDependencyFailureIsReported() {
        Set<DependencyInjectionError> errors = validate("circular", Map.of());
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e ->
            e.message().contains("Circular dependency detected")
                && e.injectionPoint() != null
                && e.injectionPoint().contains("constructor FixtureCircularBeanB(beanA)")
                && e.failingPath().stream().anyMatch(p -> p.contains("FixtureCircularBeanA"))
                && e.failingPath().stream().anyMatch(p -> p.contains("FixtureCircularBeanB"))
        ), () -> "Expected circular dependency error A->B->A, got: " + errors);
    }

    @Test
    void conditionalBeanRequirementDisablementIsReported() {
        Set<DependencyInjectionError> errors = validate("conditional-single", Map.of());
        assertTrue(errors.stream().anyMatch(e ->
                e.rootBean().contains("FixtureConditionalSingleConsumer")
                    && e.bean().contains("FixtureConditionalService")
                    && e.message().contains("Disabled candidate beans")
                    && e.message().contains("FixtureConditionalBeanRequiredCandidate")
                    && e.message().contains("FixtureConditionalDependency")
                    && e.disabledReason() != null
                    && e.disabledReason().contains("FixtureConditionalBeanRequiredCandidate")
            ), () -> "Expected disabled conditional candidate reason in message and disabledReason, got: " + errors);
    }

    @Test
    void multipleDisabledCandidatesAreReportedWithReasons() {
        Set<DependencyInjectionError> errors = validate("conditional-multi", Map.of());
        assertTrue(errors.stream().anyMatch(e ->
                e.rootBean().contains("FixtureConditionalMultiConsumer")
                    && e.bean().contains("FixtureConditionalService")
                    && e.message().contains("Disabled candidate beans")
                    && e.message().contains("FixtureConditionalPropertyRequiredCandidate")
                    && e.message().contains("FixtureConditionalMultiBeanRequiredCandidate")
                    && e.message().contains("spec.conditional.enabled")
                    && e.message().contains("FixtureConditionalDependency")
                    && e.disabledReason() != null
                    && e.disabledReason().contains("FixtureConditionalPropertyRequiredCandidate")
                    && e.disabledReason().contains("FixtureConditionalMultiBeanRequiredCandidate")
            ), () -> "Expected both disabled candidates and reasons to be reported, got: " + errors);
    }

    @Test
    void nestedDependencyFailurePathIsReported() {
        Set<DependencyInjectionError> errors = validate("nested", Map.of());
        assertTrue(errors.stream().anyMatch(e ->
                e.rootBean().contains("FixtureNestedDependencyA")
                    && e.bean().contains("FixtureMissingDependency")
                    && e.injectionPoint() != null
                    && e.injectionPoint().contains("constructor FixtureNestedDependencyB(missingDependency)")
                    && e.failingPath().stream().anyMatch(p -> p.contains("FixtureNestedDependencyA"))
                    && e.failingPath().stream().anyMatch(p -> p.contains("FixtureNestedDependencyB"))
                    && e.failingPath().stream().anyMatch(p -> p.contains("missing io.micronaut.jsonschema.configuration.validator.FixtureMissingDependency"))
            ), () -> "Expected nested path FixtureNestedDependencyA -> FixtureNestedDependencyB -> missing dependency, got: " + errors);
    }

    @Test
    void implicitInfrastructureBeansAreExcludedFromMissingDependencyErrors() {
        Set<DependencyInjectionError> errors = validate("implicit-infrastructure", Map.of());
        assertFalse(errors.stream().anyMatch(e ->
            e.rootBean().contains("FixtureImplicitInfrastructureBean")
                && (e.bean().contains("io.micronaut.context.env.Environment")
                || e.bean().contains("io.micronaut.core.value.PropertyResolver"))
        ), () -> "Did not expect Environment/PropertyResolver to be reported missing, got: " + errors);
    }

    @Test
    void nonUniqueCandidatesFailureIsReported() {
        Set<DependencyInjectionError> errors = validate("non-unique", Map.of());
        assertTrue(errors.stream().anyMatch(e ->
                e.rootBean().contains("FixtureMultipleCandidateConsumer")
                    && e.bean().contains("FixtureMultipleCandidateService")
                    && e.message().contains("Multiple possible bean candidates found")
                    && e.message().contains("FixtureMultipleCandidateOne")
                    && e.message().contains("FixtureMultipleCandidateTwo")
            ), () -> "Expected non-unique candidate error to be reported, got: " + errors);
    }

    @Test
    void primaryCandidateNarrowingResolvesSingleBean() {
        Set<DependencyInjectionError> errors = validate("non-unique-primary", Map.of());
        assertFalse(errors.stream().anyMatch(e ->
                e.rootBean().contains("FixtureMultipleCandidatePrimaryConsumer")
                    && e.bean().contains("FixtureMultipleCandidateService")
            ), () -> "Did not expect non-unique candidate error when a primary bean exists, got: " + errors);
    }

    @Test
    void orderedCandidateNarrowingResolvesSingleBean() {
        Set<DependencyInjectionError> errors = validate("non-unique-order", Map.of());
        assertFalse(errors.stream().anyMatch(e ->
                e.rootBean().contains("FixtureMultipleCandidateOrderConsumer")
                    && e.bean().contains("FixtureMultipleCandidateService")
            ), () -> "Did not expect non-unique candidate error when bean order differs, got: " + errors);
    }

    @Test
    void secondaryCandidatesAreIgnoredWhenPrimaryCandidatesExist() {
        Set<DependencyInjectionError> errors = validate("non-unique-secondary", Map.of());
        assertFalse(errors.stream().anyMatch(e ->
                e.rootBean().contains("FixtureMultipleCandidateSecondaryConsumer")
                    && e.bean().contains("FixtureMultipleCandidateService")
            ), () -> "Did not expect non-unique candidate error when only one non-secondary bean exists, got: " + errors);
    }

    @Test
    void factoryMethodMissingBeanDependencyIsReported() {
        Set<DependencyInjectionError> errors = validate("factory-method-missing-arg", Map.of());
        assertTrue(errors.stream().anyMatch(e ->
                e.rootBean().contains("FixtureFactoryMethodMissingArgConsumer")
                    && e.bean().contains("java.lang.String")
                    && e.message().contains("No bean of type [java.lang.String] exists")
                    && e.injectionPoint() != null
                    && e.injectionPoint().contains("method FixtureFactoryMethodMissingArgFactory.greeter(str)")
                    && e.snippet() != null
                    && e.snippet().contains("FixtureFactoryMethodMissingArgFactory.greeter")
            ), () -> "Expected missing String bean dependency from @Factory method argument to be reported, got: " + errors);
    }

    @Test
    void constructorInjectionPointDescriptionUsesFieldInjectionPointWhenPresent() {
        BeanDefinition<?> definition = beanDefinitionProxy(Optional.of(DeclaringFixture.class), FallbackGreeterFixture.class, "Fixture");
        ConstructorInjectionPoint<?> fieldInjectionPoint = fieldConstructorProxy("greeter");
        Argument<?> argument = Argument.of(String.class, "str");

        String description = invokeConstructorInjectionPointDescription(definition, fieldInjectionPoint, argument);

        assertEquals("field DeclaringFixture.greeter", description);
    }

    @Test
    void constructorInjectionPointDescriptionUsesFactoryMemberFallback() {
        BeanDefinition<?> definition = beanDefinitionProxy(
            Optional.of(FallbackFactoryFixture.class),
            FallbackGreeterFixture.class,
            "@j.i.Singleton i.m.m.e.g.FallbackGreeterFixture i.m.m.e.g.FallbackFactoryFixture.greeter"
        );
        ConstructorInjectionPoint<?> constructor = constructorProxy();
        Argument<?> argument = Argument.of(String.class, "str");

        String description = invokeConstructorInjectionPointDescription(definition, constructor, argument);

        assertEquals("method FallbackFactoryFixture.greeter(str)", description);
    }

    @Test
    void constructorInjectionPointDescriptionFallsBackToConstructorWhenFactoryMemberCannotBeResolved() {
        BeanDefinition<?> definition = beanDefinitionProxy(Optional.of(FallbackFactoryFixture.class), FallbackGreeterFixture.class, "FallbackGreeterFixture");
        ConstructorInjectionPoint<?> constructor = constructorProxy();
        Argument<?> argument = Argument.of(String.class, "str");

        String description = invokeConstructorInjectionPointDescription(definition, constructor, argument);

        assertEquals("constructor FallbackGreeterFixture(str)", description);
    }

    private static void assertHasError(Set<DependencyInjectionError> errors, String root, String bean, String injectionPoint) {
        assertTrue(errors.stream().anyMatch(e ->
                e.rootBean().contains(root)
                    && e.bean().contains(bean)
                    && e.injectionPoint() != null
                    && e.injectionPoint().contains(injectionPoint)
            ), () -> "Expected error [" + root + ", " + bean + ", " + injectionPoint + "], got: " + errors);
    }

    private Set<DependencyInjectionError> validate(String name, Map<String, Object> additionalProperties) {
        return validate(name, additionalProperties, validator);
    }

    private Set<DependencyInjectionError> validate(
        String name,
        Map<String, Object> additionalProperties,
        DependencyInjectionValidator validationDelegate
    ) {
        Map<String, Object> properties = new LinkedHashMap<>(additionalProperties.size() + 1);
        properties.put("spec.name", name);
        properties.put("spec.di.validator.test", "true");
        properties.put("micronaut.jsonschema.configuration.validator.endpoint.enabled", "false");
        properties.put("endpoints.enabled", "false");
        properties.putAll(additionalProperties);
        try (ConfigurableApplicationContext context = (ConfigurableApplicationContext) ApplicationContext.builder("di-validator-test").properties(properties).build()) {
            context.getEnvironment().start();
            context.configure();
            assertExpectedFixtureLoaded(context, name);
            return validationDelegate.validate(context);
        }
    }

    private static void assertExpectedFixtureLoaded(ConfigurableApplicationContext context, String name) {
        Class<?> expected = switch (name) {
            case "field" -> FixtureFieldInjectionBean.class;
            case "value" -> FixtureValueInjectionBean.class;
            case "property" -> FixturePropertyInjectionBean.class;
            case "configuration-properties-record" -> FixtureConfigurationPropertiesConsumer.class;
            case "constructor" -> FixtureConstructorInjectionBean.class;
            case "method" -> FixtureMethodInjectionBean.class;
            case "eachbean" -> FixtureEachBeanTarget.class;
            case "scheduled" -> FixtureScheduledBean.class;
            case "controller" -> FixtureControllerBean.class;
            case "context" -> FixtureContextBean.class;
            case "startup-listener" -> FixtureStartupListenerBean.class;
            case "strategy-singleton-only" -> FixtureStrategySingletonOnlyBean.class;
            case "circular" -> FixtureCircularBeanA.class;
            case "eachbean-primary" -> FixtureEachBeanPrimaryConsumer.class;
            case "eachbean-named" -> FixtureEachBeanNamedConsumer.class;
            case "eachbean-chain-primary" -> FixtureEachBeanChainPrimaryConsumer.class;
            case "eachbean-chain-named" -> FixtureEachBeanChainNamedConsumer.class;
            case "conditional-single" -> FixtureConditionalSingleConsumer.class;
            case "conditional-multi" -> FixtureConditionalMultiConsumer.class;
            case "nested" -> FixtureNestedDependencyA.class;
            case "implicit-infrastructure" -> FixtureImplicitInfrastructureBean.class;
            case "non-unique" -> FixtureMultipleCandidateConsumer.class;
            case "non-unique-primary" -> FixtureMultipleCandidatePrimaryConsumer.class;
            case "non-unique-order" -> FixtureMultipleCandidateOrderConsumer.class;
            case "non-unique-secondary" -> FixtureMultipleCandidateSecondaryConsumer.class;
            case "factory-method-missing-arg" -> FixtureFactoryMethodMissingArgConsumer.class;
            default -> null;
        };
        if (expected != null) {
            String expectedName = expected.getName();
            boolean foundInDefinitions = context.getAllBeanDefinitions()
                .stream()
                .anyMatch(definition -> definition.getBeanType().getName().equals(expectedName));
            boolean foundInDisabled = context.getDisabledBeans()
                .stream()
                .anyMatch(disabledBean -> disabledBean.getName().contains(expectedName));
            if ("eachbean-primary".equals(name) || "eachbean-named".equals(name)) {
                assertTrue(foundInDefinitions, () ->
                    "Expected fixture bean definition to be active (not disabled): " + expectedName
                        + "\nDefinitions: " + context.getAllBeanDefinitions().stream()
                        .map(definition -> definition.getBeanType().getName())
                        .filter(type -> type.contains("Fixture"))
                        .sorted()
                        .collect(Collectors.toList())
                        + "\nDisabled: " + context.getDisabledBeans().stream()
                        .map(disabledBean -> disabledBean.getName() + " => " + disabledBean.reasons())
                        .filter(type -> type.contains("Fixture"))
                        .sorted()
                        .collect(Collectors.toList())
                );
                return;
            }
            assertTrue(foundInDefinitions || foundInDisabled, () ->
                "Expected fixture metadata not loaded: " + expectedName
                    + "\nDefinitions: " + context.getAllBeanDefinitions().stream()
                    .map(definition -> definition.getBeanType().getName())
                    .filter(type -> type.contains("Fixture"))
                    .sorted()
                    .collect(Collectors.toList())
                    + "\nDisabled: " + context.getDisabledBeans().stream()
                    .map(disabledBean -> disabledBean.getName() + " => " + disabledBean.reasons())
                    .filter(type -> type.contains("Fixture"))
                    .sorted()
                    .collect(Collectors.toList())
            );
        }
    }

    private static String invokeConstructorInjectionPointDescription(
        BeanDefinition<?> definition,
        ConstructorInjectionPoint<?> constructor,
        Argument<?> argument
    ) {
        try {
            Method method = DefaultDependencyInjectionValidator.class.getDeclaredMethod(
                "constructorInjectionPointDescription",
                BeanDefinition.class,
                ConstructorInjectionPoint.class,
                Argument.class
            );
            method.setAccessible(true);
            return (String) method.invoke(null, definition, constructor, argument);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to invoke constructorInjectionPointDescription", e);
        }
    }

    private static BeanDefinition<?> beanDefinitionProxy(Optional<Class<?>> declaringType, Class<?> beanType, String beanDescription) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getDeclaringType" -> declaringType;
            case "getBeanType" -> beanType;
            case "getBeanDescription" -> beanDescription;
            default -> defaultValue(method.getReturnType());
        };
        return (BeanDefinition<?>) Proxy.newProxyInstance(
            DependencyInjectionValidatorTest.class.getClassLoader(),
            new Class<?>[] {BeanDefinition.class},
            handler
        );
    }

    private static ConfigurableBeanContext beanContextProxy(RuntimeException discoveryFailure) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "configure" -> null;
            case "getBeanDefinitionReferences" -> throw discoveryFailure;
            case "getDisabledBeans" -> List.of();
            default -> defaultValue(method.getReturnType());
        };
        return (ConfigurableBeanContext) Proxy.newProxyInstance(
            DependencyInjectionValidatorTest.class.getClassLoader(),
            new Class<?>[] {ConfigurableBeanContext.class},
            handler
        );
    }

    @SuppressWarnings("unchecked")
    private static ConfigurableBeanContext beanContextProxy(Collection<BeanDefinitionReference<Object>> references) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "configure" -> null;
            case "getBeanDefinitionReferences" -> references;
            case "getDisabledBeans" -> List.of();
            default -> defaultValue(method.getReturnType());
        };
        return (ConfigurableBeanContext) Proxy.newProxyInstance(
            DependencyInjectionValidatorTest.class.getClassLoader(),
            new Class<?>[] {ConfigurableBeanContext.class},
            handler
        );
    }

    @SuppressWarnings("unchecked")
    private static BeanDefinitionReference<Object> beanDefinitionReferenceProxy(
        String beanDefinitionName,
        Class<?> beanType,
        boolean present,
        boolean enabled,
        RuntimeException loadFailure
    ) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getBeanDefinitionName" -> beanDefinitionName;
            case "getBeanType" -> beanType;
            case "isPresent" -> present;
            case "isEnabled" -> enabled;
            case "load" -> throw loadFailure;
            default -> defaultValue(method.getReturnType());
        };
        return (BeanDefinitionReference<Object>) Proxy.newProxyInstance(
            DependencyInjectionValidatorTest.class.getClassLoader(),
            new Class<?>[] {BeanDefinitionReference.class},
            handler
        );
    }

    private static ConstructorInjectionPoint<?> constructorProxy() {
        InvocationHandler handler = (proxy, method, args) -> defaultValue(method.getReturnType());
        return (ConstructorInjectionPoint<?>) Proxy.newProxyInstance(
            DependencyInjectionValidatorTest.class.getClassLoader(),
            new Class<?>[] {ConstructorInjectionPoint.class},
            handler
        );
    }

    private static ConstructorInjectionPoint<?> fieldConstructorProxy(String fieldName) {
        BeanDefinition<?> declaringBean = beanDefinitionProxy(Optional.empty(), DeclaringFixture.class, "DeclaringFixture");
        InvocationHandler handler = (proxy, method, args) -> {
            if ("getName".equals(method.getName())) {
                return fieldName;
            }
            if ("getDeclaringBean".equals(method.getName())) {
                return declaringBean;
            }
            return defaultValue(method.getReturnType());
        };
        return (ConstructorInjectionPoint<?>) Proxy.newProxyInstance(
            DependencyInjectionValidatorTest.class.getClassLoader(),
            new Class<?>[] {ConstructorInjectionPoint.class, FieldInjectionPoint.class},
            handler
        );
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0f;
        }
        return 0d;
    }

    private static final class DeclaringFixture {
    }

    private static final class FallbackFactoryFixture {
    }

    private static final class FallbackGreeterFixture {
    }

}
