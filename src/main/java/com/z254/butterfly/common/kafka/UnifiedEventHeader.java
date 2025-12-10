package com.z254.butterfly.common.kafka;

import com.z254.butterfly.common.identity.RimNodeId;

import java.time.Instant;
import java.util.*;

/**
 * Strongly-typed unified event header for cross-system traceability.
 * 
 * <p>Every event flowing through the BUTTERFLY ecosystem must carry this header
 * to enable end-to-end trace graphs and decision episode reconstruction.
 * 
 * <h2>Required Fields</h2>
 * <ul>
 *   <li>{@code eventId} - Unique identifier for this specific event</li>
 *   <li>{@code correlationId} - Trace root linking all events in a decision flow</li>
 *   <li>{@code originSystem} - System that originated this event</li>
 * </ul>
 * 
 * <h2>Identity Anchoring</h2>
 * <ul>
 *   <li>{@code primaryRimNode} - The primary RIM entity this event concerns</li>
 *   <li>{@code affectedRimNodes} - All RIM entities affected by this event</li>
 *   <li>{@code decisionEpisodeId} - Links to the Decision Episode CAPSULE</li>
 * </ul>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * UnifiedEventHeader header = UnifiedEventHeader.builder()
 *     .correlationId(correlationId)
 *     .originSystem(OriginSystem.SYNAPSE)
 *     .primaryRimNode(RimNodeId.parse("rim:entity:finance:EURUSD"))
 *     .build();
 * }</pre>
 * 
 * @see UnifiedEventHeaderBuilder
 * @see com.z254.butterfly.common.tracing.UnifiedEventContext
 * @since 2.0.0
 */
