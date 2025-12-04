package com.z254.butterfly.common.resilience;

/**
 * Represents the degradation state of a service or component.
 * Used by {@link DegradationAwareClient} to make intelligent decisions
 * about how to handle requests based on downstream service health.
 */
public enum DegradationState {
    
    /**
     * Service is fully operational with normal performance.
     * All features are available.
     */
    HEALTHY("Service is operating normally", 0),
    
    /**
     * Service is experiencing minor issues but is mostly functional.
     * Some non-critical features may be degraded.
     * Consider reducing non-essential traffic.
     */
    DEGRADED("Service is partially degraded", 1),
    
    /**
     * Service is experiencing significant issues.
     * Only critical operations should be attempted.
     * Circuit breakers may be half-open.
     */
    IMPAIRED("Service is significantly impaired", 2),
    
    /**
     * Service is unavailable or failing.
     * Circuit breakers are open.
     * Fallback behavior should be used.
     */
    UNAVAILABLE("Service is unavailable", 3),
    
    /**
     * Service health is unknown.
     * Could be during startup or after communication failure.
     */
    UNKNOWN("Service health is unknown", -1);
    
    private final String description;
    private final int severity;
    
    DegradationState(String description, int severity) {
        this.description = description;
        this.severity = severity;
    }
    
    public String getDescription() {
        return description;
    }
    
    public int getSeverity() {
        return severity;
    }
    
    /**
     * Check if the service is operational (can handle requests).
     * @return true if state is HEALTHY or DEGRADED
     */
    public boolean isOperational() {
        return this == HEALTHY || this == DEGRADED;
    }
    
    /**
     * Check if requests should be blocked.
     * @return true if state is UNAVAILABLE
     */
    public boolean shouldBlockRequests() {
        return this == UNAVAILABLE;
    }
    
    /**
     * Check if only critical requests should be allowed.
     * @return true if state is IMPAIRED
     */
    public boolean shouldOnlyAllowCritical() {
        return this == IMPAIRED;
    }
    
    /**
     * Determine the worse of two degradation states.
     * @param other the other state to compare
     * @return the state with higher severity
     */
    public DegradationState worse(DegradationState other) {
        if (other == null || this.severity >= other.severity) {
            return this;
        }
        return other;
    }
    
    /**
     * Determine the better of two degradation states.
     * @param other the other state to compare
     * @return the state with lower severity
     */
    public DegradationState better(DegradationState other) {
        if (other == null || this.severity <= other.severity) {
            return this;
        }
        return other;
    }
}

