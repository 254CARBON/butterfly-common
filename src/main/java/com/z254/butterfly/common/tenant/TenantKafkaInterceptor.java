package com.z254.butterfly.common.tenant;

import com.z254.butterfly.common.telemetry.TenantContextHolder;
import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Kafka interceptors for tenant context propagation across service boundaries.
 * <p>
 * These interceptors ensure tenant context is automatically propagated through
 * Kafka messages, enabling tenant-aware processing in event-driven architectures.
 *
 * <h2>Producer Interceptor</h2>
 * Adds tenant context headers to outgoing messages:
 * <ul>
 *   <li>{@code x-tenant-id} - Tenant identifier</li>
 *   <li>{@code x-correlation-id} - Request correlation ID</li>
 *   <li>{@code x-request-id} - Unique request identifier</li>
 * </ul>
 *
 * <h2>Consumer Interceptor</h2>
 * Extracts tenant context from incoming message headers and sets it on
 * {@link TenantContextHolder} for the processing thread.
 *
 * <h2>Configuration</h2>
 * <pre>{@code
 * # Producer config
 * spring.kafka.producer.properties.interceptor.classes=\
 *   com.z254.butterfly.common.tenant.TenantKafkaInterceptor$TenantProducerInterceptor
 *
 * # Consumer config
 * spring.kafka.consumer.properties.interceptor.classes=\
 *   com.z254.butterfly.common.tenant.TenantKafkaInterceptor$TenantConsumerInterceptor
 * }</pre>
 *
 * @see TenantContextHolder
 */
public final class TenantKafkaInterceptor {

    private TenantKafkaInterceptor() {
        // Container class for interceptors
    }

    // Header names
    public static final String HEADER_TENANT_ID = "x-tenant-id";
    public static final String HEADER_CORRELATION_ID = "x-correlation-id";
    public static final String HEADER_REQUEST_ID = "x-request-id";
    public static final String HEADER_SOURCE_SERVICE = "x-source-service";

    /**
     * Producer interceptor that adds tenant context headers to outgoing messages.
     *
     * @param <K> key type
     * @param <V> value type
     */
    public static class TenantProducerInterceptor<K, V> implements ProducerInterceptor<K, V> {

        private static final Logger log = LoggerFactory.getLogger(TenantProducerInterceptor.class);

        private String sourceService = "unknown";

        @Override
        public void configure(Map<String, ?> configs) {
            Object serviceName = configs.get("butterfly.service.name");
            if (serviceName != null) {
                this.sourceService = serviceName.toString();
            }
        }

        @Override
        public ProducerRecord<K, V> onSend(ProducerRecord<K, V> record) {
            Headers headers = record.headers();

            // Add tenant ID
            TenantContextHolder.getTenantId().ifPresent(tenantId -> {
                if (getHeader(headers, HEADER_TENANT_ID) == null) {
                    headers.add(HEADER_TENANT_ID, tenantId.getBytes(StandardCharsets.UTF_8));
                }
            });

            // Add correlation ID
            TenantContextHolder.getCorrelationId().ifPresent(correlationId -> {
                if (getHeader(headers, HEADER_CORRELATION_ID) == null) {
                    headers.add(HEADER_CORRELATION_ID, correlationId.getBytes(StandardCharsets.UTF_8));
                }
            });

            // Add request ID
            TenantContextHolder.getRequestId().ifPresent(requestId -> {
                if (getHeader(headers, HEADER_REQUEST_ID) == null) {
                    headers.add(HEADER_REQUEST_ID, requestId.getBytes(StandardCharsets.UTF_8));
                }
            });

            // Add source service
            if (getHeader(headers, HEADER_SOURCE_SERVICE) == null) {
                headers.add(HEADER_SOURCE_SERVICE, sourceService.getBytes(StandardCharsets.UTF_8));
            }

            log.trace("Added tenant headers to message for topic: {}, tenant: {}",
                    record.topic(), TenantContextHolder.getTenantIdOrDefault("unknown"));

            return record;
        }

        @Override
        public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
            // No action needed
        }

        @Override
        public void close() {
            // No resources to close
        }

