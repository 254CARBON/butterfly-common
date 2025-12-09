# CORTEX Overview

CORTEX is the AI agent orchestration layer of the BUTTERFLY ecosystem.

It is implemented as a reactive Spring Boot 3 / Java 17 service and tightly integrated with other core systems (CAPSULE, PERCEPTION, ODYSSEY, PLATO, SYNAPSE, NEXUS). CORTEX provides:

- Multiple agent types: ReAct, Plan-and-Execute, Reflexive
- Abstraction over multiple LLM providers with failover
- Multi-level memory (episodic, semantic, working, temporal)
- Tool orchestration via SYNAPSE and built-in tools
- Governance and safety via PLATO and internal guardrails
- Rich observability, health, and resilience primitives

Status: **Beta** – core agents and infrastructure are implemented; production hardening and several integrations are in progress.

---

## 1. Position in the BUTTERFLY Ecosystem

Within `/BUTTERFLY`, CORTEX operates as the agent layer that sits on top of the core intelligence and execution services:

- **CAPSULE** – 4D atomic history and temporal memory platform (Cassandra/Redis/Iceberg).  
  Used by CORTEX for long-term memory, temporal analytics, causality, and velocity reasoning.

- **PERCEPTION** – sensory and interpretation layer.  
  Produces trusted signals, events, and scenarios that CORTEX can query via tools.

- **ODYSSEY** – world-scale understanding and strategy engine.  
  Provides “world story” and futures context for strategic decision making by agents.

- **PLATO** – governance and intelligence service.  
  Hosts plans, policies, and proofs. CORTEX submits plans and receives approvals/rejections.

- **SYNAPSE** – execution engine and tool registry.  
  Executes external actions with safety and observability. CORTEX routes tool calls to SYNAPSE.

- **NEXUS** – integration “cognitive cortex”.  
  Unifies temporal intelligence and cross-system reasoning across PERCEPTION, CAPSULE, ODYSSEY, PLATO.  
  CORTEX exposes agents, tasks, and results into this fabric.

- **butterfly-common** – shared domain models, identity primitives, and Kafka/Avro contracts used across services.

**Role of CORTEX:** coordinate LLM reasoning, memory, tools, and governance, and emit task traces, thoughts, and metrics back into the ecosystem.

---

## 2. Responsibilities & Scope

CORTEX is responsible for:

- **Agent Execution**
  - Implement ReAct, Plan-and-Execute, and Reflexive agent patterns.
  - Reason, plan, and act using tools (SYNAPSE and built-ins).
  - Support streaming and synchronous execution modes.

- **Memory & Context**
  - Maintain multi-level memory:
    - Episodic (conversations, episodes)
    - Semantic (vector-based knowledge)
    - Working (per-task scratchpad)
    - Temporal (via CAPSULE)
  - Integrate strategic and temporal intelligence (CAPSULE, ODYSSEY).

- **LLM Abstraction**
  - Provide a common interface over multiple LLM providers (OpenAI, Anthropic, Ollama).
  - Handle provider selection, health checks, and failover.

- **Governance & Safety**
  - Enforce plans and approvals via PLATO.
  - Apply token budgets, tool quotas, rate limiting, and sandboxed execution.
  - Apply guardrails for content safety and PII filtering.

- **APIs & Persistence**
  - Expose APIs for agent management, tasks, and chat workflows.
  - Persist conversations, tasks, results, and analytics in Cassandra.
  - Surface metrics and health endpoints for operations/SRE.

---

## 3. High-Level Architecture

The codebase is organized into clear functional packages:

- `agent/` – agent framework and implementations (ReAct, Plan-and-Execute, Reflexive).
- `api/` – REST controllers, DTOs, WebSocket handlers.
- `checkpoint/` – task checkpointing and resumption.
- `client/` – clients for CAPSULE, PLATO, SYNAPSE, etc.
- `cognitive/` – complexity analysis, orchestration, Tree-of-Thought-style exploration.
- `config/` – Spring configuration (security, metrics, Kafka, Redis, WebSocket, properties).
- `domain/` – entities, domain models, and repositories.
- `governance/` – plan submission, approval workflows, governance policies.
- `health/` – health indicators for internal and external dependencies.
- `kafka/` – Kafka producers/consumers and Avro integration.
- `llm/` – provider abstraction, provider implementations, guardrails.
- `memory/` – episodic, semantic, working memory and CAPSULE adapters.
- `observability/` – structured logging, tracing, governance metrics.
- `optimization/` – semantic cache, prompt optimization, parallel tool orchestration.
- `reasoning/` – self-reflection, confidence scoring, chain validation.
- `ratelimit/` – filters and rate-limiting services.
- `resilience/` – quotas, timeouts, tool sandboxing.
- `tool/` – tool registry, bridge, built-in tools (including temporal tools).
- `security/` – JWT/API-key auth, scopes, and security filters.

