# ODYSSEY: Strategic Cognition Layer for BUTTERFLY

ODYSSEY is the strategic cognition layer in BUTTERFLY. It maintains a unified world-story, projects plausible futures, models actors, and synthesizes strategies and interventions for downstream planners and executors.

---

## 1. Role in the Golden Loop

ODYSSEY sits in the middle of the golden loop, between temporal intelligence and planning/execution:

> PERCEPTION → CAPSULE → **ODYSSEY** → PLATO → SYNAPSE → (back to PERCEPTION)

References:
- Golden loop validator:  
  `NEXUS/src/main/java/com/z254/butterfly/nexus/validation/GoldenLoopValidator.java` (around line 24).
- NEXUS–ODYSSEY integration via reactive client:  
  `NEXUS/src/main/java/com/z254/butterfly/nexus/client/OdysseyClient.java` (around line 16).

Key relationships:
- **NEXUS** treats ODYSSEY as its strategic world model provider via `OdysseyClient`.
- **PERCEPTION** and **SYNAPSE** integrate with ODYSSEY over Kafka for event and effect streaming.
- CAPSULE and PLATO depend on ODYSSEY’s world-story, paths, and strategy outputs for the golden loop checkpoints and validation.

---

## 2. Architecture and Runtime

### 2.1 Runtime Stack

Primary stack (see `ODYSSEY/pom.xml` and `ODYSSEY/docs/architecture.md`):

- Java 17
- Spring Boot 3.x (WebFlux)
- Kafka (Confluent Avro)
- JanusGraph + Cassandra (graph)
- Redis (cache)
- Postgres (persistence)
- Micrometer / Spring Boot Actuator (observability)

### 2.2 Logical Layers

Defined in `ODYSSEY/docs/architecture.md`:

1. **Interface Layer**
   - REST controllers and WebSocket/SSE endpoints
   - Key paths:
     - `/api/v1/**` – core API
     - `/actuator/**` – health and metrics

2. **Strategy Synthesis**
   - Fuses paths, actor models, pluralism, and uncertainty signals into:
     - Horizon packs
     - Strategy recommendations
     - Posture guidance

3. **Cognitive Processing Engines**
   - Path engine
   - Actor engine
   - Experience engine
   - Adversarial engine
   - Experiment engine

4. **Assimilation Layer**
   - Consumes PERCEPTION events and SYNAPSE action effects.
   - Propagates stress across the world graph.
   - Updates world-story fields and path weights.

5. **State Core**
   - Unified world-story representation:
     - World
     - Paths
     - Players/actors
     - Experience
     - Ignorance / blind spots
     - Commitments
     - Attribution and topology

6. **Persistence**
   - Graph / time-series / document / blob stores behind the state core.
   - Services abstract storage details from cognitive and strategy layers.

### 2.3 Deployment and Configuration

- Deployment:
  - Helm-first deployment under `k8s/helm/odyssey`.
  - Default HTTP port: `8080`.
- Simulation flags (see `ODYSSEY/docs/architecture.md`):
  - `odyssey.horizonbox.simulated`
  - `odyssey.quantumroom.simulated`
  - Control internal simulation vs connection to external HorizonBox / QuantumRoom engines.

### 2.4 Resilience and Observability

- **Resilience**
  - Resilience4j is applied around key integrations:
    - SYNAPSE publishers
    - External foresight/game engines
  - Circuit breaker health exposed via actuator endpoints.

- **Observability**
  - Prometheus-compatible metrics and health checks:
    - `/actuator/health`
    - `/actuator/odyssey-health`
    - `/actuator/health/circuitBreakers`
  - Golden loop metrics primarily live in NEXUS, but depend on ODYSSEY emitting:
    - Path updates
    - World-story changes
    - Relevant event streams

---

## 3. Domain Model: World-Story

The world-story model is described in `ODYSSEY/README.md` and `ODYSSEY/docs/concepts.md`, and implemented across `model/**` and `service/world/**`.

### 3.1 World Field – Current State

Captures the current system configuration:

