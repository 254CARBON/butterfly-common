# High-Level Ecosystem View

The repo is a multi-service ecosystem with clear temporal and governance boundaries:

- **CAPSULE** (`CAPSULE/`)
  - 4D atomic history service (past) with strong temporal intelligence APIs.  
  - Key docs: `CAPSULE/README.md`, `CAPSULE/docs/api/temporal-intelligence.md`.

- **PERCEPTION** (`PERCEPTION/`)
  - Sensory and interpretation layer (present).  
  - Ingests external data, scores trust, detects signals, and proposes scenarios.  
  - Key doc: `PERCEPTION/README.md`.

- **ODYSSEY** (`ODYSSEY/`)
  - World-scale strategy and futures engine (future).  
  - Runs path simulations and strategic reasoning for long-horizon scenarios.  
  - Key doc: `ODYSSEY/README.md`.

- **PLATO** (`PLATO/`)
  - Governance and intelligence control plane (policies, specs, proofs, plans).  
  - Encodes constraints, approvals, and strategic rules.  
  - Key doc: `PLATO/README.md`.

- **SYNAPSE** (`SYNAPSE/`)
  - Execution engine that turns decisions into governed actions.  
  - Hosts tools, workflows, and safety controls.  
  - Key doc: `SYNAPSE/README.md`.

- **CORTEX** (`CORTEX/`)
  - AI agent orchestration layer.  
  - Uses CAPSULE, PLATO, SYNAPSE to run higher-level agentic workflows.  
  - Key doc: `CORTEX/README.md`.

- **butterfly-common** (`butterfly-common/`)
  - Shared domain models, RIM identity, Avro contracts, and security primitives.  
  - Key doc: `butterfly-common/README.md`.

- **NEXUS** (`NEXUS/`)
  - Cognitive integration layer (“cognitive cortex”).  
  - Does **not** own core domain data. Instead it:
    - Reads:
      - Present: PERCEPTION
      - Past: CAPSULE (history + temporal intelligence)
      - Future: ODYSSEY
      - Governance: PLATO
      - Actions: SYNAPSE (execution and action history)
    - Provides:
      - Unified temporal slices (past/present/future + actions)
      - Cross-system reasoning and contradiction detection
      - Strategic option synthesis across all systems
      - Self-optimizing integration and evolution patterns
    - Pushes learning and feedback back into the ecosystem, particularly:
      - PERCEPTION (signal quality, gaps)
      - SYNAPSE (execution performance and safety)
      - PLATO (governance outcomes)

---

# NEXUS Role & Responsibilities

Defined in `NEXUS/README.md` and `NexusApplication.java`, NEXUS is built around four pillars:

1. **Temporal Intelligence Fabric**  
   Unified past/present/future state and actions for any RIM node.

2. **Autonomous Reasoning Engine**  
   Cross-system inference with contradiction detection and resolution.

3. **Predictive Synthesis Core**  
   Strategic option generation and scoring across CAPSULE, PERCEPTION, ODYSSEY, PLATO, SYNAPSE.

4. **Evolution Controller**  
   Learning and self-optimization of integration patterns, rules, and SLO behavior.

### Implementation Style

- **Stack**
  - Spring Boot WebFlux (reactive)
  - Maven parent: `com.z254.butterfly:butterfly-parent`
- **Key libraries**
  - Micrometer + OpenTelemetry
  - Resilience4j
  - Spring Kafka
  - Reactive Redis
  - Caffeine
  - Bucket4j
- **Security**
  - Shared `butterfly-common` security model (JWT + roles/scopes)
  - Rate limiting via Bucket4j

---

# Architecture & Package Structure

## Entrypoint

- `NexusApplication.java`
  - Annotated with `@SpringBootApplication`, `@EnableScheduling`.
  - Wires core services, scheduling, and observability.

## Core Domains

Location: `NEXUS/src/main/java/com/z254/butterfly/nexus/domain`

