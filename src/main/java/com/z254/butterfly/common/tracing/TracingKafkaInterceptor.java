package com.z254.butterfly.common.tracing;

import com.z254.butterfly.common.kafka.UnifiedEventHeader;
import com.z254.butterfly.common.telemetry.KafkaTracingHeaders;
import com.z254.butterfly.common.telemetry.TenantContextHolder;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Kafka interceptors for propagating trace context through message headers.
 * 
 * <p>This enables distributed tracing across Kafka-based message flows,
 * maintaining trace continuity from producers through consumers.
 * 
 * <h2>Configuration</h2>
 * <pre>{@code
 * # application.properties
 * spring.kafka.producer.properties.interceptor.classes=\
 *     com.z254.butterfly.common.tracing.TracingKafkaInterceptor$TracingProducerInterceptor
 * spring.kafka.consumer.properties.interceptor.classes=\
 *     com.z254.butterfly.common.tracing.TracingKafkaInterceptor$TracingConsumerInterceptor
 * }</pre>
 * 
 * <h2>W3C Trace Context Headers</h2>
 * <ul>
 *   <li>{@code traceparent} - Required context header (version-traceid-spanid-flags)</li>
 *   <li>{@code tracestate} - Optional vendor-specific trace state</li>
 * </ul>
 * 
 * @see KafkaTracingHeaders
 */
