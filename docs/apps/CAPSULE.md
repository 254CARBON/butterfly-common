# CAPSULE: 4D Atomic History Service

## Role in the BUTTERFLY Ecosystem

CAPSULE is the 4D atomic history service for BUTTERFLY. It stores immutable, richly structured **CAPSULEs** that capture:

- State
- Dynamics
- Agency
- Counterfactuals
- Meta

for a given **scope** and **temporal resolution**.

Key references:

- `BUTTERFLY/CAPSULE/README.md`
- `BUTTERFLY/CAPSULE/docs/concepts/README.md`
- `BUTTERFLY/CAPSULE/src/main/java/com/z254/butterfly/capsule/domain/model/Capsule.java`

Within the ecosystem:

- **PERCEPTION** = sensory / present
- **CAPSULE** = memory / past
- **ODYSSEY** = strategic future
- **NEXUS** = cognitive cortex unifying past/present/future

NEXUS depends heavily on CAPSULE for temporal intelligence and cross-system reasoning (`BUTTERFLY/NEXUS/README.md`).

CAPSULE also backs the **Reality Integration Mesh (RIM)** used by PERCEPTION via:

- Canonical scope identifiers (`RimNodeId`)
- Temporal queries over CAPSULE histories

Key references:

- `BUTTERFLY/CAPSULE/src/main/java/com/z254/butterfly/capsule/service/CapsuleService.java`
- `BUTTERFLY/PERCEPTION/README.md`

---

## Conceptual Model: What a CAPSULE Is

A **CAPSULE** is a 4D atomic unit of history for:

- A fixed **scope**
- A fixed **time resolution** `Δt`
- A defined **time horizon**
- A specific **vantage**

See `docs/concepts/README.md`.

### Capsule Aggregate Root

Modeled in `Capsule.java` as an aggregate root with the following components:

1. **Header**

   - Identity
   - Time parameters
   - Scope
   - Vantage
   - Lineage (including revision and schema version)

2. **Configuration (C)**

   - Entities, relations, and fields describing what exists.

3. **Dynamics (D)**

   - Derivatives, patterns, and stability metrics describing how things change.

4. **Agency (A)**

   - Agents, intents, and motives.

5. **Counterfactual (X)**

   - Future paths, tipping points, and sensitivities.

6. **Meta (M)**

   - Confidence, data quality, uncertainty, provenance, constraints.

### Historical Structure

- Histories are **time-ordered sequences** (or DAGs) of CAPSULEs keyed by `(scope, Δt, vantage)`.
- They obey a **Markov-at-resolution** property:  
  At resolution `Δt`, the current CAPSULE is sufficient to predict the next.

See `docs/concepts/README.md`.

---

## Service Architecture

CAPSULE is implemented as a **Spring Boot 3 / Java 17** service with a layered architecture (`docs/architecture/system-design.md`).

### Layers

- **API layer** – `com.z254.butterfly.capsule.api`
  - REST controllers
  - Exception handling
  - API versioning

- **Service layer** – `com.z254.butterfly.capsule.service` and specialized services
  - History services
  - Validation
  - Temporal queries
  - Audit
  - ML and RAG integrations
  - Other orchestration services

- **Domain model layer** – `com.z254.butterfly.capsule.domain.model`
  - Capsule aggregate
  - Rich subtypes for header, time, scope, etc.

- **Repository layer** – `com.z254.butterfly.capsule.domain.repository`
  - Spring Data Cassandra repositories

- **Infrastructure layer**
  - Cassandra
  - Redis
  - JanusGraph
  - Kafka
  - Redis cache
  - Metrics / tracing configuration

### Cross-Cutting Concerns

- **Security**
  - JWT, RBAC, rate limiting
  - `SecurityConfig`, `Jwt*`
  - `src/main/java/com/z254/butterfly/capsule/security`
  - `docs/security/*`

- **Observability**
  - Micrometer, OpenTelemetry, Prometheus, Grafana
  - `docs/observability/*`
  - `TracingConfig.java`, `MetricsConfig.java`

- **Resilience**
  - Resilience4j circuit breakers and retries
  - Structured fallbacks in temporal and core services
  - `TemporalQueryService.java`, `CapsuleService.java`
  - `docs/architecture/system-design.md`

