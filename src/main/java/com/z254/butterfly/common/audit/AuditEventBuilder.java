package com.z254.butterfly.common.audit;

import com.z254.butterfly.common.security.ButterflyPrincipal;
import com.z254.butterfly.common.security.ButterflyRole;
import com.z254.butterfly.common.security.SecurityContextPropagator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builder for constructing AuditEvent records for the governance audit trail.
 * <p>
 * Provides a fluent API for creating audit events that comply with the
 * AuditEvent.avsc schema.
 * <p>
 * Usage:
 * <pre>{@code
 * AuditEvent event = AuditEventBuilder.builder()
 *     .withCorrelationId(correlationId)
 *     .fromService("odyssey")
 *     .withActor(principal)
 *     .ofType(AuditType.EXPERIMENT_STARTED)
 *     .action("CREATE")
 *     .onResource("EXPERIMENT", experimentId)
 *     .success()
 *     .atStage(PipelineStage.PATH_STRATEGY)
 *     .build();
 * }</pre>
 */
public final class AuditEventBuilder {

    private String eventId;
    private String correlationId;
    private String causationId;
    private String parentId;
    private String traceId;
    private String spanId;
    private long timestamp;
    private ActorInfo actor;
    private String service;
    private AuditType auditType;
    private String action;
    private ResourceInfo resource;
    private OutcomeInfo outcome;
    private GovernanceInfo governance;
    private final List<ProofInfo> proofs = new ArrayList<>();
    private StageInfo stageInfo;
    private final Map<String, String> metadata = new HashMap<>();
    private final Map<String, String> tags = new HashMap<>();
    private ClientInfo clientInfo;

    private AuditEventBuilder() {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = Instant.now().toEpochMilli();
    }

    public static AuditEventBuilder builder() {
        return new AuditEventBuilder();
    }

    // === Correlation and Identity ===

    public AuditEventBuilder withEventId(String eventId) {
        this.eventId = eventId;
        return this;
    }

    public AuditEventBuilder withCorrelationId(String correlationId) {
        this.correlationId = correlationId;
        return this;
    }

    public AuditEventBuilder withCausationId(String causationId) {
        this.causationId = causationId;
        return this;
    }

    public AuditEventBuilder withParentId(String parentId) {
        this.parentId = parentId;
        return this;
    }

    public AuditEventBuilder withTracing(String traceId, String spanId) {
        this.traceId = traceId;
        this.spanId = spanId;
        return this;
    }

    /**
     * Auto-populate correlation from MDC/context.
     */
    public AuditEventBuilder withContextCorrelation() {
        SecurityContextPropagator.getCorrelationId().ifPresent(id -> this.correlationId = id);
        SecurityContextPropagator.getCausationId().ifPresent(id -> this.causationId = id);
        return this;
    }

    // === Service and Timing ===

    public AuditEventBuilder fromService(String service) {
        this.service = service;
        return this;
    }

