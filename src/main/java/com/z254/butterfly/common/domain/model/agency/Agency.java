package com.z254.butterfly.common.domain.model.agency;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * CAPSULE Agency block representing actors and their intents.
 * Captures who/what is acting and their goals within the scenario.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Agency {
    /**
     * List of actors involved in this scenario
     */
    private List<Actor> actors;
    
    /**
     * Collective intent or goal
     */
    private String collectiveIntent;
    
    /**
     * Coordination signals between actors
     */
    private List<String> coordinationSignals;
    
    /**
     * Additional agency metadata
     */
    private Map<String, Object> metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Actor {
        private String actorId;
        private String actorType;
        private String name;
        private String role;
        private Double influence;
        private List<String> intents;
        private Map<String, Object> attributes;
    }
}
