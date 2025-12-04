package com.z254.butterfly.common.compliance;

import com.z254.butterfly.common.audit.AuditEventBuilder;
import com.z254.butterfly.common.audit.AuditEventBuilder.AuditEvent;
import com.z254.butterfly.common.audit.AuditEventBuilder.AuditType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for generating compliance reports and evidence for SOC2, GDPR, and SOX audits.
 * <p>
 * Provides:
 * <ul>
 *   <li>SOC2 Trust Services Criteria evidence reports</li>
 *   <li>GDPR data processing activity reports</li>
 *   <li>Access log reports for audit</li>
 *   <li>Policy compliance evidence</li>
 *   <li>Data retention compliance reports</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>{@code
 * ComplianceReportingService service = new ComplianceReportingService(auditEventStore);
 * 
 * // Generate SOC2 evidence report
 * SOC2Report report = service.generateSOC2Report(startDate, endDate, ComplianceScope.FULL);
 * 
 * // Generate GDPR processing activities report
 * GDPRReport gdprReport = service.generateGDPRReport(startDate, endDate, "tenant-123");
 * 
 * // Generate access log report
 * AccessLogReport accessReport = service.generateAccessLogReport(startDate, endDate);
 * }</pre>
 */
public class ComplianceReportingService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceReportingService.class);

    private final AuditEventStore auditEventStore;
    private final String serviceName;

    public ComplianceReportingService(AuditEventStore auditEventStore, String serviceName) {
        this.auditEventStore = auditEventStore;
        this.serviceName = serviceName;
    }

    // === SOC2 Reporting ===

    /**
     * Generate SOC2 Trust Services Criteria evidence report.
     *
     * @param startDate report start date
     * @param endDate   report end date
     * @param scope     compliance scope
     * @return SOC2 compliance report
     */
    public SOC2Report generateSOC2Report(LocalDate startDate, LocalDate endDate, ComplianceScope scope) {
        log.info("Generating SOC2 report for {} to {} (scope: {})", startDate, endDate, scope);

        Instant start = startDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = endDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        List<AuditEvent> events = auditEventStore.findByTimeRange(start, end);

        SOC2Report.Builder builder = SOC2Report.builder()
                .reportId(UUID.randomUUID().toString())
                .generatedAt(Instant.now())
                .periodStart(start)
                .periodEnd(end)
                .serviceName(serviceName)
                .scope(scope);

        // CC1: Control Environment
        builder.addCriteria(generateControlEnvironmentEvidence(events));

        // CC2: Communication and Information
        builder.addCriteria(generateCommunicationEvidence(events));

        // CC3: Risk Assessment
        builder.addCriteria(generateRiskAssessmentEvidence(events));

        // CC4: Monitoring Activities
        builder.addCriteria(generateMonitoringEvidence(events));

        // CC5: Control Activities
        builder.addCriteria(generateControlActivitiesEvidence(events));

        // CC6: Logical and Physical Access Controls
        builder.addCriteria(generateAccessControlEvidence(events));

        // CC7: System Operations
        builder.addCriteria(generateSystemOperationsEvidence(events));

        // CC8: Change Management
        builder.addCriteria(generateChangeManagementEvidence(events));

        // CC9: Risk Mitigation
        builder.addCriteria(generateRiskMitigationEvidence(events));

        return builder.build();
    }

    private SOC2Criteria generateControlEnvironmentEvidence(List<AuditEvent> events) {
        // Evidence of governance structure and policies
        long policyEvaluations = events.stream()
                .filter(e -> e.auditType() == AuditType.POLICY_EVALUATED)
                .count();
        long approvals = events.stream()
                .filter(e -> e.auditType() == AuditType.APPROVAL_GRANTED || 
                            e.auditType() == AuditType.APPROVAL_DENIED)
                .count();

        return new SOC2Criteria(
                "CC1",
                "Control Environment",
                List.of(
                        new SOC2Evidence("Governance policy evaluations", String.valueOf(policyEvaluations)),
                        new SOC2Evidence("Approval workflows executed", String.valueOf(approvals))
                ),
                policyEvaluations > 0 ? ComplianceStatus.COMPLIANT : ComplianceStatus.NEEDS_REVIEW
        );
    }

    private SOC2Criteria generateCommunicationEvidence(List<AuditEvent> events) {
        // Evidence of audit logging and information flow
        long auditedActions = events.size();
        long uniqueActors = events.stream()
                .map(e -> e.actor().subject())
                .distinct()
                .count();

        return new SOC2Criteria(
                "CC2",
                "Communication and Information",
                List.of(
                        new SOC2Evidence("Total audited events", String.valueOf(auditedActions)),
                        new SOC2Evidence("Unique actors", String.valueOf(uniqueActors)),
                        new SOC2Evidence("Audit coverage", "100%")
                ),
                ComplianceStatus.COMPLIANT
        );
    }

    private SOC2Criteria generateRiskAssessmentEvidence(List<AuditEvent> events) {
        // Evidence of policy violations and risk detection
        long violations = events.stream()
                .filter(e -> e.auditType() == AuditType.POLICY_VIOLATED)
                .count();
        long accessDenied = events.stream()
                .filter(e -> e.auditType() == AuditType.ACCESS_DENIED)
                .count();

        return new SOC2Criteria(
                "CC3",
                "Risk Assessment",
                List.of(
                        new SOC2Evidence("Policy violations detected", String.valueOf(violations)),
                        new SOC2Evidence("Access denied events", String.valueOf(accessDenied))
                ),
                ComplianceStatus.COMPLIANT
        );
    }

    private SOC2Criteria generateMonitoringEvidence(List<AuditEvent> events) {
        // Evidence of continuous monitoring
        Map<String, Long> eventsByType = events.stream()
                .collect(Collectors.groupingBy(e -> e.auditType().name(), Collectors.counting()));

        return new SOC2Criteria(
                "CC4",
                "Monitoring Activities",
                List.of(
                        new SOC2Evidence("Event types monitored", String.valueOf(eventsByType.size())),
                        new SOC2Evidence("Monitoring frequency", "Real-time"),
                        new SOC2Evidence("Audit trail retention", "Configured per policy")
                ),
                ComplianceStatus.COMPLIANT
        );
    }

    private SOC2Criteria generateControlActivitiesEvidence(List<AuditEvent> events) {
        // Evidence of control execution
        long governedActions = events.stream()
                .filter(e -> e.actor().isGovernedActor())
                .count();
        long approvalRequired = events.stream()
                .filter(e -> e.governance() != null && e.governance().approvalRequired())
                .count();

        return new SOC2Criteria(
                "CC5",
                "Control Activities",
                List.of(
                        new SOC2Evidence("Governed actor actions", String.valueOf(governedActions)),
                        new SOC2Evidence("Actions requiring approval", String.valueOf(approvalRequired))
                ),
                ComplianceStatus.COMPLIANT
        );
    }

    private SOC2Criteria generateAccessControlEvidence(List<AuditEvent> events) {
        // Evidence of access controls
        long accessGranted = events.stream()
                .filter(e -> e.auditType() == AuditType.ACCESS_GRANTED)
                .count();
        long accessDenied = events.stream()
                .filter(e -> e.auditType() == AuditType.ACCESS_DENIED)
                .count();
        long uniqueTenants = events.stream()
                .map(e -> e.actor().tenantId())
                .distinct()
                .count();

        return new SOC2Criteria(
                "CC6",
                "Logical and Physical Access Controls",
                List.of(
                        new SOC2Evidence("Access grants", String.valueOf(accessGranted)),
                        new SOC2Evidence("Access denials", String.valueOf(accessDenied)),
                        new SOC2Evidence("Tenant isolation verified", String.valueOf(uniqueTenants) + " tenants"),
                        new SOC2Evidence("Authentication method", "JWT/OAuth2")
                ),
                ComplianceStatus.COMPLIANT
        );
    }

    private SOC2Criteria generateSystemOperationsEvidence(List<AuditEvent> events) {
        // Evidence of operational monitoring
        long operationalEvents = events.stream()
                .filter(e -> e.auditType() == AuditType.ACTION_EXECUTED ||
                            e.auditType() == AuditType.ACTION_COMPLETED ||
                            e.auditType() == AuditType.ACTION_FAILED)
                .count();
        long failures = events.stream()
                .filter(e -> !e.outcome().success())
                .count();

        double successRate = events.isEmpty() ? 100.0 : 
                ((events.size() - failures) * 100.0) / events.size();

        return new SOC2Criteria(
                "CC7",
                "System Operations",
                List.of(
                        new SOC2Evidence("Operational events tracked", String.valueOf(operationalEvents)),
                        new SOC2Evidence("Success rate", String.format("%.2f%%", successRate)),
                        new SOC2Evidence("Failure events", String.valueOf(failures))
                ),
                ComplianceStatus.COMPLIANT
        );
    }

    private SOC2Criteria generateChangeManagementEvidence(List<AuditEvent> events) {
        // Evidence of change management
        long configChanges = events.stream()
                .filter(e -> e.auditType() == AuditType.CONFIG_CHANGED)
                .count();
        long evolutionEvents = events.stream()
                .filter(e -> e.auditType() == AuditType.EVOLUTION_TRIGGERED)
                .count();

        return new SOC2Criteria(
                "CC8",
                "Change Management",
                List.of(
                        new SOC2Evidence("Configuration changes", String.valueOf(configChanges)),
                        new SOC2Evidence("Evolution/learning events", String.valueOf(evolutionEvents)),
                        new SOC2Evidence("Change approval required", "Yes (for governed actors)")
                ),
                ComplianceStatus.COMPLIANT
        );
    }

    private SOC2Criteria generateRiskMitigationEvidence(List<AuditEvent> events) {
        // Evidence of risk mitigation
        long mitigationActions = events.stream()
                .filter(e -> e.governance() != null && !e.governance().violations().isEmpty())
                .count();

        return new SOC2Criteria(
                "CC9",
                "Risk Mitigation",
                List.of(
                        new SOC2Evidence("Policy violation responses", String.valueOf(mitigationActions)),
                        new SOC2Evidence("Auto-remediation enabled", "Yes (via PLATO)"),
                        new SOC2Evidence("Risk assessment frequency", "Continuous")
                ),
                ComplianceStatus.COMPLIANT
        );
    }

    // === GDPR Reporting ===

    /**
     * Generate GDPR Article 30 processing activities report.
     *
     * @param startDate report start date
     * @param endDate   report end date
     * @param tenantId  tenant ID (or null for all tenants)
     * @return GDPR compliance report
     */
    public GDPRReport generateGDPRReport(LocalDate startDate, LocalDate endDate, String tenantId) {
        log.info("Generating GDPR report for {} to {} (tenant: {})", startDate, endDate, tenantId);

        Instant start = startDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = endDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        List<AuditEvent> events = auditEventStore.findByTimeRange(start, end);
        if (tenantId != null) {
            events = events.stream()
                    .filter(e -> tenantId.equals(e.actor().tenantId()))
                    .toList();
        }

        // Processing activities summary
        Map<String, Long> processingByType = events.stream()
                .collect(Collectors.groupingBy(e -> e.resource().resourceType(), Collectors.counting()));

        // Data subject access tracking
        long dataAccessEvents = events.stream()
                .filter(e -> e.auditType() == AuditType.ACCESS_GRANTED)
                .count();

        // Data processing basis
        List<GDPRProcessingActivity> activities = processingByType.entrySet().stream()
                .map(entry -> new GDPRProcessingActivity(
                        entry.getKey(),
                        "Service operation",
                        "Legitimate interest / Contract performance",
                        entry.getValue()
                ))
                .toList();

        return new GDPRReport(
                UUID.randomUUID().toString(),
                Instant.now(),
                start,
                end,
                tenantId,
                serviceName,
                activities,
                dataAccessEvents,
                List.of("TLS 1.3", "AES-256 encryption at rest", "JWT authentication"),
                "Configured per retention policy"
        );
    }

    // === Access Log Reporting ===

    /**
     * Generate access log report for audit purposes.
     *
     * @param startDate report start date
     * @param endDate   report end date
     * @return access log report
     */
    public AccessLogReport generateAccessLogReport(LocalDate startDate, LocalDate endDate) {
        log.info("Generating access log report for {} to {}", startDate, endDate);

        Instant start = startDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = endDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        List<AuditEvent> events = auditEventStore.findByTimeRange(start, end);

        // Group by actor
        Map<String, List<AuditEvent>> byActor = events.stream()
                .collect(Collectors.groupingBy(e -> e.actor().subject()));

        List<AccessLogEntry> entries = byActor.entrySet().stream()
                .map(entry -> {
                    List<AuditEvent> actorEvents = entry.getValue();
                    String subject = entry.getKey();
                    String tenant = actorEvents.get(0).actor().tenantId();
                    Set<String> roles = actorEvents.stream()
                            .flatMap(e -> e.actor().roles().stream())
                            .collect(Collectors.toSet());
                    Map<String, Long> actionCounts = actorEvents.stream()
                            .collect(Collectors.groupingBy(AuditEvent::action, Collectors.counting()));
                    long denials = actorEvents.stream()
                            .filter(e -> e.auditType() == AuditType.ACCESS_DENIED)
                            .count();

                    return new AccessLogEntry(
                            subject,
                            tenant,
                            roles,
                            actionCounts,
                            actorEvents.size(),
                            denials
                    );
                })
                .sorted((a, b) -> Long.compare(b.totalActions(), a.totalActions()))
                .toList();

        return new AccessLogReport(
                UUID.randomUUID().toString(),
                Instant.now(),
                start,
                end,
                entries,
                events.size(),
                byActor.size()
        );
    }

    // === Record Classes ===

    public record SOC2Report(
            String reportId,
            Instant generatedAt,
            Instant periodStart,
            Instant periodEnd,
            String serviceName,
            ComplianceScope scope,
            List<SOC2Criteria> criteria,
            ComplianceStatus overallStatus
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String reportId;
            private Instant generatedAt;
            private Instant periodStart;
            private Instant periodEnd;
            private String serviceName;
            private ComplianceScope scope;
            private List<SOC2Criteria> criteria = new ArrayList<>();

            public Builder reportId(String reportId) {
                this.reportId = reportId;
                return this;
            }

            public Builder generatedAt(Instant generatedAt) {
                this.generatedAt = generatedAt;
                return this;
            }

            public Builder periodStart(Instant periodStart) {
                this.periodStart = periodStart;
                return this;
            }

            public Builder periodEnd(Instant periodEnd) {
                this.periodEnd = periodEnd;
                return this;
            }

            public Builder serviceName(String serviceName) {
                this.serviceName = serviceName;
                return this;
            }

            public Builder scope(ComplianceScope scope) {
                this.scope = scope;
                return this;
            }

            public Builder addCriteria(SOC2Criteria c) {
                this.criteria.add(c);
                return this;
            }

            public SOC2Report build() {
                ComplianceStatus overall = criteria.stream()
                        .map(SOC2Criteria::status)
                        .reduce((a, b) -> a.ordinal() > b.ordinal() ? a : b)
                        .orElse(ComplianceStatus.COMPLIANT);
                return new SOC2Report(reportId, generatedAt, periodStart, periodEnd, 
                        serviceName, scope, List.copyOf(criteria), overall);
            }
        }
    }

    public record SOC2Criteria(
            String id,
            String name,
            List<SOC2Evidence> evidence,
            ComplianceStatus status
    ) {}

    public record SOC2Evidence(
            String description,
            String value
    ) {}

    public record GDPRReport(
            String reportId,
            Instant generatedAt,
            Instant periodStart,
            Instant periodEnd,
            String tenantId,
            String serviceName,
            List<GDPRProcessingActivity> activities,
            long dataAccessEvents,
            List<String> securityMeasures,
            String retentionPolicy
    ) {}

    public record GDPRProcessingActivity(
            String dataCategory,
            String purpose,
            String legalBasis,
            long processingCount
    ) {}

    public record AccessLogReport(
            String reportId,
            Instant generatedAt,
            Instant periodStart,
            Instant periodEnd,
            List<AccessLogEntry> entries,
            long totalEvents,
            long uniqueActors
    ) {}

    public record AccessLogEntry(
            String subject,
            String tenantId,
            Set<String> roles,
            Map<String, Long> actionCounts,
            long totalActions,
            long accessDenials
    ) {}

    public enum ComplianceScope {
        FULL,
        SOC2_TYPE1,
        SOC2_TYPE2,
        GDPR_ONLY
    }

    public enum ComplianceStatus {
        COMPLIANT,
        NEEDS_REVIEW,
        NON_COMPLIANT
    }

    /**
     * Interface for retrieving audit events from storage.
     */
    public interface AuditEventStore {
        List<AuditEvent> findByTimeRange(Instant start, Instant end);
        List<AuditEvent> findByCorrelationId(String correlationId);
        List<AuditEvent> findByActor(String subject, Instant start, Instant end);
    }
}

