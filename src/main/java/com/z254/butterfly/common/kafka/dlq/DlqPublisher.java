package com.z254.butterfly.common.kafka.dlq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.core.KafkaTemplate;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes failed messages to a Dead Letter Queue (DLQ) topic.
 * <p>
 * This publisher captures comprehensive context about the failure and routes
 * messages to topic-specific DLQ topics following the pattern: {original-topic}.dlq
 * <p>
 * Features:
 * <ul>
 *     <li>Automatic DLQ topic naming based on source topic</li>
 *     <li>Full error context capture (exception, stack trace, timestamps)</li>
 *     <li>Metrics emission for monitoring</li>
 *     <li>Header preservation from original message</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>{@code
 * @Autowired DlqPublisher dlqPublisher;
 * 
 * try {
 *     processMessage(record);
 * } catch (Exception e) {
 *     dlqPublisher.publishToDlq(record, e, "my-service");
 * }
 * }</pre>
 */
@Slf4j
public class DlqPublisher {
    
    private static final String DLQ_SUFFIX = ".dlq";
    private static final int MAX_STACK_TRACE_LENGTH = 4000;
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final String applicationName;
    private final ObjectMapper objectMapper;
    
    private final Counter dlqPublishSuccessCounter;
    private final Counter dlqPublishFailureCounter;
    
    /**
     * Create a new DLQ publisher with default ObjectMapper configuration.
     *
     * @param kafkaTemplate   Kafka template for publishing DLQ messages
     * @param meterRegistry   meter registry for metrics
     * @param applicationName name of the application publishing to DLQ
     */
    public DlqPublisher(KafkaTemplate<String, String> kafkaTemplate,
                        MeterRegistry meterRegistry,
                        String applicationName) {
        this(kafkaTemplate, meterRegistry, applicationName, createDefaultObjectMapper());
    }
    
    /**
     * Create a new DLQ publisher with custom ObjectMapper.
     *
     * @param kafkaTemplate   Kafka template for publishing DLQ messages
     * @param meterRegistry   meter registry for metrics
     * @param applicationName name of the application publishing to DLQ
     * @param objectMapper    custom ObjectMapper for JSON serialization
     */
    public DlqPublisher(KafkaTemplate<String, String> kafkaTemplate,
                        MeterRegistry meterRegistry,
                        String applicationName,
                        ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
        this.applicationName = applicationName;
        this.objectMapper = objectMapper;
        
        this.dlqPublishSuccessCounter = meterRegistry.counter("dlq.publish.success");
        this.dlqPublishFailureCounter = meterRegistry.counter("dlq.publish.failure");
    }
    
