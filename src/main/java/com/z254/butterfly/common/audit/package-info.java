/**
 * End-to-end audit trail components for the BUTTERFLY platform.
 * <p>
 * This package provides unified auditing capabilities for governance compliance,
 * supporting SOC2, GDPR, and SOX requirements across all BUTTERFLY services.
 * <p>
 * Key components:
 * <ul>
 *   <li>{@link com.z254.butterfly.common.audit.AuditEventBuilder} - Fluent builder for audit events</li>
 *   <li>{@link com.z254.butterfly.common.audit.AuditService} - Service for publishing audit events</li>
 *   <li>{@link com.z254.butterfly.common.audit.AuditAspect} - AOP aspect for automatic auditing</li>
 * </ul>
 * <p>
 * The audit trail tracks the complete lifecycle of data and decisions:
 * <ol>
 *   <li>RAW_EVENT - Initial data ingestion (PERCEPTION)</li>
 *   <li>SIGNAL - Signal generation and processing</li>
 *   <li>CAPSULE - Data capsule creation (CAPSULE)</li>
 *   <li>PATH_STRATEGY - Path evaluation and experiments (ODYSSEY)</li>
 *   <li>PLAN_APPROVAL - Plan creation and approval (PLATO)</li>
 *   <li>ACTION - Action execution (SYNAPSE)</li>
 *   <li>OUTCOME - Action outcomes</li>
 *   <li>LEARNING - Learning signals and evolution (NEXUS)</li>
 * </ol>
 * <p>
 * Usage:
 * <pre>{@code
 * // Manual auditing
 * auditService.audit(
 *     AuditType.EXPERIMENT_STARTED,
 *     "EXPERIMENT",
 *     experimentId,
 *     principal,
 *     builder -> builder.atStage(PipelineStage.PATH_STRATEGY)
 * );
 * 
 * // Annotation-based auditing
 * @Audited(type = AuditType.CAPSULE_CREATED, resourceType = "CAPSULE")
 * public Capsule createCapsule(Request request) { ... }
 * }</pre>
 *
 * @see com.z254.butterfly.common.audit.AuditEventBuilder
 * @see com.z254.butterfly.common.audit.AuditService
 */
package com.z254.butterfly.common.audit;

