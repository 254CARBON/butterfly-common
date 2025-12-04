package com.z254.butterfly.common.health;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for ecosystem health aggregation.
 * 
 * <p>Example configuration:
 * <pre>
 * butterfly:
 *   health:
 *     polling-interval-ms: 30000
 *     health-check-timeout-seconds: 5
 *     publish-to-kafka: true
 *     circuit-breaker-propagation: true
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "butterfly.health")
public class EcosystemHealthProperties {

    /**
     * Health polling interval in milliseconds.
     */
    private long pollingIntervalMs = 30000;

    /**
     * Timeout for individual health checks in seconds.
     */
    private int healthCheckTimeoutSeconds = 5;

    /**
     * Whether to publish health events to Kafka.
     */
    private boolean publishToKafka = true;

    /**
     * Whether to propagate circuit breaker state changes via Kafka.
     */
    private boolean circuitBreakerPropagation = true;

    /**
     * Topic for health status events.
     */
    private String healthStatusTopic = "ecosystem.health.status";

    /**
     * Topic for circuit breaker events.
     */
    private String circuitBreakerTopic = "ecosystem.circuit-breaker.events";

    /**
     * Whether the health aggregator is enabled.
     */
    private boolean enabled = true;

    /**
     * Minimum interval between health event publications (debounce).
     */
    private long debounceIntervalMs = 5000;

    /**
     * Get polling interval in seconds for display/logging.
     */
    public int getPollingIntervalSeconds() {
        return (int) (pollingIntervalMs / 1000);
    }
}
