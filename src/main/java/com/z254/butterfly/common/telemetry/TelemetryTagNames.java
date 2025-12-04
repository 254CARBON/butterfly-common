package com.z254.butterfly.common.telemetry;

/**
 * Standardized telemetry tag names used across all BUTTERFLY services.
 * 
 * <p>These tags enable consistent multi-tenant, connector-aware observability
 * across the ecosystem. All services should use these constants when
 * instrumenting metrics, logs, and traces.
 * 
 * <h2>Required Tags</h2>
 * <ul>
 *   <li>{@link #TENANT_ID} - Required for all tenant-scoped operations</li>
 *   <li>{@link #SERVICE} - Automatically added by common MeterFilter</li>
 * </ul>
 * 
 * <h2>Naming Conventions</h2>
 * <ul>
 *   <li>Use camelCase for tag keys (Micrometer converts to snake_case for Prometheus)</li>
 *   <li>Use snake_case with service prefix for metric names</li>
 *   <li>Include _total suffix for counters</li>
 *   <li>Include _seconds suffix for duration histograms</li>
 * </ul>
 * 
 * @see TenantContextHolder
 * @see TenantAwareMeterFilter
 */
public final class TelemetryTagNames {

    private TelemetryTagNames() {
        // utility class
    }

    // ==================== Core Identity Tags ====================

    /**
     * Tenant identifier - required for multi-tenant metrics isolation.
     * This tag enables per-tenant dashboards and SLO tracking.
     */
    public static final String TENANT_ID = "tenantId";

    /**
     * Service name identifier.
     */
    public static final String SERVICE = "service";

    /**
     * Service instance identifier for horizontal scaling.
     */
    public static final String INSTANCE = "instance";

    /**
     * Environment identifier (dev, staging, prod).
     */
    public static final String ENVIRONMENT = "environment";

    // ==================== Connector/Route Tags ====================

    /**
     * Route identifier for connector/ingestion routes.
     */
    public static final String ROUTE_ID = "routeId";

    /**
     * Source identifier for data sources.
     */
    public static final String SOURCE_ID = "sourceId";

    /**
     * Route type (e.g., rss, api, webhook, database).
     */
    public static final String ROUTE_TYPE = "routeType";

    /**
     * Connector type for categorizing connectors.
     */
    public static final String CONNECTOR_TYPE = "connectorType";

    // ==================== RIM/Entity Tags ====================

    /**
     * RIM node identifier.
     */
    public static final String NODE_ID = "nodeId";

    /**
     * Entity type for domain entities.
     */
    public static final String ENTITY_TYPE = "entityType";

    /**
     * Region for geographic grouping.
     */
    public static final String REGION = "region";

    /**
     * Domain for domain classification.
     */
    public static final String DOMAIN = "domain";

    // ==================== Operation Tags ====================

    /**
     * Operation status (success, error, timeout, etc.).
     */
    public static final String STATUS = "status";

    /**
     * Operation outcome (completed, failed, cancelled, etc.).
     */
    public static final String OUTCOME = "outcome";

    /**
     * Error type or exception class.
     */
    public static final String ERROR_TYPE = "errorType";

    /**
     * Severity level (info, warning, critical).
     */
    public static final String SEVERITY = "severity";

    // ==================== Processing Tags ====================

    /**
     * Event type for event-driven processing.
     */
    public static final String EVENT_TYPE = "eventType";

    /**
     * Signal type for signal processing.
     */
    public static final String SIGNAL_TYPE = "signalType";

    /**
     * Scenario type for scenario generation.
     */
    public static final String SCENARIO_TYPE = "scenarioType";

    /**
     * Pattern type for pattern detection.
     */
    public static final String PATTERN_TYPE = "patternType";

    /**
     * Anomaly type for anomaly detection.
     */
    public static final String ANOMALY_TYPE = "anomalyType";

    // ==================== CAPSULE-specific Tags ====================

    /**
     * Vantage mode for CAPSULE operations (omniscient, observer, system).
     */
    public static final String VANTAGE_MODE = "vantageMode";

    /**
     * Resolution level for CAPSULE time-series.
     */
    public static final String RESOLUTION = "resolution";

    // ==================== ODYSSEY-specific Tags ====================

    /**
     * Query type for graph operations.
     */
    public static final String QUERY_TYPE = "queryType";

    /**
     * Actor type for player modeling.
     */
    public static final String ACTOR_TYPE = "actorType";

    /**
     * Relation type for graph edges.
     */
    public static final String RELATION_TYPE = "relationType";

    // ==================== PLATO-specific Tags ====================

    /**
     * Engine identifier for AI engine operations.
     */
    public static final String ENGINE_ID = "engineId";

    /**
     * Plan identifier for workflow tracking.
     */
    public static final String PLAN_ID = "planId";

    /**
     * Spec type for governance primitives.
     */
    public static final String SPEC_TYPE = "specType";

    /**
     * Policy identifier for governance checks.
     */
    public static final String POLICY = "policy";

    // ==================== NEXUS-specific Tags ====================

    /**
     * Target service for gateway routing.
     */
    public static final String TARGET_SERVICE = "targetService";

    /**
     * Client identifier for rate limiting.
     */
    public static final String CLIENT_ID = "clientId";

    /**
     * Circuit breaker name.
     */
    public static final String CIRCUIT_BREAKER = "circuitBreaker";

    // ==================== Tracing Tags ====================

    /**
     * Request ID for correlation.
     */
    public static final String REQUEST_ID = "requestId";

    /**
     * Correlation ID for business process correlation.
     */
    public static final String CORRELATION_ID = "correlationId";

    /**
     * Trace ID from distributed tracing.
     */
    public static final String TRACE_ID = "traceId";

    /**
     * Span ID from distributed tracing.
     */
    public static final String SPAN_ID = "spanId";

    // ==================== API Tags ====================

    /**
     * HTTP method.
     */
    public static final String METHOD = "method";

    /**
     * API endpoint/URI.
     */
    public static final String ENDPOINT = "endpoint";

    /**
     * HTTP status code.
     */
    public static final String HTTP_STATUS = "httpStatus";

    // ==================== Common Tag Values ====================

    /**
     * Unknown tenant placeholder when tenant cannot be determined.
     */
    public static final String UNKNOWN_TENANT = "unknown";

    /**
     * System tenant for internal operations.
     */
    public static final String SYSTEM_TENANT = "system";

    /**
     * Success status value.
     */
    public static final String STATUS_SUCCESS = "success";

    /**
     * Error status value.
     */
    public static final String STATUS_ERROR = "error";

    /**
     * Timeout status value.
     */
    public static final String STATUS_TIMEOUT = "timeout";
}

