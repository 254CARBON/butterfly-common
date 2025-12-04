package com.z254.butterfly.common.client;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Client interface for SYNAPSE service - the execution engine.
 * 
 * <p>Provides access to:
 * <ul>
 *   <li>Tool registry and management</li>
 *   <li>Action execution with governance</li>
 *   <li>Workflow orchestration</li>
 *   <li>Safety controls (kill switch, rate limiting)</li>
 *   <li>Execution outcomes and provenance</li>
 * </ul>
 */
public interface SynapseClient extends ButterflyServiceClient {

    @Override
    default String getServiceId() {
        return "synapse";
    }

    // === Tool Operations ===

    /**
     * Get a tool by ID.
     *
     * @param toolId The tool identifier
     * @return The tool details
     */
    Mono<Tool> getTool(String toolId);

    /**
     * List available tools.
     *
     * @param category Optional category filter
     * @return Stream of tools
     */
    Flux<Tool> listTools(String category);

    /**
     * Register a new tool.
     *
     * @param request The tool registration request
     * @return The registered tool
     */
    Mono<Tool> registerTool(ToolRegistrationRequest request);

    // === Action Operations ===

    /**
     * Execute an action.
     *
     * @param request The action execution request
     * @return The action result
     */
    Mono<ActionResult> executeAction(ActionRequest request);

    /**
     * Get action status.
     *
     * @param actionId The action identifier
     * @return The action result
     */
    Mono<ActionResult> getAction(String actionId);

    /**
     * Query actions by various filters.
     *
     * @param rimNodeId Optional RIM node filter
     * @param platoPlanId Optional PLATO plan filter
     * @param from Start time
     * @param to End time
     * @return Stream of action results
     */
    Flux<ActionResult> queryActions(String rimNodeId, String platoPlanId, Instant from, Instant to);

    /**
     * Cancel an action.
     *
     * @param actionId The action identifier
     * @return Mono indicating completion
     */
    Mono<Void> cancelAction(String actionId);

    // === Workflow Operations ===

    /**
     * Create a workflow.
     *
     * @param request The workflow creation request
     * @return The created workflow
     */
    Mono<Workflow> createWorkflow(WorkflowRequest request);

    /**
     * Get workflow status.
     *
     * @param workflowId The workflow identifier
     * @return The workflow details
     */
    Mono<Workflow> getWorkflow(String workflowId);

    /**
     * Execute a workflow.
     *
     * @param workflowId The workflow identifier
     * @return Stream of step results
     */
    Flux<WorkflowStepResult> executeWorkflow(String workflowId);

    // === Safety Operations ===

    /**
     * Get current safety status.
     *
     * @return The safety status
     */
    Mono<SafetyStatus> getSafetyStatus();

    /**
     * Activate kill switch (emergency stop).
     *
     * @param reason The reason for activation
     * @return Mono indicating completion
     */
    Mono<Void> activateKillSwitch(String reason);

    /**
     * Get rate limit status.
     *
     * @return The rate limit status
     */
    Mono<RateLimitStatus> getRateLimitStatus();

    // === Outcome Operations ===

    /**
     * Query outcome events.
     *
     * @param rimNodeId Optional RIM node filter
     * @param from Start time
     * @param limit Maximum results
     * @return Stream of outcome events
     */
    Flux<OutcomeEvent> queryOutcomes(String rimNodeId, Instant from, int limit);

    // === DTOs ===

    record Tool(
            String toolId,
            String name,
            String description,
            String connectorType,
            String category,
            Map<String, Object> configuration,
            RiskLevel riskLevel,
            boolean enabled,
            Instant registeredAt
    ) {}

    enum RiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    record ToolRegistrationRequest(
            String name,
            String description,
            String connectorType,
            Map<String, Object> configuration,
            RiskLevel riskLevel
    ) {}

    record ActionRequest(
            String toolId,
            Map<String, Object> parameters,
            String platoPlanId,
            String platoStepId,
            String rimNodeId,
            String correlationId,
            ExecutionMode executionMode
    ) {}

    enum ExecutionMode {
        PRODUCTION, SANDBOX, DRY_RUN, STAGED
    }

    record ActionResult(
            String actionId,
            String toolId,
            String status,  // PENDING, RUNNING, SUCCESS, FAILURE, TIMEOUT, CANCELLED
            Map<String, Object> result,
            ActionProvenance provenance,
            long durationMs,
            String errorCode,
            String errorMessage,
            Instant startedAt,
            Instant completedAt
    ) {}

    record ActionProvenance(
            String platoPlanId,
            String platoStepId,
            String rimNodeId,
            String capsuleId,
            String nexusLearningSignalId,
            String correlationId
    ) {}

    record WorkflowRequest(
            String name,
            String description,
            List<WorkflowStep> steps,
            Map<String, Object> context
    ) {}

    record WorkflowStep(
            String stepId,
            String toolId,
            Map<String, Object> parameters,
            List<String> dependsOn,
            String condition
    ) {}

    record Workflow(
            String workflowId,
            String name,
            String status,  // CREATED, RUNNING, COMPLETED, FAILED, CANCELLED
            List<WorkflowStep> steps,
            Map<String, Object> context,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt
    ) {}

    record WorkflowStepResult(
            String stepId,
            String status,
            Map<String, Object> result,
            long durationMs,
            String error
    ) {}

    record SafetyStatus(
            boolean killSwitchActive,
            boolean paused,
            String executionMode,
            int concurrentActions,
            int actionsPerMinute,
            double blastRadius,
            List<String> activeAlerts
    ) {}

    record RateLimitStatus(
            Map<String, TierStatus> byTier,
            int globalRequestsPerMinute,
            int globalDailyQuotaUsed
    ) {}

    record TierStatus(
            String tier,
            int requestsPerMinute,
            int dailyQuotaUsed,
            int dailyQuotaLimit
    ) {}

    record OutcomeEvent(
            String outcomeId,
            String actionId,
            String rimNodeId,
            String outcomeType,
            Map<String, Object> effects,
            double confidence,
            Instant recordedAt
    ) {}
}
