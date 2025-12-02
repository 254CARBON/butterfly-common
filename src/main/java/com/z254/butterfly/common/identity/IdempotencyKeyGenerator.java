package com.z254.butterfly.common.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Generates idempotency keys for deduplication of events and operations.
 * <p>
 * Supports multiple strategies:
 * <ul>
 *     <li>Content-based: Hash of payload content</li>
 *     <li>Time-windowed: Combines content hash with time bucket</li>
 *     <li>UUID-based: Random unique identifiers</li>
 * </ul>
 * <p>
 * Thread-safe and suitable for high-throughput scenarios.
 */
public final class IdempotencyKeyGenerator {

    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    private IdempotencyKeyGenerator() {
        // Utility class
    }

    /**
     * Generates an idempotency key from content using SHA-256 hash.
     *
     * @param content the content to hash
     * @return a hex-encoded hash string
     */
    public static String fromContent(String content) {
        Objects.requireNonNull(content, "content cannot be null");
        return sha256Hex(content);
    }

    /**
     * Generates an idempotency key from multiple content parts.
     *
     * @param parts the content parts to combine and hash
     * @return a hex-encoded hash string
     */
    public static String fromContent(String... parts) {
        Objects.requireNonNull(parts, "parts cannot be null");
        StringBuilder combined = new StringBuilder();
        for (String part : parts) {
            if (part != null) {
                combined.append(part).append("|");
            }
        }
        return sha256Hex(combined.toString());
    }

    /**
     * Generates a time-windowed idempotency key.
     * <p>
     * Events with the same content within the same time window will have the same key.
     *
     * @param content        the content to hash
     * @param windowSizeMs   the time window size in milliseconds
     * @return a hex-encoded hash string with time bucket
     */
    public static String fromContentWithTimeWindow(String content, long windowSizeMs) {
        Objects.requireNonNull(content, "content cannot be null");
        if (windowSizeMs <= 0) {
            throw new IllegalArgumentException("windowSizeMs must be positive");
        }
        
        long timeBucket = System.currentTimeMillis() / windowSizeMs;
        return sha256Hex(content + "|" + timeBucket);
    }

    /**
     * Generates a time-windowed idempotency key with a specific timestamp.
     *
     * @param content        the content to hash
     * @param timestamp      the timestamp to use for windowing
     * @param windowSizeMs   the time window size in milliseconds
     * @return a hex-encoded hash string with time bucket
     */
    public static String fromContentWithTimeWindow(String content, Instant timestamp, long windowSizeMs) {
        Objects.requireNonNull(content, "content cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        if (windowSizeMs <= 0) {
            throw new IllegalArgumentException("windowSizeMs must be positive");
        }
        
        long timeBucket = timestamp.toEpochMilli() / windowSizeMs;
        return sha256Hex(content + "|" + timeBucket);
    }

    /**
     * Generates a key for a RIM node event combining node ID, event type, and time window.
     *
     * @param rimNodeId     the RIM node identifier
     * @param eventType     the type of event
     * @param windowSizeMs  the time window size in milliseconds
     * @return a hex-encoded hash string
     */
    public static String forRimEvent(RimNodeId rimNodeId, String eventType, long windowSizeMs) {
        Objects.requireNonNull(rimNodeId, "rimNodeId cannot be null");
        Objects.requireNonNull(eventType, "eventType cannot be null");
        
        long timeBucket = System.currentTimeMillis() / windowSizeMs;
        return sha256Hex(rimNodeId.toString() + "|" + eventType + "|" + timeBucket);
    }

    /**
     * Generates an idempotency key for a RIM snapshot.
     *
     * @param rimNodeId      the RIM node identifier
     * @param snapshotTime   the snapshot timestamp
     * @param deltaSeconds   the time delta in seconds
     * @param vantage        the vantage point identifier
     * @return a hex-encoded hash string
     */
    public static String forSnapshot(RimNodeId rimNodeId, Instant snapshotTime, int deltaSeconds, String vantage) {
        Objects.requireNonNull(rimNodeId, "rimNodeId cannot be null");
        Objects.requireNonNull(snapshotTime, "snapshotTime cannot be null");
        String content = rimNodeId.toString() + "|" + snapshotTime.toEpochMilli() + "|" + deltaSeconds + "|" + vantage;
        return sha256Hex(content);
    }

    /**
     * Generates a random UUID-based idempotency key.
     *
     * @return a UUID string
     */
    public static String randomUUID() {
        return UUID.randomUUID().toString();
    }

    /**
     * Generates a prefixed idempotency key.
     *
     * @param prefix  the prefix to use
     * @param content the content to hash
     * @return a prefixed hex-encoded hash string
     */
    public static String withPrefix(String prefix, String content) {
        Objects.requireNonNull(prefix, "prefix cannot be null");
        Objects.requireNonNull(content, "content cannot be null");
        return prefix + "-" + sha256Hex(content).substring(0, 16);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_ARRAY[v >>> 4];
            hexChars[i * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }
}
