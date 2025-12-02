# BUTTERFLY Common Architecture

Shared library for cross-service domain models, identity primitives, and event contracts used by CAPSULE, PERCEPTION, and ODYSSEY.

## Contents

- `org.capsule.domain.model.*` — CAPSULE domain models (Configuration/Dynamics/Agency/Counterfactual/Meta). Packages stay the same as CAPSULE to avoid import churn.
- `com.studioz254.carbon.butterfly.identity` — `RimNodeId` (canonical `rim:{nodeType}:{namespace}:{localId}` validator) and `IdempotencyKeyGenerator` (SHA-256 of `rimNodeId + snapshotTime + resolutionSeconds + vantageMode`, delimiting with `|` for determinism).
- `com.studioz254.carbon.butterfly.hft` — Avro contracts (`RimFastEvent`, generated under `target/generated-sources/avro`) and adapters such as `RimEventFactory` for constructing minimal HFT payloads.
  - Evolution guidelines: add fields with defaults, avoid renames, keep `rim_node_id` stable as the primary key.
- `com.studioz254.carbon.butterfly.kafka` — Schema-registry helpers (`RimKafkaContracts`) and subject naming for `rim.fast-path`.

## Build & Dependency

- Maven coords: `com.studioz254.carbon:butterfly-common:0.2.1`
- Java 17, plain JAR (no Spring Boot).
- Avro Maven plugin generates sources from `src/main/avro` during `mvn compile`.
- Lombok and Jackson are included for the domain models; Lombok is `provided` scope.

## Adoption Guidance

- **CAPSULE**: Depends on this library for domain models and identity validation. Write paths should emit idempotency keys using `IdempotencyKeyGenerator` and validate `header.scope.scope_id` with `RimNodeId`.
- **PERCEPTION**: Use `RimNodeId` for JSON validation/serialization of RIM payloads and `RimFastEvent` for the fast-path streaming contract.
- **ODYSSEY**: Prefer `RimNodeId` over raw strings for world-state identifiers; reuse `RimFastEvent` for ingesting high-frequency signals.

## Versioning & Release

- Semantic-ish versioning; `0.x` is subject to change. Publish locally via `mvn clean install` or to the internal artifact repository before updating dependent services.
