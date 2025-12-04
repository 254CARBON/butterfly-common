package com.z254.butterfly.common.tenant;

import com.z254.butterfly.common.security.JwtClaimsExtractor;
import com.z254.butterfly.common.telemetry.TenantContextHolder;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.util.AntPathMatcher;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Servlet filter for extracting and propagating tenant context from HTTP requests.
 * <p>
 * This filter is the standard way to establish tenant context in BUTTERFLY services.
 * It extracts tenant ID from multiple sources (in order of precedence):
 * <ol>
 *   <li>X-Tenant-ID header (or configured header name)</li>
 *   <li>tenantId query parameter (or configured parameter name)</li>
 *   <li>JWT token claims</li>
 *   <li>Default tenant ID (if configured)</li>
 * </ol>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @Configuration
 * @EnableConfigurationProperties(TenantProperties.class)
 * public class TenantConfig {
 *     @Bean
 *     public TenantFilter tenantFilter(TenantProperties properties) {
 *         return new TenantFilter(properties);
 *     }
 * }
 * }</pre>
 *
 * <h2>Integration with CAPSULE</h2>
 * <p>For services that need to validate tenants against CAPSULE,
 * implement a {@link TenantValidator} and pass it to the constructor.
 *
 * @see TenantContextHolder
 * @see TenantProperties
 */
@Order(10)
public class TenantFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(TenantFilter.class);

    private final TenantProperties properties;
    private final TenantValidator tenantValidator;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * Create a TenantFilter with default (no-op) validation.
     *
     * @param properties tenant configuration properties
     */
    public TenantFilter(TenantProperties properties) {
        this(properties, null);
    }

    /**
     * Create a TenantFilter with custom tenant validation.
     *
     * @param properties tenant configuration properties
     * @param tenantValidator optional validator for tenant verification
     */
    public TenantFilter(TenantProperties properties, TenantValidator tenantValidator) {
        this.properties = properties;
        this.tenantValidator = tenantValidator;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        try {
            // Skip for excluded paths
            String path = httpRequest.getRequestURI();
            if (isExcludedPath(path)) {
                chain.doFilter(request, response);
                return;
            }

            // Skip tenant resolution if disabled
            if (!properties.isEnabled()) {
                setDefaultContext(httpRequest);
                chain.doFilter(request, response);
                return;
            }

            // Extract tenant ID from various sources
            String tenantId = extractTenantId(httpRequest);

            if (tenantId != null && !tenantId.isBlank()) {
                // Validate tenant if validator is configured
                if (tenantValidator != null && properties.isValidateTenantOnRequest()) {
                    TenantValidator.ValidationResult result = tenantValidator.validate(tenantId);

                    if (!result.isValid()) {
                        log.warn("Request with invalid tenant: {} - {}", tenantId, result.message());
                        httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN, result.message());
                        return;
                    }
                }

                setTenantContext(tenantId, httpRequest);
                log.debug("Set tenant context: {}", tenantId);

            } else if (properties.isRequireTenantContext()) {
                // Tenant is required but not provided
                log.warn("Request without required tenant context: {}", path);
                httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST,
                        "Tenant context is required. Provide X-Tenant-ID header or tenantId parameter.");
                return;

            } else {
                // Use default tenant
                setDefaultContext(httpRequest);
                log.debug("Using default tenant context: {}", properties.getDefaultTenantId());
            }

            chain.doFilter(request, response);

        } finally {
            TenantContextHolder.clear();
        }
    }

    /**
     * Extract tenant ID from request using configured sources.
     */
    protected String extractTenantId(HttpServletRequest request) {
        // 1. Check custom header
        String tenantId = request.getHeader(properties.getHeaderName());
        if (tenantId != null && !tenantId.isBlank()) {
            return tenantId.trim();
        }

        // 2. Check query parameter
        tenantId = request.getParameter(properties.getQueryParamName());
        if (tenantId != null && !tenantId.isBlank()) {
            return tenantId.trim();
        }

        // 3. Extract from JWT token
        tenantId = extractTenantFromJwt(request);
        if (tenantId != null && !tenantId.isBlank()) {
            return tenantId.trim();
        }

        return null;
    }

    /**
     * Extract tenant ID from JWT Authorization header.
     */
    protected String extractTenantFromJwt(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Map<String, Object> claims = parseJwtClaims(token);
                return JwtClaimsExtractor.extractTenant(claims);
            } catch (Exception e) {
                log.trace("Could not extract tenant from JWT: {}", e.getMessage());
            }
        }
        return null;
    }

    /**
     * Parse JWT claims from token payload.
     * This is a lightweight parser that doesn't verify signatures.
     * Signature verification should be handled by Spring Security.
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> parseJwtClaims(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
                com.fasterxml.jackson.databind.ObjectMapper mapper = 
                    new com.fasterxml.jackson.databind.ObjectMapper();
                return mapper.readValue(payload, Map.class);
            }
        } catch (Exception e) {
            log.trace("Failed to parse JWT: {}", e.getMessage());
        }
        return Map.of();
    }

    /**
     * Set tenant context on TenantContextHolder.
     */
    protected void setTenantContext(String tenantId, HttpServletRequest request) {
        TenantContextHolder.setTenantId(tenantId);

        // Also set correlation ID if present
        String correlationId = request.getHeader("X-Correlation-ID");
        if (correlationId == null) {
            correlationId = request.getHeader("X-Request-ID");
        }
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        TenantContextHolder.setCorrelationId(correlationId);

        // Set request ID
        String requestId = request.getHeader("X-Request-ID");
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        TenantContextHolder.setRequestId(requestId);
    }

    /**
     * Set default tenant context.
     */
    protected void setDefaultContext(HttpServletRequest request) {
        setTenantContext(properties.getDefaultTenantId(), request);
    }

    /**
     * Check if path should be excluded from tenant filtering.
     */
    protected boolean isExcludedPath(String path) {
        for (String pattern : properties.getExcludedPathPatterns()) {
            if (pathMatcher.match(pattern.trim(), path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Validator interface for tenant verification.
     * Implement this to integrate with CAPSULE tenant service.
     */
    public interface TenantValidator {

        /**
         * Validate a tenant ID.
         *
         * @param tenantId the tenant ID to validate
         * @return validation result
         */
        ValidationResult validate(String tenantId);

        /**
         * Validation result record.
         */
        record ValidationResult(boolean isValid, String message) {
            public static ValidationResult valid() {
                return new ValidationResult(true, null);
            }

            public static ValidationResult invalid(String message) {
                return new ValidationResult(false, message);
            }
        }
    }
}

