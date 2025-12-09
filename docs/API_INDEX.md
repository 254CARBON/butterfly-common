# BUTTERFLY API Index

Complete reference of all service endpoints in the BUTTERFLY ecosystem.

## Service Overview

| Service | Description | Port | Base URL |
|---------|-------------|------|----------|
| PERCEPTION | Reality Integration Mesh, signal detection | 8080 | `/api/v1` |
| CAPSULE | Temporal knowledge storage, historical queries | 8081 | `/api/v1` |
| ODYSSEY | World modeling, narrative paths, strategy | 8082 | `/api/v1` |
| PLATO | Governance, policies, plans, approvals | 8083 | `/api/v1` |
| NEXUS | Reasoning engine, synthesis, learning | 8084 | `/api/v1` |
| SYNAPSE | Action execution, tools, workflows | 8085 | `/api/v1` |
| CORTEX | AI agents, conversations, tasks | 8086 | `/api/v1` |

---

## PERCEPTION Service (Port 8080)

### RIM (Reality Integration Mesh) Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/rim/nodes/{rimNodeId}` | Get RIM node by ID |
| `GET` | `/api/v1/rim/nodes` | List RIM nodes (query: `namespace`, `limit`) |
| `POST` | `/api/v1/rim/nodes/{rimNodeId}/observations` | Submit observation |
| `GET` | `/api/v1/rim/nodes/{rimNodeId}/state` | Get current node state |
| `GET` | `/api/v1/rim/graph` | Get RIM graph structure |

### Events & Signals

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/events` | Query events (query: `since`, `entityId`, `limit`) |
| `GET` | `/api/v1/events/{eventId}` | Get event by ID |
| `GET` | `/api/v1/signals` | Get signals (query: `active`, `rimNodeId`) |
| `GET` | `/api/v1/signals/{signalId}` | Get signal by ID |

### Sources & Trust

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/sources/{sourceId}` | Get source details |
| `GET` | `/api/v1/sources/{sourceId}/trust` | Get source trust score |
| `POST` | `/api/v1/sources` | Register new source |

### Storylines

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/storylines` | List storylines |
| `GET` | `/api/v1/storylines/{storylineId}` | Get storyline |
| `GET` | `/api/v1/storylines/{storylineId}/events` | Get storyline events |

---

## CAPSULE Service (Port 8081)

### Capsule Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/capsules` | Create capsule |
| `GET` | `/api/v1/capsules/{capsuleId}` | Get capsule by ID |
| `GET` | `/api/v1/capsules` | Query capsules (query: `scopeId`, `from`, `to`, `limit`) |
| `PUT` | `/api/v1/capsules/{capsuleId}` | Update capsule |
| `DELETE` | `/api/v1/capsules/{capsuleId}` | Delete capsule (soft) |

### Temporal Queries

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/temporal/slice` | Get state at timestamp (query: `scopeId`, `timestamp`) |
| `GET` | `/api/v1/temporal/evolution` | Get state evolution (query: `scopeId`, `from`, `to`, `resolution`) |
| `GET` | `/api/v1/temporal/diff` | Compare states at two timestamps |

### Lineage

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/lineage/{capsuleId}` | Get capsule lineage (query: `depth`) |
| `GET` | `/api/v1/lineage/{capsuleId}/derived` | Get derived capsules |
| `GET` | `/api/v1/lineage/{capsuleId}/ancestors` | Get ancestor capsules |

### Counterfactuals

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/counterfactuals` | Query counterfactuals (query: `scopeId`, `limit`) |
| `POST` | `/api/v1/counterfactuals` | Create counterfactual |
| `GET` | `/api/v1/counterfactuals/{counterfactualId}` | Get counterfactual |

### Embeddings & Search

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/search/semantic` | Semantic search |
| `POST` | `/api/v1/search/similar` | Find similar capsules |

---

## ODYSSEY Service (Port 8082)

### World State

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/world-state` | Get current world state |
| `GET` | `/api/v1/world-state/history` | Get world state history |
| `GET` | `/api/v1/world-state/stress` | Get stress metrics |

### Entities

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/entities/{entityId}` | Get entity |
| `GET` | `/api/v1/entities` | List entities (query: `scopeId`, `type`) |
| `PUT` | `/api/v1/entities/{entityId}` | Update entity |

