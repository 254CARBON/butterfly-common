# BUTTERFLY Common Library

![Maturity](https://img.shields.io/badge/maturity-stable-brightgreen.svg)
![Version](https://img.shields.io/badge/version-0.2.1-blue.svg)
![Java](https://img.shields.io/badge/Java-17-orange.svg)
![License](https://img.shields.io/badge/license-MIT-green.svg)

> **Maturity**: Stable | **Status**: Production-ready shared contracts

Shared domain models, identity primitives, and event contracts for the BUTTERFLY ecosystem (CAPSULE, PERCEPTION, ODYSSEY).

## Responsibilities

- **Domain Models**: Canonical CAPSULE domain objects (Configuration, Dynamics, Agency, Counterfactual, Meta).
- **Identity**: `RimNodeId` plus `IdempotencyKeyGenerator` for the shared `(rimNodeId + snapshotTime + resolutionSeconds + vantageMode)` hash.
- **Contracts**: Shared Avro schemas (e.g., `RimFastEvent`) for high-frequency messaging.
- **Kafka/Schema Registry**: Canonical subject naming + helper configs in `RimKafkaContracts`.
- See `IDENTITY_AND_CONTRACTS.md` for guardrails, examples, and service-specific wiring notes.

## Usage

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.studioz254.carbon</groupId>
    <artifactId>butterfly-common</artifactId>
    <version>0.2.1</version>
</dependency>
```

### Identity helpers

```java
RimNodeId nodeId = RimNodeId.parse("rim:entity:finance:EURUSD");
String idempotencyKey = IdempotencyKeyGenerator.forSnapshot(
    nodeId,
    Instant.parse("2024-12-20T10:15:30Z"),
    60, // resolutionSeconds must be >0
    "omniscient"
);
```

### Avro codegen

Avro classes (e.g., `com.studioz254.carbon.butterfly.hft.RimFastEvent`) are generated during `mvn compile`. To regenerate manually:

```bash
mvn -pl butterfly-common -am generate-sources
```

### Kafka + schema registry helpers

```java
Map<String, Object> producerProps = RimKafkaContracts.fastPathProducerConfig(
    "localhost:9092", "http://localhost:8081");
producerProps.put(ProducerConfig.ACKS_CONFIG, "all");
KafkaTemplate<String, RimFastEvent> template =
        new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));
```
Subject naming for `rim.fast-path` is fixed as `rim.fast-path-value` (backward compatibility strategy).

### RimFastEvent contract

- Fields include `rim_node_id`, optional `event_id`/`instrument_id`, vector timestamps, `critical_metrics`, optional `price`/`volume`/`stress`, `status`, `source_topic`, and optional string metadata.
- Code generation uses this repository as the single source of truth; consumers should not maintain duplicate Avro schemas.
- Schema evolution: add new fields with defaults only, avoid renaming, and prefer optional/nullable fields for backward compatibility.

## Versioning

- `0.2.x` captures the tightened RimNodeId validation and expanded RimFastEvent contract (breaking from `0.1.x`).
- Future `0.3.x+` releases will stabilize before promoting to `1.0.0` once contracts stop changing across CAPSULE/PERCEPTION/ODYSSEY.
- Tagged releases publish to the internal Maven repository via the CI workflow; avoid snapshots outside local development.

## Build

```bash
mvn clean install
```
