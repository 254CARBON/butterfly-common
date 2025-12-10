package com.z254.butterfly.common.tracing;

import com.z254.butterfly.common.identity.RimNodeId;
import com.z254.butterfly.common.kafka.UnifiedEventHeader;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * Servlet filter that validates and extracts {@link UnifiedEventHeader} from HTTP requests.
 * 
 * <p>This filter enforces the identity and traceability discipline across the BUTTERFLY
 * ecosystem by requiring specific headers and populating the {@link UnifiedEventContext}.
 * 
 * <h2>Validation Modes</h2>
 * <ul>
 *   <li>{@code STRICT} - Reject requests missing required headers (for production)</li>
 *   <li>{@code WARN} - Log warnings but allow requests through (for migration)</li>
 *   <li>{@code DISABLED} - Skip validation entirely (for development)</li>
 * </ul>
 * 
 * <h2>HTTP Headers</h2>
 * <ul>
 *   <li>{@code X-Correlation-ID} - Correlation ID (required in STRICT mode)</li>
 *   <li>{@code X-Causation-ID} - ID of the causing event</li>
 *   <li>{@code X-Origin-System} - Originating system (required in STRICT mode)</li>
 *   <li>{@code X-Source-Service} - Source service identifier</li>
 *   <li>{@code X-Primary-Rim-Node} - Primary RIM entity</li>
 *   <li>{@code X-Decision-Episode-ID} - Decision episode reference</li>
 *   <li>{@code traceparent} - W3C Trace Context</li>
 * </ul>
 * 
 * <h2>Configuration</h2>
 * <pre>{@code
 * @Bean
 * public FilterRegistrationBean<UnifiedHeaderValidationFilter> unifiedHeaderFilter() {
 *     FilterRegistrationBean<UnifiedHeaderValidationFilter> registration = new FilterRegistrationBean<>();
 *     registration.setFilter(new UnifiedHeaderValidationFilter(ValidationMode.STRICT, originSystem));
 *     registration.addUrlPatterns("/api/*");
 *     registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
 *     return registration;
 * }
 * }</pre>
 * 
 * @see UnifiedEventHeader
 * @see UnifiedEventContext
 * @since 2.0.0
 */
public class UnifiedHeaderValidationFilter implements Filter {
    
    private static final Logger log = LoggerFactory.getLogger(UnifiedHeaderValidationFilter.class);
    
    /**
     * HTTP header names.
     */
    public static final String HEADER_CORRELATION_ID = "X-Correlation-ID";
    public static final String HEADER_CAUSATION_ID = "X-Causation-ID";
    public static final String HEADER_ORIGIN_SYSTEM = "X-Origin-System";
    public static final String HEADER_SOURCE_SERVICE = "X-Source-Service";
    public static final String HEADER_PRIMARY_RIM_NODE = "X-Primary-Rim-Node";
    public static final String HEADER_AFFECTED_RIM_NODES = "X-Affected-Rim-Nodes";
    public static final String HEADER_DECISION_EPISODE_ID = "X-Decision-Episode-ID";
    public static final String HEADER_EVENT_ID = "X-Event-ID";
    public static final String HEADER_TRACEPARENT = "traceparent";
    public static final String HEADER_TRACESTATE = "tracestate";
    
    /**
     * Validation modes.
     */
    public enum ValidationMode {
        /**
         * Reject requests missing required headers.
         */
        STRICT,
        
        /**
         * Log warnings but allow requests through.
         */
        WARN,
        
        /**
         * Skip validation entirely.
         */
        DISABLED
    }
    
    private final ValidationMode validationMode;
    private final UnifiedEventHeader.OriginSystem defaultOriginSystem;
    private final Set<String> requiredHeaders;
    private final boolean validateRimNode;
    private final Set<String> excludedPaths;
    
    /**
     * Create a filter with default settings.
     * 
     * @param validationMode the validation mode
     * @param defaultOriginSystem the origin system for this service
     */
    public UnifiedHeaderValidationFilter(
            ValidationMode validationMode, 
            UnifiedEventHeader.OriginSystem defaultOriginSystem) {
        this(validationMode, defaultOriginSystem, 
            Set.of(HEADER_CORRELATION_ID, HEADER_ORIGIN_SYSTEM), 
            false,
            Set.of("/actuator", "/health", "/swagger", "/v3/api-docs"));
    }
    
