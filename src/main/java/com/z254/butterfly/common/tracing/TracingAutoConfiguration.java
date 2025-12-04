package com.z254.butterfly.common.tracing;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Auto-configuration for tracing integration with Kafka interceptors.
 * 
 * <p>This configuration wires the Micrometer {@link Tracer} into the
 * Kafka interceptors for trace context propagation.
 */
@Configuration
@ConditionalOnClass({Tracer.class})
public class TracingAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TracingAutoConfiguration.class);

    private final Tracer tracer;
    private final Propagator propagator;

    @Autowired
    public TracingAutoConfiguration(
            @Autowired(required = false) Tracer tracer,
            @Autowired(required = false) Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    @PostConstruct
    public void initializeKafkaInterceptors() {
        if (tracer != null) {
            TracingKafkaInterceptor.TracingProducerInterceptor.setTracer(tracer);
            TracingKafkaInterceptor.TracingConsumerInterceptor.setTracer(tracer);
            log.info("Configured Kafka tracing interceptors with Micrometer Tracer");
        } else {
            log.warn("Tracer not available - Kafka tracing interceptors will not propagate spans");
        }

        if (propagator != null) {
            TracingKafkaInterceptor.TracingProducerInterceptor.setPropagator(propagator);
            log.debug("W3C context propagator configured for Kafka tracing");
        }
    }
}

