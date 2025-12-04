package com.z254.butterfly.common.kafka.dlq;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * Represents a failed message that has been routed to a Dead Letter Queue (DLQ).
 * <p>
 * This record captures all relevant context about the failure including:
 * <ul>
 *     <li>Original message payload and metadata</li>
 *     <li>Error details for debugging</li>
 *     <li>Retry tracking information</li>
 *     <li>Source context (topic, partition, offset)</li>
 * </ul>
 * <p>
 * DLQ records can be replayed using {@link DlqRetryService} after the underlying
 * issue has been resolved.
 */
@Data
@Builder
public class DlqRecord {
    
    /**
     * Unique identifier for this DLQ record.
     */
    private String id;
    
    /**
     * The original Kafka topic from which the message was consumed.
     */
    private String originalTopic;
    
    /**
     * The partition from which the message was consumed.
     */
    private Integer partition;
    
    /**
     * The offset of the original message in its partition.
     */
    private Long offset;
    
    /**
     * The message key (if present).
     */
    private String key;
    
    /**
     * The raw message payload that failed processing.
     */
    private String payload;
    
    /**
     * Headers from the original Kafka message.
     */
    private Map<String, String> headers;
    
    /**
     * Timestamp when the original message was produced.
     */
    private Instant originalTimestamp;
    
    /**
     * Timestamp when this DLQ record was created.
     */
    private Instant dlqTimestamp;
    
    /**
     * The class name of the exception that caused the failure.
     */
    private String errorType;
    
    /**
     * The error message from the exception.
     */
    private String errorMessage;
    
    /**
     * Stack trace of the exception (truncated if too long).
     */
    private String stackTrace;
    
    /**
     * The consumer group ID that was processing the message.
     */
    private String consumerGroupId;
    
    /**
     * The service/application that encountered the failure.
     */
    private String sourceService;
    
    /**
     * Number of times replay has been attempted for this record.
     */
    @Builder.Default
    private int retryCount = 0;
    
    /**
     * Timestamp of the last retry attempt.
     */
    private Instant lastRetryTimestamp;
    
    /**
     * Current status of the DLQ record.
     */
    @Builder.Default
    private DlqStatus status = DlqStatus.PENDING;
    
    /**
     * Category of the failure for routing and alerting.
     */
    private FailureCategory failureCategory;
    
    /**
     * Status of a DLQ record.
     */
    public enum DlqStatus {
        /** Awaiting retry or manual intervention */
        PENDING,
        /** Currently being retried */
        RETRYING,
        /** Successfully reprocessed */
        RESOLVED,
        /** Permanently failed after max retries */
        DEAD,
        /** Manually marked as skipped */
        SKIPPED
    }
    
    /**
     * Category of failure for routing and alerting purposes.
     */
    public enum FailureCategory {
        /** JSON/Avro deserialization failure */
        DESERIALIZATION,
        /** Business validation failure */
        VALIDATION,
        /** Downstream service unavailable */
        DOWNSTREAM_FAILURE,
        /** Database/storage failure */
        PERSISTENCE,
        /** Unexpected runtime error */
        UNKNOWN
    }
    
    /**
     * Increment the retry count and update the last retry timestamp.
     */
    public void markRetryAttempt() {
        this.retryCount++;
        this.lastRetryTimestamp = Instant.now();
        this.status = DlqStatus.RETRYING;
    }
    
    /**
     * Mark this record as successfully resolved.
     */
    public void markResolved() {
        this.status = DlqStatus.RESOLVED;
    }
    
    /**
     * Mark this record as permanently dead (no more retries).
     */
    public void markDead() {
        this.status = DlqStatus.DEAD;
    }
}