---

## Data & Storage Model

Primary data store: **Apache Cassandra 5.0+**, optimized for temporal and scope-based access (`docs/architecture/data-model.md`).

### Cassandra Tables

- **Canonical aggregate**

  - `capsules` – stores the full CAPSULE aggregate.

- **Denormalized tables**

  - `capsules_by_scope_time` – scope + time-window queries.
  - `capsules_by_scope_vmode_time` – separates REAL vs PREDICTIVE vantages.
  - `capsules_by_vantage` – vantage-centric access.
  - `capsules_by_label` – tag/label-based lookups.
  - `capsule_revisions` – revision chains.
  - `capsules_by_lineage_key` – idempotency via lineage keys.

### Schema Alignment

- Java domain model mirrors CQL UDTs such as:
  - `header_type`
  - `time_parameters_type`
  - `scope_type`
- Domain model remains **schema-aware** and consistent with `docs/architecture/data-model.md`, `Capsule.java`, and related header/time/scope classes.

### Caching Strategy

- Redis + in-process caches for:
  - Point lookups
  - Query result caching
- No cache invalidation is needed due to CAPSULE **immutability**; caches use TTL-based expiry only.

---

## Core Service Logic: CAPSULE Lifecycle

`CapsuleService` is the central orchestration point for CAPSULE CRUD operations:

- `src/main/java/com/z254/butterfly/capsule/service/CapsuleService.java`

### Create Flow

1. **Populate derived fields**

   - Derives:
     - `scopeId`
     - `centerTsUtc`
     - `vantageMode`
     - `observerId`
     - `tags`
   - Extracted from header, time parameters, and vantage.

2. **Idempotency & lineage**

   - Ensures an idempotency key: `lineage.lineageKey`
   - Generated via `IdempotencyKeyGenerator` and `RimNodeId`.
   - Checks `CapsuleByLineageKeyRepository` to avoid duplicates.

3. **Validation**

   - Uses `ValidationService` for:
     - Schema validation
     - Range checks
     - Reference checks
     - Domain rule enforcement

4. **Persistence**

   - Writes to:
     - `capsules`
     - All relevant denormalized tables
   - Records lineage projections.

5. **Event emission**

   - Publishes Kafka events via `CapsuleKafkaPublisher` when NEXUS integration is enabled.
   - Enables:
     - Learning loops
     - Contradiction detection
     - Temporal intelligence in NEXUS.

6. **Tenant accounting**

   - Records tenant usage:
     - Write counts
     - Storage estimates via `TenantUsageService`.

### Read & Query

- **Point lookups**
  - `getCapsuleById`:
    - Uses primary `capsules` table.
    - Caches results.
    - Tracks tenant read usage.

- **History queries**
  - `queryCapsules(scopeId, fromTs, toTs)` and vantage-aware variants.
  - Use:
    - `capsules_by_scope_time`
    - `capsules_by_scope_vmode_time`
    - `capsules_by_vantage`

- **Search & pagination**
  - Scope-based filter queries with pagination, counts, and basic search primitives.

- **Bulk operations**
  - `bulkCreate`, `bulkValidate`:
    - Deduplicate by lineage key.
    - Parallelize processing.
    - Publish batch events.

- **Revision chains**
  - `getRevisionChain`:
    - Prefer `capsule_revisions` table.
    - Fallback to header lineage queries if needed.

---

## API Surface

CAPSULE exposes **REST**, **GraphQL**, **gRPC**, and **WebSocket/STOMP** APIs.

### REST API

Docs and code:

- `docs/api/reference.md`
- `src/main/java/com/z254/butterfly/capsule/api/*`

#### Core CAPSULE REST Endpoints (CapsuleController)

- `POST /api/v1/capsules` – Create CAPSULE.
- `GET /api/v1/capsules/{id}` – Retrieve CAPSULE by ID.
- `GET /api/v1/capsules` – Filter by scope, vantage, time, labels, with pagination.
- `POST /api/v1/capsules/search` – Advanced search.
- `GET /api/v1/capsules/{id}/revisions` – Revision chain.
- `POST /api/v1/capsules/validate` – Validation only.
- `POST /api/v1/capsules/validate/bulk` – Bulk validation.
- `POST /api/v1/capsules/bulk` – Bulk create.

