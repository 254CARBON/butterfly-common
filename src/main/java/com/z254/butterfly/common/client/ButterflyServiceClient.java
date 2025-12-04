package com.z254.butterfly.common.client;

import com.z254.butterfly.common.resilience.DegradationState;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Base interface for all BUTTERFLY ecosystem service clients.
 * 
 * <p>Provides common health check and availability methods that all
 * service clients must implement.
 * 
 * <p>All clients are built on top of {@link com.z254.butterfly.common.resilience.DegradationAwareClient}
 * and include:
 * <ul>
 *   <li>Circuit breaker protection</li>
 *   <li>Retry with exponential backoff</li>
 *   <li>Health-based request blocking</li>
 *   <li>Fallback execution</li>
 *   <li>Tenant context propagation</li>
 *   <li>Metrics and tracing</li>
 * </ul>
 */
public interface ButterflyServiceClient {

    /**
     * Get the service identifier.
     *
     * @return The service ID (e.g., "perception", "capsule", "odyssey")
     */
    String getServiceId();

    /**
     * Check if the service is healthy and available.
     *
     * @return Mono emitting true if healthy, false otherwise
     */
    Mono<Boolean> isHealthy();

    /**
     * Get the health status of the service.
     *
     * @return Mono emitting the health status
     */
    Mono<HealthStatus> health();

    /**
     * Get the current degradation state of the service.
     *
     * @return The current degradation state
     */
    DegradationState getDegradationState();

    /**
     * Check if the service is operational (healthy or degraded but available).
     *
     * @return true if the service can accept requests
     */
    default boolean isOperational() {
        DegradationState state = getDegradationState();
        return state == DegradationState.HEALTHY || state == DegradationState.DEGRADED;
    }

    /**
     * Health status response from a service.
     */
    record HealthStatus(
            String status,
            String serviceId,
            Map<String, Object> details
    ) {
        public static HealthStatus up(String serviceId) {
            return new HealthStatus("UP", serviceId, Map.of());
        }

        public static HealthStatus up(String serviceId, Map<String, Object> details) {
            return new HealthStatus("UP", serviceId, details);
        }

        public static HealthStatus down(String serviceId, String reason) {
            return new HealthStatus("DOWN", serviceId, Map.of("error", reason));
        }

        public static HealthStatus down(String serviceId, Throwable error) {
            return new HealthStatus("DOWN", serviceId, Map.of(
                    "error", error.getMessage(),
                    "type", error.getClass().getSimpleName()
            ));
        }

        public boolean isUp() {
            return "UP".equalsIgnoreCase(status);
        }
    }
}
