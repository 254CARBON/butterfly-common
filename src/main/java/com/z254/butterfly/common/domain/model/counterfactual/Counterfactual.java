package com.z254.butterfly.common.domain.model.counterfactual;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * CAPSULE Counterfactual block representing alternative scenarios.
 * Captures what-if analysis and alternative pathways.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Counterfactual {
    /**
     * List of alternative scenarios considered
     */
    private List<Alternative> alternatives;
    
    /**
     * Key assumptions made in the analysis
     */
    private List<String> assumptions;
    
    /**
     * Sensitivity analysis results
     */
    private Map<String, Double> sensitivities;
    
    /**
     * Additional counterfactual metadata
     */
    private Map<String, Object> metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Alternative {
        private String alternativeId;
        private String description;
        private Double probability;
        private Double impactScore;
        private List<String> triggerConditions;
        private List<String> outcomes;
        private Map<String, Object> attributes;
    }
}
