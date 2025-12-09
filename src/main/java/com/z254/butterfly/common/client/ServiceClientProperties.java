package com.z254.butterfly.common.client;

import com.z254.butterfly.common.resilience.DegradationClientProperties;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for BUTTERFLY ecosystem service clients.
 * 
 * <p>Example configuration:
 * <pre>
 * butterfly:
 *   clients:
 *     perception:
 *       url: http://localhost:8080
 *       timeout: 30s
 *       circuit-breaker:
 *         failure-rate-threshold: 50
 *     capsule:
 *       url: http://localhost:8081
 *     odyssey:
 *       url: http://localhost:8082
 *     plato:
 *       url: http://localhost:8083
 *     nexus:
 *       url: http://localhost:8084
 *     synapse:
 *       url: http://localhost:8085
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "butterfly.clients")
public class ServiceClientProperties {

    /**
     * PERCEPTION service configuration.
     */
    private ServiceConfig perception = new ServiceConfig()
            .withUrl("http://localhost:8080")
            .withServiceId("perception");

    /**
     * CAPSULE service configuration.
     */
    private ServiceConfig capsule = new ServiceConfig()
            .withUrl("http://localhost:8081")
            .withServiceId("capsule");

    /**
     * ODYSSEY service configuration.
     */
    private ServiceConfig odyssey = new ServiceConfig()
            .withUrl("http://localhost:8082")
            .withServiceId("odyssey");

    /**
     * PLATO service configuration.
     */
    private ServiceConfig plato = new ServiceConfig()
            .withUrl("http://localhost:8083")
            .withServiceId("plato");

    /**
     * NEXUS service configuration.
     */
    private ServiceConfig nexus = new ServiceConfig()
            .withUrl("http://localhost:8084")
            .withServiceId("nexus");

    /**
     * SYNAPSE service configuration.
     */
    private ServiceConfig synapse = new ServiceConfig()
            .withUrl("http://localhost:8085")
            .withServiceId("synapse");

    /**
     * CORTEX service configuration.
     */
    private ServiceConfig cortex = new ServiceConfig()
            .withUrl("http://localhost:8086")
            .withServiceId("cortex");

    /**
     * Default configuration applied to all services unless overridden.
     */
    private ServiceConfig defaults = new ServiceConfig();

    /**
     * Additional custom services (for extensions).
     */
    private Map<String, ServiceConfig> custom = new HashMap<>();

    /**
     * Configuration for a single service client.
     */
    @Data
    public static class ServiceConfig {
        /**
         * Service identifier (used for circuit breaker naming).
         */
        private String serviceId;

        /**
         * Base URL for the service.
         */
        private String url;

        /**
         * Request timeout.
         */
        private Duration timeout = Duration.ofSeconds(30);

        /**
         * Connection timeout.
         */
        private Duration connectTimeout = Duration.ofSeconds(5);

        /**
         * Circuit breaker configuration.
         */
        private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();

        /**
         * Retry configuration.
         */
        private RetryConfig retry = new RetryConfig();

        /**
         * Whether this client is enabled.
         */
        private boolean enabled = true;

        /**
         * Whether to block non-critical requests when degraded.
         */
        private boolean blockNonCriticalWhenDegraded = true;

        public ServiceConfig withUrl(String url) {
            this.url = url;
            return this;
        }

        public ServiceConfig withServiceId(String serviceId) {
            this.serviceId = serviceId;
            return this;
        }

        /**
         * Convert to DegradationClientProperties for the underlying client.
         */
        public DegradationClientProperties toDegradationProperties() {
            DegradationClientProperties props = new DegradationClientProperties();
            props.setTimeoutMs(timeout.toMillis());
            props.setCircuitBreakerFailureRateThreshold(circuitBreaker.getFailureRateThreshold());
            props.setCircuitBreakerSlidingWindowSize(circuitBreaker.getSlidingWindowSize());
            props.setCircuitBreakerWaitDurationMs(circuitBreaker.getWaitDuration().toMillis());
            props.setCircuitBreakerPermittedCallsInHalfOpen(circuitBreaker.getPermittedCallsInHalfOpen());
            props.setMaxRetries(retry.getMaxAttempts());
            props.setRetryInitialIntervalMs(retry.getInitialInterval().toMillis());
            props.setRetryMaxIntervalMs(retry.getMaxInterval().toMillis());
            props.setRetryMultiplier(retry.getMultiplier());
            props.setBlockNonCriticalWhenImpaired(blockNonCriticalWhenDegraded);
            return props;
        }
    }

    /**
     * Circuit breaker configuration.
     */
    @Data
    public static class CircuitBreakerConfig {
        /**
         * Failure rate threshold (percentage) to trip the circuit.
         */
        private float failureRateThreshold = 50;

        /**
         * Sliding window size for failure rate calculation.
         */
        private int slidingWindowSize = 10;

        /**
         * Wait duration in open state before transitioning to half-open.
         */
        private Duration waitDuration = Duration.ofSeconds(30);

        /**
         * Number of permitted calls in half-open state.
         */
        private int permittedCallsInHalfOpen = 3;
    }

    /**
     * Retry configuration.
     */
    @Data
    public static class RetryConfig {
        /**
         * Maximum number of retry attempts.
         */
        private int maxAttempts = 3;

        /**
         * Initial wait interval between retries.
         */
        private Duration initialInterval = Duration.ofMillis(500);

        /**
         * Maximum wait interval between retries.
         */
        private Duration maxInterval = Duration.ofSeconds(5);

        /**
         * Multiplier for exponential backoff.
         */
        private double multiplier = 2.0;
    }
}
