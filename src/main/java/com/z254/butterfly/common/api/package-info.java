/**
 * BUTTERFLY Common API utilities and standards.
 * 
 * <p>This package provides standardized API components for all BUTTERFLY
 * ecosystem services including:
 * 
 * <ul>
 *   <li>{@link com.z254.butterfly.common.api.ProblemDetail} - RFC 7807 error responses</li>
 *   <li>{@link com.z254.butterfly.common.api.ProblemDetailExceptionHandler} - Global exception handling</li>
 *   <li>{@link com.z254.butterfly.common.api.ButterflyApiResponse} - Standard response wrapper</li>
 *   <li>{@link com.z254.butterfly.common.api.ApiVersioningFilter} - API versioning infrastructure</li>
 * </ul>
 * 
 * <h2>Error Response Format (RFC 7807)</h2>
 * <pre>{@code
 * {
 *   "type": "https://api.butterfly.254studioz.com/errors/not-found",
 *   "title": "Not Found",
 *   "status": 404,
 *   "detail": "Capsule with ID 'abc-123' was not found",
 *   "instance": "/api/v1/capsules/abc-123",
 *   "traceId": "trace-456",
 *   "timestamp": "2025-01-15T10:30:00Z",
 *   "service": "capsule"
 * }
 * }</pre>
 * 
 * <h2>Usage</h2>
 * <p>To enable standardized error handling in a service:
 * <pre>{@code
 * @SpringBootApplication
 * @Import(ProblemDetailExceptionHandler.class)
 * public class MyServiceApplication { }
 * }</pre>
 * 
 * @since 1.0.0
 */
package com.z254.butterfly.common.api;
