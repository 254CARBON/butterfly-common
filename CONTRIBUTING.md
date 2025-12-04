# Contributing to butterfly-common

> Guidelines for contributing to the BUTTERFLY shared library

**Last Updated**: 2025-12-03  
**Version**: 0.2.1  
**Status**: Stable

---

## Welcome

Thank you for contributing to `butterfly-common`! This library provides shared domain models, identity primitives, and event contracts used across the entire BUTTERFLY ecosystem.

For ecosystem-wide contributing standards, see:

- **[Canonical Contributing Guide](../docs/development/contributing.md)** - Branch naming, commit conventions, PR process
- **[DEVELOPMENT_OVERVIEW.md](../DEVELOPMENT_OVERVIEW.md)** - Fast path to running the stack
- **[Coding Standards](../docs/development/coding-standards.md)** - Code style guidelines

---

## Library Overview

`butterfly-common` provides:

- **Domain Models**: Canonical CAPSULE domain objects (Configuration, Dynamics, Agency, Counterfactual, Meta)
- **Identity**: `RimNodeId` plus `IdempotencyKeyGenerator` for shared hash computation
- **Contracts**: Avro schemas (e.g., `RimFastEvent`) for high-frequency messaging
- **Kafka Helpers**: Subject naming and config helpers in `RimKafkaContracts`

---

## Development Setup

### Prerequisites

- Java 17
- Maven 3.9+

### Build and Install

```bash
cd butterfly-common
mvn clean install
```

This installs the library to your local Maven repository, making it available to dependent services.

### Regenerate Avro Classes

```bash
mvn generate-sources
```

---

## Contributing Guidelines

### API Stability

`butterfly-common` is a **shared dependency** used by all BUTTERFLY services. Changes here affect the entire ecosystem.

**Breaking Change Policy:**
- **MAJOR version** (1.x.x): Breaking API changes allowed
- **MINOR version** (0.x.0): New features, backward compatible
- **PATCH version** (0.0.x): Bug fixes only

Current version `0.2.x` is pre-1.0, so some breaking changes are expected but should be minimized.

### What Belongs Here

Add to `butterfly-common` if:

- Multiple services need the same domain model
- A contract (Avro schema) is shared across service boundaries
- Identity primitives are used ecosystem-wide

Do **NOT** add:

- Service-specific logic
- Implementation details
- Large dependencies that not all consumers need

---

## Avro Schema Guidelines

### Schema Evolution Rules

1. **Add new fields with defaults only** - Never remove or rename fields
2. **Prefer optional/nullable fields** - For backward compatibility
3. **Single source of truth** - Do not duplicate schemas in consumer services

### Example

```avro
{
  "type": "record",
  "name": "RimFastEvent",
  "namespace": "com.studioz254.carbon.butterfly.hft",
  "fields": [
    {"name": "rim_node_id", "type": "string"},
    {"name": "event_id", "type": ["null", "string"], "default": null},
    {"name": "new_field", "type": ["null", "string"], "default": null}  // Added with default
  ]
}
```

---

## Testing Requirements

### Test Matrix

| Test Type | Command | Coverage Target |
|-----------|---------|-----------------|
| Unit Tests | `mvn test` | ≥90% |

### What to Test

- **Identity validation**: `RimNodeId` parsing and validation rules
- **Idempotency generation**: Hash consistency
- **Serialization**: Avro round-trip tests

```bash
# Run all tests
mvn test

# With coverage
mvn test jacoco:report
```

---

## PR Checklist

Before submitting a PR, ensure:

### General

- [ ] Code follows [coding standards](../docs/development/coding-standards.md)
- [ ] All tests pass (`mvn verify`)
- [ ] Commit messages follow [conventional commits](../docs/development/contributing.md#commit-message-format)

### butterfly-common Specific

- [ ] No breaking changes (or version bump justified)
- [ ] New contracts include comprehensive documentation
- [ ] Avro schema changes follow evolution rules
- [ ] Consumer services tested with new version (at least one)
- [ ] `IDENTITY_AND_CONTRACTS.md` updated if identity rules changed

---

## Versioning

- **0.2.x**: Current series with tightened RimNodeId validation and expanded RimFastEvent
- **1.0.0**: Planned once contracts stabilize across CAPSULE/PERCEPTION/ODYSSEY

Tagged releases publish to the internal Maven repository via CI. Avoid snapshots outside local development.

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [README](README.md) | Usage guide and API examples |
| [IDENTITY_AND_CONTRACTS.md](IDENTITY_AND_CONTRACTS.md) | Identity guardrails and wiring notes |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Design overview |

---

*Thank you for contributing to butterfly-common!*