**Runtime:**  
`CortexApplication` boots a reactive Spring Boot service (port `8086`), with Docker/K8s packaging and health/metrics endpoints suitable for production.

---

## 4. Agent Framework

### Core Agent Types

- **ReActAgent**
  - Implements a Reason–Act–Observe loop with tool use.
  - Builds prompts from system instructions plus conversation history.
  - Uses `LLMProviderRegistry` and `ToolRegistry` to:
    - Call the default LLM.
    - Interpret tool/function call responses.
  - Emits `AgentThought` events:
    - `REASONING`, `TOOL_CALL`, `OBSERVATION`, `FINAL_ANSWER`, `ERROR`, `STOP`.
  - Enforces maximum iterations and token budgets via configuration and context tracking.

- **PlanAndExecuteAgent**
  - Two-phase behavior:
    - Plan generation (multi-step plan).
    - Stepwise execution with PLATO-backed plan approvals.
  - Designed for longer-horizon, higher-risk workflows.

- **ReflexiveAgent**
  - Fast-path agent for simple queries.
  - Uses complexity analysis to decide whether to escalate to more complex agents.

### Orchestration Services

- **AgentExecutor**
  - Interface implemented by agents.
  - Exposes reactive APIs: `Flux<AgentThought>` (streaming) and `Mono<AgentResult>` (synchronous).

- **AgentExecutorFactory**
  - Resolves the appropriate executor based on `AgentType` and configuration.

- **AgentService / AgentServiceImpl**
  - Main façade for:
    - Creating agents, tasks, and conversations.
    - Executing tasks (sync/async/streaming).
    - Driving chat flows over conversations (backed by episodic memory and Cassandra).

- **TenantAwareAgentService**
  - Wraps `AgentService` with tenant/namespace awareness for multi-tenancy.

### Cognitive Orchestration

- **TaskComplexityAnalyzer**
  - Evaluates task complexity based on input, context, and configuration.
  - Influences agent selection and iteration limits.

- **CognitiveOrchestrator**
  - Coordinates:
    - Agent selection
    - Memory usage
    - Optimization features (semantic cache, prompt optimization, parallel tools).

- **ThoughtTreeExplorer**
  - Supports Tree-of-Thought-style exploration for complex reasoning tasks.

---

## 5. LLM Abstraction & Guardrails

### Provider Interface & Registry

- **LLMProvider**
  - Operations:
    - `complete`, `stream`
    - `embed`, `embedBatch`
    - Availability checks
    - Token counting (for budget enforcement)
  - Hides provider-specific details behind a common interface.

- **LLMProviderRegistry**
  - Registers providers and selects:
    - Default provider from configuration.
    - Best available provider given health and availability.
  - Provides resilient fallback when a provider is degraded or unavailable.

### Provider Implementations

- **OpenAIProvider**
- **AnthropicProvider**
- **OllamaProvider**

All implement:

- Non-streaming and streaming completions.
- Embeddings.
- Support flags for tool/function calling where applicable.

### Guardrails

- **TokenBudgetEnforcer**
  - Estimates tokens via provider logic.
  - Enforces budgets per task and conversation.

- **OutputValidator**
  - Content filtering, PII detection, and safety constraints.
  - Hooks into PLATO for governance checks on high-risk outputs.

Together, these components provide provider abstraction, failover, token budgeting, and content-level safety.

---

## 6. Memory Systems

CORTEX exposes a unified memory layer with multiple tiers:

### Episodic Memory

- Short-term, conversation-centric.
- Implemented via `EpisodicMemory` and `RedisEpisodicMemory`.
- Uses Redis Streams for ordered episodes:
  - `record(conversationId, episode)` appends and trims to a configured max.
  - Retrieval APIs by conversation and time range.
  - Scheduled pruning based on retention policies.

### Semantic Memory

- Long-term, vector-based knowledge and retrieval.
- Abstractions:
  - `SemanticMemory`
  - `VectorStoreSemanticMemory`
- Backends:
  - In-memory vector store (development)
  - PostgreSQL `pgvector` (production-oriented)
  - Pinecone (managed vector DB)
- Supports `store`, `search`, and `delete` semantics.

### Working Memory