#### History & Temporal REST Endpoints

- `HistoryController`
  - `GET /api/v1/history` – Time-ordered sequences.

- `TemporalQueryController`
  - `GET /api/v1/temporal/state-at`
  - `GET /api/v1/temporal/diff`
  - `GET /api/v1/temporal/timeline`
  - `POST /api/v1/temporal/slice`
  - `POST /api/v1/temporal/aggregate`
  - `POST /api/v1/temporal/correlations`

#### Tenant & Governance REST Endpoints

- `TenantController`
  - `/tenants/{id}/usage`
  - `/tenants/{id}/usage/daily`
  - `/tenants/{id}/export/*` (usage exports)
  - Reflected in `docs/api/reference.md`.

- `AuditController`, `ValidationController`, `SsoAdminController`
  - Audit logging and views
  - Validation workflows
  - SSO admin operations

#### ML and RAG REST Endpoints

- `MLController` + services:
  - `PredictionService`
  - `AnomalyDetectionService`
  - `PatternDetectionService`
- Capabilities:
  - Predictive APIs (e.g., next state outcome prediction).
  - Anomaly and pattern detection over CAPSULE histories.

- `RAGController`, `CapsuleRAGService`
  - Retrieval-augmented generation over CAPSULE histories.

### GraphQL API

Docs and code:

- `docs/api/reference.md`
- `src/main/java/com/z254/butterfly/capsule/graphql/*`

Resolvers:

- `CapsuleQueryResolver`
- `CapsuleMutationResolver`
- `CapsuleSubscriptionResolver`
- `PredictionQueryResolver`
- `DetectionQueryResolver`

Coverage:

- CAPSULE CRUD and retrieval
- History windows
- Search
- Temporal operations
- ML endpoints

### gRPC API

Code:

- `src/main/proto`
- `CapsuleGrpcService.java`

Capabilities:

- CAPSULE CRUD
- Temporal queries (state-at, slice, diff, aggregation, correlations)
- Streaming APIs (e.g. `StreamCapsules`)
- High-throughput service-to-service integrations

### WebSocket / STOMP API

Code and docs:

- `CapsuleWebSocketConfig.java`
- `CapsuleWebSocketHandler.java`
- `docs/api/reference.md`

Capabilities:

- Real-time CAPSULE subscriptions per scope
- WebSocket equivalents of query and temporal operations

Endpoints:

- Base WebSocket: `/ws`
- STOMP destinations:
  - `/app/capsules/*`
  - `/topic/capsules/scope/{scopeId}`

---

## Temporal Intelligence Layer

Documented in:

- `docs/api/temporal-intelligence.md`

Implemented by:

- Controller:
  - `TemporalIntelligenceController.java`
- Services:
  - `TemporalCausalityEngine`
  - `StateVelocityAnalyzer`
  - `TemporalCompressionService`
  - `IntelligentPrefetchService`
  - `ComplexEventProcessor`
  - Packages: `src/main/java/com/z254/butterfly/capsule/temporal/*`, `stream/cep`, `tiering/service`

### Capabilities

#### Causality Analysis

- `POST /api/v1/temporal-intelligence/causality/analyze`
- Techniques:
  - Granger causality
  - Cross-correlation
  - Transfer entropy
- Additional features:
  - Interpretation block
  - Rolling analysis
  - Optimal lag detection
  - Causal breakpoint detection

#### State Velocity & Momentum

- `POST /api/v1/temporal-intelligence/velocity/analyze`
- `GET /api/v1/temporal-intelligence/velocity/snapshot/{scopeId}`
- `GET /api/v1/temporal-intelligence/velocity/anomalies/{scopeId}`

Outputs:

- Velocity
- Acceleration
- Momentum
- Volatility
- Trend classification

#### Temporal Compression

- `POST /api/v1/temporal-intelligence/compression/estimate`
- Uses:
  - `HistoryService` to fetch CAPSULE sequences
  - `TemporalCompressionService` to compute similarity and estimated compression ratio

#### Intelligent Prefetch

