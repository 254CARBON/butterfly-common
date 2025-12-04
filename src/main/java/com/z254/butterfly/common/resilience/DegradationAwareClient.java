package com.z254.butterfly.common.resilience;

import com.z254.butterfly.common.telemetry.TelemetryTagNames;
import com.z254.butterfly.common.telemetry.TenantContextHolder;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A WebClient wrapper that is aware of downstream service degradation states.
 * Provides intelligent request routing, fallback behavior, and resilience patterns
 * based on real-time service health.
 * 
 * <p>Features:
 * <ul>
 *   <li>Automatic circuit breaking via Resilience4j</li>
 *   <li>Retry with exponential backoff</li>
 *   <li>Health-based request blocking</li>
 *   <li>Fallback execution for degraded services</li>
 *   <li>Tenant context propagation</li>
 *   <li>Metrics and observability</li>
 * </ul>
 */
@Slf4j
public class DegradationAwareClient {
    
    private final String serviceId;
    private final WebClient webClient;
    private final HealthStateManager healthStateManager;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final MeterRegistry meterRegistry;
    private final DegradationClientProperties properties;
    private final Map<String, Function<Throwable, ?>> fallbackHandlers = new ConcurrentHashMap<>();
    
    /**
     * Builder for creating DegradationAwareClient instances.
     */
    public static Builder builder(String serviceId) {
        return new Builder(serviceId);
    }
    
    private DegradationAwareClient(Builder builder) {
        this.serviceId = builder.serviceId;
        this.healthStateManager = builder.healthStateManager;
        this.meterRegistry = builder.meterRegistry;
        this.properties = builder.properties;
        
        // Configure circuit breaker
        this.circuitBreaker = builder.circuitBreakerRegistry.circuitBreaker(serviceId);
        this.retry = builder.retryRegistry.retry(serviceId);
        
        // Build WebClient with filters
        this.webClient = builder.webClientBuilder
                .filter(tenantContextFilter())
                .filter(metricsFilter())
                .filter(healthRecordingFilter())
                .build();
        
        log.info("DegradationAwareClient initialized for service: {}", serviceId);
    }
    
    /**
     * Execute a GET request with degradation awareness.
     */
    public <T> Mono<T> get(String path, Class<T> responseType) {
        return execute(HttpMethod.GET, path, null, responseType);
    }
    
    /**
     * Execute a POST request with degradation awareness.
     */
    public <T, B> Mono<T> post(String path, B body, Class<T> responseType) {
        return execute(HttpMethod.POST, path, body, responseType);
    }
    
    /**
     * Execute a PUT request with degradation awareness.
     */
    public <T, B> Mono<T> put(String path, B body, Class<T> responseType) {
        return execute(HttpMethod.PUT, path, body, responseType);
    }
    
    /**
     * Execute a DELETE request with degradation awareness.
     */
    public <T> Mono<T> delete(String path, Class<T> responseType) {
        return execute(HttpMethod.DELETE, path, null, responseType);
    }
    
    /**
     * Execute a request with full degradation awareness.
     */
    public <T, B> Mono<T> execute(HttpMethod method, String path, B body, Class<T> responseType) {
        // Check service health first
        ServiceHealth health = healthStateManager.getServiceHealth(serviceId);
        
        if (health.shouldBlockRequests()) {
            log.warn("Blocking request to {} - service unavailable: {}", serviceId, health.getMessage());
            meterRegistry.counter("butterfly.client.blocked",
                    List.of(Tag.of("service", serviceId), Tag.of("reason", "unavailable")))
                    .increment();
            return executeWithFallback(method, path, responseType,
                    new ServiceUnavailableException(serviceId, health.getMessage()));
        }
        
        if (health.shouldOnlyAllowCritical() && !isCriticalRequest(path)) {
            log.info("Blocking non-critical request to {} - service impaired", serviceId);
            meterRegistry.counter("butterfly.client.blocked",
                    List.of(Tag.of("service", serviceId), Tag.of("reason", "impaired_non_critical")))
                    .increment();
            return executeWithFallback(method, path, responseType,
                    new ServiceDegradedException(serviceId, "Service impaired - only critical requests allowed"));
        }
        
        // Build and execute request with resilience patterns
        return buildRequest(method, path, body)
                .retrieve()
                .bodyToMono(responseType)
                .transform(mono -> applyResilience(mono, path, responseType))
                .doOnSuccess(result -> healthStateManager.recordSuccess(serviceId, 0))
                .doOnError(error -> healthStateManager.recordFailure(serviceId, error));
    }
    