- Per-task scratchpad.
- Key-value store tied to `taskId`.
- Used for intermediate results (e.g., tool outputs) across multi-step reasoning.
- Cleared when the task completes.

### CAPSULE Integration

- `CapsuleMemoryAdapter` connects to CAPSULE for durable, cross-conversation memory and temporal analytics.
- Full CAPSULE-backed persistence is planned but not fully complete.

---

## 7. Tool Integration & Temporal Intelligence

### Tool Framework

- **Core abstractions**
  - `AgentTool` – tool interface:
    - ID, name, description
    - JSON parameter schema
    - Risk level and validation
    - `execute` API
  - `ToolResult` – normalized result including:
    - Success flag and error codes
    - Structured data payload
    - LLM-friendly summary text

- **ToolRegistry**
  - Registers tools and exposes them as LLM tool/function JSON.
  - Executes tools with validation and standardized error handling.

- **ToolBridge**
  - Binds LLM tool calls to actual tool execution.
  - Routes to:
    - SYNAPSE tools
    - Built-in tools
    - Temporal and governance tools

### SYNAPSE Bridge

- `SynapseToolClient` and `WebClientSynapseToolClient` provide:
  - Tool/action invocation
  - Status and cancellation
  - Tool discovery
- Resilience:
  - Circuit breakers and retry (resilience4j)
  - Stub mode when `SYNAPSE_URL` is not configured, enabling local development with mock results.

### Built-in Tools

- **CapsuleQueryTool** – queries CAPSULE histories and analytics.
- **OdysseyQueryTool** – retrieves strategic context from ODYSSEY.
- **PerceptionQueryTool** – fetches signals/events from PERCEPTION.
- **PlatoGovernanceTool** – runs policy and governance evaluations via PLATO.

### Temporal Tools

CORTEX exposes temporal analytics directly to agents:

- **TemporalCausalityTool**
  - Tool ID: `cortex.temporal.causality`
  - Function: `temporal_causality_analysis`
  - Inputs: `scope_a`, `scope_b`, `from_time`, optional `to_time`, `metric`.
  - Uses CAPSULE to perform causality analysis (e.g., Granger causality, cross-correlation).
  - Supports ISO-8601 and relative times (`-7d`, `-24h`).
  - Returns structured analysis plus human-readable summary.

- **TemporalVelocityTool**
  - Tool ID: `cortex.temporal.velocity`
  - Function: `temporal_velocity_analysis`
  - Inputs: `scope_id`, `query_type`, `lookback`, `sensitivity`.
  - Answers “how fast/trending/anomaly?” questions for entities over time.

These tools enable agents to reason about causality, trends, and anomalies inside ReAct and Plan-and-Execute loops.

---

## 8. Governance & Safety

### PLATO Integration

- **PlatoGovernanceClient**
  - Policy evaluation and governance checks.
  - Plan submission and approval tracking.
  - Evidence submission and retrieval of governance artifacts.

- **Governance Orchestration**
  - `GovernancePolicy` – local, configurable policies for low-latency checks.
  - `AgentPlanSubmitter` – transforms CORTEX plans into PLATO submissions.
  - `ApprovalWorkflowHandler` – manages approval lifecycle and timeouts.

- **PlanApprovalService**
  - Decides when approvals are required based on:
    - Agent governance configuration
    - Global governance settings
  - Performs:
    - Local evaluation for low-risk flows (when enabled)
    - Remote submission to PLATO for higher-risk plans
  - Returns `PlanApprovalResult` with:
    - Approval type: `LOCAL`, `AUTO`, `MANUAL`, `TIMEOUT`, `BYPASS`
    - Associated metadata for audit.

### Safety Controls

- **Token Budgets**
  - Configurable per:
    - Task
    - Conversation
    - Day (global limits)
  - Enforced via `TokenBudgetEnforcer` and tracked in `AgentContext`.

- **Tool Sandbox & Quotas**
  - `ToolSandbox` and `ExecutionQuotaManager` enforce:
    - Per-tool call timeouts (defaults and per-tool overrides).
    - Max concurrent tool calls per task.
    - Max total tool calls per task.
  - Failures surface as structured `ToolResult` error codes.

- **Rate Limiting**
  - `RateLimitingWebFilter` and `RateLimitService` implement per-user/namespace rate limits.

- **Multi-Tenancy & Isolation**
  - `TenantFilter` extracts tenant/namespace from JWT or headers.
  - Cassandra tables are keyed by namespace to ensure logical isolation.

- **Security**
  - JWT-based authentication with scopes:
    - e.g., `SCOPE_cortex:task:write`, `SCOPE_cortex:chat:read`.
  - API key support for service-to-service integration.
  - PII/content-safety guardrails configurable via properties.

