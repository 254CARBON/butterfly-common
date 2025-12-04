package com.z254.butterfly.common.client;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Client interface for ODYSSEY service - the strategic cognition engine.
 * 
 * <p>Provides access to:
 * <ul>
 *   <li>World state modeling (World Field)</li>
 *   <li>Narrative future projection (Path Field)</li>
 *   <li>Actor/player modeling (Player Field)</li>
 *   <li>Ignorance surface tracking</li>
 *   <li>Experience learning from past episodes</li>
 * </ul>
 */
public interface OdysseyClient extends ButterflyServiceClient {

    @Override
    default String getServiceId() {
        return "odyssey";
    }

    // === World State Operations ===

    /**
     * Get the current world state.
     *
     * @return The current world state snapshot
     */
    Mono<WorldState> getWorldState();

    /**
     * Get entity details by ID.
     *
     * @param entityId The entity identifier (canonical RIM node ID)
     * @return The entity details
     */
    Mono<Entity> getEntity(String entityId);

    // === Narrative Path Operations ===

    /**
     * Get narrative paths for a scope.
     *
     * @param scopeId Optional scope filter (null for all)
     * @param horizon Optional horizon filter (SHORT_TERM, MEDIUM_TERM, LONG_TERM)
     * @return Stream of narrative paths
     */
    Flux<NarrativePath> getPaths(String scopeId, String horizon);

    /**
     * Get a specific narrative path.
     *
     * @param pathId The path identifier
     * @return The narrative path details
     */
    Mono<NarrativePath> getPath(String pathId);

    /**
     * Update path weight based on realized outcomes.
     *
     * @param pathId The path identifier
     * @param newWeight The new weight (0.0 to 1.0)
     * @return Mono indicating completion
     */
    Mono<Void> updatePathWeight(String pathId, double newWeight);

    // === Actor Operations ===

    /**
     * Get actor model by ID.
     *
     * @param actorId The actor identifier
     * @return The actor model details
     */
    Mono<Actor> getActor(String actorId);

    /**
     * Get all actors for a scope.
     *
     * @param scopeId Optional scope filter
     * @return Stream of actors
     */
    Flux<Actor> getActors(String scopeId);

    /**
     * Update an actor's behavioral model parameter.
     *
     * @param actorId The actor identifier
     * @param parameter The parameter name
     * @param value The new value
     * @return Mono indicating completion
     */
    Mono<Void> updateActorModel(String actorId, String parameter, double value);

    // === Ignorance Surface Operations ===

    /**
     * Get the ignorance surface.
     *
     * @return The ignorance surface from ODYSSEY
     */
    Mono<IgnoranceSurface> getIgnoranceSurface();

    // === Strategy Operations ===

    /**
     * Get the current weight of a strategy.
     *
     * @param strategyId The strategy identifier
     * @return The current strategy weight
     */
    Mono<Double> getStrategyWeight(String strategyId);

    /**
     * Update strategy weight based on action outcomes.
     *
     * @param strategyId The strategy identifier
     * @param newWeight The new weight (0.0 to 1.0)
     * @return Mono indicating completion
     */
    Mono<Void> updateStrategyWeight(String strategyId, double newWeight);

    // === Experience Operations ===

    /**
     * Get experience field episodes.
     *
     * @param scopeId Optional scope filter
     * @param limit Maximum episodes to return
     * @return Stream of historical episodes
     */
    Flux<Episode> getEpisodes(String scopeId, int limit);

    // === DTOs ===

    record WorldState(
            String snapshotId,
            Instant timestamp,
            List<Entity> entities,
            List<Relationship> relationships,
            Map<String, Object> aggregateMetrics
    ) {}

    record Entity(
            String entityId,
            String entityType,
            String name,
            Map<String, Object> attributes,
            StressMetrics stress,
            List<String> relatedPathIds
    ) {}

    record StressMetrics(
            double stressScore,
            double bufferRemaining,
            double fragility,
            String trend  // INCREASING, DECREASING, STABLE
    ) {}

    record Relationship(
            String relationshipId,
            String fromEntityId,
            String toEntityId,
            String relationshipType,
            double strength,
            Map<String, Object> attributes
    ) {}

    record NarrativePath(
            String pathId,
            String description,
            String horizon,  // SHORT_TERM, MEDIUM_TERM, LONG_TERM
            double weight,
            double confidence,
            List<String> keyDrivers,
            List<TippingPoint> tippingPoints,
            Map<String, Object> implications,
            String schoolId
    ) {}

    record TippingPoint(
            String tippingId,
            String label,
            String description,
            double distanceState,
            Long distanceTimeSeconds,
            String directionOfTravel,  // APPROACHING, RECEDING, HOVERING
            String accelerationSign    // INCREASING_RISK, DECREASING_RISK
    ) {}

    record Actor(
            String actorId,
            String name,
            String actorType,  // REGULATOR, SOVEREIGN, COUNTERPARTY, ALLIANCE
            List<Objective> objectives,
            BehavioralProfile behavioralProfile,
            List<String> relatedPathIds
    ) {}

    record Objective(
            String objectiveId,
            String key,
            String targetDirection,  // INCREASE, DECREASE, MAINTAIN
            double priority
    ) {}

    record BehavioralProfile(
            double riskTolerance,
            String timeHorizon,
            double predictability,
            double coordination
    ) {}

    record IgnoranceSurface(
            List<IgnoranceRegion> regions,
            double overallIgnorance,
            List<String> highUncertaintyNodeIds,
            Map<String, Double> byDomain
    ) {}

    record IgnoranceRegion(
            String regionId,
            String description,
            double severity,
            List<String> affectedEntityIds,
            String recommendedAction
    ) {}

    record Episode(
            String episodeId,
            Instant timestamp,
            String context,
            Map<String, Object> worldConfiguration,
            Decision decision,
            Outcome outcome
    ) {}

    record Decision(
            String chosenOption,
            String rationale,
            Map<String, Object> predictionMade
    ) {}

    record Outcome(
            Map<String, Object> actualResult,
            List<String> secondOrderEffects,
            long timeToRealizationMs
    ) {}
}
