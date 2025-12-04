package com.z254.butterfly.common.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

/**
 * Factory for creating and managing DegradationAwareClient instances.
 * Ensures consistent configuration and shared registries across clients.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DegradationClientFactory {
    
    private final HealthStateManager healthStateManager;
    private final MeterRegistry meterRegistry;
    
    private final Map<String, DegradationAwareClient> clientCache = new ConcurrentHashMap<>();
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    
    public DegradationClientFactory(
            HealthStateManager healthStateManager,
            MeterRegistry meterRegistry) {
        this.healthStateManager = healthStateManager;
        this.meterRegistry = meterRegistry;
        
        // Initialize default registries
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(defaultCircuitBreakerConfig());
        this.retryRegistry = RetryRegistry.of(defaultRetryConfig());
        
        log.info("DegradationClientFactory initialized");
    }
    
    /**
     * Create or get a DegradationAwareClient for a service.
     * Clients are cached by service ID.
     */
    public DegradationAwareClient getClient(String serviceId, String baseUrl) {
        return clientCache.computeIfAbsent(serviceId, id -> {
            log.info("Creating DegradationAwareClient for service: {} at {}", serviceId, baseUrl);
            return createClient(serviceId, baseUrl, new DegradationClientProperties());
        });
    }
    
    /**
     * Create a DegradationAwareClient with custom properties.
     */
    public DegradationAwareClient getClient(
            String serviceId,
            String baseUrl,
            DegradationClientProperties properties) {
        return clientCache.computeIfAbsent(serviceId, id -> createClient(serviceId, baseUrl, properties));
    }
    
    /**
     * Create a new DegradationAwareClient (not cached).
     */
    public DegradationAwareClient createClient(
            String serviceId,
            String baseUrl,
            DegradationClientProperties properties) {
        
        // Configure custom circuit breaker for this service
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(properties.getCircuitBreakerFailureRateThreshold())
                .slidingWindowSize(properties.getCircuitBreakerSlidingWindowSize())
                .waitDurationInOpenState(Duration.ofMillis(properties.getCircuitBreakerWaitDurationMs()))
                .permittedNumberOfCallsInHalfOpenState(properties.getCircuitBreakerPermittedCallsInHalfOpen())
                .recordExceptions(IOException.class, TimeoutException.class, RuntimeException.class)
                .build();
        
        circuitBreakerRegistry.circuitBreaker(serviceId, cbConfig);
        
        // Configure custom retry for this service
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(properties.getMaxRetries())
                .waitDuration(Duration.ofMillis(properties.getRetryInitialIntervalMs()))
                .retryExceptions(IOException.class, TimeoutException.class)
                .build();
        
        retryRegistry.retry(serviceId, retryConfig);
        
        return DegradationAwareClient.builder(serviceId)
                .webClientBuilder(WebClient.builder().baseUrl(baseUrl))
                .healthStateManager(healthStateManager)
                .circuitBreakerRegistry(circuitBreakerRegistry)
                .retryRegistry(retryRegistry)
                .meterRegistry(meterRegistry)
                .properties(properties)
                .build();
    }
    
    /**
     * Create pre-configured clients for all BUTTERFLY services.
     */
    public Map<String, DegradationAwareClient> createButterflyClients(ServiceUrlProvider urlProvider) {
        Map<String, DegradationAwareClient> clients = new ConcurrentHashMap<>();
        
        clients.put("capsule", getClient("capsule", urlProvider.getCapsuleUrl()));
        clients.put("odyssey", getClient("odyssey", urlProvider.getOdysseyUrl()));
        clients.put("perception", getClient("perception", urlProvider.getPerceptionUrl()));
        clients.put("plato", getClient("plato", urlProvider.getPlatoUrl()));
        clients.put("nexus", getClient("nexus", urlProvider.getNexusUrl()));
        clients.put("synapse", getClient("synapse", urlProvider.getSynapseUrl()));
        
        log.info("Created BUTTERFLY ecosystem clients: {}", clients.keySet());
        return clients;
    }
    
    /**
     * Get the circuit breaker registry (for monitoring/testing).
     */
    public CircuitBreakerRegistry getCircuitBreakerRegistry() {
        return circuitBreakerRegistry;
    }
    
    /**
     * Get the retry registry (for monitoring/testing).
     */
    public RetryRegistry getRetryRegistry() {
        return retryRegistry;
    }
    
    /**
     * Clear the client cache (useful for testing or reconfiguration).
     */
    public void clearCache() {
        clientCache.clear();
    }
    
    private CircuitBreakerConfig defaultCircuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(10)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .recordExceptions(IOException.class, TimeoutException.class, RuntimeException.class)
                .build();
    }
    
    private RetryConfig defaultRetryConfig() {
        return RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .retryExceptions(IOException.class, TimeoutException.class)
                .build();
    }
    
    /**
     * Interface for providing service URLs.
     */
    public interface ServiceUrlProvider {
        String getCapsuleUrl();
        String getOdysseyUrl();
        String getPerceptionUrl();
        String getPlatoUrl();
        String getNexusUrl();
        String getSynapseUrl();
    }
}

