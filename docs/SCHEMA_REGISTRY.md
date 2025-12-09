# BUTTERFLY Schema Registry Guide

Documentation for Avro schema management, versioning, and compatibility rules across the BUTTERFLY ecosystem.

## Table of Contents

1. [Overview](#overview)
2. [Schema Registry Configuration](#schema-registry-configuration)
3. [Schema Naming Conventions](#schema-naming-conventions)
4. [Compatibility Rules](#compatibility-rules)
5. [Schema Evolution Guidelines](#schema-evolution-guidelines)
6. [Centralized Schemas](#centralized-schemas)
7. [Breaking Change Process](#breaking-change-process)
8. [Schema Validation](#schema-validation)
9. [Troubleshooting](#troubleshooting)

---

## Overview

The BUTTERFLY ecosystem uses Apache Avro schemas with Confluent Schema Registry for event serialization. All Kafka messages should use registered schemas to ensure compatibility and enable schema evolution.

### Key Principles

1. **Backward Compatibility by Default**: New consumers can read old data
2. **Centralized Common Schemas**: Shared schemas in `butterfly-common`
3. **Service-Specific Schemas**: Module-specific schemas in each service
4. **Version Tracking**: All schemas include version metadata

### Schema Locations

| Location | Purpose |
|----------|---------|
| `butterfly-common/src/main/avro/` | Shared cross-service schemas |
| `{service}/src/main/avro/` | Service-specific schemas |
| `{service}/target/generated-sources/avro/` | Generated Java classes |

---

## Schema Registry Configuration

### Production Configuration

```yaml
spring:
  kafka:
    properties:
      schema.registry.url: http://schema-registry:8081
      # Use AVRO serializers
      key.serializer: org.apache.kafka.common.serialization.StringSerializer
      value.serializer: io.confluent.kafka.serializers.KafkaAvroSerializer
      key.deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value.deserializer: io.confluent.kafka.serializers.KafkaAvroDeserializer
      # Schema settings
      auto.register.schemas: false  # Require explicit registration in production
      use.latest.version: true
      specific.avro.reader: true
```

### Development Configuration

```yaml
spring:
  kafka:
    properties:
      schema.registry.url: http://localhost:8081
      auto.register.schemas: true  # Allow auto-registration in dev
```

### Schema Registry Health Check

The `SchemaRegistryHealthIndicator` monitors registry availability:

```java
@Bean
public SchemaRegistryHealthIndicator schemaRegistryHealth() {
    return new SchemaRegistryHealthIndicator(schemaRegistryUrl);
}
```

---

## Schema Naming Conventions

### Namespace Pattern

```
com.z254.butterfly.{service}.avro
```

| Service | Namespace |
|---------|-----------|
| Common | `com.z254.butterfly.common.avro` |
| Perception | `com.z254.butterfly.perception.avro` |
| Capsule | `com.z254.butterfly.capsule.avro` |
| Odyssey | `com.z254.butterfly.odyssey.avro` |
| Plato | `com.z254.butterfly.plato.avro` |
| Nexus | `com.z254.butterfly.nexus.avro` |
| Synapse | `com.z254.butterfly.synapse.avro` |
| Cortex | `com.z254.butterfly.cortex.avro` |

### Record Naming

```
{ServiceName}{Domain}{EventType}

Examples:
- PerceptionSignalEvent
- CapsuleDetectionEvent
- SynapseOutcomeEvent
- NexusSynthesisEvent
```

### Subject Naming (Schema Registry)

Subjects follow the topic naming convention:

```
{topic-name}-value
{topic-name}-key

Examples:
- capsule.events.created-value
- perception.signals.generated-value
```

---

## Compatibility Rules

### Default: BACKWARD Compatibility

New schema can read data written by old schema.

**Allowed Changes:**
- Add optional fields (with default values)
- Remove fields with default values
- Add new enum values (at the end)

**Disallowed Changes:**
- Remove required fields
- Change field types
- Rename fields
- Remove enum values

### Compatibility Modes

| Mode | Description | Use Case |
|------|-------------|----------|
| `BACKWARD` | New can read old (default) | Most services |
| `FORWARD` | Old can read new | Coordinated upgrades |
| `FULL` | Both directions | Critical events |
| `NONE` | No compatibility | Development only |

### Setting Compatibility

```bash
# Set subject-level compatibility
curl -X PUT -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  --data '{"compatibility": "BACKWARD"}' \
  http://schema-registry:8081/config/capsule.events.created-value
```

---

## Schema Evolution Guidelines

### Adding a New Field

✅ **Correct**: Add with default value

```avro
{
  "name": "newField",
  "type": ["null", "string"],
  "default": null,
  "doc": "Added in v1.2.0 - optional new field"
}
```

### Removing a Field

✅ **Correct**: Remove only if field had default

```avro
// Before (v1.1.0)
{
  "name": "oldField",
  "type": ["null", "string"],
  "default": null
}

// After (v1.2.0) - field removed entirely
```

### Renaming a Field

❌ **Incorrect**: Direct rename breaks compatibility

```avro
// DON'T DO THIS
"name": "newFieldName"  // was "oldFieldName"
```

✅ **Correct**: Add new, deprecate old

```avro
{
  "name": "oldFieldName",
  "type": ["null", "string"],
  "default": null,
  "doc": "@deprecated Use newFieldName instead"
},
{
  "name": "newFieldName",
  "type": ["null", "string"],
  "default": null,
  "doc": "Added in v1.2.0 - replaces oldFieldName"
}
```

### Changing Field Types

❌ **Incorrect**: Direct type change

```avro
"type": "long"  // was "int"
```

✅ **Correct**: Union or new field

```avro
// Option 1: Union (if compatible promotion)
"type": ["int", "long"]

// Option 2: New field
{
  "name": "valueAsLong",
  "type": ["null", "long"],
  "default": null
}
```

### Adding Enum Values

✅ **Correct**: Add at the end

```avro
{
  "type": "enum",
  "name": "Status",
  "symbols": ["PENDING", "ACTIVE", "COMPLETED", "CANCELLED"]  // CANCELLED added
}
```

❌ **Incorrect**: Insert in middle or remove

---

## Centralized Schemas

### UnifiedEventHeader

All events should include or reference `UnifiedEventHeader`:

```avro
// butterfly-common/src/main/avro/UnifiedEventHeader.avsc
{
  "namespace": "com.z254.butterfly.common.avro",
  "type": "record",
  "name": "UnifiedEventHeader",
  "fields": [
    {"name": "event_id", "type": "string"},
    {"name": "correlation_id", "type": "string"},
    {"name": "causation_id", "type": ["null", "string"], "default": null},
    {"name": "origin_system", "type": {"type": "enum", "name": "OriginSystem", "symbols": ["PERCEPTION", "CAPSULE", "ODYSSEY", "PLATO", "NEXUS", "SYNAPSE", "EXTERNAL"]}},
    {"name": "timestamp", "type": "long"},
    {"name": "trace_context", "type": "TraceContext"},
    {"name": "source_service", "type": "string"},
    {"name": "schema_version", "type": "string", "default": "1.0.0"},
    {"name": "tags", "type": ["null", {"type": "map", "values": "string"}], "default": null}
  ]
}
```

### Using UnifiedEventHeader

Reference in your schemas:

```avro
{
  "namespace": "com.z254.butterfly.capsule.avro",
  "type": "record",
  "name": "CapsuleCreatedEvent",
  "fields": [
    {"name": "header", "type": "com.z254.butterfly.common.avro.UnifiedEventHeader"},
    {"name": "capsuleId", "type": "string"},
    {"name": "scopeId", "type": "string"},
    {"name": "content", "type": "bytes"},
    {"name": "createdAt", "type": "long"}
  ]
}
```

### Common Schema List

| Schema | Description | Location |
|--------|-------------|----------|
| `UnifiedEventHeader` | Event correlation and tracing | `butterfly-common` |
| `TraceContext` | OpenTelemetry trace context | `butterfly-common` |
| `NexusContext` | Cross-module correlation | `butterfly-common` |
| `LearningSignal` | Learning feedback events | `butterfly-common` |
| `LearningOutcome` | Learning outcome events | `butterfly-common` |
| `GovernancePolicy` | Policy update events | `butterfly-common` |
| `AuditEvent` | Audit trail events | `butterfly-common` |
| `RimFastEvent` | RIM fast-path events | `butterfly-common` |

---

## Breaking Change Process

### When Breaking Changes Are Necessary

1. Fundamental data model changes
2. Security-related schema modifications
3. Major version upgrades

### Process

1. **Proposal**: Create RFC document describing change
2. **Review**: Engineering review of impact
3. **Communication**: Notify all consuming teams
4. **Migration Period**: 
   - Create new schema with new name/version
   - Update producers to write both formats
   - Update consumers to read new format
   - Deprecate old schema
5. **Cutover**: Remove old schema support
6. **Cleanup**: Delete deprecated schema

### Timeline

| Phase | Duration | Activities |
|-------|----------|------------|
| Proposal | 1 week | RFC, impact analysis |
| Development | 1-2 weeks | New schema, dual writes |
| Migration | 2-4 weeks | Consumer updates |
| Deprecation | 2 weeks | Remove old schema |

### Documentation

Create migration guide:

```markdown
# Schema Migration: CapsuleEvent v1 → v2

## Summary
Breaking change to CapsuleEvent schema for improved temporal precision.

## Changes
- `timestamp`: Changed from `int` (seconds) to `long` (milliseconds)
- `content`: Changed from embedded record to reference

## Migration Steps
1. Deploy consumer with v2 support by 2025-03-01
2. Producer switch to v2-only by 2025-03-15
3. v1 sunset on 2025-04-01
```

---

## Schema Validation

### Build-Time Validation

Maven plugin configuration:

```xml
<plugin>
    <groupId>org.apache.avro</groupId>
    <artifactId>avro-maven-plugin</artifactId>
    <version>1.11.3</version>
    <executions>
        <execution>
            <phase>generate-sources</phase>
            <goals>
                <goal>schema</goal>
            </goals>
            <configuration>
                <sourceDirectory>${project.basedir}/src/main/avro</sourceDirectory>
                <outputDirectory>${project.build.directory}/generated-sources/avro</outputDirectory>
                <stringType>String</stringType>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Compatibility Testing

Run compatibility check before deployment:

```bash
# Check compatibility with latest registered version
curl -X POST -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  --data '{"schema": "{...escaped schema...}"}' \
  http://schema-registry:8081/compatibility/subjects/{subject}/versions/latest
```

### CI/CD Integration

```yaml
# .github/workflows/schema-check.yml
- name: Check Schema Compatibility
  run: |
    for schema in src/main/avro/*.avsc; do
      subject=$(basename $schema .avsc)-value
      ./scripts/check-compatibility.sh $schema $subject
    done
```

---

## Troubleshooting

### Common Issues

#### Schema Not Found

```
Error: Subject 'capsule.events.created-value' not found
```

**Solution**: Register schema first

```bash
curl -X POST -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  --data '{"schema": "{...}"}' \
  http://schema-registry:8081/subjects/capsule.events.created-value/versions
```

#### Incompatible Schema

```
Error: Schema being registered is incompatible with an earlier schema
```

**Solution**: Check compatibility rules and make backward-compatible changes

#### Deserialization Error

```
Error: Could not find class com.z254.butterfly.capsule.avro.CapsuleEvent
```

**Solution**: Ensure generated classes are on classpath

```xml
<dependency>
    <groupId>com.z254.butterfly</groupId>
    <artifactId>butterfly-common</artifactId>
    <version>${project.version}</version>
</dependency>
```

### Schema Registry CLI

```bash
# List all subjects
curl http://schema-registry:8081/subjects

# Get latest version of a subject
curl http://schema-registry:8081/subjects/capsule.events.created-value/versions/latest

# Delete a subject (use with caution)
curl -X DELETE http://schema-registry:8081/subjects/test-subject

# Get schema by ID
curl http://schema-registry:8081/schemas/ids/1
```

---

## Best Practices Summary

1. **Always include default values** for new fields
2. **Document all changes** with version and date
3. **Test compatibility** before deployment
4. **Use UnifiedEventHeader** for correlation
5. **Version your schemas** using `schema_version` field
6. **Never remove required fields** without migration
7. **Avoid type changes** - use unions or new fields
8. **Register schemas explicitly** in production
9. **Monitor schema registry** health
10. **Keep changelog** of all schema modifications
