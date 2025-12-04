package com.z254.butterfly.common.client;

import com.z254.butterfly.common.resilience.DegradationClientFactory;
import com.z254.butterfly.common.resilience.HealthStateManager;
import com.z254.butterfly.common.resilience.HealthStateProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Auto-configuration for BUTTERFLY service clients.
 * 
 * <p>This configuration is enabled by default and provides:
 * <ul>
 *   <li>{@link ServiceClientProperties} - Configuration properties</li>
 *   <li>{@link ButterflyServiceClientFactory} - Factory for creating typed clients</li>
 * </ul>
 * 
 * <p>To disable auto-configuration, set:
 * <pre>
 * butterfly.clients.enabled=false
 * </pre>
 */
@AutoConfiguration
@ConditionalOnClass({WebClient.class, CircuitBreakerRegistry.class})
@ConditionalOnProperty(prefix = "butterfly.clients", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({ServiceClientProperties.class, HealthStateProperties.class})
@Slf4j
public class ServiceClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public HealthStateManager healthStateManager(
            CircuitBreakerRegistry circuitBreakerRegistry,
            MeterRegistry meterRegistry,
            HealthStateProperties properties) {
        log.info("Creating HealthStateManager with monitored services: {}", properties.getMonitoredServices());
        return new HealthStateManager(circuitBreakerRegistry, meterRegistry, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public DegradationClientFactory degradationClientFactory(
            HealthStateManager healthStateManager,
            MeterRegistry meterRegistry) {
        log.info("Creating DegradationClientFactory");
        return new DegradationClientFactory(healthStateManager, meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public ButterflyServiceClientFactory butterflyServiceClientFactory(
            DegradationClientFactory degradationClientFactory,
            ServiceClientProperties properties) {
        log.info("Creating ButterflyServiceClientFactory");
        return new ButterflyServiceClientFactory(degradationClientFactory, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        log.info("Creating default CircuitBreakerRegistry");
        return CircuitBreakerRegistry.ofDefaults();
    }
}
