package com.z254.butterfly.common.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Auto-configuration for BUTTERFLY telemetry components.
 * 
 * <p>This configuration automatically registers:
 * <ul>
 *   <li>{@link CorrelationIdFilter} - Extracts/generates correlation IDs from HTTP headers</li>
 *   <li>{@link TenantAwareMeterFilter} - Injects tenant context into metrics</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * <p>This auto-configuration is automatically applied to all BUTTERFLY services
 * that include butterfly-common as a dependency. No explicit configuration is required.
 * 
 * <h2>Customization</h2>
 * <p>To disable auto-configuration, set the property:
 * <pre>butterfly.telemetry.enabled=false</pre>
 * 
 * <p>To customize the filter order:
 * <pre>butterfly.telemetry.filter-order=1</pre>
 * 
 * @see CorrelationIdFilter
 * @see TenantAwareMeterFilter
 * @see TenantContextHolder
 */
@Configuration
@ConditionalOnClass({MeterRegistry.class})
public class TelemetryAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TelemetryAutoConfiguration.class);

    @Value("${spring.application.name:unknown}")
    private String serviceName;

    @Value("${ENVIRONMENT:development}")
    private String environment;

    @Value("${butterfly.telemetry.filter-order:#{T(org.springframework.core.Ordered).HIGHEST_PRECEDENCE + 10}}")
    private int filterOrder;

    /**
     * Registers the CorrelationIdFilter as a servlet filter.
     * 
     * <p>This filter:
     * <ul>
     *   <li>Extracts X-Correlation-ID, X-Request-ID, X-Tenant-ID headers</li>
     *   <li>Generates UUIDs if headers are missing</li>
     *   <li>Populates TenantContextHolder for metric tagging</li>
     *   <li>Sets MDC for structured logging</li>
     *   <li>Echoes correlation headers in response</li>
     * </ul>
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean(name = "correlationIdFilterRegistration")
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration() {
        log.info("Registering CorrelationIdFilter for service: {}", serviceName);
        
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new CorrelationIdFilter());
        registration.addUrlPatterns("/*");
        registration.setName("correlationIdFilter");
        registration.setOrder(filterOrder);
        
        return registration;
    }

    /**
     * Provides the shared TenantAwareMeterFilter for metric tagging.
     * 
     * <p>This filter automatically injects tenant context from TenantContextHolder
     * into all application metrics, enabling per-tenant dashboards and SLOs.
     */
    @Bean
    @ConditionalOnMissingBean(TenantAwareMeterFilter.class)
    public MeterFilter sharedTenantAwareMeterFilter() {
        log.info("Registering TenantAwareMeterFilter for service: {} in environment: {}", 
            serviceName, environment);
        return new TenantAwareMeterFilter(serviceName, environment);
    }

    /**
     * Provides the WebSocket tracing interceptor for WebSocket correlation.
     */
    @Bean
    @ConditionalOnMissingBean(WebSocketTracingInterceptor.class)
    public WebSocketTracingInterceptor webSocketTracingInterceptor() {
        log.debug("Registering WebSocketTracingInterceptor for service: {}", serviceName);
        return new WebSocketTracingInterceptor();
    }
}

