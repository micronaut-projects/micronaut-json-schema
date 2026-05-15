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
package io.micronaut.jsonschema.registry;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.jsonschema.registry.oracle.OracleDomainDiscoveryProvider;
import io.micronaut.jsonschema.registry.oracle.OracleDomainMaterializer;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime configuration for JSON Schema Registry reconciliation.
 *
 * @since 2.0.0
 */
@ConfigurationProperties(JsonSchemaRegistryConfiguration.PREFIX)
public final class JsonSchemaRegistryConfiguration {
    /**
     * Configuration prefix.
     */
    public static final String PREFIX = "json-schema.registry";

    private boolean enabled;
    private JsonSchemaRegistryAuthority authority = JsonSchemaRegistryAuthority.APPLICATION;
    private boolean dryRun;
    private boolean failFast = true;
    private List<Mapping> mappings = List.of();
    private NamingConfiguration naming = new NamingConfiguration();
    private SrConfiguration sr = new SrConfiguration();
    private OracleConfiguration oracle = new OracleConfiguration();

    /**
     * @return Whether registry reconciliation is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @param enabled Whether registry reconciliation is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return The authority mode
     */
    public JsonSchemaRegistryAuthority getAuthority() {
        return authority;
    }

    /**
     * @param authority The authority mode
     */
    public void setAuthority(@Nullable JsonSchemaRegistryAuthority authority) {
        this.authority = authority == null ? JsonSchemaRegistryAuthority.APPLICATION : authority;
    }

    /**
     * @return Whether write operations should be previewed only
     */
    public boolean isDryRun() {
        return dryRun;
    }

    /**
     * @param dryRun Whether write operations should be previewed only
     */
    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    /**
     * @return Whether reconciliation failures should apply fail-fast behavior
     */
    public boolean isFailFast() {
        return failFast;
    }

    /**
     * @param failFast Whether reconciliation failures should apply fail-fast behavior
     */
    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    /**
     * @return Built-in domain pairing overrides
     */
    public List<Mapping> getMappings() {
        return mappings;
    }

    /**
     * @param mappings Built-in domain pairing overrides
     */
    public void setMappings(@Nullable List<Mapping> mappings) {
        this.mappings = mappings == null ? List.of() : List.copyOf(mappings);
    }

    /**
     * Apply nested configuration keys that are part of the public specification.
     *
     * @param environment The Micronaut environment
     */
    @Inject
    void readNestedProperties(Environment environment) {
        environment.getProperty(PREFIX + ".naming.subject.prefix", String.class).ifPresent(naming::setSubjectPrefix);
        environment.getProperty(PREFIX + ".naming.domain.prefix", String.class).ifPresent(naming::setDomainPrefix);

        environment.getProperty(PREFIX + ".sr.enabled", Boolean.class).ifPresent(sr::setEnabled);
        environment.getProperty(PREFIX + ".sr.url", String.class).ifPresent(sr::setUrl);
        environment.getProperty(PREFIX + ".sr.subjects", Argument.listOf(String.class)).ifPresent(sr::setSubjects);
        environment.getProperty(PREFIX + ".sr.policy.mode", JsonSchemaRegistryPolicyMode.class).ifPresent(sr.getPolicy()::setMode);

        environment.getProperty(PREFIX + ".oracle.enabled", Boolean.class).ifPresent(oracle::setEnabled);
        environment.getProperty(PREFIX + ".oracle.datasource", String.class).ifPresent(oracle::setDatasource);
        environment.getProperty(PREFIX + ".oracle.domains", Argument.listOf(String.class)).ifPresent(oracle::setDomains);
        environment.getProperty(PREFIX + ".oracle.policy.mode", JsonSchemaRegistryPolicyMode.class).ifPresent(oracle.getPolicy()::setMode);
        environment.getProperty(PREFIX + ".oracle.drift.mode", JsonSchemaRegistryDriftMode.class).ifPresent(oracle.getDrift()::setMode);
        environment.getProperty(PREFIX + ".oracle.authority.providers", Argument.listOf(ProviderConfiguration.class))
            .ifPresent(oracle.getAuthority()::setProviders);
        environment.getProperty(PREFIX + ".oracle.materializers", Argument.listOf(ProviderConfiguration.class))
            .ifPresent(oracle::setMaterializers);
    }

    /**
     * @return Naming configuration
     */
    public NamingConfiguration getNaming() {
        return naming;
    }

    /**
     * @param naming Naming configuration
     */
    public void setNaming(@Nullable NamingConfiguration naming) {
        this.naming = naming == null ? new NamingConfiguration() : naming;
    }

    /**
     * @return Schema Registry configuration
     */
    public SrConfiguration getSr() {
        return sr;
    }

    /**
     * @param sr Schema Registry configuration
     */
    public void setSr(@Nullable SrConfiguration sr) {
        this.sr = sr == null ? new SrConfiguration() : sr;
    }

    /**
     * @return Oracle configuration
     */
    public OracleConfiguration getOracle() {
        return oracle;
    }

