/**
 * Tenant context propagation infrastructure for the BUTTERFLY ecosystem.
 * <p>
 * This package provides standardized utilities for propagating tenant context
 * across service boundaries via HTTP requests, Kafka messages, and reactive streams.
 *
 * <h2>Architecture</h2>
 * <p>CAPSULE is the central tenant authority in BUTTERFLY. Other services use
 * this package to:
 * <ul>
 *   <li>Extract tenant ID from incoming requests</li>
 *   <li>Propagate tenant context to downstream services</li>
 *   <li>Tag metrics and logs with tenant information</li>
 * </ul>
 *
 * <h2>Components</h2>
 * <ul>
 *   <li>{@link com.z254.butterfly.common.tenant.TenantFilter} - Servlet filter for HTTP requests</li>
 *   <li>{@link com.z254.butterfly.common.tenant.TenantWebFilter} - WebFlux filter for reactive apps</li>
 *   <li>{@link com.z254.butterfly.common.tenant.TenantKafkaInterceptor} - Kafka message header propagation</li>
 *   <li>{@link com.z254.butterfly.common.tenant.TenantReactiveContextPropagator} - Reactor Context utilities</li>
 *   <li>{@link com.z254.butterfly.common.tenant.TenantProperties} - Configuration properties</li>
 *   <li>{@link com.z254.butterfly.common.tenant.TenantAutoConfiguration} - Spring Boot auto-configuration</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // In application.yml
 * butterfly:
 *   tenant:
 *     enabled: true
 *     header-name: X-Tenant-ID
 *     default-tenant-id: default
 *
 * // Auto-configured filters will extract tenant from requests
 * // and set it on TenantContextHolder for metric tagging and logging
 * }</pre>
 *
 * @see com.z254.butterfly.common.telemetry.TenantContextHolder
 * @see com.z254.butterfly.common.telemetry.TenantAwareMeterFilter
 */
package com.z254.butterfly.common.tenant;

