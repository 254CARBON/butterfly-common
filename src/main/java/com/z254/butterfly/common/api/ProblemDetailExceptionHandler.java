package com.z254.butterfly.common.api;

import com.z254.butterfly.common.telemetry.TenantContextHolder;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Global exception handler that produces RFC 7807 Problem Details responses.
 * 
 * <p>This handler provides standardized error responses across all BUTTERFLY
 * ecosystem services. It handles common exceptions and maps them to appropriate
 * HTTP status codes and problem details.
 * 
 * <h2>Features</h2>
 * <ul>
 *   <li>RFC 7807 compliant error responses</li>
 *   <li>Automatic trace ID propagation</li>
 *   <li>Service identification in responses</li>
 *   <li>Structured logging for all errors</li>
 *   <li>Validation error field mapping</li>
 *   <li>Circuit breaker integration</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * <p>Import this handler via component scanning or include in your configuration:
 * <pre>{@code
 * @Import(ProblemDetailExceptionHandler.class)
 * public class MyServiceConfiguration { }
 * }</pre>
 * 
 * @since 1.0.0
 */
@RestControllerAdvice
public class ProblemDetailExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(ProblemDetailExceptionHandler.class);
    
    private static final String TRACE_ID_KEY = "traceId";
    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final MediaType PROBLEM_JSON = MediaType.valueOf("application/problem+json");
    
    @Value("${spring.application.name:unknown}")
    private String serviceName;
    
    @Value("${butterfly.api.error.include-stacktrace:false}")
    private boolean includeStackTrace;
    
    // --- Validation Errors (400) ---
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(
            MethodArgumentNotValidException ex, WebRequest request) {
        
        Map<String, Object> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );
        
        ProblemDetail problem = ProblemDetail.builder()
                .type(ProblemDetail.ErrorTypes.VALIDATION_ERROR)
                .title("Validation Failed")
                .status(400)
                .detail("Request validation failed. See fieldErrors for details.")
                .instance(getRequestUri(request))
                .traceId(getTraceId())
                .service(serviceName)
                .errorCode("VALIDATION_ERROR")
                .extension("fieldErrors", fieldErrors)
                .build();
        
        log.warn("Validation error: fields={}, traceId={}", fieldErrors.keySet(), problem.traceId());
        return createResponse(problem);
    }
    
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException ex, WebRequest request) {
        
        Map<String, String> violations = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        v -> v.getPropertyPath().toString(),
                        ConstraintViolation::getMessage,
                        (a, b) -> a + "; " + b
                ));
        
        ProblemDetail problem = ProblemDetail.builder()
                .type(ProblemDetail.ErrorTypes.VALIDATION_ERROR)
                .title("Constraint Violation")
                .status(400)
                .detail("Request constraints violated. See violations for details.")
                .instance(getRequestUri(request))
                .traceId(getTraceId())
                .service(serviceName)
                .errorCode("CONSTRAINT_VIOLATION")
                .extension("violations", violations)
                .build();
        
        log.warn("Constraint violation: violations={}, traceId={}", violations, problem.traceId());
        return createResponse(problem);
    }
    
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParameter(
            MissingServletRequestParameterException ex, WebRequest request) {
        
        ProblemDetail problem = ProblemDetail.builder()
                .type(ProblemDetail.ErrorTypes.BAD_REQUEST)
                .title("Missing Parameter")
                .status(400)
                .detail(String.format("Required parameter '%s' of type %s is missing",
                        ex.getParameterName(), ex.getParameterType()))
                .instance(getRequestUri(request))
                .traceId(getTraceId())
                .service(serviceName)
                .errorCode("MISSING_PARAMETER")
                .extension("parameter", ex.getParameterName())
                .build();
        
        log.warn("Missing parameter: {}, traceId={}", ex.getParameterName(), problem.traceId());
        return createResponse(problem);
    }
    
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, WebRequest request) {
        
        String requiredType = ex.getRequiredType() != null 
                ? ex.getRequiredType().getSimpleName() 
                : "unknown";
        
        ProblemDetail problem = ProblemDetail.builder()
                .type(ProblemDetail.ErrorTypes.BAD_REQUEST)
                .title("Type Mismatch")
                .status(400)
                .detail(String.format("Parameter '%s' should be of type %s", 
                        ex.getName(), requiredType))
                .instance(getRequestUri(request))
                .traceId(getTraceId())
                .service(serviceName)
                .errorCode("TYPE_MISMATCH")
                .extension("parameter", ex.getName())
                .extension("expectedType", requiredType)
                .build();
        
        log.warn("Type mismatch: parameter={}, expected={}, traceId={}", 
                ex.getName(), requiredType, problem.traceId());
        return createResponse(problem);
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {
        
        ProblemDetail problem = ProblemDetail.builder()
                .type(ProblemDetail.ErrorTypes.BAD_REQUEST)
                .title("Invalid Argument")
                .status(400)
                .detail(ex.getMessage())
                .instance(getRequestUri(request))
                .traceId(getTraceId())
                .service(serviceName)
                .errorCode("INVALID_ARGUMENT")
                .build();
        
        log.warn("Invalid argument: {}, traceId={}", ex.getMessage(), problem.traceId());
        return createResponse(problem);
    }
    
    // --- Authentication/Authorization Errors (401/403) ---
    
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(
            AuthenticationException ex, WebRequest request) {
        
        ProblemDetail problem = ProblemDetail.builder()
                .type(ProblemDetail.ErrorTypes.UNAUTHORIZED)
                .title("Unauthorized")
                .status(401)
                .detail("Authentication required. Please provide valid credentials.")
                .instance(getRequestUri(request))
                .traceId(getTraceId())
                .service(serviceName)
                .errorCode("UNAUTHORIZED")
                .build();
        
        log.warn("Authentication failed: traceId={}", problem.traceId());
        return createResponse(problem);
    }
    
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(
            AccessDeniedException ex, WebRequest request) {
        
        ProblemDetail problem = ProblemDetail.builder()
                .type(ProblemDetail.ErrorTypes.FORBIDDEN)
                .title("Forbidden")
                .status(403)
                .detail("You do not have permission to access this resource.")
                .instance(getRequestUri(request))
                .traceId(getTraceId())
                .service(serviceName)
                .errorCode("FORBIDDEN")
                .build();
        
        log.warn("Access denied: traceId={}", problem.traceId());
        return createResponse(problem);
    }
    
    // --- Not Found (404) ---
    
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoHandler(
            NoHandlerFoundException ex, WebRequest request) {
        
        ProblemDetail problem = ProblemDetail.builder()
                .type(ProblemDetail.ErrorTypes.NOT_FOUND)
                .title("Not Found")
                .status(404)
                .detail(String.format("No handler found for %s %s", 
                        ex.getHttpMethod(), ex.getRequestURL()))
                .instance(getRequestUri(request))
                .traceId(getTraceId())
                .service(serviceName)
                .errorCode("NOT_FOUND")
                .build();
        
        return createResponse(problem);
    }
    
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResource(
            NoResourceFoundException ex, WebRequest request) {
        
        ProblemDetail problem = ProblemDetail.builder()
                .type(ProblemDetail.ErrorTypes.NOT_FOUND)
                .title("Resource Not Found")
                .status(404)
                .detail("The requested resource was not found.")
                .instance(getRequestUri(request))
                .traceId(getTraceId())
                .service(serviceName)
                .errorCode("RESOURCE_NOT_FOUND")
                .build();
        
        return createResponse(problem);
    }
    
    // --- Method Not Allowed (405) ---
    
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, WebRequest request) {
        
        ProblemDetail problem = ProblemDetail.builder()
                .type(ProblemDetail.ErrorTypes.BAD_REQUEST)
                .title("Method Not Allowed")
                .status(405)
                .detail(String.format("Method %s is not supported for this endpoint. Supported: %s",
                        ex.getMethod(), ex.getSupportedHttpMethods()))
                .instance(getRequestUri(request))
                .traceId(getTraceId())
                .service(serviceName)
                .errorCode("METHOD_NOT_ALLOWED")
                .extension("supportedMethods", ex.getSupportedHttpMethods())
                .build();
        
        return createResponse(problem);
    }
    
    // --- Unsupported Media Type (415) ---
    
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex, WebRequest request) {
        
        ProblemDetail problem = ProblemDetail.builder()
                .type(ProblemDetail.ErrorTypes.BAD_REQUEST)
                .title("Unsupported Media Type")
                .status(415)
                .detail(String.format("Content type '%s' is not supported. Supported: %s",
                        ex.getContentType(), ex.getSupportedMediaTypes()))
                .instance(getRequestUri(request))
                .traceId(getTraceId())
                .service(serviceName)
                .errorCode("UNSUPPORTED_MEDIA_TYPE")
                .build();
        
        return createResponse(problem);
    }
    
    // --- Circuit Breaker (503) ---
    
    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ProblemDetail> handleCircuitBreakerOpen(
            CallNotPermittedException ex, WebRequest request) {
        
        String circuitName = ex.getCausingCircuitBreakerName();
        
        ProblemDetail problem = ProblemDetail.builder()
                .type(ProblemDetail.ErrorTypes.CIRCUIT_OPEN)
                .title("Service Circuit Open")
                .status(503)
                .detail(String.format("Service temporarily unavailable due to circuit breaker '%s' being open", 
                        circuitName))
                .instance(getRequestUri(request))
                .traceId(getTraceId())
                .service(serviceName)
                .errorCode("CIRCUIT_OPEN")
                .extension("circuit", circuitName)
                .build();
        
        log.warn("Circuit breaker open: circuit={}, traceId={}", circuitName, problem.traceId());
        
        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", "30");
        return ResponseEntity.status(503)
                .headers(headers)
                .contentType(PROBLEM_JSON)
                .body(problem);
    }
    
    // --- Timeout (504) ---
    
    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ProblemDetail> handleTimeout(
            TimeoutException ex, WebRequest request) {
        
        ProblemDetail problem = ProblemDetail.builder()
                .type(ProblemDetail.ErrorTypes.TIMEOUT)
                .title("Gateway Timeout")
                .status(504)
                .detail("Request timed out while waiting for upstream service.")
                .instance(getRequestUri(request))
                .traceId(getTraceId())
                .service(serviceName)
                .errorCode("TIMEOUT")
                .build();
        
        log.warn("Request timeout: traceId={}", problem.traceId());
        return createResponse(problem);
    }
    
    // --- Generic Server Error (500) ---
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(
            Exception ex, WebRequest request) {
        
        String traceId = getTraceId();
        
        // Log full exception for debugging
        log.error("Unhandled exception: traceId={}, type={}", traceId, ex.getClass().getName(), ex);
        
        ProblemDetail.Builder builder = ProblemDetail.builder()
                .type(ProblemDetail.ErrorTypes.INTERNAL_ERROR)
                .title("Internal Server Error")
                .status(500)
                .detail("An unexpected error occurred. Please contact support with trace ID: " + traceId)
                .instance(getRequestUri(request))
                .traceId(traceId)
                .service(serviceName)
                .errorCode("INTERNAL_ERROR");
        
        if (includeStackTrace) {
            builder.extension("exception", ex.getClass().getName());
            builder.extension("message", ex.getMessage());
        }
        
        return createResponse(builder.build());
    }
    
    // --- Helper Methods ---
    
    private ResponseEntity<ProblemDetail> createResponse(ProblemDetail problem) {
        return ResponseEntity
                .status(problem.status())
                .contentType(PROBLEM_JSON)
                .body(problem);
    }
    
    private String getTraceId() {
        // Try MDC first (set by tracing infrastructure)
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId == null) {
            traceId = MDC.get(CORRELATION_ID_KEY);
        }
        if (traceId == null) {
            traceId = java.util.UUID.randomUUID().toString();
        }
        return traceId;
    }
    
    private String getRequestUri(WebRequest request) {
        String description = request.getDescription(false);
        // WebRequest.getDescription returns "uri=/path" format
        if (description != null && description.startsWith("uri=")) {
            return description.substring(4);
        }
        return description;
    }
}
