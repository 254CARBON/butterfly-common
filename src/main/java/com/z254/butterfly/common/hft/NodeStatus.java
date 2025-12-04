package com.z254.butterfly.common.hft;

/**
 * Health or operational status of a RIM node.
 * <p>
 * Used in high-frequency events to indicate the current state of a node
 * in the Reality Integration Mesh.
 */
public enum NodeStatus {
    /**
     * Node is operating normally.
     */
    OK,
    
    /**
     * Node has warnings that may require attention.
     */
    WARN,
    
    /**
     * Node is in an alert state requiring immediate attention.
     */
    ALERT,
    
    /**
     * Node status is unknown or cannot be determined.
     */
    UNKNOWN,
    
    /**
     * Node is offline or unreachable.
     */
    OFFLINE
}

