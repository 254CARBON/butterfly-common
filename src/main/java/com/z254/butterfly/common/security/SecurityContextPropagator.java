package com.z254.butterfly.common.security;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Propagates security context (ButterflyPrincipal) across service boundaries.
 * <p>
 * Supports propagation via:
 * <ul>
 *   <li>HTTP headers for synchronous REST calls</li>
 *   <li>Kafka headers for asynchronous event processing</li>
 *   <li>MDC (Mapped Diagnostic Context) for logging correlation</li>
 * </ul>
 * <p>
 * Header format:
 * <ul>
 *   <li>{@code X-Butterfly-Subject}: The principal's subject ID</li>
 *   <li>{@code X-Butterfly-Tenant}: The tenant ID</li>
 *   <li>{@code X-Butterfly-Roles}: Comma-separated role codes</li>
 *   <li>{@code X-Butterfly-Scopes}: Comma-separated scope strings</li>
 *   <li>{@code X-Governance-Correlation-Id}: Governance audit correlation ID</li>
 * </ul>
 */
public final class SecurityContextPropagator {

    private static final Logger log = LoggerFactory.getLogger(SecurityContextPropagator.class);

    // HTTP/Kafka Header names
    public static final String HEADER_SUBJECT = "X-Butterfly-Subject";
    public static final String HEADER_TENANT = "X-Butterfly-Tenant";
    public static final String HEADER_ROLES = "X-Butterfly-Roles";
    public static final String HEADER_SCOPES = "X-Butterfly-Scopes";
    public static final String HEADER_ISSUER = "X-Butterfly-Issuer";
    public static final String HEADER_CORRELATION_ID = "X-Governance-Correlation-Id";
    public static final String HEADER_CAUSATION_ID = "X-Governance-Causation-Id";
    
    // Legacy header names (for backward compatibility)
    public static final String HEADER_TENANT_LEGACY = "X-Tenant-Id";
    public static final String HEADER_API_KEY = "X-API-Key";
    public static final String HEADER_AUTHORIZATION = "Authorization";

    // MDC keys
    public static final String MDC_SUBJECT = "principal.subject";
    public static final String MDC_TENANT = "tenantId";
    public static final String MDC_ROLES = "principal.roles";
    public static final String MDC_CORRELATION_ID = "governanceCorrelationId";
    public static final String MDC_CAUSATION_ID = "governanceCausationId";

    private SecurityContextPropagator() {
        // Utility class
    }

    // === HTTP Header Propagation ===

    /**
     * Convert a ButterflyPrincipal to HTTP headers for propagation.
     *
     * @param principal the principal to propagate
     * @return map of header names to values
     */
    public static Map<String, String> toHttpHeaders(ButterflyPrincipal principal) {
        Map<String, String> headers = new HashMap<>();
        
        if (principal == null) {
            return headers;
        }

        headers.put(HEADER_SUBJECT, principal.getSubject());
        headers.put(HEADER_TENANT, principal.getTenantId());
        headers.put(HEADER_TENANT_LEGACY, principal.getTenantId()); // Backward compat
        
        if (!principal.getRoles().isEmpty()) {
            String rolesStr = principal.getRoles().stream()
                    .map(ButterflyRole::getCode)
                    .collect(Collectors.joining(","));
            headers.put(HEADER_ROLES, rolesStr);
        }
        
        if (!principal.getScopes().isEmpty()) {
            String scopesStr = principal.getScopes().stream()
                    .map(ButterflyScope::getFullScope)
                    .collect(Collectors.joining(","));
            headers.put(HEADER_SCOPES, scopesStr);
        }
        
        if (principal.getIssuer() != null) {
            headers.put(HEADER_ISSUER, principal.getIssuer());
        }

        return headers;
    }

