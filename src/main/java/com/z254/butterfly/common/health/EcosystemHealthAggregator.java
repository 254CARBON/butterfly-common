package com.z254.butterfly.common.health;

import com.z254.butterfly.common.client.ButterflyServiceClient;
import com.z254.butterfly.common.client.ButterflyServiceClientFactory;
import com.z254.butterfly.common.resilience.DegradationState;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Aggregates health status from all BUTTERFLY ecosystem services.
 * 
 * <p>Features:
 * <ul>
 *   <li>Periodic health polling of all services</li>
 *   <li>Aggregated ecosystem health status</li>
 *   <li>Kafka event publishing for health changes</li>
 *   <li>Circuit breaker state tracking</li>
 *   <li>Metrics exposure for monitoring</li>
 * </ul>
 * 
 * <p>Health events are published to the {@code ecosystem.health.status} Kafka topic
 * whenever a service's health state changes.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EcosystemHealthAggregator {

    public static final String HEALTH_TOPIC = "ecosystem.health.status";
    public static final String CIRCUIT_BREAKER_TOPIC = "ecosystem.circuit-breaker.events";

    private static final List<String> SERVICE_IDS = List.of(
            "perception", "capsule", "odyssey", "plato", "nexus", "synapse"
    );

    private final ButterflyServiceClientFactory clientFactory;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final EcosystemHealthProperties properties;

    private final Map<String, ServiceHealthSnapshot> healthCache = new ConcurrentHashMap<>();
    private final Map<String, CircuitBreaker.State> circuitBreakerStateCache = new ConcurrentHashMap<>();
    private final AtomicReference<EcosystemHealthStatus> aggregatedHealth = new AtomicReference<>();

    @PostConstruct
    public void init() {
        log.info("EcosystemHealthAggregator initialized - polling interval: {}s",
                properties.getPollingIntervalSeconds());

        // Initialize health cache with UNKNOWN state
        for (String serviceId : SERVICE_IDS) {
            healthCache.put(serviceId, ServiceHealthSnapshot.unknown(serviceId));
        }

        // Register circuit breaker event listeners
        registerCircuitBreakerListeners();

        // Initial health check
        refreshAllHealthStates();
    }

    /**
     * Periodically refresh health status for all services.
     */
    @Scheduled(fixedDelayString = "${butterfly.health.polling-interval-ms:30000}")
    public void refreshAllHealthStates() {
        log.debug("Starting ecosystem health refresh");

        List<Mono<ServiceHealthSnapshot>> healthChecks = SERVICE_IDS.stream()
                .map(this::checkServiceHealth)
                .toList();

        Flux.merge(healthChecks)
                .collectList()
                .doOnSuccess(snapshots -> {
                    EcosystemHealthStatus newStatus = aggregateHealth(snapshots);
                    EcosystemHealthStatus oldStatus = aggregatedHealth.getAndSet(newStatus);

                    // Check for status changes
                    if (oldStatus != null && !oldStatus.getOverallStatus().equals(newStatus.getOverallStatus())) {
                        log.info("Ecosystem health changed: {} -> {}", 
                                oldStatus.getOverallStatus(), newStatus.getOverallStatus());
                        publishHealthEvent(newStatus, "STATUS_CHANGE");
                    }

                    // Update metrics
                    updateMetrics(newStatus);
                })
                .doOnError(e -> log.error("Error during health refresh: {}", e.getMessage()))
                .subscribe();
    }

    /**
     * Check health of a single service.
     */
    private Mono<ServiceHealthSnapshot> checkServiceHealth(String serviceId) {
        ButterflyServiceClient client = getClientByServiceId(serviceId);
        if (client == null) {
            return Mono.just(ServiceHealthSnapshot.unknown(serviceId));
        }

        Instant start = Instant.now();

        return client.health()
                .timeout(Duration.ofSeconds(properties.getHealthCheckTimeoutSeconds()))
                .map(health -> {
                    long latencyMs = Duration.between(start, Instant.now()).toMillis();
                    CircuitBreaker.State cbState = getCircuitBreakerState(serviceId);

                    ServiceHealthSnapshot snapshot = ServiceHealthSnapshot.builder()
                            .serviceId(serviceId)
                            .status(health.isUp() ? "UP" : "DOWN")
                            .degradationState(client.getDegradationState())
                            .latencyMs(latencyMs)
                            .circuitBreakerState(cbState)
                            .lastChecked(Instant.now())
                            .details(health.details())
                            .build();

                    updateHealthCache(serviceId, snapshot);
                    return snapshot;
                })
                .onErrorResume(e -> {
                    long latencyMs = Duration.between(start, Instant.now()).toMillis();
                    CircuitBreaker.State cbState = getCircuitBreakerState(serviceId);

                    ServiceHealthSnapshot snapshot = ServiceHealthSnapshot.builder()
                            .serviceId(serviceId)
                            .status("DOWN")
                            .degradationState(DegradationState.UNAVAILABLE)
                            .latencyMs(latencyMs)
                            .circuitBreakerState(cbState)
                            .lastChecked(Instant.now())
                            .error(e.getMessage())
                            .build();

                    updateHealthCache(serviceId, snapshot);
                    return Mono.just(snapshot);
                });
    }

    /**
     * Update the health cache and check for state changes.
     */
    private void updateHealthCache(String serviceId, ServiceHealthSnapshot newSnapshot) {
        ServiceHealthSnapshot oldSnapshot = healthCache.put(serviceId, newSnapshot);

        // Check for state changes
        if (oldSnapshot != null && !oldSnapshot.getStatus().equals(newSnapshot.getStatus())) {
            log.info("Service {} health changed: {} -> {}",
                    serviceId, oldSnapshot.getStatus(), newSnapshot.getStatus());

            publishServiceHealthChange(oldSnapshot, newSnapshot);
        }
    }

    /**
     * Aggregate health from all services.
     */
    private EcosystemHealthStatus aggregateHealth(List<ServiceHealthSnapshot> snapshots) {
        Map<String, ServiceHealthSnapshot> services = new HashMap<>();
        List<String> unhealthyServices = new ArrayList<>();
        List<String> degradedServices = new ArrayList<>();

        for (ServiceHealthSnapshot snapshot : snapshots) {
            services.put(snapshot.getServiceId(), snapshot);

            if (!"UP".equals(snapshot.getStatus())) {
                unhealthyServices.add(snapshot.getServiceId());
            } else if (snapshot.getDegradationState() != DegradationState.HEALTHY) {
                degradedServices.add(snapshot.getServiceId());
            }
        }

        // Determine overall status
        String overallStatus;
        if (!unhealthyServices.isEmpty()) {
            overallStatus = unhealthyServices.size() >= snapshots.size() / 2 ? "DOWN" : "DEGRADED";
        } else if (!degradedServices.isEmpty()) {
            overallStatus = "DEGRADED";
        } else {
            overallStatus = "UP";
        }

        // Calculate integration score (0-100)
        double integrationScore = calculateIntegrationScore(snapshots);

        return EcosystemHealthStatus.builder()
                .overallStatus(overallStatus)
                .services(services)
                .unhealthyServices(unhealthyServices)
                .degradedServices(degradedServices)
                .integrationScore(integrationScore)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Calculate an integration score based on service health.
     */
    private double calculateIntegrationScore(List<ServiceHealthSnapshot> snapshots) {
        if (snapshots.isEmpty()) return 0.0;

        double score = 0.0;
        for (ServiceHealthSnapshot snapshot : snapshots) {
            double serviceScore = switch (snapshot.getStatus()) {
                case "UP" -> switch (snapshot.getDegradationState()) {
                    case HEALTHY -> 100.0;
                    case DEGRADED -> 75.0;
                    case IMPAIRED -> 50.0;
                    default -> 25.0;
                };
                case "DEGRADED" -> 50.0;
                default -> 0.0;
            };

            // Penalize for high latency
            if (snapshot.getLatencyMs() > 1000) {
                serviceScore -= 10;
            } else if (snapshot.getLatencyMs() > 500) {
                serviceScore -= 5;
            }

            // Penalize for open circuit breaker
            if (snapshot.getCircuitBreakerState() == CircuitBreaker.State.OPEN) {
                serviceScore -= 20;
            }

            score += Math.max(0, serviceScore);
        }

        return score / snapshots.size();
    }

    /**
     * Get the client for a service by ID.
     */
    private ButterflyServiceClient getClientByServiceId(String serviceId) {
        return switch (serviceId) {
            case "perception" -> clientFactory.perceptionClient();
            case "capsule" -> clientFactory.capsuleClient();
            case "odyssey" -> clientFactory.odysseyClient();
            case "plato" -> clientFactory.platoClient();
            case "nexus" -> clientFactory.nexusClient();
            case "synapse" -> clientFactory.synapseClient();
            default -> null;
        };
    }

    /**
     * Get the circuit breaker state for a service.
     */
    private CircuitBreaker.State getCircuitBreakerState(String serviceId) {
        return circuitBreakerRegistry.find(serviceId)
                .map(CircuitBreaker::getState)
                .orElse(CircuitBreaker.State.CLOSED);
    }

    /**
     * Register listeners for circuit breaker state changes.
     */
    private void registerCircuitBreakerListeners() {
        for (String serviceId : SERVICE_IDS) {
            circuitBreakerRegistry.find(serviceId).ifPresent(cb -> {
                cb.getEventPublisher()
                        .onStateTransition(event -> {
                            CircuitBreaker.State oldState = circuitBreakerStateCache.put(
                                    serviceId, event.getStateTransition().getToState());

                            log.info("Circuit breaker {} state changed: {} -> {}",
                                    serviceId,
                                    event.getStateTransition().getFromState(),
                                    event.getStateTransition().getToState());

                            publishCircuitBreakerEvent(serviceId, event.getStateTransition());
                        });
            });
        }
    }

    /**
     * Publish a health event to Kafka.
     */
    private void publishHealthEvent(EcosystemHealthStatus status, String eventType) {
        if (!properties.isPublishToKafka()) {
            return;
        }

        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", eventType);
            event.put("overallStatus", status.getOverallStatus());
            event.put("integrationScore", status.getIntegrationScore());
            event.put("unhealthyServices", status.getUnhealthyServices());
            event.put("degradedServices", status.getDegradedServices());
            event.put("timestamp", status.getTimestamp().toString());

            kafkaTemplate.send(HEALTH_TOPIC, "ecosystem", event);
            log.debug("Published health event: {}", eventType);
        } catch (Exception e) {
            log.warn("Failed to publish health event: {}", e.getMessage());
        }
    }

    /**
     * Publish a service health change event to Kafka.
     */
    private void publishServiceHealthChange(ServiceHealthSnapshot oldSnapshot, ServiceHealthSnapshot newSnapshot) {
        if (!properties.isPublishToKafka()) {
            return;
        }

        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "SERVICE_HEALTH_CHANGE");
            event.put("serviceId", newSnapshot.getServiceId());
            event.put("oldStatus", oldSnapshot.getStatus());
            event.put("newStatus", newSnapshot.getStatus());
            event.put("degradationState", newSnapshot.getDegradationState().name());
            event.put("latencyMs", newSnapshot.getLatencyMs());
            event.put("timestamp", newSnapshot.getLastChecked().toString());
            if (newSnapshot.getError() != null) {
                event.put("error", newSnapshot.getError());
            }

            kafkaTemplate.send(HEALTH_TOPIC, newSnapshot.getServiceId(), event);
        } catch (Exception e) {
            log.warn("Failed to publish service health change event: {}", e.getMessage());
        }
    }

    /**
     * Publish a circuit breaker state change event to Kafka.
     */
    private void publishCircuitBreakerEvent(String serviceId, CircuitBreaker.StateTransition transition) {
        if (!properties.isPublishToKafka()) {
            return;
        }

        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "CIRCUIT_BREAKER_STATE_CHANGE");
            event.put("serviceId", serviceId);
            event.put("fromState", transition.getFromState().name());
            event.put("toState", transition.getToState().name());
            event.put("timestamp", Instant.now().toString());

            kafkaTemplate.send(CIRCUIT_BREAKER_TOPIC, serviceId, event);
        } catch (Exception e) {
            log.warn("Failed to publish circuit breaker event: {}", e.getMessage());
        }
    }

    /**
     * Update Prometheus metrics.
     */
    private void updateMetrics(EcosystemHealthStatus status) {
        // Overall ecosystem health gauge
        meterRegistry.gauge("butterfly.ecosystem.health.score", status.getIntegrationScore());

        // Per-service health metrics
        for (Map.Entry<String, ServiceHealthSnapshot> entry : status.getServices().entrySet()) {
            String serviceId = entry.getKey();
            ServiceHealthSnapshot snapshot = entry.getValue();

            List<Tag> tags = List.of(Tag.of("service", serviceId));

            meterRegistry.gauge("butterfly.service.health.latency_ms", tags, snapshot.getLatencyMs());
            meterRegistry.gauge("butterfly.service.health.up", tags,
                    "UP".equals(snapshot.getStatus()) ? 1 : 0);
        }

        // Unhealthy services count
        meterRegistry.gauge("butterfly.ecosystem.unhealthy_count", status.getUnhealthyServices().size());
        meterRegistry.gauge("butterfly.ecosystem.degraded_count", status.getDegradedServices().size());
    }

    // === Public API ===

    /**
     * Get the current aggregated ecosystem health status.
     */
    public EcosystemHealthStatus getEcosystemHealth() {
        return aggregatedHealth.get();
    }

    /**
     * Get health snapshot for a specific service.
     */
    public ServiceHealthSnapshot getServiceHealth(String serviceId) {
        return healthCache.get(serviceId);
    }

    /**
     * Get all service health snapshots.
     */
    public Map<String, ServiceHealthSnapshot> getAllServiceHealth() {
        return new HashMap<>(healthCache);
    }

    /**
     * Check if the ecosystem is healthy (all services UP).
     */
    public boolean isEcosystemHealthy() {
        EcosystemHealthStatus status = aggregatedHealth.get();
        return status != null && "UP".equals(status.getOverallStatus());
    }

    /**
     * Get services that are currently unhealthy.
     */
    public List<String> getUnhealthyServices() {
        EcosystemHealthStatus status = aggregatedHealth.get();
        return status != null ? status.getUnhealthyServices() : List.of();
    }

    /**
     * Force an immediate health check for a service.
     */
    public Mono<ServiceHealthSnapshot> forceHealthCheck(String serviceId) {
        return checkServiceHealth(serviceId);
    }

    // === DTOs ===

    @Data
    @Builder
    public static class ServiceHealthSnapshot {
        private String serviceId;
        private String status;
        private DegradationState degradationState;
        private long latencyMs;
        private CircuitBreaker.State circuitBreakerState;
        private Instant lastChecked;
        private Map<String, Object> details;
        private String error;

        public static ServiceHealthSnapshot unknown(String serviceId) {
            return ServiceHealthSnapshot.builder()
                    .serviceId(serviceId)
                    .status("UNKNOWN")
                    .degradationState(DegradationState.UNKNOWN)
                    .latencyMs(0)
                    .circuitBreakerState(CircuitBreaker.State.CLOSED)
                    .lastChecked(Instant.now())
                    .build();
        }
    }

    @Data
    @Builder
    public static class EcosystemHealthStatus {
        private String overallStatus;
        private Map<String, ServiceHealthSnapshot> services;
        private List<String> unhealthyServices;
        private List<String> degradedServices;
        private double integrationScore;
        private Instant timestamp;
    }
}
