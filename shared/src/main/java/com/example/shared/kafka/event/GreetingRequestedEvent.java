package com.example.shared.kafka.event;

import java.time.Instant;

/**
 * Event published when a greeting is requested.
 * Used to demonstrate Kafka + OTel trace context propagation.
 */
public record GreetingRequestedEvent(
    String traceId,
    Long userId,
    String language,
    String greeting,
    Instant timestamp
) {}
