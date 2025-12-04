package com.z254.butterfly.common.governance;

import com.z254.butterfly.common.security.ButterflyPrincipal;
import com.z254.butterfly.common.security.SecurityContextPropagator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Lightweight policy enforcement agent for per-service policy evaluation.
 * <p>
 * Each BUTTERFLY service embeds this agent to evaluate policies locally before
 * critical operations. The agent caches policies received from PLATO's
 * {@code plato.policies} Kafka topic and evaluates them without network calls
 * for most operations.
 * <p>
 * Features:
 * <ul>
 *   <li>Local policy cache with TTL-based expiration</li>
 *   <li>Fast policy evaluation without remote calls</li>
 *   <li>Callback to PLATO for complex evaluations and violation recording</li>
 *   <li>Metrics and audit logging for compliance</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>{@code
 * @Autowired
 * private PolicyEnforcementAgent policyAgent;
 * 
 * // Before critical operation
 * PolicyEnforcementAgent.EnforcementResult result = 
 *     policyAgent.evaluate("create-capsule", capsuleData, principal);
 * 
 * if (result.isBlocked()) {
 *     throw new PolicyViolationException(result.violations());
 * }
 * if (result.hasWarnings()) {
 *     log.warn("Policy warnings: {}", result.warnings());
 * }
 * }</pre>
 */
public class PolicyEnforcementAgent {

    private static final Logger log = LoggerFactory.getLogger(PolicyEnforcementAgent.class);

    private final String serviceName;
    private final PolicyCache policyCache;
    private final PolicyViolationRecorder violationRecorder;
    private final PolicyEnforcementMetrics metrics;
    
    // Configuration
    private final Duration cacheTtl;
    private final boolean strictMode;

    /**
     * Create a new PolicyEnforcementAgent.
     *
     * @param serviceName       the service name (e.g., "odyssey", "capsule")
     * @param violationRecorder callback for recording violations (null for local-only)
     * @param cacheTtl          cache TTL for policies
     * @param strictMode        if true, unknown policies are treated as violations
     */
    public PolicyEnforcementAgent(
            String serviceName,
            PolicyViolationRecorder violationRecorder,
            Duration cacheTtl,
            boolean strictMode) {
        this.serviceName = serviceName;
        this.violationRecorder = violationRecorder;
        this.cacheTtl = cacheTtl != null ? cacheTtl : Duration.ofMinutes(5);
        this.strictMode = strictMode;
        this.policyCache = new PolicyCache(this.cacheTtl);
        this.metrics = new PolicyEnforcementMetrics(serviceName);
    }

    /**
     * Create with default configuration.
     */
    public PolicyEnforcementAgent(String serviceName) {
        this(serviceName, null, Duration.ofMinutes(5), false);
    }

    // === Policy Cache Management ===

    /**
     * Update cached policy from Kafka message.
     */
    public void handlePolicyUpdate(ConsumerRecord<String, CachedPolicy> record) {
        String policyId = record.key();
        CachedPolicy policy = record.value();
        
        if (policy == null) {
            // Tombstone - remove policy
            policyCache.remove(policyId);
            log.info("[{}] Removed policy: {}", serviceName, policyId);
        } else if (appliesToService(policy)) {
            policyCache.put(policy);
            log.debug("[{}] Cached policy: {} (version: {})", 
                    serviceName, policy.name(), policy.version());
        }
    }

    /**
     * Update cached policy directly.
     */
    public void updatePolicy(CachedPolicy policy) {
        if (policy == null) return;
        
        if (appliesToService(policy)) {
            policyCache.put(policy);
            log.debug("[{}] Cached policy: {} (version: {})", 
                    serviceName, policy.name(), policy.version());
        }
    }

    /**
     * Remove a policy from cache.
     */
    public void removePolicy(String policyId) {
        policyCache.remove(policyId);
    }

    /**
     * Get count of cached policies.
     */
    public int getCachedPolicyCount() {
        return policyCache.size();
    }

    // === Policy Evaluation ===