- **Governance Audit**
  - Governance activities persisted in Cassandra (`governance_audit`) with TTL and indices for analysis.

---

## 9. Data Model & Persistence

CORTEX uses Cassandra as the primary data store:

- **Keyspace**
  - `cortex` keyspace with `NetworkTopologyStrategy` and replication factor tuned for HA.

- **UDTs**
  - `token_usage` – input/output/total tokens, model, provider.
  - `agent_thought` – type, content, tool metadata, iteration, timestamp.
  - `tool_call_record` – tool parameters, result, success, duration.
  - `governance_info` – PLATO plan and approval metadata.

- **Core Tables**
  - `agents` – agent definitions and configuration, keyed by `(namespace, agent_id)`.
  - `agent_tasks` – execution records partitioned by `(namespace, task_date)` with TTL (e.g., 90 days).
  - `agent_tasks_by_id` – direct lookup by `task_id`.
  - `conversations` – conversation state and message history keyed by `(namespace, conversation_id)`.
  - `conversations_by_user` – optimized access for listing user conversations.
  - `task_results` – task analytics: tokens, iterations, tool calls, latency.
  - `governance_audit` – governance and approval events.
  - `llm_usage` – LLM call usage, latency, and cost (TTL-based retention).
  - `tool_executions` – per-tool execution records and errors.
  - `schema_version` – migration tracking.

- **Materialized Views**
  - e.g. `tasks_by_status_mv`, `active_conversations_by_agent`  
    Used for monitoring, dashboards, and listing operations.

---

## 10. API Surface & Integration Points

### REST APIs

- `/api/v1/agents`
  - Create, list, and delete agents.

- `/api/v1/agent-tasks`
  - Submit tasks.
  - Execute tasks synchronously.
  - Stream execution results.
  - Cancel tasks.

- `/api/v1/agent-tasks/{id}`
  - Get task status and metadata.

- `/api/v1/agent-tasks/{id}/result`
  - Retrieve final task results.

- `/api/v1/chat` and `/api/v1/chat/stream`
  - Chat workflows (sync and server-sent events streaming).

- `/api/v1/chat/conversations` and related endpoints
  - Create, list, get, archive conversations.

Auth: JWT Bearer tokens and/or API keys with scope-based authorization.

### WebSocket

- `/ws/agents/{taskId}`
  - Real-time streaming of agent thoughts for UI clients.

### Kafka

Topics for asynchronous integration:

- `cortex.agent.tasks` – consumes `CortexAgentTaskRequest`.
- `cortex.agent.results` – publishes `CortexAgentResult`.
- `cortex.agent.thoughts` – publishes `CortexAgentThought` streams.
- `cortex.conversations` – publishes conversation events.

### Actuator & Metrics

- Health:
  - `/actuator/health`
  - `/actuator/health/liveness`
  - `/actuator/health/readiness`
- Metrics (Prometheus):
  - `cortex.agent.tasks.submitted`, `.completed`, `.iterations`
  - `cortex.llm.calls`, `.latency`
  - `cortex.tool.executions`
  - Memory sizes and governance metrics

---

## 11. Observability & Resilience

### Observability

- **Metrics**
  - Prometheus metrics configured for:
    - Agent lifecycle and iterations
    - LLM usage and latency
    - Tool execution rates and errors
    - Memory usage and sizes
    - Governance decisions and safety events

- **Logging**
  - `StructuredLogger` emits JSON logs with:
    - `correlationId`, `taskId`, `agentId`, and other key fields.
  - Logs:
    - Task lifecycle
    - LLM calls
    - Tool executions
    - Memory operations
    - Governance checks and safety violations
    - Agent thoughts (selected)

- **Tracing**
  - `TracingAspect` integrates with OpenTelemetry.
  - Spans created around core operations and external calls (LLMs, CAPSULE, SYNAPSE, PLATO).

### Resilience

- **Resilience4j Integration**
  - Circuit breakers for LLM and tool calls (e.g., `openai`, `synapse`).
  - Retry policies with exponential backoff.
  - Time limiters:
    - LLM calls (e.g., ~120s)
    - Tool calls (e.g., ~30s)

- **Rate Limiting & Quotas**
  - Endpoint-level rate limits via web filters.
  - Per-task quotas for tools and LLM usage via `ExecutionQuotaManager` and safety config.

- **Health Indicators**
  - Custom health indicators for:
    - CORTEX itself
    - LLM providers
    - Memory systems
    - CAPSULE, PLATO, SYNAPSE connectivity
  - Used to drive readiness/liveness in Kubernetes.

