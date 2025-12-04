package com.z254.butterfly.common.security;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Standardized JWT claims extraction for the BUTTERFLY platform.
 * <p>
 * Extracts and normalizes claims from various JWT formats (internal, Keycloak, Auth0, etc.)
 * into a consistent {@link ButterflyPrincipal}.
 * <p>
 * Supported claim names:
 * <ul>
 *   <li>Subject: sub</li>
 *   <li>Tenant: tenant, tenant_id, tenantId, org, organization</li>
 *   <li>Roles: roles, realm_access.roles, resource_access.*.roles, groups</li>
 *   <li>Scopes: scope, scopes, permissions</li>
 * </ul>
 */
public final class JwtClaimsExtractor {

    // Standard JWT claims
    public static final String CLAIM_SUBJECT = "sub";
    public static final String CLAIM_ISSUER = "iss";
    public static final String CLAIM_AUDIENCE = "aud";
    public static final String CLAIM_EXPIRATION = "exp";
    public static final String CLAIM_ISSUED_AT = "iat";

    // Tenant claim names (in order of precedence)
    private static final List<String> TENANT_CLAIMS = List.of(
            "tenant", "tenant_id", "tenantId", "org", "organization", "org_id"
    );

    // Role claim names (in order of precedence)
    private static final List<String> ROLE_CLAIMS = List.of(
            "roles", "role", "groups", "authorities"
    );

    // Scope claim names
    private static final List<String> SCOPE_CLAIMS = List.of(
            "scope", "scopes", "permissions", "scp"
    );

    // Keycloak-specific claim paths
    private static final String KEYCLOAK_REALM_ACCESS = "realm_access";
    private static final String KEYCLOAK_RESOURCE_ACCESS = "resource_access";

    private JwtClaimsExtractor() {
        // Utility class
    }

    /**
     * Extract a ButterflyPrincipal from JWT claims.
     *
     * @param claims the JWT claims as a map
     * @return the extracted principal
     */
    public static ButterflyPrincipal extractPrincipal(Map<String, Object> claims) {
        if (claims == null || claims.isEmpty()) {
            return ButterflyPrincipal.anonymous();
        }

        String subject = extractString(claims, CLAIM_SUBJECT);
        if (subject == null || subject.isBlank()) {
            return ButterflyPrincipal.anonymous();
        }

        return ButterflyPrincipal.builder()
                .subject(subject)
                .tenantId(extractTenant(claims))
                .roles(extractRoles(claims))
                .scopes(extractScopes(claims))
                .issuer(extractString(claims, CLAIM_ISSUER))
                .issuedAt(extractInstant(claims, CLAIM_ISSUED_AT))
                .expiresAt(extractInstant(claims, CLAIM_EXPIRATION))
                .metadata(extractMetadata(claims))
                .build();
    }

