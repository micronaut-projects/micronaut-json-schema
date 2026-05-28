/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.ConfigurableBeanContext;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.env.Environment;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.context.DisabledBean;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.exceptions.NonUniqueBeanException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.EntryPoint;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.TypeInformation;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.ConstructorInjectionPoint;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.FieldInjectionPoint;
import io.micronaut.inject.MethodInjectionPoint;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Default metadata-only implementation of {@link DependencyInjectionValidator}.
 * <p>
 * This validator inspects bean definitions and their constructor/field/method injection points
 * to detect unresolved dependencies, missing required {@code @Value} properties, disabled-bean
 * causes, and circular dependency paths without starting the context or instantiating beans.
 */
@Singleton
public final class DefaultDependencyInjectionValidator implements DependencyInjectionValidator {
    private static final String DISCOVERY_SENTINEL = "<bean-definition-discovery>";
    private static final Logger LOG = LoggerFactory.getLogger(DefaultDependencyInjectionValidator.class);
    private static final String METHOD_INJECTION_POINT_PREFIX = "method ";
    private static final List<String> DEFAULT_SUPPRESSED_CLASS_PATTERNS = List.of(
        "io.micronaut.security.session.SessionLoginHandler*",
        "io.micronaut.security.session.SessionLogoutHandler*",
        "io.micronaut.security.oauth2.proxy.WellKnownProxySecurityRule*",
        "io.micronaut.security.oauth2.proxy.WellKnownProxyFilter*",
        "io.micronaut.security.oauth2.client.DefaultOauthClient*",
        "io.micronaut.security.oauth2.client.OpenIdClientFactory*",
        "io.micronaut.security.oauth2.client.clientcredentials.propagation.ClientCredentialsHeaderTokenPropagator*",
        "io.micronaut.security.oauth2.client.clientcredentials.ClientCredentialsFactory*",
        "io.micronaut.security.oauth2.endpoint.authorization.pkce.S256PkceGenerator*",
        "io.micronaut.security.oauth2.endpoint.token.request.password.PasswordGrantFactory*",
        "io.micronaut.security.token.jwt.signature.jwks.CacheableJwkSetFetcher*",
        "io.micronaut.security.ldap.LdapAuthenticationProviderFactory*",
        "io.micronaut.security.authentication.Authenticator*",
        "io.micronaut.security.token.cookie.TokenCookieConfigurationProperties*",
        "io.micronaut.security.token.cookie.TokenCookieClearerLogoutHandler*",
        "io.micronaut.security.token.cookie.CookieTokenReader*",
        "io.micronaut.security.token.cookie.RefreshTokenCookieConfigurationProperties*"
    );

    private static final String STARTUP_EVENT = "io.micronaut.runtime.event.StartupEvent";
    private static final String SERVER_STARTUP_EVENT = "io.micronaut.runtime.server.event.ServerStartupEvent";
    private final List<ClassSuppressionMatcher> suppressedClassMatchers;
    private final DependencyInjectionValidationStrategy validationStrategy;

    @Inject
    public DefaultDependencyInjectionValidator(ConfigurationValidatorConfiguration configuration) {
        this(List.of(), configuration.getDependencyInjectionValidationStrategy());
    }

    public DefaultDependencyInjectionValidator() {
        this(List.of(), DependencyInjectionValidationStrategy.REACHABLE);
    }

    public DefaultDependencyInjectionValidator(@Nullable List<String> suppressedClassPatterns) {
        this(suppressedClassPatterns, DependencyInjectionValidationStrategy.REACHABLE);
    }

    public DefaultDependencyInjectionValidator(DependencyInjectionValidationStrategy validationStrategy) {
        this(List.of(), validationStrategy);
    }

    public DefaultDependencyInjectionValidator(
        @Nullable List<String> suppressedClassPatterns,
        @Nullable DependencyInjectionValidationStrategy validationStrategy
    ) {
        this.suppressedClassMatchers = ClassSuppressionMatcher.compileAll(
            effectiveSuppressedClassPatterns(suppressedClassPatterns)
        );
        this.validationStrategy = validationStrategy != null
            ? validationStrategy
            : DependencyInjectionValidationStrategy.REACHABLE;
    }

    public static List<String> defaultSuppressedClassPatterns() {
        return DEFAULT_SUPPRESSED_CLASS_PATTERNS;
    }

    /**
     * Validates dependency wiring for reachable roots discovered in the given context.
     *
     * @param beanContext The bean context to inspect
     * @return The detected dependency-injection validation errors
     */
    @Override
    public Set<DependencyInjectionError> validate(ConfigurableBeanContext beanContext) {
        Objects.requireNonNull(beanContext, "beanContext");
        ensureConfigured(beanContext);

        BeanDiscoveryResult discoveryResult = discoverBeanDefinitions(beanContext);
        Collection<BeanDefinition<Object>> definitions = discoveryResult.definitions();
        Set<DependencyInjectionError> discoveryErrors = discoveryResult.discoveryErrors();
        if (definitions.isEmpty()) {
            return discoveryErrors;
        }

        List<DisabledCandidate> disabledCandidates = disabledCandidates(resolveDisabledBeans(beanContext));
        List<BeanDefinition<Object>> roots = validationRoots(definitions);

        Set<DependencyInjectionError> errors = new LinkedHashSet<>();
        errors.addAll(discoveryErrors);
        Set<String> dedupe = new LinkedHashSet<>();
        TraversalState traversalState = new TraversalState(beanContext, definitions, disabledCandidates, errors, dedupe);
        for (BeanDefinition<Object> root : roots) {
            traverse(traversalState, root, root, new LinkedHashSet<>(), new ArrayList<>());
        }
        return errors;
    }