    public AuditEventBuilder atTimestamp(long timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public AuditEventBuilder atTimestamp(Instant timestamp) {
        this.timestamp = timestamp.toEpochMilli();
        return this;
    }

    // === Actor ===

    public AuditEventBuilder withActor(ButterflyPrincipal principal) {
        if (principal != null) {
            this.actor = new ActorInfo(
                    principal.getSubject(),
                    principal.getTenantId(),
                    principal.getRoles().stream().map(ButterflyRole::getCode).toList(),
                    principal.isGovernedActor(),
                    principal.getIssuer()
            );
        }
        return this;
    }

    public AuditEventBuilder withActor(String subject, String tenantId, List<String> roles, boolean isGovernedActor) {
        this.actor = new ActorInfo(subject, tenantId, roles, isGovernedActor, null);
        return this;
    }

    public AuditEventBuilder withSystemActor() {
        this.actor = new ActorInfo("system", "global", List.of("service"), false, "butterfly-internal");
        return this;
    }

    // === Audit Type and Action ===

    public AuditEventBuilder ofType(AuditType auditType) {
        this.auditType = auditType;
        return this;
    }

    public AuditEventBuilder action(String action) {
        this.action = action;
        return this;
    }

    // === Resource ===

    public AuditEventBuilder onResource(String resourceType, String resourceId) {
        this.resource = new ResourceInfo(resourceType, resourceId, null, null);
        return this;
    }

    public AuditEventBuilder onResource(String resourceType, String resourceId, String namespace) {
        this.resource = new ResourceInfo(resourceType, resourceId, namespace, null);
        return this;
    }

    public AuditEventBuilder onResource(String resourceType, String resourceId, String namespace, Integer version) {
        this.resource = new ResourceInfo(resourceType, resourceId, namespace, version);
        return this;
    }

    // === Outcome ===

    public AuditEventBuilder success() {
        this.outcome = new OutcomeInfo(true, null, null, null, null);
        return this;
    }

    public AuditEventBuilder success(String message) {
        this.outcome = new OutcomeInfo(true, "200", message, null, null);
        return this;
    }

    public AuditEventBuilder success(String resultCode, String message, Long durationMs) {
        this.outcome = new OutcomeInfo(true, resultCode, message, null, durationMs);
        return this;
    }

    public AuditEventBuilder failure(String errorType, String message) {
        this.outcome = new OutcomeInfo(false, null, message, errorType, null);
        return this;
    }

    public AuditEventBuilder failure(String resultCode, String errorType, String message, Long durationMs) {
        this.outcome = new OutcomeInfo(false, resultCode, message, errorType, durationMs);
        return this;
    }

    public AuditEventBuilder withDuration(long durationMs) {
        if (this.outcome != null) {
            this.outcome = new OutcomeInfo(
                    outcome.success, outcome.resultCode, outcome.message, outcome.errorType, durationMs
            );
        }
        return this;
    }

    // === Governance ===

    public AuditEventBuilder withGovernance(List<String> policiesEvaluated, List<ViolationInfo> violations) {
        this.governance = new GovernanceInfo(policiesEvaluated, violations, false, null, List.of());
        return this;
    }

    public AuditEventBuilder withGovernance(GovernanceInfo governance) {
        this.governance = governance;
        return this;
    }

    public AuditEventBuilder withApproval(String approvalId, List<String> approvers) {
        if (this.governance != null) {
            this.governance = new GovernanceInfo(
                    governance.policiesEvaluated, governance.violations, true, approvalId, approvers
            );
        } else {
            this.governance = new GovernanceInfo(List.of(), List.of(), true, approvalId, approvers);
        }
        return this;
    }

    // === Proofs ===

    public AuditEventBuilder withProof(String proofType, String value) {
        this.proofs.add(new ProofInfo(proofType, value, null, null));
        return this;
    }

    public AuditEventBuilder withProof(String proofType, String value, String algorithm) {
        this.proofs.add(new ProofInfo(proofType, value, algorithm, null));
        return this;
    }

    public AuditEventBuilder withHashProof(String hash, String algorithm) {
        this.proofs.add(new ProofInfo("HASH", hash, algorithm, null));
        return this;
    }

    public AuditEventBuilder withSignatureProof(String signature, String algorithm, String verificationUri) {
        this.proofs.add(new ProofInfo("SIGNATURE", signature, algorithm, verificationUri));
        return this;
    }

    // === Pipeline Stage ===

    public AuditEventBuilder atStage(PipelineStage stage) {
        this.stageInfo = new StageInfo(stage, null, null, null);
        return this;
    }

    public AuditEventBuilder atStage(PipelineStage stage, String inputRef, String outputRef) {
        this.stageInfo = new StageInfo(stage, inputRef, outputRef, null);
        return this;
    }

    public AuditEventBuilder atStage(PipelineStage stage, String inputRef, String outputRef, String dataHash) {
        this.stageInfo = new StageInfo(stage, inputRef, outputRef, dataHash);
        return this;
    }

    // === Metadata and Tags ===

    public AuditEventBuilder metadata(String key, String value) {
        this.metadata.put(key, value);
        return this;
    }

    public AuditEventBuilder metadata(Map<String, String> metadata) {
        this.metadata.putAll(metadata);
        return this;
    }

    public AuditEventBuilder tag(String key, String value) {
        this.tags.put(key, value);
        return this;
    }

    public AuditEventBuilder tags(Map<String, String> tags) {
        this.tags.putAll(tags);
        return this;
    }

    // === Client Info ===

    public AuditEventBuilder withClientInfo(String ipAddress, String userAgent, String requestId) {
        this.clientInfo = new ClientInfo(ipAddress, userAgent, requestId, null);
        return this;
    }

    public AuditEventBuilder withClientInfo(ClientInfo clientInfo) {
        this.clientInfo = clientInfo;
        return this;
    }

    // === Build ===

    public AuditEvent build() {
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        if (service == null) {
            throw new IllegalStateException("Service is required");
        }
        if (auditType == null) {
            throw new IllegalStateException("Audit type is required");
        }
        if (action == null) {
            action = auditType.name();
        }
        if (actor == null) {
            actor = new ActorInfo("unknown", "unknown", List.of(), false, null);
        }
        if (resource == null) {
            resource = new ResourceInfo("unknown", "unknown", null, null);
        }
        if (outcome == null) {
            outcome = new OutcomeInfo(true, null, null, null, null);
        }

        return new AuditEvent(
                eventId,
                correlationId,
                causationId,
                parentId,
                traceId,
                spanId,
                timestamp,
                actor,
                service,
                auditType,
                action,
                resource,
                outcome,
                governance,
                List.copyOf(proofs),
                stageInfo,
                Map.copyOf(metadata),
                Map.copyOf(tags),
                clientInfo
        );
    }

    // === Record Classes ===

    public record AuditEvent(
            String eventId,
            String correlationId,
            String causationId,
            String parentId,
            String traceId,
            String spanId,
            long timestamp,
            ActorInfo actor,
            String service,
            AuditType auditType,
            String action,
            ResourceInfo resource,
            OutcomeInfo outcome,
            GovernanceInfo governance,
            List<ProofInfo> proofs,
            StageInfo stageInfo,
            Map<String, String> metadata,
            Map<String, String> tags,
            ClientInfo clientInfo
    ) {}

    public record ActorInfo(
            String subject,
            String tenantId,
            List<String> roles,
            boolean isGovernedActor,
            String issuer
    ) {}

    public record ResourceInfo(
            String resourceType,
            String resourceId,
            String namespace,
            Integer version
    ) {}

    public record OutcomeInfo(
            boolean success,
            String resultCode,
            String message,
            String errorType,
            Long durationMs
    ) {}

    public record GovernanceInfo(
            List<String> policiesEvaluated,
            List<ViolationInfo> violations,
            boolean approvalRequired,
            String approvalId,
            List<String> approvers
    ) {}

    public record ViolationInfo(
            String policyId,
            String policyName,
            String constraintType,
            String message,
            String severity
    ) {}

    public record ProofInfo(
            String proofType,
            String value,
            String algorithm,
            String verificationUri
    ) {}

    public record StageInfo(
            PipelineStage stage,
            String inputRef,
            String outputRef,
            String dataHash
    ) {}

    public record ClientInfo(
            String ipAddress,
            String userAgent,
            String requestId,
            String apiVersion
    ) {}

    public enum AuditType {
        EVENT_INGESTED,
        SIGNAL_GENERATED,
        CAPSULE_CREATED,
        CAPSULE_UPDATED,
        CAPSULE_DELETED,
        PATH_EVALUATED,
        EXPERIMENT_STARTED,
        EXPERIMENT_COMPLETED,
        PLAN_CREATED,
        PLAN_EXECUTED,
        APPROVAL_REQUESTED,
        APPROVAL_GRANTED,
        APPROVAL_DENIED,
        ACTION_EXECUTED,
        ACTION_COMPLETED,
        ACTION_FAILED,
        POLICY_EVALUATED,
        POLICY_VIOLATED,
        LEARNING_SIGNAL_RECORDED,
        EVOLUTION_TRIGGERED,
        ACCESS_GRANTED,
        ACCESS_DENIED,
        CONFIG_CHANGED,
        ERROR_OCCURRED
    }

    public enum PipelineStage {
        RAW_EVENT,
        SIGNAL,
        CAPSULE,
        PATH_STRATEGY,
        PLAN_APPROVAL,
        ACTION,
        OUTCOME,
        LEARNING
    }
}

