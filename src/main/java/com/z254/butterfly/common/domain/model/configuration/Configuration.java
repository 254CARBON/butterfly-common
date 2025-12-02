package com.z254.butterfly.common.domain.model.configuration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * CAPSULE Configuration block representing the static state snapshot.
 * Captures entities, relationships, and spatial/temporal context.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Configuration {
    /**
     * Entities present in the configuration
     */
    private List<Entity> entities;
    
    /**
     * Relationships between entities
     */
    private List<Relationship> relationships;
    
    /**
     * Spatial context
     */
    private SpatialContext spatialContext;
    
    /**
     * Temporal context
     */
    private TemporalContext temporalContext;
    
    /**
     * Schema version for this configuration
     */
    private String schemaVersion;
    
    /**
     * Additional configuration metadata
     */
    private Map<String, Object> metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entity {
        private String entityId;
        private String entityType;
        private String name;
        private Map<String, Object> attributes;
        private List<String> tags;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Relationship {
        private String relationshipId;
        private String sourceEntityId;
        private String targetEntityId;
        private String relationshipType;
        private Double strength;
        private Map<String, Object> attributes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpatialContext {
        private String region;
        private String country;
        private String city;
        private Double latitude;
        private Double longitude;
        private String jurisdiction;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemporalContext {
        private Instant effectiveAt;
        private Instant validFrom;
        private Instant validTo;
        private String timezone;
        private String resolution;
    }
}
