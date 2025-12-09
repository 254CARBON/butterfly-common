package com.z254.butterfly.common.kafka;

import org.slf4j.MDC;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Builder utility for creating unified event headers across BUTTERFLY services.
 * 
 * <p>This builder creates header maps compatible with the UnifiedEventHeader Avro schema
 * and ensures consistent header propagation across Kafka messages.
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * Map<String, Object> headers = UnifiedEventHeaderBuilder.create()
 *     .originSystem("CAPSULE")
 *     .sourceService("capsule-service")
 *     .correlationId(MDC.get("correlationId"))
 *     .tag("operation", "create")
 *     .build();
 *     
 * // Apply to Kafka producer record
 * ProducerRecord<String, T> record = new ProducerRecord<>(topic, key, value);
 * headers.forEach((k, v) -> record.headers().add(k, v.toString().getBytes()));
 * }</pre>
 * 
 * <h2>Header Fields</h2>
 * <ul>
 *   <li>{@code event_id} - Unique event identifier (auto-generated if not set)</li>
 *   <li>{@code correlation_id} - Distributed tracing correlation ID</li>
 *   <li>{@code causation_id} - ID of the event that caused this event</li>
 *   <li>{@code origin_system} - System that originated the event</li>
 *   <li>{@code source_service} - Service instance identifier</li>
 *   <li>{@code timestamp} - Event timestamp (milliseconds since epoch)</li>
 *   <li>{@code trace_id} - OpenTelemetry trace ID</li>
 *   <li>{@code span_id} - OpenTelemetry span ID</li>
 *   <li>{@code schema_version} - Schema version for compatibility</li>
 *   <li>{@code tags.*} - Custom tags (prefixed with "tags.")</li>
 * </ul>
 * 
 * @see ButterflyTopics
 * @since 1.0.0
 */
public class UnifiedEventHeaderBuilder {
    
    private static final String SCHEMA_VERSION = "1.0.0";
    
    private String eventId;
    private String correlationId;
    private String causationId;
    private String originSystem;
    private String sourceService;
    private Long timestamp;
    private String traceId;
    private String spanId;
    private String traceFlags;
    private String traceState;
    private String schemaVersion = SCHEMA_VERSION;
    private final Map<String, String> tags = new HashMap<>();
    
    /**
     * Create a new header builder.
     */
    public static UnifiedEventHeaderBuilder create() {
        return new UnifiedEventHeaderBuilder();
    }
    
    /**
     * Create a builder initialized from MDC context.
     * Automatically extracts traceId, spanId, and correlationId if present.
     */
    public static UnifiedEventHeaderBuilder fromMdc() {
        UnifiedEventHeaderBuilder builder = new UnifiedEventHeaderBuilder();
        builder.traceId = MDC.get("traceId");
        builder.spanId = MDC.get("spanId");
        builder.correlationId = MDC.get("correlationId");
        if (builder.correlationId == null) {
            builder.correlationId = MDC.get("X-Correlation-ID");
        }
        return builder;
    }
    
    /**
     * Set the event ID. Auto-generates UUID if not called.
     */
    public UnifiedEventHeaderBuilder eventId(String eventId) {
        this.eventId = eventId;
        return this;
    }
    
    /**
     * Set the correlation ID for distributed tracing.
     */
    public UnifiedEventHeaderBuilder correlationId(String correlationId) {
        this.correlationId = correlationId;
        return this;
    }
    
    /**
     * Set the causation ID (ID of the event that caused this event).
     */
    public UnifiedEventHeaderBuilder causationId(String causationId) {
        this.causationId = causationId;
        return this;
    }
    
    /**
     * Set the origin system.
     * 
     * @param originSystem One of: PERCEPTION, CAPSULE, ODYSSEY, PLATO, NEXUS, SYNAPSE, CORTEX, EXTERNAL
     */
    public UnifiedEventHeaderBuilder originSystem(String originSystem) {
        this.originSystem = originSystem;
        return this;
    }
    
    /**
     * Set the source service identifier.
     */
    public UnifiedEventHeaderBuilder sourceService(String sourceService) {
        this.sourceService = sourceService;
        return this;
    }
    
    /**
     * Set the event timestamp. Uses current time if not called.
     */
    public UnifiedEventHeaderBuilder timestamp(long timestamp) {
        this.timestamp = timestamp;
        return this;
    }
    
    /**
     * Set the event timestamp from an Instant.
     */
    public UnifiedEventHeaderBuilder timestamp(Instant timestamp) {
        this.timestamp = timestamp.toEpochMilli();
        return this;
    }
    
