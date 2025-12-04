package com.z254.butterfly.common.tenant;

import com.z254.butterfly.common.telemetry.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.Optional;
import java.util.function.Function;

/**
 * Utilities for propagating tenant context in reactive (WebFlux) applications.
 * <p>
 * In reactive applications, ThreadLocal-based context (like {@link TenantContextHolder})
 * doesn't work because operations may execute on different threads. This class provides
 * utilities for propagating tenant context through Reactor's {@link Context}.
 *
 * <h2>Usage with Mono/Flux</h2>
 * <pre>{@code
 * // Capture current context
 * Mono<Result> result = myService.doSomething()
 *     .contextWrite(TenantReactiveContextPropagator.withCurrentTenant());
 *
 * // Read context in subscriber
 * return Mono.deferContextual(ctx -> {
 *     String tenantId = TenantReactiveContextPropagator.getTenantId(ctx);
 *     return processForTenant(tenantId);
 * });
 * }</pre>
 *
 * <h2>Usage with WebFilter</h2>
 * <pre>{@code
 * @Component
 * public class TenantWebFilter implements WebFilter {
 *     @Override
 *     public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
 *         String tenantId = extractTenantId(exchange);
 *         return chain.filter(exchange)
 *             .contextWrite(TenantReactiveContextPropagator.withTenant(tenantId));
 *     }
 * }
 * }</pre>
 *
 * @see TenantContextHolder
 */
public final class TenantReactiveContextPropagator {

    private static final Logger log = LoggerFactory.getLogger(TenantReactiveContextPropagator.class);

    private TenantReactiveContextPropagator() {
        // Utility class
    }

    // Context keys
    public static final String KEY_TENANT_ID = "butterfly.tenant.id";
    public static final String KEY_CORRELATION_ID = "butterfly.correlation.id";
    public static final String KEY_REQUEST_ID = "butterfly.request.id";
    public static final String KEY_ROUTE_ID = "butterfly.route.id";
    public static final String KEY_SOURCE_ID = "butterfly.source.id";

    // ==================== Context Writers ====================

    /**
     * Create a context writer that adds the current tenant context from TenantContextHolder.
     *
     * @return function to write tenant context
     */
    public static Function<Context, Context> withCurrentTenant() {
        TenantContextHolder.TenantContext snapshot = TenantContextHolder.snapshot();
        return ctx -> {
            Context result = ctx;
            if (snapshot.getTenantId() != null) {
                result = result.put(KEY_TENANT_ID, snapshot.getTenantId());
            }
            if (snapshot.getCorrelationId() != null) {
                result = result.put(KEY_CORRELATION_ID, snapshot.getCorrelationId());
            }
            if (snapshot.getRequestId() != null) {
                result = result.put(KEY_REQUEST_ID, snapshot.getRequestId());
            }
            if (snapshot.getRouteId() != null) {
                result = result.put(KEY_ROUTE_ID, snapshot.getRouteId());
            }
            if (snapshot.getSourceId() != null) {
                result = result.put(KEY_SOURCE_ID, snapshot.getSourceId());
            }
            return result;
        };
    }

    /**
     * Create a context writer with a specific tenant ID.
     *
     * @param tenantId the tenant identifier
     * @return function to write tenant context
     */
    public static Function<Context, Context> withTenant(String tenantId) {
        return ctx -> tenantId != null ? ctx.put(KEY_TENANT_ID, tenantId) : ctx;
    }

    /**
     * Create a context writer with tenant and correlation ID.
     *
     * @param tenantId the tenant identifier
     * @param correlationId the correlation identifier
     * @return function to write tenant context
     */
    public static Function<Context, Context> withTenant(String tenantId, String correlationId) {
        return ctx -> {
            Context result = ctx;
            if (tenantId != null) {
                result = result.put(KEY_TENANT_ID, tenantId);
            }
            if (correlationId != null) {
                result = result.put(KEY_CORRELATION_ID, correlationId);
            }
            return result;
        };
    }

    /**
     * Create a context writer with full tenant context.
     *
     * @param tenantId the tenant identifier
     * @param correlationId the correlation identifier
     * @param requestId the request identifier
     * @return function to write tenant context
     */
    public static Function<Context, Context> withFullContext(
            String tenantId, String correlationId, String requestId) {
        return ctx -> {
            Context result = ctx;
            if (tenantId != null) {
                result = result.put(KEY_TENANT_ID, tenantId);
            }
            if (correlationId != null) {
                result = result.put(KEY_CORRELATION_ID, correlationId);
            }
            if (requestId != null) {
                result = result.put(KEY_REQUEST_ID, requestId);
            }
            return result;
        };
    }

