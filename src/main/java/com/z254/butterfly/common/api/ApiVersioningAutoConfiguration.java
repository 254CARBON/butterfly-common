package com.z254.butterfly.common.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Auto-configuration for API versioning infrastructure.
 * 
 * <p>Automatically configures:
 * <ul>
 *   <li>API versioning filter for version negotiation</li>
 *   <li>Deprecation headers for legacy endpoints</li>
 *   <li>Problem detail exception handler</li>
 * </ul>
 * 
 * <h2>Enabling</h2>
 * <p>Enabled by default when {@code butterfly.api.versioning.enabled=true} (default).
 * 
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(ApiVersionProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "butterfly.api.versioning", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ApiVersioningAutoConfiguration {
    
    @Value("${spring.application.name:unknown}")
    private String serviceName;
    
    @Bean
    @ConditionalOnMissingBean
    public ApiVersioningFilter apiVersioningFilter(
            ApiVersionProperties properties,
            ObjectMapper objectMapper) {
        return new ApiVersioningFilter(properties, objectMapper, serviceName);
    }
    
    @Bean
    public FilterRegistrationBean<ApiVersioningFilter> apiVersioningFilterRegistration(
            ApiVersioningFilter filter) {
        FilterRegistrationBean<ApiVersioningFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/api/*", "/v1/*", "/v2/*");
        registration.setName("apiVersioningFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
    
    @Bean
    @ConditionalOnMissingBean
    public ProblemDetailExceptionHandler problemDetailExceptionHandler() {
        return new ProblemDetailExceptionHandler();
    }
}