### Paths

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/paths` | Get narrative paths (query: `scopeId`, `horizon`) |
| `GET` | `/api/v1/paths/{pathId}` | Get path details |
| `PUT` | `/api/v1/paths/{pathId}/weight` | Update path weight |
| `POST` | `/api/v1/paths/{pathId}/prune` | Prune path |

### Actors

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/actors` | List actors (query: `scopeId`) |
| `GET` | `/api/v1/actors/{actorId}` | Get actor model |
| `PUT` | `/api/v1/actors/{actorId}/model` | Update actor model parameters |

### Schools

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/schools` | List strategy schools |
| `GET` | `/api/v1/schools/{schoolId}` | Get school details |
| `GET` | `/api/v1/schools/{schoolId}/consensus` | Get school consensus |

### Strategies

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/strategies` | List strategies |
| `GET` | `/api/v1/strategies/{strategyId}` | Get strategy |
| `GET` | `/api/v1/strategies/{strategyId}/weight` | Get strategy weight |
| `PUT` | `/api/v1/strategies/{strategyId}/weight` | Update strategy weight |

### Ignorance Surface

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/ignorance` | Get ignorance surface |
| `GET` | `/api/v1/ignorance/hotspots` | Get uncertainty hotspots |

---

## PLATO Service (Port 8083)

### Policies

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/policies` | List policies (query: `category`) |
| `GET` | `/api/v1/policies/{policyId}` | Get policy |
| `POST` | `/api/v1/policies` | Create policy |
| `PUT` | `/api/v1/policies/{policyId}` | Update policy |
| `POST` | `/api/v1/policies/evaluate` | Evaluate policy against request |

### Specs (Artifacts)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/specs` | List specs (query: `type`) |
| `GET` | `/api/v1/specs/{specId}` | Get spec |
| `POST` | `/api/v1/specs` | Create spec |
| `PUT` | `/api/v1/specs/{specId}` | Update spec |

### Plans

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/plans` | List plans (query: `status`) |
| `GET` | `/api/v1/plans/{planId}` | Get plan |
| `POST` | `/api/v1/plans` | Create plan |
| `PUT` | `/api/v1/plans/{planId}` | Update plan |
| `POST` | `/api/v1/plans/{planId}/approve` | Request/grant approval |
| `POST` | `/api/v1/plans/{planId}/execute` | Execute plan |

### Governance

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/governance/status/{entityId}` | Get governance status |
| `GET` | `/api/v1/governance/violations` | List violations |
| `POST` | `/api/v1/decisions/{decisionId}/feedback` | Record decision feedback |

---

## NEXUS Service (Port 8084)

### Temporal Reasoning

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/temporal/slice` | Get temporal slice (query: `entityId`, `timestamp`) |
| `GET` | `/api/v1/temporal/causal-chains` | Get causal chains (query: `entityId`, `depth`) |
| `GET` | `/api/v1/temporal/anomalies` | Detect anomalies (query: `entityId`, `since`) |

### Reasoning Engine

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/reasoning/inferences` | Get inferences (query: `entityId`, `limit`) |
| `GET` | `/api/v1/reasoning/hypotheses` | Get hypotheses (query: `domain`, `limit`) |
| `GET` | `/api/v1/reasoning/contradictions` | Get contradictions |
| `POST` | `/api/v1/reasoning/query` | Submit reasoning query |

### Synthesis

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/synthesis/options` | Get strategic options |
| `GET` | `/api/v1/synthesis/ignorance` | Get ignorance map |
| `POST` | `/api/v1/synthesis/scenarios` | Generate scenarios |

### Learning Signals

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/learning/signal` | Submit learning signal |
| `GET` | `/api/v1/learning/calibration` | Get calibration status |

### Evolution

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/evolution/health` | Get ecosystem health |
| `GET` | `/api/v1/evolution/patterns` | Get integration patterns |

---

## SYNAPSE Service (Port 8085)

### Tools

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/tools` | List tools (query: `category`, `enabled`) |
| `GET` | `/api/v1/tools/{toolId}` | Get tool details |
| `POST` | `/api/v1/tools` | Register tool |
| `PUT` | `/api/v1/tools/{toolId}` | Update tool |
| `DELETE` | `/api/v1/tools/{toolId}` | Unregister tool |

