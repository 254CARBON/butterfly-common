package com.z254.butterfly.common.security;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Hierarchical permission scope for the BUTTERFLY platform.
 * <p>
 * Format: {@code {service}:{resource}:{action}}
 * <p>
 * Examples:
 * <ul>
 *   <li>{@code capsule:capsules:read} - Read CAPSULE capsules</li>
 *   <li>{@code odyssey:experiments:write} - Write ODYSSEY experiments</li>
 *   <li>{@code plato:governance:approve} - Approve PLATO governance workflows</li>
 *   <li>{@code *:*:read} - Read access to all resources in all services</li>
 *   <li>{@code perception:*:write} - Write access to all PERCEPTION resources</li>
 * </ul>
 * <p>
 * Wildcard (*) can be used for service, resource, or action to indicate "all".
 */
public final class ButterflyScope {

    public static final String WILDCARD = "*";
    public static final String DELIMITER = ":";

    private static final Pattern VALID_COMPONENT = Pattern.compile("^[a-z0-9_*-]+$");
    private static final Pattern SCOPE_PATTERN = Pattern.compile(
            "^[a-z0-9_*-]+:[a-z0-9_*-]+:[a-z0-9_*-]+$"
    );

    private final String service;
    private final String resource;
    private final String action;
    private final String fullScope;

    private ButterflyScope(String service, String resource, String action) {
        this.service = service.toLowerCase();
        this.resource = resource.toLowerCase();
        this.action = action.toLowerCase();
        this.fullScope = this.service + DELIMITER + this.resource + DELIMITER + this.action;
    }

    /**
     * Create a scope from individual components.
     *
     * @param service  the service name (e.g., "capsule", "odyssey", "*")
     * @param resource the resource type (e.g., "capsules", "experiments", "*")
     * @param action   the action (e.g., "read", "write", "admin", "*")
     * @return the scope instance
     */
    public static ButterflyScope of(String service, String resource, String action) {
        validateComponent("service", service);
        validateComponent("resource", resource);
        validateComponent("action", action);
        return new ButterflyScope(service, resource, action);
    }

    /**
     * Parse a scope string into a ButterflyScope.
     *
     * @param scopeString the scope string (e.g., "capsule:capsules:read")
     * @return the parsed scope
     * @throws IllegalArgumentException if the scope string is invalid
     */
    public static ButterflyScope parse(String scopeString) {
        if (scopeString == null || scopeString.isBlank()) {
            throw new IllegalArgumentException("Scope string cannot be null or blank");
        }
        String normalized = scopeString.trim().toLowerCase();
        if (!SCOPE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid scope format: " + scopeString + 
                    ". Expected format: service:resource:action");
        }
        String[] parts = normalized.split(DELIMITER);
        return new ButterflyScope(parts[0], parts[1], parts[2]);
    }

    /**
     * Try to parse a scope string, returning null if invalid.
     */
    public static ButterflyScope parseOrNull(String scopeString) {
        try {
            return parse(scopeString);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void validateComponent(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Scope " + name + " cannot be null or blank");
        }
        if (!VALID_COMPONENT.matcher(value.toLowerCase()).matches()) {
            throw new IllegalArgumentException("Invalid scope " + name + ": " + value + 
                    ". Only lowercase alphanumeric, underscore, hyphen, and * are allowed");
        }
    }

    // === Factory methods for common scopes ===

    /**
     * Create a wildcard scope that matches everything.
     */
    public static ButterflyScope wildcard() {
        return new ButterflyScope(WILDCARD, WILDCARD, WILDCARD);
    }

    /**
     * Get all scopes (equivalent to full admin access).
     */
    public static Set<ButterflyScope> all() {
        return Set.of(wildcard());
    }

    /**
     * Create a governance-specific scope for governed actors.
     * This scope indicates the actor is subject to governance policies.
     */
    public static ButterflyScope governance() {
        return of("plato", "governance", "subject");
    }

    // === Service-specific convenience methods ===

    public static ButterflyScope capsuleRead() {
        return of("capsule", WILDCARD, "read");
    }

    public static ButterflyScope capsuleWrite() {
        return of("capsule", WILDCARD, "write");
    }

    public static ButterflyScope perceptionRead() {
        return of("perception", WILDCARD, "read");
    }

    public static ButterflyScope perceptionWrite() {
        return of("perception", WILDCARD, "write");
    }

    public static ButterflyScope odysseyRead() {
        return of("odyssey", WILDCARD, "read");
    }

    public static ButterflyScope odysseyWrite() {
        return of("odyssey", WILDCARD, "write");
    }

    public static ButterflyScope platoRead() {
        return of("plato", WILDCARD, "read");
    }

    public static ButterflyScope platoWrite() {
        return of("plato", WILDCARD, "write");
    }

    public static ButterflyScope nexusRead() {
        return of("nexus", WILDCARD, "read");
    }

    public static ButterflyScope nexusWrite() {
        return of("nexus", WILDCARD, "write");
    }

    public static ButterflyScope synapseRead() {
        return of("synapse", WILDCARD, "read");
    }

    public static ButterflyScope synapseWrite() {
        return of("synapse", WILDCARD, "write");
    }

    // === Matching methods ===

    /**
     * Check if this scope implies (grants access to) another scope.
     * Wildcards match any value in that position.
     *
     * @param other the scope to check
     * @return true if this scope implies the other scope
     */
    public boolean implies(ButterflyScope other) {
        if (other == null) {
            return false;
        }
        return matches(this.service, other.service) &&
               matches(this.resource, other.resource) &&
               matches(this.action, other.action);
    }

    /**
     * Check if this scope implies a scope string.
     */
    public boolean implies(String scopeString) {
        ButterflyScope other = parseOrNull(scopeString);
        return other != null && implies(other);
    }

    private boolean matches(String pattern, String value) {
        return WILDCARD.equals(pattern) || pattern.equals(value);
    }

    /**
     * Check if any scope in the collection implies this scope.
     */
    public boolean isImpliedBy(Set<ButterflyScope> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return false;
        }
        return scopes.stream().anyMatch(scope -> scope.implies(this));
    }

    /**
     * Check if this scope is for a specific service.
     */
    public boolean isForService(String serviceName) {
        return WILDCARD.equals(this.service) || this.service.equalsIgnoreCase(serviceName);
    }

    /**
     * Check if this is a read-only scope.
     */
    public boolean isReadOnly() {
        return "read".equals(this.action);
    }

    /**
     * Check if this is a write scope.
     */
    public boolean isWrite() {
        return "write".equals(this.action);
    }

    /**
     * Check if this is an admin scope.
     */
    public boolean isAdmin() {
        return "admin".equals(this.action) || WILDCARD.equals(this.action);
    }

    // === Getters ===

    public String getService() {
        return service;
    }

    public String getResource() {
        return resource;
    }

    public String getAction() {
        return action;
    }

    /**
     * Get the full scope string representation.
     */
    public String getFullScope() {
        return fullScope;
    }

    /**
     * Convert to Spring Security authority format.
     * Format: SCOPE_{service}_{resource}_{action}
     */
    public String toAuthority() {
        return "SCOPE_" + fullScope.replace(DELIMITER, "_").toUpperCase();
    }

    @Override
    public String toString() {
        return fullScope;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ButterflyScope that = (ButterflyScope) o;
        return fullScope.equals(that.fullScope);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fullScope);
    }
}

