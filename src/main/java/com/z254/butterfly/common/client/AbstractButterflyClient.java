package com.z254.butterfly.common.client;

import com.z254.butterfly.common.api.ProblemDetail;
import com.z254.butterfly.common.resilience.DegradationState;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.function.Function;

/**
 * Abstract base class for BUTTERFLY service clients.
 * 
 * <p>Provides common functionality for all service clients:
 * <ul>
 *   <li>WebClient-based HTTP communication</li>
 *   <li>Circuit breaker protection</li>
 *   <li>Retry with exponential backoff</li>
 *   <li>Standard error handling with fallbacks</li>
 *   <li>Health check implementation</li>
 *   <li>Degradation state tracking</li>
 * </ul>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * public class MyCapsuleClient extends AbstractButterflyClient implements CapsuleClient {
 *     
 *     public MyCapsuleClient(WebClient webClient) {
 *         super(webClient, "capsule", ClientConfig.defaults());
 *     }
 *     
 *     @Override
 *     public Mono<Capsule> getCapsule(String capsuleId) {
 *         return get("/api/v1/capsules/" + capsuleId, Capsule.class);
 *     }
 * }
 * }</pre>
 * 
 * @since 1.0.0
 */
public abstract class AbstractButterflyClient implements ButterflyServiceClient {
    
    protected final Logger log = LoggerFactory.getLogger(getClass());
    
    protected final WebClient webClient;
    protected final String serviceId;
    protected final CircuitBreaker circuitBreaker;
    protected final Retry retry;
    protected final ClientConfig config;
    
    private volatile DegradationState degradationState = DegradationState.HEALTHY;
    
    /**
     * Create a client with the given WebClient and configuration.
     * 
     * @param webClient Configured WebClient instance
     * @param serviceId Service identifier for circuit breaker naming
     * @param config Client configuration
     */
    protected AbstractButterflyClient(WebClient webClient, String serviceId, ClientConfig config) {
        this.webClient = webClient;
        this.serviceId = serviceId;
        this.config = config;
        
        // Configure circuit breaker
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(config.circuitBreakerFailureRateThreshold())
                .slidingWindowSize(config.circuitBreakerSlidingWindowSize())
                .waitDurationInOpenState(config.circuitBreakerWaitDuration())
                .permittedNumberOfCallsInHalfOpenState(config.circuitBreakerPermittedCallsInHalfOpen())
                .recordExceptions(WebClientResponseException.class, java.net.ConnectException.class)
                .ignoreExceptions(WebClientResponseException.NotFound.class)
                .build();
        
        this.circuitBreaker = CircuitBreakerRegistry.ofDefaults()
                .circuitBreaker(serviceId + "-circuit", cbConfig);
        
        // Track circuit breaker state changes
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> {
                    log.info("Circuit breaker {} state transition: {} -> {}",
                            serviceId, event.getStateTransition().getFromState(),
                            event.getStateTransition().getToState());
                    updateDegradationState(event.getStateTransition().getToState());
                });
        
        // Configure retry
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(config.maxRetries())
                .waitDuration(config.retryInitialInterval())
                .retryOnException(e -> e instanceof WebClientResponseException.ServiceUnavailable
                        || e instanceof java.net.ConnectException)
                .build();
        
