package com.z254.butterfly.common.security;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Unified security principal for the BUTTERFLY platform.
 * <p>
 * Represents an authenticated user or service with:
 * <ul>
 *   <li>Unique subject identifier</li>
 *   <li>Tenant context for multi-tenancy</li>
 *   <li>Ecosystem-wide roles ({@link ButterflyRole})</li>
 *   <li>Fine-grained scopes ({@link ButterflyScope})</li>
 *   <li>Optional metadata for additional context</li>
 * </ul>
 * <p>
 * This principal is propagated across service boundaries via JWT claims
 * and Kafka headers to maintain consistent identity throughout request flows.
 */
public final class ButterflyPrincipal implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String DEFAULT_TENANT = "global";
    public static final String SYSTEM_SUBJECT = "system";

    private final String subject;
    private final String tenantId;
    private final Set<ButterflyRole> roles;
    private final Set<ButterflyScope> scopes;
    private final String issuer;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private final Map<String, Object> metadata;

    private ButterflyPrincipal(Builder builder) {
        this.subject = Objects.requireNonNull(builder.subject, "Subject cannot be null");
        this.tenantId = builder.tenantId != null ? builder.tenantId : DEFAULT_TENANT;
        this.roles = builder.roles != null ? Set.copyOf(builder.roles) : Set.of();
        this.scopes = builder.scopes != null ? Set.copyOf(builder.scopes) : Set.of();
        this.issuer = builder.issuer;
        this.issuedAt = builder.issuedAt;
        this.expiresAt = builder.expiresAt;
        this.metadata = builder.metadata != null ? Map.copyOf(builder.metadata) : Map.of();
    }

    /**
     * Create a new builder for constructing a ButterflyPrincipal.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a system principal for internal service operations.
     */
    public static ButterflyPrincipal system() {
        return builder()
                .subject(SYSTEM_SUBJECT)
                .tenantId(DEFAULT_TENANT)
                .role(ButterflyRole.SERVICE)
                .scope(ButterflyScope.wildcard())
                .issuer("butterfly-internal")
                .build();
    }

    /**
     * Create a system principal for a specific service.
     */
    public static ButterflyPrincipal forService(String serviceName) {
        return builder()
                .subject("service:" + serviceName)
                .tenantId(DEFAULT_TENANT)
                .role(ButterflyRole.SERVICE)
                .scope(ButterflyScope.of(serviceName, "*", "*"))
                .issuer("butterfly-internal")
                .build();
    }

    /**
     * Create an anonymous/unauthenticated principal.
     */
    public static ButterflyPrincipal anonymous() {
        return builder()
                .subject("anonymous")
                .tenantId(DEFAULT_TENANT)
                .role(ButterflyRole.VIEWER)
                .build();
    }

    // === Authorization checks ===

    /**
     * Check if the principal has a specific role.
     */
    public boolean hasRole(ButterflyRole role) {
        return roles.contains(role) || roles.stream().anyMatch(r -> r.hasAtLeast(role));
    }

    /**
     * Check if the principal has any of the specified roles.
     */
    public boolean hasAnyRole(ButterflyRole... rolesToCheck) {
        for (ButterflyRole role : rolesToCheck) {
            if (hasRole(role)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the principal has all of the specified roles.
     */
    public boolean hasAllRoles(ButterflyRole... rolesToCheck) {
        for (ButterflyRole role : rolesToCheck) {
            if (!hasRole(role)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if the principal has a specific scope.
     */
    public boolean hasScope(ButterflyScope scope) {
        return scopes.stream().anyMatch(s -> s.implies(scope));
    }

    /**
     * Check if the principal has a scope matching the string.
     */
    public boolean hasScope(String scopeString) {
        ButterflyScope scope = ButterflyScope.parseOrNull(scopeString);
        return scope != null && hasScope(scope);
    }

    /**
     * Check if the principal can access a specific service.
     */
    public boolean canAccessService(String serviceName) {
        return scopes.stream().anyMatch(s -> s.isForService(serviceName));
    }

    /**
     * Check if the principal can perform a specific action on a resource.
     */
    public boolean canPerform(String service, String resource, String action) {
        ButterflyScope required = ButterflyScope.of(service, resource, action);
        return hasScope(required);
    }

    /**
     * Check if the principal is a governed actor subject to governance policies.
     */
    public boolean isGovernedActor() {
        return hasRole(ButterflyRole.GOVERNED_ACTOR);
    }

    /**
     * Check if the principal requires governance approval for write operations.
     */
    public boolean requiresGovernanceApproval() {
        return roles.stream().anyMatch(ButterflyRole::requiresGovernanceApproval);
    }

    /**
     * Check if the principal can approve governance workflows.
     */
    public boolean canApprove() {
        return roles.stream().anyMatch(ButterflyRole::canApprove);
    }

    /**
     * Check if the principal has admin access.
     */
    public boolean isAdmin() {
        return hasRole(ButterflyRole.ADMIN);
    }

    /**
     * Check if the principal is a service account.
     */
    public boolean isServiceAccount() {
        return hasRole(ButterflyRole.SERVICE) || subject.startsWith("service:");
    }

    /**
     * Check if the principal belongs to a specific tenant.
     */
    public boolean belongsToTenant(String tenant) {
        return DEFAULT_TENANT.equals(tenantId) || // Global tenant can access all
               tenantId.equals(tenant);
    }

    /**
     * Check if the principal's token has expired.
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Check if the principal is valid (not expired, has subject).
     */
    public boolean isValid() {
        return subject != null && !subject.isBlank() && !isExpired();
    }

    // === Conversion methods ===

    /**
     * Get all authorities for Spring Security integration.
     * Returns both role-based and scope-based authorities.
     */
    public Set<String> getAuthorities() {
        Set<String> authorities = new HashSet<>();
        roles.forEach(role -> authorities.add(role.toAuthority()));
        scopes.forEach(scope -> authorities.add(scope.toAuthority()));
        return Collections.unmodifiableSet(authorities);
    }

    /**
     * Get effective scopes including role-implied scopes.
     */
    public Set<ButterflyScope> getEffectiveScopes() {
        Set<ButterflyScope> effective = new HashSet<>(scopes);
        roles.forEach(role -> effective.addAll(role.getDefaultScopes()));
        return Collections.unmodifiableSet(effective);
    }

    /**
     * Get the highest role in the hierarchy.
     */
    public ButterflyRole getHighestRole() {
        return roles.stream()
                .max((r1, r2) -> Integer.compare(r1.getHierarchyLevel(), r2.getHierarchyLevel()))
                .orElse(ButterflyRole.VIEWER);
    }

    // === Getters ===

    public String getSubject() {
        return subject;
    }

    public String getTenantId() {
        return tenantId;
    }

    public Set<ButterflyRole> getRoles() {
        return roles;
    }

    public Set<ButterflyScope> getScopes() {
        return scopes;
    }

    public String getIssuer() {
        return issuer;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Get a metadata value by key.
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, Class<T> type) {
        Object value = metadata.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    @Override
    public String toString() {
        return "ButterflyPrincipal{" +
                "subject='" + subject + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", roles=" + roles.stream().map(ButterflyRole::getCode).collect(Collectors.joining(",")) +
                ", scopeCount=" + scopes.size() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ButterflyPrincipal that = (ButterflyPrincipal) o;
        return subject.equals(that.subject) && tenantId.equals(that.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subject, tenantId);
    }

    // === Builder ===

    public static class Builder {
        private String subject;
        private String tenantId;
        private Set<ButterflyRole> roles = new HashSet<>();
        private Set<ButterflyScope> scopes = new HashSet<>();
        private String issuer;
        private Instant issuedAt;
        private Instant expiresAt;
        private Map<String, Object> metadata;

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder roles(Set<ButterflyRole> roles) {
            this.roles = new HashSet<>(roles);
            return this;
        }

        public Builder role(ButterflyRole role) {
            this.roles.add(role);
            return this;
        }

        public Builder roles(Collection<String> roleStrings) {
            roleStrings.forEach(r -> {
                ButterflyRole role = ButterflyRole.fromCodeOrNull(r);
                if (role == null) {
                    role = ButterflyRole.fromLegacyRole(r);
                }
                this.roles.add(role);
            });
            return this;
        }

        public Builder scopes(Set<ButterflyScope> scopes) {
            this.scopes = new HashSet<>(scopes);
            return this;
        }

        public Builder scope(ButterflyScope scope) {
            this.scopes.add(scope);
            return this;
        }

        public Builder scopes(Collection<String> scopeStrings) {
            scopeStrings.forEach(s -> {
                ButterflyScope scope = ButterflyScope.parseOrNull(s);
                if (scope != null) {
                    this.scopes.add(scope);
                }
            });
            return this;
        }

        public Builder issuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        public Builder issuedAt(Instant issuedAt) {
            this.issuedAt = issuedAt;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public ButterflyPrincipal build() {
            return new ButterflyPrincipal(this);
        }
    }
}