        private String getHeader(Headers headers, String name) {
            Header header = headers.lastHeader(name);
            return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
        }
    }

    /**
     * Consumer interceptor that extracts tenant context from incoming messages.
     *
     * @param <K> key type
     * @param <V> value type
     */
    public static class TenantConsumerInterceptor<K, V> implements ConsumerInterceptor<K, V> {

        private static final Logger log = LoggerFactory.getLogger(TenantConsumerInterceptor.class);

        private String defaultTenantId = "default";

        @Override
        public void configure(Map<String, ?> configs) {
            Object defaultTenant = configs.get("butterfly.tenant.default-tenant-id");
            if (defaultTenant != null) {
                this.defaultTenantId = defaultTenant.toString();
            }
        }

        @Override
        public ConsumerRecords<K, V> onConsume(ConsumerRecords<K, V> records) {
            // Note: For batch processing, the last record's tenant context will be set.
            // For proper multi-tenant batch processing, tenant context should be
            // extracted per-record during message handling.
            records.forEach(record -> {
                Headers headers = record.headers();

                // Extract tenant ID
                String tenantId = getHeader(headers, HEADER_TENANT_ID);
                if (tenantId != null && !tenantId.isBlank()) {
                    TenantContextHolder.setTenantId(tenantId);
                } else {
                    TenantContextHolder.setTenantId(defaultTenantId);
                }

                // Extract correlation ID
                String correlationId = getHeader(headers, HEADER_CORRELATION_ID);
                if (correlationId != null) {
                    TenantContextHolder.setCorrelationId(correlationId);
                }

                // Extract request ID
                String requestId = getHeader(headers, HEADER_REQUEST_ID);
                if (requestId != null) {
                    TenantContextHolder.setRequestId(requestId);
                }

                log.trace("Extracted tenant context from message: topic={}, partition={}, tenant={}",
                        record.topic(), record.partition(), tenantId);
            });

            return records;
        }

        @Override
        public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {
            // No action needed
        }

        @Override
        public void close() {
            // No resources to close
        }

        private String getHeader(Headers headers, String name) {
            Header header = headers.lastHeader(name);
            return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
        }
    }

    /**
     * Helper class for programmatic tenant header operations.
     */
    public static class TenantHeaders {

        /**
         * Extract tenant ID from Kafka headers.
         *
         * @param headers Kafka message headers
         * @return tenant ID or null if not present
         */
        public static String extractTenantId(Headers headers) {
            Header header = headers.lastHeader(HEADER_TENANT_ID);
            return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
        }

        /**
         * Extract correlation ID from Kafka headers.
         *
         * @param headers Kafka message headers
         * @return correlation ID or null if not present
         */
        public static String extractCorrelationId(Headers headers) {
            Header header = headers.lastHeader(HEADER_CORRELATION_ID);
            return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
        }

        /**
         * Add tenant context headers to a producer record.
         *
         * @param headers Kafka message headers
         * @param tenantId tenant identifier
         * @param correlationId correlation identifier
         */
        public static void addTenantHeaders(Headers headers, String tenantId, String correlationId) {
            if (tenantId != null) {
                headers.add(HEADER_TENANT_ID, tenantId.getBytes(StandardCharsets.UTF_8));
            }
            if (correlationId != null) {
                headers.add(HEADER_CORRELATION_ID, correlationId.getBytes(StandardCharsets.UTF_8));
            }
        }

        /**
         * Add tenant context from current TenantContextHolder to headers.
         *
         * @param headers Kafka message headers
         */
        public static void addCurrentTenantHeaders(Headers headers) {
            TenantContextHolder.getTenantId().ifPresent(tenantId ->
                    headers.add(HEADER_TENANT_ID, tenantId.getBytes(StandardCharsets.UTF_8)));
            TenantContextHolder.getCorrelationId().ifPresent(correlationId ->
                    headers.add(HEADER_CORRELATION_ID, correlationId.getBytes(StandardCharsets.UTF_8)));
            TenantContextHolder.getRequestId().ifPresent(requestId ->
                    headers.add(HEADER_REQUEST_ID, requestId.getBytes(StandardCharsets.UTF_8)));
        }
    }
}

