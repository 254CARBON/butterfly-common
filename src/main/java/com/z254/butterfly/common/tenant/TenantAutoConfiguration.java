package com.z254.butterfly.common.tenant;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.web.server.WebFilter;

/**
 * Auto-configuration for tenant context propagation in BUTTERFLY services.
 * <p>
 * This configuration automatically sets up:
 * <ul>
 *   <li>{@link TenantFilter} for servlet-based applications</li>
 *   <li>{@link TenantWebFilter} for reactive (WebFlux) applications</li>
 * </ul>
 *
 * <h2>Enabling/Disabling</h2>
 * <pre>{@code
 * # Enable tenant propagation (default)
 * butterfly.tenant.enabled=true
 *
 * # Disable tenant propagation
 * butterfly.tenant.enabled=false
 * }</pre>
 *
 * <h2>Custom Tenant Validation</h2>
 * To validate tenants against CAPSULE, define a TenantValidator bean:
 * <pre>{@code
 * @Bean
 * public TenantFilter.TenantValidator tenantValidator(CapsuleClient capsuleClient) {
 *     return tenantId -> {
 *         try {
 *             var tenant = capsuleClient.getTenant(tenantId).block();
 *             if (tenant != null && tenant.isActive()) {
 *                 return TenantFilter.TenantValidator.ValidationResult.valid();
 *             }
 *             return TenantFilter.TenantValidator.ValidationResult.invalid("Tenant not found or inactive");
 *         } catch (Exception e) {
 *             return TenantFilter.TenantValidator.ValidationResult.invalid("Validation failed: " + e.getMessage());
 *         }
 *     };
 * }
 * }</pre>
 */
@AutoConfiguration
@EnableConfigurationProperties(TenantProperties.class)
@ConditionalOnProperty(prefix = "butterfly.tenant", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class TenantAutoConfiguration {

    /**
     * Reactive WebFilter for Spring WebFlux applications.
     */
    @Bean
    @ConditionalOnClass(WebFilter.class)
    @ConditionalOnMissingBean(name = "tenantWebFilter")
    @Order(10)
    public WebFilter tenantWebFilter(TenantProperties properties) {
        return new TenantWebFilter(properties);
    }
}

