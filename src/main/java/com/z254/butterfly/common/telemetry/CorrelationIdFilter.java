package com.z254.butterfly.common.telemetry;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that extracts or generates correlation IDs and tenant context
 * from HTTP headers and populates the {@link TenantContextHolder}.
 * 
 * <h2>Header Handling</h2>
 * <ul>
 *   <li>{@code X-Correlation-ID} - Business process correlation (generated if missing)</li>
 *   <li>{@code X-Request-ID} - Per-request tracking (generated if missing)</li>
 *   <li>{@code X-Tenant-ID} - Tenant identifier (required for tenant-scoped operations)</li>
 *   <li>{@code X-Route-ID} - Connector route identifier</li>
 *   <li>{@code X-Source-ID} - Data source identifier</li>
 * </ul>
 * 
 * <h2>Response Headers</h2>
 * <p>The filter echoes correlation headers back in the response for client-side
 * correlation and debugging.
 * 
 * <h2>Usage</h2>
 * <p>Register as a Spring bean or servlet filter:
 * <pre>{@code
 * @Bean
 * public FilterRegistrationBean<CorrelationIdFilter> correlationFilter() {
 *     FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>();
 *     registration.setFilter(new CorrelationIdFilter());
 *     registration.addUrlPatterns("/*");
 *     registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
 *     return registration;
 * }
 * }</pre>
 * 
 * @see TenantContextHolder
 * @see TelemetryTagNames
 */
public class CorrelationIdFilter implements Filter {

    /**
     * Header name for correlation ID.
     */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    /**
     * Header name for request ID.
     */
    public static final String REQUEST_ID_HEADER = "X-Request-ID";

    /**
     * Header name for tenant ID.
     */
    public static final String TENANT_ID_HEADER = "X-Tenant-ID";

    /**
     * Header name for route ID.
     */
    public static final String ROUTE_ID_HEADER = "X-Route-ID";

    /**
     * Header name for source ID.
     */
    public static final String SOURCE_ID_HEADER = "X-Source-ID";

    /**
     * W3C Trace Context header for distributed tracing.
     */
    public static final String TRACEPARENT_HEADER = "traceparent";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // No initialization required
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        if (request instanceof HttpServletRequest httpRequest && 
            response instanceof HttpServletResponse httpResponse) {
            
            try {
                // Extract or generate correlation ID
                String correlationId = extractOrGenerate(httpRequest, CORRELATION_ID_HEADER);
                TenantContextHolder.setCorrelationId(correlationId);

                // Extract or generate request ID
                String requestId = extractOrGenerate(httpRequest, REQUEST_ID_HEADER);
                TenantContextHolder.setRequestId(requestId);

                // Extract tenant ID (may be null for unauthenticated endpoints)
                String tenantId = httpRequest.getHeader(TENANT_ID_HEADER);
                if (tenantId != null && !tenantId.isBlank()) {
                    TenantContextHolder.setTenantId(tenantId);
                }

                // Extract optional route context
                String routeId = httpRequest.getHeader(ROUTE_ID_HEADER);
                if (routeId != null && !routeId.isBlank()) {
                    TenantContextHolder.setRouteId(routeId);
                }

                String sourceId = httpRequest.getHeader(SOURCE_ID_HEADER);
                if (sourceId != null && !sourceId.isBlank()) {
                    TenantContextHolder.setSourceId(sourceId);
                }

                // Echo correlation headers in response
                httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);
                httpResponse.setHeader(REQUEST_ID_HEADER, requestId);

                chain.doFilter(request, response);
                
            } finally {
                // Always clear context after request processing
                TenantContextHolder.clear();
            }
        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {
        // No cleanup required
    }

    /**
     * Extracts a header value or generates a new UUID if not present.
     *
     * @param request the HTTP request
     * @param headerName the header to extract
     * @return the header value or a generated UUID
     */
    private String extractOrGenerate(HttpServletRequest request, String headerName) {
        String value = request.getHeader(headerName);
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return value;
    }
}

