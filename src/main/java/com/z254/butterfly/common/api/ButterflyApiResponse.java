package com.z254.butterfly.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Standard API response wrapper for BUTTERFLY ecosystem services.
 * 
 * <p>Provides a consistent response structure for successful API calls
 * with support for pagination, metadata, and warnings.
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Simple data response
 * ButterflyApiResponse<User> response = ButterflyApiResponse.ok(user);
 * 
 * // Paginated response
 * ButterflyApiResponse<List<User>> response = ButterflyApiResponse.paginated(
 *     users, 0, 20, 150, "users"
 * );
 * }</pre>
 * 
 * @param <T> The type of data in the response
 * @since 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"data", "meta", "pagination", "warnings", "links"})
public record ButterflyApiResponse<T>(
        /**
         * The response payload.
         */
        T data,
        
        /**
         * Response metadata including timestamps and service info.
         */
        ResponseMeta meta,
        
        /**
         * Pagination information for list responses.
         */
        Pagination pagination,
        
        /**
         * Non-fatal warnings to communicate to clients.
         */
        List<Warning> warnings,
        
        /**
         * HATEOAS-style links for resource navigation.
         */
        Map<String, String> links
) {
    
    /**
     * Create a simple success response.
     */
    public static <T> ButterflyApiResponse<T> ok(T data) {
        return new ButterflyApiResponse<>(
                data,
                ResponseMeta.now(),
                null, null, null
        );
    }
    
    /**
     * Create a success response with metadata.
     */
    public static <T> ButterflyApiResponse<T> ok(T data, String service) {
        return new ButterflyApiResponse<>(
                data,
                new ResponseMeta(Instant.now(), service, null),
                null, null, null
        );
    }
    
    /**
     * Create a paginated response.
     */
    public static <T> ButterflyApiResponse<T> paginated(
            T data, int offset, int limit, long total, String resourceName) {
        
        int page = limit > 0 ? offset / limit : 0;
        int totalPages = limit > 0 ? (int) Math.ceil((double) total / limit) : 1;
        
        return new ButterflyApiResponse<>(
                data,
                ResponseMeta.now(),
                new Pagination(offset, limit, total, page, totalPages, 
                        page > 0, page < totalPages - 1, resourceName),
                null, null
        );
    }
    
    /**
     * Create a response with warnings.
     */
    public static <T> ButterflyApiResponse<T> withWarnings(T data, List<Warning> warnings) {
        return new ButterflyApiResponse<>(
                data,
                ResponseMeta.now(),
                null, warnings, null
        );
    }
    
    /**
     * Create a response with links.
     */
    public static <T> ButterflyApiResponse<T> withLinks(T data, Map<String, String> links) {
        return new ButterflyApiResponse<>(
                data,
                ResponseMeta.now(),
                null, null, links
        );
    }
    
    /**
     * Response metadata.
     */
    public record ResponseMeta(
            Instant timestamp,
            String service,
            String requestId
    ) {
        public static ResponseMeta now() {
            return new ResponseMeta(Instant.now(), null, null);
        }
    }
    
    /**
     * Pagination information.
     */
    public record Pagination(
            int offset,
            int limit,
            long total,
            int page,
            int totalPages,
            boolean hasPrevious,
            boolean hasNext,
            String resource
    ) {}
    
    /**
     * Warning message for non-fatal issues.
     */
    public record Warning(
            String code,
            String message,
            String field
    ) {
        public static Warning of(String code, String message) {
            return new Warning(code, message, null);
        }
        
        public static Warning forField(String field, String code, String message) {
            return new Warning(code, message, field);
        }
    }
}