    /**
     * Extract a ButterflyPrincipal from HTTP headers.
     *
     * @param headers map of header names to values (case-insensitive lookup recommended)
     * @return the extracted principal, or anonymous if headers are missing
     */
    public static ButterflyPrincipal fromHttpHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return ButterflyPrincipal.anonymous();
        }

        String subject = getHeader(headers, HEADER_SUBJECT);
        if (subject == null || subject.isBlank()) {
            return ButterflyPrincipal.anonymous();
        }

        ButterflyPrincipal.Builder builder = ButterflyPrincipal.builder()
                .subject(subject)
                .tenantId(getHeader(headers, HEADER_TENANT, HEADER_TENANT_LEGACY))
                .issuer(getHeader(headers, HEADER_ISSUER));

        // Parse roles
        String rolesStr = getHeader(headers, HEADER_ROLES);
        if (rolesStr != null && !rolesStr.isBlank()) {
            for (String roleCode : rolesStr.split(",")) {
                ButterflyRole role = ButterflyRole.fromCodeOrNull(roleCode.trim());
                if (role != null) {
                    builder.role(role);
                }
            }
        }

        // Parse scopes
        String scopesStr = getHeader(headers, HEADER_SCOPES);
        if (scopesStr != null && !scopesStr.isBlank()) {
            for (String scopeStr : scopesStr.split(",")) {
                ButterflyScope scope = ButterflyScope.parseOrNull(scopeStr.trim());
                if (scope != null) {
                    builder.scope(scope);
                }
            }
        }

        return builder.build();
    }

    private static String getHeader(Map<String, String> headers, String... names) {
        for (String name : names) {
            String value = headers.get(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
            // Try case-insensitive
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    // === Kafka Header Propagation ===

    /**
     * Add security context headers to Kafka message headers.
     *
     * @param principal the principal to propagate
     * @param headers   the Kafka headers to populate
     */
    public static void toKafkaHeaders(ButterflyPrincipal principal, Headers headers) {
        if (principal == null || headers == null) {
            return;
        }

        addKafkaHeader(headers, HEADER_SUBJECT, principal.getSubject());
        addKafkaHeader(headers, HEADER_TENANT, principal.getTenantId());
        
        if (!principal.getRoles().isEmpty()) {
            String rolesStr = principal.getRoles().stream()
                    .map(ButterflyRole::getCode)
                    .collect(Collectors.joining(","));
            addKafkaHeader(headers, HEADER_ROLES, rolesStr);
        }
        
        if (!principal.getScopes().isEmpty()) {
            String scopesStr = principal.getScopes().stream()
                    .map(ButterflyScope::getFullScope)
                    .collect(Collectors.joining(","));
            addKafkaHeader(headers, HEADER_SCOPES, scopesStr);
        }

        if (principal.getIssuer() != null) {
            addKafkaHeader(headers, HEADER_ISSUER, principal.getIssuer());
        }

        // Propagate correlation ID from MDC if present
        String correlationId = MDC.get(MDC_CORRELATION_ID);
        if (correlationId != null) {
            addKafkaHeader(headers, HEADER_CORRELATION_ID, correlationId);
        }

        String causationId = MDC.get(MDC_CAUSATION_ID);
        if (causationId != null) {
            addKafkaHeader(headers, HEADER_CAUSATION_ID, causationId);
        }
    }

    /**
     * Extract a ButterflyPrincipal from Kafka message headers.
     *
     * @param headers the Kafka headers
     * @return the extracted principal, or anonymous if headers are missing
     */
    public static ButterflyPrincipal fromKafkaHeaders(Headers headers) {
        if (headers == null) {
            return ButterflyPrincipal.anonymous();
        }

        String subject = getKafkaHeader(headers, HEADER_SUBJECT);
        if (subject == null || subject.isBlank()) {
            return ButterflyPrincipal.anonymous();
        }

        ButterflyPrincipal.Builder builder = ButterflyPrincipal.builder()
                .subject(subject)
                .tenantId(getKafkaHeader(headers, HEADER_TENANT))
                .issuer(getKafkaHeader(headers, HEADER_ISSUER));

        // Parse roles
        String rolesStr = getKafkaHeader(headers, HEADER_ROLES);
        if (rolesStr != null && !rolesStr.isBlank()) {
            for (String roleCode : rolesStr.split(",")) {
                ButterflyRole role = ButterflyRole.fromCodeOrNull(roleCode.trim());
                if (role != null) {
                    builder.role(role);
                }
            }
        }

        // Parse scopes
        String scopesStr = getKafkaHeader(headers, HEADER_SCOPES);
        if (scopesStr != null && !scopesStr.isBlank()) {
            for (String scopeStr : scopesStr.split(",")) {
                ButterflyScope scope = ButterflyScope.parseOrNull(scopeStr.trim());
                if (scope != null) {
                    builder.scope(scope);
                }
            }
        }

        return builder.build();
    }

    /**
     * Extract the governance correlation ID from Kafka headers.
     */
    public static Optional<String> extractCorrelationId(Headers headers) {
        String value = getKafkaHeader(headers, HEADER_CORRELATION_ID);
        return Optional.ofNullable(value);
    }

    /**
     * Extract the governance causation ID from Kafka headers.
     */
    public static Optional<String> extractCausationId(Headers headers) {
        String value = getKafkaHeader(headers, HEADER_CAUSATION_ID);
        return Optional.ofNullable(value);
    }

    private static void addKafkaHeader(Headers headers, String name, String value) {
        if (value != null && !value.isBlank()) {
            headers.remove(name);
            headers.add(name, value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String getKafkaHeader(Headers headers, String name) {
        Header header = headers.lastHeader(name);
        if (header != null && header.value() != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        return null;
    }

    // === MDC Propagation ===

    /**
     * Set the ButterflyPrincipal in MDC for logging correlation.
     *
     * @param principal the principal to set
     */
    public static void setMdc(ButterflyPrincipal principal) {
        if (principal == null) {
            clearMdc();
            return;
        }

        MDC.put(MDC_SUBJECT, principal.getSubject());
        MDC.put(MDC_TENANT, principal.getTenantId());
        
        if (!principal.getRoles().isEmpty()) {
            String rolesStr = principal.getRoles().stream()
                    .map(ButterflyRole::getCode)
                    .collect(Collectors.joining(","));
            MDC.put(MDC_ROLES, rolesStr);
        }
    }

    /**
     * Set the governance correlation ID in MDC.
     */
    public static void setCorrelationId(String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put(MDC_CORRELATION_ID, correlationId);
        }
    }

    /**
     * Set the governance causation ID in MDC.
     */
    public static void setCausationId(String causationId) {
        if (causationId != null && !causationId.isBlank()) {
            MDC.put(MDC_CAUSATION_ID, causationId);
        }
    }

    /**
     * Get the current governance correlation ID from MDC.
     */
    public static Optional<String> getCorrelationId() {
        return Optional.ofNullable(MDC.get(MDC_CORRELATION_ID));
    }

    /**
     * Get the current governance causation ID from MDC.
     */
    public static Optional<String> getCausationId() {
        return Optional.ofNullable(MDC.get(MDC_CAUSATION_ID));
    }

    /**
     * Clear all security-related MDC entries.
     */
    public static void clearMdc() {
        MDC.remove(MDC_SUBJECT);
        MDC.remove(MDC_TENANT);
        MDC.remove(MDC_ROLES);
        MDC.remove(MDC_CORRELATION_ID);
        MDC.remove(MDC_CAUSATION_ID);
    }

    /**
     * Extract principal information from MDC (limited - for logging context only).
     */
    public static Optional<String> getSubjectFromMdc() {
        return Optional.ofNullable(MDC.get(MDC_SUBJECT));
    }

    /**
     * Extract tenant from MDC.
     */
    public static Optional<String> getTenantFromMdc() {
        return Optional.ofNullable(MDC.get(MDC_TENANT));
    }

    // === Context Management ===

    /**
     * Thread-local storage for the current principal.
     * Use this when Spring Security context is not available.
     */
    private static final ThreadLocal<ButterflyPrincipal> CURRENT_PRINCIPAL = new ThreadLocal<>();

    /**
     * Set the current principal for this thread.
     */
    public static void setCurrentPrincipal(ButterflyPrincipal principal) {
        if (principal != null) {
            CURRENT_PRINCIPAL.set(principal);
            setMdc(principal);
        } else {
            clearCurrentPrincipal();
        }
    }

    /**
     * Get the current principal for this thread.
     */
    public static Optional<ButterflyPrincipal> getCurrentPrincipal() {
        return Optional.ofNullable(CURRENT_PRINCIPAL.get());
    }

    /**
     * Clear the current principal from this thread.
     */
    public static void clearCurrentPrincipal() {
        CURRENT_PRINCIPAL.remove();
        clearMdc();
    }

    /**
     * Execute a runnable with a specific principal context.
     */
    public static void runAs(ButterflyPrincipal principal, Runnable action) {
        ButterflyPrincipal previous = CURRENT_PRINCIPAL.get();
        try {
            setCurrentPrincipal(principal);
            action.run();
        } finally {
            if (previous != null) {
                setCurrentPrincipal(previous);
            } else {
                clearCurrentPrincipal();
            }
        }
    }
}