    /**
     * Create a filter with custom settings.
     * 
     * @param validationMode the validation mode
     * @param defaultOriginSystem the origin system for this service
     * @param requiredHeaders set of header names that must be present in STRICT mode
     * @param validateRimNode whether to require primary RIM node
     * @param excludedPaths paths to exclude from validation
     */
    public UnifiedHeaderValidationFilter(
            ValidationMode validationMode,
            UnifiedEventHeader.OriginSystem defaultOriginSystem,
            Set<String> requiredHeaders,
            boolean validateRimNode,
            Set<String> excludedPaths) {
        this.validationMode = validationMode;
        this.defaultOriginSystem = defaultOriginSystem;
        this.requiredHeaders = requiredHeaders != null ? Set.copyOf(requiredHeaders) : Set.of();
        this.validateRimNode = validateRimNode;
        this.excludedPaths = excludedPaths != null ? Set.copyOf(excludedPaths) : Set.of();
    }
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("UnifiedHeaderValidationFilter initialized: mode={}, originSystem={}, requiredHeaders={}, validateRimNode={}",
            validationMode, defaultOriginSystem, requiredHeaders, validateRimNode);
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        if (!(request instanceof HttpServletRequest httpRequest) ||
            !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }
        
        // Skip excluded paths
        String path = httpRequest.getRequestURI();
        if (isExcludedPath(path)) {
            chain.doFilter(request, response);
            return;
        }
        
        // Skip if validation is disabled
        if (validationMode == ValidationMode.DISABLED) {
            // Still try to extract headers if present for context propagation
            tryExtractAndSetContext(httpRequest);
            try {
                chain.doFilter(request, response);
            } finally {
                UnifiedEventContext.clear();
            }
            return;
        }
        