- **Temporal primitives**
  - `TemporalWindow`: reference time, lookback, lookahead, granularity; factory methods for centered/history-only/future-only windows.
  - Temporal slice model:
    - `domain/temporal/TemporalSlice.java`
    - `HistoricalState`, `CurrentState`, `ProjectedFutures`, `ActionHistory`.

- **Confidence model**
  - `ConfidenceBands`: confidence by system and by CAPSULE C/D/A/X/M components.

- **Synthesis & reasoning**
  - `domain/synthesis/*`
  - `domain/reasoning/*`
  - `domain/feedback/*`
  - `domain/ml/*`

- **Evolution & learning**
  - `domain/evolution/*`
  - Core types: `EcosystemLearningState`, `IntegrationPattern`, `LearningSignal`.

- **Execution**
  - `domain/execution/*`
  - Decision execution state, events, and execution audit trail.

## Service Layers

- `temporal/*`
  - Temporal Intelligence Fabric.

- `reasoning/*`
  - `InferenceGraph`, hybrid rule + ML reasoning, confidence calibration.

- `synthesis/*`
  - Strategic option synthesis, ignorance maps, pluralistic multi-model synthesis.

- `execution/*`
  - Decision execution orchestration and tracking (SYNAPSE integration).

- `evolution/*`
  - Integration health, learning, SLO-driven optimization, advanced evolution and SLO learning.

- `cache/*`, `cache/adaptive/*`
  - Temporal caching backed by Redis with adaptive, ML-based TTL and eviction.

## Integration & Cross-Cutting Packages

- `client/*`
  - Typed reactive clients for:
    - CAPSULE
    - PERCEPTION
    - ODYSSEY
    - PLATO
    - SYNAPSE
    - ML scoring services

- `kafka/*`
  - Event ingestion and publishing across systems.

- `persistence/*`
  - Redis repositories for:
    - Rules
    - Patterns
    - Learning signals
    - Contradictions
    - Other integration artifacts

- `api/*`
  - REST controllers for:
    - Temporal
    - Reasoning
    - Synthesis
    - Evolution
    - Experiments
    - Divergence
    - DLQ
    - Decision execution

- `api/websocket/*`
  - WebSocket handlers for progressive streaming use cases.

- Cross-cutting utilities:
  - `metrics/*`
  - `tracing/*`
  - `validation/*`
  - `health/*`
  - `security/*`
  - `config/*`

---

# Temporal Intelligence Fabric

### Key Types

- **`TemporalWindow`**
  - Validated record describing:
    - `referenceTime`
    - `lookback`
    - `lookahead`
    - `granularity`
  - Factory methods for:
    - Centered windows
    - History-only windows
    - Future-only windows

- **`TemporalSlice`** (`domain/temporal/TemporalSlice.java`)
  - Composes:
    - Past: `HistoricalState` (from CAPSULE)
    - Present: `CurrentState` (from PERCEPTION RIM)
    - Future: `ProjectedFutures` (from ODYSSEY)
    - Actions: `ActionHistory` (from SYNAPSE)
  - Enrichments:
    - `ConfidenceBands`
    - Cross-system correlations
    - `generatedAt`
    - `UncertaintySummary` (ML-derived)
  - Health/consistency helpers:
    - `isComplete()`
    - `hasContradictions()`
    - `healthStatus()` → `HEALTHY`, `PARTIAL`, `CONTRADICTED`, `LOW_CONFIDENCE`, `UNVERIFIED_ACTIONS`, `NO_DATA`

### TemporalQueryService

- **Interface**
  - `Mono<TemporalSlice> getTemporalSlice(String nodeId, TemporalWindow window)`  
  - Bulk and paginated variants for high-throughput use.

