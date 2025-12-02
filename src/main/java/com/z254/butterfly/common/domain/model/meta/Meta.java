package com.z254.butterfly.common.domain.model.meta;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * CAPSULE Meta block containing provenance and quality metadata.
 * Captures information about the capsule itself.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Meta {
    /**
     * Unique capsule identifier
     */
    private String capsuleId;
    
    /**
     * Schema version used
     */
    private String schemaVersion;
    
    /**
     * Creation timestamp
     */
    private Instant createdAt;
    
    /**
     * Last update timestamp
     */
    private Instant updatedAt;
    
    /**
     * Source system identifier
     */
    private String sourceSystem;
    
    /**
     * Provenance chain
     */
    private List<ProvenanceEntry> provenance;
    
    /**
     * Quality metrics
     */
    private QualityMetrics quality;
    
    /**
     * Tags for categorization
     */
    private List<String> tags;
    
    /**
     * Additional metadata
     */
    private Map<String, Object> attributes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProvenanceEntry {
        private String step;
        private String system;
        private Instant timestamp;
        private String actor;
        private String action;
        private Map<String, Object> details;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QualityMetrics {
        private Double completeness;
        private Double accuracy;
        private Double timeliness;
        private Double consistency;
        private Integer sourceCount;
        private List<String> validationErrors;
    }
}