    /**
     * @param oracle Oracle configuration
     */
    public void setOracle(@Nullable OracleConfiguration oracle) {
        this.oracle = oracle == null ? new OracleConfiguration() : oracle;
    }

    List<ProviderConfiguration> resolveOracleAuthorityProviders() {
        List<ProviderConfiguration> configured = oracle.getAuthority().getProviders();
        if (!configured.isEmpty()) {
            return configured;
        }
        ProviderConfiguration provider = new ProviderConfiguration();
        provider.setName("domains");
        provider.setProviderClassName(OracleDomainDiscoveryProvider.class.getName());
        if (!oracle.getDomains().isEmpty()) {
            provider.setOptions(Map.of("include", String.join(",", oracle.getDomains())));
        }
        return List.of(provider);
    }

    List<ProviderConfiguration> resolveOracleMaterializers() {
        List<ProviderConfiguration> configured = oracle.getMaterializers();
        if (!configured.isEmpty()) {
            return configured;
        }
        ProviderConfiguration provider = new ProviderConfiguration();
        provider.setName("domains");
        provider.setProviderClassName(OracleDomainMaterializer.class.getName());
        return List.of(provider);
    }

    Map<String, Mapping> mappingsByDomain() {
        Map<String, Mapping> result = new LinkedHashMap<>();
        for (Mapping mapping : mappings) {
            if (mapping.getDomain() != null) {
                result.put(mapping.getDomain().toUpperCase(java.util.Locale.ENGLISH), mapping);
            }
        }
        return result;
    }

    Map<String, Mapping> mappingsBySubject() {
        Map<String, Mapping> result = new LinkedHashMap<>();
        for (Mapping mapping : mappings) {
            if (mapping.getSubject() != null) {
                result.put(mapping.getSubject(), mapping);
            }
        }
        return result;
    }

    /**
     * Explicit mapping for the built-in Oracle domain provider/materializer.
     */
    @Introspected
    public static final class Mapping {
        private String subject;
        private String domain;

        /**
         * @return Schema Registry subject
         */
        @Nullable
        public String getSubject() {
            return subject;
        }

        /**
         * @param subject Schema Registry subject
         */
        public void setSubject(@Nullable String subject) {
            this.subject = subject;
        }

        /**
         * @return Oracle domain name
         */
        @Nullable
        public String getDomain() {
            return domain;
        }

        /**
         * @param domain Oracle domain name
         */
        public void setDomain(@Nullable String domain) {
            this.domain = domain;
        }
    }

    /**
     * Naming configuration.
     */
    @Introspected
    public static final class NamingConfiguration {
        private String subjectPrefix = "";
        private String domainPrefix = "APP_";

        /**
         * @return Schema Registry subject prefix
         */
        public String getSubjectPrefix() {
            return subjectPrefix;
        }

        /**
         * @param subjectPrefix Schema Registry subject prefix
         */
        public void setSubjectPrefix(@Nullable String subjectPrefix) {
            this.subjectPrefix = subjectPrefix == null ? "" : subjectPrefix;
        }

        /**
         * @return Oracle domain prefix
         */
        public String getDomainPrefix() {
            return domainPrefix;
        }

        /**
         * @param domainPrefix Oracle domain prefix
         */
        public void setDomainPrefix(@Nullable String domainPrefix) {
            this.domainPrefix = domainPrefix == null ? "" : domainPrefix;
        }
    }

    /**
     * Schema Registry target configuration.
     */
    @Introspected
    public static final class SrConfiguration {
        private boolean enabled;
        private String url = "http://localhost:8081";
        private List<String> subjects = List.of();
        private TargetPolicyConfiguration policy = new TargetPolicyConfiguration();

        /**
         * @return Whether Schema Registry is enabled
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * @param enabled Whether Schema Registry is enabled
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * @return Schema Registry URL
         */
        public String getUrl() {
            return url;
        }

        /**
         * @param url Schema Registry URL
         */
        public void setUrl(@Nullable String url) {
            this.url = url == null ? "http://localhost:8081" : url;
        }

        /**
         * @return Explicit subjects
         */
        public List<String> getSubjects() {
            return subjects;
        }

        /**
         * @param subjects Explicit subjects
         */
        public void setSubjects(@Nullable List<String> subjects) {
            this.subjects = subjects == null ? List.of() : List.copyOf(subjects);
        }

        /**
         * @return Target policy
         */
        public TargetPolicyConfiguration getPolicy() {
            return policy;
        }

        /**
         * @param policy Target policy
         */
        public void setPolicy(@Nullable TargetPolicyConfiguration policy) {
            this.policy = policy == null ? new TargetPolicyConfiguration() : policy;
        }
    }

    /**
     * Oracle authority and non-authoritative target configuration.
     */
    @Introspected
    public static final class OracleConfiguration {
        private boolean enabled;
        private String datasource = "default";
        private List<String> domains = List.of();
        private TargetPolicyConfiguration policy = new TargetPolicyConfiguration();
        private DriftConfiguration drift = new DriftConfiguration();
        private AuthorityConfiguration authority = new AuthorityConfiguration();
        private List<ProviderConfiguration> materializers = List.of();

