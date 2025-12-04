package com.z254.butterfly.common.tenant;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;

/**
 * Auto-configuration for tenant context propagation in servlet-based BUTTERFLY services.
 * <p>
 * This is separated from {@link TenantAutoConfiguration} to prevent class loading issues
 * in WebFlux applications that don't have the servlet API on the classpath.
 */
@AutoConfiguration
@EnableConfigurationProperties(TenantProperties.class)
@ConditionalOnProperty(prefix = "butterfly.tenant", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "jakarta.servlet.Filter")
public class TenantServletAutoConfiguration {

    /**
     * Servlet-based tenant filter for Spring MVC applications.
     */
    @Bean
    @ConditionalOnMissingBean(TenantFilter.class)
    @Order(10)
    public TenantFilter tenantFilter(TenantProperties properties,
                                     @org.springframework.beans.factory.annotation.Autowired(required = false)
                                     TenantFilter.TenantValidator tenantValidator) {
        return new TenantFilter(properties, tenantValidator);
    }
}

