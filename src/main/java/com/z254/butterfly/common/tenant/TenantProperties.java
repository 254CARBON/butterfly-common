package com.z254.butterfly.common.tenant;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for tenant context propagation across BUTTERFLY services.
 * <p>
 * This provides standardized tenant extraction configuration that can be used
 * by any BUTTERFLY service. CAPSULE remains the central tenant authority,
 * while other services use these properties for context propagation.
 *
 * <h2>Usage in application.yml</h2>
 * <pre>{@code
 * butterfly:
 *   tenant:
 *     enabled: true
 *     header-name: X-Tenant-ID
 *     query-param-name: tenantId
 *     jwt-claim-name: tenant_id
 *     default-tenant-id: default
 *     require-tenant-context: false
 *     validate-tenant-on-request: false
 * }</pre>
 */
@ConfigurationProperties(prefix = "butterfly.tenant")
public class TenantProperties {

    /**
     * Whether tenant context propagation is enabled.
     */
    private boolean enabled = true;

    /**
     * HTTP header name for tenant ID extraction.
     */
    private String headerName = "X-Tenant-ID";

    /**
     * Query parameter name for tenant ID extraction.
     */
    private String queryParamName = "tenantId";

    /**
     * JWT claim name containing the tenant ID.
     */
    private String jwtClaimName = "tenant_id";

    /**
     * Default tenant ID when none is provided.
     */
    private String defaultTenantId = "default";

    /**
     * Whether to require tenant context on all requests.
     * If true, requests without tenant context will be rejected.
     */
    private boolean requireTenantContext = false;

    /**
     * Whether to validate tenant exists via CAPSULE before processing.
     * Set to true only if the service has a CapsuleClient configured.
     */
    private boolean validateTenantOnRequest = false;

    /**
     * Kafka header name for tenant propagation.
     */
    private String kafkaHeaderName = "x-tenant-id";

    /**
     * Paths to exclude from tenant filtering (comma-separated patterns).
     */
    private String excludedPaths = "/actuator/**,/health/**,/swagger/**,/v3/api-docs/**";

    // Getters and setters

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getQueryParamName() {
        return queryParamName;
    }

    public void setQueryParamName(String queryParamName) {
        this.queryParamName = queryParamName;
    }

    public String getJwtClaimName() {
        return jwtClaimName;
    }

    public void setJwtClaimName(String jwtClaimName) {
        this.jwtClaimName = jwtClaimName;
    }

    public String getDefaultTenantId() {
        return defaultTenantId;
    }

    public void setDefaultTenantId(String defaultTenantId) {
        this.defaultTenantId = defaultTenantId;
    }

    public boolean isRequireTenantContext() {
        return requireTenantContext;
    }

    public void setRequireTenantContext(boolean requireTenantContext) {
        this.requireTenantContext = requireTenantContext;
    }

    public boolean isValidateTenantOnRequest() {
        return validateTenantOnRequest;
    }

    public void setValidateTenantOnRequest(boolean validateTenantOnRequest) {
        this.validateTenantOnRequest = validateTenantOnRequest;
    }

    public String getKafkaHeaderName() {
        return kafkaHeaderName;
    }

    public void setKafkaHeaderName(String kafkaHeaderName) {
        this.kafkaHeaderName = kafkaHeaderName;
    }

    public String getExcludedPaths() {
        return excludedPaths;
    }

    public void setExcludedPaths(String excludedPaths) {
        this.excludedPaths = excludedPaths;
    }

    /**
     * Get excluded path patterns as array.
     */
    public String[] getExcludedPathPatterns() {
        if (excludedPaths == null || excludedPaths.isBlank()) {
            return new String[0];
        }
        return excludedPaths.split(",");
    }
}

