package com.z254.butterfly.common.client;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Client interface for PLATO service - the governance and intelligence layer.
 * 
 * <p>Provides access to:
 * <ul>
 *   <li>Governance specifications and policies</li>
 *   <li>Plans and approval workflows</li>
 *   <li>AI engine coordination (SynthPlane, FeatureForge, etc.)</li>
 *   <li>Evidence planning and proof generation</li>
 * </ul>
 */
public interface PlatoClient extends ButterflyServiceClient {

    @Override
    default String getServiceId() {
        return "plato";
    }

    // === Policy Operations ===

    /**
     * Get a policy by ID.
     *
     * @param policyId The policy identifier
     * @return The policy details
     */
    Mono<Policy> getPolicy(String policyId);

    /**
     * List active policies.
     *
     * @param category Optional category filter
     * @return Stream of policies
     */
    Flux<Policy> listPolicies(String category);

    /**
     * Evaluate an action against policies.
     *
     * @param request The evaluation request
     * @return The evaluation result
     */
    Mono<PolicyEvaluationResult> evaluatePolicy(PolicyEvaluationRequest request);

    // === Spec Operations ===

    /**
     * Get a specification by ID.
     *
     * @param specId The specification identifier
     * @return The specification details
     */
    Mono<Spec> getSpec(String specId);

    /**
     * List specifications.
     *
     * @param type Optional type filter
     * @return Stream of specifications
     */
    Flux<Spec> listSpecs(String type);

    // === Plan Operations ===

    /**
     * Get a plan by ID.
     *
     * @param planId The plan identifier
     * @return The plan details
     */
    Mono<Plan> getPlan(String planId);

    /**
     * List plans by status.
     *
     * @param status Optional status filter (PENDING, APPROVED, REJECTED, EXECUTING, COMPLETED)
     * @return Stream of plans
     */
    Flux<Plan> listPlans(String status);

    /**
     * Create a new plan.
     *
     * @param request The plan creation request
     * @return The created plan
     */
    Mono<Plan> createPlan(PlanCreateRequest request);

    /**
     * Request approval for a plan.
     *
     * @param planId The plan identifier
     * @param request The approval request
     * @return The approval result
     */
    Mono<ApprovalResult> requestApproval(String planId, ApprovalRequest request);

    // === Governance Operations ===

    /**
     * Get governance status for an entity.
     *
     * @param entityId The entity identifier
     * @return The governance status
     */
    Mono<GovernanceStatus> getGovernanceStatus(String entityId);

    /**
     * Record governance decision feedback.
     *
     * @param decisionId The decision identifier
     * @param feedback The feedback to record
     * @return Mono indicating completion
     */
    Mono<Void> recordDecisionFeedback(String decisionId, DecisionFeedback feedback);

    // === DTOs ===

    record Policy(
            String policyId,
            String name,
            String description,
            String category,
            List<PolicyRule> rules,
            boolean enabled,
            int priority,
            Instant createdAt,
            Instant updatedAt
    ) {}

    record PolicyRule(
            String ruleId,
            String condition,
            String action,
            Map<String, Object> parameters
    ) {}

    record PolicyEvaluationRequest(
            String entityId,
            String actionType,
            Map<String, Object> context,
            double riskScore
    ) {}

    record PolicyEvaluationResult(
            boolean allowed,
            String decision,  // ALLOW, DENY, REQUIRE_APPROVAL
            List<String> matchedPolicies,
            List<String> violations,
            String reason,
            Map<String, Object> conditions
    ) {}

    record Spec(
            String specId,
            String name,
            String type,
            String status,
            Map<String, Object> definition,
            List<String> artifacts,
            Instant createdAt
    ) {}

    record Plan(
            String planId,
            String name,
            String description,
            String status,  // PENDING, APPROVED, REJECTED, EXECUTING, COMPLETED
            List<PlanStep> steps,
            String approvalId,
            Map<String, Object> context,
            Instant createdAt,
            Instant approvedAt,
            Instant completedAt
    ) {}

    record PlanStep(
            String stepId,
            int order,
            String action,
            String targetService,
            Map<String, Object> parameters,
            String status,
            Map<String, Object> result
    ) {}

    record PlanCreateRequest(
            String name,
            String description,
            List<PlanStepRequest> steps,
            Map<String, Object> context,
            boolean requiresApproval
    ) {}

    record PlanStepRequest(
            String action,
            String targetService,
            Map<String, Object> parameters
    ) {}

    record ApprovalRequest(
            String requestedBy,
            String reason,
            int priority,
            Map<String, Object> justification
    ) {}

    record ApprovalResult(
            String approvalId,
            String status,  // PENDING, APPROVED, REJECTED
            String approvedBy,
            String reason,
            Instant decidedAt
    ) {}

    record GovernanceStatus(
            String entityId,
            boolean compliant,
            List<String> activeViolations,
            List<String> pendingApprovals,
            double riskScore,
            Instant lastAssessedAt
    ) {}

    record DecisionFeedback(
            String outcome,
            double effectiveness,
            Map<String, Object> metrics,
            String notes
    ) {}
}