    private BeanDiscoveryResult discoverBeanDefinitions(ConfigurableBeanContext beanContext) {
        Collection<BeanDefinitionReference<Object>> references;
        try {
            references = beanContext.getBeanDefinitionReferences();
        } catch (RuntimeException e) {
            return beanDefinitionReferenceDiscoveryFailure(e);
        }

        return loadBeanDefinitions(beanContext, references);
    }

    private BeanDiscoveryResult beanDefinitionReferenceDiscoveryFailure(RuntimeException e) {
        Optional<String> suppressedType = firstSuppressedTypeInThrowable(e);
        if (suppressedType.isPresent()) {
            LOG.debug("Suppressing metadata DI reference discovery failure for security type {}", suppressedType.get(), e);
            return new BeanDiscoveryResult(List.of(), Set.of());
        }
        LOG.debug("Unable to resolve bean definition references for metadata DI validation; returning synthetic discovery failure", e);
        return new BeanDiscoveryResult(List.of(), Set.of(beanDefinitionDiscoveryFailure(e)));
    }

    private BeanDiscoveryResult loadBeanDefinitions(
        ConfigurableBeanContext beanContext,
        Collection<BeanDefinitionReference<Object>> references
    ) {
        List<BeanDefinition<Object>> definitions = new ArrayList<>(references.size());
        Set<DependencyInjectionError> discoveryErrors = new LinkedHashSet<>();
        for (BeanDefinitionReference<Object> reference : references) {
            ReferenceLoadResult result = loadBeanDefinitionReference(beanContext, reference);
            result.definition().ifPresent(definitions::add);
            result.discoveryError().ifPresent(discoveryErrors::add);
        }
        return new BeanDiscoveryResult(definitions, discoveryErrors);
    }

    private ReferenceLoadResult loadBeanDefinitionReference(
        ConfigurableBeanContext beanContext,
        BeanDefinitionReference<Object> reference
    ) {
        String referenceName = reference.getBeanDefinitionName();
        try {
            if (shouldLoadReference(beanContext, reference)) {
                return new ReferenceLoadResult(Optional.ofNullable(reference.load(beanContext)), Optional.empty());
            }
            return ReferenceLoadResult.empty();
        } catch (RuntimeException | LinkageError e) {
            if (isSuppressedReferenceLoadFailure(referenceName, e)) {
                LOG.debug("Suppressing metadata DI reference load failure for {}", referenceName, e);
                return ReferenceLoadResult.empty();
            }
            LOG.debug("Unable to load bean definition reference {} during metadata DI validation", referenceName, e);
            return new ReferenceLoadResult(Optional.empty(), Optional.of(beanDefinitionReferenceLoadFailure(referenceName, e)));
        }
    }

    private boolean shouldLoadReference(
        ConfigurableBeanContext beanContext,
        BeanDefinitionReference<Object> reference
    ) {
        if (!reference.isPresent()) {
            return false;
        }
        String beanTypeName = reference.getBeanType().getName();
        if (matchesSuppressionPattern(beanTypeName)) {
            return false;
        }
        return reference.isEnabled(beanContext);
    }

    private boolean isSuppressedReferenceLoadFailure(String referenceName, Throwable throwable) {
        return firstSuppressedTypeInThrowable(throwable).isPresent() || matchesSuppressionPattern(referenceName);
    }

    private static Collection<DisabledBean<?>> resolveDisabledBeans(ConfigurableBeanContext beanContext) {
        try {
            return beanContext.getDisabledBeans();
        } catch (RuntimeException e) {
            LOG.debug("Unable to resolve disabled beans for metadata DI validation; continuing without disabled-bean diagnostics", e);
            return List.of();
        }
    }

    private static DependencyInjectionError beanDefinitionDiscoveryFailure(RuntimeException e) {
        return new DependencyInjectionError(
            DISCOVERY_SENTINEL,
            DISCOVERY_SENTINEL,
            formatDiscoveryFailureMessage(e),
            null,
            null,
            discoveryFailurePath(e),
            null,
            null
        );
    }

    private static DependencyInjectionError beanDefinitionReferenceLoadFailure(String beanTypeName, Throwable e) {
        return new DependencyInjectionError(
            beanTypeName,
            beanTypeName,
            "Dependency injection bean-definition reference loading failed for [" + beanTypeName + "]: " + formatReferenceLoadFailureMessage(e),
            null,
            null,
            referenceLoadFailurePath(beanTypeName, e),
            null,
            null
        );
    }

    private List<BeanDefinition<Object>> validationRoots(Collection<BeanDefinition<Object>> definitions) {
        Stream<BeanDefinition<Object>> stream = switch (validationStrategy) {
            case REACHABLE -> definitions.stream().filter(this::isReachableRoot);
            case APPLICATION_BEANS -> definitions.stream().filter(DefaultDependencyInjectionValidator::isApplicationBean);
            case ALL_BEANS -> definitions.stream();
        };
        return stream
            .filter(this::isConcreteRoot)
            .filter(root -> !isSuppressed(root))
            .sorted(Comparator.comparing(BeanDefinition::getName))
            .toList();
    }

    private static void ensureConfigured(ConfigurableBeanContext beanContext) {
        try {
            beanContext.configure();
        } catch (RuntimeException e) {
            LOG.debug("Bean context configure() failed during metadata DI validation; continuing with available definitions", e);
        }
    }