- Prefetch statistics and recommendations
- Manual prefetch triggers
- Backed by:
  - Access-pattern learning
  - Tiered storage integration

#### Complex Event Processing (CEP)

- CEP statistics endpoints
- Pattern registry and event polling endpoints
- `ComplexEventProcessor` consumes CAPSULE streams and registered patterns

### Technical Characteristics

- Heavy usage of Resilience4j and Micrometer timers around temporal operations (`TemporalQueryService.java`).
- Caching on expensive temporal operations (e.g., `@Cacheable` on `getStateAt` and `aggregate`).
- Temporal correlation logic:
  - Bucketization
  - Pearson correlation
  - Approximate p-values
  - Significance flags

---

## Streaming, Search, and Tiered Storage

### Kafka Streaming

- `CapsuleKafkaPublisher` publishes CAPSULE creation events:
  - For NEXUS
  - For CEP and ML consumers
- Configuration and integration:
  - `KafkaConfig.java`
  - `NexusIntegrationProperties.java`
  - `kafka` package

### Tiered Storage

- **Hot tier**
  - Cassandra CAPSULE and index tables

- **Cold tier**
  - Apache Iceberg via Nessie catalog
  - Used for embeddings and older data slices
  - Managed by tiering services:
    - `tiering` package
    - `docs/architecture/tiered-storage.md`

### Graph / JanusGraph

- Config:
  - `JanusGraphConfig.java`
  - `JanusGraphSchemaManager.java`
- Backend:
  - CQL backend
  - Elasticsearch index
- Graph schema:
  - Capsules
  - Actors
  - Paths
  - Episodes
- APIs:
  - `GraphService`, `GraphController`
  - Example endpoints:
    - `GET /api/v1/capsules/{id}/graph`
    - `POST /api/v1/capsules/query` (graph semantics)

### Vector / RAG

- `vector` package:
  - Embedding pipelines
  - Vector index
  - Vector storage
- Used by:
  - `CapsuleRAGService`
  - Hybrid search (e.g., time-decayed semantic search in `docs/api/temporal-intelligence.md`)
- Integrates with:
  - ODYSSEY
  - SYNAPSE
  - via NEXUS

---

## Multi-Tenancy and Governance

### Tenant Model

Code: `tenant` package:

- Entities and repositories:
  - `Tenant`
  - `TenantEntity`
  - `TenantRepository`
  - `TenantUsageRecord`
  - `TenantUsageRepository`
  - `TenantUsageService`
  - `TenantQuotaService`

Runtime enforcement:

- `TenantFilter`
- `TenantContext`
- Ensure tenant-scoped requests.

Usage exports:

- `TenantUsageExportService`
- CSV-based exports for billing and reporting
- Exposed via tenant endpoints (see `docs/api/reference.md`).

### Governance & Audit

- `AuditService`, `AuditController`, `AuditAspect`:
  - Audit logging for all mutating operations.

- Constraints framework:
  - `domain.model.constraint.*`
  - `ConstraintValidationService`
  - Enforces domain invariants.
  - Violations surfaced via validation endpoints.

- Governance docs:
  - `docs/governance/*`

---

## ML & MLOps Integration

ML packages:

- `ml/serving`
- `ml/prediction`
- `ml/registry`
- `ml/tracking`
- `ml/detection`
- `MLController.java`

Capabilities:

- Prediction APIs (e.g., “next state outcome” prediction; see `docs/sdks/README.md`).
- Anomaly detection over CAPSULE histories.
- Pattern detection over temporal sequences.

MLOps API (`docs/api/mlops.md`):

- Model registration
- Versioning
- Health checks
- Tracking (metrics, events, evaluations)

---

## Client SDKs and UI

### SDKs

- `capsule-sdk-java`
- `capsule-sdk-python`
- `capsule-sdk-typescript`

Features:

- Authentication
- CAPSULE CRUD
- History queries
- Validation
- Bulk operations
- Temporal and ML endpoints
- Retries and structured error typing

Configuration:

- Environment-driven:
  - `CAPSULE_BASE_URL`
  - `CAPSULE_API_KEY`
  - Other env vars as documented in `docs/sdks/README.md` and per-language docs.