- **Implementation (`TemporalQueryServiceImpl`)**
  - Observability:
    - Starts a Micrometer timer per query.
    - Tags by node type and status.
    - Enforces p99 SLO (`< 100 ms` target per README).
    - Emits SLO metrics; logs explicit SLO violation events.
  - Caching:
    - Uses `TemporalCache` (Redis-backed, JSON-encoded slices).
    - SLO-aware TTLs; caches only sufficiently confident slices.
    - Namespaced keys: `nexus:temporal:...`.
    - Invalidation:
      - Node-specific
      - Full flush
  - Data fan-out (reactive, parallel):
    - CAPSULE client:
      - History, causal chains, temporal intelligence.
      - Failures: logged + graceful degradation, returns partial slices.
    - PERCEPTION client:
      - Current state and signals.
    - ODYSSEY client:
      - Future paths and scenario projections.
    - SYNAPSE client:
      - Actions and outcomes inside the requested window.
  - Composition:
    - Builds a `TemporalSlice` with:
      - Cross-system correlations (alignment/contradiction scores)
      - Confidence bands by system and component
    - Calls `TemporalUncertaintyService` to annotate uncertainty from ML metadata.

### Adaptive Caching

- **`TemporalCache`**
  - Redis storage for slices and causal chains.
  - Integrated with access pattern analysis.

- **Adaptive cache (under `cache/adaptive/*`)**
  - `CacheAccessPatternAnalyzer`
    - Computes patterns: `HOT`, `WARM`, `COLD`, `EVICTION_CANDIDATE`.
    - Uses frequency, recency, and size metrics.
  - `MLCacheEvictionPolicy`, `PredictiveCacheWarmer`
    - Use patterns and optional ML scoring.
    - Adjust TTLs and proactively warm frequently accessed slices.

---

# Autonomous Reasoning Engine

### Core Interface: `InferenceGraph`

Responsibilities:

- Rule management:
  - Registers built-in and dynamic `InferenceRule`s.
  - Persists rules via `RedisInferenceRuleRepository`.
- Inference operations:
  - `infer(...)`, `inferWithRules(...)`
  - `detectContradictions(...)`
  - `resolveContradiction(...)`
  - `getActiveContradictions(...)`
- Context control (`InferenceContext`):
  - Depth
  - Minimum confidence
  - Included / excluded systems
  - Inclusion of CAPSULE counterfactuals and ODYSSEY futures
  - Auto-resolution settings for contradictions

### Implementation: `InferenceGraphImpl`

- In-memory rules cache with bootstrap of cross-system rules:
  - PERCEPTION–CAPSULE trust consistency
  - ODYSSEY–PERCEPTION alignment
  - Additional cross-service consistency rules
- Fact gathering:
  - Pulls from CAPSULE, PERCEPTION, ODYSSEY (and PLATO as needed).
  - Uses reactive clients and Kafka feeds.
- Inference:
  - Forward-chaining reasoning.
  - Optional ML integration via `MlEnhancedInferenceService`:
    - ML models supply calibrated confidence and uncertainty.
    - Results feed back via `LearningSignal` events.

### Contradiction Management

- Storage:
  - `RedisContradictionRepository` for detected contradictions.
- Resolution strategies:
  - Prefer higher-trust system.
  - Wait for more data.
  - Hybrid/weighted strategies as implemented.
- Observability:
  - Metrics:
    - Starts/completed/failed inferences
    - Rule registrations
    - Contradiction counts
  - Tracing:
    - `TracingAspect` wraps `InferenceGraph` methods.
    - Creates Observations with node IDs and correlation IDs.

---

# Predictive Synthesis Core

### Interface: `StrategicOptionSynthesizer`

Main operations:

- `synthesizeOptions(scopeId, SynthesisConfig)`
  - Core strategic synthesis pipeline.
- `scoreOption(option, ScoringCriteria)`
  - Cross-system scoring and ranking.
- `generateIgnoranceMap(scopeId)`
  - Identifies blind spots and data gaps.
- `triggerScenarioCascade(scenarioId, scopeId)`
  - Takes a PERCEPTION scenario → produces enriched options.
- `getTopOptions(scopeId, limit)`
  - Convenience endpoint for ranked options.

### Implementation: `StrategicOptionSynthesizerImpl`

- Inputs:
  - PERCEPTION: scenarios and signals for `scopeId`.
  - ODYSSEY: narratives and path forecasts.
  - CAPSULE: counterfactual structure and historical capsules.
  - PLATO: governance constraints and policies (via `PlatoGovernanceHooks`).

