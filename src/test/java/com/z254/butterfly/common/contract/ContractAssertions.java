package com.z254.butterfly.common.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fluent assertion utilities for contract testing.
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * ContractAssertions.forResponse(response)
 *     .expectSuccess()
 *     .expectHeader("X-API-Version")
 *     .expectLatencyUnder(Duration.ofMillis(500))
 *     .expectBodyContains("id", "createdAt")
 *     .verify();
 * }</pre>
 * 
 * @since 1.0.0
 */
public class ContractAssertions {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private ContractAssertions() {}
    
    /**
     * Create assertions for a response.
     */
    public static <T> ResponseAssertions<T> forResponse(ResponseEntity<T> response) {
        return new ResponseAssertions<>(response);
    }
    
    /**
     * Create assertions for timed execution.
     */
    public static <T> TimedAssertions<T> forTimed(Supplier<T> supplier) {
        return new TimedAssertions<>(supplier);
    }
    
    /**
     * Response assertions builder.
     */
    public static class ResponseAssertions<T> {
        private final ResponseEntity<T> response;
        private Duration measuredLatency;
        
        public ResponseAssertions(ResponseEntity<T> response) {
            this.response = response;
        }
        
        /**
         * Expect a 2xx success status.
         */
        public ResponseAssertions<T> expectSuccess() {
            assertTrue(response.getStatusCode().is2xxSuccessful(),
                    "Expected 2xx status but got " + response.getStatusCode());
            return this;
        }
        
        /**
         * Expect specific status code.
         */
        public ResponseAssertions<T> expectStatus(int status) {
            assertEquals(status, response.getStatusCode().value(),
                    "Unexpected status code");
            return this;
        }
        
        /**
         * Expect an error status (4xx or 5xx).
         */
        public ResponseAssertions<T> expectError() {
            assertTrue(response.getStatusCode().isError(),
                    "Expected error status but got " + response.getStatusCode());
            return this;
        }
        
        /**
         * Expect a specific header to be present.
         */
        public ResponseAssertions<T> expectHeader(String headerName) {
            assertNotNull(response.getHeaders().getFirst(headerName),
                    "Expected header: " + headerName);
            return this;
        }
        
        /**
         * Expect header with specific value.
         */
        public ResponseAssertions<T> expectHeader(String headerName, String value) {
            assertEquals(value, response.getHeaders().getFirst(headerName),
                    "Header " + headerName + " has unexpected value");
            return this;
        }
        
        /**
         * Expect Content-Type header.
         */
        public ResponseAssertions<T> expectContentType(String contentType) {
            String actual = response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
            assertNotNull(actual, "Content-Type header missing");
            assertTrue(actual.contains(contentType),
                    "Expected Content-Type containing " + contentType + " but got " + actual);
            return this;
        }
        
        /**
         * Expect JSON content type.
         */
        public ResponseAssertions<T> expectJsonContent() {
            return expectContentType("application/json");
        }
        
        /**
         * Expect problem+json content type for errors.
         */
        public ResponseAssertions<T> expectProblemJsonContent() {
            return expectContentType("application/problem+json");
        }
        
        /**
         * Expect non-null body.
         */
        public ResponseAssertions<T> expectBodyPresent() {
            assertNotNull(response.getBody(), "Response body should not be null");
            return this;
        }
        
        /**
         * Expect body to contain specific fields (for Map or JsonNode bodies).
         */
        public ResponseAssertions<T> expectBodyContains(String... fields) {
            Object body = response.getBody();
            assertNotNull(body, "Response body should not be null");
            
            if (body instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) body;
                for (String field : fields) {
                    assertTrue(map.containsKey(field), "Body missing field: " + field);
                }
            } else if (body instanceof JsonNode node) {
                for (String field : fields) {
                    assertTrue(node.has(field), "Body missing field: " + field);
                }
            }
            return this;
        }
        
        /**
         * Expect latency under threshold (from X-Response-Time header).
         */
        public ResponseAssertions<T> expectLatencyUnder(Duration threshold) {
            String timing = response.getHeaders().getFirst("X-Response-Time");
            if (timing != null) {
                long millis = Long.parseLong(timing.replace("ms", "").trim());
                assertTrue(millis <= threshold.toMillis(),
                        String.format("Latency %dms exceeds threshold %dms", millis, threshold.toMillis()));
            }
            return this;
        }
        
