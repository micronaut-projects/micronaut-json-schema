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
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.TypeInformation;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ConstructorInjectionPoint;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.FieldInjectionPoint;
import io.micronaut.inject.MethodInjectionPoint;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Modifier;
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
    private static final Logger LOG = LoggerFactory.getLogger(DefaultDependencyInjectionValidator.class);

    private static final String STARTUP_EVENT = "io.micronaut.runtime.event.StartupEvent";
    private static final String SERVER_STARTUP_EVENT = "io.micronaut.runtime.server.event.ServerStartupEvent";

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

        Collection<BeanDefinition<Object>> definitions = beanContext.getAllBeanDefinitions();
        if (definitions.isEmpty()) {
            return Set.of();
        }

        Map<String, List<String>> disabledByBeanName = disabledReasonsByBeanName(beanContext.getDisabledBeans());
        List<BeanDefinition<Object>> roots = definitions.stream()
            .filter(this::isReachableRoot)
            .filter(this::isConcreteRoot)
            .sorted(Comparator.comparing(BeanDefinition::getName))
            .toList();

        Set<DependencyInjectionError> errors = new LinkedHashSet<>();
        Set<String> dedupe = new LinkedHashSet<>();
        for (BeanDefinition<Object> root : roots) {
            traverse(
                beanContext,
                root,
                root,
                new LinkedHashSet<>(),
                new ArrayList<>(),
                errors,
                dedupe,
                disabledByBeanName
            );
        }
        return errors;
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

    private static Map<String, List<String>> disabledReasonsByBeanName(Collection<DisabledBean<?>> disabledBeans) {
        Map<String, List<String>> map = new LinkedHashMap<>(disabledBeans.size());
        for (DisabledBean<?> disabledBean : disabledBeans) {
            map.put(disabledBean.getName(), disabledBean.reasons());
        }
        return map;
    }

    private void traverse(
        ConfigurableBeanContext beanContext,
        BeanDefinition<?> root,
        BeanDefinition<?> current,
        Set<String> stack,
        List<String> path,
        Set<DependencyInjectionError> errors,
        Set<String> dedupe,
        Map<String, List<String>> disabledByBeanName
    ) {
        String currentName = current.getName();
        if (!stack.add(currentName)) {
            return;
        }
        path.add(currentName);
        try {
            for (DependencyRequirement requirement : dependenciesOf(current)) {
                Argument<?> argument = requirement.argument();

                String unresolvedProperty = resolveMissingValueProperty(beanContext, argument);
                if (unresolvedProperty != null) {
                    addError(
                        root,
                        current,
                        argument,
                        requirement,
                        "Missing required property [" + unresolvedProperty + "] for @Value injection",
                        path,
                        errors,
                        dedupe,
                        disabledByBeanName
                    );
                    continue;
                }

                if (skipInjection(requirement, argument, current)) {
                    continue;
                }

                Optional<BeanDefinition<?>> target;
                try {
                    target = resolveBeanDefinition(beanContext, argument, Qualifiers.forArgument(argument));
                } catch (Exception e) {
                    addError(
                        root,
                        current,
                        argument,
                        requirement,
                        sanitizeMessage(e),
                        path,
                        errors,
                        dedupe,
                        disabledByBeanName
                    );
                    continue;
                }

                if (target.isEmpty()) {
                    String message = missingBeanMessage(beanContext, argument, requirement, disabledByBeanName);
                    addError(root, current, argument, requirement, message, path, errors, dedupe, disabledByBeanName);
                    continue;
                }

                BeanDefinition<?> targetDefinition = target.get();
                if (stack.contains(targetDefinition.getName())) {
                    addCircularDependencyError(
                        root,
                        current,
                        requirement,
                        targetDefinition,
                        path,
                        errors,
                        dedupe
                    );
                    continue;
                }
                traverse(beanContext, root, targetDefinition, stack, path, errors, dedupe, disabledByBeanName);
            }
        } finally {
            path.removeLast();
            stack.remove(currentName);
        }
    }

    private List<DependencyRequirement> dependenciesOf(BeanDefinition<?> definition) {
        List<DependencyRequirement> requirements = new ArrayList<>();

        ConstructorInjectionPoint<?> constructor = definition.getConstructor();
        for (Argument<?> argument : constructor.getArguments()) {
            requirements.add(new DependencyRequirement(
                argument,
                "constructor " + constructorDescription(definition, argument),
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
                    "method " + definition.getBeanType().getSimpleName() + "." + method.getName() + "(" + argument.getName() + ")",
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
            || isPrimitiveOrSimple(argument)
            || isConfigurationBean(current)
            || hasPropertyBinding(argument)
            || hasInternalName(argument)
            || requirement.injectionPoint().startsWith("method set");
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

    private static boolean isPrimitiveOrSimple(Argument<?> argument) {
        return ClassUtils.isJavaBasicType(argument.getType());
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

    @SuppressWarnings({"unchecked", "rawtypes"})
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
        Argument<?> argument,
        DependencyRequirement requirement,
        Map<String, List<String>> disabledByBeanName
    ) {
        String message = "No bean of type [" + argument.getType().getName() + "] exists for " + requirement.injectionPoint();
        Optional<String> configurationKey = missingEachPropertyConfigurationKey(beanContext, argument);
        if (configurationKey.isPresent() && !containsProperty(beanContext, configurationKey.get())) {
            return message + ". Bean may be non-creatable because configuration property [" + configurationKey.get() + "] is missing";
        }
        List<DisabledCandidate> disabledCandidates = matchingDisabledCandidates(argument, disabledByBeanName);
        if (!disabledCandidates.isEmpty()) {
            String candidates = disabledCandidates.stream()
                .map(DefaultDependencyInjectionValidator::formatDisabledCandidate)
                .collect(Collectors.joining("; "));
            return message + ". Disabled candidate beans: " + candidates;
        }
        return message;
    }

    private static Optional<String> missingEachPropertyConfigurationKey(ConfigurableBeanContext beanContext, Argument<?> argument) {
        Collection<BeanDefinition<Object>> definitions = beanContext.getAllBeanDefinitions();
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
        BeanDefinition<?> current,
        Argument<?> missingArgument,
        DependencyRequirement requirement,
        String message,
        List<String> path,
        Set<DependencyInjectionError> errors,
        Set<String> dedupe,
        Map<String, List<String>> disabledByBeanName
    ) {
        String beanName = missingArgument.getType().getName();
        String disabledReason = findDisabledReason(missingArgument, disabledByBeanName);
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
        BeanDefinition<?> current,
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
    private static String findDisabledReason(Argument<?> argument, Map<String, List<String>> disabledByBeanName) {
        List<DisabledCandidate> disabledCandidates = matchingDisabledCandidates(argument, disabledByBeanName);
        if (!disabledCandidates.isEmpty()) {
            return disabledCandidates.stream()
                .map(DefaultDependencyInjectionValidator::formatDisabledCandidate)
                .collect(Collectors.joining("; "));
        }
        String beanName = argument.getType().getName();
        List<String> reasons = disabledByBeanName.get(beanName);
        if (reasons == null || reasons.isEmpty()) {
            for (Map.Entry<String, List<String>> entry : disabledByBeanName.entrySet()) {
                if (entry.getKey().contains(beanName)) {
                    reasons = entry.getValue();
                    break;
                }
            }
        }
        if (reasons == null || reasons.isEmpty()) {
            return null;
        }
        if (reasons.size() == 1) {
            return reasons.getFirst();
        }
        return String.join("; ", reasons);
    }

    private static List<DisabledCandidate> matchingDisabledCandidates(
        Argument<?> argument,
        Map<String, List<String>> disabledByBeanName
    ) {
        Class<?> requiredType = argument.getType();
        String requiredTypeName = requiredType.getName();
        Map<String, DisabledCandidate> matched = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : disabledByBeanName.entrySet()) {
            String candidateName = entry.getKey();
            if (candidateName.isBlank()) {
                continue;
            }
            if (candidateName.equals(requiredTypeName) || candidateName.contains(requiredTypeName)) {
                matched.put(candidateName, new DisabledCandidate(candidateName, entry.getValue()));
                continue;
            }

            String className = normalizeBeanClassName(candidateName);
            try {
                Class<?> candidateType = Class.forName(className, false, requiredType.getClassLoader());
                if (requiredType.isAssignableFrom(candidateType)) {
                    matched.put(candidateName, new DisabledCandidate(candidateName, entry.getValue()));
                }
            } catch (ClassNotFoundException | LinkageError ignored) {
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

    private static String formatDisabledCandidate(DisabledCandidate candidate) {
        List<String> reasons = candidate.reasons();
        String reason = reasons.isEmpty() ? "No disable reason available" : String.join("; ", reasons);
        return candidate.name() + " (" + reason + ")";
    }

    private record DependencyRequirement(
        Argument<?> argument,
        String injectionPoint,
        String snippet,
        Class<?> ownerType
    ) {
    }

    private record DisabledCandidate(String name, List<String> reasons) {
    }

    private record EachPropertyOrigin(String prefix, Optional<String> primary) {
    }
}
