/**
 * Resilience components for the BUTTERFLY ecosystem.
 * 
 * <p>This package provides:
 * <ul>
 *   <li>{@link com.z254.butterfly.common.resilience.DegradationState} - Service degradation states</li>
 *   <li>{@link com.z254.butterfly.common.resilience.ServiceHealth} - Health state model</li>
 *   <li>{@link com.z254.butterfly.common.resilience.HealthStateManager} - Central health state tracker</li>
 *   <li>{@link com.z254.butterfly.common.resilience.DegradationAwareClient} - Health-aware HTTP client</li>
 *   <li>{@link com.z254.butterfly.common.resilience.DegradationClientFactory} - Client factory</li>
 * </ul>
 * 
 * <p>Usage example:
 * <pre>{@code
 * // Inject the factory
 * @Autowired
 * private DegradationClientFactory clientFactory;
 * 
 * // Create a client for a service
 * DegradationAwareClient capsuleClient = clientFactory.getClient("capsule", "http://capsule:8081");
 * 
 * // Make requests with automatic health awareness
 * capsuleClient.get("/api/v1/entities/123", Entity.class)
 *     .subscribe(entity -> System.out.println(entity));
 * 
 * // Check service health before making requests
 * if (capsuleClient.isOperational()) {
 *     // proceed
 * }
 * 
 * // Register fallback handlers
 * capsuleClient.withFallback("/api/v1/entities/*", error -> defaultEntity());
 * }</pre>
 */
package com.z254.butterfly.common.resilience;