    // A reachable root is some bean that is reachable either via a startup event
    // or via an externally reachable network like a controller or a type meta annotated
    // with EntryPoint
    private boolean isReachableRoot(BeanDefinition<?> definition) {
        if (definition.hasStereotype(Context.class)) {
            return true;
        }
        if (definition.hasStereotype("io.micronaut.http.annotation.Controller")) {
            return true;
        }
        if (hasEntryPoint(definition)) {
            return true;
        }
        return isStartupEventListener(definition);
    }

    private static boolean hasEntryPoint(BeanDefinition<?> definition) {
        for (ExecutableMethod<?, ?> executableMethod : definition.getExecutableMethods()) {
            if (executableMethod.hasStereotype(EntryPoint.class)) {
                return true;
            }
            if (executableMethod.hasStereotype("io.micronaut.scheduling.annotation.Scheduled")) {
                return true;
            }
            if (executableMethod.hasStereotype(Executable.class)
                && executableMethod.booleanValue(Executable.class, Executable.MEMBER_PROCESS_ON_STARTUP).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    private boolean isConcreteRoot(BeanDefinition<?> definition) {
        Class<?> beanType = definition.getBeanType();
        int modifiers = beanType.getModifiers();
        return !beanType.isInterface() && !Modifier.isAbstract(modifiers);
    }

    private static boolean isStartupEventListener(BeanDefinition<?> definition) {
        if (!ApplicationEventListener.class.isAssignableFrom(definition.getBeanType())) {
            return false;
        }
        List<Argument<?>> arguments = definition.getTypeArguments(ApplicationEventListener.class);
        if (arguments.isEmpty()) {
            return false;
        }
        String eventType = arguments.getFirst().getType().getName();
        return STARTUP_EVENT.equals(eventType) || SERVER_STARTUP_EVENT.equals(eventType);
    }

    private static List<DisabledCandidate> disabledCandidates(Collection<DisabledBean<?>> disabledBeans) {
        return disabledBeans.stream()
            .map(disabledBean -> new DisabledCandidate(disabledBean.getName(), disabledBean.getBeanType(), disabledBean.reasons()))
            .sorted(Comparator.comparing(DisabledCandidate::name))
            .toList();
    }

    private void traverse(
        TraversalState state,
        BeanDefinition<?> root,
        BeanDefinition<?> current,
        Set<String> stack,
        List<String> path
    ) {
        String currentName = current.getName();
        if (!stack.add(currentName)) {
            return;
        }
        path.add(currentName);
        try {
            for (DependencyRequirement requirement : dependenciesOf(current)) {
                Optional<BeanDefinition<?>> target = resolveDependencyTarget(state, root, current, requirement, path);
                if (target.isPresent()) {
                    BeanDefinition<?> targetDefinition = target.get();
                    if (stack.contains(targetDefinition.getName())) {
                        addCircularDependencyError(
                            root,
                            requirement,
                            targetDefinition,
                            path,
                            state.errors(),
                            state.dedupe()
                        );
                    } else {
                        traverse(state, root, targetDefinition, stack, path);
                    }
                }
            }
        } finally {
            path.removeLast();
            stack.remove(currentName);
        }
    }

    private Optional<BeanDefinition<?>> resolveDependencyTarget(
        TraversalState state,
        BeanDefinition<?> root,
        BeanDefinition<?> current,
        DependencyRequirement requirement,
        List<String> path
    ) {
        Argument<?> argument = requirement.argument();
        if (isSuppressed(argument)) {
            return Optional.empty();
        }
        String missingPropertyMessage = resolveMissingPropertyMessage(state.beanContext(), current, argument);
        if (missingPropertyMessage != null) {
            addError(
                root,
                argument,
                requirement,
                missingPropertyMessage,
                path,
                state.errors(),
                state.dedupe(),
                state.disabledCandidates()
            );
            return Optional.empty();
        }
        if (skipInjection(requirement, argument, current)) {
            return Optional.empty();
        }
        Optional<BeanDefinition<?>> target;
        try {
            target = resolveBeanDefinition(state.beanContext(), argument, Qualifiers.forArgument(argument));
        } catch (Exception e) {
            addError(
                root,
                argument,
                requirement,
                sanitizeMessage(e),
                path,
                state.errors(),
                state.dedupe(),
                state.disabledCandidates()
            );
            return Optional.empty();
        }
        if (target.isEmpty()) {
            String message = missingBeanMessage(state.beanContext(), state.definitions(), argument, requirement, state.disabledCandidates());
            addError(root, argument, requirement, message, path, state.errors(), state.dedupe(), state.disabledCandidates());
            return Optional.empty();
        }
        BeanDefinition<?> targetDefinition = target.get();
        if (isSuppressed(targetDefinition)) {
            return Optional.empty();
        }
        if (validationStrategy == DependencyInjectionValidationStrategy.APPLICATION_BEANS && !isApplicationBean(targetDefinition)) {
            return Optional.empty();
        }
        return target;
    }

    private static boolean isApplicationBean(BeanDefinition<?> definition) {
        return isApplicationClass(definition.getClass()) || isApplicationClass(definition.getBeanType());
    }

    private static boolean isApplicationClass(Class<?> type) {
        URL location = type.getProtectionDomain() != null && type.getProtectionDomain().getCodeSource() != null
            ? type.getProtectionDomain().getCodeSource().getLocation()
            : null;
        if (location == null) {
            return false;
        }
        try {
            return !Path.of(location.toURI()).toString().endsWith(".jar");
        } catch (Exception e) {
            LOG.trace("Unable to determine code source path for {}", type.getName(), e);
            return false;
        }
    }

    private boolean isSuppressed(BeanDefinition<?> definition) {
        return matchesSuppressionPattern(normalizeBeanClassName(definition.getName()))
            || matchesSuppressionPattern(definition.getBeanType().getName());
    }

    private boolean isSuppressed(Argument<?> argument) {
        return matchesSuppressionPattern(argument.getType().getName());
    }

    private boolean matchesSuppressionPattern(String className) {
        if (suppressedClassMatchers.isEmpty()) {
            return false;
        }
        for (ClassSuppressionMatcher matcher : suppressedClassMatchers) {
            if (matcher.matches(className)) {
                return true;
            }
        }
        return false;
    }

    private List<DependencyRequirement> dependenciesOf(BeanDefinition<?> definition) {
        List<DependencyRequirement> requirements = new ArrayList<>();

        ConstructorInjectionPoint<?> constructor = definition.getConstructor();
        for (Argument<?> argument : constructor.getArguments()) {
            requirements.add(new DependencyRequirement(
                argument,
                constructorInjectionPointDescription(definition, constructor, argument),
                constructorSnippet(definition, argument),
                definition.getBeanType()
            ));
        }

        for (FieldInjectionPoint<?, ?> field : definition.getInjectedFields()) {
            Argument<?> argument = field.asArgument();
            requirements.add(new DependencyRequirement(
                argument,
                "field " + definition.getBeanType().getSimpleName() + "." + field.getName(),
                fieldSnippet(field, argument),
                definition.getBeanType()
            ));
        }

        for (MethodInjectionPoint<?, ?> method : definition.getInjectedMethods()) {
            for (Argument<?> argument : method.getArguments()) {
                requirements.add(new DependencyRequirement(
                    argument,
                    METHOD_INJECTION_POINT_PREFIX + definition.getBeanType().getSimpleName() + "." + method.getName() + "(" + argument.getName() + ")",
                    methodSnippet(method, argument),
                    definition.getBeanType()
                ));
            }
        }

        return requirements;
    }

    private static String constructorSnippet(BeanDefinition<?> definition, Argument<?> argument) {
        return definition.getBeanDescription(TypeInformation.TypeFormat.SHORTENED, false)
            + "(" + argumentSnippet(argument) + ")";
    }

    private static String fieldSnippet(FieldInjectionPoint<?, ?> field, Argument<?> argument) {
        return argument.getBeanTypeString(TypeInformation.TypeFormat.SHORTENED) + " " + field.getName() + ";";
    }

    private static String methodSnippet(MethodInjectionPoint<?, ?> method, Argument<?> argument) {
        return method.getName() + "(" + argumentSnippet(argument) + ")";
    }

    private static String argumentSnippet(Argument<?> argument) {
        return argument.getBeanTypeString(TypeInformation.TypeFormat.SHORTENED) + " " + argument.getName();
    }

    private static boolean skipInjection(DependencyRequirement requirement, Argument<?> argument, BeanDefinition<?> current) {
        return isLazyDependency(argument)
            || !isRequiredInjection(argument)
            || isInternalArgument(argument)
            || isContainerArgument(argument)
            || isConfigurationBean(current)
            || hasPropertyBinding(argument)
            || hasInternalName(argument)
            || requirement.injectionPoint().startsWith("method set");
    }

    private static String constructorInjectionPointDescription(
        BeanDefinition<?> definition,
        ConstructorInjectionPoint<?> constructor,
        Argument<?> argument
    ) {
        Optional<Class<?>> declaringType = definition.getDeclaringType();
        if (constructor instanceof MethodInjectionPoint<?, ?> methodInjectionPoint) {
            Class<?> methodDeclaringType = declaringType.orElse(methodInjectionPoint.getDeclaringType());
            return METHOD_INJECTION_POINT_PREFIX
                + methodDeclaringType.getSimpleName()
                + "."
                + methodInjectionPoint.getName()
                + "("
                + argument.getName()
                + ")";
        }
        if (constructor instanceof FieldInjectionPoint<?, ?> fieldInjectionPoint) {
            Class<?> fieldDeclaringType = declaringType.orElse(fieldInjectionPoint.getDeclaringBean().getBeanType());
            return "field "
                + fieldDeclaringType.getSimpleName()
                + "."
                + fieldInjectionPoint.getName();
        }

        if (declaringType.isPresent() && !declaringType.get().equals(definition.getBeanType())) {
            String factoryMemberName = resolveFactoryMemberName(definition);
            if (factoryMemberName != null) {
                return METHOD_INJECTION_POINT_PREFIX
                    + declaringType.get().getSimpleName()
                    + "."
                    + factoryMemberName
                    + "("
                    + argument.getName()
                    + ")";
            }
        }

        return "constructor " + constructorDescription(definition, argument);
    }

    @Nullable
    private static String resolveFactoryMemberName(BeanDefinition<?> definition) {
        String beanDescription = definition.getBeanDescription(TypeInformation.TypeFormat.SHORTENED, false);
        int factoryQualifierStart = beanDescription.lastIndexOf(' ');
        String memberReference = factoryQualifierStart >= 0
            ? beanDescription.substring(factoryQualifierStart + 1)
            : beanDescription;
        int lastDot = memberReference.lastIndexOf('.');
        if (lastDot < 0 || lastDot == memberReference.length() - 1) {
            return null;
        }
        return memberReference.substring(lastDot + 1);
    }

    private static String constructorDescription(BeanDefinition<?> definition, Argument<?> argument) {
        return definition.getBeanType().getSimpleName() + "(" + argument.getName() + ")";
    }

    private static boolean isLazyDependency(Argument<?> argument) {
        Class<?> type = argument.getType();
        return Optional.class == type
            || Provider.class == type
            || BeanProvider.class == type;
    }

    private static boolean isRequiredInjection(Argument<?> argument) {
        return !argument.isNullable();
    }

    private static boolean isInternalArgument(Argument<?> argument) {
        Class<?> type = argument.getType();
        return BeanContext.class == type
            || ApplicationContext.class == type
            || BeanResolutionContext.class == type
            || Environment.class == type
            || PropertyResolver.class == type
            || ConversionService.class == type;
    }

    private static boolean isContainerArgument(Argument<?> argument) {
        Class<?> type = argument.getType();
        return type.isArray()
            || Iterable.class.isAssignableFrom(type)
            || Collection.class.isAssignableFrom(type)
            || Map.class.isAssignableFrom(type)
            || Stream.class.isAssignableFrom(type);
    }

    private static boolean isConfigurationBean(BeanDefinition<?> definition) {
        return definition.hasStereotype(ConfigurationReader.class);
    }

    private static boolean hasPropertyBinding(Argument<?> argument) {
        AnnotationMetadata annotationMetadata = argument.getAnnotationMetadata();
        return annotationMetadata.hasStereotype(Value.class)
            || annotationMetadata.hasStereotype(Property.class)
            || annotationMetadata.hasStereotype(Parameter.class);
    }

    @Nullable
    private static String resolveMissingPropertyMessage(
        ConfigurableBeanContext beanContext,
        BeanDefinition<?> current,
        Argument<?> argument
    ) {
        if (isConfigurationBean(current) || !isRequiredInjection(argument)) {
            return null;
        }
        String missingValueProperty = resolveMissingValueProperty(beanContext, argument);
        if (missingValueProperty != null) {
            return "Missing required property [" + missingValueProperty + "] for @Value injection";
        }
        String missingPropertyInjection = resolveMissingPropertyInjection(beanContext, argument);
        if (missingPropertyInjection != null) {
            return "Missing required property [" + missingPropertyInjection + "] for @Property injection";
        }
        return null;
    }

    private static boolean hasInternalName(Argument<?> argument) {
        String name = argument.getName();
        return name.startsWith("$");
    }

    @Nullable
    private static String resolveMissingValueProperty(ConfigurableBeanContext beanContext, Argument<?> argument) {
        Optional<String> valueExpression;
        try {
            valueExpression = argument.getAnnotationMetadata().stringValue(Value.class);
        } catch (ConfigurationException e) {
            valueExpression = extractPlaceholderExpression(e.getMessage());
        }
        if (valueExpression.isEmpty()) {
            return null;
        }
        String expr = valueExpression.get();
        if (!expr.startsWith("${") || !expr.endsWith("}")) {
            return null;
        }
        String inner = expr.substring(2, expr.length() - 1);
        int colon = inner.indexOf(':');
        String property = colon > -1 ? inner.substring(0, colon) : inner;
        boolean hasDefault = colon > -1;

        if (hasDefault) {
            return null;
        }

        if (beanContext instanceof ApplicationContext applicationContext) {
            return applicationContext.containsProperty(property) ? null : property;
        }
        if (beanContext instanceof PropertyResolver propertyResolver) {
            return propertyResolver.containsProperty(property) ? null : property;
        }
        return null;
    }

    @Nullable
    private static String resolveMissingPropertyInjection(ConfigurableBeanContext beanContext, Argument<?> argument) {
        AnnotationMetadata annotationMetadata = argument.getAnnotationMetadata();
        if (!annotationMetadata.hasStereotype(Property.class)) {
            return null;
        }
        Optional<String> propertyName = annotationMetadata.stringValue(Property.class, "name")
            .filter(name -> !name.isBlank());
        if (propertyName.isEmpty()) {
            return null;
        }
        boolean hasDefault = annotationMetadata.stringValue(Property.class, "defaultValue")
            .filter(defaultValue -> !defaultValue.isBlank())
            .isPresent();
        if (hasDefault) {
            return null;
        }
        return containsProperty(beanContext, propertyName.get()) ? null : propertyName.get();
    }

    private static Optional<String> extractPlaceholderExpression(@Nullable String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        int start = message.indexOf("${");
        if (start < 0) {
            return Optional.empty();
        }
        int end = message.indexOf('}', start + 2);
        if (end < 0) {
            return Optional.empty();
        }
        return Optional.of(message.substring(start, end + 1));
    }

    private static Optional<BeanDefinition<?>> resolveBeanDefinition(
        ConfigurableBeanContext beanContext,
        Argument<?> argument,
        @Nullable Qualifier<?> qualifier
    ) {
        Collection<BeanDefinition<?>> candidates = findBeanDefinitions(beanContext, argument, qualifier);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (candidates.size() == 1) {
            return Optional.of(candidates.iterator().next());
        }
        return Optional.of(lastChanceResolve(argument, candidates));
    }

    private static BeanDefinition<?> lastChanceResolve(Argument<?> argument, Collection<BeanDefinition<?>> candidates) {
        Collection<BeanDefinition<?>> narrowedCandidates = candidates;
        if (narrowedCandidates.size() > 1) {
            List<BeanDefinition<?>> primary = narrowedCandidates.stream()
                .filter(BeanDefinition::isPrimary)
                .toList();
            if (!primary.isEmpty()) {
                narrowedCandidates = primary;
            }
        }
        if (narrowedCandidates.size() == 1) {
            return narrowedCandidates.iterator().next();
        }

        Collection<BeanDefinition<?>> originalCandidates = narrowedCandidates;
        narrowedCandidates = narrowedCandidates.stream()
            .filter(candidate -> !candidate.hasDeclaredStereotype(Secondary.class))
            .toList();
        if (narrowedCandidates.size() == 1) {
            return narrowedCandidates.iterator().next();
        }
        if (narrowedCandidates.isEmpty()) {
            throw newNonUniqueBeanException(argument, originalCandidates);
        }

        List<BeanDefinition<?>> orderedCandidates = new ArrayList<>(narrowedCandidates);
        orderedCandidates.sort(OrderUtil.ORDERED_COMPARATOR);
        BeanDefinition<?> bean = orderedCandidates.getFirst();
        BeanDefinition<?> next = orderedCandidates.get(1);
        if (bean.getOrder() != next.getOrder()) {
            return bean;
        }

        Class<?> defaultImplementation = bean.getDefaultImplementation();
        if (defaultImplementation != null) {
            for (BeanDefinition<?> candidate : narrowedCandidates) {
                if (candidate.getBeanType().equals(defaultImplementation)) {
                    return candidate;
                }
            }
        }

        List<BeanDefinition<?>> exactMatches = narrowedCandidates.stream()
            .filter(candidate -> candidate.getBeanType().equals(argument.getType()))
            .toList();
        if (exactMatches.size() == 1) {
            return exactMatches.getFirst();
        }

        throw newNonUniqueBeanException(argument, narrowedCandidates);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static NonUniqueBeanException newNonUniqueBeanException(
        Argument<?> argument,
        Collection<BeanDefinition<?>> candidates
    ) {
        return new NonUniqueBeanException((Class) argument.getType(), (java.util.Iterator) candidates.iterator());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Collection<BeanDefinition<?>> findBeanDefinitions(
        ConfigurableBeanContext beanContext,
        Argument<?> argument,
        @Nullable Qualifier<?> qualifier
    ) {
        return (Collection) beanContext.getBeanDefinitions((Argument) argument, (Qualifier) qualifier);
    }

    private static String missingBeanMessage(
        ConfigurableBeanContext beanContext,
        Collection<BeanDefinition<Object>> definitions,
        Argument<?> argument,
        DependencyRequirement requirement,
        List<DisabledCandidate> disabledCandidates
    ) {
        String message = "No bean of type [" + argument.getType().getName() + "] exists for " + requirement.injectionPoint();
        Optional<String> configurationKey = missingEachPropertyConfigurationKey(definitions, argument);
        if (configurationKey.isPresent() && !containsProperty(beanContext, configurationKey.get())) {
            return message + ". Bean may be non-creatable because configuration property [" + configurationKey.get() + "] is missing";
        }
        List<DisabledCandidate> matchingDisabledCandidates = matchingDisabledCandidates(argument, disabledCandidates);
        if (!matchingDisabledCandidates.isEmpty()) {
            String candidates = matchingDisabledCandidates.stream()
                .map(DefaultDependencyInjectionValidator::formatDisabledCandidate)
                .collect(Collectors.joining("; "));
            return message + ". Disabled candidate beans: " + candidates;
        }
        return message;
    }

    private static Optional<String> missingEachPropertyConfigurationKey(
        Collection<BeanDefinition<Object>> definitions,
        Argument<?> argument
    ) {
        if (definitions.isEmpty()) {
            return Optional.empty();
        }

        Map<Class<?>, List<BeanDefinition<Object>>> candidatesByType = new HashMap<>();
        for (BeanDefinition<Object> candidate : definitions) {
            if (!argument.getType().isAssignableFrom(candidate.getBeanType())) {
                continue;
            }
            if (!candidate.getAnnotationMetadata().hasStereotype(EachBean.class)) {
                continue;
            }

            Optional<EachPropertyOrigin> origin = resolveEachPropertyOrigin(candidate, definitions, new HashSet<>(), candidatesByType);
            if (origin.isEmpty() || origin.get().prefix().isBlank()) {
                continue;
            }

            Optional<String> primary = origin.get().primary();
            Optional<String> name = argument.getAnnotationMetadata().stringValue(Named.class).or(() -> primary.filter(s -> !s.isBlank()));
            if (name.isEmpty() || name.get().isBlank()) {
                continue;
            }

            return Optional.of(origin.get().prefix() + "." + name.get());
        }
        return Optional.empty();
    }

    @SuppressWarnings("java:S3776")
    private static Optional<EachPropertyOrigin> resolveEachPropertyOrigin(
        BeanDefinition<Object> candidate,
        Collection<BeanDefinition<Object>> definitions,
        Set<String> visited,
        Map<Class<?>, List<BeanDefinition<Object>>> candidatesByType
    ) {
        if (!visited.add(candidate.getName())) {
            return Optional.empty();
        }

        for (Argument<?> constructorArgument : candidate.getConstructor().getArguments()) {
            List<BeanDefinition<Object>> candidatesForArgument = candidatesByType.computeIfAbsent(
                constructorArgument.getType(),
                type -> definitions.stream()
                    .filter(definition -> type.isAssignableFrom(definition.getBeanType()))
                    .toList()
            );

            for (BeanDefinition<Object> definition : candidatesForArgument) {
                if (definition.getAnnotationMetadata().hasStereotype(EachProperty.class)) {
                    Optional<String> prefix = definition.getAnnotationMetadata().stringValue(EachProperty.class);
                    if (prefix.isPresent() && !prefix.get().isBlank()) {
                        Optional<String> primary = definition.getAnnotationMetadata()
                            .stringValue(EachProperty.class, "primary")
                            .filter(s -> !s.isBlank());
                        return Optional.of(new EachPropertyOrigin(prefix.get(), primary));
                    }
                }
            }

            for (BeanDefinition<Object> definition : candidatesForArgument) {
                if (!definition.getAnnotationMetadata().hasStereotype(EachBean.class)) {
                    continue;
                }
                Optional<EachPropertyOrigin> nested = resolveEachPropertyOrigin(definition, definitions, visited, candidatesByType);
                if (nested.isPresent()) {
                    return nested;
                }
            }
        }

        return Optional.empty();
    }

    private static boolean containsProperty(ConfigurableBeanContext beanContext, String property) {
        if (beanContext instanceof ApplicationContext applicationContext) {
            if (applicationContext.containsProperties(property) || applicationContext.containsProperty(property)) {
                return true;
            }
            Environment environment = applicationContext.getEnvironment();
            return environment.containsProperties(property)
                || environment.containsProperty(property);
        }
        if (beanContext instanceof PropertyResolver propertyResolver) {
            return propertyResolver.containsProperties(property) || propertyResolver.containsProperty(property);
        }
        return false;
    }

    private static String sanitizeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getName();
        }
        return message;
    }

    private void addError(
        BeanDefinition<?> root,
        Argument<?> missingArgument,
        DependencyRequirement requirement,
        String message,
        List<String> path,
        Set<DependencyInjectionError> errors,
        Set<String> dedupe,
        List<DisabledCandidate> disabledCandidates
    ) {
        String beanName = missingArgument.getType().getName();
        String disabledReason = findDisabledReason(missingArgument, disabledCandidates);
        List<String> failingPath = new ArrayList<>(path.size() + 1);
        for (String s : path) {
            failingPath.add("* " + s);
        }
        failingPath.add("* missing " + beanName + " for " + requirement.injectionPoint());

        String snippet = requirement.snippet();
        DependencyInjectionError error = new DependencyInjectionError(
            root.getName(),
            beanName,
            message,
            requirement.injectionPoint(),
            disabledReason,
            failingPath,
            snippet,
            "text"
        );

        String key = error.rootBean() + "|" + error.bean() + "|" + error.injectionPoint() + "|" + error.message();
        if (dedupe.add(key)) {
            errors.add(error);
        }
    }

    private void addCircularDependencyError(
        BeanDefinition<?> root,
        DependencyRequirement requirement,
        BeanDefinition<?> cycleTarget,
        List<String> path,
        Set<DependencyInjectionError> errors,
        Set<String> dedupe
    ) {
        String cycleBean = cycleTarget.getName();
        List<String> failingPath = new ArrayList<>(path.size() + 2);
        for (String s : path) {
            failingPath.add("* " + s);
        }
        failingPath.add("* " + cycleBean);

        String message = "Circular dependency detected: "
            + String.join(" -> ", path)
            + " -> "
            + cycleBean
            + " for "
            + requirement.injectionPoint();

        DependencyInjectionError error = new DependencyInjectionError(
            root.getName(),
            cycleBean,
            message,
            requirement.injectionPoint(),
            null,
            failingPath,
            requirement.snippet(),
            "text"
        );

        String key = error.rootBean() + "|" + error.bean() + "|" + error.injectionPoint() + "|" + error.message();
        if (dedupe.add(key)) {
            errors.add(error);
        }
    }

    @Nullable
    private static String findDisabledReason(Argument<?> argument, List<DisabledCandidate> disabledCandidates) {
        List<DisabledCandidate> matchingDisabledCandidates = matchingDisabledCandidates(argument, disabledCandidates);
        if (!matchingDisabledCandidates.isEmpty()) {
            return matchingDisabledCandidates.stream()
                .map(DefaultDependencyInjectionValidator::formatDisabledCandidate)
                .collect(Collectors.joining("; "));
        }
        return null;
    }

    private static List<DisabledCandidate> matchingDisabledCandidates(
        Argument<?> argument,
        List<DisabledCandidate> disabledCandidates
    ) {
        Class<?> requiredType = argument.getType();
        String requiredTypeName = requiredType.getName();
        Map<String, DisabledCandidate> matched = new LinkedHashMap<>();
        for (DisabledCandidate candidate : disabledCandidates) {
            String candidateName = candidate.name();
            if (candidateName.isBlank()) {
                continue;
            }
            if (candidateName.equals(requiredTypeName) || candidateName.contains(requiredTypeName)) {
                matched.put(candidateName, candidate);
                continue;
            }

            if (requiredType.isAssignableFrom(candidate.beanType())) {
                matched.put(candidateName, candidate);
            }
        }
        return matched.values().stream()
            .sorted(Comparator.comparing(DisabledCandidate::name))
            .toList();
    }

    private static String normalizeBeanClassName(String beanName) {
        int dollarDollar = beanName.indexOf("$$");
        if (dollarDollar > -1) {
            return beanName.substring(0, dollarDollar);
        }
        return beanName;
    }

    private static List<String> effectiveSuppressedClassPatterns(@Nullable List<String> suppressedClassPatterns) {
        if (suppressedClassPatterns == null || suppressedClassPatterns.isEmpty()) {
            return DEFAULT_SUPPRESSED_CLASS_PATTERNS;
        }
        LinkedHashSet<String> merged = new LinkedHashSet<>(DEFAULT_SUPPRESSED_CLASS_PATTERNS);
        merged.addAll(suppressedClassPatterns);
        return List.copyOf(merged);
    }

    private static String formatDisabledCandidate(DisabledCandidate candidate) {
        List<String> reasons = candidate.reasons();
        String reason = reasons.isEmpty() ? "No disable reason available" : String.join("; ", reasons);
        return candidate.name() + " (" + reason + ")";
    }

    private static String formatDiscoveryFailureMessage(Throwable throwable) {
        StringBuilder message = new StringBuilder(
            "Dependency injection bean-definition discovery failed before the context was running. "
        );
        message.append("This usually indicates a runtime-only condition was evaluated during metadata validation, not necessarily an application misconfiguration. ");
        message.append("Failure: ").append(sanitizeMessage(throwable));

        List<String> causeChain = new ArrayList<>(4);
        Throwable current = throwable.getCause();
        int depth = 0;
        while (current != null && depth++ < 5) {
            causeChain.add(current.getClass().getName() + ": " + sanitizeMessage(current));
            current = current.getCause() == current ? null : current.getCause();
        }
        if (!causeChain.isEmpty()) {
            message.append(". Cause chain: ");
            message.append(String.join(" -> ", causeChain));
        }

        Optional<String> conditionClass = triggeringConditionClass(throwable);
        conditionClass.ifPresent(condition -> message.append(". Triggering condition: ").append(condition));
        return message.toString();
    }

    private static String formatReferenceLoadFailureMessage(Throwable throwable) {
        StringBuilder message = new StringBuilder();
        message.append(throwable.getClass().getSimpleName()).append(": ").append(sanitizeMessage(throwable));

        List<String> causeChain = new ArrayList<>(4);
        Throwable current = throwable.getCause();
        int depth = 0;
        while (current != null && depth++ < 5) {
            causeChain.add(current.getClass().getName() + ": " + sanitizeMessage(current));
            current = current.getCause() == current ? null : current.getCause();
        }
        if (!causeChain.isEmpty()) {
            message.append(". Cause chain: ").append(String.join(" -> ", causeChain));
        }

        Optional<String> conditionClass = triggeringConditionClass(throwable);
        conditionClass.ifPresent(condition -> message.append(". Triggering condition: ").append(condition));
        return message.toString();
    }

    private static List<String> discoveryFailurePath(Throwable throwable) {
        List<String> path = new ArrayList<>(4);
        path.add(DISCOVERY_SENTINEL);
        Optional<String> conditionClass = triggeringConditionClass(throwable);
        conditionClass.ifPresent(path::add);
        path.add("failed: " + sanitizeMessage(throwable));
        return List.copyOf(path);
    }

    private static List<String> referenceLoadFailurePath(String beanTypeName, Throwable throwable) {
        List<String> path = new ArrayList<>(4);
        path.add(beanTypeName);
        Optional<String> conditionClass = triggeringConditionClass(throwable);
        conditionClass.ifPresent(path::add);
        path.add("failed: " + sanitizeMessage(throwable));
        return List.copyOf(path);
    }

    private static Optional<String> triggeringConditionClass(Throwable throwable) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth++ < 6) {
            for (StackTraceElement element : current.getStackTrace()) {
                String className = element.getClassName();
                if (className.endsWith("Condition")) {
                    return Optional.of(className);
                }
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return Optional.empty();
    }

    private Optional<String> firstSuppressedTypeInThrowable(Throwable throwable) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth++ < 6) {
            for (StackTraceElement element : current.getStackTrace()) {
                String className = element.getClassName();
                if (matchesSuppressionPattern(className)) {
                    return Optional.of(className);
                }
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return Optional.empty();
    }

    private record DependencyRequirement(
        Argument<?> argument,
        String injectionPoint,
        String snippet,
        Class<?> ownerType
    ) {
    }

    private record DisabledCandidate(String name, Class<?> beanType, List<String> reasons) {
    }

    private record EachPropertyOrigin(String prefix, Optional<String> primary) {
    }

    private record TraversalState(
        ConfigurableBeanContext beanContext,
        Collection<BeanDefinition<Object>> definitions,
        List<DisabledCandidate> disabledCandidates,
        Set<DependencyInjectionError> errors,
        Set<String> dedupe
    ) {
    }

    private record BeanDiscoveryResult(
        Collection<BeanDefinition<Object>> definitions,
        Set<DependencyInjectionError> discoveryErrors
    ) {
    }

    private record ReferenceLoadResult(
        Optional<BeanDefinition<Object>> definition,
        Optional<DependencyInjectionError> discoveryError
    ) {
        static ReferenceLoadResult empty() {
            return new ReferenceLoadResult(Optional.empty(), Optional.empty());
        }
    }

    private interface ClassSuppressionMatcher {
        boolean matches(String className);

        static List<ClassSuppressionMatcher> compileAll(List<String> patterns) {
            List<ClassSuppressionMatcher> matchers = new ArrayList<>(patterns.size());
            for (String pattern : patterns) {
                if (pattern.isBlank()) {
                    continue;
                }
                matchers.add(compile(pattern));
            }
            return matchers;
        }

        static ClassSuppressionMatcher compile(String pattern) {
            if (pattern.indexOf('*') > -1) {
                Pattern regex = Pattern.compile("^" + wildcardToRegex(pattern) + "$");
                return className -> regex.matcher(className).matches();
            }
            return className -> className.equals(pattern);
        }

        private static String wildcardToRegex(String wildcardPattern) {
            StringBuilder regex = new StringBuilder(wildcardPattern.length() * 2);
            for (int i = 0; i < wildcardPattern.length(); i++) {
                char c = wildcardPattern.charAt(i);
                if (c == '*') {
                    regex.append(".*");
                } else {
                    if ("\\.^$|?+()[]{}".indexOf(c) != -1) {
                        regex.append('\\');
                    }
                    regex.append(c);
                }
            }
            return regex.toString();
        }
    }
}
