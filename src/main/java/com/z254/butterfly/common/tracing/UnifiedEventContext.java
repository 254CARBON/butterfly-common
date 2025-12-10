package com.z254.butterfly.common.tracing;

import com.z254.butterfly.common.kafka.UnifiedEventHeader;
import org.slf4j.MDC;
import reactor.util.context.Context;

import java.util.Optional;
import java.util.function.Function;

/**
 * Thread-local and reactive context propagator for {@link UnifiedEventHeader}.
 * 
 * <p>This class provides a unified way to propagate event headers across:
 * <ul>
 *   <li>Synchronous/blocking code via ThreadLocal</li>
 *   <li>Reactive pipelines via Reactor Context</li>
 *   <li>MDC for logging correlation</li>
 * </ul>
 * 
 * <h2>Usage in Synchronous Code</h2>
 * <pre>{@code
 * // Set context at entry point (e.g., HTTP filter, Kafka consumer)
 * UnifiedEventContext.set(header);
 * try {
 *     // Access anywhere in the call chain
 *     UnifiedEventHeader current = UnifiedEventContext.requireCurrent();
 *     processEvent(current);
 * } finally {
 *     UnifiedEventContext.clear();
 * }
 * }</pre>
 * 
 * <h2>Usage in Reactive Code</h2>
 * <pre>{@code
 * Mono.just(data)
 *     .contextWrite(UnifiedEventContext.toReactorContext(header))
 *     .flatMap(d -> {
 *         UnifiedEventHeader h = UnifiedEventContext.fromReactorContext(
 *             Mono.deferContextual(Mono::just).block()
 *         );
 *         return process(d, h);
 *     });
 * }</pre>
 * 
 * @see UnifiedEventHeader
 * @since 2.0.0
 */
public final class UnifiedEventContext {
    
    /**
     * ThreadLocal holder for synchronous code paths.
     */
    private static final ThreadLocal<UnifiedEventHeader> CURRENT = new ThreadLocal<>();
    
    /**
     * Key for storing the header in Reactor Context.
     */
    public static final String REACTOR_CONTEXT_KEY = "butterfly.unified.event.header";
    
    /**
     * MDC keys for logging correlation.
     */
    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String MDC_EVENT_ID = "eventId";
    public static final String MDC_ORIGIN_SYSTEM = "originSystem";
    public static final String MDC_PRIMARY_RIM_NODE = "primaryRimNode";
    public static final String MDC_DECISION_EPISODE_ID = "decisionEpisodeId";
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_SPAN_ID = "spanId";
    
    private UnifiedEventContext() {
        // Utility class - no instantiation
    }
    
    // ========================================
    // ThreadLocal Operations
    // ========================================
    
    /**
     * Set the current event header in thread-local storage.
     * Also updates MDC for logging correlation.
     * 
     * @param header the header to set
     */
    public static void set(UnifiedEventHeader header) {
        if (header == null) {
            clear();
            return;
        }
        CURRENT.set(header);
        propagateToMdc(header);
    }
    
    /**
     * Get the current event header from thread-local storage.
     * 
     * @return the current header, or null if not set
     */
    public static UnifiedEventHeader current() {
        return CURRENT.get();
    }
    
    /**
     * Get the current event header, wrapped in Optional.
     * 
     * @return Optional containing the header, or empty if not set
     */
    public static Optional<UnifiedEventHeader> currentOptional() {
        return Optional.ofNullable(CURRENT.get());
    }
    
    /**
     * Get the current event header, throwing if not present.
     * Use this in code paths where a header is mandatory.
     * 
     * @return the current header
     * @throws IllegalStateException if no header is set
     */
    public static UnifiedEventHeader requireCurrent() {
        UnifiedEventHeader header = CURRENT.get();
        if (header == null) {
            throw new IllegalStateException(
                "UnifiedEventHeader is required but not present in context. " +
                "Ensure the header is set at the entry point (HTTP filter, Kafka consumer, etc.)."
            );
        }
        return header;
    }
    
    /**
     * Clear the current event header from thread-local storage and MDC.
     */
    public static void clear() {
        CURRENT.remove();
        clearMdc();
    }
    
    /**
     * Check if a header is present in the current context.
     * 
     * @return true if a header is set
     */
    public static boolean isPresent() {
        return CURRENT.get() != null;
    }
    
    /**
     * Execute a function with a specific header set, restoring the previous header afterward.
     * 
     * @param header the header to use during execution
     * @param action the action to execute
     * @param <T> the return type
     * @return the result of the action
     */
    public static <T> T withHeader(UnifiedEventHeader header, java.util.function.Supplier<T> action) {
        UnifiedEventHeader previous = CURRENT.get();
        try {
            set(header);
            return action.get();
        } finally {
            if (previous != null) {
                set(previous);
            } else {
                clear();
            }
        }
    }
    
    /**
     * Execute a runnable with a specific header set, restoring the previous header afterward.
     * 
     * @param header the header to use during execution
     * @param action the action to execute
     */
    public static void withHeader(UnifiedEventHeader header, Runnable action) {
        UnifiedEventHeader previous = CURRENT.get();
        try {
            set(header);
            action.run();
        } finally {
            if (previous != null) {
                set(previous);
            } else {
                clear();
            }
        }
    }
    
    // ========================================
    // Reactor Context Operations
    // ========================================
    
    /**
     * Create a Reactor Context containing the event header.
     * 
     * @param header the header to include
     * @return a Context with the header
     */
    public static Context toReactorContext(UnifiedEventHeader header) {
        if (header == null) {
            return Context.empty();
        }
        return Context.of(REACTOR_CONTEXT_KEY, header);
    }
    
