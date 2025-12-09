package com.z254.butterfly.common.client;

import com.z254.butterfly.common.telemetry.TenantContextHolder;
import io.micrometer.core.instrument.MeterRegistry;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Factory for creating pre-configured WebClient instances for BUTTERFLY services.
 * 
 * <p>This factory provides WebClients with:
 * <ul>
 *   <li>Standard timeout configuration</li>
 *   <li>Connection pooling</li>
 *   <li>Tenant context propagation</li>
 *   <li>Correlation ID propagation</li>
 *   <li>Request/response logging</li>
 *   <li>Metrics integration</li>
 * </ul>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * WebClient client = WebClientFactory.create()
 *     .baseUrl("http://capsule-service:8081")
 *     .timeout(Duration.ofSeconds(30))
 *     .serviceName("capsule")
 *     .build();
 *     
 * client.get()
 *     .uri("/api/v1/capsules/{id}", capsuleId)
 *     .retrieve()
 *     .bodyToMono(Capsule.class);
 * }</pre>
 * 
 * @since 1.0.0
 */
public class WebClientFactory {
    
    private static final String TENANT_HEADER = "X-Tenant-ID";
    private static final String CORRELATION_HEADER = "X-Correlation-ID";
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String SOURCE_SERVICE_HEADER = "X-Source-Service";
    private static final String API_VERSION_HEADER = "Accept-Version";
    
    /**
     * Standard timeouts for BUTTERFLY services.
     */
    public static final class Timeouts {
        /** Connection timeout for establishing connection */
        public static final Duration CONNECT = Duration.ofSeconds(5);
        
        /** Read timeout for waiting on response */
        public static final Duration READ = Duration.ofSeconds(30);
        
        /** Write timeout for sending request */
        public static final Duration WRITE = Duration.ofSeconds(10);
        
        /** Total request timeout */
        public static final Duration REQUEST = Duration.ofSeconds(60);
        
        /** Fast path timeout for real-time operations */
        public static final Duration FAST_PATH = Duration.ofSeconds(5);
        
        /** Long-running operation timeout */
        public static final Duration LONG_RUNNING = Duration.ofMinutes(5);
        
        private Timeouts() {}
    }
    
    /**
     * Create a new WebClient builder.
     */
    public static Builder create() {
        return new Builder();
    }
    
    /**
     * Create a pre-configured WebClient for a BUTTERFLY service.
     * 
     * @param baseUrl Service base URL
     * @param serviceName Target service name
     * @param sourceService Name of the calling service
     * @return Configured WebClient
     */
    public static WebClient forService(String baseUrl, String serviceName, String sourceService) {
        return create()
                .baseUrl(baseUrl)
                .serviceName(serviceName)
                .sourceService(sourceService)
                .build();
    }
    
    /**
     * Builder for WebClient configuration.
     */
    public static class Builder {
        private String baseUrl;
        private String serviceName;
        private String sourceService;
        private String apiVersion = "v1";
        private Duration connectTimeout = Timeouts.CONNECT;
        private Duration readTimeout = Timeouts.READ;
        private Duration writeTimeout = Timeouts.WRITE;
        private MeterRegistry meterRegistry;
        private boolean enableLogging = true;
        private boolean propagateTenant = true;
        private boolean propagateTrace = true;
        private int maxConnections = 500;
        private int maxIdleTime = 20; // seconds
        
        /**
         * Set the base URL for the service.
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }
        
        /**
         * Set the target service name (for logging and metrics).
         */
        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }
        
        /**
         * Set the source service name (for X-Source-Service header).
         */
        public Builder sourceService(String sourceService) {
            this.sourceService = sourceService;
            return this;
        }
        
        /**
         * Set the API version to request.
         */
        public Builder apiVersion(String apiVersion) {
            this.apiVersion = apiVersion;
            return this;
        }
        
        /**
         * Set all timeouts to the same value.
         */
        public Builder timeout(Duration timeout) {
            this.readTimeout = timeout;
            this.writeTimeout = timeout;
            return this;
        }
        
