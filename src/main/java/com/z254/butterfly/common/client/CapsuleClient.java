package com.z254.butterfly.common.client;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Client interface for CAPSULE service - the 4D atomic history storage.
 * 
 * <p>Provides access to:
 * <ul>
 *   <li>CAPSULE storage and retrieval</li>
 *   <li>Temporal queries and history</li>
 *   <li>Lineage and provenance tracking</li>
 *   <li>Multi-vantage perspective support</li>
 * </ul>
 */
public interface CapsuleClient extends ButterflyServiceClient {

    @Override
    default String getServiceId() {
        return "capsule";
    }

    // === Capsule Operations ===

    /**
     * Get a capsule by ID.
     *
     * @param capsuleId The capsule identifier
     * @return The capsule details
     */
    Mono<Capsule> getCapsule(String capsuleId);

    /**
     * Query capsules by scope.
     *
     * @param scopeId The scope identifier (RIM node ID)
     * @param from Start of time range
     * @param to End of time range
     * @param limit Maximum results
     * @return Stream of capsules
     */
    Flux<Capsule> getCapsulesByScope(String scopeId, Instant from, Instant to, int limit);

    /**
     * Store a new capsule.
     *
     * @param request The capsule creation request
     * @return The created capsule
     */
    Mono<Capsule> createCapsule(CapsuleCreateRequest request);

    // === Temporal Operations ===

    /**
     * Get a temporal slice at a specific point in time.
     *
     * @param scopeId The scope identifier
     * @param timestamp The point in time
     * @return The temporal slice
     */
    Mono<TemporalSlice> getTemporalSlice(String scopeId, Instant timestamp);

    /**
     * Get historical state evolution for an entity.
     *
     * @param scopeId The scope identifier
     * @param from Start time
     * @param to End time
     * @param resolution Resolution in seconds
     * @return Stream of temporal slices
     */
    Flux<TemporalSlice> getStateEvolution(String scopeId, Instant from, Instant to, int resolution);

    // === Lineage Operations ===

    /**
     * Get the lineage chain for a capsule.
     *
     * @param capsuleId The capsule identifier
     * @param depth Maximum depth to traverse
     * @return The lineage chain
     */
    Mono<LineageChain> getLineage(String capsuleId, int depth);

    /**
     * Get capsules derived from a source capsule.
     *
     * @param capsuleId The source capsule identifier
     * @return Stream of derived capsules
     */
    Flux<Capsule> getDerivedCapsules(String capsuleId);

    // === Counterfactual Operations ===

    /**
     * Get counterfactual scenarios for an entity.
     *
     * @param scopeId The scope identifier
     * @param limit Maximum results
     * @return Stream of counterfactual scenarios
     */
    Flux<Counterfactual> getCounterfactuals(String scopeId, int limit);

    // === DTOs ===

    record Capsule(
            String capsuleId,
            String scopeId,
            Instant timestamp,
            int resolution,
            String vantageMode,
            String idempotencyKey,
            CapsuleContent content,
            Map<String, Object> metadata,
            Instant createdAt
    ) {}

    record CapsuleContent(
            Map<String, Object> configuration,
            Map<String, Object> dynamics,
            Map<String, Object> agency,
            Map<String, Object> counterfactual,
            Map<String, Object> meta
    ) {}

    record CapsuleCreateRequest(
            String scopeId,
            int resolution,
            String vantageMode,
            String idempotencyKey,
            CapsuleContent content,
            Map<String, Object> metadata
    ) {}

    record TemporalSlice(
            String sliceId,
            String scopeId,
            Instant timestamp,
            Map<String, Object> state,
            double confidence,
            List<String> contributingCapsuleIds
    ) {}

    record LineageChain(
            String rootCapsuleId,
            List<LineageNode> nodes,
            int depth
    ) {}

    record LineageNode(
            String capsuleId,
            String parentCapsuleId,
            String relationship,
            Instant timestamp
    ) {}

    record Counterfactual(
            String counterfactualId,
            String scopeId,
            String hypothesis,
            Map<String, Object> alternateState,
            double probability,
            List<String> supportingEvidence
    ) {}
}