---

## 12. Maturity & Roadmap

Current state:

- Foundation (LLM abstraction, OpenAI provider, core domain models): **Complete**
- Core agents (ReAct, Plan-and-Execute, Reflexive): **Complete**
- Memory systems (episodic, semantic, working; CAPSULE adapter): **Complete / scaffolded**
- Tool integration (SYNAPSE bridge, built-in tools, registry): **Complete**
- Production hardening: **In progress**

Key roadmap items:

- **Testing**
  - Unit test coverage is currently low; target is 80%+.
  - Integration tests and Testcontainers-based suites are planned.

- **Providers & Integrations**
  - Anthropic provider: full tool-use and streaming support in progress.
  - CAPSULE: full persistence and richer temporal memory integration planned.
  - PLATO: more advanced workflows, policy cache, and richer auditing.

- **Multi-Tenancy**
  - Tenant filters and namespaces are present.
  - Deeper tenant isolation and operational guarantees are planned.

- **Observability & Ops**
  - Grafana dashboards for CORTEX metrics are planned.
  - Database schema migration tooling and backup/restore strategies are being formalized.
  - Kubernetes deployment assets (Helm charts, HPA, NetworkPolicy, PDB) are planned.

---

## 13. Strengths

- **Clear Role in the Ecosystem**
  - Well-aligned with BUTTERFLY’s architecture and responsibilities.

- **Separation of Concerns**
  - Clean boundaries between:
    - Agent logic
    - LLM provider abstraction
    - Memory systems
    - Tools and execution
    - Governance and safety
    - Observability and resilience

- **Deep Ecosystem Integration**
  - Tight, explicit interfaces with CAPSULE, PERCEPTION, ODYSSEY, PLATO, SYNAPSE, and NEXUS.

- **Robust Safety Model**
  - Token budgets
  - Tool sandboxing and quotas
  - Governance checks and content guardrails
  - Rate limiting and security scopes

- **Operational Readiness**
  - Containerized, non-root Docker image with health checks.
  - Cassandra schema tuned for time-series workloads with TTL.
  - Metrics and health endpoints suitable for production SRE.

---

## 14. Risks, Gaps, and Recommended Next Steps

### Key Risks / Gaps

- **Testing**
  - Test coverage is currently low for a system of this complexity.
  - Lacking comprehensive integration tests around failure modes.

- **Ecosystem Integrations**
  - Anthropic provider features are still evolving.
  - CAPSULE-based full persistence is not yet fully realized.
  - PLATO workflows and policy cache are early-stage.

- **Multi-Tenancy**
  - Namespaces and filters exist but require exhaustive review to guarantee isolation in a multi-tenant environment.

- **System Complexity**
  - Many moving parts (LLMs, memory, tools, Kafka, Redis, Cassandra, several external systems).
  - Advanced features (Tree-of-Thought, self-reflection, semantic cache, parallel tools) increase the risk of misconfiguration and unexpected cost/latency.

### Recommended Next Steps

1. **Testing & Hardening**
   - Prioritize unit and integration tests for:
     - Agent loops (ReAct, Plan-and-Execute, Reflexive)
     - Tool orchestrator and sandbox
     - Token-budget enforcement
     - Governance workflows (PLATO integration)
   - Add explicit tests for failure scenarios:
     - LLM provider outages
     - CAPSULE/SYNAPSE/PLATO unavailability
     - Cassandra/Redis issues

2. **Governance & Safety Validation**
   - End-to-end tests to ensure high-risk tools consistently trigger PLATO approvals.
   - Validate token budgets, tool quotas, and rate limits in production-like environments.

3. **Multi-Tenancy Review**
   - Confirm all read/write paths are namespace-aware.
   - Prove that cross-tenant data leakage is impossible under misconfiguration.
   - Add tests and, where useful, runtime guards for tenant boundaries.

4. **Operational Playbooks**
   - Create runbooks for:
     - LLM provider outage
     - CAPSULE/SYNAPSE/PLATO downtime
     - Cassandra and Redis incidents
   - Finalize Grafana dashboards and alerts for:
     - LLM latency and error rates
     - Token budget exhaustions
     - Tool failure patterns

5. **Developer & Integrator Experience**
   - Ensure SDKs (TypeScript, Python) fully support:
     - Streaming APIs and agent thoughts
     - Temporal tools
     - Governance flows
   - Provide reference implementations of agents that:
     - Use temporal tools (causality, velocity)
     - Integrate with PLATO for approvals end-to-end.

---