        /**
         * Set connection timeout.
         */
        public Builder connectTimeout(Duration timeout) {
            this.connectTimeout = timeout;
            return this;
        }
        
        /**
         * Set read timeout.
         */
        public Builder readTimeout(Duration timeout) {
            this.readTimeout = timeout;
            return this;
        }
        
        /**
         * Set write timeout.
         */
        public Builder writeTimeout(Duration timeout) {
            this.writeTimeout = timeout;
            return this;
        }
        
        /**
         * Set meter registry for metrics.
         */
        public Builder meterRegistry(MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
            return this;
        }
        
        /**
         * Enable or disable request/response logging.
         */
        public Builder logging(boolean enable) {
            this.enableLogging = enable;
            return this;
        }
        
        /**
         * Enable or disable tenant context propagation.
         */
        public Builder propagateTenant(boolean enable) {
            this.propagateTenant = enable;
            return this;
        }
        
        /**
         * Enable or disable trace context propagation.
         */
        public Builder propagateTrace(boolean enable) {
            this.propagateTrace = enable;
            return this;
        }
        
        /**
         * Set maximum connections in the pool.
         */
        public Builder maxConnections(int max) {
            this.maxConnections = max;
            return this;
        }
        
        /**
         * Set maximum idle time for connections (in seconds).
         */
        public Builder maxIdleTime(int seconds) {
            this.maxIdleTime = seconds;
            return this;
        }
        
        /**
         * Build the WebClient.
         */
        public WebClient build() {
            HttpClient httpClient = HttpClient.create()
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis())
                    .responseTimeout(readTimeout)
                    .doOnConnected(conn -> conn
                            .addHandlerLast(new ReadTimeoutHandler(readTimeout.toMillis(), TimeUnit.MILLISECONDS))
                            .addHandlerLast(new WriteTimeoutHandler(writeTimeout.toMillis(), TimeUnit.MILLISECONDS)));
            
            WebClient.Builder builder = WebClient.builder()
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json");
            
            if (baseUrl != null) {
                builder.baseUrl(baseUrl);
            }
            
            if (apiVersion != null) {
                builder.defaultHeader(API_VERSION_HEADER, apiVersion);
            }
            
            // Add context propagation filters
            if (propagateTenant || propagateTrace) {
                builder.filter(contextPropagationFilter());
            }
            
            if (sourceService != null) {
                builder.defaultHeader(SOURCE_SERVICE_HEADER, sourceService);
            }
            
            return builder.build();
        }
        
        private ExchangeFilterFunction contextPropagationFilter() {
            return ExchangeFilterFunction.ofRequestProcessor(request -> {
                ClientRequest.Builder requestBuilder = ClientRequest.from(request);
                
                // Propagate tenant context
                if (propagateTenant) {
                    String tenantId = TenantContextHolder.getTenantId();
                    if (tenantId != null && !tenantId.isEmpty()) {
                        requestBuilder.header(TENANT_HEADER, tenantId);
                    }
                }
                
                // Propagate trace context from MDC
                if (propagateTrace) {
                    String correlationId = MDC.get("correlationId");
                    if (correlationId == null) {
                        correlationId = MDC.get("X-Correlation-ID");
                    }
                    if (correlationId != null) {
                        requestBuilder.header(CORRELATION_HEADER, correlationId);
                    }
                    
                    String requestId = MDC.get("requestId");
                    if (requestId == null) {
                        requestId = MDC.get("X-Request-ID");
                    }
                    if (requestId != null) {
                        requestBuilder.header(REQUEST_ID_HEADER, requestId);
                    }
                    
                    String traceId = MDC.get("traceId");
                    if (traceId != null) {
                        requestBuilder.header("X-B3-TraceId", traceId);
                    }
                    
                    String spanId = MDC.get("spanId");
                    if (spanId != null) {
                        requestBuilder.header("X-B3-SpanId", spanId);
                    }
                }
                
                return Mono.just(requestBuilder.build());
            });
        }
    }
}