    /**
     * Creates a default ObjectMapper configured for DLQ serialization.
     */
    private static ObjectMapper createDefaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        return mapper;
    }
    
    /**
     * Publish a failed message to the DLQ.
     *
     * @param record        the original Kafka consumer record that failed
     * @param exception     the exception that caused the failure
     * @param consumerGroup the consumer group that was processing the message
     * @return the DLQ record that was published
     */
    public DlqRecord publishToDlq(ConsumerRecord<String, String> record, 
                                   Exception exception,
                                   String consumerGroup) {
        
        String dlqTopic = resolveDlqTopic(record.topic());
        
        DlqRecord dlqRecord = buildDlqRecord(record, exception, consumerGroup);
        
        try {
            String dlqPayload = serializeDlqRecord(dlqRecord);
            
            ProducerRecord<String, String> producerRecord = new ProducerRecord<>(
                dlqTopic,
                null, // partition - let Kafka decide
                record.key(),
                dlqPayload,
                buildDlqHeaders(dlqRecord)
            );
            
            kafkaTemplate.send(producerRecord)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish to DLQ topic {}: {}", dlqTopic, ex.getMessage());
                        dlqPublishFailureCounter.increment();
                        meterRegistry.counter("dlq.publish.failure", 
                            "topic", record.topic(),
                            "error_type", exception.getClass().getSimpleName()
                        ).increment();
                    } else {
                        log.info("Published failed message to DLQ: topic={}, offset={}, dlqId={}", 
                            record.topic(), record.offset(), dlqRecord.getId());
                        dlqPublishSuccessCounter.increment();
                        meterRegistry.counter("dlq.publish.success",
                            "topic", record.topic(),
                            "failure_category", dlqRecord.getFailureCategory().name()
                        ).increment();
                    }
                });
            
            return dlqRecord;
            
        } catch (Exception e) {
            log.error("Failed to serialize/publish DLQ record for topic {} offset {}: {}",
                record.topic(), record.offset(), e.getMessage());
            dlqPublishFailureCounter.increment();
            throw new DlqPublishException("Failed to publish to DLQ", e);
        }
    }
    
    /**
     * Publish a failed message with raw payload (for cases where deserialization failed).
     *
     * @param topic         the original topic
     * @param partition     the partition
     * @param offset        the offset
     * @param key           the message key
     * @param rawPayload    the raw payload bytes
     * @param exception     the exception that caused the failure
     * @param consumerGroup the consumer group
     * @return the DLQ record
     */
    public DlqRecord publishToDlqRaw(String topic,
                                      int partition,
                                      long offset,
                                      String key,
                                      byte[] rawPayload,
                                      Exception exception,
                                      String consumerGroup) {
        
        String payload = rawPayload != null 
            ? new String(rawPayload, StandardCharsets.UTF_8) 
            : null;
        
        DlqRecord dlqRecord = DlqRecord.builder()
            .id(UUID.randomUUID().toString())
            .originalTopic(topic)
            .partition(partition)
            .offset(offset)
            .key(key)
            .payload(payload)
            .originalTimestamp(Instant.now())
            .dlqTimestamp(Instant.now())
            .errorType(exception.getClass().getName())
            .errorMessage(exception.getMessage())
            .stackTrace(truncateStackTrace(exception))
            .consumerGroupId(consumerGroup)
            .sourceService(applicationName)
            .failureCategory(categorizeFailure(exception))
            .build();
        
        String dlqTopic = resolveDlqTopic(topic);
        
        try {
            String dlqPayload = serializeDlqRecord(dlqRecord);
            kafkaTemplate.send(dlqTopic, key, dlqPayload);
            
            log.info("Published raw failed message to DLQ: topic={}, offset={}, dlqId={}",
                topic, offset, dlqRecord.getId());
            dlqPublishSuccessCounter.increment();
            
            return dlqRecord;
        } catch (Exception e) {
            log.error("Failed to publish raw message to DLQ: {}", e.getMessage());
            dlqPublishFailureCounter.increment();
            throw new DlqPublishException("Failed to publish raw message to DLQ", e);
        }
    }
    
    /**
     * Resolve the DLQ topic name for a given source topic.
     */
    public static String resolveDlqTopic(String sourceTopic) {
        return sourceTopic + DLQ_SUFFIX;
    }
    
    private DlqRecord buildDlqRecord(ConsumerRecord<String, String> record, 
                                      Exception exception,
                                      String consumerGroup) {
        return DlqRecord.builder()
            .id(UUID.randomUUID().toString())
            .originalTopic(record.topic())
            .partition(record.partition())
            .offset(record.offset())
            .key(record.key())
            .payload(record.value())
            .headers(extractHeaders(record))
            .originalTimestamp(Instant.ofEpochMilli(record.timestamp()))
            .dlqTimestamp(Instant.now())
            .errorType(exception.getClass().getName())
            .errorMessage(exception.getMessage())
            .stackTrace(truncateStackTrace(exception))
            .consumerGroupId(consumerGroup)
            .sourceService(applicationName)
            .failureCategory(categorizeFailure(exception))
            .build();
    }
    
    private Map<String, String> extractHeaders(ConsumerRecord<String, String> record) {
        Map<String, String> headers = new HashMap<>();
        for (Header header : record.headers()) {
            headers.put(header.key(), 
                header.value() != null ? new String(header.value(), StandardCharsets.UTF_8) : null);
        }
        return headers;
    }
    
    private Iterable<org.apache.kafka.common.header.Header> buildDlqHeaders(DlqRecord dlqRecord) {
        return java.util.List.of(
            new org.apache.kafka.common.header.internals.RecordHeader(
                "dlq-id", dlqRecord.getId().getBytes(StandardCharsets.UTF_8)),
            new org.apache.kafka.common.header.internals.RecordHeader(
                "dlq-original-topic", dlqRecord.getOriginalTopic().getBytes(StandardCharsets.UTF_8)),
            new org.apache.kafka.common.header.internals.RecordHeader(
                "dlq-error-type", dlqRecord.getErrorType().getBytes(StandardCharsets.UTF_8)),
            new org.apache.kafka.common.header.internals.RecordHeader(
                "dlq-failure-category", dlqRecord.getFailureCategory().name().getBytes(StandardCharsets.UTF_8)),
            new org.apache.kafka.common.header.internals.RecordHeader(
                "dlq-timestamp", dlqRecord.getDlqTimestamp().toString().getBytes(StandardCharsets.UTF_8))
        );
    }
    
    private String truncateStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String fullTrace = sw.toString();
        
        if (fullTrace.length() > MAX_STACK_TRACE_LENGTH) {
            return fullTrace.substring(0, MAX_STACK_TRACE_LENGTH) + "\n... truncated ...";
        }
        return fullTrace;
    }
    
    private DlqRecord.FailureCategory categorizeFailure(Exception e) {
        String className = e.getClass().getName().toLowerCase();
        String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        
        if (className.contains("json") || className.contains("parse") || 
            className.contains("deseriali") || className.contains("avro")) {
            return DlqRecord.FailureCategory.DESERIALIZATION;
        }
        
        if (className.contains("validation") || className.contains("illegal") ||
            message.contains("invalid") || message.contains("required")) {
            return DlqRecord.FailureCategory.VALIDATION;
        }
        
        if (className.contains("connect") || className.contains("timeout") ||
            className.contains("unavailable")) {
            return DlqRecord.FailureCategory.DOWNSTREAM_FAILURE;
        }
        
        if (className.contains("database") || className.contains("sql") ||
            className.contains("cassandra") || className.contains("persistence")) {
            return DlqRecord.FailureCategory.PERSISTENCE;
        }
        
        return DlqRecord.FailureCategory.UNKNOWN;
    }
    
    /**
     * Serialize a DLQ record to JSON using Jackson ObjectMapper.
     * <p>
     * This method provides robust JSON serialization with proper handling of:
     * <ul>
     *     <li>ISO-8601 date/time formatting for Instant fields</li>
     *     <li>Null value handling</li>
     *     <li>Special character escaping</li>
     *     <li>Nested objects and collections</li>
     * </ul>
     *
     * @param record the DLQ record to serialize
     * @return JSON string representation
     * @throws DlqPublishException if serialization fails
     */
    private String serializeDlqRecord(DlqRecord record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize DLQ record: {}", e.getMessage());
            throw new DlqPublishException("Failed to serialize DLQ record to JSON", e);
        }
    }
    
    /**
     * Exception thrown when DLQ publishing fails.
     */
    public static class DlqPublishException extends RuntimeException {
        public DlqPublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
