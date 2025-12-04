package com.z254.butterfly.common.security;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ecosystem-wide roles for the BUTTERFLY platform.
 * <p>
 * These roles are standardized across all services (PERCEPTION, CAPSULE, ODYSSEY, PLATO, NEXUS, SYNAPSE)
 * to provide consistent authorization semantics throughout the platform.
 * <p>
 * Role hierarchy (highest to lowest):
 * <ol>
 *   <li>ADMIN - Full system access</li>
 *   <li>APPROVER - Governance approvers with override capabilities</li>
 *   <li>OPERATOR - Platform operators with monitoring access</li>
 *   <li>DEVELOPER - Service developers (non-prod write access)</li>
 *   <li>ANALYST - Human analysts with read and feedback access</li>
 *   <li>GOVERNED_ACTOR - Autonomous agents subject to governance</li>
 *   <li>SERVICE - Inter-service communication</li>
 *   <li>VIEWER - Read-only access</li>
 * </ol>
 */
public enum ButterflyRole {

    /**
     * Full system administrator with unrestricted access.
     * Can perform any operation across all services.
     */
    ADMIN("admin", "Full system access", 100),

    /**
     * Governance approvers who can approve deployments and override violations.
     * Critical for human-in-the-loop governance workflows.
     */
    APPROVER("approver", "Governance approvers", 90),

    /**
     * Platform operators with monitoring and operational access.
     * Can view all data and manage platform health.
     */
    OPERATOR("operator", "Platform operators", 80),

    /**
     * Service developers with write access to non-production environments.
     * Can create specs, artifacts, and plans in dev/staging.
     */
    DEVELOPER("developer", "Service developers", 70),

    /**
     * Human analysts with read access to intelligence and feedback capabilities.
     * Primary consumers of CAPSULE, PERCEPTION, and ODYSSEY data.
     */
    ANALYST("analyst", "Human analysts", 60),

    /**
     * Autonomous agents (AI/ML systems) that operate under governance constraints.
     * Can read all data but writes require approval gates or policy compliance.
     */
    GOVERNED_ACTOR("governed_actor", "Autonomous agents subject to governance", 50),

    /**
     * Service accounts for inter-service communication.
     * Scopes are determined by service-to-service contracts.
     */
    SERVICE("service", "Inter-service calls", 40),

    /**
     * Read-only access for viewing data without modification rights.
     */
    VIEWER("viewer", "Read-only access", 10);

    private final String code;
    private final String description;
    private final int hierarchyLevel;

