package com.z254.butterfly.common.resilience;

import lombok.Builder;
import lombok.Data;
import lombok.With;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the health status of a downstream service.
 * Combines multiple signals (circuit breaker state, latency, error rate, etc.)
 * into a comprehensive health assessment.
 */
@Data
@Builder
@With
public class ServiceHealth {
    
    /**
     * The service identifier (e.g., "capsule", "odyssey", "perception").
     */
    private final String serviceId;
    
    /**
     * Current degradation state based on combined health signals.
     */
    private final DegradationState state;
    
    /**
     * When this health assessment was made.
     */
    private final Instant lastUpdated;
    
    /**
     * Current error rate (0.0 - 1.0) over the evaluation window.
     */
    private final double errorRate;
    
    /**
     * Current P95 latency in milliseconds.
     */
    private final long latencyP95Ms;
    
    /**
     * Current P99 latency in milliseconds.
     */
    private final long latencyP99Ms;
    
    /**
     * Current throughput (requests per second).
     */
    private final double throughputRps;
    
    /**
     * Whether the circuit breaker is open.
     */
    private final boolean circuitBreakerOpen;
    
    /**
     * Number of consecutive failures.
     */
    private final int consecutiveFailures;
    
    /**
     * Number of consecutive successes.
     */
    private final int consecutiveSuccesses;
    
    /**
     * Additional health details (e.g., specific component health).
     */
    private final Map<String, Object> details;
    
    /**
     * Human-readable health message.
     */
    private final String message;
    
    /**
     * Create a healthy service health instance.
     */
    public static ServiceHealth healthy(String serviceId) {
        return ServiceHealth.builder()
                .serviceId(serviceId)
                .state(DegradationState.HEALTHY)
                .lastUpdated(Instant.now())
                .errorRate(0.0)
                .latencyP95Ms(0)
                .latencyP99Ms(0)
                .throughputRps(0)
                .circuitBreakerOpen(false)
                .consecutiveFailures(0)
                .consecutiveSuccesses(0)
                .message("Service is healthy")
                .build();
    }
    
    /**
     * Create an unknown health instance (e.g., during startup).
     */
    public static ServiceHealth unknown(String serviceId) {
        return ServiceHealth.builder()
                .serviceId(serviceId)
                .state(DegradationState.UNKNOWN)
                .lastUpdated(Instant.now())
                .errorRate(-1)
                .latencyP95Ms(-1)
                .latencyP99Ms(-1)
                .throughputRps(-1)
                .circuitBreakerOpen(false)
                .consecutiveFailures(0)
                .consecutiveSuccesses(0)
                .message("Service health is unknown")
                .build();
    }
    
    /**
     * Create an unavailable health instance.
     */
    public static ServiceHealth unavailable(String serviceId, String reason) {
        return ServiceHealth.builder()
                .serviceId(serviceId)
                .state(DegradationState.UNAVAILABLE)
                .lastUpdated(Instant.now())
                .errorRate(1.0)
                .latencyP95Ms(-1)
                .latencyP99Ms(-1)
                .throughputRps(0)
                .circuitBreakerOpen(true)
                .consecutiveFailures(-1)
                .consecutiveSuccesses(0)
                .message(reason)
                .build();
    }
    
    /**
     * Check if the service is operational.
     */
    public boolean isOperational() {
        return state != null && state.isOperational();
    }
    
    /**
     * Check if requests should be blocked to this service.
     */
    public boolean shouldBlockRequests() {
        return state != null && state.shouldBlockRequests();
    }
    
    /**
     * Check if only critical requests should be allowed (service is impaired).
     */
    public boolean shouldOnlyAllowCritical() {
        return state != null && state.shouldOnlyAllowCritical();
    }
    
    /**
     * Get the age of this health assessment in milliseconds.
     */
    public long getAgeMs() {
        return Objects.requireNonNullElse(lastUpdated, Instant.now())
                .until(Instant.now(), java.time.temporal.ChronoUnit.MILLIS);
    }
    
    /**
     * Check if this health assessment is stale (older than given threshold).
     */
    public boolean isStale(long maxAgeMs) {
        return getAgeMs() > maxAgeMs;
    }
}

