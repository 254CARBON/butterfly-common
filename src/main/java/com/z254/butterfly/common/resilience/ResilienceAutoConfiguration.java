package com.z254.butterfly.common.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Auto-configuration for BUTTERFLY resilience components.
 * Provides health state management and degradation-aware clients.
 * 
 * <p>This configuration loads after Resilience4j's auto-configuration to ensure
 * proper bean ordering. The {@code @Primary} annotations ensure that when multiple
 * CircuitBreakerRegistry or RetryRegistry beans exist (e.g., from Resilience4j's
 * Spring Boot 3 starter), BUTTERFLY's beans are used as the default for injection.</p>
 */
@AutoConfiguration
@AutoConfigureAfter(name = "io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration")
@EnableConfigurationProperties(HealthStateProperties.class)
@ConditionalOnProperty(prefix = "butterfly.resilience", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
@Slf4j
public class ResilienceAutoConfiguration {
    
    public ResilienceAutoConfiguration() {
        log.info("BUTTERFLY ResilienceAutoConfiguration enabled.");
    }
    
    @Bean
    @Primary
    @ConditionalOnMissingBean
    @ConditionalOnClass(CircuitBreakerRegistry.class)
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        log.debug("Registering default CircuitBreakerRegistry");
        return CircuitBreakerRegistry.ofDefaults();
    }
    
    @Bean
    @Primary
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