public record UnifiedEventHeader(
    /**
     * Unique identifier for this event. Auto-generated if not provided.
     */
    String eventId,
    
    /**
     * Correlation ID linking all events in a decision flow.
     * This is the trace root and is REQUIRED.
     */
    String correlationId,
    
    /**
     * ID of the event that directly caused this event.
     * Used to build causal chains within a correlation.
     */
    String causationId,
    
    /**
     * System that originated this event.
     */
    OriginSystem originSystem,
    
    /**
     * Service instance identifier (e.g., "synapse-service-pod-abc123").
     */
    String sourceService,
    
    /**
     * Timestamp when this event was created.
     */
    Instant timestamp,
    
    /**
     * OpenTelemetry trace ID for distributed tracing integration.
     */
    String traceId,
    
    /**
     * OpenTelemetry span ID.
     */
    String spanId,
    
    /**
     * W3C trace flags.
     */
    String traceFlags,
    
    /**
     * W3C trace state.
     */
    String traceState,
    
    /**
     * The primary RIM entity this event concerns.
     * Anchors the event to the identity fabric.
     */
    RimNodeId primaryRimNode,
    
    /**
     * All RIM entities affected by this event.
     */
    List<RimNodeId> affectedRimNodes,
    
    /**
     * Links to the Decision Episode CAPSULE that aggregates
     * all events for this decision flow.
     */
    String decisionEpisodeId,
    
    /**
     * Schema version for forward/backward compatibility.
     */
    String schemaVersion,
    
    /**
     * Custom tags for additional context.
     */
    Map<String, String> tags
) {
    
    /**
     * Current schema version.
     */
    public static final String CURRENT_SCHEMA_VERSION = "2.0.0";
    
    /**
     * Systems that can originate events in the BUTTERFLY ecosystem.
     */
    public enum OriginSystem {
        PERCEPTION,
        CAPSULE,
        ODYSSEY,
        PLATO,
        NEXUS,
        SYNAPSE,
        CORTEX,
        PORTAL,
        EXTERNAL
    }
    
    /**
     * Compact constructor with validation and defaults.
     */
    public UnifiedEventHeader {
        // Generate event ID if not provided
        if (eventId == null || eventId.isBlank()) {
            eventId = UUID.randomUUID().toString();
        }
        
        // Correlation ID is required
        Objects.requireNonNull(correlationId, "correlationId is required for traceability");
        if (correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId cannot be blank");
        }
        
        // Origin system is required
        Objects.requireNonNull(originSystem, "originSystem is required");
        
        // Default timestamp to now
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        
        // Default schema version
        if (schemaVersion == null || schemaVersion.isBlank()) {
            schemaVersion = CURRENT_SCHEMA_VERSION;
        }
        
        // Ensure immutable collections
        affectedRimNodes = affectedRimNodes != null 
            ? List.copyOf(affectedRimNodes) 
            : List.of();
        tags = tags != null 
            ? Map.copyOf(tags) 
            : Map.of();
    }
    
    /**
     * Create a builder for constructing UnifiedEventHeader instances.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Create a child header that inherits correlation context from this header.
     * 
     * @param childOriginSystem the system creating the child event
     * @return a new header linked to this one via causation
     */
    public UnifiedEventHeader createChild(OriginSystem childOriginSystem) {
        return builder()
            .correlationId(this.correlationId)
            .causationId(this.eventId)
            .originSystem(childOriginSystem)
            .primaryRimNode(this.primaryRimNode)
            .affectedRimNodes(this.affectedRimNodes)
            .decisionEpisodeId(this.decisionEpisodeId)
            .traceId(this.traceId)
            .spanId(this.spanId)
            .traceFlags(this.traceFlags)
            .traceState(this.traceState)
            .build();
    }
    
    /**
     * Create a copy with an additional affected RIM node.
     */
    public UnifiedEventHeader withAffectedRimNode(RimNodeId rimNodeId) {
        List<RimNodeId> newAffected = new ArrayList<>(this.affectedRimNodes);
        if (!newAffected.contains(rimNodeId)) {
            newAffected.add(rimNodeId);
        }
        return new UnifiedEventHeader(
            eventId, correlationId, causationId, originSystem, sourceService,
            timestamp, traceId, spanId, traceFlags, traceState,
            primaryRimNode, newAffected, decisionEpisodeId, schemaVersion, tags
        );
    }
    
    /**
     * Create a copy with the decision episode ID set.
     */
    public UnifiedEventHeader withDecisionEpisodeId(String episodeId) {
        return new UnifiedEventHeader(
            eventId, correlationId, causationId, originSystem, sourceService,
            timestamp, traceId, spanId, traceFlags, traceState,
            primaryRimNode, affectedRimNodes, episodeId, schemaVersion, tags
        );
    }
    
    /**
     * Create a copy with an additional tag.
     */
    public UnifiedEventHeader withTag(String key, String value) {
        Map<String, String> newTags = new HashMap<>(this.tags);
        newTags.put(key, value);
        return new UnifiedEventHeader(
            eventId, correlationId, causationId, originSystem, sourceService,
            timestamp, traceId, spanId, traceFlags, traceState,
            primaryRimNode, affectedRimNodes, decisionEpisodeId, schemaVersion, newTags
        );
    }
    
    /**
     * Check if this header has complete identity anchoring.
     */
    public boolean hasIdentityAnchoring() {
        return primaryRimNode != null;
    }
    
    /**
     * Check if this header is linked to a decision episode.
     */
    public boolean hasDecisionEpisode() {
        return decisionEpisodeId != null && !decisionEpisodeId.isBlank();
    }
    
    /**
     * Convert to a map suitable for Kafka headers.
     */
    public Map<String, String> toHeaderMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("event_id", eventId);
        map.put("correlation_id", correlationId);
        if (causationId != null) map.put("causation_id", causationId);
        map.put("origin_system", originSystem.name());
        if (sourceService != null) map.put("source_service", sourceService);
        map.put("timestamp", String.valueOf(timestamp.toEpochMilli()));
        if (traceId != null) map.put("trace_id", traceId);
        if (spanId != null) map.put("span_id", spanId);
        if (traceFlags != null) map.put("trace_flags", traceFlags);
        if (traceState != null) map.put("trace_state", traceState);
        if (primaryRimNode != null) map.put("primary_rim_node", primaryRimNode.toString());
        if (!affectedRimNodes.isEmpty()) {
            map.put("affected_rim_nodes", String.join(",", 
                affectedRimNodes.stream().map(RimNodeId::toString).toList()));
        }
        if (decisionEpisodeId != null) map.put("decision_episode_id", decisionEpisodeId);
        map.put("schema_version", schemaVersion);
        tags.forEach((k, v) -> map.put("tags." + k, v));
        return map;
    }
    
    /**
     * Convert to a map suitable for Kafka headers as byte arrays.
     */
    public Map<String, byte[]> toHeaderBytes() {
        Map<String, byte[]> byteMap = new LinkedHashMap<>();
        toHeaderMap().forEach((k, v) -> byteMap.put(k, v.getBytes()));
        return byteMap;
    }
    
    /**
     * Parse from Kafka headers.
     */
    public static UnifiedEventHeader fromHeaders(org.apache.kafka.common.header.Headers headers) {
        if (headers == null) {
            throw new IllegalArgumentException("Headers cannot be null");
        }
        
        Builder builder = builder();
        
        String eventId = extractHeader(headers, "event_id");
        if (eventId != null) builder.eventId(eventId);
        
        String correlationId = extractHeader(headers, "correlation_id");
        if (correlationId == null) {
            throw new IllegalArgumentException("correlation_id header is required");
        }
        builder.correlationId(correlationId);
        
        builder.causationId(extractHeader(headers, "causation_id"));
        
        String originSystemStr = extractHeader(headers, "origin_system");
        if (originSystemStr != null) {
            builder.originSystem(OriginSystem.valueOf(originSystemStr));
        }
        
        builder.sourceService(extractHeader(headers, "source_service"));
        
        String timestampStr = extractHeader(headers, "timestamp");
        if (timestampStr != null) {
            builder.timestamp(Instant.ofEpochMilli(Long.parseLong(timestampStr)));
        }
        
        builder.traceId(extractHeader(headers, "trace_id"));
        builder.spanId(extractHeader(headers, "span_id"));
        builder.traceFlags(extractHeader(headers, "trace_flags"));
        builder.traceState(extractHeader(headers, "trace_state"));
        
        String primaryRimNodeStr = extractHeader(headers, "primary_rim_node");
        if (primaryRimNodeStr != null) {
            builder.primaryRimNode(RimNodeId.parse(primaryRimNodeStr));
        }
        
        String affectedRimNodesStr = extractHeader(headers, "affected_rim_nodes");
        if (affectedRimNodesStr != null && !affectedRimNodesStr.isBlank()) {
            List<RimNodeId> affectedNodes = Arrays.stream(affectedRimNodesStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(RimNodeId::parse)
                .toList();
            builder.affectedRimNodes(affectedNodes);
        }
        
        builder.decisionEpisodeId(extractHeader(headers, "decision_episode_id"));
        builder.schemaVersion(extractHeader(headers, "schema_version"));
        
        // Extract tags
        for (org.apache.kafka.common.header.Header header : headers) {
            if (header.key().startsWith("tags.")) {
                String tagKey = header.key().substring(5);
                builder.tag(tagKey, new String(header.value()));
            }
        }
        
        return builder.build();
    }
    
    private static String extractHeader(org.apache.kafka.common.header.Headers headers, String key) {
        var header = headers.lastHeader(key);
        if (header != null && header.value() != null) {
            return new String(header.value());
        }
        return null;
    }
    
    /**
     * Builder for UnifiedEventHeader.
     */
    public static class Builder {
        private String eventId;
        private String correlationId;
        private String causationId;
        private OriginSystem originSystem;
        private String sourceService;
        private Instant timestamp;
        private String traceId;
        private String spanId;
        private String traceFlags;
        private String traceState;
        private RimNodeId primaryRimNode;
        private List<RimNodeId> affectedRimNodes = new ArrayList<>();
        private String decisionEpisodeId;
        private String schemaVersion;
        private Map<String, String> tags = new HashMap<>();
        
        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }
        
        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }
        
        public Builder causationId(String causationId) {
            this.causationId = causationId;
            return this;
        }
        
        public Builder originSystem(OriginSystem originSystem) {
            this.originSystem = originSystem;
            return this;
        }
        
        public Builder sourceService(String sourceService) {
            this.sourceService = sourceService;
            return this;
        }
        
        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }
        
        public Builder spanId(String spanId) {
            this.spanId = spanId;
            return this;
        }
        
        public Builder traceFlags(String traceFlags) {
            this.traceFlags = traceFlags;
            return this;
        }
        
        public Builder traceState(String traceState) {
            this.traceState = traceState;
            return this;
        }
        
        public Builder primaryRimNode(RimNodeId primaryRimNode) {
            this.primaryRimNode = primaryRimNode;
            return this;
        }
        
        public Builder affectedRimNodes(List<RimNodeId> affectedRimNodes) {
            this.affectedRimNodes = affectedRimNodes != null ? new ArrayList<>(affectedRimNodes) : new ArrayList<>();
            return this;
        }
        
        public Builder addAffectedRimNode(RimNodeId rimNodeId) {
            if (rimNodeId != null && !this.affectedRimNodes.contains(rimNodeId)) {
                this.affectedRimNodes.add(rimNodeId);
            }
            return this;
        }
        
        public Builder decisionEpisodeId(String decisionEpisodeId) {
            this.decisionEpisodeId = decisionEpisodeId;
            return this;
        }
        
        public Builder schemaVersion(String schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }
        
        public Builder tags(Map<String, String> tags) {
            this.tags = tags != null ? new HashMap<>(tags) : new HashMap<>();
            return this;
        }
        
        public Builder tag(String key, String value) {
            this.tags.put(key, value);
            return this;
        }
        
        /**
         * Initialize from MDC context for tracing continuity.
         */
        public Builder fromMdc() {
            String mdcCorrelationId = org.slf4j.MDC.get("correlationId");
            if (mdcCorrelationId == null) {
                mdcCorrelationId = org.slf4j.MDC.get("X-Correlation-ID");
            }
            if (mdcCorrelationId != null) {
                this.correlationId = mdcCorrelationId;
            }
            
            String mdcTraceId = org.slf4j.MDC.get("traceId");
            if (mdcTraceId != null) {
                this.traceId = mdcTraceId;
            }
            
            String mdcSpanId = org.slf4j.MDC.get("spanId");
            if (mdcSpanId != null) {
                this.spanId = mdcSpanId;
            }
            
            return this;
        }
        
        public UnifiedEventHeader build() {
            return new UnifiedEventHeader(
                eventId, correlationId, causationId, originSystem, sourceService,
                timestamp, traceId, spanId, traceFlags, traceState,
                primaryRimNode, affectedRimNodes, decisionEpisodeId, schemaVersion, tags
            );
        }
    }
}
