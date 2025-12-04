package com.z254.butterfly.common.tenant;

import com.z254.butterfly.common.security.JwtClaimsExtractor;
import com.z254.butterfly.common.telemetry.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Reactive WebFilter for tenant context propagation in WebFlux applications.
 * <p>
 * This filter extracts tenant context from HTTP requests and propagates it
 * through the reactive chain using Reactor Context.
 *
 * <h2>Extraction Order</h2>
 * <ol>
 *   <li>X-Tenant-ID header (or configured header name)</li>
 *   <li>tenantId query parameter</li>
 *   <li>JWT token claims</li>
 *   <li>Default tenant ID</li>
 * </ol>
 *
 * @see TenantReactiveContextPropagator
 * @see TenantFilter for servlet-based applications
 */
@Order(10)
public class TenantWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantWebFilter.class);

    private final TenantProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public TenantWebFilter(TenantProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // Skip excluded paths
        if (isExcludedPath(path)) {
            return chain.filter(exchange);
        }

        // Skip if disabled
        if (!properties.isEnabled()) {
            return chain.filter(exchange)
                    .contextWrite(TenantReactiveContextPropagator.withTenant(properties.getDefaultTenantId()));
        }

        // Extract tenant ID
        String tenantId = extractTenantId(request);

        if (tenantId != null && !tenantId.isBlank()) {
            log.debug("Extracted tenant ID: {} for path: {}", tenantId, path);
            return chain.filter(exchange)
                    .contextWrite(TenantReactiveContextPropagator.withFullContext(
                            tenantId,
                            extractCorrelationId(request),
                            extractRequestId(request)));

        } else if (properties.isRequireTenantContext()) {
            log.warn("Request without required tenant context: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
            return exchange.getResponse().setComplete();

        } else {
            log.debug("Using default tenant for path: {}", path);
            return chain.filter(exchange)
                    .contextWrite(TenantReactiveContextPropagator.withFullContext(
                            properties.getDefaultTenantId(),
                            extractCorrelationId(request),
                            extractRequestId(request)));
        }
    }

    /**
     * Extract tenant ID from request.
     */
    protected String extractTenantId(ServerHttpRequest request) {
        // 1. Check header
        String tenantId = request.getHeaders().getFirst(properties.getHeaderName());
        if (tenantId != null && !tenantId.isBlank()) {
            return tenantId.trim();
        }

        // 2. Check query parameter
        tenantId = request.getQueryParams().getFirst(properties.getQueryParamName());
        if (tenantId != null && !tenantId.isBlank()) {
            return tenantId.trim();
        }

        // 3. Extract from JWT
        tenantId = extractTenantFromJwt(request);
        if (tenantId != null && !tenantId.isBlank()) {
            return tenantId.trim();
        }

        return null;
    }

    /**
     * Extract tenant ID from JWT token.
     */
    protected String extractTenantFromJwt(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Map<String, Object> claims = parseJwtClaims(token);
                return JwtClaimsExtractor.extractTenant(claims);
            } catch (Exception e) {
                log.trace("Could not extract tenant from JWT: {}", e.getMessage());
            }
        }
        return null;
    }

    /**
     * Parse JWT claims (lightweight, no signature verification).
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> parseJwtClaims(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
                com.fasterxml.jackson.databind.ObjectMapper mapper =
                        new com.fasterxml.jackson.databind.ObjectMapper();
                return mapper.readValue(payload, Map.class);
            }
        } catch (Exception e) {
            log.trace("Failed to parse JWT: {}", e.getMessage());
        }
        return Map.of();
    }

    /**
     * Extract correlation ID from request headers.
     */
    protected String extractCorrelationId(ServerHttpRequest request) {
        String correlationId = request.getHeaders().getFirst("X-Correlation-ID");
        if (correlationId == null) {
            correlationId = request.getHeaders().getFirst("X-Request-ID");
        }
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        return correlationId;
    }

    /**
     * Extract request ID from request headers.
     */
    protected String extractRequestId(ServerHttpRequest request) {
        String requestId = request.getHeaders().getFirst("X-Request-ID");
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        return requestId;
    }

    /**
     * Check if path is excluded from tenant filtering.
     */
    protected boolean isExcludedPath(String path) {
        for (String pattern : properties.getExcludedPathPatterns()) {
            if (pathMatcher.match(pattern.trim(), path)) {
                return true;
            }
        }
        return false;
    }
}