- Degradation modes:
  - Computes `DegradationStatus` based on system health:
    - `FULL`, `PARTIAL`, `MINIMAL`.
  - Behavior adjusts accordingly; emits metrics when degraded.

- Governance coupling:
  - `SynthesisConfig.includeGovernanceCheck` flag.
  - `validateWithPlatoIfEnabled(...)`:
    - Wraps PLATO calls with timeout + metrics.
    - On slow/unavailable PLATO:
      - Marks options as `NOT_EVALUATED`.
      - Does not fail synthesis outright.

- Pluralistic synthesis (`synthesis/pluralism/*`):
  - `MultiModelSynthesizer`, `SynthesisSchool`:
    - Combine multiple synthesis schools (risk-minimizing, exploration-favoring, etc.).
  - `EcosystemDivergence`:
    - Summarizes divergence between schools and models.

- Performance:
  - Timed via Micrometer.
  - Target SLOs:
    - Full synthesis (with governance): p99 ≈ 5 s.
    - Governance-free: p99 ≈ 3 s.
  - Concurrency limits to avoid overload; reactive cancellation on timeouts.

---

# Evolution Controller & Learning

### Public API: `EvolutionController` (`/api/v1/evolution`)

Endpoints:

- Health and patterns:
  - `GET /health`
  - `GET /patterns`
  - `GET /patterns/{patternId}`
- Outcome recording:
  - `POST /outcomes`
    - Records success/failure + latency for an `IntegrationPattern`.
- Optimization:
  - `GET /recommendations`
  - `POST /auto-tune`
  - SLO-aware tuning based on request payloads.

### Internal Services

- **`IntegrationHealthService`**
  - Computes:
    - Health scores
    - Recommendations
    - Tuning actions (cache, clients, SLO configs, etc.)

- **NexusMetrics + Micrometer `MeterRegistry`**
  - Reads and correlates:
    - Cache metrics
    - Client error rates
    - SLO violation metrics

- **Resilience4j `CircuitBreakerRegistry`**
  - Tracks state of all major clients.

### Domain: `EcosystemLearningState`

Contains:

- Per-system `SystemPerformanceMetrics`
- Cross-system correlations
- Learning rates
- Monthly improvement rates
- Active experiments and recommendations

Key helpers:

- `isMeetingImprovementTarget()`:
  - Returns true if monthly improvement ≥ 10%.
- `getOverallHealthScore()`
- `getUnderperformingSystems()`
- `getImprovingSystems()`
- `getStrongestCorrelations()`

Recommendations:

- `LearningRecommendation` with enums like:
  - `INCREASE_SIGNAL_FREQUENCY`
  - `TUNE_CONFIDENCE_THRESHOLD`
  - `RUN_EXPERIMENT`
  - Other evolution actions.

### Persistence

- `RedisIntegrationPatternRepository`
- `RedisLearningSignalRepository`
- `RedisRuleAdjustmentRepository`
- Related stores for rules, patterns, feedback.

Advanced logic:

- `evolution/advanced/*`, `evolution/slo/*`
  - SLO learning
  - Drift detection
  - Experiment management
  - Ties learning directly into SLO violations and auto-tuning.

---

# Decision Execution & SYNAPSE Integration

### API: `DecisionExecutionController` (`/api/v1/execution/decisions`)

Implements the Phase 6 decision → execution lifecycle:

- `POST /`
  - Submit a decision with options, governance context, and metadata.
  - Returns a tracking ID.
- `GET /{id}`
  - Current execution status.
- `GET /{id}/stream`
  - SSE/stream of execution status updates.
- `POST /{id}/cancel`
- `POST /{id}/retry`
- `POST /{id}/rollback`
- `GET /`
  - List executions with filters.

### Service: `DecisionExecutionServiceImpl` (under `execution/impl`)

- Creates `DecisionExecution` records:
  - Timestamps
  - Retry counts
  - Status transitions
  - Audit events
- State management:
  - Uses Redis (`ReactiveStringRedisTemplate` or similar).
  - Maintains index from SYNAPSE decision IDs to NEXUS executions.
