package com.z254.butterfly.common.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * API Versioning Filter for BUTTERFLY ecosystem services.
 * 
 * <p>Provides standardized API versioning with:
 * <ul>
 *   <li>Version negotiation via Accept-Version header</li>
 *   <li>Deprecation headers (RFC 8594)</li>
 *   <li>Sunset headers for endpoints being retired</li>
 *   <li>API version tracking and logging</li>
 * </ul>
 * 
 * <h2>Headers</h2>
 * <ul>
 *   <li><strong>X-API-Version</strong>: Current API version in response</li>
 *   <li><strong>Accept-Version</strong>: Requested API version (request)</li>
 *   <li><strong>Deprecation</strong>: RFC 8594 deprecation indicator</li>
 *   <li><strong>Sunset</strong>: RFC 8594 sunset date for deprecated endpoints</li>
 *   <li><strong>Link</strong>: Links to successor endpoints</li>
 * </ul>
 * 
 * <h2>Configuration</h2>
 * <pre>{@code
 * butterfly:
 *   api:
 *     versioning:
 *       enabled: true
 *       current-version: "1.0.0"
 *       supported-versions: ["v1"]
 *       strict: false
 * }</pre>
 * 
 * @see ApiVersionProperties
 * @since 1.0.0
 */
public class ApiVersioningFilter implements Filter {
    
    private static final Logger log = LoggerFactory.getLogger(ApiVersioningFilter.class);
    
    private static final String DEPRECATION_HEADER = "Deprecation";
    private static final String SUNSET_HEADER = "Sunset";
    private static final String LINK_HEADER = "Link";
    private static final String SUPPORTED_VERSIONS_HEADER = "X-Supported-Versions";
    private static final String DEPRECATION_NOTICE_HEADER = "X-Deprecation-Notice";
    
