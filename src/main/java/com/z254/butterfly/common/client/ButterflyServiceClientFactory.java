package com.z254.butterfly.common.client;

import com.z254.butterfly.common.resilience.DegradationAwareClient;
import com.z254.butterfly.common.resilience.DegradationClientFactory;
import com.z254.butterfly.common.resilience.DegradationState;
import com.z254.butterfly.common.resilience.ServiceHealth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating typed BUTTERFLY ecosystem service clients.
 * 
 * <p>This factory provides pre-configured, typed clients for all BUTTERFLY services
 * with built-in resilience features:
 * <ul>
 *   <li>Circuit breaker protection</li>
 *   <li>Retry with exponential backoff</li>
 *   <li>Health-based request blocking</li>
 *   <li>Tenant context propagation</li>
 *   <li>Metrics and tracing</li>
 * </ul>
 * 
 * <p>Usage:
 * <pre>
 * &#64;Autowired
 * private ButterflyServiceClientFactory clientFactory;
 * 
 * public void example() {
 *     PerceptionClient perception = clientFactory.perceptionClient();
 *     perception.getRimNode("rim:entity:finance:EURUSD")
 *         .subscribe(node -&gt; log.info("Got RIM node: {}", node));
 * }
 * </pre>
 */
@Component
@EnableConfigurationProperties(ServiceClientProperties.class)
@RequiredArgsConstructor
@Slf4j
public class ButterflyServiceClientFactory {

    private final DegradationClientFactory degradationClientFactory;
    private final ServiceClientProperties properties;

