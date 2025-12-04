package com.z254.butterfly.common.telemetry;

import org.slf4j.MDC;

import java.util.Optional;

/**
 * Thread-local holder for tenant context information.
 * 
 * <p>This class provides a centralized way to store and retrieve tenant context
 * that can be used for:
 * <ul>
 *   <li>Multi-tenant metric tagging via {@link TenantAwareMeterFilter}</li>
 *   <li>Structured logging via MDC</li>
 *   <li>Request tracing and correlation</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * <pre>{@code
 * // Set context (typically in a filter/interceptor)
 * TenantContextHolder.setTenantId("tenant-123");
 * TenantContextHolder.setCorrelationId("corr-456");
 * 
 * try {
 *     // Business logic - metrics will automatically include tenant tags
 *     processRequest();
 * } finally {
 *     TenantContextHolder.clear();
 * }
 * }</pre>
 * 
 * <h2>Thread Safety</h2>
 * <p>This class uses ThreadLocal storage, so context is automatically
 * isolated per thread. For async operations, use {@link #snapshot()} and
 * {@link #restore(TenantContext)} to propagate context.
 * 
 * @see CorrelationIdFilter
 * @see TenantAwareMeterFilter
 */
public final class TenantContextHolder {

    private static final ThreadLocal<TenantContext> CONTEXT = ThreadLocal.withInitial(TenantContext::new);

    private TenantContextHolder() {
        // utility class
    }

    // ==================== Tenant ID ====================

    /**
     * Sets the current tenant ID and updates MDC.
     *
     * @param tenantId the tenant identifier
     */
    public static void setTenantId(String tenantId) {
        CONTEXT.get().setTenantId(tenantId);
        if (tenantId != null) {
            MDC.put(TelemetryTagNames.TENANT_ID, tenantId);
        }
    }

    /**
     * Gets the current tenant ID.
     *
     * @return the tenant ID, or empty if not set
     */
    public static Optional<String> getTenantId() {
        return Optional.ofNullable(CONTEXT.get().getTenantId());
    }

    /**
     * Gets the current tenant ID or a default value.
     *
     * @param defaultValue the default value if tenant ID is not set
     * @return the tenant ID or default value
     */
    public static String getTenantIdOrDefault(String defaultValue) {
        return getTenantId().orElse(defaultValue);
    }

    // ==================== Correlation ID ====================

    /**
     * Sets the correlation ID for request tracing and updates MDC.
     *
     * @param correlationId the correlation identifier
     */
    public static void setCorrelationId(String correlationId) {
        CONTEXT.get().setCorrelationId(correlationId);
        if (correlationId != null) {
            MDC.put(TelemetryTagNames.CORRELATION_ID, correlationId);
        }
    }

    /**
     * Gets the current correlation ID.
     *
     * @return the correlation ID, or empty if not set
     */
    public static Optional<String> getCorrelationId() {
        return Optional.ofNullable(CONTEXT.get().getCorrelationId());
    }

    // ==================== Request ID ====================

    /**
     * Sets the request ID and updates MDC.
     *
     * @param requestId the request identifier
     */
    public static void setRequestId(String requestId) {
        CONTEXT.get().setRequestId(requestId);
        if (requestId != null) {
            MDC.put(TelemetryTagNames.REQUEST_ID, requestId);
        }
    }

    /**
     * Gets the current request ID.
     *
     * @return the request ID, or empty if not set
     */
    public static Optional<String> getRequestId() {
        return Optional.ofNullable(CONTEXT.get().getRequestId());
    }

    // ==================== Route Context ====================

    /**
     * Sets the route ID for connector/ingestion tracking.
     *
     * @param routeId the route identifier
     */
    public static void setRouteId(String routeId) {
        CONTEXT.get().setRouteId(routeId);
        if (routeId != null) {
            MDC.put(TelemetryTagNames.ROUTE_ID, routeId);
        }
    }

    /**
     * Gets the current route ID.
     *
     * @return the route ID, or empty if not set
     */
    public static Optional<String> getRouteId() {
        return Optional.ofNullable(CONTEXT.get().getRouteId());
    }

    /**
     * Sets the source ID for data source tracking.
     *
     * @param sourceId the source identifier
     */
    public static void setSourceId(String sourceId) {
        CONTEXT.get().setSourceId(sourceId);
        if (sourceId != null) {
            MDC.put(TelemetryTagNames.SOURCE_ID, sourceId);
        }
    }

    /**
     * Gets the current source ID.
     *
     * @return the source ID, or empty if not set
     */
    public static Optional<String> getSourceId() {
        return Optional.ofNullable(CONTEXT.get().getSourceId());
    }

    // ==================== Context Management ====================

    /**
     * Creates a snapshot of the current context for async propagation.
     *
     * @return a copy of the current context
     */
    public static TenantContext snapshot() {
        return CONTEXT.get().copy();
    }

    /**
     * Restores a previously captured context snapshot.
     *
     * @param context the context to restore
     */
    public static void restore(TenantContext context) {
        if (context != null) {
            CONTEXT.set(context);
            context.applyToMdc();
        }
    }

    /**
     * Clears all context information and MDC entries.
     * Should be called at the end of request processing.
     */
    public static void clear() {
        CONTEXT.remove();
        MDC.remove(TelemetryTagNames.TENANT_ID);
        MDC.remove(TelemetryTagNames.CORRELATION_ID);
        MDC.remove(TelemetryTagNames.REQUEST_ID);
        MDC.remove(TelemetryTagNames.ROUTE_ID);
        MDC.remove(TelemetryTagNames.SOURCE_ID);
    }

    /**
     * Context data holder for tenant and tracing information.
     */
    public static class TenantContext {
        private String tenantId;
        private String correlationId;
        private String requestId;
        private String routeId;
        private String sourceId;

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public String getCorrelationId() {
            return correlationId;
        }

        public void setCorrelationId(String correlationId) {
            this.correlationId = correlationId;
        }

        public String getRequestId() {
            return requestId;
        }

        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }

        public String getRouteId() {
            return routeId;
        }

        public void setRouteId(String routeId) {
            this.routeId = routeId;
        }

        public String getSourceId() {
            return sourceId;
        }

        public void setSourceId(String sourceId) {
            this.sourceId = sourceId;
        }

        /**
         * Creates a copy of this context.
         *
         * @return a new TenantContext with the same values
         */
        public TenantContext copy() {
            TenantContext copy = new TenantContext();
            copy.tenantId = this.tenantId;
            copy.correlationId = this.correlationId;
            copy.requestId = this.requestId;
            copy.routeId = this.routeId;
            copy.sourceId = this.sourceId;
            return copy;
        }

        /**
         * Applies this context to MDC.
         */
        public void applyToMdc() {
            if (tenantId != null) MDC.put(TelemetryTagNames.TENANT_ID, tenantId);
            if (correlationId != null) MDC.put(TelemetryTagNames.CORRELATION_ID, correlationId);
            if (requestId != null) MDC.put(TelemetryTagNames.REQUEST_ID, requestId);
            if (routeId != null) MDC.put(TelemetryTagNames.ROUTE_ID, routeId);
            if (sourceId != null) MDC.put(TelemetryTagNames.SOURCE_ID, sourceId);
        }
    }
}

