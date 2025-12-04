/**
 * Standardized service clients for the BUTTERFLY ecosystem.
 * 
 * <p>This package provides typed, resilient clients for all BUTTERFLY services:
 * <ul>
 *   <li>{@link com.z254.butterfly.common.client.PerceptionClient} - PERCEPTION service (sensory layer)</li>
 *   <li>{@link com.z254.butterfly.common.client.CapsuleClient} - CAPSULE service (4D history storage)</li>
 *   <li>{@link com.z254.butterfly.common.client.OdysseyClient} - ODYSSEY service (strategic cognition)</li>
 *   <li>{@link com.z254.butterfly.common.client.PlatoClient} - PLATO service (governance)</li>
 *   <li>{@link com.z254.butterfly.common.client.NexusClient} - NEXUS service (integration cortex)</li>
 *   <li>{@link com.z254.butterfly.common.client.SynapseClient} - SYNAPSE service (execution engine)</li>
 * </ul>
 * 
 * <p>All clients are built on top of {@link com.z254.butterfly.common.resilience.DegradationAwareClient}
 * and include:
 * <ul>
 *   <li>Circuit breaker protection</li>
 *   <li>Retry with exponential backoff</li>
 *   <li>Health-based request blocking</li>
 *   <li>Fallback execution</li>
 *   <li>Tenant context propagation</li>
 *   <li>Metrics and tracing</li>
 * </ul>
 * 
 * <h2>Usage Example</h2>
 * <pre>
 * &#64;Autowired
 * private ButterflyServiceClientFactory clientFactory;
 * 
 * public void example() {
 *     PerceptionClient perception = clientFactory.perceptionClient();
 *     
 *     perception.getRimNode("rim:entity:finance:EURUSD")
 *         .subscribe(node -&gt; log.info("Got RIM node: {}", node));
 *     
 *     // Check health before making calls
 *     if (perception.isOperational()) {
 *         perception.getActiveSignals()
 *             .subscribe(signal -&gt; log.info("Active signal: {}", signal));
 *     }
 * }
 * </pre>
 * 
 * <h2>Configuration</h2>
 * <pre>
 * butterfly:
 *   clients:
 *     perception:
 *       url: http://localhost:8080
 *       timeout: 30s
 *       circuit-breaker:
 *         failure-rate-threshold: 50
 *     capsule:
 *       url: http://localhost:8081
 *     odyssey:
 *       url: http://localhost:8082
 *     plato:
 *       url: http://localhost:8083
 *     nexus:
 *       url: http://localhost:8084
 *     synapse:
 *       url: http://localhost:8085
 * </pre>
 * 
 * @see com.z254.butterfly.common.client.ButterflyServiceClientFactory
 * @see com.z254.butterfly.common.client.ServiceClientProperties
 * @see com.z254.butterfly.common.resilience.DegradationAwareClient
 */
package com.z254.butterfly.common.client;
