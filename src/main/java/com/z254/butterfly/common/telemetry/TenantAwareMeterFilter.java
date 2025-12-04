package com.z254.butterfly.common.telemetry;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Micrometer MeterFilter that automatically injects tenant context tags
 * into all metrics.
 * 
 * <p>This filter reads from {@link TenantContextHolder} and adds tenant-aware
 * tags to enable per-tenant dashboards, SLOs, and alerting.
 * 
 * <h2>Added Tags</h2>
 * <ul>
 *   <li>{@code tenantId} - From TenantContextHolder (defaults to "unknown")</li>
 *   <li>{@code service} - Service name from configuration</li>
 *   <li>{@code environment} - Environment from configuration</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * <pre>{@code
 * @Configuration
 * public class MetricsConfig {
 *     @Bean
 *     public MeterFilter tenantAwareMeterFilter() {
 *         return new TenantAwareMeterFilter("capsule-service", "prod");
 *     }
 * }
 * }</pre>
 * 
 * <h2>Metric Filtering</h2>
 * <p>The filter only adds tenant tags to application metrics (those starting
 * with service-specific prefixes). JVM and system metrics are excluded to
 * avoid high cardinality issues.
 * 
 * @see TenantContextHolder
 * @see TelemetryTagNames
 */
public class TenantAwareMeterFilter implements MeterFilter {

    private final String serviceName;
    private final String environment;
    private final Set<String> excludedPrefixes;

    /**
     * Creates a tenant-aware meter filter.
     *
     * @param serviceName the service name to add as a tag
     * @param environment the environment (dev, staging, prod)
     */
    public TenantAwareMeterFilter(String serviceName, String environment) {
        this(serviceName, environment, Set.of(
            "jvm.", "process.", "system.", "tomcat.", "hikaricp.",
            "jdbc.", "logback.", "disk.", "executor."
        ));
    }

    /**
     * Creates a tenant-aware meter filter with custom exclusions.
     *
     * @param serviceName the service name to add as a tag
     * @param environment the environment (dev, staging, prod)
     * @param excludedPrefixes metric prefixes to exclude from tenant tagging
     */
    public TenantAwareMeterFilter(String serviceName, String environment, Set<String> excludedPrefixes) {
        this.serviceName = serviceName;
        this.environment = environment;
        this.excludedPrefixes = excludedPrefixes;
    }

    @Override
    public MeterFilterReply accept(Meter.Id id) {
        // Accept all metrics
        return MeterFilterReply.NEUTRAL;
    }

    @Override
    public Meter.Id map(Meter.Id id) {
        // Skip excluded prefixes to avoid high cardinality on system metrics
        if (shouldExclude(id.getName())) {
            return id;
        }

        List<Tag> tags = new ArrayList<>(id.getTags());

        // Add service and environment tags
        addTagIfMissing(tags, TelemetryTagNames.SERVICE, serviceName);
        addTagIfMissing(tags, TelemetryTagNames.ENVIRONMENT, environment);

        // Add tenant context if available
        TenantContextHolder.getTenantId().ifPresent(tenantId ->
            addTagIfMissing(tags, TelemetryTagNames.TENANT_ID, tenantId)
        );

        // Add route context if available
        TenantContextHolder.getRouteId().ifPresent(routeId ->
            addTagIfMissing(tags, TelemetryTagNames.ROUTE_ID, routeId)
        );

        TenantContextHolder.getSourceId().ifPresent(sourceId ->
            addTagIfMissing(tags, TelemetryTagNames.SOURCE_ID, sourceId)
        );

        return id.replaceTags(tags);
    }

    /**
     * Checks if the metric should be excluded from tenant tagging.
     *
     * @param metricName the metric name
     * @return true if the metric should be excluded
     */
    private boolean shouldExclude(String metricName) {
        for (String prefix : excludedPrefixes) {
            if (metricName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds a tag if it doesn't already exist.
     *
     * @param tags the list of tags
     * @param key the tag key
     * @param value the tag value
     */
    private void addTagIfMissing(List<Tag> tags, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        
        boolean exists = tags.stream()
            .anyMatch(tag -> tag.getKey().equals(key));
        
        if (!exists) {
            tags.add(Tag.of(key, value));
        }
    }
}

