package com.z254.butterfly.common.health;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * REST controller for ecosystem health status.
 * 
 * <p>Endpoints:
 * <ul>
 *   <li>GET /api/v1/ecosystem/health - Get overall ecosystem health</li>
 *   <li>GET /api/v1/ecosystem/health/services - Get all service health snapshots</li>
 *   <li>GET /api/v1/ecosystem/health/services/{serviceId} - Get specific service health</li>
 *   <li>POST /api/v1/ecosystem/health/services/{serviceId}/refresh - Force health check</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/ecosystem/health")
@RequiredArgsConstructor
public class EcosystemHealthController {

    private final EcosystemHealthAggregator healthAggregator;

    /**
     * Get overall ecosystem health status.
     */
    @GetMapping
    public ResponseEntity<EcosystemHealthAggregator.EcosystemHealthStatus> getEcosystemHealth() {
        EcosystemHealthAggregator.EcosystemHealthStatus status = healthAggregator.getEcosystemHealth();
        if (status == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(status);
    }

    /**
     * Get all service health snapshots.
     */
    @GetMapping("/services")
    public ResponseEntity<Map<String, EcosystemHealthAggregator.ServiceHealthSnapshot>> getAllServiceHealth() {
        Map<String, EcosystemHealthAggregator.ServiceHealthSnapshot> health = healthAggregator.getAllServiceHealth();
        return ResponseEntity.ok(health);
    }

    /**
     * Get health snapshot for a specific service.
     */
    @GetMapping("/services/{serviceId}")
    public ResponseEntity<EcosystemHealthAggregator.ServiceHealthSnapshot> getServiceHealth(
            @PathVariable String serviceId) {
        EcosystemHealthAggregator.ServiceHealthSnapshot snapshot = healthAggregator.getServiceHealth(serviceId);
        if (snapshot == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(snapshot);
    }

    /**
     * Force a health check for a specific service.
     */
    @PostMapping("/services/{serviceId}/refresh")
    public Mono<ResponseEntity<EcosystemHealthAggregator.ServiceHealthSnapshot>> forceHealthCheck(
            @PathVariable String serviceId) {
        return healthAggregator.forceHealthCheck(serviceId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Get list of unhealthy services.
     */
    @GetMapping("/unhealthy")
    public ResponseEntity<List<String>> getUnhealthyServices() {
        return ResponseEntity.ok(healthAggregator.getUnhealthyServices());
    }

    /**
     * Simple health check endpoint for load balancers.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        EcosystemHealthAggregator.EcosystemHealthStatus status = healthAggregator.getEcosystemHealth();
        if (status == null) {
            return ResponseEntity.ok(Map.of(
                    "status", "INITIALIZING",
                    "healthy", false
            ));
        }

        return ResponseEntity.ok(Map.of(
                "status", status.getOverallStatus(),
                "healthy", healthAggregator.isEcosystemHealthy(),
                "integrationScore", status.getIntegrationScore(),
                "unhealthyCount", status.getUnhealthyServices().size(),
                "degradedCount", status.getDegradedServices().size()
        ));
    }
}
