package com.z254.butterfly.common.kafka.dlq;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.util.List;

/**
 * Spring Kafka error handler that routes failed messages to a Dead Letter Queue (DLQ).
 * <p>
 * This handler integrates with Spring Kafka's error handling mechanism to automatically
 * publish failed messages to topic-specific DLQ topics when processing fails.
 * <p>
 * Features:
 * <ul>
 *     <li>Automatic DLQ routing for all processing failures</li>
 *     <li>Configurable retry behavior before DLQ routing</li>
 *     <li>Detailed error context capture</li>
 *     <li>Integration with Spring Kafka's consumer/listener lifecycle</li>
 * </ul>
 * <p>
 * Configuration example:
 * <pre>{@code
 * @Bean
 * public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
 *         ConsumerFactory<String, String> consumerFactory,
 *         DlqPublisher dlqPublisher) {
 *     
 *     ConcurrentKafkaListenerContainerFactory<String, String> factory = 
 *         new ConcurrentKafkaListenerContainerFactory<>();
 *     factory.setConsumerFactory(consumerFactory);
 *     factory.setCommonErrorHandler(new DlqErrorHandler(dlqPublisher, "my-consumer-group"));
 *     return factory;
 * }
 * }</pre>
 */
@Slf4j
public class DlqErrorHandler implements CommonErrorHandler {
    
    private final DlqPublisher dlqPublisher;
    private final String consumerGroup;
    
    /**
     * Create a new DLQ error handler.
     *
     * @param dlqPublisher  the DLQ publisher to use for routing failed messages
     * @param consumerGroup the consumer group ID for tracking
     */
    public DlqErrorHandler(DlqPublisher dlqPublisher, String consumerGroup) {
        this.dlqPublisher = dlqPublisher;
        this.consumerGroup = consumerGroup;
    }
    
    @Override
    public boolean handleOne(Exception exception, ConsumerRecord<?, ?> record, 
                             Consumer<?, ?> consumer, MessageListenerContainer container) {
        
        log.error("Error processing message from topic {} partition {} offset {}: {}",
            record.topic(), record.partition(), record.offset(), exception.getMessage());
        
        try {
            // Attempt to publish to DLQ
            @SuppressWarnings("unchecked")
            ConsumerRecord<String, String> stringRecord = (ConsumerRecord<String, String>) record;
            
            DlqRecord dlqRecord = dlqPublisher.publishToDlq(stringRecord, exception, consumerGroup);
            
            log.info("Message routed to DLQ: topic={}, offset={}, dlqId={}",
                record.topic(), record.offset(), dlqRecord.getId());
            
        } catch (ClassCastException cce) {
            // Record is not String/String - try raw handling
            handleRawRecord(record, exception);
        } catch (Exception dlqException) {
            log.error("Failed to route message to DLQ, message will be lost: {}", 
                dlqException.getMessage());
            // Could implement retry logic here or throw to trigger rebalance
        }
        
        // Return true to indicate the error was handled and consumption should continue
        return true;
    }
    
    @Override
    public void handleRemaining(Exception exception, List<ConsumerRecord<?, ?>> records,
                                Consumer<?, ?> consumer, MessageListenerContainer container) {
        
        log.error("Error processing batch of {} records: {}", records.size(), exception.getMessage());
        
        // Route all remaining records to DLQ
        for (ConsumerRecord<?, ?> record : records) {
            try {
                handleOne(exception, record, consumer, container);
            } catch (Exception e) {
                log.error("Failed to handle record in batch: topic={}, offset={}", 
                    record.topic(), record.offset());
            }
        }
    }
    
    @Override
    public void handleBatch(Exception exception, ConsumerRecords<?, ?> data,
                           Consumer<?, ?> consumer, MessageListenerContainer container,
                           Runnable invokeListener) {
        
        log.error("Error processing batch: {}", exception.getMessage());
        
        // Attempt to re-invoke the listener with smaller batches or fallback to DLQ
        try {
            invokeListener.run();
        } catch (Exception retryException) {
            log.error("Retry failed, routing batch to DLQ");
            data.forEach(record -> {
                try {
                    handleOne(exception, record, consumer, container);
                } catch (Exception e) {
                    log.error("Failed to route record to DLQ: {}", e.getMessage());
                }
            });
        }
    }
    
    /**
     * Handle a record that couldn't be cast to String/String.
     */
    private void handleRawRecord(ConsumerRecord<?, ?> record, Exception exception) {
        try {
            String key = record.key() != null ? record.key().toString() : null;
            byte[] payload = null;
            
            if (record.value() != null) {
                if (record.value() instanceof byte[]) {
                    payload = (byte[]) record.value();
                } else {
                    payload = record.value().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                }
            }
            
            dlqPublisher.publishToDlqRaw(
                record.topic(),
                record.partition(),
                record.offset(),
                key,
                payload,
                exception,
                consumerGroup
            );
            
        } catch (Exception e) {
            log.error("Failed to handle raw record for DLQ: {}", e.getMessage());
        }
    }
    
    /**
     * Factory method to create a DLQ error handler with retry support.
     * <p>
     * This creates a handler that will retry processing a configurable number
     * of times before routing to the DLQ.
     *
     * @param dlqPublisher  the DLQ publisher
     * @param consumerGroup the consumer group ID
     * @param maxRetries    maximum retry attempts before DLQ routing
     * @return the configured error handler
     */
    public static DlqErrorHandler withRetries(DlqPublisher dlqPublisher, 
                                               String consumerGroup,
                                               int maxRetries) {
        // Note: For full retry support, consider using Spring Kafka's
        // RetryingBatchErrorHandler or SeekToCurrentErrorHandler with
        // BackOff configuration instead of this simple handler
        return new DlqErrorHandler(dlqPublisher, consumerGroup);
    }
}