    /**
     * Set the OpenTelemetry trace ID.
     */
    public UnifiedEventHeaderBuilder traceId(String traceId) {
        this.traceId = traceId;
        return this;
    }
    
    /**
     * Set the OpenTelemetry span ID.
     */
    public UnifiedEventHeaderBuilder spanId(String spanId) {
        this.spanId = spanId;
        return this;
    }
    
    /**
     * Set W3C trace flags.
     */
    public UnifiedEventHeaderBuilder traceFlags(String traceFlags) {
        this.traceFlags = traceFlags;
        return this;
    }
    
    /**
     * Set W3C trace state.
     */
    public UnifiedEventHeaderBuilder traceState(String traceState) {
        this.traceState = traceState;
        return this;
    }
    
    /**
     * Set the schema version.
     */
    public UnifiedEventHeaderBuilder schemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
        return this;
    }
    
    /**
     * Add a custom tag.
     */
    public UnifiedEventHeaderBuilder tag(String key, String value) {
        this.tags.put(key, value);
        return this;
    }
    
    /**
     * Add multiple custom tags.
     */
    public UnifiedEventHeaderBuilder tags(Map<String, String> tags) {
        this.tags.putAll(tags);
        return this;
    }
    
    /**
     * Build the header map.
     */
    public Map<String, Object> build() {
        Map<String, Object> headers = new HashMap<>();
        
        // Required fields with defaults
        headers.put("event_id", eventId != null ? eventId : UUID.randomUUID().toString());
        headers.put("timestamp", timestamp != null ? timestamp : System.currentTimeMillis());
        headers.put("schema_version", schemaVersion);
        
        // Optional standard fields
        if (correlationId != null) {
            headers.put("correlation_id", correlationId);
        }
        if (causationId != null) {
            headers.put("causation_id", causationId);
        }
        if (originSystem != null) {
            headers.put("origin_system", originSystem);
        }
        if (sourceService != null) {
            headers.put("source_service", sourceService);
        }
        
        // Trace context
        if (traceId != null) {
            headers.put("trace_id", traceId);
        }
        if (spanId != null) {
            headers.put("span_id", spanId);
        }
        if (traceFlags != null) {
            headers.put("trace_flags", traceFlags);
        }
        if (traceState != null) {
            headers.put("trace_state", traceState);
        }
        
        // Custom tags (prefixed)
        for (Map.Entry<String, String> tag : tags.entrySet()) {
            headers.put("tags." + tag.getKey(), tag.getValue());
        }
        
        return headers;
    }
    
    /**
     * Build headers as a map suitable for Kafka record headers.
     * All values are converted to byte arrays.
     */
    public Map<String, byte[]> buildAsBytes() {
        Map<String, byte[]> byteHeaders = new HashMap<>();
        for (Map.Entry<String, Object> entry : build().entrySet()) {
            byteHeaders.put(entry.getKey(), entry.getValue().toString().getBytes());
        }
        return byteHeaders;
    }
    
    /**
     * Extract correlation ID from incoming Kafka headers.
     */
    public static String extractCorrelationId(org.apache.kafka.common.header.Headers headers) {
        if (headers == null) return null;
        var header = headers.lastHeader("correlation_id");
        if (header != null && header.value() != null) {
            return new String(header.value());
        }
        return null;
    }
    
    /**
     * Extract trace ID from incoming Kafka headers.
     */
    public static String extractTraceId(org.apache.kafka.common.header.Headers headers) {
        if (headers == null) return null;
        var header = headers.lastHeader("trace_id");
        if (header != null && header.value() != null) {
            return new String(header.value());
        }
        return null;
    }
    
    /**
     * Extract origin system from incoming Kafka headers.
     */
    public static String extractOriginSystem(org.apache.kafka.common.header.Headers headers) {
        if (headers == null) return null;
        var header = headers.lastHeader("origin_system");
        if (header != null && header.value() != null) {
            return new String(header.value());
        }
        return null;
    }
    
    /**
     * Propagate headers from an incoming message to MDC for tracing continuity.
     */
    public static void propagateToMdc(org.apache.kafka.common.header.Headers headers) {
        String correlationId = extractCorrelationId(headers);
        if (correlationId != null) {
            MDC.put("correlationId", correlationId);
        }
        
        String traceId = extractTraceId(headers);
        if (traceId != null) {
            MDC.put("traceId", traceId);
        }
        
        String originSystem = extractOriginSystem(headers);
        if (originSystem != null) {
            MDC.put("originSystem", originSystem);
        }
    }
}