- **Entities**
  - Assets, firms, regulators, sovereigns, markets, etc.
- **Relations**
  - Flows, exposures, alliances, contracts, and structural links.

Metrics per entity/relation:
- Stress
- Buffer
- Fragility
- Leverage
- Snapshotting capabilities (see `WorldFieldService` example in `ODYSSEY/docs/architecture.md`).

Exposure:
- World and graph snapshots exposed via `/api/v1/world/**` and `/api/v1/snapshots/**`.
- Ingestion primarily from PERCEPTION events and CAPSULE temporal structures.

### 3.2 Path Field – Narrative Futures

Paths are interpretable scenario narratives:

Each path includes:
- Descriptive narrative
- Time horizon (short / medium / long)
- Key drivers and branch/tipping points
- Implications:
  - P&L
  - Exposure and risk posture
  - Strategic posture and constraints

Dynamics:
- Path weights evolve based on:
  - CAPSULE feedback and temporal intelligence
  - Experience engine outcomes
  - Cognitive pluralism fusion results

Exposure:
- `PathEngineController`, `StrategicPathController` and related controllers under `/api/v1/paths/**`.

### 3.3 Player (Actor) Field

Actor models for:
- Regulators
- Sovereigns
- Counterparties
- Alliances and coalitions

Actor profiles include:
- Objectives and constraints
- Risk tolerance
- Time horizon
- Predictability / volatility
- Coordination and coalition behavior

Integration:
- NEXUS’ `OdysseyClient.Actor` mirrors the ODYSSEY actor model  
  (`NEXUS/src/main/java/com/z254/butterfly/nexus/client/OdysseyClient.java`, around line 90).
- QUANTUMROOM extends this into:
  - Multi-agent equilibrium
  - Negotiation corridors
  - Coalition formation  
  (see QuantumRoom integration docs and `QuantumRoomController`).

### 3.4 Experience Field

Experience captures episodes such as:
- World configurations at decision time
- Actions/decisions taken
- Outcomes and second-order effects
- Post-mortems and evaluation metadata

Uses:
- Misjudgement detection
- Calibration and model improvement
- Updating path, actor, and strategy weights

Exposure:
- `ExperienceEngineController` and `/api/v1/experience/**`.
- NEXUS integration via `OdysseyClient.Episode`  
  (`NEXUS/src/main/java/com/z254/butterfly/nexus/client/OdysseyClient.java`, around line 132).

### 3.5 Ignorance Surface

Ignorance is modeled explicitly as first-class state:

- Represents:
  - Blind spots and weak models
  - Novel regimes
  - High-divergence or poorly-understood zones
- Internal DTOs:
  - `BlindSpot`
  - `IgnoranceBoundary`
- NEXUS-facing DTO:
  - `IgnoranceSurfaceResponse`

Mapping:
- `WorldController` maps ODYSSEY’s blind spots to NEXUS ignorance regions:  
  `ODYSSEY/odyssey-core/src/main/java/com/z254/butterfly/odyssey/controller/WorldController.java` (around line 24).

API surface:
- `/api/v1/world/ignorance`
  - Overall ignorance surface
  - Domain-specific scores
  - High-uncertainty nodes
- `/api/v1/world/ignorance/regions`
  - Filtered regions by domain, severity, etc.
- `/api/v1/world/ignorance/penalty/{pathId}`
  - Path-specific ignorance penalties
  - Caution indicators and recommended mitigations

### 3.6 Commitment Ledger and Attribution

Tracks:
- Explicit commitments:
  - Treaties, contracts, regulatory commitments
- Implicit commitments:
  - Norms, historical behavior patterns, de facto promises
- Mapping from strategies/paths back to:
  - Exposures
  - Obligations
  - Counterparty expectations

Exposure:
- Commitment topology and attribution endpoints under:
  - Commitment-related controllers
  - Attribution-tagged operations in the OpenAPI spec

---

## 4. Cognitive Pluralism and Schools of Thought

ODYSSEY’s pluralism framework is detailed in  
`ODYSSEY/docs/advanced/COGNITIVE_PLURALISM.md`, and implemented via:

