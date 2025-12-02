# Identity & Contracts

Single source of truth for RimNodeId, idempotency keys, and the RimFastEvent Avro contract.

## RimNodeId (identity)
- Format: `rim:{nodeType}:{namespace}:{localId}`. Allowed `nodeType`: `entity`, `region`, `market`, `actor`, `system`.
- Namespace: lowercase letters/numbers/hyphens (1–64 chars). Local ID: letters/numbers/hyphens/underscores (1–128 chars). Max whole string length: 255.
- Helpers: `RimNodeId.parse(...)` (throws), `tryParse(...)`/`isValid(...)` (non-throwing), `of(type, ns, id)` (component constructor).
- Anti-patterns: uppercase namespaces, missing segments, free-form UUIDs not prefixed with `rim:`.
- Example: `RimNodeId.parse("rim:entity:finance:EURUSD")`.

## Idempotency key generation
- Deterministic hash: `SHA-256(rimNodeId | snapshotTime | resolutionSeconds | vantageMode)`.
- Guardrails: `resolutionSeconds > 0`, vantage mode is trimmed + lower-cased, snapshot time is UTC (`Instant`); overloaded `forSnapshot(..., ZonedDateTime, ..)` handles zone conversion safely.
- Usage: `IdempotencyKeyGenerator.forSnapshot(rimNodeId, snapshotInstant, 60, "omniscient")`.

## RimFastEvent Avro contract
- Schema lives in `butterfly-common/src/main/avro/RimFastEvent.avsc` and is generated during `mvn generate-sources`.
- Compatibility: `BACKWARD` for `rim.fast-path` (constant: `RimKafkaContracts.FAST_PATH_COMPATIBILITY`).
- Subject naming: `rim.fast-path-value` (constant: `RimKafkaContracts.FAST_PATH_VALUE_SUBJECT`), using Confluent `TopicNameStrategy`.
- Schema-registry helpers:
  ```java
  Map<String, Object> producerProps = RimKafkaContracts.fastPathProducerConfig(
      "localhost:9092", "http://localhost:8081");
  producerProps.put(ProducerConfig.ACKS_CONFIG, "all");
  ProducerFactory<String, RimFastEvent> pf = new DefaultKafkaProducerFactory<>(producerProps);
  KafkaTemplate<String, RimFastEvent> template = new KafkaTemplate<>(pf);

  Map<String, Object> consumerProps = RimKafkaContracts.fastPathConsumerConfig(
      "localhost:9092", "http://localhost:8081");
  consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "odyssey-hft-reflex");
  ```
- Do not copy or fork the Avro schema into services—use the generated class from this module.

## Service how-tos
- Using RimFastEvent in PERCEPTION: set `rim.sinks.fast-path.enabled=true`, `rim.sinks.fast-path.bootstrap-servers=localhost:9092`, `rim.sinks.fast-path.schema-registry-url=http://localhost:8081`. The module exposes a `KafkaTemplate<String, RimFastEvent>` wired with Avro + registry defaults via `RimKafkaContracts`.
- Using RimNodeId in CAPSULE queries: parse/validate `header.scope.scope_id` with `RimNodeId.parse(...)`; derive write-path idempotency keys with `IdempotencyKeyGenerator.forSnapshot(...)` to match PERCEPTION replay behavior.
- Using RimFastEvent in ODYSSEY: keep JSON fallback for legacy payloads, or set `hft.kafka.value-format=AVRO` plus `hft.kafka.schema-registry-url` to consume Avro directly; map to reflex events via `RimFastEventMapper.toReflexEvent(...)`.

## Versioning & evolution
- Additive-only schema evolution with defaults; no renames or removals of existing fields.
- Backward compatibility on registry: `BACKWARD` for `rim.fast-path`.
- New contract work must land in `butterfly-common` first. CI guardrails block stray `*.avsc` files elsewhere.