### capsule-ui

Location:

- `capsule-ui/`

Implementation:

- React / TypeScript front-end
- Components, pages, hooks, and tests (`capsule-ui/src/*`)

Capabilities:

- CAPSULE timelines and histories
- Temporal analytics:
  - Velocity
  - Causal graphs
- Tenant usage dashboards
- ML outputs and results

Testing and design:

- Playwright-based E2E tests
- Design documentation: `capsule-ui/docs/design`

---

## Operational & Deployment Model

Deployment docs:

- `docs/operations/deployment.md`

### Deployment Modes

- **Local**
  - Maven + Dockerized Cassandra

- **Docker Compose**
  - Full stack via `docker-compose.yml`

- **Kubernetes**
  - Helm chart: `k8s/helm/capsule`
  - Production values: `values-prod.yaml`

### Production Considerations

- **High Availability**
  - Multi-replica deployment
  - Pod Disruption Budgets (PDB)
  - Anti-affinity rules
  - Topology spread constraints
  - Horizontal Pod Autoscaler (HPA)

- **Secrets**
  - JWT secrets
  - Cassandra credentials
  - Managed via Kubernetes Secrets

- **Health Checks**
  - `/actuator/health`
  - `/health/liveness`
  - `/health/readiness`
  - Used by liveness/readiness probes

- **Monitoring**
  - Prometheus scraping
  - Grafana dashboards
  - SLOs and alerting:
    - `docs/operations/monitoring.md`
    - `docs/operations/alerting.md`
    - `docs/operations/slo.md`

- **Maintenance**
  - Cassandra repairs and compactions
  - Rolling updates
  - Troubleshooting commands
  - All codified in operations docs

---

## Integration with Other BUTTERFLY Systems

### NEXUS

- Consumes CAPSULE creation events via Kafka (`CapsuleKafkaPublisher`).
- Uses:
  - `TemporalQueryService`
  - Temporal Intelligence APIs
- Provides unified past/present/future queries (see NEXUS README temporal fabric examples).
- Feeds back learning signals via:
  - `LearningSignalService`
  - NEXUS callbacks in `nexus` package of CAPSULE
- Supports trust modeling and model refinement.

### PERCEPTION

- Uses CAPSULE as the **memory backing** for the Reality Integration Mesh (RIM).
- Scope IDs aligned via `RimNodeId`.
- PERCEPTION ingestion and signals are materialized as CAPSULE histories.
- Enables temporal analytics and ML over perception outputs.

### ODYSSEY

- ODYSSEY’s strategic paths and projected futures are reconciled with CAPSULE’s realized histories via NEXUS evolution loops.
- Example pattern (documented in `BUTTERFLY/NEXUS/README.md`):
  - Realized futures adjust ODYSSEY path weights.
- CAPSULE counterfactuals provide:
  - The realized/realizable branch structure that ODYSSEY reasons over.

### PLATO and SYNAPSE

- **PLATO**
  - Governance decisions (RBAC, constraints, audit) drive:
    - Which CAPSULEs can be created
    - Which CAPSULEs can be accessed

- **SYNAPSE**
  - Execution outcomes are fed back into CAPSULE/NEXUS via learning signals.
  - Enables:
    - Trust adjustments
    - Temporal intelligence around action results

---

## High-Level Assessment

The CAPSULE codebase is:

- **Cohesive and well-documented**
  - `docs/index.md` provides a clear entry point with personas and reading paths.
- **Conceptually aligned**
  - Java implementation closely matches conceptual and data model docs.
- **Designed for temporal intelligence**
  - Immutability, idempotency, and lineage are first-class for safe historical reasoning.
  - Temporal analytics are built-in:
    - Temporal queries
    - Causality analysis
    - Velocity and momentum
    - Diff, aggregation, correlations

- **Built for scale and resilience**
  - Cassandra + denormalized tables
  - Caching
  - Circuit breakers and retries
  - Operationally mature deployment patterns

- **Deeply integrated into the BUTTERFLY ecosystem**
  - NEXUS, PERCEPTION, ODYSSEY, PLATO, and SYNAPSE all use CAPSULE as the canonical temporal fabric for **memory, analysis, and learning**.
