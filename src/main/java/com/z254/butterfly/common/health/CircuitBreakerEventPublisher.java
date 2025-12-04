package com.z254.butterfly.common.health;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Publishes circuit breaker state changes across the BUTTERFLY ecosystem via Kafka.
 * 
 * <p>This component:
 * <ul>
 *   <li>Listens to local circuit breaker events</li>
 *   <li>Publishes state changes to Kafka for ecosystem-wide visibility</li>
 *   <li>Consumes circuit breaker events from other services</li>
 *   <li>Maintains a cross-service circuit breaker state map</li>
 *   <li>Exposes metrics for monitoring</li>
 * </ul>
 * 
 * <p>Events are published to the {@code ecosystem.circuit-breaker.events} topic.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CircuitBreakerEventPublisher {

    public static final String CIRCUIT_BREAKER_TOPIC = "ecosystem.circuit-breaker.events";

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final EcosystemHealthProperties properties;

    // Track circuit breaker states from all services
    private final Map<String, CircuitBreakerState> ecosystemCircuitBreakerStates = new ConcurrentHashMap<>();

    // Callbacks for state changes
    private final List<CircuitBreakerStateChangeCallback> callbacks = new ArrayList<>();

    // Local service ID
    private String localServiceId;

    @PostConstruct
    public void init() {
        // Determine local service ID from environment or properties
        localServiceId = System.getProperty("spring.application.name",
                System.getenv().getOrDefault("SERVICE_NAME", "unknown"));

        log.info("CircuitBreakerEventPublisher initialized for service: {}", localServiceId);

        // Register listeners for all circuit breakers
        registerCircuitBreakerListeners();
    }

    /**
     * Register listeners for all circuit breakers in the registry.
     */
    private void registerCircuitBreakerListeners() {
        // Register for new circuit breakers
        circuitBreakerRegistry.getEventPublisher()
                .onEntryAdded(event -> registerListenerForCircuitBreaker(event.getAddedEntry()));

        // Register for existing circuit breakers
        circuitBreakerRegistry.getAllCircuitBreakers()
                .forEach(this::registerListenerForCircuitBreaker);
    }

    /**
     * Register event listeners for a specific circuit breaker.
     */
    private void registerListenerForCircuitBreaker(CircuitBreaker circuitBreaker) {
        String name = circuitBreaker.getName();

        circuitBreaker.getEventPublisher()
                .onStateTransition(this::handleStateTransition)
                .onFailureRateExceeded(this::handleFailureRateExceeded)
                .onSlowCallRateExceeded(this::handleSlowCallRateExceeded)
                .onError(this::handleError)
                .onSuccess(this::handleSuccess);

        log.debug("Registered circuit breaker listener for: {}", name);
    }

    /**
     * Handle circuit breaker state transition.
     */
    private void handleStateTransition(CircuitBreakerOnStateTransitionEvent event) {
        String circuitBreakerName = event.getCircuitBreakerName();
        CircuitBreaker.State fromState = event.getStateTransition().getFromState();
        CircuitBreaker.State toState = event.getStateTransition().getToState();

        log.info("Circuit breaker {} transitioned: {} -> {}",
                circuitBreakerName, fromState, toState);

        // Update local state
        CircuitBreakerState state = CircuitBreakerState.builder()
                .circuitBreakerName(circuitBreakerName)
                .serviceId(localServiceId)
                .state(toState)
                .previousState(fromState)
                .timestamp(Instant.now())
                .build();

        ecosystemCircuitBreakerStates.put(getStateKey(localServiceId, circuitBreakerName), state);

        // Publish to Kafka
        publishStateChange(state);

        // Update metrics
        updateMetrics(state);

        // Notify callbacks
        notifyCallbacks(state);
    }

    /**
     * Handle failure rate exceeded event.
     */
    private void handleFailureRateExceeded(CircuitBreakerOnFailureRateExceededEvent event) {
        String circuitBreakerName = event.getCircuitBreakerName();
        float failureRate = event.getFailureRate();

        log.warn("Circuit breaker {} failure rate exceeded: {}%",
                circuitBreakerName, failureRate);

        publishFailureRateExceeded(circuitBreakerName, failureRate);
    }

    /**
     * Handle slow call rate exceeded event.
     */
    private void handleSlowCallRateExceeded(CircuitBreakerOnSlowCallRateExceededEvent event) {
        String circuitBreakerName = event.getCircuitBreakerName();
        float slowCallRate = event.getSlowCallRate();

        log.warn("Circuit breaker {} slow call rate exceeded: {}%",
                circuitBreakerName, slowCallRate);

        publishSlowCallRateExceeded(circuitBreakerName, slowCallRate);
    }

    /**
     * Handle error event.
     */
    private void handleError(CircuitBreakerOnErrorEvent event) {
        // Track errors for metrics
        meterRegistry.counter("butterfly.circuit_breaker.errors",
                        List.of(
                                Tag.of("circuit_breaker", event.getCircuitBreakerName()),
                                Tag.of("service", localServiceId)
                        ))
                .increment();
    }

    /**
     * Handle success event.
     */
    private void handleSuccess(CircuitBreakerOnSuccessEvent event) {
        // Track successes for metrics
        meterRegistry.counter("butterfly.circuit_breaker.successes",
                        List.of(
                                Tag.of("circuit_breaker", event.getCircuitBreakerName()),
                                Tag.of("service", localServiceId)
                        ))
                .increment();

        meterRegistry.timer("butterfly.circuit_breaker.call_duration",
                        List.of(
                                Tag.of("circuit_breaker", event.getCircuitBreakerName()),
                                Tag.of("service", localServiceId)
                        ))
                .record(event.getElapsedDuration());
    }

    /**
     * Publish state change to Kafka.
     */
    private void publishStateChange(CircuitBreakerState state) {
        if (!properties.isCircuitBreakerPropagation()) {
            return;
        }

        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "STATE_TRANSITION");
            event.put("circuitBreakerName", state.getCircuitBreakerName());
            event.put("serviceId", state.getServiceId());
            event.put("state", state.getState().name());
            event.put("previousState", state.getPreviousState() != null ? state.getPreviousState().name() : null);
            event.put("timestamp", state.getTimestamp().toString());

            kafkaTemplate.send(CIRCUIT_BREAKER_TOPIC, state.getCircuitBreakerName(), event);

            log.debug("Published circuit breaker state change: {} -> {}",
                    state.getCircuitBreakerName(), state.getState());
        } catch (Exception e) {
            log.warn("Failed to publish circuit breaker state change: {}", e.getMessage());
        }
    }

    /**
     * Publish failure rate exceeded event.
     */
    private void publishFailureRateExceeded(String circuitBreakerName, float failureRate) {
        if (!properties.isCircuitBreakerPropagation()) {
            return;
        }

        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "FAILURE_RATE_EXCEEDED");
            event.put("circuitBreakerName", circuitBreakerName);
            event.put("serviceId", localServiceId);
            event.put("failureRate", failureRate);
            event.put("timestamp", Instant.now().toString());

            kafkaTemplate.send(CIRCUIT_BREAKER_TOPIC, circuitBreakerName, event);
        } catch (Exception e) {
            log.warn("Failed to publish failure rate exceeded event: {}", e.getMessage());
        }
    }

    /**
     * Publish slow call rate exceeded event.
     */
    private void publishSlowCallRateExceeded(String circuitBreakerName, float slowCallRate) {
        if (!properties.isCircuitBreakerPropagation()) {
            return;
        }

        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "SLOW_CALL_RATE_EXCEEDED");
            event.put("circuitBreakerName", circuitBreakerName);
            event.put("serviceId", localServiceId);
            event.put("slowCallRate", slowCallRate);
            event.put("timestamp", Instant.now().toString());

            kafkaTemplate.send(CIRCUIT_BREAKER_TOPIC, circuitBreakerName, event);
        } catch (Exception e) {
            log.warn("Failed to publish slow call rate exceeded event: {}", e.getMessage());
        }
    }

    /**
     * Update metrics for circuit breaker state.
     */
    private void updateMetrics(CircuitBreakerState state) {
        String stateValue = switch (state.getState()) {
            case CLOSED -> "0";
            case HALF_OPEN -> "1";
            case OPEN -> "2";
            case DISABLED -> "3";
            case FORCED_OPEN -> "4";
            case METRICS_ONLY -> "5";
        };

        meterRegistry.gauge("butterfly.circuit_breaker.state",
                List.of(
                        Tag.of("circuit_breaker", state.getCircuitBreakerName()),
                        Tag.of("service", state.getServiceId())
                ),
                state.getState().ordinal());

        meterRegistry.counter("butterfly.circuit_breaker.transitions",
                        List.of(
                                Tag.of("circuit_breaker", state.getCircuitBreakerName()),
                                Tag.of("service", state.getServiceId()),
                                Tag.of("from", state.getPreviousState() != null ? state.getPreviousState().name() : "UNKNOWN"),
                                Tag.of("to", state.getState().name())
                        ))
                .increment();
    }

    /**
     * Notify registered callbacks of state change.
     */
    private void notifyCallbacks(CircuitBreakerState state) {
        for (CircuitBreakerStateChangeCallback callback : callbacks) {
            try {
                callback.onStateChange(state);
            } catch (Exception e) {
                log.warn("Callback failed for circuit breaker state change: {}", e.getMessage());
            }
        }
    }

    // === Kafka Consumer for ecosystem circuit breaker events ===

    @KafkaListener(topics = CIRCUIT_BREAKER_TOPIC, groupId = "${spring.application.name}-cb-consumer")
    public void onCircuitBreakerEvent(Map<String, Object> event) {
        String serviceId = (String) event.get("serviceId");
        String circuitBreakerName = (String) event.get("circuitBreakerName");
        String eventType = (String) event.get("eventType");

        // Skip events from this service
        if (localServiceId.equals(serviceId)) {
            return;
        }

        log.debug("Received circuit breaker event from {}: {} - {}",
                serviceId, circuitBreakerName, eventType);

        if ("STATE_TRANSITION".equals(eventType)) {
            String stateStr = (String) event.get("state");
            String previousStateStr = (String) event.get("previousState");

            CircuitBreaker.State state = CircuitBreaker.State.valueOf(stateStr);
            CircuitBreaker.State previousState = previousStateStr != null
                    ? CircuitBreaker.State.valueOf(previousStateStr)
                    : null;

            CircuitBreakerState cbState = CircuitBreakerState.builder()
                    .circuitBreakerName(circuitBreakerName)
                    .serviceId(serviceId)
                    .state(state)
                    .previousState(previousState)
                    .timestamp(Instant.parse((String) event.get("timestamp")))
                    .build();

            // Update ecosystem state map
            ecosystemCircuitBreakerStates.put(getStateKey(serviceId, circuitBreakerName), cbState);

            // Notify callbacks about remote circuit breaker changes
            notifyCallbacks(cbState);
        }
    }

    // === Public API ===

    /**
     * Get the current state of a circuit breaker from any service.
     */
    public Optional<CircuitBreakerState> getCircuitBreakerState(String serviceId, String circuitBreakerName) {
        return Optional.ofNullable(ecosystemCircuitBreakerStates.get(getStateKey(serviceId, circuitBreakerName)));
    }

    /**
     * Get all circuit breaker states for a service.
     */
    public List<CircuitBreakerState> getCircuitBreakerStatesByService(String serviceId) {
        return ecosystemCircuitBreakerStates.values().stream()
                .filter(state -> serviceId.equals(state.getServiceId()))
                .toList();
    }

    /**
     * Get all open circuit breakers across the ecosystem.
     */
    public List<CircuitBreakerState> getOpenCircuitBreakers() {
        return ecosystemCircuitBreakerStates.values().stream()
                .filter(state -> state.getState() == CircuitBreaker.State.OPEN)
                .toList();
    }

    /**
     * Get all circuit breaker states.
     */
    public Collection<CircuitBreakerState> getAllCircuitBreakerStates() {
        return Collections.unmodifiableCollection(ecosystemCircuitBreakerStates.values());
    }

    /**
     * Register a callback for circuit breaker state changes.
     */
    public void registerCallback(CircuitBreakerStateChangeCallback callback) {
        callbacks.add(callback);
    }

    /**
     * Check if any circuit breaker is open for a service.
     */
    public boolean hasOpenCircuitBreaker(String serviceId) {
        return ecosystemCircuitBreakerStates.values().stream()
                .filter(state -> serviceId.equals(state.getServiceId()))
                .anyMatch(state -> state.getState() == CircuitBreaker.State.OPEN);
    }

    /**
     * Get ecosystem circuit breaker summary.
     */
    public CircuitBreakerSummary getEcosystemSummary() {
        Map<CircuitBreaker.State, Long> byState = new HashMap<>();
        Map<String, Integer> byService = new HashMap<>();

        for (CircuitBreakerState state : ecosystemCircuitBreakerStates.values()) {
            byState.merge(state.getState(), 1L, Long::sum);
            byService.merge(state.getServiceId(), 1, Integer::sum);
        }

        return CircuitBreakerSummary.builder()
                .totalCircuitBreakers(ecosystemCircuitBreakerStates.size())
                .openCount(byState.getOrDefault(CircuitBreaker.State.OPEN, 0L).intValue())
                .halfOpenCount(byState.getOrDefault(CircuitBreaker.State.HALF_OPEN, 0L).intValue())
                .closedCount(byState.getOrDefault(CircuitBreaker.State.CLOSED, 0L).intValue())
                .byService(byService)
                .timestamp(Instant.now())
                .build();
    }

    private String getStateKey(String serviceId, String circuitBreakerName) {
        return serviceId + ":" + circuitBreakerName;
    }

    // === DTOs ===

    @Data
    @Builder
    public static class CircuitBreakerState {
        private String circuitBreakerName;
        private String serviceId;
        private CircuitBreaker.State state;
        private CircuitBreaker.State previousState;
        private Instant timestamp;
    }

    @Data
    @Builder
    public static class CircuitBreakerSummary {
        private int totalCircuitBreakers;
        private int openCount;
        private int halfOpenCount;
        private int closedCount;
        private Map<String, Integer> byService;
        private Instant timestamp;
    }

    /**
     * Callback interface for circuit breaker state changes.
     */
    @FunctionalInterface
    public interface CircuitBreakerStateChangeCallback {
        void onStateChange(CircuitBreakerState state);
    }
}
