package com.z254.butterfly.common.client;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Client interface for NEXUS service - the integration cortex layer.
 * 
 * <p>Provides access to:
 * <ul>
 *   <li>Temporal Intelligence Fabric (unified queries)</li>
 *   <li>Autonomous Reasoning Engine (cross-system inference)</li>
 *   <li>Predictive Synthesis Core (strategic options)</li>
 *   <li>Evolution Controller (self-optimization)</li>
 *   <li>Learning signal submission</li>
 * </ul>
 */
public interface NexusClient extends ButterflyServiceClient {

    @Override
    default String getServiceId() {
        return "nexus";
    }

    // === Temporal Operations ===

    /**
     * Query a temporal slice at a specific time.
     *
     * @param entityId The entity identifier
     * @param timestamp The point in time
     * @return The temporal slice
     */
    Mono<TemporalSlice> getTemporalSlice(String entityId, Instant timestamp);

    /**
     * Get causal chains for an entity.
     *
     * @param entityId The entity identifier
     * @param depth Maximum depth to traverse
     * @return Stream of causal chain events
     */
    Flux<CausalChainEvent> getCausalChains(String entityId, int depth);

    /**
     * Detect temporal anomalies.
     *
     * @param entityId Optional entity filter
     * @param since Look back from this time
     * @return Stream of anomalies
     */
    Flux<TemporalAnomaly> detectAnomalies(String entityId, Instant since);

    // === Reasoning Operations ===

    /**
     * Get cross-system inferences.
     *
     * @param entityId Optional entity filter
     * @param limit Maximum results
     * @return Stream of inferences
     */
    Flux<Inference> getInferences(String entityId, int limit);

    /**
     * Get autonomous hypotheses.
     *
     * @param domain Optional domain filter
     * @param limit Maximum results
     * @return Stream of hypotheses
     */
    Flux<Hypothesis> getHypotheses(String domain, int limit);

    /**
     * Get detected contradictions.
     *
     * @return Stream of contradictions
     */
    Flux<Contradiction> getContradictions();

    // === Synthesis Operations ===

    /**
     * Get strategic options.
     *
     * @param context Context for option generation
     * @return Stream of strategic options
     */
    Flux<StrategicOption> getStrategicOptions(Map<String, Object> context);

    /**
     * Get aggregated ignorance map.
     *
     * @return The ignorance map
     */
    Mono<IgnoranceMap> getIgnoranceMap();

    // === Learning Operations ===

    /**
     * Submit a learning signal.
     *
     * @param request The learning signal request
     * @return The submission result with signal ID
     */
    Mono<LearningSignalResponse> submitLearningSignal(LearningSignalRequest request);

    // === Evolution Operations ===

    /**
     * Get ecosystem health status.
     *
     * @return The ecosystem health
     */
    Mono<EcosystemHealth> getEcosystemHealth();

    /**
     * Get integration patterns.
     *
     * @return Stream of detected patterns
     */
    Flux<IntegrationPattern> getIntegrationPatterns();

    // === DTOs ===

    record TemporalSlice(
            String sliceId,
            String entityId,
            Instant timestamp,
            Map<String, Object> perceptionState,
            Map<String, Object> capsuleState,
            Map<String, Object> odysseyState,
            double coherence,
            List<String> sourceSystems
    ) {}

    record CausalChainEvent(
            String eventId,
            String sourceSystem,
            String entityId,
            String eventType,
            Instant timestamp,
            String causedBy,
            List<String> effects,
            double confidence
    ) {}

    record TemporalAnomaly(
            String anomalyId,
            String entityId,
            String anomalyType,
            String description,
            double severity,
            Instant detectedAt,
            Map<String, Object> evidence
    ) {}

    record Inference(
            String inferenceId,
            String statement,
            double confidence,
            List<String> sourceSystems,
            List<String> supportingEvidence,
            Instant generatedAt
    ) {}

    record Hypothesis(
            String hypothesisId,
            String domain,
            String statement,
            double plausibility,
            List<String> predictions,
            List<String> testsRequired,
            Instant generatedAt
    ) {}

    record Contradiction(
            String contradictionId,
            String system1,
            String system2,
            String description,
            double severity,
            String resolutionStrategy,
            Instant detectedAt
    ) {}

    record StrategicOption(
            String optionId,
            String description,
            double expectedValue,
            double risk,
            List<String> keyDrivers,
            List<String> requiredActions,
            Map<String, Object> tradeoffs
    ) {}

    record IgnoranceMap(
            Map<String, Double> byDomain,
            Map<String, Double> byEntity,
            List<String> criticalGaps,
            double overallUncertainty
    ) {}

    record LearningSignalRequest(
            String signalType,
            String entityId,
            String sourceSystem,
            String targetSystem,
            Map<String, Object> payload,
            double confidence,
            String correlationId
    ) {}

    record LearningSignalResponse(
            String signalId,
            boolean accepted,
            String message
    ) {
        public static LearningSignalResponse fallback() {
            return new LearningSignalResponse(null, false, "NEXUS unavailable");
        }

        public boolean isFallback() {
            return signalId == null;
        }
    }

    record EcosystemHealth(
            String overallStatus,
            Map<String, ServiceStatus> services,
            List<String> activeIssues,
            double integrationScore,
            Instant assessedAt
    ) {}

    record ServiceStatus(
            String status,
            double latencyP95Ms,
            double errorRate,
            boolean circuitBreakerOpen
    ) {}

    record IntegrationPattern(
            String patternId,
            String name,
            String description,
            List<String> involvedServices,
            double frequency,
            double health,
            Instant lastObserved
    ) {}
}
