package com.z254.butterfly.common.domain.model.dynamics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * CAPSULE Dynamics block representing temporal evolution and trends.
 * Captures how the scenario changes over time.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Dynamics {
    /**
     * Current trend direction
     */
    private TrendDirection trend;
    
    /**
     * Rate of change (normalized 0-1)
     */
    private Double velocity;
    
    /**
     * Acceleration of change
     */
    private Double acceleration;
    
    /**
     * Historical trajectory points
     */
    private List<TrajectoryPoint> trajectory;
    
    /**
     * Projected future states
     */
    private List<Projection> projections;
    
    /**
     * Detected cycles or patterns
     */
    private List<String> cycles;
    
    /**
     * Additional dynamics metadata
     */
    private Map<String, Object> metadata;

    public enum TrendDirection {
        INCREASING,
        DECREASING,
        STABLE,
        VOLATILE,
        UNKNOWN
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrajectoryPoint {
        private Instant timestamp;
        private Double value;
        private Double confidence;
        private Map<String, Object> attributes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Projection {
        private String projectionId;
        private Instant targetTime;
        private Double projectedValue;
        private Double confidence;
        private String methodology;
        private Map<String, Object> assumptions;
    }
}
