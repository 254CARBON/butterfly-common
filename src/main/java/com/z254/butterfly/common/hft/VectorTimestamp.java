package com.z254.butterfly.common.hft;

import java.util.Objects;

/**
 * Vector timestamp for ordering and causality tracking in distributed events.
 * <p>
 * Contains:
 * <ul>
 *     <li>Event timestamp (wall clock at event creation)</li>
 *     <li>Ingest timestamp (wall clock at RIM gateway ingestion)</li>
 *     <li>Logical clock (monotonic counter for ordering)</li>
 * </ul>
 */
public final class VectorTimestamp {

    private final long eventTsMs;
    private final long ingestTsMs;
    private final long logicalClock;

    /**
     * Creates a new VectorTimestamp.
     *
     * @param eventTsMs    timestamp of event creation (wall clock in milliseconds)
     * @param ingestTsMs   timestamp of ingestion by RIM gateway (wall clock in milliseconds)
     * @param logicalClock monotonic logical clock for ordering
     */
    public VectorTimestamp(long eventTsMs, long ingestTsMs, long logicalClock) {
        this.eventTsMs = eventTsMs;
        this.ingestTsMs = ingestTsMs;
        this.logicalClock = logicalClock;
    }

    /**
     * Creates a VectorTimestamp with the current time and zero logical clock.
     *
     * @return a new VectorTimestamp with current timestamps
     */
    public static VectorTimestamp now() {
        long now = System.currentTimeMillis();
        return new VectorTimestamp(now, now, 0L);
    }

    /**
     * Creates a VectorTimestamp with the specified event time and current ingest time.
     *
     * @param eventTsMs the event timestamp in milliseconds
     * @return a new VectorTimestamp
     */
    public static VectorTimestamp withEventTime(long eventTsMs) {
        return new VectorTimestamp(eventTsMs, System.currentTimeMillis(), 0L);
    }

    /**
     * @return timestamp of event creation (wall clock in milliseconds)
     */
    public long getEventTsMs() {
        return eventTsMs;
    }

    /**
     * @return timestamp of ingestion by RIM gateway (wall clock in milliseconds)
     */
    public long getIngestTsMs() {
        return ingestTsMs;
    }

    /**
     * @return monotonic logical clock for ordering
     */
    public long getLogicalClock() {
        return logicalClock;
    }

    /**
     * @return the latency between event creation and ingestion in milliseconds
     */
    public long getIngestLatencyMs() {
        return ingestTsMs - eventTsMs;
    }

    /**
     * Creates a new VectorTimestamp with an incremented logical clock.
     *
     * @return a new VectorTimestamp with logicalClock + 1
     */
    public VectorTimestamp tick() {
        return new VectorTimestamp(eventTsMs, ingestTsMs, logicalClock + 1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VectorTimestamp that = (VectorTimestamp) o;
        return eventTsMs == that.eventTsMs &&
               ingestTsMs == that.ingestTsMs &&
               logicalClock == that.logicalClock;
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventTsMs, ingestTsMs, logicalClock);
    }

    @Override
    public String toString() {
        return "VectorTimestamp{" +
               "eventTsMs=" + eventTsMs +
               ", ingestTsMs=" + ingestTsMs +
               ", logicalClock=" + logicalClock +
               '}';
    }
}