    /**
     * Evaluate policies for an operation.
     *
     * @param operation   the operation being performed (e.g., "create-capsule")
     * @param context     the evaluation context (object data, metadata)
     * @param principal   the principal performing the operation
     * @return enforcement result with violations and recommendations
     */
    public EnforcementResult evaluate(String operation, EvaluationContext context, ButterflyPrincipal principal) {
        Instant start = Instant.now();
        metrics.incrementEvaluations();
        
        String correlationId = SecurityContextPropagator.getCorrelationId()
                .orElse(generateCorrelationId());
        
        try {
            MDC.put("operation", operation);
            MDC.put("policyCorrelationId", correlationId);
            
            List<CachedPolicy> applicablePolicies = policyCache.getApplicable(
                    context.objectType(),
                    context.namespace()
            );
            
            if (applicablePolicies.isEmpty()) {
                metrics.recordEvaluationTime(Duration.between(start, Instant.now()));
                return EnforcementResult.pass(correlationId);
            }
            
            List<Violation> violations = new ArrayList<>();
            List<Warning> warnings = new ArrayList<>();
            
            for (CachedPolicy policy : applicablePolicies) {
                PolicyEvaluationResult result = evaluatePolicy(policy, context, principal);
                
                if (!result.allowed()) {
                    if (policy.enforcement() == EnforcementLevel.BLOCK || 
                        policy.enforcement() == EnforcementLevel.QUARANTINE) {
                        violations.add(new Violation(
                                policy.policyId(),
                                policy.name(),
                                result.failedConstraint(),
                                result.message(),
                                policy.enforcement(),
                                policy.violationSeverity()
                        ));
                        metrics.incrementViolations();
                    } else {
                        warnings.add(new Warning(
                                policy.policyId(),
                                policy.name(),
                                result.failedConstraint(),
                                result.message()
                        ));
                        metrics.incrementWarnings();
                    }
                }
            }
            
            // Record violations if recorder is configured
            if (!violations.isEmpty() && violationRecorder != null) {
                violationRecorder.recordViolations(operation, context, principal, violations, correlationId);
            }
            
            Duration evalTime = Duration.between(start, Instant.now());
            metrics.recordEvaluationTime(evalTime);
            
            EnforcementResult result = new EnforcementResult(
                    violations.isEmpty(),
                    violations,
                    warnings,
                    correlationId,
                    evalTime,
                    applicablePolicies.size()
            );
            
            if (!result.passed()) {
                log.info("[{}] Policy enforcement blocked operation '{}': {} violations, {} warnings",
                        serviceName, operation, violations.size(), warnings.size());
            }
            
            return result;
            
        } finally {
            MDC.remove("operation");
            MDC.remove("policyCorrelationId");
        }
    }

    /**
     * Quick check if an operation would be allowed (without full evaluation).
     */
    public boolean wouldAllow(String operation, EvaluationContext context, ButterflyPrincipal principal) {
        return evaluate(operation, context, principal).passed();
    }

    /**
     * Evaluate a single policy against context.
     */
    private PolicyEvaluationResult evaluatePolicy(CachedPolicy policy, EvaluationContext context, ButterflyPrincipal principal) {
        // Check conditions first
        if (!conditionsMatch(policy, context)) {
            return PolicyEvaluationResult.notApplicable();
        }
        
        // Evaluate constraints
        for (CachedPolicy.Constraint constraint : policy.constraints()) {
            ConstraintEvaluationResult constraintResult = evaluateConstraint(constraint, context, principal);
            
            if (!constraintResult.fulfilled()) {
                return PolicyEvaluationResult.failure(
                        constraint.constraintType(),
                        constraintResult.message()
                );
            }
        }
        
        return PolicyEvaluationResult.success();
    }

    private boolean conditionsMatch(CachedPolicy policy, EvaluationContext context) {
        if (policy.conditions() == null || policy.conditions().isEmpty()) {
            return true;
        }
        
        for (CachedPolicy.Condition condition : policy.conditions()) {
            if (!evaluateCondition(condition, context)) {
                return false;
            }
        }
        
        return true;
    }

