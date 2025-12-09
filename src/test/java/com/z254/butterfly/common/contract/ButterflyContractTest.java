package com.z254.butterfly.common.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.z254.butterfly.common.api.ProblemDetail;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Base class for BUTTERFLY service contract tests.
 * 
 * <p>Provides standard assertions for:
 * <ul>
 *   <li>SLA compliance (latency, availability)</li>
 *   <li>Response format validation</li>
 *   <li>Header compliance</li>
 *   <li>Error response format (RFC 7807)</li>
 * </ul>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * class CapsuleNexusContractTest extends ButterflyContractTest {
 *     
 *     @Override
 *     protected ContractDefinition loadContract() {
 *         return loadContractFromResource("contracts/capsule-to-nexus.json");
 *     }
 *     
 *     @Test
 *     void testLearningSignalContract() {
 *         // Given
 *         LearningSignalRequest request = new LearningSignalRequest(...);
 *         
 *         // When
 *         ResponseEntity<LearningSignalResponse> response = 
 *             nexusClient.submitLearningSignal(request);
 *         
 *         // Then
 *         assertResponseWithinSla(response, Duration.ofMillis(500));
 *         assertStandardHeaders(response.getHeaders());
 *         assertNotNull(response.getBody());
 *     }
 * }
 * }</pre>
 * 
 * @since 1.0.0
 */
public abstract class ButterflyContractTest {
    
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final ObjectMapper objectMapper = new ObjectMapper();
    
    protected ContractDefinition contract;
    
    @BeforeEach
    void loadContractDefinition() {
        contract = loadContract();
        if (contract != null) {
            log.info("Loaded contract: {} -> {} (version {})",
                    contract.consumer(), contract.provider(), contract.version());
        }
    }
    
    /**
     * Load the contract definition for this test.
     * Override to provide the specific contract.
     */
    protected abstract ContractDefinition loadContract();
    
    // === SLA Assertions ===
    
    /**
     * Assert that a response was received within the SLA latency threshold.
     * 
     * @param actual Actual duration
     * @param sla SLA threshold
     */
    protected void assertLatencyWithinSla(Duration actual, Duration sla) {
        assertTrue(actual.compareTo(sla) <= 0,
                String.format("Latency %dms exceeds SLA of %dms",
                        actual.toMillis(), sla.toMillis()));
    }
    
    /**
     * Assert that a response was received within the SLA latency threshold.
     * 
     * @param response Response entity with timing
     * @param sla SLA threshold
     */
    protected void assertResponseWithinSla(ResponseEntity<?> response, Duration sla) {
        // Extract timing from headers if available
        String timingHeader = response.getHeaders().getFirst("X-Response-Time");
        if (timingHeader != null) {
            long millis = Long.parseLong(timingHeader.replace("ms", "").trim());
            assertLatencyWithinSla(Duration.ofMillis(millis), sla);
        }
    }
    
    /**
     * Measure and assert execution time.
     * 
     * @param runnable Operation to measure
     * @param sla SLA threshold
     */
    protected void assertExecutionWithinSla(Runnable runnable, Duration sla) {
        Instant start = Instant.now();
        runnable.run();
        Duration duration = Duration.between(start, Instant.now());
        assertLatencyWithinSla(duration, sla);
    }
    
    /**
     * Assert service P99 latency meets SLA.
     * Typically used with metrics endpoint data.
     * 
     * @param p99Millis P99 latency in milliseconds
     * @param slaMillis SLA threshold in milliseconds
     */
    protected void assertP99LatencyWithinSla(double p99Millis, long slaMillis) {
        assertTrue(p99Millis <= slaMillis,
                String.format("P99 latency %.2fms exceeds SLA of %dms", p99Millis, slaMillis));
    }
    
    // === Response Format Assertions ===
    
    /**
     * Assert that an error response conforms to RFC 7807 Problem Details.
     * 
     * @param response Error response
     */
    protected void assertValidProblemDetail(ResponseEntity<?> response) {
        assertTrue(response.getStatusCode().isError(),
                "Expected error status code but got " + response.getStatusCode());
        
        String contentType = response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
        assertTrue(contentType != null && contentType.contains("application/problem+json"),
                "Error response should have Content-Type: application/problem+json");
        
        Object body = response.getBody();
        if (body instanceof ProblemDetail problem) {
            assertNotNull(problem.type(), "ProblemDetail must have 'type'");
            assertNotNull(problem.title(), "ProblemDetail must have 'title'");
            assertTrue(problem.status() >= 400, "ProblemDetail status must be >= 400");
        } else if (body instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> problemMap = (Map<String, Object>) body;
            assertTrue(problemMap.containsKey("type"), "ProblemDetail must have 'type'");
            assertTrue(problemMap.containsKey("title"), "ProblemDetail must have 'title'");
            assertTrue(problemMap.containsKey("status"), "ProblemDetail must have 'status'");
        }
    }
    