        try {
            // Validate and extract headers
            ValidationResult result = validateAndExtract(httpRequest);
            
            if (!result.valid && validationMode == ValidationMode.STRICT) {
                log.warn("Request rejected due to missing required headers: {} path={}",
                    result.missingHeaders, path);
                httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                httpResponse.setContentType("application/problem+json");
                httpResponse.getWriter().write(buildProblemDetail(result));
                return;
            }
            
            if (!result.valid && validationMode == ValidationMode.WARN) {
                log.warn("Missing recommended headers: {} path={}", result.missingHeaders, path);
            }
            
            // Set context and echo headers in response
            UnifiedEventContext.set(result.header);
            echoHeadersInResponse(httpResponse, result.header);
            
            chain.doFilter(request, response);
            
        } finally {
            UnifiedEventContext.clear();
        }
    }
    
    @Override
    public void destroy() {
        // No cleanup required
    }
    
    private boolean isExcludedPath(String path) {
        return excludedPaths.stream().anyMatch(path::startsWith);
    }
    
    private void tryExtractAndSetContext(HttpServletRequest request) {
        try {
            ValidationResult result = validateAndExtract(request);
            if (result.header != null) {
                UnifiedEventContext.set(result.header);
            }
        } catch (Exception e) {
            log.debug("Failed to extract headers in disabled mode: {}", e.getMessage());
        }
    }
    
    private ValidationResult validateAndExtract(HttpServletRequest request) {
        List<String> missingHeaders = new ArrayList<>();
        
        // Extract correlation ID (generate if missing)
        String correlationId = request.getHeader(HEADER_CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            if (requiredHeaders.contains(HEADER_CORRELATION_ID)) {
                missingHeaders.add(HEADER_CORRELATION_ID);
            }
            correlationId = UUID.randomUUID().toString();
        }
        
        // Extract origin system
        String originSystemStr = request.getHeader(HEADER_ORIGIN_SYSTEM);
        UnifiedEventHeader.OriginSystem originSystem = defaultOriginSystem;
        if (originSystemStr != null && !originSystemStr.isBlank()) {
            try {
                originSystem = UnifiedEventHeader.OriginSystem.valueOf(originSystemStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid origin system header value: {}", originSystemStr);
            }
        } else if (requiredHeaders.contains(HEADER_ORIGIN_SYSTEM)) {
            missingHeaders.add(HEADER_ORIGIN_SYSTEM);
        }
        
        // Extract primary RIM node
        RimNodeId primaryRimNode = null;
        String primaryRimNodeStr = request.getHeader(HEADER_PRIMARY_RIM_NODE);
        if (primaryRimNodeStr != null && !primaryRimNodeStr.isBlank()) {
            try {
                primaryRimNode = RimNodeId.parse(primaryRimNodeStr);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid primary RIM node header value: {}", primaryRimNodeStr);
            }
        } else if (validateRimNode) {
            missingHeaders.add(HEADER_PRIMARY_RIM_NODE);
        }
        
        // Extract affected RIM nodes
        List<RimNodeId> affectedRimNodes = new ArrayList<>();
        String affectedRimNodesStr = request.getHeader(HEADER_AFFECTED_RIM_NODES);
        if (affectedRimNodesStr != null && !affectedRimNodesStr.isBlank()) {
            for (String nodeStr : affectedRimNodesStr.split(",")) {
                try {
                    affectedRimNodes.add(RimNodeId.parse(nodeStr.trim()));
                } catch (IllegalArgumentException e) {
                    log.debug("Skipping invalid affected RIM node: {}", nodeStr);
                }
            }
        }
        
        // Extract trace context from W3C traceparent header
        String traceId = null;
        String spanId = null;
        String traceFlags = null;
        String traceparent = request.getHeader(HEADER_TRACEPARENT);
        if (traceparent != null && !traceparent.isBlank()) {
            TraceContextParts parts = parseTraceparent(traceparent);
            if (parts != null) {
                traceId = parts.traceId;
                spanId = parts.spanId;
                traceFlags = parts.traceFlags;
            }
        }
        
        // Build the header
        UnifiedEventHeader header = UnifiedEventHeader.builder()
            .eventId(request.getHeader(HEADER_EVENT_ID))
            .correlationId(correlationId)
            .causationId(request.getHeader(HEADER_CAUSATION_ID))
            .originSystem(originSystem)
            .sourceService(request.getHeader(HEADER_SOURCE_SERVICE))
            .traceId(traceId)
            .spanId(spanId)
            .traceFlags(traceFlags)
            .traceState(request.getHeader(HEADER_TRACESTATE))
            .primaryRimNode(primaryRimNode)
            .affectedRimNodes(affectedRimNodes)
            .decisionEpisodeId(request.getHeader(HEADER_DECISION_EPISODE_ID))
            .build();
        
        return new ValidationResult(missingHeaders.isEmpty(), missingHeaders, header);
    }
    
    private void echoHeadersInResponse(HttpServletResponse response, UnifiedEventHeader header) {
        response.setHeader(HEADER_CORRELATION_ID, header.correlationId());
        response.setHeader(HEADER_EVENT_ID, header.eventId());
        
        if (header.decisionEpisodeId() != null) {
            response.setHeader(HEADER_DECISION_EPISODE_ID, header.decisionEpisodeId());
        }
    }
    
    private String buildProblemDetail(ValidationResult result) {
        return String.format("""
            {
                "type": "https://api.butterfly.254studioz.com/errors/missing-headers",
                "title": "Missing Required Headers",
                "status": 400,
                "detail": "Request is missing required headers for traceability: %s",
                "instance": null,
                "missingHeaders": %s
            }
            """, 
            String.join(", ", result.missingHeaders),
            result.missingHeaders.stream()
                .map(h -> "\"" + h + "\"")
                .toList());
    }
    
    /**
     * Parse W3C traceparent header.
     * Format: {version}-{trace-id}-{parent-id}-{trace-flags}
     */
    private TraceContextParts parseTraceparent(String traceparent) {
        try {
            String[] parts = traceparent.split("-");
            if (parts.length >= 4) {
                return new TraceContextParts(parts[1], parts[2], parts[3]);
            }
        } catch (Exception e) {
            log.debug("Failed to parse traceparent: {}", traceparent);
        }
        return null;
    }
    
    private record TraceContextParts(String traceId, String spanId, String traceFlags) {}
    
    private record ValidationResult(boolean valid, List<String> missingHeaders, UnifiedEventHeader header) {}
    
    /**
     * Builder for creating UnifiedHeaderValidationFilter with custom configuration.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private ValidationMode validationMode = ValidationMode.WARN;
        private UnifiedEventHeader.OriginSystem defaultOriginSystem;
        private Set<String> requiredHeaders = new HashSet<>(Set.of(HEADER_CORRELATION_ID));
        private boolean validateRimNode = false;
        private Set<String> excludedPaths = new HashSet<>(
            Set.of("/actuator", "/health", "/swagger", "/v3/api-docs"));
        
        public Builder validationMode(ValidationMode mode) {
            this.validationMode = mode;
            return this;
        }
        
        public Builder defaultOriginSystem(UnifiedEventHeader.OriginSystem system) {
            this.defaultOriginSystem = system;
            return this;
        }
        
        public Builder requiredHeaders(String... headers) {
            this.requiredHeaders = new HashSet<>(Arrays.asList(headers));
            return this;
        }
        
        public Builder addRequiredHeader(String header) {
            this.requiredHeaders.add(header);
            return this;
        }
        
        public Builder validateRimNode(boolean validate) {
            this.validateRimNode = validate;
            return this;
        }
        
        public Builder excludedPaths(String... paths) {
            this.excludedPaths = new HashSet<>(Arrays.asList(paths));
            return this;
        }
        
        public Builder addExcludedPath(String path) {
            this.excludedPaths.add(path);
            return this;
        }
        
        public UnifiedHeaderValidationFilter build() {
            Objects.requireNonNull(defaultOriginSystem, "defaultOriginSystem must be set");
            return new UnifiedHeaderValidationFilter(
                validationMode, defaultOriginSystem, requiredHeaders, validateRimNode, excludedPaths);
        }
    }
}
