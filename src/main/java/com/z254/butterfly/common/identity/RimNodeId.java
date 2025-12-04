package com.z254.butterfly.common.identity;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Canonical RIM Node Identifier.
 * <p>
 * Format: {@code rim:{nodeType}:{namespace}:{localId}}
 * <p>
 * Examples:
 * <ul>
 *     <li>{@code rim:entity:finance:EURUSD}</li>
 *     <li>{@code rim:region:geo:emea}</li>
 *     <li>{@code rim:actor:trading:market-maker-1}</li>
 * </ul>
 * <p>
 * The RimNodeId is immutable and thread-safe. It supports parsing from string
 * representation and provides factory methods for construction.
 *
 * @see <a href="docs/IDENTITY_MODEL.md">Identity Model Documentation</a>
 */
public final class RimNodeId {

    private static final String PREFIX = "rim";
    private static final String DELIMITER = ":";
    
    /**
     * Valid node types in the RIM identity system.
     */
    private static final String[] VALID_NODE_TYPES = {
        "entity", "region", "actor", "system", "event", "metric", "model"
    };
    
    /**
     * Pattern for parsing RimNodeId strings.
     * Groups: 1=nodeType, 2=namespace, 3=localId
     */
    private static final Pattern PARSE_PATTERN = Pattern.compile(
        "^rim:([a-z]+):([a-zA-Z0-9_-]+):([a-zA-Z0-9_.-]+)$"
    );

    private final String nodeType;
    private final String namespace;
    private final String localId;
    private final String canonical;

    private RimNodeId(String nodeType, String namespace, String localId) {
        this.nodeType = Objects.requireNonNull(nodeType, "nodeType cannot be null");
        this.namespace = Objects.requireNonNull(namespace, "namespace cannot be null");
        this.localId = Objects.requireNonNull(localId, "localId cannot be null");
        this.canonical = PREFIX + DELIMITER + nodeType + DELIMITER + namespace + DELIMITER + localId;
    }

    /**
     * Creates a new RimNodeId with the specified components.
     *
     * @param nodeType  the type of node (e.g., "entity", "region", "actor")
     * @param namespace the namespace (e.g., "finance", "geo", "trading")
     * @param localId   the local identifier within the namespace
     * @return a new RimNodeId instance
     * @throws IllegalArgumentException if any component is invalid
     */
    public static RimNodeId of(String nodeType, String namespace, String localId) {
        validateNodeType(nodeType);
        validateNamespace(namespace);
        validateLocalId(localId);
        return new RimNodeId(nodeType, namespace, localId);
    }

    /**
     * Parses a RimNodeId from its canonical string representation.
     *
     * @param value the string to parse (format: rim:nodeType:namespace:localId)
     * @return the parsed RimNodeId
     * @throws IllegalArgumentException if the string is not a valid RimNodeId
     */
    public static RimNodeId parse(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("RimNodeId cannot be null or empty");
        }
        
        Matcher matcher = PARSE_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                "Invalid RimNodeId format: '" + value + "'. Expected: rim:{nodeType}:{namespace}:{localId}"
            );
        }
        
        String nodeType = matcher.group(1);
        String namespace = matcher.group(2);
        String localId = matcher.group(3);
        
        validateNodeType(nodeType);
        return new RimNodeId(nodeType, namespace, localId);
    }

    /**
     * Attempts to parse a RimNodeId, returning null if parsing fails.
     *
     * @param value the string to parse
     * @return the parsed RimNodeId, or null if parsing fails
     */
    public static RimNodeId tryParse(String value) {
        try {
            return parse(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void validateNodeType(String nodeType) {
        boolean valid = false;
        for (String validType : VALID_NODE_TYPES) {
            if (validType.equals(nodeType)) {
                valid = true;
                break;
            }
        }
        if (!valid) {
            throw new IllegalArgumentException(
                "Invalid node type: '" + nodeType + "'. Valid types: entity, region, actor, system, event, metric, model"
            );
        }
    }

    private static void validateNamespace(String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            throw new IllegalArgumentException("Namespace cannot be null or empty");
        }
        if (!namespace.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException(
                "Invalid namespace: '" + namespace + "'. Must contain only alphanumeric, underscore, or hyphen characters"
            );
        }
    }

    private static void validateLocalId(String localId) {
        if (localId == null || localId.isEmpty()) {
            throw new IllegalArgumentException("LocalId cannot be null or empty");
        }
        if (!localId.matches("^[a-zA-Z0-9_.-]+$")) {
            throw new IllegalArgumentException(
                "Invalid localId: '" + localId + "'. Must contain only alphanumeric, underscore, hyphen, or dot characters"
            );
        }
    }

    /**
     * @return the node type (e.g., "entity", "region", "actor")
     */
    public String getNodeType() {
        return nodeType;
    }

    /**
     * @return the namespace (e.g., "finance", "geo", "trading")
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * @return the local identifier within the namespace
     */
    public String getLocalId() {
        return localId;
    }

    /**
     * @return the canonical string representation (rim:nodeType:namespace:localId)
     */
    @Override
    public String toString() {
        return canonical;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RimNodeId rimNodeId = (RimNodeId) o;
        return canonical.equals(rimNodeId.canonical);
    }

    @Override
    public int hashCode() {
        return canonical.hashCode();
    }
}