- `model/pluralism/**`
- `service/CognitivePluralismService`
- `service/SchoolOfThoughtService`
- Corresponding controllers in `odyssey-core`.

### 4.1 School of Thought Model

`SchoolOfThought` entities include:

- Identity:
  - `code`, `name`, `archetype`, `status`
- Weighting and performance:
  - `weight`
  - `accuracyScore`
  - Horizon weights
- Priors and fusion:
  - Priors and fusion parameters
  - Bias sensitivities and risk attitudes

Archetypes (non-exhaustive):
- OPTIMISTIC
- PESSIMISTIC
- STRUCTURAL
- CYCLICAL
- MOMENTUM
- CONTRARIAN
- QUANTITATIVE
- BEHAVIORAL

Lifecycle statuses:
- EXPERIMENTAL
- ACTIVE
- DEPRECATED
- INACTIVE

### 4.2 School APIs

`SchoolOfThoughtController`  
(`ODYSSEY/odyssey-core/src/main/java/com/z254/butterfly/odyssey/controller/SchoolOfThoughtController.java`, around line 38):

- `/api/v1/schools`
  - List, filter, sort
- `/api/v1/schools/page`
  - Paginated listing
- `/api/v1/schools/{code}` and variants
  - Get/update a school
  - Status management
  - Set default school
- `/api/v1/schools/resolve`
  - Resolve a `SchoolContext` for fusion operations

### 4.3 Pluralism Operations

`CognitivePluralismController`  
(`ODYSSEY/odyssey-core/src/main/java/com/z254/butterfly/odyssey/controller/CognitivePluralismController.java`, around line 23):

- School lifecycle:
  - Active school list
  - Default school initialization
- Path-level operations:
  - `/weights/{pathId}` – per-school path weights
  - `/consensus/{pathId}` – fused consensus view
  - `/contrarian/{pathId}` – contrarian view vs consensus
- Divergence:
  - `/divergence/measure`
  - `/divergence/summary`
- Accuracy and predictions:
  - School-level predictions
  - Accuracy and calibration updates

### 4.4 Front-End Alignment

The PERCEPTION portal TypeScript types under:

- `BUTTERFLY/PERCEPTION/perception-portal/src/types/odyssey.ts`

mirror ODYSSEY pluralism and gameboard models:

- `SchoolOfThought`, `SchoolSummary`, `SchoolContext`
- `PerformanceSnapshot`
- Consensus and divergence structures
- Multi-school results and risk/action structures

Notes:
- File is labeled “Ported from capsule-ui with minimal changes”.
- It is aligned with ODYSSEY’s pluralism domain:
  - Consensus algorithms
  - Risk levels
  - Action representations

---

## 5. Gameboard Editing and Structural Interventions

Gameboard editing is a first-class ODYSSEY capability, documented in  
`ODYSSEY/docs/advanced/GAMEBOARD_EDITING.md`.

### 5.1 Purpose

Gameboard editing manages **structural interventions**—changes to the rules of the game—rather than local tactics:

Examples:
- Product designs and structured products
- Contracts and covenant structures
- Regulatory angles and policy strategies
- Partnerships and ecosystem plays
- Custom strategic playbooks

### 5.2 Core Components

`GameboardEditingController`  
(`ODYSSEY/odyssey-core/src/main/java/com/z254/butterfly/odyssey/controller/GameboardEditingController.java`, around line 32):

Backed by services:
- `StructuralInterventionService`
  - CRUD operations and lifecycle management
  - Scoring and metadata
- `InterventionImpactService`
  - Multi-dimensional impact scoring:
    - Narrative shift
    - Competitive position
    - Risk reduction
    - Optionality
    - Synergy
- `InterventionPrioritizationService`
  - Feasibility assessment
  - Portfolio-level optimization
- Type-specific services:
  - Product, contract, regulatory, partnership
- `InertiaGameboardIntegrationService`
  - Connects interventions to organisational inertia analysis

### 5.3 API Endpoints

