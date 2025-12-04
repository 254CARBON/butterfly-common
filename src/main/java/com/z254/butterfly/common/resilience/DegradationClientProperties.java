package com.z254.butterfly.common.resilience;

import lombok.Data;

/**
 * Configuration properties for DegradationAwareClient instances.
 */
@Data
public class DegradationClientProperties {
    
    /**
     * Request timeout in milliseconds.
     */
    private long timeoutMs = 5000;
    
    /**
     * Maximum number of retry attempts.
     */
    private int maxRetries = 3;
    
    /**
     * Initial wait duration between retries (milliseconds).
     */
    private long retryInitialIntervalMs = 500;
    
    /**
     * Maximum wait duration between retries (milliseconds).
     */
    private long retryMaxIntervalMs = 5000;
    
    /**
     * Retry multiplier for exponential backoff.
     */
    private double retryMultiplier = 2.0;
    
    /**
     * Circuit breaker failure rate threshold (percentage).
     */
    private float circuitBreakerFailureRateThreshold = 50;
    
    /**
     * Circuit breaker sliding window size.
     */
    private int circuitBreakerSlidingWindowSize = 10;
    
    /**
     * Circuit breaker wait duration in open state (milliseconds).
     */
    private long circuitBreakerWaitDurationMs = 30000;
    
    /**
     * Permitted number of calls in half-open state.
     */
    private int circuitBreakerPermittedCallsInHalfOpen = 3;
    
    /**
     * Whether to block non-critical requests when service is impaired.
     */
    private boolean blockNonCriticalWhenImpaired = true;
    
    /**
     * Path patterns considered critical (comma-separated).
     */
    private String criticalPathPatterns = "/health,/critical,/emergency";
}