    private boolean evaluateCondition(CachedPolicy.Condition condition, EvaluationContext context) {
        Object actualValue = context.getField(condition.field());
        Object expectedValue = condition.value();
        
        return switch (condition.operator()) {
            case "EQUALS" -> actualValue != null && actualValue.equals(expectedValue);
            case "NOT_EQUALS" -> actualValue == null || !actualValue.equals(expectedValue);
            case "CONTAINS" -> actualValue != null && actualValue.toString().contains(String.valueOf(expectedValue));
            case "MATCHES" -> actualValue != null && Pattern.matches(String.valueOf(expectedValue), actualValue.toString());
            case "IN" -> expectedValue instanceof Collection<?> c && c.contains(actualValue);
            case "NOT_IN" -> !(expectedValue instanceof Collection<?> c) || !c.contains(actualValue);
            case "EXISTS" -> actualValue != null;
            case "NOT_EXISTS" -> actualValue == null;
            case "GREATER_THAN" -> compareNumeric(actualValue, expectedValue) > 0;
            case "LESS_THAN" -> compareNumeric(actualValue, expectedValue) < 0;
            default -> true;
        };
    }

    private int compareNumeric(Object a, Object b) {
        if (a instanceof Number na && b instanceof Number nb) {
            return Double.compare(na.doubleValue(), nb.doubleValue());
        }
        return 0;
    }

    private ConstraintEvaluationResult evaluateConstraint(CachedPolicy.Constraint constraint, EvaluationContext context, ButterflyPrincipal principal) {
        String type = constraint.constraintType();
        Map<String, String> params = constraint.parameters();
        
        return switch (type) {
            case "REQUIRE_GOVERNANCE" -> {
                if (principal != null && principal.requiresGovernanceApproval()) {
                    yield ConstraintEvaluationResult.success();
                }
                yield ConstraintEvaluationResult.failure("Operation requires governed actor context");
            }
            
            case "REQUIRE_GOVERNED_ACTOR" -> {
                if (principal != null && principal.isGovernedActor()) {
                    yield ConstraintEvaluationResult.success();
                }
                yield ConstraintEvaluationResult.failure("Operation requires governed actor");
            }
            
            case "REQUIRE_AUDIT_CORRELATION" -> {
                Optional<String> correlationId = SecurityContextPropagator.getCorrelationId();
                if (correlationId.isPresent()) {
                    yield ConstraintEvaluationResult.success();
                }
                yield ConstraintEvaluationResult.failure("Operation requires audit correlation ID");
            }
            
            case "REQUIRE_OWNER" -> {
                Object owner = context.getField("owner");
                if (owner != null && !owner.toString().isBlank()) {
                    yield ConstraintEvaluationResult.success();
                }
                yield ConstraintEvaluationResult.failure("Owner is required");
            }
            
            case "REQUIRE_DESCRIPTION" -> {
                Object desc = context.getField("description");
                if (desc != null && !desc.toString().isBlank()) {
                    yield ConstraintEvaluationResult.success();
                }
                yield ConstraintEvaluationResult.failure("Description is required");
            }
            
            case "LIMIT_VALUE" -> {
                String field = params.get("field");
                String maxValueStr = params.get("maxValue");
                if (field != null && maxValueStr != null) {
                    Object value = context.getField(field);
                    if (value instanceof Number num) {
                        double maxValue = Double.parseDouble(maxValueStr);
                        if (num.doubleValue() <= maxValue) {
                            yield ConstraintEvaluationResult.success();
                        }
                        yield ConstraintEvaluationResult.failure(
                                String.format("%s exceeds limit %s (actual: %s)", field, maxValueStr, num));
                    }
                }
                yield ConstraintEvaluationResult.success();
            }
            
            case "IN_NAMESPACES" -> {
                String namespacesStr = params.get("namespaces");
                if (namespacesStr != null) {
                    String[] allowed = namespacesStr.split(",");
                    String ns = context.namespace();
                    for (String allowedNs : allowed) {
                        if (ns != null && (ns.equals(allowedNs.trim()) || ns.startsWith(allowedNs.trim() + "/"))) {
                            yield ConstraintEvaluationResult.success();
                        }
                    }
                    yield ConstraintEvaluationResult.failure(
                            "Namespace " + ns + " is not in allowed namespaces: " + namespacesStr);
                }
                yield ConstraintEvaluationResult.success();
            }
            
            case "EXECUTION_LIMIT" -> {
                // This would typically involve checking against a rate limiter
                // For now, always satisfied locally; PLATO handles actual limits
                yield ConstraintEvaluationResult.success();
            }
            
            default -> {
                if (strictMode) {
                    yield ConstraintEvaluationResult.failure("Unknown constraint type: " + type);
                }
                yield ConstraintEvaluationResult.success();
            }
        };
    }