All under `/api/v1/gameboard/**`:

- Interventions:
  - `/interventions` (list, create)
  - `/interventions/{id}` (read, update, delete)
- Lifecycle:
  - Evaluation, proposal, approval
  - Start, complete, reject, defer, archive
  - Bulk transitions: `/interventions/bulk/transition`
- Impact:
  - `/interventions/{id}/impact*` (estimates and history)
  - `/impact/compare` (cross-intervention comparisons)
- Feasibility:
  - `/interventions/{id}/feasibility`
- Prioritization:
  - `/prioritize`
  - `/prioritize/top`
  - `/prioritize/optimize`

### 5.4 Data Model

Front-end types in:

- `perception-portal/src/types/odyssey.ts` (around line 84)

mirror back-end DTOs:
- `Intervention`, `InterventionSummary`
- `InterventionImpact`
- `FeasibilityAssessment`
- `PrioritizedIntervention`
- `PortfolioOptimization`
- `ExcludedIntervention`

Support for type-specific payloads:
- `ProductInnovation`
- `ContractDesign`
- `RegulatoryAngle`
- `PartnershipStructure`

### 5.5 Events and Streaming

- Kafka topic: `odyssey.gameboard.interventions`
  - Avro schema: `schema/avro/GameboardIntervention.avsc`
- WebSocket topic: `/topic/gameboard/interventions`
  - Live updates to UI clients and internal consumers

### 5.6 Integration

- Interventions link directly to:
  - Paths (`affectedPathIds`)
  - Actors (`involvedActorIds`)
- Evaluations can be performed under different schools of thought:
  - School-specific impact
  - School-specific feasibility and trade-offs

---

## 6. Uncertainty Modeling and Ignorance

Uncertainty is a first-class concern across ODYSSEY, described in  
`ODYSSEY/docs/advanced/UNCERTAINTY_MODELING.md`.

### 6.1 Capabilities

- Confidence bands:
  - Percentiles: 5th, 25th, 50th, 75th, 95th
- Calibration metrics:
  - Brier score
  - Log loss
  - Expected Calibration Error (ECE)
- Calibration techniques:
  - Platt scaling
  - Isotonic regression
  - Temperature scaling
  - Beta calibration
- `UncertaintyEnvelope` type (see docs, around line 72):
  - Point estimates
  - Variance
  - Entropy
  - Coverage
  - Epistemic vs aleatoric split
- Bayesian helpers:
  - Priors/posteriors
  - Beta distributions

### 6.2 Integration Points

Uncertainty envelopes and measures are attached to:

- Path weights and narrative futures
- Actor reactions and behavioral predictions
- Mispricing and radar outputs
- CAPSULE fusion and temporal signals
- Experiment design and evaluation:
  - Information gain
  - Calibration improvement

Ignorance surface:
- Combines:
  - Uncertainty signals
  - Structural gaps
  - Regime change indicators
- Exported as a NEXUS-compatible `IgnoranceSurface` via `WorldController.getIgnoranceSurface`.

---

## 7. APIs and SDKs

The canonical HTTP API is defined in:

- `ODYSSEY/API_SPECIFICATION.md`
- `ODYSSEY/openapi/odyssey-v1.yaml`

### 7.1 OpenAPI Metadata

From `ODYSSEY/openapi/odyssey-v1.yaml`:

- Title: **ODYSSEY API**
- Version: **1.2.1**
- Scope includes:
  - World, paths, actors, strategies
  - Experiments and adversarial engines
  - Radar and misjudgement
  - NLP query endpoints
  - Gameboard and pluralism
  - ML calibration and temporal intelligence
  - Attribution, CAPSULE, and advanced surfaces

Endpoint tags (non-exhaustive):
- World Field
- Paths
- Actors
- Strategy
- Experience
- Experiments
- HORIZONBOX
- QUANTUMROOM
- Radar
- Overrides
- Temporal Intelligence
- Gameboard
- Hardening
- Multi-School Consensus
- ML Calibration
- CAPSULE
- Attribution