    /**
     * Create a Context write function for use with contextWrite().
     * 
     * @param header the header to add
     * @return a function that adds the header to an existing context
     */
    public static Function<Context, Context> contextWriter(UnifiedEventHeader header) {
        return ctx -> header != null 
            ? ctx.put(REACTOR_CONTEXT_KEY, header) 
            : ctx;
    }
    
    /**
     * Extract the event header from a Reactor Context.
     * 
     * @param context the Reactor Context
     * @return the header, or null if not present
     */
    public static UnifiedEventHeader fromReactorContext(Context context) {
        if (context == null || !context.hasKey(REACTOR_CONTEXT_KEY)) {
            return null;
        }
        return context.get(REACTOR_CONTEXT_KEY);
    }
    
    /**
     * Extract the event header from a Reactor Context, wrapped in Optional.
     * 
     * @param context the Reactor Context
     * @return Optional containing the header, or empty if not present
     */
    public static Optional<UnifiedEventHeader> fromReactorContextOptional(Context context) {
        return Optional.ofNullable(fromReactorContext(context));
    }
    
    /**
     * Extract the event header from a Reactor Context, throwing if not present.
     * 
     * @param context the Reactor Context
     * @return the header
     * @throws IllegalStateException if the header is not present
     */
    public static UnifiedEventHeader requireFromReactorContext(Context context) {
        UnifiedEventHeader header = fromReactorContext(context);
        if (header == null) {
            throw new IllegalStateException(
                "UnifiedEventHeader is required but not present in Reactor Context. " +
                "Ensure contextWrite() is called with the header upstream."
            );
        }
        return header;
    }
    
    /**
     * Check if a header is present in the Reactor Context.
     * 
     * @param context the Reactor Context
     * @return true if a header is present
     */
    public static boolean isPresentInContext(Context context) {
        return context != null && context.hasKey(REACTOR_CONTEXT_KEY);
    }
    
    // ========================================
    // MDC Operations
    // ========================================
    
    /**
     * Propagate the header fields to MDC for logging correlation.
     * 
     * @param header the header to propagate
     */
    public static void propagateToMdc(UnifiedEventHeader header) {
        if (header == null) {
            clearMdc();
            return;
        }
        
        MDC.put(MDC_CORRELATION_ID, header.correlationId());
        MDC.put(MDC_EVENT_ID, header.eventId());
        MDC.put(MDC_ORIGIN_SYSTEM, header.originSystem().name());
        
        if (header.primaryRimNode() != null) {
            MDC.put(MDC_PRIMARY_RIM_NODE, header.primaryRimNode().toString());
        }
        
        if (header.decisionEpisodeId() != null) {
            MDC.put(MDC_DECISION_EPISODE_ID, header.decisionEpisodeId());
        }
        
        if (header.traceId() != null) {
            MDC.put(MDC_TRACE_ID, header.traceId());
        }
        
        if (header.spanId() != null) {
            MDC.put(MDC_SPAN_ID, header.spanId());
        }
    }
    
    /**
     * Clear all MDC fields set by this context.
     */
    public static void clearMdc() {
        MDC.remove(MDC_CORRELATION_ID);
        MDC.remove(MDC_EVENT_ID);
        MDC.remove(MDC_ORIGIN_SYSTEM);
        MDC.remove(MDC_PRIMARY_RIM_NODE);
        MDC.remove(MDC_DECISION_EPISODE_ID);
        MDC.remove(MDC_TRACE_ID);
        MDC.remove(MDC_SPAN_ID);
    }
    
    /**
     * Get the current correlation ID from MDC.
     * 
     * @return the correlation ID, or null if not set
     */
    public static String getCorrelationIdFromMdc() {
        return MDC.get(MDC_CORRELATION_ID);
    }
    
    /**
     * Create a new header builder initialized from current MDC values.
     * Useful when transitioning from legacy code that only uses MDC.
     * 
     * @return a builder with MDC values pre-populated
     */
    public static UnifiedEventHeader.Builder builderFromMdc() {
        return UnifiedEventHeader.builder().fromMdc();
    }
    
    // ========================================
    // Utility Methods
    // ========================================
    
    /**
     * Create a child header from the current context.
     * 
     * @param childOriginSystem the system creating the child event
     * @return a new header linked to the current one via causation
     * @throws IllegalStateException if no current header exists
     */
    public static UnifiedEventHeader createChild(UnifiedEventHeader.OriginSystem childOriginSystem) {
        return requireCurrent().createChild(childOriginSystem);
    }
    
    /**
     * Get the correlation ID from the current context if available.
     * Falls back to MDC if thread-local is not set.
     * 
     * @return the correlation ID, or null if not available
     */
    public static String getCorrelationId() {
        UnifiedEventHeader header = CURRENT.get();
        if (header != null) {
            return header.correlationId();
        }
        return getCorrelationIdFromMdc();
    }
    
    /**
     * Get the primary RIM node from the current context.
     * 
     * @return the primary RIM node, or null if not set
     */
    public static com.z254.butterfly.common.identity.RimNodeId getPrimaryRimNode() {
        UnifiedEventHeader header = CURRENT.get();
        return header != null ? header.primaryRimNode() : null;
    }
    
    /**
     * Get the decision episode ID from the current context.
     * 
     * @return the decision episode ID, or null if not set
     */
    public static String getDecisionEpisodeId() {
        UnifiedEventHeader header = CURRENT.get();
        return header != null ? header.decisionEpisodeId() : null;
    }
}