    private static final DateTimeFormatter HTTP_DATE_FORMAT = 
            DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneId.of("GMT"));
    
    private static final Pattern VERSION_IN_PATH = Pattern.compile("/v(\\d+(?:\\.\\d+)?)/");
    
    private final ApiVersionProperties properties;
    private final ObjectMapper objectMapper;
    private final String serviceName;
    
    public ApiVersioningFilter(ApiVersionProperties properties, ObjectMapper objectMapper, String serviceName) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.serviceName = serviceName;
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        if (!properties.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }
        
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        
        String requestUri = req.getRequestURI();
        
        // Skip non-API paths
        if (!requestUri.startsWith("/api/") && !requestUri.startsWith("/v")) {
            chain.doFilter(request, response);
            return;
        }
        
        // Determine requested version
        String requestedVersion = negotiateVersion(req);
        
        // Add version headers to response
        res.setHeader(properties.getResponseVersionHeader(), properties.getCurrentVersion());
        res.setHeader(SUPPORTED_VERSIONS_HEADER, String.join(", ", properties.getSupportedVersions()));
        
        // Check for deprecated endpoints
        addDeprecationHeaders(requestUri, res);
        
        // Validate version if strict mode
        if (properties.isStrict() && requestedVersion != null && !isVersionSupported(requestedVersion)) {
            if (properties.isLogWarnings()) {
                log.warn("Unsupported API version requested: {} for endpoint {}", 
                        requestedVersion, requestUri);
            }
            sendUnsupportedVersionError(res, requestUri, requestedVersion);
            return;
        }
        
        // Log version usage for analytics
        if (requestedVersion != null && properties.isLogWarnings()) {
            log.debug("API request: version={}, endpoint={}, method={}, service={}", 
                    requestedVersion, requestUri, req.getMethod(), serviceName);
        }
        
        chain.doFilter(request, response);
    }
    
    /**
     * Negotiate API version from request headers.
     * Priority: Accept-Version > X-API-Version > URL path > default
     */
    private String negotiateVersion(HttpServletRequest request) {
        // 1. Check Accept-Version header (preferred)
        String acceptVersion = request.getHeader(properties.getVersionHeader());
        if (acceptVersion != null && !acceptVersion.isBlank()) {
            return normalizeVersion(acceptVersion);
        }
        
        // 2. Check X-API-Version header
        String xApiVersion = request.getHeader(properties.getResponseVersionHeader());
        if (xApiVersion != null && !xApiVersion.isBlank()) {
            return normalizeVersion(xApiVersion);
        }
        
        // 3. Extract from URL path
        String uri = request.getRequestURI();
        var matcher = VERSION_IN_PATH.matcher(uri);
        if (matcher.find()) {
            return "v" + matcher.group(1);
        }
        
        // 4. Default to v1
        return "v1";
    }
    
    /**
     * Normalize version string to standard format.
     */
    private String normalizeVersion(String version) {
        if (version == null) return "v1";
        version = version.trim().toLowerCase();
        if (!version.startsWith("v")) {
            version = "v" + version;
        }
        return version;
    }
    
    /**
     * Check if a version is supported.
     */
    private boolean isVersionSupported(String version) {
        Set<String> supported = properties.getSupportedVersions();
        String normalized = normalizeVersion(version);
        return supported.contains(normalized);
    }
    
    /**
     * Add deprecation headers if the endpoint is deprecated.
     */
    private void addDeprecationHeaders(String uri, HttpServletResponse response) {
        Map<String, ApiVersionProperties.DeprecatedEndpoint> deprecated = properties.getDeprecatedEndpoints();
        
        if (deprecated == null || deprecated.isEmpty()) {
            return;
        }
        
        // Check exact match first
        ApiVersionProperties.DeprecatedEndpoint endpoint = deprecated.get(uri);
        
        // Check pattern matches if no exact match
        if (endpoint == null) {
            for (Map.Entry<String, ApiVersionProperties.DeprecatedEndpoint> entry : deprecated.entrySet()) {
                if (matchesPattern(uri, entry.getKey())) {
                    endpoint = entry.getValue();
                    break;
                }
            }
        }
        
        if (endpoint != null) {
            // RFC 8594 Deprecation header
            response.setHeader(DEPRECATION_HEADER, "true");
            
            // Sunset header with date
            if (endpoint.getSunsetDate() != null) {
                response.setHeader(SUNSET_HEADER, endpoint.getSunsetDate());
            }
            
            // Link to successor endpoint
            if (endpoint.getSuccessor() != null) {
                response.setHeader(LINK_HEADER, String.format(
                    "<%s>; rel=\"successor-version\"", endpoint.getSuccessor()));
            }
            
            // Add deprecation warning header
            if (endpoint.getNotice() != null) {
                response.setHeader(DEPRECATION_NOTICE_HEADER, endpoint.getNotice());
            }
            
            if (properties.isLogWarnings()) {
                log.warn("Deprecated endpoint accessed: {} - sunsets {}", uri, endpoint.getSunsetDate());
            }
        }
    }
    
    /**
     * Simple pattern matching for URL paths with wildcards.
     */
    private boolean matchesPattern(String uri, String pattern) {
        if (pattern.contains("{")) {
            // Convert path template to regex
            String regex = pattern.replaceAll("\\{[^}]+}", "[^/]+");
            return uri.matches(regex);
        }
        return uri.equals(pattern);
    }
    
    /**
     * Send unsupported version error response.
     */
    private void sendUnsupportedVersionError(HttpServletResponse response, String uri, String requestedVersion) 
            throws IOException {
        
        ProblemDetail problem = ProblemDetail.builder()
                .type(ProblemDetail.ErrorTypes.UNSUPPORTED_VERSION)
                .title("Unsupported API Version")
                .status(400)
                .detail(String.format("API version '%s' is not supported. Supported versions: %s",
                        requestedVersion, String.join(", ", properties.getSupportedVersions())))
                .instance(URI.create(uri))
                .service(serviceName)
                .errorCode("UNSUPPORTED_VERSION")
                .extension("requestedVersion", requestedVersion)
                .extension("supportedVersions", properties.getSupportedVersions())
                .build();
        
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