### 7.2 Key Endpoint Groups

- **World & State**
  - `/api/v1/state`
  - `/api/v1/world/**`
  - `/api/v1/snapshots/**`
- **Paths**
  - `/api/v1/paths/**`
  - Listing, details, implications, weight updates
- **Actors**
  - `/api/v1/actors/**`
- **Strategy & Posture**
  - `/api/v1/strategy/**`
  - `/api/v1/strategies/**`
  - `StrategySynthesisController`
- **Experience & Experiments**
  - `/api/v1/experience/**`
  - `/api/v1/experiments/**`
  - `ExperimentController`, `EnhancedExperimentController`
- **Adversarial & Hardening**
  - `/api/v1/adversarial/**`
  - `/api/v1/hardening/**`
- **HorizonBox & QuantumRoom**
  - `/api/v1/foresight/**`
  - `/api/v1/quantumroom/**`
  - Either external or simulated depending on feature flags
- **Radar & Misjudgement**
  - `/api/v1/radar/**`
  - `StoryMarketRadarController`
  - `MisjudgementRadarController`
- **Pluralism / Schools**
  - `/api/v1/schools/**`
  - `/api/v1/pluralism/**`
- **Gameboard**
  - `/api/v1/gameboard/**`
- **Temporal Intelligence & Advanced**
  - `TemporalIntelligenceController`
  - `AdvancedFeaturesController`
- **NLP Query**
  - `/api/v1/nlp/query`
  - Backed by LLM inference via `NlpQueryController`

### 7.3 SDKs

- Python SDK:
  - `ODYSSEY/sdk/python/odyssey_client`
  - Generated from the OpenAPI spec
  - Mirrors ODYSSEY’s HTTP surface (e.g. `uncertainty_request.py`)
- Documentation and pipelines:
  - SDK publishing and versioning covered in `ODYSSEY/DOCUMENTATION.md` and CI-docs.

---

## 8. Integration with Other BUTTERFLY Services

### 8.1 PERCEPTION

Configuration:
- `PerceptionKafkaConfig`  
  `ODYSSEY/odyssey-core/src/main/java/com/z254/butterfly/odyssey/config/PerceptionKafkaConfig.java` (around line 16)

Features:
- Dedicated consumer factories per PERCEPTION topic (`perception.kafka.*`).
- Audio intelligence:
  - Requests/results via Kafka topics:
    - `perception.audio.requests`
    - `perception.audio.results`
  - Avro contracts referenced in README.

Function:
- Assimilation layer converts PERCEPTION events into:
  - Stress changes
  - Regime updates
  - Path reweighting and world-story evolution

### 8.2 CAPSULE

CAPSULE provides:

- 4D canonical frame and temporal intelligence  
  (`CAPSULE/docs/api/temporal-intelligence.md`).

ODYSSEY integration:

- `odyssey-core/src/main/java/com/z254/butterfly/odyssey/capsule/**`
  - Domain models and services
  - `CapsuleController`, `CapsuleQueryController`
- Uses CAPSULE’s:
  - Temporal intelligence
  - State velocity metrics
- Feeds:
  - Path evolution
  - Actor evolution
  - Mispricing and regime detection

### 8.3 SYNAPSE

Kafka configuration:

- `SynapseKafkaConfig`
- `SynapseKafkaProperties`  
  (`ODYSSEY/odyssey-core/src/main/java/com/z254/butterfly/odyssey/config/SynapseKafkaConfig.java`, around line 24)

Function:
- ODYSSEY publishes:
  - Strategy proposals (Avro)
  - World-effect events
- ODYSSEY consumes:
  - Action effects from SYNAPSE
- Closed-loop:
  - Strategy learning updates:
    - Path weights
    - Strategy weights
    - Actor parameters
  - Feeds back into the world-story and golden loop checkpoints

### 8.4 NEXUS

NEXUS uses `OdysseyClient` to:

- Request:
  - World state
  - Paths and actors
  - Ignorance surface
  - Episodes and experiments