### Actions

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/actions` | Execute action |
| `GET` | `/api/v1/actions/{actionId}` | Get action status |
| `GET` | `/api/v1/actions` | Query actions (query: `rimNodeId`, `platoPlanId`, `from`, `to`) |
| `POST` | `/api/v1/actions/{actionId}/cancel` | Cancel action |

### Workflows

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/workflows` | Create workflow |
| `GET` | `/api/v1/workflows/{workflowId}` | Get workflow |
| `POST` | `/api/v1/workflows/{workflowId}/execute` | Execute workflow |
| `POST` | `/api/v1/workflows/{workflowId}/cancel` | Cancel workflow |

### Safety

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/safety/status` | Get safety status |
| `POST` | `/api/v1/safety/kill-switch` | Activate kill switch |
| `DELETE` | `/api/v1/safety/kill-switch` | Deactivate kill switch |
| `GET` | `/api/v1/safety/limits` | Get rate limit status |

### Outcomes

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/outcomes` | Query outcomes (query: `rimNodeId`, `from`, `limit`) |
| `GET` | `/api/v1/outcomes/{outcomeId}` | Get outcome details |

---

## CORTEX Service (Port 8086)

### Agent Tasks

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/tasks` | Submit agent task |
| `GET` | `/api/v1/tasks/{taskId}` | Get task status |
| `POST` | `/api/v1/tasks/{taskId}/cancel` | Cancel task |
| `GET` | `/api/v1/tasks/{taskId}/thoughts` | Stream thoughts (SSE) |

### Conversations

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/conversations` | Create conversation |
| `GET` | `/api/v1/conversations/{conversationId}` | Get conversation |
| `DELETE` | `/api/v1/conversations/{conversationId}` | Delete conversation |
| `POST` | `/api/v1/conversations/{conversationId}/messages` | Send message |
| `GET` | `/api/v1/conversations/{conversationId}/messages` | Get messages |
| `GET` | `/api/v1/conversations/{conversationId}/stream` | Stream response (SSE) |

### Agents

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/agents` | List agent types |
| `GET` | `/api/v1/agents/{agentType}` | Get agent configuration |

### Tools (Agent-facing)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/agent-tools` | List tools available to agents |
| `POST` | `/api/v1/agent-tools/{toolId}/execute` | Execute tool call |

---

## Common Endpoints (All Services)

### Health & Metrics

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/actuator/health` | Health check |
| `GET` | `/actuator/health/liveness` | Liveness probe |
| `GET` | `/actuator/health/readiness` | Readiness probe |
| `GET` | `/actuator/prometheus` | Prometheus metrics |
| `GET` | `/actuator/info` | Service info |

---

## Standard Headers

### Request Headers

| Header | Required | Description |
|--------|----------|-------------|
| `X-Tenant-ID` | Optional* | Tenant identifier (* required for multi-tenant) |
| `X-Correlation-ID` | Recommended | Request correlation ID |
| `Accept-Version` | Optional | Requested API version (e.g., `v1`, `v1.1`) |
| `Authorization` | Required | Bearer JWT token |

### Response Headers

| Header | Description |
|--------|-------------|
| `X-API-Version` | Current API version |
| `X-Supported-Versions` | Comma-separated supported versions |
| `X-Correlation-ID` | Echoed correlation ID |
| `Deprecation` | RFC 8594 deprecation indicator |
| `Sunset` | RFC 8594 sunset date |
| `X-RateLimit-Limit` | Rate limit maximum |
| `X-RateLimit-Remaining` | Remaining requests |
| `X-RateLimit-Reset` | Reset timestamp |

---

## Error Response Format

All errors follow RFC 7807 Problem Details:

```json
{
  "type": "https://api.butterfly.254studioz.com/errors/{error-type}",
  "title": "Human Readable Title",
  "status": 400,
  "detail": "Detailed explanation of the error",
  "instance": "/api/v1/capsules/abc-123",
  "traceId": "correlation-id-here",
  "timestamp": "2025-01-15T10:30:00Z",
  "service": "capsule",
  "errorCode": "VALIDATION_ERROR"
}
```

### Error Types

| Type | Status | Description |
|------|--------|-------------|
| `not-found` | 404 | Resource not found |
| `bad-request` | 400 | Invalid request |
| `validation-error` | 400 | Validation failed |
| `unauthorized` | 401 | Authentication required |
| `forbidden` | 403 | Insufficient permissions |
| `conflict` | 409 | Resource conflict |
| `rate-limited` | 429 | Rate limit exceeded |
| `service-unavailable` | 503 | Service temporarily unavailable |
| `internal-error` | 500 | Unexpected error |
| `circuit-open` | 503 | Circuit breaker open |
| `timeout` | 504 | Request timeout |
