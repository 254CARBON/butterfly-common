package com.z254.butterfly.common.resilience;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for the health state manager.
 */
@Data
@ConfigurationProperties(prefix = "butterfly.resilience")
public class HealthStateProperties {
    
    /**
     * List of service IDs to monitor for health.
     */
    private List<String> monitoredServices = new ArrayList<>(List.of(
            "capsule",
            "odyssey", 
            "perception",
            "plato",
            "nexus",
            "synapse"
    ));
    
    /**
     * How long to cache health state before recomputing (milliseconds).
     */
    private long healthCacheTtlMs = 5000;
    
    /**
     * How often to proactively refresh health states (milliseconds).
     */
    private long healthUpdateIntervalMs = 5000;
    
    /**
     * Error rate threshold for DEGRADED state (0.0 - 1.0).
     */
    private double errorRateDegraded = 0.01;
    
    /**
     * Error rate threshold for IMPAIRED state (0.0 - 1.0).
     */
    private double errorRateImpaired = 0.05;
    
    /**
     * Error rate threshold for UNAVAILABLE state (0.0 - 1.0).
     */
    private double errorRateUnavailable = 0.10;
    
    /**
     * P95 latency threshold for DEGRADED state (milliseconds).
     */
    private long latencyDegradedMs = 200;
    
    /**
     * P95 latency threshold for IMPAIRED state (milliseconds).
     */
    private long latencyImpairedMs = 500;
    
    /**
     * Consecutive failures threshold for IMPAIRED state.
     */
    private int consecutiveFailuresImpaired = 3;
    
    /**
     * Consecutive failures threshold for UNAVAILABLE state.
     */
    private int consecutiveFailuresUnavailable = 5;
    
    /**
     * Consecutive successes required before returning to HEALTHY.
     */
    private int consecutiveSuccessesForHealthy = 3;
    
    /**
     * Whether to enable automatic health state management.
     */
    private boolean enabled = true;
}