        /**
         * Set measured latency (for external timing).
         */
        public ResponseAssertions<T> withMeasuredLatency(Duration latency) {
            this.measuredLatency = latency;
            return this;
        }
        
        /**
         * Expect measured latency under threshold.
         */
        public ResponseAssertions<T> expectMeasuredLatencyUnder(Duration threshold) {
            assertNotNull(measuredLatency, "No measured latency available");
            assertTrue(measuredLatency.compareTo(threshold) <= 0,
                    String.format("Measured latency %dms exceeds threshold %dms",
                            measuredLatency.toMillis(), threshold.toMillis()));
            return this;
        }
        
        /**
         * Custom body assertion.
         */
        public ResponseAssertions<T> expectBody(Consumer<T> assertion) {
            assertion.accept(response.getBody());
            return this;
        }
        
        /**
         * Custom header assertion.
         */
        public ResponseAssertions<T> expectHeaders(Consumer<HttpHeaders> assertion) {
            assertion.accept(response.getHeaders());
            return this;
        }
        
        /**
         * Complete verification (no-op, for fluent termination).
         */
        public void verify() {
            // All assertions already executed
        }
        
        /**
         * Get the underlying response.
         */
        public ResponseEntity<T> getResponse() {
            return response;
        }
    }
    
    /**
     * Timed execution assertions.
     */
    public static class TimedAssertions<T> {
        private final Supplier<T> supplier;
        private T result;
        private Duration duration;
        private Exception exception;
        
        public TimedAssertions(Supplier<T> supplier) {
            this.supplier = supplier;
        }
        
        /**
         * Execute and measure.
         */
        public TimedAssertions<T> execute() {
            Instant start = Instant.now();
            try {
                this.result = supplier.get();
            } catch (Exception e) {
                this.exception = e;
            }
            this.duration = Duration.between(start, Instant.now());
            return this;
        }
        
        /**
         * Expect successful execution (no exception).
         */
        public TimedAssertions<T> expectSuccess() {
            assertNull(exception, "Expected success but got exception: " + exception);
            return this;
        }
        
        /**
         * Expect exception of type.
         */
        public <E extends Exception> TimedAssertions<T> expectException(Class<E> exceptionClass) {
            assertNotNull(exception, "Expected exception but none thrown");
            assertTrue(exceptionClass.isInstance(exception),
                    "Expected " + exceptionClass.getName() + " but got " + exception.getClass().getName());
            return this;
        }
        
        /**
         * Expect duration under threshold.
         */
        public TimedAssertions<T> expectDurationUnder(Duration threshold) {
            assertNotNull(duration, "No duration measured - call execute() first");
            assertTrue(duration.compareTo(threshold) <= 0,
                    String.format("Duration %dms exceeds threshold %dms",
                            duration.toMillis(), threshold.toMillis()));
            return this;
        }
        
        /**
         * Custom result assertion.
         */
        public TimedAssertions<T> expectResult(Consumer<T> assertion) {
            assertion.accept(result);
            return this;
        }
        
        /**
         * Get the result.
         */
        public T getResult() {
            return result;
        }
        
        /**
         * Get the measured duration.
         */
        public Duration getDuration() {
            return duration;
        }
    }
    
    // === Static Utility Methods ===
    
    /**
     * Assert that a list response has expected pagination.
     */
    public static void assertPagination(Map<String, Object> response, int expectedOffset, int expectedLimit) {
        @SuppressWarnings("unchecked")
        Map<String, Object> pagination = (Map<String, Object>) response.get("pagination");
        assertNotNull(pagination, "Response should include pagination");
        assertEquals(expectedOffset, pagination.get("offset"));
        assertEquals(expectedLimit, pagination.get("limit"));
    }
    
    /**
     * Assert that a value is a valid UUID.
     */
    public static void assertValidUuid(String value) {
        assertNotNull(value, "UUID should not be null");
        assertTrue(value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
                "Invalid UUID format: " + value);
    }
    
    /**
     * Assert that a timestamp is recent (within threshold of now).
     */
    public static void assertRecentTimestamp(Instant timestamp, Duration threshold) {
        Instant now = Instant.now();
        Duration age = Duration.between(timestamp, now);
        assertTrue(age.abs().compareTo(threshold) <= 0,
                String.format("Timestamp is not recent: %s (age: %dms)", timestamp, age.toMillis()));
    }
}
