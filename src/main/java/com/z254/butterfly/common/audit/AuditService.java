package com.z254.butterfly.common.audit;

import com.z254.butterfly.common.audit.AuditEventBuilder.AuditEvent;
import com.z254.butterfly.common.audit.AuditEventBuilder.AuditType;
import com.z254.butterfly.common.audit.AuditEventBuilder.PipelineStage;
import com.z254.butterfly.common.security.ButterflyPrincipal;
import com.z254.butterfly.common.security.SecurityContextPropagator;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Service for publishing audit events to the governance audit trail.
 * <p>
 * This service should be used by all BUTTERFLY services to record auditable
 * actions for compliance and traceability.
 * <p>
 * Usage:
 * <pre>{@code
 * @Autowired
 * private AuditService auditService;
 * 
 * // Record an audit event
 * auditService.audit(
 *     AuditType.EXPERIMENT_STARTED,
 *     "EXPERIMENT",
 *     experimentId,
 *     principal,
 *     builder -> builder
 *         .atStage(PipelineStage.PATH_STRATEGY)
 *         .metadata("hypothesis", hypothesis)
 * );
 * 
 * // Audit an operation with automatic timing
 * Object result = auditService.auditOperation(
 *     AuditType.CAPSULE_CREATED,
 *     "CAPSULE",
 *     () -> createCapsule(request),
 *     principal
 * );
 * }</pre>
 */
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    public static final String TOPIC_GOVERNANCE_AUDIT = "governance.audit";

    private final String serviceName;
    private final AuditEventPublisher publisher;
    private final boolean enabled;

    /**
     * Create an audit service.
     *
     * @param serviceName the service name (e.g., "odyssey", "capsule")
     * @param publisher   the event publisher (typically Kafka-based)
     * @param enabled     whether auditing is enabled
     */
    public AuditService(String serviceName, AuditEventPublisher publisher, boolean enabled) {
        this.serviceName = serviceName;
        this.publisher = publisher;
        this.enabled = enabled;
    }

    /**
     * Create an audit service with auditing enabled.
     */
    public AuditService(String serviceName, AuditEventPublisher publisher) {
        this(serviceName, publisher, true);
    }

    // === Basic Audit Methods ===

    /**
     * Record an audit event.
     *
     * @param auditType    the type of audit event
     * @param resourceType the type of resource being acted upon
     * @param resourceId   the resource identifier
     * @param principal    the actor performing the action (can be null)
     */
    public void audit(AuditType auditType, String resourceType, String resourceId, ButterflyPrincipal principal) {
        audit(auditType, resourceType, resourceId, principal, builder -> {});
    }

    /**
     * Record an audit event with customization.
     *
     * @param auditType    the type of audit event
     * @param resourceType the type of resource being acted upon
     * @param resourceId   the resource identifier
     * @param principal    the actor performing the action (can be null)
     * @param customizer   callback to customize the audit event
     */
    public void audit(
            AuditType auditType,
            String resourceType,
            String resourceId,
            ButterflyPrincipal principal,
            Consumer<AuditEventBuilder> customizer) {

        if (!enabled) {
            return;
        }

        try {
            AuditEventBuilder builder = AuditEventBuilder.builder()
                    .fromService(serviceName)
                    .ofType(auditType)
                    .onResource(resourceType, resourceId)
                    .withContextCorrelation()
                    .success();

            if (principal != null) {
                builder.withActor(principal);
            } else {
                builder.withSystemActor();
            }

            // Apply customizations
            customizer.accept(builder);

            AuditEvent event = builder.build();
            publish(event);

        } catch (Exception e) {
            log.error("[{}] Failed to create audit event for {}: {}", serviceName, auditType, e.getMessage());
        }
    }

    /**
     * Record a failure audit event.
     */
    public void auditFailure(
            AuditType auditType,
            String resourceType,
            String resourceId,
            ButterflyPrincipal principal,
            String errorType,
            String errorMessage) {

        audit(auditType, resourceType, resourceId, principal, builder ->
                builder.failure(errorType, errorMessage));
    }

    /**
     * Record an access denied event.
     */
    public void auditAccessDenied(String resourceType, String resourceId, ButterflyPrincipal principal, String reason) {
        audit(AuditType.ACCESS_DENIED, resourceType, resourceId, principal, builder ->
                builder.failure("ACCESS_DENIED", reason)
                        .tag("security", "access-control"));
    }

    /**
     * Record a policy violation event.
     */
    public void auditPolicyViolation(
            String resourceType,
            String resourceId,
            ButterflyPrincipal principal,
            AuditEventBuilder.GovernanceInfo governance) {

        audit(AuditType.POLICY_VIOLATED, resourceType, resourceId, principal, builder ->
                builder.withGovernance(governance)
                        .failure("POLICY_VIOLATION", "Policy constraints not satisfied")
                        .tag("governance", "violation"));
    }

    // === Operation Auditing ===

    /**
     * Audit an operation with automatic success/failure detection and timing.
     *
     * @param auditType    the type of audit event
     * @param resourceType the type of resource being acted upon
     * @param operation    the operation to execute
     * @param principal    the actor performing the action
     * @param <T>          return type of the operation
     * @return the operation result
     */
    public <T> T auditOperation(
            AuditType auditType,
            String resourceType,
            Supplier<T> operation,
            ButterflyPrincipal principal) {

        return auditOperation(auditType, resourceType, operation, principal, builder -> {});
    }

    /**
     * Audit an operation with customization.
     */
    public <T> T auditOperation(
            AuditType auditType,
            String resourceType,
            Supplier<T> operation,
            ButterflyPrincipal principal,
            Consumer<AuditEventBuilder> customizer) {

        Instant start = Instant.now();
        String resourceId = "pending";
        
        try {
            T result = operation.get();
            
            // Try to extract resource ID from result
            if (result != null) {
                resourceId = extractResourceId(result);
            }
            
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            
            audit(auditType, resourceType, resourceId, principal, builder -> {
                builder.success("200", "Operation completed", durationMs);
                customizer.accept(builder);
            });
            
            return result;
            
        } catch (Exception e) {
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            
            audit(auditType, resourceType, resourceId, principal, builder -> {
                builder.failure("500", e.getClass().getSimpleName(), e.getMessage(), durationMs);
                customizer.accept(builder);
            });
            
            throw e;
        }
    }

    /**
     * Audit an operation that doesn't return a value.
     */
    public void auditVoidOperation(
            AuditType auditType,
            String resourceType,
            String resourceId,
            Runnable operation,
            ButterflyPrincipal principal) {

        auditOperation(auditType, resourceType, () -> {
            operation.run();
            return resourceId;
        }, principal);
    }

    // === Pipeline Stage Auditing ===

    /**
     * Record a pipeline stage transition for end-to-end tracing.
     */
    public void auditPipelineStage(
            PipelineStage stage,
            AuditType auditType,
            String resourceType,
            String resourceId,
            ButterflyPrincipal principal,
            String inputRef,
            String outputRef) {

        audit(auditType, resourceType, resourceId, principal, builder ->
                builder.atStage(stage, inputRef, outputRef)
                        .tag("pipeline", stage.name().toLowerCase()));
    }

    /**
     * Record a pipeline stage with data integrity hash.
     */
    public void auditPipelineStageWithHash(
            PipelineStage stage,
            AuditType auditType,
            String resourceType,
            String resourceId,
            ButterflyPrincipal principal,
            String inputRef,
            String outputRef,
            String dataHash) {

        audit(auditType, resourceType, resourceId, principal, builder ->
                builder.atStage(stage, inputRef, outputRef, dataHash)
                        .withHashProof(dataHash, "SHA-256")
                        .tag("pipeline", stage.name().toLowerCase()));
    }

    // === Correlation Management ===

    /**
     * Create a new correlation context for a request flow.
     * Returns the correlation ID that should be propagated.
     */
    public String startCorrelation() {
        String correlationId = UUID.randomUUID().toString();
        SecurityContextPropagator.setCorrelationId(correlationId);
        MDC.put("governanceCorrelationId", correlationId);
        return correlationId;
    }

    /**
     * Continue an existing correlation from upstream.
     */
    public void continueCorrelation(String correlationId, String causationId) {
        SecurityContextPropagator.setCorrelationId(correlationId);
        SecurityContextPropagator.setCausationId(causationId);
        MDC.put("governanceCorrelationId", correlationId);
        if (causationId != null) {
            MDC.put("governanceCausationId", causationId);
        }
    }

    /**
     * Get current correlation ID.
     */
    public Optional<String> getCurrentCorrelationId() {
        return SecurityContextPropagator.getCorrelationId();
    }

    // === Private Methods ===

    private void publish(AuditEvent event) {
        if (publisher != null) {
            CompletableFuture.runAsync(() -> {
                try {
                    publisher.publish(TOPIC_GOVERNANCE_AUDIT, event.correlationId(), event);
                    
                    if (log.isDebugEnabled()) {
                        log.debug("[{}] Published audit event: type={}, resource={}/{}, correlation={}",
                                serviceName, event.auditType(), event.resource().resourceType(),
                                event.resource().resourceId(), event.correlationId());
                    }
                } catch (Exception e) {
                    log.error("[{}] Failed to publish audit event: {}", serviceName, e.getMessage());
                }
            });
        } else {
            // Fallback to logging if no publisher configured
            log.info("[{}] AUDIT: type={}, action={}, resource={}/{}, actor={}, outcome={}",
                    serviceName,
                    event.auditType(),
                    event.action(),
                    event.resource().resourceType(),
                    event.resource().resourceId(),
                    event.actor().subject(),
                    event.outcome().success() ? "SUCCESS" : "FAILURE");
        }
    }

    private String extractResourceId(Object result) {
        // Try common patterns for extracting ID from result
        if (result instanceof String s) {
            return s;
        }
        try {
            // Try getId() method
            var method = result.getClass().getMethod("getId");
            Object id = method.invoke(result);
            return id != null ? id.toString() : "unknown";
        } catch (Exception e) {
            // Try id field
            try {
                var field = result.getClass().getDeclaredField("id");
                field.setAccessible(true);
                Object id = field.get(result);
                return id != null ? id.toString() : "unknown";
            } catch (Exception e2) {
                return "unknown";
            }
        }
    }

    /**
     * Interface for publishing audit events.
     */
    @FunctionalInterface
    public interface AuditEventPublisher {
        void publish(String topic, String key, AuditEvent event);
    }

    /**
     * No-op publisher for testing or when auditing is disabled.
     */
    public static class NoOpPublisher implements AuditEventPublisher {
        @Override
        public void publish(String topic, String key, AuditEvent event) {
            // No-op
        }
    }

    /**
     * Logging-only publisher for debugging.
     */
    public static class LoggingPublisher implements AuditEventPublisher {
        private static final Logger auditLog = LoggerFactory.getLogger("audit");

        @Override
        public void publish(String topic, String key, AuditEvent event) {
            auditLog.info("AUDIT [{}] type={} action={} resource={}/{} actor={} success={} correlation={}",
                    event.service(),
                    event.auditType(),
                    event.action(),
                    event.resource().resourceType(),
                    event.resource().resourceId(),
                    event.actor().subject(),
                    event.outcome().success(),
                    event.correlationId());
        }
    }
}

