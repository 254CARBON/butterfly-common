# BUTTERFLY Integration Cookbook

A comprehensive guide to integrating services within the BUTTERFLY ecosystem.

## Table of Contents

1. [Service Communication Patterns](#service-communication-patterns)
2. [Error Handling](#error-handling)
3. [Tenant Propagation](#tenant-propagation)
4. [Distributed Tracing](#distributed-tracing)
5. [Event-Driven Communication](#event-driven-communication)
6. [Resilience Patterns](#resilience-patterns)
7. [Security](#security)
8. [Best Practices](#best-practices)

---

## Service Communication Patterns

### Synchronous REST Calls

Use the `ButterflyServiceClientFactory` for typed, resilient REST communication:

```java
@Autowired
private ButterflyServiceClientFactory clientFactory;

public Mono<Capsule> getCapsule(String capsuleId) {
    return clientFactory.capsuleClient()
        .getCapsule(capsuleId)
        .timeout(Duration.ofSeconds(5))
        .onErrorResume(e -> {
            log.warn("Failed to get capsule {}: {}", capsuleId, e.getMessage());
            return Mono.empty();
        });
}
```

### Custom Client with AbstractButterflyClient

Extend `AbstractButterflyClient` for custom service clients:

```java
public class MyServiceClient extends AbstractButterflyClient {
    
    public MyServiceClient(WebClient webClient) {
        super(webClient, "my-service", ClientConfig.defaults());
    }
    
    @Override
    public String getServiceId() {
        return "my-service";
    }
    
    public Mono<MyResponse> getData(String id) {
        return get("/api/v1/data/" + id, MyResponse.class)
            .onErrorResume(fallbackTo(MyResponse.empty()));
    }
}
```

### WebClient Configuration

Use `WebClientFactory` for pre-configured WebClient instances:

```java
WebClient client = WebClientFactory.create()
    .baseUrl("http://capsule-service:8081")
    .serviceName("capsule")
    .sourceService("my-service")
    .timeout(Duration.ofSeconds(30))
    .propagateTenant(true)
    .propagateTrace(true)
    .build();
```

### Async Kafka Events

Publish events using the unified topic naming convention:

```java
@Autowired
private KafkaTemplate<String, Object> kafkaTemplate;

public void publishEvent(CapsuleCreatedEvent event) {
    Map<String, byte[]> headers = UnifiedEventHeaderBuilder.fromMdc()
        .originSystem("CAPSULE")
        .sourceService("capsule-service")
        .tag("operation", "create")
        .buildAsBytes();
    
    ProducerRecord<String, Object> record = 
        new ProducerRecord<>(ButterflyTopics.CAPSULE_CREATED, event.getId(), event);
    headers.forEach((k, v) -> record.headers().add(k, v));
    
    kafkaTemplate.send(record);
}
```

### WebSocket Streaming

For real-time data streaming, use WebSocket with tracing:

```java
@MessageMapping("rim.updates")
public Flux<RimUpdate> streamRimUpdates(String namespace) {
    return rimService.getUpdates(namespace)
        .doOnSubscribe(s -> log.debug("Client subscribed to RIM updates: {}", namespace))
        .doOnCancel(() -> log.debug("Client unsubscribed from RIM updates"));
}
```

---

## Error Handling

### RFC 7807 Problem Details

All services should return RFC 7807 compliant error responses:

```json
{
  "type": "https://api.butterfly.254studioz.com/errors/not-found",
  "title": "Not Found",
  "status": 404,
  "detail": "Capsule with ID 'abc-123' was not found",
  "instance": "/api/v1/capsules/abc-123",
  "traceId": "trace-456def",
  "timestamp": "2025-01-15T10:30:00Z",
  "service": "capsule"
}
```

### Enable Exception Handler

Import the `ProblemDetailExceptionHandler` in your service:

```java
@SpringBootApplication
@Import(ProblemDetailExceptionHandler.class)
public class MyServiceApplication { }
```

### Custom Error Responses

Create custom problem details:

```java
throw new ResponseStatusException(HttpStatus.CONFLICT,
    ProblemDetail.builder()
        .type(ProblemDetail.ErrorTypes.CONFLICT)
        .title("Resource Conflict")
        .status(409)
        .detail("Capsule already exists with this scope")
        .traceId(MDC.get("traceId"))
        .errorCode("CAPSULE_EXISTS")
        .build());
```

### Fallback Behavior

Implement graceful degradation:

```java
public Mono<TemporalSlice> getSliceWithFallback(String scopeId, Instant timestamp) {
    return capsuleClient.getTemporalSlice(scopeId, timestamp)
        .onErrorResume(ServiceClientException.class, e -> {
            if (e.getStatus() == 503) {
                log.warn("Capsule service unavailable, using cached data");
                return cacheService.getCachedSlice(scopeId, timestamp);
            }
            return Mono.error(e);
        });
}
```

---

## Tenant Propagation

### HTTP Header Propagation

Tenant context is propagated via `X-Tenant-ID` header:

```java
// Reading tenant from incoming request
String tenantId = TenantContextHolder.getTenantId();

// Propagating to outgoing requests (automatic with WebClientFactory)
WebClient client = WebClientFactory.create()
    .propagateTenant(true)
    .build();
```

### Kafka Header Propagation

Include tenant in Kafka message headers:

```java
Map<String, Object> headers = UnifiedEventHeaderBuilder.create()
    .correlationId(MDC.get("correlationId"))
    .build();

// Tenant is automatically added by TenantKafkaInterceptor
kafkaTemplate.send(new ProducerRecord<>(topic, key, value));
```

### JWT Claim Extraction

Extract tenant from JWT:

```java
@Bean
public TenantFilter tenantFilter(JwtClaimsExtractor extractor) {
    return new TenantFilter(extractor);
}
```

The `tenant_id` claim in JWT is automatically extracted and set in `TenantContextHolder`.

---

## Distributed Tracing

### Correlation ID Flow

Every request should carry a correlation ID through the entire flow:

```
Client → API Gateway → Service A → Service B → Database
         ↓              ↓           ↓           ↓
    correlationId  correlationId  correlationId  correlationId
```

### Setting Correlation ID

The `CorrelationIdFilter` automatically sets correlation IDs:

```java
// Access in code
String correlationId = MDC.get("correlationId");
```

### OpenTelemetry Integration

Trace context is automatically propagated:

```java
// In service configuration
@Bean
public TracingAutoConfiguration tracingConfig() {
    return new TracingAutoConfiguration();
}
```

### Kafka Tracing

Use `TracingKafkaInterceptor` for Kafka trace propagation:

```yaml
spring:
  kafka:
    producer:
      properties:
        interceptor.classes: com.z254.butterfly.common.tracing.TracingKafkaInterceptor
```

### Extracting from Kafka Headers

```java
@KafkaListener(topics = ButterflyTopics.CAPSULE_CREATED)
public void handleEvent(ConsumerRecord<String, CapsuleEvent> record) {
    UnifiedEventHeaderBuilder.propagateToMdc(record.headers());
    
    String correlationId = UnifiedEventHeaderBuilder.extractCorrelationId(record.headers());
    String traceId = UnifiedEventHeaderBuilder.extractTraceId(record.headers());
    
    // Process event with proper context
}
```

---

## Event-Driven Communication

### Topic Naming Convention

Use `ButterflyTopics` constants for consistent naming:

| Pattern | Example | Description |
|---------|---------|-------------|
| `{service}.{domain}.{action}` | `capsule.events.created` | Service-specific events |
| `butterfly.{flow}.{stage}` | `butterfly.learning.signals` | Cross-service events |
| `{topic}.dlq` | `capsule.events.dlq` | Dead letter queues |

### Publishing Events

```java
import static com.z254.butterfly.common.kafka.ButterflyTopics.*;

kafkaTemplate.send(CAPSULE_CREATED, capsuleId, event);
kafkaTemplate.send(LEARNING_SIGNALS, signalId, signal);
```

### Consuming Events

```java
@KafkaListener(topics = ButterflyTopics.PERCEPTION_SIGNALS)
public void handleSignal(PerceptionSignal signal) {
    // Process signal
}
```

### Dead Letter Queue Handling

```java
@KafkaListener(topics = ButterflyTopics.DLQ_CAPSULE)
public void handleDlq(ConsumerRecord<String, ?> record) {
    DlqRetryService.process(record);
}
```

### Event Schema Versioning

Always include schema version in events:

```java
UnifiedEventHeaderBuilder.create()
    .schemaVersion("1.2.0")
    .build();
```

---

## Resilience Patterns

### Circuit Breaker

Built into `AbstractButterflyClient`:

```java
// Configure via ClientConfig
ClientConfig.defaults()  // 50% failure rate threshold
ClientConfig.strict()    // 30% failure rate threshold
ClientConfig.fastPath()  // 60% tolerance for real-time ops
```

### Retry with Backoff

```java
return webClient.get()
    .uri("/api/v1/resource")
    .retrieve()
    .bodyToMono(Resource.class)
    .retryWhen(Retry.backoff(3, Duration.ofMillis(500))
        .maxBackoff(Duration.ofSeconds(5))
        .filter(e -> e instanceof WebClientResponseException.ServiceUnavailable));
```

### Health-Based Request Blocking

```java
if (!clientFactory.capsuleClient().isOperational()) {
    return Mono.error(new ServiceUnavailableException("Capsule service degraded"));
}
```

### Timeouts

Use standard timeout constants:

```java
import com.z254.butterfly.common.client.WebClientFactory.Timeouts;

.timeout(Timeouts.READ)        // 30s
.timeout(Timeouts.FAST_PATH)   // 5s
.timeout(Timeouts.LONG_RUNNING) // 5min
```

### Degradation States

Monitor service health states:

| State | Description | Action |
|-------|-------------|--------|
| `HEALTHY` | Service operating normally | Full functionality |
| `DEGRADED` | Some issues detected | Consider fallbacks |
| `IMPAIRED` | Circuit breaker open | Use cached data |
| `UNKNOWN` | Health unknown | Probe cautiously |

---

## Security

### Authentication

All service-to-service calls should use JWT:

```java
WebClient client = WebClient.builder()
    .defaultHeader("Authorization", "Bearer " + jwtToken)
    .build();
```

### API Key Authentication

For external integrations:

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) {
    http.addFilterBefore(new ApiKeyFilter(), UsernamePasswordAuthenticationFilter.class);
    return http.build();
}
```

### Tenant Isolation

Always filter data by tenant:

```java
public Flux<Capsule> getCapsules(String scopeId) {
    String tenantId = TenantContextHolder.getTenantId();
    return capsuleRepository.findByScopeIdAndTenantId(scopeId, tenantId);
}
```

---

## Best Practices

### 1. Always Use Typed Clients

```java
// Good
CapsuleClient client = clientFactory.capsuleClient();

// Avoid
WebClient.create("http://capsule:8081").get()...
```

### 2. Propagate Context

```java
// Always include correlation ID
.header("X-Correlation-ID", MDC.get("correlationId"))
.header("X-Tenant-ID", TenantContextHolder.getTenantId())
```

### 3. Use Standard Topics

```java
// Good
kafkaTemplate.send(ButterflyTopics.CAPSULE_CREATED, event);

// Avoid
kafkaTemplate.send("capsule-created-events", event);
```

### 4. Handle Errors Gracefully

```java
// Good
.onErrorResume(e -> {
    log.warn("Service unavailable: {}", e.getMessage());
    return fallback();
})

// Avoid
.onErrorResume(e -> Mono.error(new RuntimeException(e)))
```

### 5. Set Appropriate Timeouts

```java
// Good - explicit timeout
.timeout(Duration.ofSeconds(5))

// Avoid - no timeout (infinite wait)
.block()
```

### 6. Log with Context

```java
log.info("Processing capsule: id={}, correlationId={}, tenant={}",
    capsuleId, MDC.get("correlationId"), TenantContextHolder.getTenantId());
```

### 7. Version Your APIs

```java
// Use API versioning filter
@GetMapping("/api/v1/capsules")
public Flux<Capsule> getCapsules() { ... }

@GetMapping("/api/v2/capsules")
public Flux<CapsuleV2> getCapsulesV2() { ... }
```

### 8. Document Contracts

Keep contract definitions up to date in:
- `butterfly-common/src/test/resources/contracts/`
- OpenAPI specifications per service

---

## Quick Reference

### Service Ports

| Service | Port | Base URL |
|---------|------|----------|
| PERCEPTION | 8080 | `/api/v1` |
| CAPSULE | 8081 | `/api/v1` |
| ODYSSEY | 8082 | `/api/v1` |
| PLATO | 8083 | `/api/v1` |
| NEXUS | 8084 | `/api/v1` |
| SYNAPSE | 8085 | `/api/v1` |
| CORTEX | 8086 | `/api/v1` |

### Standard Headers

| Header | Description |
|--------|-------------|
| `X-Tenant-ID` | Tenant identifier |
| `X-Correlation-ID` | Request correlation ID |
| `X-API-Version` | API version in response |
| `Accept-Version` | Requested API version |
| `X-Source-Service` | Calling service identifier |

### Kafka Topics

See `ButterflyTopics` class for complete list of standardized topic names.
