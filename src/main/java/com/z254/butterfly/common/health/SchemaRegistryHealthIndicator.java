package com.z254.butterfly.common.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Health indicator for Confluent Schema Registry.
 * <p>
 * This indicator verifies that the Schema Registry is reachable and functioning
 * by querying its subjects endpoint.
 * <p>
 * Health status:
 * <ul>
 *     <li>UP - Schema Registry is reachable and responding</li>
 *     <li>DOWN - Cannot connect or receive valid response</li>
 * </ul>
 * <p>
 * Details provided:
 * <ul>
 *     <li>subjectCount - Number of registered schemas</li>
 *     <li>url - The Schema Registry URL being checked</li>
 * </ul>
 */
@Slf4j
public class SchemaRegistryHealthIndicator implements HealthIndicator {

    private final String schemaRegistryUrl;
    private final RestTemplate restTemplate;

    /**
     * Create a new Schema Registry health indicator.
     *
     * @param schemaRegistryUrl the Schema Registry base URL
     */
    public SchemaRegistryHealthIndicator(String schemaRegistryUrl) {
        this.schemaRegistryUrl = schemaRegistryUrl;
        this.restTemplate = new RestTemplate();
    }
    
    /**
     * Create with a custom RestTemplate.
     *
     * @param schemaRegistryUrl the Schema Registry base URL
     * @param restTemplate      custom REST template (e.g., with authentication)
     */
    public SchemaRegistryHealthIndicator(String schemaRegistryUrl, RestTemplate restTemplate) {
        this.schemaRegistryUrl = schemaRegistryUrl;
        this.restTemplate = restTemplate;
    }

    @Override
    public Health health() {
        try {
            // Query subjects endpoint to verify connectivity
            String subjectsUrl = schemaRegistryUrl + "/subjects";
            String[] subjects = restTemplate.getForObject(subjectsUrl, String[].class);
            
            int subjectCount = subjects != null ? subjects.length : 0;
            
            return Health.up()
                .withDetail("url", schemaRegistryUrl)
                .withDetail("subjectCount", subjectCount)
                .build();
            
        } catch (Exception e) {
            log.warn("Schema Registry health check failed: {}", e.getMessage());
            return Health.down()
                .withDetail("url", schemaRegistryUrl)
                .withDetail("error", "Failed to connect to Schema Registry")
                .withDetail("exception", e.getClass().getSimpleName())
                .withDetail("message", e.getMessage())
                .build();
        }
    }
}
