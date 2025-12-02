package com.z254.butterfly.common.hft;

import com.z254.butterfly.common.identity.RimNodeId;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Factory for creating RimFastEvent instances.
 * <p>
 * Provides convenient methods for constructing high-frequency events
 * with proper validation and default values.
 * <p>
 * This factory is designed for use cases where Avro-generated classes
 * are not available (e.g., in tests or when working with JSON serialization).
 */
public final class RimEventFactory {

    private RimEventFactory() {
        // Utility class
    }

    /**
     * Creates a minimal RimFastEvent with required fields only.
     *
     * @param rimNodeId the RIM node identifier
     * @param eventTime the event timestamp
     * @param ingestTime the ingestion timestamp
     * @param metrics the critical metrics map
     * @param status the node status
     * @return a new RimFastEvent instance
     */
    public static RimFastEvent create(
            RimNodeId rimNodeId,
            Instant eventTime,
            Instant ingestTime,
            Map<String, Double> metrics,
            NodeStatus status) {
        
        return create(rimNodeId, eventTime, ingestTime, metrics, status, 
                     0L, null, null, null, null, null, null, null);
    }

    /**
     * Creates a RimFastEvent with all fields.
     *
     * @param rimNodeId    the RIM node identifier
     * @param eventTime    the event timestamp
     * @param ingestTime   the ingestion timestamp
     * @param metrics      the critical metrics map
     * @param status       the node status
     * @param logicalClock the logical clock value
     * @param eventId      optional event identifier
     * @param instrumentId optional instrument identifier
     * @param price        optional price value
     * @param volume       optional volume value
     * @param stress       optional stress metric (0.0-1.0)
     * @param metadata     optional string metadata
     * @param sourceTopic  optional source topic
     * @return a new RimFastEvent instance
     */
    public static RimFastEvent create(
            RimNodeId rimNodeId,
            Instant eventTime,
            Instant ingestTime,
            Map<String, Double> metrics,
            NodeStatus status,
            long logicalClock,
            String eventId,
            String instrumentId,
            Double price,
            Double volume,
            Double stress,
            Map<String, String> metadata,
            String sourceTopic) {
        
        Objects.requireNonNull(rimNodeId, "rimNodeId cannot be null");
        Objects.requireNonNull(eventTime, "eventTime cannot be null");
        Objects.requireNonNull(ingestTime, "ingestTime cannot be null");
        Objects.requireNonNull(status, "status cannot be null");

        VectorTimestamp vectorTimestamp = new VectorTimestamp(
            eventTime.toEpochMilli(),
            ingestTime.toEpochMilli(),
            logicalClock
        );

        return new RimFastEvent(
            rimNodeId.toString(),
            eventId,
            instrumentId,
            vectorTimestamp,
            metrics != null ? metrics : new HashMap<>(),
            price,
            volume,
            stress,
            status,
            sourceTopic,
            metadata
        );
    }

    /**
     * Creates a builder for fluent RimFastEvent construction.
     *
     * @param rimNodeId the RIM node identifier
     * @return a new builder instance
     */
    public static Builder builder(RimNodeId rimNodeId) {
        return new Builder(rimNodeId);
    }

    /**
     * Fluent builder for RimFastEvent construction.
     */
    public static class Builder {
        private final RimNodeId rimNodeId;
        private Instant eventTime = Instant.now();
        private Instant ingestTime = Instant.now();
        private Map<String, Double> metrics = new HashMap<>();
        private NodeStatus status = NodeStatus.UNKNOWN;
        private long logicalClock = 0L;
        private String eventId;
        private String instrumentId;
        private Double price;
        private Double volume;
        private Double stress;
        private Map<String, String> metadata;
        private String sourceTopic;

        private Builder(RimNodeId rimNodeId) {
            this.rimNodeId = Objects.requireNonNull(rimNodeId, "rimNodeId cannot be null");
        }

        public Builder eventTime(Instant eventTime) {
            this.eventTime = eventTime;
            return this;
        }

        public Builder ingestTime(Instant ingestTime) {
            this.ingestTime = ingestTime;
            return this;
        }

        public Builder metrics(Map<String, Double> metrics) {
            this.metrics = metrics;
            return this;
        }

        public Builder metric(String key, Double value) {
            this.metrics.put(key, value);
            return this;
        }

        public Builder status(NodeStatus status) {
            this.status = status;
            return this;
        }

        public Builder logicalClock(long logicalClock) {
            this.logicalClock = logicalClock;
            return this;
        }

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder instrumentId(String instrumentId) {
            this.instrumentId = instrumentId;
            return this;
        }

        public Builder price(Double price) {
            this.price = price;
            return this;
        }

        public Builder volume(Double volume) {
            this.volume = volume;
            return this;
        }

        public Builder stress(Double stress) {
            this.stress = stress;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder sourceTopic(String sourceTopic) {
            this.sourceTopic = sourceTopic;
            return this;
        }

        public RimFastEvent build() {
            return RimEventFactory.create(
                rimNodeId, eventTime, ingestTime, metrics, status,
                logicalClock, eventId, instrumentId, price, volume, 
                stress, metadata, sourceTopic
            );
        }
    }
}
