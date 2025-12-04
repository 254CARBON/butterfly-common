package com.z254.butterfly.common.kafka;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration contracts for RIM Fast Path messaging.
 * Provides standardized producer/consumer configs for Avro-serialized RimFastEvent messages.
 */
public final class RimKafkaContracts {

    private RimKafkaContracts() {
        // Utility class
    }

    /**
     * Default topic for RIM fast path events
     */
    public static final String RIM_FAST_PATH_TOPIC = "rim.fastpath.events";

    /**
     * Default topic for RIM state updates
     */
    public static final String RIM_STATE_TOPIC = "rim.state.updates";

    /**
     * DLQ topic for failed RIM events
     */
    public static final String RIM_DLQ_TOPIC = "rim.dlq";

    /**
     * Creates producer configuration for RIM fast path with Avro serialization.
     *
     * @param bootstrapServers Kafka bootstrap servers
     * @param schemaRegistryUrl Schema registry URL
     * @return Producer configuration map
     */
    public static Map<String, Object> fastPathProducerConfig(String bootstrapServers, String schemaRegistryUrl) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        config.put("schema.registry.url", schemaRegistryUrl);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        return config;
    }

    /**
     * Creates consumer configuration for RIM fast path with Avro deserialization.
     *
     * @param bootstrapServers Kafka bootstrap servers
     * @param schemaRegistryUrl Schema registry URL
     * @param groupId Consumer group ID
     * @return Consumer configuration map
     */
    public static Map<String, Object> fastPathConsumerConfig(String bootstrapServers, String schemaRegistryUrl, String groupId) {
        Map<String, Object> config = new HashMap<>();
        config.put("bootstrap.servers", bootstrapServers);
        config.put("group.id", groupId);
        config.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        config.put("value.deserializer", "io.confluent.kafka.serializers.KafkaAvroDeserializer");
        config.put("schema.registry.url", schemaRegistryUrl);
        config.put("specific.avro.reader", true);
        config.put("auto.offset.reset", "earliest");
        config.put("enable.auto.commit", false);
        return config;
    }
}