    private boolean appliesToService(CachedPolicy policy) {
        if (policy.scope() == null || policy.scope().services() == null || policy.scope().services().isEmpty()) {
            return true;
        }
        return policy.scope().services().contains(serviceName) || 
               policy.scope().services().contains("*");
    }

    private String generateCorrelationId() {
        return serviceName + "-" + System.currentTimeMillis() + "-" + 
               Thread.currentThread().getId();
    }

    // === Inner Classes ===

    /**
     * Result of policy enforcement.
     */
    public record EnforcementResult(
            boolean passed,
            List<Violation> violations,
            List<Warning> warnings,
            String correlationId,
            Duration evaluationTime,
            int policiesEvaluated
    ) {
        public static EnforcementResult pass(String correlationId) {
            return new EnforcementResult(true, List.of(), List.of(), correlationId, Duration.ZERO, 0);
        }

        public boolean isBlocked() {
            return !passed && !violations.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }

    /**
     * A policy violation that blocks the operation.
     */
    public record Violation(
            String policyId,
            String policyName,
            String constraintType,
            String message,
            EnforcementLevel enforcement,
            ViolationSeverity severity
    ) {}

    /**
     * A policy warning that doesn't block but should be logged.
     */
    public record Warning(
            String policyId,
            String policyName,
            String constraintType,
            String message
    ) {}

    /**
     * Context for policy evaluation.
     */
    public interface EvaluationContext {
        String objectType();
        String namespace();
        Object getField(String fieldPath);
        Map<String, Object> getAllFields();
        
        static EvaluationContext of(String objectType, String namespace, Map<String, Object> fields) {
            return new SimpleEvaluationContext(objectType, namespace, fields);
        }
    }

    /**
     * Simple implementation of EvaluationContext.
     */
    public record SimpleEvaluationContext(
            String objectType,
            String namespace,
            Map<String, Object> fields
    ) implements EvaluationContext {
        @Override
        public Object getField(String fieldPath) {
            if (fields == null) return null;
            
            // Support nested field access with dots
            String[] parts = fieldPath.split("\\.");
            Object current = fields;
            
            for (String part : parts) {
                if (current instanceof Map<?, ?> map) {
                    current = map.get(part);
                } else {
                    return null;
                }
            }
            
            return current;
        }

        @Override
        public Map<String, Object> getAllFields() {
            return fields != null ? fields : Map.of();
        }
    }

    /**
     * Cached policy from PLATO.
     */
    public record CachedPolicy(
            String policyId,
            String name,
            String description,
            int version,
            boolean enabled,
            Scope scope,
            List<Condition> conditions,
            List<Constraint> constraints,
            EnforcementLevel enforcement,
            ViolationSeverity violationSeverity,
            int priority,
            Map<String, String> tags,
            Instant cachedAt
    ) {
        public record Scope(
                List<String> services,
                List<String> objectTypes,
                List<String> namespacePatterns
        ) {}

        public record Condition(
                String field,
                String operator,
                Object value
        ) {}

        public record Constraint(
                String constraintType,
                Map<String, String> parameters,
                String message
        ) {}
    }

    public enum EnforcementLevel {
        LOG, WARN, BLOCK, QUARANTINE
    }

    public enum ViolationSeverity {
        INFO, WARNING, ERROR, CRITICAL
    }

    private record PolicyEvaluationResult(boolean allowed, boolean applicable, String failedConstraint, String message) {
        static PolicyEvaluationResult success() {
            return new PolicyEvaluationResult(true, true, null, null);
        }

        static PolicyEvaluationResult failure(String constraint, String message) {
            return new PolicyEvaluationResult(false, true, constraint, message);
        }

        static PolicyEvaluationResult notApplicable() {
            return new PolicyEvaluationResult(true, false, null, null);
        }
    }

    private record ConstraintEvaluationResult(boolean fulfilled, String message) {
        static ConstraintEvaluationResult success() {
            return new ConstraintEvaluationResult(true, null);
        }

        static ConstraintEvaluationResult failure(String message) {
            return new ConstraintEvaluationResult(false, message);
        }
    }

    /**
     * Callback interface for recording violations with PLATO.
     */
    @FunctionalInterface
    public interface PolicyViolationRecorder {
        void recordViolations(String operation, EvaluationContext context, 
                            ButterflyPrincipal principal, List<Violation> violations, 
                            String correlationId);
    }

    /**
     * Simple policy cache with TTL.
     */
    private static class PolicyCache {
        private final Map<String, CachedPolicy> policies = new ConcurrentHashMap<>();
        private final Map<String, Instant> policyExpiry = new ConcurrentHashMap<>();
        private final Duration ttl;

        PolicyCache(Duration ttl) {
            this.ttl = ttl;
        }

        void put(CachedPolicy policy) {
            policies.put(policy.policyId(), policy);
            policyExpiry.put(policy.policyId(), Instant.now().plus(ttl));
        }

        void remove(String policyId) {
            policies.remove(policyId);
            policyExpiry.remove(policyId);
        }

        int size() {
            evictExpired();
            return policies.size();
        }

        List<CachedPolicy> getApplicable(String objectType, String namespace) {
            evictExpired();
            return policies.values().stream()
                    .filter(CachedPolicy::enabled)
                    .filter(p -> matchesObjectType(p, objectType))
                    .filter(p -> matchesNamespace(p, namespace))
                    .sorted((a, b) -> Integer.compare(a.priority(), b.priority()))
                    .toList();
        }

        private boolean matchesObjectType(CachedPolicy policy, String objectType) {
            if (policy.scope() == null || policy.scope().objectTypes() == null || 
                policy.scope().objectTypes().isEmpty()) {
                return true;
            }
            return policy.scope().objectTypes().contains(objectType) ||
                   policy.scope().objectTypes().contains("*");
        }

        private boolean matchesNamespace(CachedPolicy policy, String namespace) {
            if (policy.scope() == null || policy.scope().namespacePatterns() == null ||
                policy.scope().namespacePatterns().isEmpty()) {
                return true;
            }
            for (String pattern : policy.scope().namespacePatterns()) {
                if (namespace != null && Pattern.matches(pattern, namespace)) {
                    return true;
                }
            }
            return false;
        }

        private void evictExpired() {
            Instant now = Instant.now();
            policyExpiry.forEach((id, expiry) -> {
                if (now.isAfter(expiry)) {
                    policies.remove(id);
                    policyExpiry.remove(id);
                }
            });
        }
    }

    /**
     * Metrics for policy enforcement.
     */
    private static class PolicyEnforcementMetrics {
        private final String serviceName;
        private final AtomicLong evaluations = new AtomicLong();
        private final AtomicLong violations = new AtomicLong();
        private final AtomicLong warnings = new AtomicLong();
        private final AtomicLong totalEvalTimeNanos = new AtomicLong();

        PolicyEnforcementMetrics(String serviceName) {
            this.serviceName = serviceName;
        }

        void incrementEvaluations() {
            evaluations.incrementAndGet();
        }

        void incrementViolations() {
            violations.incrementAndGet();
        }

        void incrementWarnings() {
            warnings.incrementAndGet();
        }

        void recordEvaluationTime(Duration duration) {
            totalEvalTimeNanos.addAndGet(duration.toNanos());
        }

        public long getEvaluations() {
            return evaluations.get();
        }

        public long getViolations() {
            return violations.get();
        }

        public long getWarnings() {
            return warnings.get();
        }
    }
}

