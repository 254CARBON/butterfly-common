package com.z254.butterfly.common.kafka.dlq;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Service for replaying messages from Dead Letter Queues (DLQs).
 * <p>
 * This service provides functionality to:
 * <ul>
 *     <li>Consume messages from DLQ topics</li>
 *     <li>Replay them to the original topic</li>
 *     <li>Track retry attempts and mark records as resolved/dead</li>
 *     <li>Apply filtering criteria before replay</li>
 * </ul>
 * <p>
 * Usage patterns:
 * <ul>
 *     <li>Automated scheduled replay of transient failures</li>
 *     <li>Manual replay after bug fixes</li>
 *     <li>Selective replay based on failure category</li>
 * </ul>
 * <p>
 * Configuration:
 * <pre>{@code
 * DlqRetryService retryService = new DlqRetryService(
 *     consumerFactory, kafkaTemplate, meterRegistry, 3 // max retries
 * );
 * 
 * // Replay all pending messages from a DLQ
 * retryService.replayDlq("rim.fast-path.dlq", record -> {
 *     // Return true to replay, false to skip
 *     return record.getRetryCount() < 3;
 * });
 * }</pre>
 */
@Slf4j
public class DlqRetryService {
    
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(5);
    
    private final KafkaConsumer<String, String> consumer;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final int maxRetries;
    
    private final Counter replaySuccessCounter;
    private final Counter replayFailureCounter;
    private final Counter replaySkippedCounter;
    
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    /**
     * Create a new DLQ retry service.
     *
     * @param consumer       Kafka consumer for reading DLQ messages
     * @param kafkaTemplate  Kafka template for republishing messages
     * @param meterRegistry  meter registry for metrics
     * @param maxRetries     maximum number of retry attempts before marking dead
     */
    public DlqRetryService(KafkaConsumer<String, String> consumer,
                          KafkaTemplate<String, String> kafkaTemplate,
                          MeterRegistry meterRegistry,
                          int maxRetries) {
        this.consumer = consumer;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
        this.maxRetries = maxRetries;
        
        this.replaySuccessCounter = meterRegistry.counter("dlq.replay.success");
        this.replayFailureCounter = meterRegistry.counter("dlq.replay.failure");
        this.replaySkippedCounter = meterRegistry.counter("dlq.replay.skipped");
    }
    
    /**
     * Replay messages from a DLQ topic to the original topic.
     *
     * @param dlqTopic      the DLQ topic to consume from
     * @param shouldReplay  filter function that returns true if a message should be replayed
     * @return summary of the replay operation
     */
    public ReplaySummary replayDlq(String dlqTopic, Function<DlqRecord, Boolean> shouldReplay) {
        if (!running.compareAndSet(false, true)) {
            log.warn("Replay already in progress, skipping");
            return ReplaySummary.builder()
                .dlqTopic(dlqTopic)
                .status(ReplaySummary.Status.SKIPPED)
                .message("Replay already in progress")
                .build();
        }
        
        ReplaySummary.ReplaySummaryBuilder summary = ReplaySummary.builder()
            .dlqTopic(dlqTopic)
            .startTime(java.time.Instant.now());
        
        int replayed = 0;
        int skipped = 0;
        int failed = 0;
        int markedDead = 0;
        
        try {
            consumer.subscribe(Collections.singletonList(dlqTopic));
            log.info("Starting DLQ replay from topic: {}", dlqTopic);
            
            boolean hasMore = true;
            while (hasMore) {
                ConsumerRecords<String, String> records = consumer.poll(POLL_TIMEOUT);
                
                if (records.isEmpty()) {
                    hasMore = false;
                    continue;
                }
                
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        DlqRecord dlqRecord = deserializeDlqRecord(record.value());
                        
                        // Check if max retries exceeded
                        if (dlqRecord.getRetryCount() >= maxRetries) {
                            dlqRecord.markDead();
                            markedDead++;
                            log.warn("DLQ record {} exceeded max retries, marking as dead", 
                                dlqRecord.getId());
                            meterRegistry.counter("dlq.record.dead",
                                "topic", dlqRecord.getOriginalTopic()
                            ).increment();
                            continue;
                        }
                        
                        // Apply filter
                        if (shouldReplay != null && !shouldReplay.apply(dlqRecord)) {
                            skipped++;
                            replaySkippedCounter.increment();
                            continue;
                        }
                        
                        // Attempt replay
                        dlqRecord.markRetryAttempt();
                        boolean success = replayMessage(dlqRecord);
                        
                        if (success) {
                            dlqRecord.markResolved();
                            replayed++;
                            replaySuccessCounter.increment();
                            log.debug("Successfully replayed DLQ record {} to {}", 
                                dlqRecord.getId(), dlqRecord.getOriginalTopic());
                        } else {
                            failed++;
                            replayFailureCounter.increment();
                        }
                        
                    } catch (Exception e) {
                        failed++;
                        replayFailureCounter.increment();
                        log.error("Failed to process DLQ record: {}", e.getMessage());
                    }
                }
                
                consumer.commitSync();
            }
            
