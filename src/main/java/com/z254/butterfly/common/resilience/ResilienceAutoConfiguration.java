package com.z254.butterfly.common.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Auto-configuration for BUTTERFLY resilience components.
 * Provides health state management and degradation-aware clients.
 */
@AutoConfiguration
@EnableConfigurationProperties(HealthStateProperties.class)
@ConditionalOnProperty(prefix = "butterfly.resilience", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
@Slf4j
public class ResilienceAutoConfiguration {
    
    public ResilienceAutoConfiguration() {
        log.info("BUTTERFLY ResilienceAutoConfiguration enabled.");
    }
    
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(CircuitBreakerRegistry.class)
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        log.debug("Registering default CircuitBreakerRegistry");
        return CircuitBreakerRegistry.ofDefaults();
    }
    
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(RetryRegistry.class)
    public RetryRegistry retryRegistry() {
        log.debug("Registering default RetryRegistry");
        return RetryRegistry.ofDefaults();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public HealthStateManager healthStateManager(
            CircuitBreakerRegistry circuitBreakerRegistry,
            MeterRegistry meterRegistry,
            HealthStateProperties properties) {
        log.debug("Registering HealthStateManager");
        return new HealthStateManager(circuitBreakerRegistry, meterRegistry, properties);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public DegradationClientFactory degradationClientFactory(
            HealthStateManager healthStateManager,
            MeterRegistry meterRegistry) {
        log.debug("Registering DegradationClientFactory");
        return new DegradationClientFactory(healthStateManager, meterRegistry);
    }
}