        this.retry = RetryRegistry.ofDefaults().retry(serviceId + "-retry", retryConfig);
    }
    
    /**
     * Create a client with default configuration.
     */
    protected AbstractButterflyClient(WebClient webClient, String serviceId) {
        this(webClient, serviceId, ClientConfig.defaults());
    }
    
    @Override
    public String getServiceId() {
        return serviceId;
    }
    
    @Override
    public Mono<Boolean> isHealthy() {
        return health()
                .map(HealthStatus::isUp)
                .onErrorReturn(false);
    }
    
    @Override
    public Mono<HealthStatus> health() {
        return webClient.get()
                .uri("/actuator/health")
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    String status = (String) response.getOrDefault("status", "UNKNOWN");
                    return new HealthStatus(status, serviceId, response);
                })
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(e -> Mono.just(HealthStatus.down(serviceId, e)));
    }
    
    @Override
    public DegradationState getDegradationState() {
        return degradationState;
    }
    
    // === HTTP Operations ===
    
    /**
     * Perform a GET request.
     * 
     * @param path Request path
     * @param responseType Response type class
     * @return Mono emitting the response
     */
    protected <T> Mono<T> get(String path, Class<T> responseType) {
        return webClient.get()
                .uri(path)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleErrorResponse)
                .bodyToMono(responseType)
                .transform(CircuitBreakerOperator.of(circuitBreaker))
                .doOnError(e -> log.warn("GET {} failed: {}", path, e.getMessage()));
    }
    
    /**
     * Perform a GET request returning a Flux.
     * 
     * @param path Request path
     * @param responseType Response element type class
     * @return Flux emitting response elements
     */
    protected <T> Flux<T> getMany(String path, Class<T> responseType) {
        return webClient.get()
                .uri(path)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleErrorResponse)
                .bodyToFlux(responseType)
                .transform(CircuitBreakerOperator.of(circuitBreaker))
                .doOnError(e -> log.warn("GET {} failed: {}", path, e.getMessage()));
    }
    
    /**
     * Perform a POST request.
     * 
     * @param path Request path
     * @param body Request body
     * @param responseType Response type class
     * @return Mono emitting the response
     */
    protected <T, R> Mono<R> post(String path, T body, Class<R> responseType) {
        return webClient.post()
                .uri(path)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleErrorResponse)
                .bodyToMono(responseType)
                .transform(CircuitBreakerOperator.of(circuitBreaker))
                .doOnError(e -> log.warn("POST {} failed: {}", path, e.getMessage()));
    }
    
    /**
     * Perform a PUT request.
     * 
     * @param path Request path
     * @param body Request body
     * @param responseType Response type class
     * @return Mono emitting the response
     */
    protected <T, R> Mono<R> put(String path, T body, Class<R> responseType) {
        return webClient.put()
                .uri(path)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleErrorResponse)
                .bodyToMono(responseType)
                .transform(CircuitBreakerOperator.of(circuitBreaker))
                .doOnError(e -> log.warn("PUT {} failed: {}", path, e.getMessage()));
    }
    
    /**
     * Perform a PATCH request.
     * 
     * @param path Request path
     * @param body Request body
     * @param responseType Response type class
     * @return Mono emitting the response
     */
    protected <T, R> Mono<R> patch(String path, T body, Class<R> responseType) {
        return webClient.patch()
                .uri(path)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleErrorResponse)
                .bodyToMono(responseType)
                .transform(CircuitBreakerOperator.of(circuitBreaker))
                .doOnError(e -> log.warn("PATCH {} failed: {}", path, e.getMessage()));
    }
    
    /**
     * Perform a DELETE request.
     * 
     * @param path Request path
     * @return Mono completing on success
     */
    protected Mono<Void> delete(String path) {
        return webClient.delete()
                .uri(path)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleErrorResponse)
                .bodyToMono(Void.class)
                .transform(CircuitBreakerOperator.of(circuitBreaker))
                .doOnError(e -> log.warn("DELETE {} failed: {}", path, e.getMessage()));
    }
    
    // === Error Handling ===
    
    /**
     * Handle error response from service.
     */
    protected Mono<? extends Throwable> handleErrorResponse(ClientResponse response) {
        return response.bodyToMono(ProblemDetail.class)
                .defaultIfEmpty(createDefaultProblemDetail(response.statusCode()))
                .flatMap(problem -> Mono.error(new ServiceClientException(problem)));
    }
    
    /**
     * Create a default ProblemDetail for responses without a body.
     */
    protected ProblemDetail createDefaultProblemDetail(HttpStatusCode status) {
        return ProblemDetail.builder()
                .type(ProblemDetail.ErrorTypes.INTERNAL_ERROR)
                .title(HttpStatus.valueOf(status.value()).getReasonPhrase())
                .status(status.value())
                .detail("Error response from " + serviceId)
                .service(serviceId)
                .build();
    }
    
    /**
     * Get a fallback value for a Mono operation.
     * 
     * @param fallback Fallback value
     * @return Error handler function
     */
    protected <T> Function<Throwable, Mono<T>> fallbackTo(T fallback) {
        return error -> {
            log.warn("Returning fallback for {}: {}", serviceId, error.getMessage());
            return Mono.just(fallback);
        };
    }
    
    /**
     * Get a fallback value for a Flux operation.
     * 
     * @param fallback Fallback values
     * @return Error handler function
     */
    protected <T> Function<Throwable, Flux<T>> fallbackToMany(Iterable<T> fallback) {
        return error -> {
            log.warn("Returning fallback for {}: {}", serviceId, error.getMessage());
            return Flux.fromIterable(fallback);
        };
    }
    
    /**
     * Update degradation state based on circuit breaker state.
     */
    private void updateDegradationState(CircuitBreaker.State cbState) {
        this.degradationState = switch (cbState) {
            case CLOSED -> DegradationState.HEALTHY;
            case HALF_OPEN -> DegradationState.DEGRADED;
            case OPEN -> DegradationState.IMPAIRED;
            default -> DegradationState.UNKNOWN;
        };
    }
    
    // === Configuration ===
    
    /**
     * Client configuration record.
     */
    public record ClientConfig(
            float circuitBreakerFailureRateThreshold,
            int circuitBreakerSlidingWindowSize,
            Duration circuitBreakerWaitDuration,
            int circuitBreakerPermittedCallsInHalfOpen,
            int maxRetries,
            Duration retryInitialInterval,
            Duration retryMaxInterval,
            double retryMultiplier,
            Duration timeout
    ) {
        /**
         * Create default configuration.
         */
        public static ClientConfig defaults() {
            return new ClientConfig(
                    50.0f,          // 50% failure rate
                    10,             // sliding window size
                    Duration.ofSeconds(30),
                    3,              // permitted calls in half-open
                    3,              // max retries
                    Duration.ofMillis(500),
                    Duration.ofSeconds(5),
                    2.0,
                    Duration.ofSeconds(30)
            );
        }
        
        /**
         * Create configuration with stricter circuit breaker settings.
         */
        public static ClientConfig strict() {
            return new ClientConfig(
                    30.0f,          // 30% failure rate
                    5,              // smaller sliding window
                    Duration.ofMinutes(1),
                    2,              // fewer permitted calls
                    2,              // fewer retries
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(10),
                    2.0,
                    Duration.ofSeconds(15)
            );
        }
        
        /**
         * Create configuration for fast-path operations.
         */
        public static ClientConfig fastPath() {
            return new ClientConfig(
                    60.0f,          // higher tolerance
                    20,             // larger window
                    Duration.ofSeconds(15),
                    5,
                    1,              // single retry
                    Duration.ofMillis(100),
                    Duration.ofMillis(500),
                    1.5,
                    Duration.ofSeconds(5)
            );
        }
    }
    
    /**
     * Exception for service client errors.
     */
    public static class ServiceClientException extends RuntimeException {
        private final ProblemDetail problemDetail;
        
        public ServiceClientException(ProblemDetail problemDetail) {
            super(problemDetail.detail());
            this.problemDetail = problemDetail;
        }
        
        public ProblemDetail getProblemDetail() {
            return problemDetail;
        }
        
        public int getStatus() {
            return problemDetail.status();
        }
    }
}
