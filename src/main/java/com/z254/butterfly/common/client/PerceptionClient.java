package com.z254.butterfly.common.client;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Client interface for PERCEPTION service - the sensory and interpretation layer.
 * 
 * <p>Provides access to:
 * <ul>
 *   <li>Events and signals detected by PERCEPTION</li>
 *   <li>Reality Integration Mesh (RIM) state</li>
 *   <li>Source and acquisition information</li>
 *   <li>Trust scores and content validation</li>
 * </ul>
 */
public interface PerceptionClient extends ButterflyServiceClient {

    @Override
    default String getServiceId() {
        return "perception";
    }

    // === RIM Operations ===

    /**
     * Get the current state of a RIM node.
     *
     * @param rimNodeId The RIM node identifier
     * @return The RIM node state
     */
    Mono<RimNode> getRimNode(String rimNodeId);

    /**
     * Query RIM nodes by namespace.
     *
     * @param namespace The namespace to query
     * @param limit Maximum number of results
     * @return Stream of RIM nodes
     */
    Flux<RimNode> getRimNodesByNamespace(String namespace, int limit);

    /**
     * Submit an observation to a RIM node.
     *
     * @param rimNodeId The RIM node identifier
     * @param observation The observation to submit
     * @return The result of the observation submission
     */
    Mono<ObservationResult> submitObservation(String rimNodeId, Map<String, Object> observation);

    // === Event Operations ===

    /**
     * Get recent events.
     *
     * @param since Only events after this timestamp
     * @param limit Maximum number of results
     * @return Stream of events
     */
    Flux<PerceptionEvent> getEvents(Instant since, int limit);

    /**
     * Get events for a specific entity.
     *
     * @param entityId The entity identifier
     * @param limit Maximum number of results
     * @return Stream of events
     */
    Flux<PerceptionEvent> getEventsByEntity(String entityId, int limit);

    // === Signal Operations ===

    /**
     * Get active signals.
     *
     * @return Stream of active signals
     */
    Flux<Signal> getActiveSignals();

    /**
     * Get signals for a specific RIM node.
     *
     * @param rimNodeId The RIM node identifier
     * @return Stream of signals
     */
    Flux<Signal> getSignalsByRimNode(String rimNodeId);

    // === Source Operations ===

    /**
     * Get source information.
     *
     * @param sourceId The source identifier
     * @return The source details
     */
    Mono<Source> getSource(String sourceId);

    /**
     * Get trust score for a source.
     *
     * @param sourceId The source identifier
     * @return The trust score
     */
    Mono<TrustScore> getSourceTrustScore(String sourceId);

    // === DTOs ===

    record RimNode(
            String rimNodeId,
            String namespace,
            String entityType,
            String localId,
            Map<String, Object> state,
            double confidence,
            Instant lastUpdated,
            List<String> connectedNodes
    ) {}

    record ObservationResult(
            String observationId,
            boolean accepted,
            String reason,
            double newConfidence
    ) {}

    record PerceptionEvent(
            String eventId,
            String eventType,
            Instant timestamp,
            String title,
            String summary,
            List<String> affectedEntities,
            double trustScore,
            Map<String, Object> metadata
    ) {}

    record Signal(
            String signalId,
            String signalType,
            String rimNodeId,
            double strength,
            double confidence,
            Instant detectedAt,
            Map<String, Object> details
    ) {}

    record Source(
            String sourceId,
            String name,
            String sourceType,
            boolean active,
            Map<String, Object> configuration
    ) {}

    record TrustScore(
            String sourceId,
            double score,
            double reliability,
            double accuracy,
            int sampleCount,
            Instant lastUpdated
    ) {}
}