- Push feedback:
  - Path/actor/strategy weight updates
  - Calibration and evaluation data

References:
- `NEXUS/src/main/java/com/z254/butterfly/nexus/client/OdysseyClient.java` (around line 22)

Golden loop validation:
- `GoldenLoopValidator` tracks `ODYSSEY_PATH_UPDATED` and related checkpoints:
  - Ensures ODYSSEY has reacted to:
    - CAPSULE snapshots
    - PERCEPTION signals
- File:
  - `NEXUS/src/main/java/com/z254/butterfly/nexus/validation/GoldenLoopValidator.java` (around line 52)

### 8.5 PLATO and Other Services

PLATO:
- Planning layer consuming ODYSSEY strategies as a core input to plan generation.
- PLATO models reference ODYSSEY experiments:
  - Example: `ButterflyAttachments` referencing ODYSSEY experiment IDs  
    `PLATO/src/main/java/com/z254/butterfly/plato/domain/model/common/ButterflyAttachments.java` (around line 12).

Other integrations:
- HFT, hardening, radar, and temporal intelligence modules integrate via:
  - Kafka streams
  - REST APIs
  - Shared DTOs and OpenAPI contracts

---

## 9. Security, Tenancy, and Governance

### 9.1 Tenancy and Correlation

- Multi-tenant isolation:
  - `X-Tenant-Id` header enforced via filter layer.
- Correlation and tracing:
  - `X-Correlation-ID` propagated across:
    - HTTP
    - Kafka headers
- Described in `ODYSSEY/docs/architecture.md` under “Integration Patterns”.

### 9.2 Security and Compliance

Governance docs:
- `docs/governance-security/COMPLIANCE_VALIDATION.md`
- `docs/governance-security/SECURITY_AUDIT.md`
- `docs/governance-security/TECHNOLOGY_STACK_DECISION.md`

Areas covered:
- Dependency governance, SBOM, SCA
- Signing and artifact integrity
- Runtime hardening and security posture

API security:
- Bearer auth:
  - `BearerAuth` defined in OpenAPI (`ODYSSEY/openapi/odyssey-v1.yaml`, around line 52).
- Rate limiting:
  - Headers documented in OpenAPI and gateway config.
- Error handling:
  - Standard `application/problem+json` responses documented and implemented.

### 9.3 Docs and Process Governance

Documentation and engineering standards:

- `DOCUMENTATION.md`
- `docs/DOCS_STYLE_GUIDE.md`
- `z254_master_document.md`

Include:
- DORA metrics and tracking
- Release gating and quality bars
- Incident management
- Documentation consistency and review requirements

---

## 10. Code Quality and Maturity Observations

### 10.1 Maturity

- Status: **“Beta”**, but:
  - Broad, end-to-end feature coverage:
    - Ingestion → world-story → strategy → experiments → pluralism
  - Deep documentation and strong alignment between:
    - Docs
    - Code structure
    - Integration boundaries

### 10.2 Design Coherence

- Clear separation of concerns:
  - State core
  - Cognitive engines
  - Integration / streaming
  - Interface layer
- Advanced domains (pluralism, gameboard, uncertainty, experiments, radar):
  - Factored into dedicated packages
  - Backed by matching docs and OpenAPI tags

### 10.3 Integration Clarity

- Kafka configuration:
  - Explicit, per-boundary (PERCEPTION, SYNAPSE, HFT).
  - Allows tuning and isolation per stream.
- NEXUS and world controllers:
  - Provide typed, versioned bridges for:
    - Ignorance surfaces
    - World/story state
    - Path and actor updates

### 10.4 Areas to Watch

From a reviewer’s perspective:

- `odyssey-parent`:
  - Some modules (e.g. `odyssey-audio`, `odyssey-quantum`) are listed despite being marked retired/removed in README.
  - Alignment between POMs and reality should be kept tight.
- Operational surface:
  - Advanced controllers and features create a wide operational footprint.
  - Production deployment will require:
    - Endpoint-level SLOs
    - Hardening and limits per API group
    - Careful observability and incident playbooks

---