    private final Map<String, Object> clientCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("ButterflyServiceClientFactory initialized with service URLs: " +
                        "perception={}, capsule={}, odyssey={}, plato={}, nexus={}, synapse={}",
                properties.getPerception().getUrl(),
                properties.getCapsule().getUrl(),
                properties.getOdyssey().getUrl(),
                properties.getPlato().getUrl(),
                properties.getNexus().getUrl(),
                properties.getSynapse().getUrl());
    }

    /**
     * Get or create a PERCEPTION client.
     */
    @SuppressWarnings("unchecked")
    public PerceptionClient perceptionClient() {
        return (PerceptionClient) clientCache.computeIfAbsent("perception", 
                k -> createPerceptionClient(properties.getPerception()));
    }

    /**
     * Get or create a CAPSULE client.
     */
    @SuppressWarnings("unchecked")
    public CapsuleClient capsuleClient() {
        return (CapsuleClient) clientCache.computeIfAbsent("capsule",
                k -> createCapsuleClient(properties.getCapsule()));
    }

    /**
     * Get or create an ODYSSEY client.
     */
    @SuppressWarnings("unchecked")
    public OdysseyClient odysseyClient() {
        return (OdysseyClient) clientCache.computeIfAbsent("odyssey",
                k -> createOdysseyClient(properties.getOdyssey()));
    }

    /**
     * Get or create a PLATO client.
     */
    @SuppressWarnings("unchecked")
    public PlatoClient platoClient() {
        return (PlatoClient) clientCache.computeIfAbsent("plato",
                k -> createPlatoClient(properties.getPlato()));
    }

    /**
     * Get or create a NEXUS client.
     */
    @SuppressWarnings("unchecked")
    public NexusClient nexusClient() {
        return (NexusClient) clientCache.computeIfAbsent("nexus",
                k -> createNexusClient(properties.getNexus()));
    }

    /**
     * Get or create a SYNAPSE client.
     */
    @SuppressWarnings("unchecked")
    public SynapseClient synapseClient() {
        return (SynapseClient) clientCache.computeIfAbsent("synapse",
                k -> createSynapseClient(properties.getSynapse()));
    }

    /**
     * Clear the client cache (for testing or reconfiguration).
     */
    public void clearCache() {
        clientCache.clear();
        degradationClientFactory.clearCache();
    }

    // === Client Creation Methods ===

    private PerceptionClient createPerceptionClient(ServiceClientProperties.ServiceConfig config) {
        DegradationAwareClient client = degradationClientFactory.getClient(
                config.getServiceId(),
                config.getUrl(),
                config.toDegradationProperties());

        return new PerceptionClientImpl(client);
    }

    private CapsuleClient createCapsuleClient(ServiceClientProperties.ServiceConfig config) {
        DegradationAwareClient client = degradationClientFactory.getClient(
                config.getServiceId(),
                config.getUrl(),
                config.toDegradationProperties());

        return new CapsuleClientImpl(client);
    }

    private OdysseyClient createOdysseyClient(ServiceClientProperties.ServiceConfig config) {
        DegradationAwareClient client = degradationClientFactory.getClient(
                config.getServiceId(),
                config.getUrl(),
                config.toDegradationProperties());

        return new OdysseyClientImpl(client);
    }

    private PlatoClient createPlatoClient(ServiceClientProperties.ServiceConfig config) {
        DegradationAwareClient client = degradationClientFactory.getClient(
                config.getServiceId(),
                config.getUrl(),
                config.toDegradationProperties());

        return new PlatoClientImpl(client);
    }

    private NexusClient createNexusClient(ServiceClientProperties.ServiceConfig config) {
        DegradationAwareClient client = degradationClientFactory.getClient(
                config.getServiceId(),
                config.getUrl(),
                config.toDegradationProperties());

        return new NexusClientImpl(client);
    }

    private SynapseClient createSynapseClient(ServiceClientProperties.ServiceConfig config) {
        DegradationAwareClient client = degradationClientFactory.getClient(
                config.getServiceId(),
                config.getUrl(),
                config.toDegradationProperties());

        return new SynapseClientImpl(client);
    }

    // === Client Implementations ===

    /**
     * Base implementation for service clients.
     */
    @RequiredArgsConstructor
    private static abstract class BaseClientImpl implements ButterflyServiceClient {
        protected final DegradationAwareClient client;

        @Override
        public Mono<Boolean> isHealthy() {
            return health().map(HealthStatus::isUp).onErrorReturn(false);
        }

        @Override
        public Mono<HealthStatus> health() {
            return client.get("/actuator/health", Map.class)
                    .map(response -> {
                        String status = (String) response.getOrDefault("status", "UNKNOWN");
                        return new HealthStatus(status, getServiceId(), response);
                    })
                    .onErrorResume(e -> Mono.just(HealthStatus.down(getServiceId(), e)));
        }

        @Override
        public DegradationState getDegradationState() {
            ServiceHealth health = client.getServiceHealth();
            return health != null ? health.getState() : DegradationState.UNKNOWN;
        }
    }

    /**
     * PERCEPTION client implementation.
     */
    private static class PerceptionClientImpl extends BaseClientImpl implements PerceptionClient {
        public PerceptionClientImpl(DegradationAwareClient client) {
            super(client);
        }

        @Override
        public Mono<RimNode> getRimNode(String rimNodeId) {
            return client.get("/api/v1/rim/nodes/" + rimNodeId, RimNode.class);
        }

        @Override
        public Flux<RimNode> getRimNodesByNamespace(String namespace, int limit) {
            return client.get("/api/v1/rim/nodes?namespace=" + namespace + "&limit=" + limit, RimNode[].class)
                    .flatMapMany(Flux::fromArray);
        }

        @Override
        public Mono<ObservationResult> submitObservation(String rimNodeId, Map<String, Object> observation) {
            return client.post("/api/v1/rim/nodes/" + rimNodeId + "/observations", observation, ObservationResult.class);
        }

        @Override
        public Flux<PerceptionEvent> getEvents(Instant since, int limit) {
            return client.get("/api/v1/events?since=" + since.toEpochMilli() + "&limit=" + limit, PerceptionEvent[].class)
                    .flatMapMany(Flux::fromArray);
        }

        @Override
        public Flux<PerceptionEvent> getEventsByEntity(String entityId, int limit) {
            return client.get("/api/v1/events?entityId=" + entityId + "&limit=" + limit, PerceptionEvent[].class)
                    .flatMapMany(Flux::fromArray);
        }

        @Override
        public Flux<Signal> getActiveSignals() {
            return client.get("/api/v1/signals?active=true", Signal[].class)
                    .flatMapMany(Flux::fromArray);
        }

        @Override
        public Flux<Signal> getSignalsByRimNode(String rimNodeId) {
            return client.get("/api/v1/signals?rimNodeId=" + rimNodeId, Signal[].class)
                    .flatMapMany(Flux::fromArray);
        }

        @Override
        public Mono<Source> getSource(String sourceId) {
            return client.get("/api/v1/sources/" + sourceId, Source.class);
        }

        @Override
        public Mono<TrustScore> getSourceTrustScore(String sourceId) {
            return client.get("/api/v1/sources/" + sourceId + "/trust", TrustScore.class);
        }
    }

    /**
     * CAPSULE client implementation.
     */
    private static class CapsuleClientImpl extends BaseClientImpl implements CapsuleClient {
        public CapsuleClientImpl(DegradationAwareClient client) {
            super(client);
        }

        @Override
        public Mono<Capsule> getCapsule(String capsuleId) {
            return client.get("/api/v1/capsules/" + capsuleId, Capsule.class);
        }

        @Override
        public Flux<Capsule> getCapsulesByScope(String scopeId, Instant from, Instant to, int limit) {
            String url = String.format("/api/v1/capsules?scopeId=%s&from=%d&to=%d&limit=%d",
                    scopeId, from.toEpochMilli(), to.toEpochMilli(), limit);
            return client.get(url, Capsule[].class).flatMapMany(Flux::fromArray);
        }

        @Override
        public Mono<Capsule> createCapsule(CapsuleCreateRequest request) {
            return client.post("/api/v1/capsules", request, Capsule.class);
        }

        @Override
        public Mono<TemporalSlice> getTemporalSlice(String scopeId, Instant timestamp) {
            return client.get("/api/v1/temporal/slice?scopeId=" + scopeId + "&timestamp=" + timestamp.toEpochMilli(),
                    TemporalSlice.class);
        }

        @Override
        public Flux<TemporalSlice> getStateEvolution(String scopeId, Instant from, Instant to, int resolution) {
            String url = String.format("/api/v1/temporal/evolution?scopeId=%s&from=%d&to=%d&resolution=%d",
                    scopeId, from.toEpochMilli(), to.toEpochMilli(), resolution);
            return client.get(url, TemporalSlice[].class).flatMapMany(Flux::fromArray);
        }

        @Override
        public Mono<LineageChain> getLineage(String capsuleId, int depth) {
            return client.get("/api/v1/lineage/" + capsuleId + "?depth=" + depth, LineageChain.class);
        }

        @Override
        public Flux<Capsule> getDerivedCapsules(String capsuleId) {
            return client.get("/api/v1/lineage/" + capsuleId + "/derived", Capsule[].class)
                    .flatMapMany(Flux::fromArray);
        }

        @Override
        public Flux<Counterfactual> getCounterfactuals(String scopeId, int limit) {
            return client.get("/api/v1/counterfactuals?scopeId=" + scopeId + "&limit=" + limit, Counterfactual[].class)
                    .flatMapMany(Flux::fromArray);
        }
    }

    /**
     * ODYSSEY client implementation.
     */
    private static class OdysseyClientImpl extends BaseClientImpl implements OdysseyClient {
        public OdysseyClientImpl(DegradationAwareClient client) {
            super(client);
        }

        @Override
        public Mono<WorldState> getWorldState() {
            return client.get("/api/v1/world-state", WorldState.class);
        }

        @Override
        public Mono<Entity> getEntity(String entityId) {
            return client.get("/api/v1/entities/" + entityId, Entity.class);
        }

        @Override
        public Flux<NarrativePath> getPaths(String scopeId, String horizon) {
            StringBuilder url = new StringBuilder("/api/v1/paths?");
            if (scopeId != null) url.append("scopeId=").append(scopeId).append("&");
            if (horizon != null) url.append("horizon=").append(horizon);
            return client.get(url.toString(), NarrativePath[].class).flatMapMany(Flux::fromArray);
        }

        @Override
        public Mono<NarrativePath> getPath(String pathId) {
            return client.get("/api/v1/paths/" + pathId, NarrativePath.class);
        }

        @Override
        public Mono<Void> updatePathWeight(String pathId, double newWeight) {
            return client.put("/api/v1/paths/" + pathId + "/weight", Map.of("weight", newWeight), Void.class);
        }

        @Override
        public Mono<Actor> getActor(String actorId) {
            return client.get("/api/v1/actors/" + actorId, Actor.class);
        }

        @Override
        public Flux<Actor> getActors(String scopeId) {
            String url = scopeId != null ? "/api/v1/actors?scopeId=" + scopeId : "/api/v1/actors";
            return client.get(url, Actor[].class).flatMapMany(Flux::fromArray);
        }

        @Override
        public Mono<Void> updateActorModel(String actorId, String parameter, double value) {
            return client.put("/api/v1/actors/" + actorId + "/model",
                    Map.of("parameter", parameter, "value", value), Void.class);
        }

        @Override
        public Mono<IgnoranceSurface> getIgnoranceSurface() {
            return client.get("/api/v1/ignorance", IgnoranceSurface.class);
        }

        @Override
        public Mono<Double> getStrategyWeight(String strategyId) {
            return client.get("/api/v1/strategies/" + strategyId + "/weight", Map.class)
                    .map(m -> ((Number) m.get("weight")).doubleValue());
        }

        @Override
        public Mono<Void> updateStrategyWeight(String strategyId, double newWeight) {
            return client.put("/api/v1/strategies/" + strategyId + "/weight",
                    Map.of("weight", newWeight), Void.class);
        }

        @Override
        public Flux<Episode> getEpisodes(String scopeId, int limit) {
            String url = scopeId != null
                    ? "/api/v1/episodes?scopeId=" + scopeId + "&limit=" + limit
                    : "/api/v1/episodes?limit=" + limit;
            return client.get(url, Episode[].class).flatMapMany(Flux::fromArray);
        }
    }

    /**
     * PLATO client implementation.
     */
    private static class PlatoClientImpl extends BaseClientImpl implements PlatoClient {
        public PlatoClientImpl(DegradationAwareClient client) {
            super(client);
        }

        @Override
        public Mono<Policy> getPolicy(String policyId) {
            return client.get("/api/v1/policies/" + policyId, Policy.class);
        }

        @Override
        public Flux<Policy> listPolicies(String category) {
            String url = category != null ? "/api/v1/policies?category=" + category : "/api/v1/policies";
            return client.get(url, Policy[].class).flatMapMany(Flux::fromArray);
        }

        @Override
        public Mono<PolicyEvaluationResult> evaluatePolicy(PolicyEvaluationRequest request) {
            return client.post("/api/v1/policies/evaluate", request, PolicyEvaluationResult.class);
        }

        @Override
        public Mono<Spec> getSpec(String specId) {
            return client.get("/api/v1/specs/" + specId, Spec.class);
        }

        @Override
        public Flux<Spec> listSpecs(String type) {
            String url = type != null ? "/api/v1/specs?type=" + type : "/api/v1/specs";
            return client.get(url, Spec[].class).flatMapMany(Flux::fromArray);
        }

        @Override
        public Mono<Plan> getPlan(String planId) {
            return client.get("/api/v1/plans/" + planId, Plan.class);
        }

        @Override
        public Flux<Plan> listPlans(String status) {
            String url = status != null ? "/api/v1/plans?status=" + status : "/api/v1/plans";
            return client.get(url, Plan[].class).flatMapMany(Flux::fromArray);
        }

        @Override
        public Mono<Plan> createPlan(PlanCreateRequest request) {
            return client.post("/api/v1/plans", request, Plan.class);
        }

        @Override
        public Mono<ApprovalResult> requestApproval(String planId, ApprovalRequest request) {
            return client.post("/api/v1/plans/" + planId + "/approve", request, ApprovalResult.class);
        }

        @Override
        public Mono<GovernanceStatus> getGovernanceStatus(String entityId) {
            return client.get("/api/v1/governance/status/" + entityId, GovernanceStatus.class);
        }

        @Override
        public Mono<Void> recordDecisionFeedback(String decisionId, DecisionFeedback feedback) {
            return client.post("/api/v1/decisions/" + decisionId + "/feedback", feedback, Void.class);
        }
    }

    /**
     * NEXUS client implementation.
     */
    private static class NexusClientImpl extends BaseClientImpl implements NexusClient {
        public NexusClientImpl(DegradationAwareClient client) {
            super(client);
        }

        @Override
        public Mono<TemporalSlice> getTemporalSlice(String entityId, Instant timestamp) {
            return client.get("/api/v1/temporal/slice?entityId=" + entityId + "&timestamp=" + timestamp.toEpochMilli(),
                    TemporalSlice.class);
        }

        @Override
        public Flux<CausalChainEvent> getCausalChains(String entityId, int depth) {
            return client.get("/api/v1/temporal/causal-chains?entityId=" + entityId + "&depth=" + depth,
                    CausalChainEvent[].class).flatMapMany(Flux::fromArray);
        }

        @Override
        public Flux<TemporalAnomaly> detectAnomalies(String entityId, Instant since) {
            StringBuilder url = new StringBuilder("/api/v1/temporal/anomalies?since=").append(since.toEpochMilli());
            if (entityId != null) url.append("&entityId=").append(entityId);
            return client.get(url.toString(), TemporalAnomaly[].class).flatMapMany(Flux::fromArray);
        }

        @Override
        public Flux<Inference> getInferences(String entityId, int limit) {
            StringBuilder url = new StringBuilder("/api/v1/reasoning/inferences?limit=").append(limit);
            if (entityId != null) url.append("&entityId=").append(entityId);
            return client.get(url.toString(), Inference[].class).flatMapMany(Flux::fromArray);
        }

        @Override
        public Flux<Hypothesis> getHypotheses(String domain, int limit) {
            StringBuilder url = new StringBuilder("/api/v1/reasoning/hypotheses?limit=").append(limit);
            if (domain != null) url.append("&domain=").append(domain);
            return client.get(url.toString(), Hypothesis[].class).flatMapMany(Flux::fromArray);
        }

        @Override
        public Flux<Contradiction> getContradictions() {
            return client.get("/api/v1/reasoning/contradictions", Contradiction[].class)
                    .flatMapMany(Flux::fromArray);
        }

        @Override
        public Flux<StrategicOption> getStrategicOptions(Map<String, Object> context) {
            return client.post("/api/v1/synthesis/options", context, StrategicOption[].class)
                    .flatMapMany(Flux::fromArray);
        }

        @Override
        public Mono<IgnoranceMap> getIgnoranceMap() {
            return client.get("/api/v1/synthesis/ignorance", IgnoranceMap.class);
        }

        @Override
        public Mono<LearningSignalResponse> submitLearningSignal(LearningSignalRequest request) {
            return client.post("/api/v1/learning/signal", request, LearningSignalResponse.class)
                    .onErrorReturn(LearningSignalResponse.fallback());
        }

        @Override
        public Mono<EcosystemHealth> getEcosystemHealth() {
            return client.get("/api/v1/evolution/health", EcosystemHealth.class);
        }

        @Override
        public Flux<IntegrationPattern> getIntegrationPatterns() {
            return client.get("/api/v1/evolution/patterns", IntegrationPattern[].class)
                    .flatMapMany(Flux::fromArray);
        }
    }

    /**
     * SYNAPSE client implementation.
     */
    private static class SynapseClientImpl extends BaseClientImpl implements SynapseClient {
        public SynapseClientImpl(DegradationAwareClient client) {
            super(client);
        }

        @Override
        public Mono<Tool> getTool(String toolId) {
            return client.get("/api/v1/tools/" + toolId, Tool.class);
        }

        @Override
        public Flux<Tool> listTools(String category) {
            String url = category != null ? "/api/v1/tools?category=" + category : "/api/v1/tools";
            return client.get(url, Tool[].class).flatMapMany(Flux::fromArray);
        }

        @Override
        public Mono<Tool> registerTool(ToolRegistrationRequest request) {
            return client.post("/api/v1/tools", request, Tool.class);
        }

        @Override
        public Mono<ActionResult> executeAction(ActionRequest request) {
            return client.post("/api/v1/actions", request, ActionResult.class);
        }

        @Override
        public Mono<ActionResult> getAction(String actionId) {
            return client.get("/api/v1/actions/" + actionId, ActionResult.class);
        }

        @Override
        public Flux<ActionResult> queryActions(String rimNodeId, String platoPlanId, Instant from, Instant to) {
            StringBuilder url = new StringBuilder("/api/v1/actions?from=").append(from.toEpochMilli())
                    .append("&to=").append(to.toEpochMilli());
            if (rimNodeId != null) url.append("&rimNodeId=").append(rimNodeId);
            if (platoPlanId != null) url.append("&platoPlanId=").append(platoPlanId);
            return client.get(url.toString(), ActionResult[].class).flatMapMany(Flux::fromArray);
        }

        @Override
        public Mono<Void> cancelAction(String actionId) {
            return client.post("/api/v1/actions/" + actionId + "/cancel", null, Void.class);
        }

        @Override
        public Mono<Workflow> createWorkflow(WorkflowRequest request) {
            return client.post("/api/v1/workflows", request, Workflow.class);
        }

        @Override
        public Mono<Workflow> getWorkflow(String workflowId) {
            return client.get("/api/v1/workflows/" + workflowId, Workflow.class);
        }

        @Override
        public Flux<WorkflowStepResult> executeWorkflow(String workflowId) {
            return client.post("/api/v1/workflows/" + workflowId + "/execute", null, WorkflowStepResult[].class)
                    .flatMapMany(Flux::fromArray);
        }

        @Override
        public Mono<SafetyStatus> getSafetyStatus() {
            return client.get("/api/v1/safety/status", SafetyStatus.class);
        }

        @Override
        public Mono<Void> activateKillSwitch(String reason) {
            return client.post("/api/v1/safety/kill-switch", Map.of("reason", reason), Void.class);
        }

        @Override
        public Mono<RateLimitStatus> getRateLimitStatus() {
            return client.get("/api/v1/safety/limits", RateLimitStatus.class);
        }

        @Override
        public Flux<OutcomeEvent> queryOutcomes(String rimNodeId, Instant from, int limit) {
            StringBuilder url = new StringBuilder("/api/v1/outcomes?from=").append(from.toEpochMilli())
                    .append("&limit=").append(limit);
            if (rimNodeId != null) url.append("&rimNodeId=").append(rimNodeId);
            return client.get(url.toString(), OutcomeEvent[].class).flatMapMany(Flux::fromArray);
        }
    }
}
