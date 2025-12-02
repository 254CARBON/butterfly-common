# Butterfly Common Consumer Guide

Canonical identity, CAPSULE models, and HFT event contracts shared across CAPSULE, PERCEPTION, and ODYSSEY.

## Maven Coordinates

```xml
<dependency>
  <groupId>com.studioz254.carbon</groupId>
  <artifactId>butterfly-common</artifactId>
  <version>0.2.1</version>
</dependency>
```

## Project Notes

- **CAPSULE**: Scope IDs must be canonical `RimNodeId` values. Regenerate idempotency keys using `IdempotencyKeyGenerator.forSnapshot(...)`.
- **PERCEPTION**: RIM fusion emits CAPSULE envelopes with CAPSULE model types; RimFastPathPublisher now emits the expanded RimFastEvent contract using `RimKafkaContracts` for schema-registry settings.
- **ODYSSEY**: Reflex/HFT ingestion consumes `RimFastEvent`; world/actor/path entities carry optional `RimNodeId` with a JPA converter. Avro consumption can be toggled with `hft.kafka.value-format=AVRO`.

## Migration Tips

- Regenerate Avro classes after upgrading: `mvn -pl butterfly-common -am generate-sources`.
- Update Kafka consumers to trust new RimFastEvent fields (`event_id`, `instrument_id`, `price`, `volume`, `stress`, `source_topic`).
- Validate all scope identifiers with `RimNodeId.parse(...)` to avoid ingestion failures introduced in `0.2.x`.
- Keep additional metadata in `RimFastEvent.metadata` as string values; use `critical_metrics` for numeric metrics.
- See `IDENTITY_AND_CONTRACTS.md` for identity guardrails and schema-registry examples.
