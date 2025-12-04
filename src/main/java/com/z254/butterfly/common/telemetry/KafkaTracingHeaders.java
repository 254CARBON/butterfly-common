package com.z254.butterfly.common.telemetry;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Utility class for propagating trace context through Kafka message headers.
 * 
 * <p>This enables correlation of traces across async message-based flows,
 * allowing end-to-end visibility from ingestion through processing.
 * 
 * <h2>Supported Headers</h2>
 * <ul>
 *   <li>{@code X-Correlation-ID} - Business process correlation</li>
 *   <li>{@code X-Tenant-ID} - Tenant identifier</li>
 *   <li>{@code X-Route-ID} - Connector route identifier</li>
 *   <li>{@code X-Source-ID} - Data source identifier</li>
 *   <li>{@code traceparent} - W3C Trace Context</li>
 *   <li>{@code tracestate} - W3C Trace State</li>
 * </ul>
 * 
 * <h2>Usage - Producer</h2>
 * <pre>{@code
 * ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, value);
 * KafkaTracingHeaders.injectContext(record);
 * kafkaTemplate.send(record);
 * }</pre>
 * 
 * <h2>Usage - Consumer</h2>
 * <pre>{@code
 * @KafkaListener(topics = "my-topic")
 * public void consume(ConsumerRecord<String, Object> record) {
 *     KafkaTracingHeaders.extractContext(record);
 *     try {
 *         // Process message - TenantContextHolder is populated
 *         processMessage(record.value());
 *     } finally {
 *         TenantContextHolder.clear();
 *     }
 * }
 * }</pre>
 * 
 * @see TenantContextHolder
 */
public final class KafkaTracingHeaders {

    /**
     * Kafka header for correlation ID.
     */
    public static final String HEADER_CORRELATION_ID = "X-Correlation-ID";

    /**
     * Kafka header for tenant ID.
     */
    public static final String HEADER_TENANT_ID = "X-Tenant-ID";

    /**
     * Kafka header for route ID.
     */
    public static final String HEADER_ROUTE_ID = "X-Route-ID";

    /**
     * Kafka header for source ID.
     */
    public static final String HEADER_SOURCE_ID = "X-Source-ID";

    /**
     * W3C Trace Context traceparent header.
     */
    public static final String HEADER_TRACEPARENT = "traceparent";

    /**
     * W3C Trace Context tracestate header.
     */
    public static final String HEADER_TRACESTATE = "tracestate";

    private KafkaTracingHeaders() {
        // utility class
    }

    /**
     * Injects the current tenant context into Kafka message headers.
     *
     * @param record the producer record to inject headers into
     * @param <K> the key type
     * @param <V> the value type
     */
    public static <K, V> void injectContext(ProducerRecord<K, V> record) {
        Headers headers = record.headers();

        TenantContextHolder.getCorrelationId().ifPresent(correlationId ->
            addHeader(headers, HEADER_CORRELATION_ID, correlationId)
        );

        TenantContextHolder.getTenantId().ifPresent(tenantId ->
            addHeader(headers, HEADER_TENANT_ID, tenantId)
        );

        TenantContextHolder.getRouteId().ifPresent(routeId ->
            addHeader(headers, HEADER_ROUTE_ID, routeId)
        );

        TenantContextHolder.getSourceId().ifPresent(sourceId ->
            addHeader(headers, HEADER_SOURCE_ID, sourceId)
        );
    }

    /**
     * Extracts tenant context from Kafka message headers and populates
     * the TenantContextHolder.
     *
     * @param record the consumer record to extract headers from
     * @param <K> the key type
     * @param <V> the value type
     */
    public static <K, V> void extractContext(ConsumerRecord<K, V> record) {
        Headers headers = record.headers();

        getHeader(headers, HEADER_CORRELATION_ID)
            .ifPresent(TenantContextHolder::setCorrelationId);

        getHeader(headers, HEADER_TENANT_ID)
            .ifPresent(TenantContextHolder::setTenantId);

        getHeader(headers, HEADER_ROUTE_ID)
            .ifPresent(TenantContextHolder::setRouteId);

        getHeader(headers, HEADER_SOURCE_ID)
            .ifPresent(TenantContextHolder::setSourceId);
    }

    /**
     * Gets a header value from Kafka headers.
     *
     * @param headers the Kafka headers
     * @param key the header key
     * @return the header value, or empty if not present
     */
    public static Optional<String> getHeader(Headers headers, String key) {
        Header header = headers.lastHeader(key);
        if (header == null || header.value() == null) {
            return Optional.empty();
        }
        return Optional.of(new String(header.value(), StandardCharsets.UTF_8));
    }

    /**
     * Adds a header to Kafka headers if the value is not null or blank.
     *
     * @param headers the Kafka headers
     * @param key the header key
     * @param value the header value
     */
    public static void addHeader(Headers headers, String key, String value) {
        if (value != null && !value.isBlank()) {
            headers.remove(key); // Remove existing to avoid duplicates
            headers.add(key, value.getBytes(StandardCharsets.UTF_8));
        }
    }
}