    /**
     * Execute a request as critical (bypasses some health checks).
     */
    public <T, B> Mono<T> executeCritical(HttpMethod method, String path, B body, Class<T> responseType) {
        ServiceHealth health = healthStateManager.getServiceHealth(serviceId);
        
        // Even critical requests are blocked if completely unavailable
        if (health.getState() == DegradationState.UNAVAILABLE) {
            return executeWithFallback(method, path, responseType,
                    new ServiceUnavailableException(serviceId, health.getMessage()));
        }
        
        return buildRequest(method, path, body)
                .retrieve()
                .bodyToMono(responseType)
                .transform(mono -> applyResilience(mono, path, responseType))
                .doOnSuccess(result -> healthStateManager.recordSuccess(serviceId, 0))
                .doOnError(error -> healthStateManager.recordFailure(serviceId, error));
    }
    
    /**
     * Register a fallback handler for a specific endpoint pattern.
     */
    public <T> DegradationAwareClient withFallback(String pathPattern, Function<Throwable, T> fallback) {
        fallbackHandlers.put(pathPattern, fallback);
        return this;
    }
    
    /**
     * Register a default fallback handler.
     */
    public <T> DegradationAwareClient withDefaultFallback(Function<Throwable, T> fallback) {
        fallbackHandlers.put("*", fallback);
        return this;
    }
    
    /**
     * Get the current health of the target service.
     */
    public ServiceHealth getServiceHealth() {
        return healthStateManager.getServiceHealth(serviceId);
    }
    
    /**
     * Check if the service is operational.
     */
    public boolean isOperational() {
        return getServiceHealth().isOperational();
    }
    
    // === Internal Methods ===
    
    private WebClient.RequestBodySpec buildRequest(HttpMethod method, String path, Object body) {
        WebClient.RequestBodySpec spec = webClient
                .method(method)
                .uri(path)
                .header(HttpHeaders.ACCEPT, "application/json");
        
        if (body != null) {
            spec.bodyValue(body);
        }
        
        return spec;
    }
    
    @SuppressWarnings("unchecked")
    private <T> Mono<T> applyResilience(Mono<T> mono, String path, Class<T> responseType) {
        return mono
                // Apply retry
                .transformDeferred(Retry.decorateFunction(retry, Function.identity()))
                // Apply circuit breaker
                .transformDeferred(CircuitBreaker.decorateFunction(circuitBreaker, Function.identity()))
                // Timeout
                .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                // Handle errors with fallback
                .onErrorResume(error -> executeWithFallback(HttpMethod.GET, path, responseType, error));
    }
    
    @SuppressWarnings("unchecked")
    private <T> Mono<T> executeWithFallback(HttpMethod method, String path, Class<T> responseType, Throwable error) {
        // Try to find a matching fallback handler
        Function<Throwable, ?> handler = findFallbackHandler(path);
        
        if (handler != null) {
            try {
                T result = (T) handler.apply(error);
                log.debug("Executed fallback for {} {} -> {}", method, path, result);
                meterRegistry.counter("butterfly.client.fallback",
                        List.of(Tag.of("service", serviceId), Tag.of("path", path)))
                        .increment();
                return Mono.justOrEmpty(result);
            } catch (Exception e) {
                log.warn("Fallback handler failed for {} {}: {}", method, path, e.getMessage());
            }
        }
        
        return Mono.error(error);
    }
    
    private Function<Throwable, ?> findFallbackHandler(String path) {
        // Try exact match first
        if (fallbackHandlers.containsKey(path)) {
            return fallbackHandlers.get(path);
        }
        
        // Try pattern matching
        for (Map.Entry<String, Function<Throwable, ?>> entry : fallbackHandlers.entrySet()) {
            if (pathMatches(path, entry.getKey())) {
                return entry.getValue();
            }
        }
        
        // Fall back to default
        return fallbackHandlers.get("*");
    }
    