    // ==================== Context Readers ====================

    /**
     * Get tenant ID from Reactor Context.
     *
     * @param ctx the Reactor context
     * @return tenant ID or null if not present
     */
    public static String getTenantId(reactor.util.context.ContextView ctx) {
        return ctx.getOrDefault(KEY_TENANT_ID, null);
    }

    /**
     * Get tenant ID from Reactor Context with default value.
     *
     * @param ctx the Reactor context
     * @param defaultValue value to return if tenant ID not present
     * @return tenant ID or default value
     */
    public static String getTenantIdOrDefault(reactor.util.context.ContextView ctx, String defaultValue) {
        return ctx.getOrDefault(KEY_TENANT_ID, defaultValue);
    }

    /**
     * Get correlation ID from Reactor Context.
     *
     * @param ctx the Reactor context
     * @return correlation ID or null if not present
     */
    public static String getCorrelationId(reactor.util.context.ContextView ctx) {
        return ctx.getOrDefault(KEY_CORRELATION_ID, null);
    }

    /**
     * Get request ID from Reactor Context.
     *
     * @param ctx the Reactor context
     * @return request ID or null if not present
     */
    public static String getRequestId(reactor.util.context.ContextView ctx) {
        return ctx.getOrDefault(KEY_REQUEST_ID, null);
    }

    // ==================== Context Bridge ====================

    /**
     * Execute a block of code with tenant context from Reactor context set on TenantContextHolder.
     * <p>
     * This is useful when you need to call blocking code that relies on ThreadLocal context.
     *
     * @param ctx the Reactor context
     * @param runnable the code to execute
     */
    public static void runWithContext(reactor.util.context.ContextView ctx, Runnable runnable) {
        String tenantId = getTenantId(ctx);
        String correlationId = getCorrelationId(ctx);
        String requestId = getRequestId(ctx);

        try {
            if (tenantId != null) {
                TenantContextHolder.setTenantId(tenantId);
            }
            if (correlationId != null) {
                TenantContextHolder.setCorrelationId(correlationId);
            }
            if (requestId != null) {
                TenantContextHolder.setRequestId(requestId);
            }
            runnable.run();
        } finally {
            TenantContextHolder.clear();
        }
    }

    /**
     * Create a Mono that executes with tenant context from Reactor context.
     *
     * @param mono the source Mono
     * @param <T> the element type
     * @return Mono that sets ThreadLocal context during subscription
     */
    public static <T> Mono<T> withThreadLocalContext(Mono<T> mono) {
        return Mono.deferContextual(ctx -> {
            String tenantId = getTenantId(ctx);
            String correlationId = getCorrelationId(ctx);

            return mono.doFirst(() -> {
                if (tenantId != null) {
                    TenantContextHolder.setTenantId(tenantId);
                }
                if (correlationId != null) {
                    TenantContextHolder.setCorrelationId(correlationId);
                }
            }).doFinally(signal -> TenantContextHolder.clear());
        });
    }

    /**
     * Create a Flux that executes with tenant context from Reactor context.
     *
     * @param flux the source Flux
     * @param <T> the element type
     * @return Flux that sets ThreadLocal context during subscription
     */
    public static <T> Flux<T> withThreadLocalContext(Flux<T> flux) {
        return Flux.deferContextual(ctx -> {
            String tenantId = getTenantId(ctx);
            String correlationId = getCorrelationId(ctx);

            return flux.doFirst(() -> {
                if (tenantId != null) {
                    TenantContextHolder.setTenantId(tenantId);
                }
                if (correlationId != null) {
                    TenantContextHolder.setCorrelationId(correlationId);
                }
            }).doFinally(signal -> TenantContextHolder.clear());
        });
    }

    // ==================== Logging Helpers ====================

    /**
     * Log with tenant context from Reactor context.
     *
     * @param ctx the Reactor context
     * @param message log message format
     * @param args message arguments
     */
    public static void logWithContext(reactor.util.context.ContextView ctx, String message, Object... args) {
        String tenantId = getTenantIdOrDefault(ctx, "unknown");
        String correlationId = Optional.ofNullable(getCorrelationId(ctx)).orElse("none");
        log.info("[tenant={}, correlation={}] " + message, 
                concatArgs(tenantId, correlationId, args));
    }

    private static Object[] concatArgs(String tenantId, String correlationId, Object... args) {
        Object[] result = new Object[args.length + 2];
        result[0] = tenantId;
        result[1] = correlationId;
        System.arraycopy(args, 0, result, 2, args.length);
        return result;
    }
}