        /**
         * @return Whether Oracle is enabled
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * @param enabled Whether Oracle is enabled
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * @return DataSource bean name
         */
        public String getDatasource() {
            return datasource;
        }

        /**
         * @param datasource DataSource bean name
         */
        public void setDatasource(@Nullable String datasource) {
            this.datasource = datasource == null ? "default" : datasource;
        }

        /**
         * @return Explicit selected domain set for built-in domain authority discovery
         */
        public List<String> getDomains() {
            return domains;
        }

        /**
         * @param domains Explicit selected domain set for built-in domain authority discovery
         */
        public void setDomains(@Nullable List<String> domains) {
            this.domains = domains == null ? List.of() : List.copyOf(domains);
        }

        /**
         * @return Target policy
         */
        public TargetPolicyConfiguration getPolicy() {
            return policy;
        }

        /**
         * @param policy Target policy
         */
        public void setPolicy(@Nullable TargetPolicyConfiguration policy) {
            this.policy = policy == null ? new TargetPolicyConfiguration() : policy;
        }

        /**
         * @return Drift handling configuration
         */
        public DriftConfiguration getDrift() {
            return drift;
        }

        /**
         * @param drift Drift handling configuration
         */
        public void setDrift(@Nullable DriftConfiguration drift) {
            this.drift = drift == null ? new DriftConfiguration() : drift;
        }

        /**
         * @return Oracle authority discovery configuration
         */
        public AuthorityConfiguration getAuthority() {
            return authority;
        }

        /**
         * @param authority Oracle authority discovery configuration
         */
        public void setAuthority(@Nullable AuthorityConfiguration authority) {
            this.authority = authority == null ? new AuthorityConfiguration() : authority;
        }

        /**
         * @return Oracle materializer configuration
         */
        public List<ProviderConfiguration> getMaterializers() {
            return materializers;
        }

        /**
         * @param materializers Oracle materializer configuration
         */
        public void setMaterializers(@Nullable List<ProviderConfiguration> materializers) {
            this.materializers = materializers == null ? List.of() : List.copyOf(materializers);
        }
    }

    /**
     * Authority provider list holder.
     */
    @Introspected
    public static final class AuthorityConfiguration {
        private List<ProviderConfiguration> providers = List.of();

        /**
         * @return Authority discovery providers
         */
        public List<ProviderConfiguration> getProviders() {
            return providers;
        }

        /**
         * @param providers Authority discovery providers
         */
        public void setProviders(@Nullable List<ProviderConfiguration> providers) {
            this.providers = providers == null ? List.of() : List.copyOf(providers);
        }
    }

    /**
     * Shared target policy configuration.
     */
    @Introspected
    public static final class TargetPolicyConfiguration {
        private JsonSchemaRegistryPolicyMode mode = JsonSchemaRegistryPolicyMode.MANAGE;

        /**
         * @return Policy mode
         */
        public JsonSchemaRegistryPolicyMode getMode() {
            return mode;
        }

        /**
         * @param mode Policy mode
         */
        public void setMode(@Nullable JsonSchemaRegistryPolicyMode mode) {
            this.mode = mode == null ? JsonSchemaRegistryPolicyMode.MANAGE : mode;
        }
    }

    /**
     * Drift handling configuration.
     */
    @Introspected
    public static final class DriftConfiguration {
        private JsonSchemaRegistryDriftMode mode = JsonSchemaRegistryDriftMode.REPORT;

        /**
         * @return Drift mode
         */
        public JsonSchemaRegistryDriftMode getMode() {
            return mode;
        }

        /**
         * @param mode Drift mode
         */
        public void setMode(@Nullable JsonSchemaRegistryDriftMode mode) {
            this.mode = mode == null ? JsonSchemaRegistryDriftMode.REPORT : mode;
        }
    }

    /**
     * Provider or materializer configuration.
     */
    @Introspected
    public static final class ProviderConfiguration {
        private String name;
        private String providerClassName;
        private String owner;
        private Map<String, String> options = Map.of();

        /**
         * @return Provider name
         */
        @Nullable
        public String getName() {
            return name;
        }

        /**
         * @param name Provider name
         */
        public void setName(@Nullable String name) {
            this.name = name;
        }

        /**
         * @return Provider implementation class name
         */
        @Nullable
        public String getProviderClassName() {
            return providerClassName;
        }

        /**
         * @param providerClassName Provider implementation class name
         */
        public void setProviderClassName(@Nullable String providerClassName) {
            this.providerClassName = providerClassName;
        }

        /**
         * @return Optional Oracle owner/schema
         */
        @Nullable
        public String getOwner() {
            return owner;
        }

        /**
         * @param owner Optional Oracle owner/schema
         */
        public void setOwner(@Nullable String owner) {
            this.owner = owner;
        }

        /**
         * @return Provider options
         */
        public Map<String, String> getOptions() {
            return options;
        }

        /**
         * @param options Provider options
         */
        public void setOptions(@Nullable Map<String, String> options) {
            this.options = options == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(options));
        }
    }

}