    ButterflyRole(String code, String description, int hierarchyLevel) {
        this.code = code;
        this.description = description;
        this.hierarchyLevel = hierarchyLevel;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public int getHierarchyLevel() {
        return hierarchyLevel;
    }

    /**
     * Returns the Spring Security authority string for this role.
     * Format: ROLE_{ROLE_NAME}
     */
    public String toAuthority() {
        return "ROLE_" + name();
    }

    /**
     * Check if this role has at least the same level of access as another role.
     * Based on the hierarchy level where higher values indicate more privileges.
     */
    public boolean hasAtLeast(ButterflyRole other) {
        return this.hierarchyLevel >= other.hierarchyLevel;
    }

    /**
     * Get all roles that this role implies (i.e., roles with lower or equal hierarchy level).
     */
    public Set<ButterflyRole> getImpliedRoles() {
        return Arrays.stream(values())
                .filter(role -> this.hierarchyLevel >= role.hierarchyLevel)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ButterflyRole.class)));
    }

    /**
     * Parse a role from its code string (case-insensitive).
     *
     * @param code the role code (e.g., "admin", "ADMIN", "governed_actor")
     * @return the matching role
     * @throws IllegalArgumentException if no matching role is found
     */
    public static ButterflyRole fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Role code cannot be null or blank");
        }
        String normalized = code.trim().toLowerCase();
        for (ButterflyRole role : values()) {
            if (role.code.equals(normalized) || role.name().equalsIgnoreCase(normalized)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role code: " + code);
    }

    /**
     * Try to parse a role from its code string, returning null if not found.
     */
    public static ButterflyRole fromCodeOrNull(String code) {
        try {
            return fromCode(code);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Map legacy service-specific roles to ecosystem-wide roles.
     * <p>
     * Mappings:
     * <ul>
     *   <li>READER, VIEWER → VIEWER</li>
     *   <li>WRITER, USER → DEVELOPER</li>
     *   <li>ENGINEER → DEVELOPER</li>
     *   <li>ADMIN → ADMIN</li>
     *   <li>SERVICE → SERVICE</li>
     * </ul>
     */
    public static ButterflyRole fromLegacyRole(String legacyRole) {
        if (legacyRole == null || legacyRole.isBlank()) {
            return VIEWER;
        }
        String normalized = legacyRole.trim().toUpperCase();
        return switch (normalized) {
            case "READER", "VIEWER", "READ" -> VIEWER;
            case "WRITER", "USER", "WRITE" -> DEVELOPER;
            case "ENGINEER" -> DEVELOPER;
            case "ADMIN", "ADMINISTRATOR" -> ADMIN;
            case "SERVICE", "SYSTEM" -> SERVICE;
            case "OPERATOR", "OPS" -> OPERATOR;
            case "APPROVER", "APPROVAL" -> APPROVER;
            case "ANALYST", "ANALYSIS" -> ANALYST;
            case "GOVERNED_ACTOR", "AGENT", "BOT" -> GOVERNED_ACTOR;
            case "DEVELOPER", "DEV" -> DEVELOPER;
            default -> fromCodeOrNull(normalized) != null ? fromCode(normalized) : VIEWER;
        };
    }

    /**
     * Default scopes associated with each role.
     * These provide baseline permissions; actual scopes may be further restricted by context.
     */
    public Set<ButterflyScope> getDefaultScopes() {
        return switch (this) {
            case ADMIN -> ButterflyScope.all();
            case APPROVER -> Set.of(
                    ButterflyScope.of("*", "*", "read"),
                    ButterflyScope.of("plato", "governance", "approve"),
                    ButterflyScope.of("plato", "governance", "override"),
                    ButterflyScope.of("nexus", "evolution", "read")
            );
            case OPERATOR -> Set.of(
                    ButterflyScope.of("*", "*", "read"),
                    ButterflyScope.of("*", "monitoring", "admin"),
                    ButterflyScope.of("*", "health", "admin"),
                    ButterflyScope.of("plato", "plans", "execute")
            );
            case DEVELOPER -> Set.of(
                    ButterflyScope.of("*", "*", "read"),
                    ButterflyScope.of("plato", "specs", "write"),
                    ButterflyScope.of("plato", "artifacts", "write"),
                    ButterflyScope.of("plato", "plans", "write"),
                    ButterflyScope.of("perception", "signals", "write"),
                    ButterflyScope.of("perception", "scenarios", "write")
            );
            case ANALYST -> Set.of(
                    ButterflyScope.of("capsule", "*", "read"),
                    ButterflyScope.of("perception", "*", "read"),
                    ButterflyScope.of("odyssey", "*", "read"),
                    ButterflyScope.of("perception", "feedback", "write")
            );
            case GOVERNED_ACTOR -> Set.of(
                    ButterflyScope.of("*", "*", "read"),
                    ButterflyScope.governance()
            );
            case SERVICE -> Set.of(
                    ButterflyScope.of("*", "*", "read"),
                    ButterflyScope.of("*", "*", "write")
            );
            case VIEWER -> Set.of(
                    ButterflyScope.of("*", "*", "read")
            );
        };
    }

    /**
     * Check if this role requires governance approval for write operations.
     */
    public boolean requiresGovernanceApproval() {
        return this == GOVERNED_ACTOR;
    }

    /**
     * Check if this role can approve governance workflows.
     */
    public boolean canApprove() {
        return this == ADMIN || this == APPROVER;
    }

    /**
     * Check if this role has admin-level access.
     */
    public boolean isAdmin() {
        return this == ADMIN;
    }
}