            summary.status(ReplaySummary.Status.COMPLETED)
                   .replayed(replayed)
                   .skipped(skipped)
                   .failed(failed)
                   .markedDead(markedDead);
            
            log.info("DLQ replay completed: replayed={}, skipped={}, failed={}, markedDead={}",
                replayed, skipped, failed, markedDead);
            
        } catch (Exception e) {
            log.error("DLQ replay failed: {}", e.getMessage(), e);
            summary.status(ReplaySummary.Status.FAILED)
                   .message(e.getMessage())
                   .replayed(replayed)
                   .skipped(skipped)
                   .failed(failed);
        } finally {
            consumer.unsubscribe();
            running.set(false);
            summary.endTime(java.time.Instant.now());
        }
        
        return summary.build();
    }
    
    /**
     * Replay a single DLQ record to its original topic.
     */
    private boolean replayMessage(DlqRecord dlqRecord) {
        try {
            String originalTopic = dlqRecord.getOriginalTopic();
            
            ProducerRecord<String, String> producerRecord = new ProducerRecord<>(
                originalTopic,
                dlqRecord.getKey(),
                dlqRecord.getPayload()
            );
            
            // Add replay headers
            producerRecord.headers().add("dlq-replay-id", 
                dlqRecord.getId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            producerRecord.headers().add("dlq-retry-count", 
                String.valueOf(dlqRecord.getRetryCount()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            
            kafkaTemplate.send(producerRecord).get();
            return true;
            
        } catch (Exception e) {
            log.error("Failed to replay message {} to {}: {}", 
                dlqRecord.getId(), dlqRecord.getOriginalTopic(), e.getMessage());
            return false;
        }
    }
    
    /**
     * Deserialize a DLQ record from JSON.
     */
    private DlqRecord deserializeDlqRecord(String json) {
        // Simple JSON parsing - in production use Jackson
        DlqRecord.DlqRecordBuilder builder = DlqRecord.builder();
        
        // Extract fields using simple string operations
        builder.id(extractJsonField(json, "id"));
        builder.originalTopic(extractJsonField(json, "originalTopic"));
        
        String partitionStr = extractJsonField(json, "partition");
        if (partitionStr != null) {
            builder.partition(Integer.parseInt(partitionStr));
        }
        
        String offsetStr = extractJsonField(json, "offset");
        if (offsetStr != null) {
            builder.offset(Long.parseLong(offsetStr));
        }
        
        builder.key(extractJsonField(json, "key"));
        builder.payload(extractJsonField(json, "payload"));
        builder.errorType(extractJsonField(json, "errorType"));
        builder.errorMessage(extractJsonField(json, "errorMessage"));
        builder.sourceService(extractJsonField(json, "sourceService"));
        
        String retryCountStr = extractJsonField(json, "retryCount");
        if (retryCountStr != null) {
            builder.retryCount(Integer.parseInt(retryCountStr));
        }
        
        String categoryStr = extractJsonField(json, "failureCategory");
        if (categoryStr != null) {
            builder.failureCategory(DlqRecord.FailureCategory.valueOf(categoryStr));
        }
        
        String statusStr = extractJsonField(json, "status");
        if (statusStr != null) {
            builder.status(DlqRecord.DlqStatus.valueOf(statusStr));
        }
        
        String timestampStr = extractJsonField(json, "dlqTimestamp");
        if (timestampStr != null) {
            builder.dlqTimestamp(java.time.Instant.parse(timestampStr));
        }
        
        return builder.build();
    }
    
    private String extractJsonField(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\":";
        int startIndex = json.indexOf(pattern);
        if (startIndex == -1) return null;
        
        startIndex += pattern.length();
        
        // Skip whitespace
        while (startIndex < json.length() && Character.isWhitespace(json.charAt(startIndex))) {
            startIndex++;
        }
        
        if (startIndex >= json.length()) return null;
        
        char firstChar = json.charAt(startIndex);
        
        if (firstChar == '"') {
            // String value
            startIndex++;
            int endIndex = startIndex;
            while (endIndex < json.length()) {
                char c = json.charAt(endIndex);
                if (c == '"' && json.charAt(endIndex - 1) != '\\') {
                    break;
                }
                endIndex++;
            }
            return json.substring(startIndex, endIndex)
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
        } else {
            // Number or other value
            int endIndex = startIndex;
            while (endIndex < json.length()) {
                char c = json.charAt(endIndex);
                if (c == ',' || c == '}') break;
                endIndex++;
            }
            return json.substring(startIndex, endIndex).trim();
        }
    }
    
    /**
     * Summary of a DLQ replay operation.
     */
    @lombok.Data
    @lombok.Builder
    public static class ReplaySummary {
        private String dlqTopic;
        private Status status;
        private String message;
        private int replayed;
        private int skipped;
        private int failed;
        private int markedDead;
        private java.time.Instant startTime;
        private java.time.Instant endTime;
        
        public enum Status {
            COMPLETED, FAILED, SKIPPED
        }
        
        public java.time.Duration getDuration() {
            if (startTime == null || endTime == null) return null;
            return java.time.Duration.between(startTime, endTime);
        }
    }
}