    /**
     * Assert that a response has a valid JSON body.
     * 
     * @param response Response to validate
     */
    protected void assertValidJsonResponse(ResponseEntity<?> response) {
        assertNotNull(response.getBody(), "Response body should not be null");
        
        String contentType = response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
        assertTrue(contentType != null && contentType.contains("application/json"),
                "Response should have Content-Type: application/json");
    }
    
    /**
     * Assert that response contains expected fields.
     * 
     * @param response Response entity
     * @param requiredFields Fields that must be present
     */
    protected void assertResponseContainsFields(ResponseEntity<?> response, String... requiredFields) {
        Object body = response.getBody();
        assertNotNull(body, "Response body should not be null");
        
        if (body instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) body;
            for (String field : requiredFields) {
                assertTrue(map.containsKey(field),
                        "Response should contain field: " + field);
            }
        }
    }
    
    // === Header Assertions ===
    
    /**
     * Assert that standard BUTTERFLY headers are present.
     * 
     * @param headers Response headers
     */
    protected void assertStandardHeaders(HttpHeaders headers) {
        // API version header
        String apiVersion = headers.getFirst("X-API-Version");
        assertNotNull(apiVersion, "Response should include X-API-Version header");
        
        // Correlation ID (if provided in request)
        // This is typically echoed back
    }
    
    /**
     * Assert that correlation ID is propagated.
     * 
     * @param requestCorrelationId Original correlation ID
     * @param headers Response headers
     */
    protected void assertCorrelationIdPropagated(String requestCorrelationId, HttpHeaders headers) {
        String responseCorrelationId = headers.getFirst("X-Correlation-ID");
        if (requestCorrelationId != null) {
            assertEquals(requestCorrelationId, responseCorrelationId,
                    "Correlation ID should be propagated in response");
        }
    }
    
    /**
     * Assert rate limit headers are present.
     * 
     * @param headers Response headers
     */
    protected void assertRateLimitHeaders(HttpHeaders headers) {
        assertNotNull(headers.getFirst("X-RateLimit-Limit"),
                "Should include X-RateLimit-Limit header");
        assertNotNull(headers.getFirst("X-RateLimit-Remaining"),
                "Should include X-RateLimit-Remaining header");
    }
    
    // === Status Code Assertions ===
    
    /**
     * Assert successful response (2xx).
     */
    protected void assertSuccess(ResponseEntity<?> response) {
        assertTrue(response.getStatusCode().is2xxSuccessful(),
                "Expected 2xx status but got " + response.getStatusCode());
    }
    
    /**
     * Assert specific status code.
     */
    protected void assertStatusCode(ResponseEntity<?> response, HttpStatus expected) {
        assertEquals(expected, response.getStatusCode(),
                "Expected status " + expected + " but got " + response.getStatusCode());
    }
    
    // === Contract Loading ===
    
    /**
     * Load a contract definition from resources.
     * 
     * @param resourcePath Path to contract JSON file
     * @return Parsed contract definition
     */
    protected ContractDefinition loadContractFromResource(String resourcePath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                log.warn("Contract resource not found: {}", resourcePath);
                return null;
            }
            return objectMapper.readValue(is, ContractDefinition.class);
        } catch (IOException e) {
            log.error("Failed to load contract: {}", resourcePath, e);
            return null;
        }
    }
    
    /**
     * Get the SLA for an interaction from the contract.
     * 
     * @param interactionName Name of the interaction
     * @return SLA duration or default
     */
    protected Duration getSlaForInteraction(String interactionName) {
        if (contract == null || contract.interactions() == null) {
            return Duration.ofSeconds(5); // Default SLA
        }
        
        return contract.interactions().stream()
                .filter(i -> i.name().equals(interactionName))
                .findFirst()
                .map(i -> Duration.ofMillis(i.slaMillis()))
                .orElse(Duration.ofSeconds(5));
    }
    
    // === Contract Definition DTOs ===
    
    /**
     * Contract definition record.
     */
    public record ContractDefinition(
            String consumer,
            String provider,
            String version,
            String description,
            List<Interaction> interactions,
            Sla defaultSla
    ) {}
    
    /**
     * Interaction definition.
     */
    public record Interaction(
            String name,
            String description,
            String method,
            String path,
            Map<String, Object> requestSchema,
            Map<String, Object> responseSchema,
            int expectedStatus,
            long slaMillis,
            List<String> requiredHeaders
    ) {}
    
    /**
     * SLA definition.
     */
    public record Sla(
            long p50Millis,
            long p95Millis,
            long p99Millis,
            double availabilityPercent,
            int errorRatePercent
    ) {
        public static Sla defaults() {
            return new Sla(100, 500, 1000, 99.9, 1);
        }
    }
}
