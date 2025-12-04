package com.z254.butterfly.common.audit;

import com.z254.butterfly.common.audit.AuditEventBuilder.AuditType;
import com.z254.butterfly.common.audit.AuditEventBuilder.PipelineStage;
import com.z254.butterfly.common.security.ButterflyPrincipal;
import com.z254.butterfly.common.security.SecurityContextPropagator;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;

/**
 * Aspect for automatic audit event generation using the {@link Audited} annotation.
 * <p>
 * Usage:
 * <pre>{@code
 * @Audited(type = AuditType.CAPSULE_CREATED, resourceType = "CAPSULE")
 * public Capsule createCapsule(CreateCapsuleRequest request) {
 *     // Implementation
 * }
 * }</pre>
 */
@Aspect
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditService auditService;

    public AuditAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @Around("@annotation(audited)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        String resourceType = audited.resourceType();
        String resourceId = extractResourceId(joinPoint.getArgs(), audited.resourceIdParam());
        ButterflyPrincipal principal = extractPrincipal(joinPoint.getArgs());
        
        Instant start = Instant.now();
        
        try {
            Object result = joinPoint.proceed();
            
            // Try to extract resource ID from result if not available from params
            if (resourceId == null || resourceId.isEmpty()) {
                resourceId = extractResourceIdFromResult(result);
            }
            
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            final String finalResourceId = resourceId != null ? resourceId : "unknown";
            
            auditService.audit(audited.type(), resourceType, finalResourceId, principal, builder -> {
                builder.success("200", "Operation completed", durationMs);
                
                if (audited.stage() != PipelineStage.RAW_EVENT) {
                    builder.atStage(audited.stage());
                }
                
                if (!audited.tag().isEmpty()) {
                    String[] parts = audited.tag().split("=", 2);
                    if (parts.length == 2) {
                        builder.tag(parts[0], parts[1]);
                    }
                }
            });
            
            return result;
            
        } catch (Exception e) {
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            final String finalResourceId = resourceId != null ? resourceId : "unknown";
            
            auditService.audit(audited.type(), resourceType, finalResourceId, principal, builder -> {
                builder.failure("500", e.getClass().getSimpleName(), e.getMessage(), durationMs);
                
                if (audited.stage() != PipelineStage.RAW_EVENT) {
                    builder.atStage(audited.stage());
                }
            });
            
            throw e;
        }
    }

    private String extractResourceId(Object[] args, int paramIndex) {
        if (paramIndex >= 0 && paramIndex < args.length) {
            Object arg = args[paramIndex];
            if (arg != null) {
                return arg.toString();
            }
        }
        return null;
    }

    private ButterflyPrincipal extractPrincipal(Object[] args) {
        // First try to find a ButterflyPrincipal in arguments
        for (Object arg : args) {
            if (arg instanceof ButterflyPrincipal principal) {
                return principal;
            }
        }
        
        // Fall back to thread-local context
        return SecurityContextPropagator.getCurrentPrincipal().orElse(null);
    }

    private String extractResourceIdFromResult(Object result) {
        if (result == null) {
            return null;
        }
        
        if (result instanceof String s) {
            return s;
        }
        
        try {
            var method = result.getClass().getMethod("getId");
            Object id = method.invoke(result);
            return id != null ? id.toString() : null;
        } catch (Exception e) {
            try {
                var method = result.getClass().getMethod("id");
                Object id = method.invoke(result);
                return id != null ? id.toString() : null;
            } catch (Exception e2) {
                return null;
            }
        }
    }

    /**
     * Annotation for marking methods as audited.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Audited {
        /**
         * The audit event type.
         */
        AuditType type();

        /**
         * The resource type being acted upon.
         */
        String resourceType();

        /**
         * Index of the parameter containing the resource ID (0-based).
         * Set to -1 to extract from result.
         */
        int resourceIdParam() default -1;

        /**
         * The pipeline stage for end-to-end tracing.
         */
        PipelineStage stage() default PipelineStage.RAW_EVENT;

        /**
         * Optional tag in format "key=value".
         */
        String tag() default "";
    }
}

