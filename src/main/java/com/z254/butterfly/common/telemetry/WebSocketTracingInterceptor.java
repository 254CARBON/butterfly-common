package com.z254.butterfly.common.telemetry;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.UUID;

/**
 * WebSocket handshake interceptor that propagates trace context.
 * 
 * <p>This interceptor extracts trace context from the WebSocket upgrade request
 * and stores it in the session attributes for use during message handling.
 * 
 * <h2>Propagated Context</h2>
 * <ul>
 *   <li>traceparent - W3C Trace Context header</li>
 *   <li>X-Correlation-ID - Business correlation identifier</li>
 *   <li>X-Tenant-ID - Tenant identifier</li>
 *   <li>X-Request-ID - Request identifier (generated if missing)</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * <pre>{@code
 * @Configuration
 * public class WebSocketConfig implements WebSocketConfigurer {
 *     @Override
 *     public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
 *         registry.addHandler(myHandler(), "/ws/endpoint")
 *             .addInterceptors(new WebSocketTracingInterceptor());
 *     }
 * }
 * }</pre>
 * 
 * <h2>Message Handling</h2>
 * <p>In your WebSocket handler, restore the context before processing:
 * <pre>{@code
 * @Override
 * public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
 *     // Restore trace context from session
 *     String traceId = (String) session.getAttributes().get("traceId");
 *     String tenantId = (String) session.getAttributes().get("tenantId");
 *     
 *     TenantContextHolder.setTenantId(tenantId);
 *     MDC.put("traceId", traceId);
 *     
 *     try {
 *         processMessage(message);
 *     } finally {
 *         TenantContextHolder.clear();
 *     }
 * }
 * }</pre>
 */
public class WebSocketTracingInterceptor implements HandshakeInterceptor {

    public static final String ATTR_TRACE_ID = "traceId";
    public static final String ATTR_SPAN_ID = "spanId";
    public static final String ATTR_TENANT_ID = "tenantId";
    public static final String ATTR_CORRELATION_ID = "correlationId";
    public static final String ATTR_REQUEST_ID = "requestId";

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) throws Exception {
        
        // Extract trace context from W3C traceparent header
        String traceparent = getHeader(request, "traceparent");
        if (traceparent != null && !traceparent.isBlank()) {
            String[] parts = traceparent.split("-");
            if (parts.length >= 3) {
                attributes.put(ATTR_TRACE_ID, parts[1]);
                attributes.put(ATTR_SPAN_ID, parts[2]);
            }
        }
        
        // Extract correlation ID
        String correlationId = getHeader(request, CorrelationIdFilter.CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        attributes.put(ATTR_CORRELATION_ID, correlationId);
        
        // Extract request ID
        String requestId = getHeader(request, CorrelationIdFilter.REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        attributes.put(ATTR_REQUEST_ID, requestId);
        
        // Extract tenant ID
        String tenantId = getHeader(request, CorrelationIdFilter.TENANT_ID_HEADER);
        if (tenantId != null && !tenantId.isBlank()) {
            attributes.put(ATTR_TENANT_ID, tenantId);
        }
        
        // Set response headers for client correlation
        response.getHeaders().add(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId);
        response.getHeaders().add(CorrelationIdFilter.REQUEST_ID_HEADER, requestId);
        
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // Nothing to do after handshake
    }

    private String getHeader(ServerHttpRequest request, String headerName) {
        var values = request.getHeaders().get(headerName);
        if (values != null && !values.isEmpty()) {
            return values.get(0);
        }
        return null;
    }
}