- Integration with SYNAPSE (`SynapseClient`):
  - `submitDecision(DecisionExecutionRequest)`
  - `getDecisionStatus(...)`
  - `streamDecisionStatus(...)`
  - `cancelDecision(...)`
  - `listDecisions(...)`
  - `getDecisionInvocations(...)`

### Streaming & Control

- Streaming:
  - `Reactor Sinks.Many<DecisionExecution>` per execution ID.
  - Multiplexes updates to multiple subscribers.
  - Auto-stops after terminal state.

- Cancellation & retry:
  - Enforces constraints:
    - `canCancel(...)`, `canRetry(...)`.
  - Increments counters and republishes to SYNAPSE as needed.

- Metrics:
  - Counters:
    - Submitted, cancelled, failed decisions.
  - Timers:
    - Submission latency
    - Status polling
  - SLO-aware metrics for end-to-end decision loops.

### SynapseClient DTOs

- Governance context:
  - `DecisionGovernanceContext`
- Options:
  - `DecisionOption`:
    - Execution mode
    - Confidence
    - Expected impact
    - `requiresApproval`
- Safety limits:
  - `getSafetyLimitsStatus(...)`
  - `updateBlastRadiusLimit(...)`
- Actions and outcomes aligned with Avro schemas.

### Golden Loop Validation

- `GoldenLoopValidator`, `LoopCompletionTracker` (under `validation/*`):

Track the full loop:

1. PERCEPTION signal  
2. NEXUS reasoning/synthesis  
3. PLATO governance  
4. SYNAPSE execution  
5. PERCEPTION outcome  

Metrics:

- `butterfly.golden_loop.completion`
- In-flight loop counts
- Per-phase latency and failure/timeouts

---

# Cross-Cutting Concerns

## HTTP Clients

- `client/config/TracePropagatingWebClientFactory`
  - Builds `WebClient` instances with:
    - Trace propagation
    - Common timeouts and retries
    - Config from `NexusClientProperties`
- Service-specific WebClient clients:
  - `WebClientCapsuleClient`
  - `WebClientPerceptionClient`
  - Similar for ODYSSEY, PLATO, SYNAPSE, ML services.

## Kafka

- `KafkaConfig`:
  - Producer factory
  - Schema registry integration
  - Topic definitions (via `TopicBuilder`)
  - Avro serializer
  - Reliability settings:
    - `acks=all`
    - Retries
    - Backoff

- Consumers/producers for:
  - PERCEPTION events
  - CAPSULE events
  - ODYSSEY events
  - SYNAPSE events
  - Learning signals:
    - `LearningSignalConsumer`, `LearningSignalPublisher`
  - Governance feedback:
    - `GovernanceFeedbackPublisher`

- `KafkaTracePropagator`
  - Injects/extracts trace context into Kafka headers.

## Observability

- `MetricsConfig`, `metrics/*`:
  - `TemporalMetrics`
  - `ReasoningMetrics`
  - `GoldenLoopMetrics`
  - `NexusMetrics`
  - Standard timers/counters/gauges with consistent tags.

- `NexusTracingConfig`, `TracingAspect`:
  - AspectJ-based tracing for:
    - Temporal
    - Reasoning
    - Synthesis
    - Evolution services
  - Wraps `Mono`/`Flux` results with Observations and spans.

## Security

- `SecurityConfig`:
  - JWT-based auth via `butterfly-common`:
    - `ButterflyPrincipal`
    - `ButterflyRole`
    - `SecurityContextPropagator`
  - Role mapping:
    - Temporal & reasoning `GET`:
      - `VIEWER`, `ANALYST`, `DEVELOPER`, `OPERATOR`, `ADMIN`, `GOVERNED_ACTOR`, `READER`, `WRITER`
    - Temporal & reasoning `POST`:
      - Writer-class roles
    - Synthesis:
      - Higher-privilege roles only
    - Evolution:
      - `GET`: `OPERATOR`, `ADMIN`, `APPROVER`
      - `POST`: `ADMIN`, `APPROVER`
  - Conditional security disable:
    - `nexus.security.enabled=false` for dev/test only.

