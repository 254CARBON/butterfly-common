package com.z254.butterfly.common.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages health state for downstream services.
 * Aggregates signals from circuit breakers, metrics, and health checks
 * to compute overall service health and degradation state.
 */
@Component
@Slf4j
public class HealthStateManager {
    
    private final Map<String, ServiceHealth> healthCache = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> consecutiveFailures = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> consecutiveSuccesses = new ConcurrentHashMap<>();
    private final Map<String, AtomicReference<Instant>> lastSuccessTime = new ConcurrentHashMap<>();
    
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final MeterRegistry meterRegistry;
    private final HealthStateProperties properties;
    
    // Health thresholds
    private static final double ERROR_RATE_DEGRADED = 0.01;  // 1%
    private static final double ERROR_RATE_IMPAIRED = 0.05;  // 5%
    private static final double ERROR_RATE_UNAVAILABLE = 0.10;  // 10%
    private static final long LATENCY_DEGRADED_MS = 200;
    private static final long LATENCY_IMPAIRED_MS = 500;
    private static final int CONSECUTIVE_FAILURES_IMPAIRED = 3;
    private static final int CONSECUTIVE_FAILURES_UNAVAILABLE = 5;
    
    public HealthStateManager(
            CircuitBreakerRegistry circuitBreakerRegistry,
            MeterRegistry meterRegistry,
            HealthStateProperties properties) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.meterRegistry = meterRegistry;
        this.properties = properties;
        log.info("HealthStateManager initialized with {} services", properties.getMonitoredServices().size());
    }
    
    /**
     * Get the current health state for a service.
     * If no cached state exists or it's stale, compute a fresh one.
     */
    public ServiceHealth getServiceHealth(String serviceId) {
        ServiceHealth cached = healthCache.get(serviceId);
        if (cached == null || cached.isStale(properties.getHealthCacheTtlMs())) {
            return computeAndCacheHealth(serviceId);
        }
        return cached;
    }
    
    /**
     * Record a successful call to a service.
     */
    public void recordSuccess(String serviceId, long latencyMs) {
        consecutiveSuccesses.computeIfAbsent(serviceId, k -> new AtomicInteger(0)).incrementAndGet();
        consecutiveFailures.computeIfAbsent(serviceId, k -> new AtomicInteger(0)).set(0);
        lastSuccessTime.computeIfAbsent(serviceId, k -> new AtomicReference<>()).set(Instant.now());
        
        // Trigger health recomputation if we were degraded
        ServiceHealth current = healthCache.get(serviceId);
        if (current != null && !current.getState().isOperational()) {
            computeAndCacheHealth(serviceId);
        }
    }
    
    /**
     * Record a failed call to a service.
     */
    public void recordFailure(String serviceId, Throwable error) {
        int failures = consecutiveFailures.computeIfAbsent(serviceId, k -> new AtomicInteger(0)).incrementAndGet();
        consecutiveSuccesses.computeIfAbsent(serviceId, k -> new AtomicInteger(0)).set(0);
        
        log.debug("Recorded failure for service {}: {} consecutive failures", serviceId, failures);
        
        // Trigger health recomputation
        computeAndCacheHealth(serviceId);
    }
    
    /**
     * Compute and cache health for all monitored services.
     */
    @Scheduled(fixedDelayString = "${butterfly.resilience.health-update-interval-ms:5000}")
    public void refreshAllHealthStates() {
        properties.getMonitoredServices().forEach(this::computeAndCacheHealth);
    }
    
    /**
     * Compute the health state for a service based on all available signals.
     */
    private ServiceHealth computeAndCacheHealth(String serviceId) {
        ServiceHealth health = computeHealth(serviceId);
        healthCache.put(serviceId, health);
        
        // Log state transitions
        ServiceHealth previous = healthCache.get(serviceId);
        if (previous != null && previous.getState() != health.getState()) {
            log.info("Service {} health state changed: {} -> {}", 
                    serviceId, previous.getState(), health.getState());
        }
        
        return health;
    }
    
    private ServiceHealth computeHealth(String serviceId) {
        // Gather signals
        boolean circuitOpen = isCircuitBreakerOpen(serviceId);
        double errorRate = getErrorRate(serviceId);
        long latencyP95 = getLatencyP95(serviceId);
        long latencyP99 = getLatencyP99(serviceId);
        double throughput = getThroughput(serviceId);
        int failures = consecutiveFailures.getOrDefault(serviceId, new AtomicInteger(0)).get();
        int successes = consecutiveSuccesses.getOrDefault(serviceId, new AtomicInteger(0)).get();
        
        // Determine degradation state
        DegradationState state = computeDegradationState(
                circuitOpen, errorRate, latencyP95, failures, successes);
        
        String message = buildHealthMessage(state, circuitOpen, errorRate, latencyP95, failures);
        
        return ServiceHealth.builder()
                .serviceId(serviceId)
                .state(state)
                .lastUpdated(Instant.now())
                .errorRate(errorRate)
                .latencyP95Ms(latencyP95)
                .latencyP99Ms(latencyP99)
                .throughputRps(throughput)
                .circuitBreakerOpen(circuitOpen)
                .consecutiveFailures(failures)
                .consecutiveSuccesses(successes)
                .message(message)
                .build();
    }
    
    private DegradationState computeDegradationState(
            boolean circuitOpen,
            double errorRate,
            long latencyP95,
            int consecutiveFailures,
            int consecutiveSuccesses) {
        
        // Circuit breaker open = UNAVAILABLE
        if (circuitOpen) {
            return DegradationState.UNAVAILABLE;
        }
        
        // High consecutive failures = UNAVAILABLE or IMPAIRED
        if (consecutiveFailures >= CONSECUTIVE_FAILURES_UNAVAILABLE) {
            return DegradationState.UNAVAILABLE;
        }
        if (consecutiveFailures >= CONSECUTIVE_FAILURES_IMPAIRED) {
            return DegradationState.IMPAIRED;
        }
        
        // Error rate thresholds
        if (errorRate >= ERROR_RATE_UNAVAILABLE) {
            return DegradationState.UNAVAILABLE;
        }
        if (errorRate >= ERROR_RATE_IMPAIRED) {
            return DegradationState.IMPAIRED;
        }
        if (errorRate >= ERROR_RATE_DEGRADED) {
            return DegradationState.DEGRADED;
        }
        
        // Latency thresholds
        if (latencyP95 >= LATENCY_IMPAIRED_MS) {
            return DegradationState.IMPAIRED;
        }
        if (latencyP95 >= LATENCY_DEGRADED_MS) {
            return DegradationState.DEGRADED;
        }
        
        // If recovering from degraded state, require consecutive successes
        if (consecutiveSuccesses < 3) {
            // Might still be recovering - stay cautious
            return DegradationState.DEGRADED;
        }
        
        return DegradationState.HEALTHY;
    }
    
    private String buildHealthMessage(
            DegradationState state,
            boolean circuitOpen,
            double errorRate,
            long latencyP95,
            int failures) {
        
        return switch (state) {
            case HEALTHY -> "Service is operating normally";
            case DEGRADED -> String.format(
                    "Service degraded: error_rate=%.2f%%, latency_p95=%dms",
                    errorRate * 100, latencyP95);
            case IMPAIRED -> String.format(
                    "Service impaired: error_rate=%.2f%%, failures=%d",
                    errorRate * 100, failures);
            case UNAVAILABLE -> circuitOpen
                    ? "Circuit breaker open - service unavailable"
                    : String.format("Service unavailable: %d consecutive failures", failures);
            case UNKNOWN -> "Service health unknown";
        };
    }
    
    // === Metrics Collection ===
    
    private boolean isCircuitBreakerOpen(String serviceId) {
        try {
            CircuitBreaker cb = circuitBreakerRegistry.find(serviceId).orElse(null);
            if (cb != null) {
                return cb.getState() == CircuitBreaker.State.OPEN;
            }
        } catch (Exception e) {
            log.trace("Could not get circuit breaker state for {}: {}", serviceId, e.getMessage());
        }
        return false;
    }
    
    private double getErrorRate(String serviceId) {
        try {
            // Try to get error rate from micrometer metrics
            double totalRequests = meterRegistry.get("http.client.requests")
                    .tag("clientName", serviceId)
                    .timer()
                    .count();
            
            double errorRequests = meterRegistry.get("http.client.requests")
                    .tag("clientName", serviceId)
                    .tag("status", "5xx")
                    .timer()
                    .count();
            
            return totalRequests > 0 ? errorRequests / totalRequests : 0.0;
        } catch (Exception e) {
            // Metrics might not exist yet
            return 0.0;
        }
    }
    
    private long getLatencyP95(String serviceId) {
        try {
            Timer timer = meterRegistry.get("http.client.requests")
                    .tag("clientName", serviceId)
                    .timer();
            
            // Use takeSnapshot to get percentile values
            io.micrometer.core.instrument.distribution.HistogramSnapshot snapshot = timer.takeSnapshot();
            io.micrometer.core.instrument.distribution.ValueAtPercentile[] percentiles = snapshot.percentileValues();
            for (io.micrometer.core.instrument.distribution.ValueAtPercentile vap : percentiles) {
                if (Math.abs(vap.percentile() - 0.95) < 0.01) {
                    return (long) (vap.value(java.util.concurrent.TimeUnit.MILLISECONDS));
                }
            }
            // Fallback: use mean
            return (long) (timer.mean(java.util.concurrent.TimeUnit.MILLISECONDS));
        } catch (Exception e) {
            return 0;
        }
    }
    
    private long getLatencyP99(String serviceId) {
        try {
            Timer timer = meterRegistry.get("http.client.requests")
                    .tag("clientName", serviceId)
                    .timer();
            
            // Use takeSnapshot to get percentile values
            io.micrometer.core.instrument.distribution.HistogramSnapshot snapshot = timer.takeSnapshot();
            io.micrometer.core.instrument.distribution.ValueAtPercentile[] percentiles = snapshot.percentileValues();
            for (io.micrometer.core.instrument.distribution.ValueAtPercentile vap : percentiles) {
                if (Math.abs(vap.percentile() - 0.99) < 0.01) {
                    return (long) (vap.value(java.util.concurrent.TimeUnit.MILLISECONDS));
                }
            }
            // Fallback: use max
            return (long) (timer.max(java.util.concurrent.TimeUnit.MILLISECONDS));
        } catch (Exception e) {
            return 0;
        }
    }
    
    private double getThroughput(String serviceId) {
        try {
            Timer timer = meterRegistry.get("http.client.requests")
                    .tag("clientName", serviceId)
                    .timer();
            
            return timer.count() / timer.totalTime(java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    /**
     * Get the composite health state across all monitored services.
     */
    public DegradationState getOverallSystemState() {
        DegradationState worst = DegradationState.HEALTHY;
        for (String serviceId : properties.getMonitoredServices()) {
            ServiceHealth health = getServiceHealth(serviceId);
            worst = worst.worse(health.getState());
        }
        return worst;
    }
    
    /**
     * Get all current health states.
     */
    public Map<String, ServiceHealth> getAllHealthStates() {
        Map<String, ServiceHealth> result = new ConcurrentHashMap<>();
        for (String serviceId : properties.getMonitoredServices()) {
            result.put(serviceId, getServiceHealth(serviceId));
        }
        return result;
    }
}

