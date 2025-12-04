/**
 * Cross-ecosystem security components for the BUTTERFLY platform.
 * <p>
 * This package provides unified security primitives that are shared across all
 * BUTTERFLY services (PERCEPTION, CAPSULE, ODYSSEY, PLATO, NEXUS, SYNAPSE).
 * <p>
 * Key components:
 * <ul>
 *   <li>{@link com.z254.butterfly.common.security.ButterflyPrincipal} - Unified security principal</li>
 *   <li>{@link com.z254.butterfly.common.security.ButterflyRole} - Ecosystem-wide roles</li>
 *   <li>{@link com.z254.butterfly.common.security.ButterflyScope} - Hierarchical permission scopes</li>
 *   <li>{@link com.z254.butterfly.common.security.JwtClaimsExtractor} - JWT claim extraction</li>
 *   <li>{@link com.z254.butterfly.common.security.SecurityContextPropagator} - Context propagation</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>{@code
 * // Extract principal from JWT
 * ButterflyPrincipal principal = JwtClaimsExtractor.extractPrincipal(jwtClaims);
 * 
 * // Check authorization
 * if (principal.hasRole(ButterflyRole.ADMIN)) {
 *     // Admin operation
 * }
 * if (principal.canPerform("capsule", "capsules", "write")) {
 *     // Create capsule
 * }
 * 
 * // Propagate context to downstream service
 * Map<String, String> headers = SecurityContextPropagator.toHttpHeaders(principal);
 * 
 * // Propagate context to Kafka
 * SecurityContextPropagator.toKafkaHeaders(principal, kafkaHeaders);
 * }</pre>
 *
 * @see com.z254.butterfly.common.security.ButterflyPrincipal
 * @see com.z254.butterfly.common.security.ButterflyRole
 */
package com.z254.butterfly.common.security;