public class TracingKafkaInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TracingKafkaInterceptor.class);

    private TracingKafkaInterceptor() {
        // Utility class containing nested interceptor classes
    }

    /**
     * Producer interceptor that injects trace context into outgoing messages.
     */
    public static class TracingProducerInterceptor<K, V> implements ProducerInterceptor<K, V> {

        private static Tracer tracer;
        private static Propagator propagator;
        private String serviceName = "unknown";

        /**
         * Set the Micrometer tracer (called from auto-configuration).
         */
        public static void setTracer(Tracer tracer) {
            TracingProducerInterceptor.tracer = tracer;
        }

        /**
         * Set the W3C context propagator.
         */
        public static void setPropagator(Propagator propagator) {
            TracingProducerInterceptor.propagator = propagator;
        }

        @Override
        public void configure(Map<String, ?> configs) {
            Object name = configs.get("butterfly.service.name");
            if (name != null) {
                this.serviceName = name.toString();
            }
            log.debug("TracingProducerInterceptor configured for service: {}", serviceName);
        }

        @Override
        public ProducerRecord<K, V> onSend(ProducerRecord<K, V> record) {
            // Inject tenant context
            KafkaTracingHeaders.injectContext(record);

            // Inject trace context using W3C format
            injectTraceContext(record.headers());

            // Inject UnifiedEventHeader from context if present
            injectUnifiedEventHeader(record.headers());

            log.trace("Injected trace context into message for topic: {}", record.topic());
            return record;
        }

        @Override
        public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
            if (exception != null) {
                log.debug("Producer message failed: topic={}, partition={}", 
                        metadata != null ? metadata.topic() : "unknown",
                        metadata != null ? metadata.partition() : -1);
            }
        }

        @Override
        public void close() {
            // No resources to clean up
        }

        private void injectTraceContext(Headers headers) {
            if (tracer == null) {
                return;
            }

            Span currentSpan = tracer.currentSpan();
            if (currentSpan == null) {
                return;
            }

            // Create traceparent header manually in W3C format
            String traceId = currentSpan.context().traceId();
            String spanId = currentSpan.context().spanId();
            String traceparent = String.format("00-%s-%s-01", traceId, spanId);
            
            KafkaTracingHeaders.addHeader(headers, KafkaTracingHeaders.HEADER_TRACEPARENT, traceparent);
            
            // Add optional service identifier in tracestate
            String tracestate = String.format("butterfly=%s", serviceName);
            KafkaTracingHeaders.addHeader(headers, KafkaTracingHeaders.HEADER_TRACESTATE, tracestate);
        }

        /**
         * Inject UnifiedEventHeader from the current context into Kafka headers.
         */
        private void injectUnifiedEventHeader(Headers headers) {
            UnifiedEventHeader eventHeader = UnifiedEventContext.current();
            if (eventHeader == null) {
                log.trace("No UnifiedEventHeader in context, skipping injection");
                return;
            }

            // Inject all header fields
            Map<String, byte[]> headerBytes = eventHeader.toHeaderBytes();
            for (Map.Entry<String, byte[]> entry : headerBytes.entrySet()) {
                // Avoid duplicate headers - remove existing first
                headers.remove(entry.getKey());
                headers.add(entry.getKey(), entry.getValue());
            }

            log.trace("Injected UnifiedEventHeader: correlationId={}, originSystem={}, primaryRimNode={}",
                eventHeader.correlationId(), eventHeader.originSystem(),
                eventHeader.primaryRimNode() != null ? eventHeader.primaryRimNode().toString() : "null");
        }
    }

    /**
     * Consumer interceptor that extracts trace context from incoming messages.
     */
    public static class TracingConsumerInterceptor<K, V> implements ConsumerInterceptor<K, V> {

        private static Tracer tracer;

        /**
         * Set the Micrometer tracer (called from auto-configuration).
         */
        public static void setTracer(Tracer tracer) {
            TracingConsumerInterceptor.tracer = tracer;
        }

        @Override
        public void configure(Map<String, ?> configs) {
            log.debug("TracingConsumerInterceptor configured");
        }

        @Override
        public ConsumerRecords<K, V> onConsume(ConsumerRecords<K, V> records) {
            for (ConsumerRecord<K, V> record : records) {
                // Extract tenant context
                KafkaTracingHeaders.extractContext(record);

                // Extract and continue trace context
                extractAndContinueTrace(record);

                // Extract UnifiedEventHeader and set in context
                extractUnifiedEventHeader(record);
            }
            return records;
        }

        @Override
        public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {
            // No action needed on commit
        }

        @Override
        public void close() {
            // No resources to clean up
        }

        private void extractAndContinueTrace(ConsumerRecord<K, V> record) {
            // Get traceparent header
            var traceparent = KafkaTracingHeaders.getHeader(
                    record.headers(), 
                    KafkaTracingHeaders.HEADER_TRACEPARENT
            );

            if (traceparent.isEmpty()) {
                log.trace("No traceparent header found in message from topic: {}", record.topic());
                return;
            }

            // Parse W3C traceparent format: 00-traceid-spanid-flags
            String[] parts = traceparent.get().split("-");
            if (parts.length >= 4) {
                String traceId = parts[1];
                String parentSpanId = parts[2];
                
                // Set trace context for downstream processing
                // The actual span creation is handled by the consumer's business logic
                // or by Micrometer's auto-instrumentation
                TenantContextHolder.setRequestId(parentSpanId);
                
                log.trace("Extracted trace context: traceId={}, spanId={} from topic: {}",
                        traceId, parentSpanId, record.topic());
            }
        }

        /**
         * Extract UnifiedEventHeader from Kafka message headers and set in context.
         */
        private void extractUnifiedEventHeader(ConsumerRecord<K, V> record) {
            try {
                // Check if correlation_id header is present (required for UnifiedEventHeader)
                var correlationIdHeader = record.headers().lastHeader("correlation_id");
                if (correlationIdHeader == null) {
                    log.trace("No correlation_id header found in message from topic: {}, skipping UnifiedEventHeader extraction", 
                        record.topic());
                    return;
                }

                // Parse the full header from Kafka headers
                UnifiedEventHeader header = UnifiedEventHeader.fromHeaders(record.headers());
                
                // Set in thread-local context
                UnifiedEventContext.set(header);
                
                log.trace("Extracted UnifiedEventHeader: correlationId={}, originSystem={}, primaryRimNode={} from topic: {}",
                    header.correlationId(), header.originSystem(),
                    header.primaryRimNode() != null ? header.primaryRimNode().toString() : "null",
                    record.topic());

            } catch (Exception e) {
                log.debug("Failed to extract UnifiedEventHeader from message: topic={}, error={}",
                    record.topic(), e.getMessage());
            }
        }
    }
}

