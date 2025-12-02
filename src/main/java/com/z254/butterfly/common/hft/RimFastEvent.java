package com.z254.butterfly.common.hft;

import com.z254.butterfly.common.identity.RimNodeId;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * High-frequency event for RIM updates.
 * <p>
 * This is a POJO representation of the RimFastEvent that can be used
 * independently of the Avro-generated classes. It provides the same
 * structure with proper Java types.
 * <p>
 * Designed for high-throughput scenarios with minimal payload size.
 */
public final class RimFastEvent {

    private final String rimNodeId;
    private final String eventId;
    private final String instrumentId;
    private final VectorTimestamp vectorTimestamp;
    private final Map<String, Double> criticalMetrics;
    private final Double price;
    private final Double volume;
    private final Double stress;
    private final NodeStatus status;
    private final String sourceTopic;
    private final Map<String, String> metadata;

    /**
     * Creates a new RimFastEvent with all fields.
     */
    public RimFastEvent(
            String rimNodeId,
            String eventId,
            String instrumentId,
            VectorTimestamp vectorTimestamp,
            Map<String, Double> criticalMetrics,
            Double price,
            Double volume,
            Double stress,
            NodeStatus status,
            String sourceTopic,
            Map<String, String> metadata) {
        
        this.rimNodeId = Objects.requireNonNull(rimNodeId, "rimNodeId cannot be null");
        this.eventId = eventId;
        this.instrumentId = instrumentId;
        this.vectorTimestamp = Objects.requireNonNull(vectorTimestamp, "vectorTimestamp cannot be null");
        this.criticalMetrics = criticalMetrics != null ? new HashMap<>(criticalMetrics) : new HashMap<>();
        this.price = price;
        this.volume = volume;
        this.stress = stress;
        this.status = status != null ? status : NodeStatus.UNKNOWN;
        this.sourceTopic = sourceTopic;
        this.metadata = metadata != null ? new HashMap<>(metadata) : null;
    }

    /**
     * @return the canonical RIM Node Identifier string
     */
    public String getRimNodeId() {
        return rimNodeId;
    }

    /**
     * Parses and returns the RimNodeId object.
     *
     * @return the parsed RimNodeId
     * @throws IllegalArgumentException if the rimNodeId is not valid
     */
    public RimNodeId getParsedRimNodeId() {
        return RimNodeId.parse(rimNodeId);
    }

    /**
     * @return optional globally unique event identifier
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * @return optional instrument identity
     */
    public String getInstrumentId() {
        return instrumentId;
    }

    /**
     * @return the vector timestamp for ordering
     */
    public VectorTimestamp getVectorTimestamp() {
        return vectorTimestamp;
    }

    /**
     * @return sparse key-value metrics map
     */
    public Map<String, Double> getCriticalMetrics() {
        return new HashMap<>(criticalMetrics);
    }

    /**
     * Gets a specific metric value.
     *
     * @param key the metric key
     * @return the metric value, or null if not present
     */
    public Double getMetric(String key) {
        return criticalMetrics.get(key);
    }

    /**
     * Gets a specific metric value with a default.
     *
     * @param key          the metric key
     * @param defaultValue the default value if not present
     * @return the metric value, or defaultValue if not present
     */
    public double getMetricOrDefault(String key, double defaultValue) {
        Double value = criticalMetrics.get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * @return optional point-in-time price
     */
    public Double getPrice() {
        return price;
    }

    /**
     * @return optional trade or order-book volume
     */
    public Double getVolume() {
        return volume;
    }

    /**
     * @return optional stress/pressure metric (0.0-1.0)
     */
    public Double getStress() {
        return stress;
    }

    /**
     * @return the node status
     */
    public NodeStatus getStatus() {
        return status;
    }

    /**
     * @return optional source Kafka topic
     */
    public String getSourceTopic() {
        return sourceTopic;
    }

    /**
     * @return optional string metadata map
     */
    public Map<String, String> getMetadata() {
        return metadata != null ? new HashMap<>(metadata) : null;
    }

    /**
     * Checks if this event has high stress (>= 0.8).
     *
     * @return true if stress is high
     */
    public boolean isHighStress() {
        return stress != null && stress >= 0.8;
    }

    /**
     * Checks if this event is in alert status.
     *
     * @return true if status is ALERT
     */
    public boolean isAlert() {
        return status == NodeStatus.ALERT;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RimFastEvent that = (RimFastEvent) o;
        return Objects.equals(rimNodeId, that.rimNodeId) &&
               Objects.equals(eventId, that.eventId) &&
               Objects.equals(vectorTimestamp, that.vectorTimestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rimNodeId, eventId, vectorTimestamp);
    }

    @Override
    public String toString() {
        return "RimFastEvent{" +
               "rimNodeId='" + rimNodeId + '\'' +
               ", eventId='" + eventId + '\'' +
               ", status=" + status +
               ", stress=" + stress +
               '}';
    }
}