    private boolean pathMatches(String path, String pattern) {
        if ("*".equals(pattern)) {
            return true;
        }
        // Simple pattern matching - can be enhanced with AntPathMatcher
        return path.startsWith(pattern.replace("*", ""));
    }
    
    private boolean isCriticalRequest(String path) {
        // Critical paths that should be allowed even when service is impaired
        return path.contains("/health") ||
               path.contains("/critical") ||
               path.contains("/emergency");
    }
    
    // === Exchange Filters ===
    
    private ExchangeFilterFunction tenantContextFilter() {
        return (request, next) -> Mono.deferContextual(ctx -> {
            ClientRequest.Builder builder = ClientRequest.from(request);
            
            // Add tenant context
            Optional<String> tenantId = ctx.getOrEmpty(TelemetryTagNames.TENANT_ID)
                    .map(Object::toString)
                    .or(TenantContextHolder::getTenantId);
            
            tenantId.ifPresent(id -> builder.header(TelemetryTagNames.TENANT_ID, id));
            
            return next.exchange(builder.build());
        });
    }
    
    private ExchangeFilterFunction metricsFilter() {
        return (request, next) -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            
            return next.exchange(request)
                    .doOnTerminate(() -> {
                        sample.stop(Timer.builder("butterfly.client.requests")
                                .tag("service", serviceId)
                                .tag("method", request.method().name())
                                .tag("uri", request.url().getPath())
                                .register(meterRegistry));
                    });
        };
    }
    
    private ExchangeFilterFunction healthRecordingFilter() {
        return (request, next) -> next.exchange(request)
                .doOnNext(response -> {
                    if (response.statusCode().isError()) {
                        healthStateManager.recordFailure(serviceId,
                                new RuntimeException("HTTP " + response.statusCode().value()));
                    } else {
                        healthStateManager.recordSuccess(serviceId, 0);
                    }
                });
    }
    
    // === Builder ===
    
    public static class Builder {
        private final String serviceId;
        private WebClient.Builder webClientBuilder;
        private HealthStateManager healthStateManager;
        private CircuitBreakerRegistry circuitBreakerRegistry;
        private RetryRegistry retryRegistry;
        private MeterRegistry meterRegistry;
        private DegradationClientProperties properties = new DegradationClientProperties();
        
        private Builder(String serviceId) {
            this.serviceId = serviceId;
        }
        
        public Builder webClientBuilder(WebClient.Builder builder) {
            this.webClientBuilder = builder;
            return this;
        }
        
        public Builder healthStateManager(HealthStateManager manager) {
            this.healthStateManager = manager;
            return this;
        }
        
        public Builder circuitBreakerRegistry(CircuitBreakerRegistry registry) {
            this.circuitBreakerRegistry = registry;
            return this;
        }
        
        public Builder retryRegistry(RetryRegistry registry) {
            this.retryRegistry = registry;
            return this;
        }
        
        public Builder meterRegistry(MeterRegistry registry) {
            this.meterRegistry = registry;
            return this;
        }
        
        public Builder properties(DegradationClientProperties props) {
            this.properties = props;
            return this;
        }
        
        public DegradationAwareClient build() {
            if (webClientBuilder == null) {
                throw new IllegalStateException("WebClient.Builder is required");
            }
            if (healthStateManager == null) {
                throw new IllegalStateException("HealthStateManager is required");
            }
            return new DegradationAwareClient(this);
        }
    }
    
    // === Exceptions ===
    
    public static class ServiceUnavailableException extends RuntimeException {
        private final String serviceId;
        
        public ServiceUnavailableException(String serviceId, String message) {
            super(String.format("Service %s is unavailable: %s", serviceId, message));
            this.serviceId = serviceId;
        }
        
        public String getServiceId() {
            return serviceId;
        }
    }
    
    public static class ServiceDegradedException extends RuntimeException {
        private final String serviceId;
        
        public ServiceDegradedException(String serviceId, String message) {
            super(String.format("Service %s is degraded: %s", serviceId, message));
            this.serviceId = serviceId;
        }
        
        public String getServiceId() {
            return serviceId;
        }
    }
}

