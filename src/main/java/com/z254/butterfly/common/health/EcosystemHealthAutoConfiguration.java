package com.z254.butterfly.common.health;

import com.z254.butterfly.common.client.ButterflyServiceClientFactory;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Auto-configuration for ecosystem health aggregation.
 * 
 * <p>Provides:
 * <ul>
 *   <li>{@link EcosystemHealthAggregator} - Health aggregation service</li>
 *   <li>{@link EcosystemHealthController} - REST endpoints</li>
 * </ul>
 * 
 * <p>To disable, set:
 * <pre>
 * butterfly.health.enabled=false
 * </pre>
 */
@AutoConfiguration
@EnableScheduling
@ConditionalOnProperty(prefix = "butterfly.health", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(EcosystemHealthProperties.class)
@Slf4j
public class EcosystemHealthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ButterflyServiceClientFactory.class)
    public EcosystemHealthAggregator ecosystemHealthAggregator(
            ButterflyServiceClientFactory clientFactory,
            CircuitBreakerRegistry circuitBreakerRegistry,
            KafkaTemplate<String, Object> kafkaTemplate,
            MeterRegistry meterRegistry,
            EcosystemHealthProperties properties) {
        log.info("Creating EcosystemHealthAggregator - polling interval: {}ms, kafka publishing: {}",
                properties.getPollingIntervalMs(), properties.isPublishToKafka());
        return new EcosystemHealthAggregator(
                clientFactory,
                circuitBreakerRegistry,
                kafkaTemplate,
                meterRegistry,
                properties
        );
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(EcosystemHealthAggregator.class)
    public EcosystemHealthController ecosystemHealthController(
            EcosystemHealthAggregator healthAggregator) {
        log.info("Creating EcosystemHealthController");
        return new EcosystemHealthController(healthAggregator);
    }
}