- `RateLimitingFilter`:
  - Bucket4j-based reactive filter.
  - Enforces per-key quotas.
  - Exposes rate-limit metrics via Micrometer.

## API Surface

- Controllers under `api/*` cover:
  - Temporal
  - Reasoning
  - Synthesis
  - Evolution
  - Experiments
  - Divergence
  - Global learning
  - Policy learning
  - DLQ administration
  - Decision execution

- `OpenApiConfig`, `NexusConfig`:
  - OpenAPI configuration with:
    - Rich descriptions
    - Contact info
    - License metadata

---

# Quality, Tests, and Documentation

## Tests

Location: `NEXUS/src/test/java`

- Focus areas:
  - Kafka wiring and integration
  - ML-enhanced inference behavior
  - PLATO governance contract tests
  - SYNAPSE outcome consumer
  - Integration health and degradation modes
  - `TemporalWindow` validations and edge cases
- Tooling:
  - Testcontainers for Kafka
  - Spring reactive test stack

## Documentation & Runbooks

- `NEXUS/README.md`
  - High-level capabilities, phases, SLOs, and performance targets.
  - Directly mapped to classes and metrics in code.

- `ENGINEERING_ROADMAP.md`
  - Phased delivery plan and future capabilities.

- `NEXUS/docs/runbooks/*`
  - Temporal recovery
  - Incident response
  - Scaling and performance tuning
  - Operational playbooks for on-call.

## Configuration

- Strongly typed properties:
  - `TemporalSloProperties`
  - `TemporalLearningProperties`
  - `AdaptiveCacheProperties`
  - `ReasoningProperties`
  - `PerceptionFeedbackProperties`
  - Others tied to SLO/ML/evolution features.
- Tuning:
  - Explicit and testable via configuration + property-driven tests.

---

# Strengths, Risks, and Recommendations

## Strengths

- **Clear separation of concerns**
  - NEXUS orchestrates; it does not own core data.
  - Temporal, reasoning, synthesis, execution, and evolution layers are clearly delineated.

- **Strong observability**
  - Metrics, tracing, and SLO tracking are first-class.
  - Golden loop validation gives a full closed-loop view.

- **Resilience by design**
  - Explicit degradation modes.
  - Circuit breakers and timeouts around all critical dependencies.
  - DLQ awareness and adaptive caching strategies.

- **Deep governance integration**
  - PLATO and SYNAPSE embedded in synthesis and execution paths.
  - Decisions have rich audit trails and governance context.

- **Temporal unification**
  - `TemporalSlice` is a coherent abstraction that aligns neatly with CAPSULE, PERCEPTION, and ODYSSEY capabilities.

## Risks / Considerations

- **System complexity**
  - Large number of moving parts (caches, ML, rules, multiple services, Kafka, Redis).
  - End-to-end debugging and mental modeling are non-trivial.
  - Runbooks and automated diagnostics are critical.

- **ML dependencies**
  - Evolution and adaptive caching rely on ML scoring and learning signals.
  - Misconfiguration, model drift, or bad feedback loops can silently degrade performance or option quality.

- **Security configuration**
  - Default “mock” JWT decoder when `issuerUri` is blank is acceptable for dev/test.
  - Must be strictly prohibited from reaching production; CI/infra checks should enforce this.

- **Cross-service schema coupling**
  - Heavy use of Avro/JSON contracts across CAPSULE, PERCEPTION, ODYSSEY, PLATO, SYNAPSE, and NEXUS.
  - Coordination cost on upgrades is non-trivial.
  - Tests such as `PlatoGovernanceContractTest` and `KafkaIntegrationTest` mitigate this but must remain current.

- **Operational load and SLO pressure**
  - Temporal slice SLOs (`< 100 ms`) and synthesis SLOs (`< 5 s`) are heavily dependent on upstream health and infra (Kafka, Redis, services).
  - Capacity planning and performance tuning must be done **end-to-end**, not just at NEXUS.