    /**
     * Extract tenant ID from claims.
     */
    public static String extractTenant(Map<String, Object> claims) {
        for (String claimName : TENANT_CLAIMS) {
            String value = extractString(claims, claimName);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return ButterflyPrincipal.DEFAULT_TENANT;
    }

    /**
     * Extract roles from claims (supports multiple formats).
     */
    public static Set<ButterflyRole> extractRoles(Map<String, Object> claims) {
        Set<String> roleStrings = new HashSet<>();

        // Try standard role claims
        for (String claimName : ROLE_CLAIMS) {
            Collection<String> roles = extractStringCollection(claims, claimName);
            if (roles != null) {
                roleStrings.addAll(roles);
            }
        }

        // Try Keycloak realm_access.roles
        roleStrings.addAll(extractKeycloakRealmRoles(claims));

        // Try Keycloak resource_access.*.roles
        roleStrings.addAll(extractKeycloakResourceRoles(claims));

        // Convert to ButterflyRole
        Set<ButterflyRole> roles = new HashSet<>();
        for (String roleString : roleStrings) {
            ButterflyRole role = ButterflyRole.fromCodeOrNull(roleString);
            if (role == null) {
                role = ButterflyRole.fromLegacyRole(roleString);
            }
            roles.add(role);
        }

        // Default to VIEWER if no roles found
        if (roles.isEmpty()) {
            roles.add(ButterflyRole.VIEWER);
        }

        return roles;
    }

    /**
     * Extract scopes from claims.
     */
    public static Set<ButterflyScope> extractScopes(Map<String, Object> claims) {
        Set<ButterflyScope> scopes = new HashSet<>();

        for (String claimName : SCOPE_CLAIMS) {
            Object value = claims.get(claimName);
            
            if (value instanceof String scopeString) {
                // Space-separated scopes (OAuth2 standard)
                for (String scope : scopeString.split("\\s+")) {
                    ButterflyScope parsed = ButterflyScope.parseOrNull(scope);
                    if (parsed != null) {
                        scopes.add(parsed);
                    } else {
                        // Try to interpret as simple scope (e.g., "read" -> "*:*:read")
                        scopes.add(interpretSimpleScope(scope));
                    }
                }
            } else if (value instanceof Collection<?> scopeCollection) {
                for (Object item : scopeCollection) {
                    if (item instanceof String scope) {
                        ButterflyScope parsed = ButterflyScope.parseOrNull(scope);
                        if (parsed != null) {
                            scopes.add(parsed);
                        } else {
                            scopes.add(interpretSimpleScope(scope));
                        }
                    }
                }
            }
        }

        return scopes;
    }

    /**
     * Interpret a simple scope string (e.g., "read", "write") as a ButterflyScope.
     */
    private static ButterflyScope interpretSimpleScope(String simpleScope) {
        if (simpleScope == null || simpleScope.isBlank()) {
            return ButterflyScope.of("*", "*", "read");
        }
        String normalized = simpleScope.trim().toLowerCase();
        return switch (normalized) {
            case "read", "read:all" -> ButterflyScope.of("*", "*", "read");
            case "write", "write:all" -> ButterflyScope.of("*", "*", "write");
            case "admin", "admin:all" -> ButterflyScope.of("*", "*", "admin");
            case "openid", "profile", "email" -> ButterflyScope.of("*", "user", "read");
            default -> {
                // Try to parse as "action:resource" format
                if (normalized.contains(":")) {
                    String[] parts = normalized.split(":");
                    if (parts.length == 2) {
                        yield ButterflyScope.of("*", parts[1], parts[0]);
                    }
                }
                yield ButterflyScope.of("*", "*", "read");
            }
        };
    }

    /**
     * Extract Keycloak realm_access.roles.
     */
    @SuppressWarnings("unchecked")
    private static Set<String> extractKeycloakRealmRoles(Map<String, Object> claims) {
        Object realmAccess = claims.get(KEYCLOAK_REALM_ACCESS);
        if (realmAccess instanceof Map<?, ?> realmMap) {
            Object roles = realmMap.get("roles");
            if (roles instanceof Collection<?> roleCollection) {
                Set<String> result = new HashSet<>();
                for (Object role : roleCollection) {
                    if (role instanceof String roleStr) {
                        result.add(roleStr);
                    }
                }
                return result;
            }
        }
        return Collections.emptySet();
    }

    /**
     * Extract Keycloak resource_access.*.roles for all resources.
     */
    @SuppressWarnings("unchecked")
    private static Set<String> extractKeycloakResourceRoles(Map<String, Object> claims) {
        Set<String> roles = new HashSet<>();
        Object resourceAccess = claims.get(KEYCLOAK_RESOURCE_ACCESS);
        
        if (resourceAccess instanceof Map<?, ?> resourceMap) {
            for (Object value : resourceMap.values()) {
                if (value instanceof Map<?, ?> resource) {
                    Object resourceRoles = resource.get("roles");
                    if (resourceRoles instanceof Collection<?> roleCollection) {
                        for (Object role : roleCollection) {
                            if (role instanceof String roleStr) {
                                roles.add(roleStr);
                            }
                        }
                    }
                }
            }
        }
        
        return roles;
    }

    /**
     * Extract additional metadata from claims.
     */
    private static Map<String, Object> extractMetadata(Map<String, Object> claims) {
        Map<String, Object> metadata = new HashMap<>();
        
        // Include email if present
        String email = extractString(claims, "email");
        if (email != null) {
            metadata.put("email", email);
        }
        
        // Include name if present
        String name = extractString(claims, "name");
        if (name != null) {
            metadata.put("name", name);
        }
        
        // Include preferred_username if present
        String username = extractString(claims, "preferred_username");
        if (username != null) {
            metadata.put("username", username);
        }
        
        // Include audience
        Object aud = claims.get(CLAIM_AUDIENCE);
        if (aud != null) {
            metadata.put("audience", aud);
        }
        
        return metadata;
    }

    // === Utility methods ===

    private static String extractString(Map<String, Object> claims, String claimName) {
        Object value = claims.get(claimName);
        if (value instanceof String str) {
            return str;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> extractStringCollection(Map<String, Object> claims, String claimName) {
        Object value = claims.get(claimName);
        
        if (value instanceof Collection<?> collection) {
            List<String> result = new ArrayList<>();
            for (Object item : collection) {
                if (item instanceof String str) {
                    result.add(str);
                }
            }
            return result;
        } else if (value instanceof String str) {
            // Single value or space-separated
            return List.of(str.split("\\s+"));
        }
        
        return null;
    }

    private static Instant extractInstant(Map<String, Object> claims, String claimName) {
        Object value = claims.get(claimName);
        
        if (value instanceof Instant instant) {
            return instant;
        } else if (value instanceof Long epochSeconds) {
            return Instant.ofEpochSecond(epochSeconds);
        } else if (value instanceof Integer epochSeconds) {
            return Instant.ofEpochSecond(epochSeconds);
        } else if (value instanceof Number num) {
            return Instant.ofEpochSecond(num.longValue());
        }
        
        return null;
    }
}

