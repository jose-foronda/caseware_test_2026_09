# Communication Patterns

How bounded contexts interact in the **modular monolith** deployment.
For context boundaries see [context-map.md](./context-map.md).
For data ownership see [erd.md](./erd.md).

---

## Rules

1. No context imports a repository or accesses a DB table from another schema.
2. Synchronous calls are only made to the **public service interface** of another context — never to internal classes.
3. Events are **published in-process and consumed synchronously** within the originating transaction (`@EventListener`) — decision + projection commit or roll back together.
4. If a context needs data owned by another context, it either calls its public service or maintains its own projection via events — never a cross-schema join.
5. Async consumption (message broker with durable delivery and retries) is deferred — only adopted when those guarantees are required.

---

## Patterns

### Synchronous — Direct In-Process Call

Used when the caller needs an answer before it can proceed.

```
CallerContext.SomeService  →  calls  →  TargetContext.PublicServiceInterface
```

### Event — Synchronous In-Process Consumption

Used when the producer does not need a response but must guarantee the consumer ran. The event is dispatched in-process and handled **synchronously in the same transaction** as the producer's work, so everything commits or rolls back atomically. A failure in the handler rolls the producer back too — no lost or half-applied events.

```
ProducerContext.SomeService  →  publishes  →  EventBus  →  ConsumerContext.SomeHandler
        (same transaction, synchronous)
```

> **Future:** if a message broker (SNS/SQS, Kafka) is adopted for durable delivery, DLQs, and retries, these same handlers run *asynchronously*. The event contract and subscriber code stay the same — only the dispatch changes.

---

## Interaction Map

| Producer | Consumer | Pattern | Event / Method | Trigger |
| :--- | :--- | :--- | :--- | :--- |
| `em` | `em` | Event (sync in-tx) | `EngagementCreated` | Practitioner creates a new engagement |
| `em` | `em` | Event (sync in-tx) | `UpdateDecisionRecorded` | Practitioner accepts or declines an update |
| `tm` | `em` | Event (sync in-tx) | `TemplatePublished` | Content team publishes a new template version |
| `em` | `tm` | Sync call | `TemplateQueryService.getVersionChain(templateId, fromVersion)` | `em` needs the ordered list of versions between current and latest |
| `em` | `ds` | Sync call | `DiffSummaryService.getSummary(templateId, fromVersionId, toVersionId)` | Practitioner clicks Compare |
| `ds` | `tm` | Sync call | `TemplateQueryService.getVersionChain(templateId, fromVersion)` | `ds` needs `location_key`s to read zip archives |
| `dashboard` | `em` | Sync call | `EngagementQueryService.getEngagementsByTenant(tenantId)` | Dashboard UI loads update status list |
| `dashboard` | `em` | Sync call | `EngagementService.recordDecision(engagementId, decision)` | Practitioner submits accept / decline |

---

## Public Interface Per Context

Each context exposes only what other contexts are allowed to call. Everything else is internal.

### `tm` — Template Management
```
TemplateQueryService (public)
  + getVersionChain(templateId, fromVersion): List<TemplateVersion>
```

### `em` — Engagement Management
```
EngagementService (public)
  + recordDecision(engagementId, decision, targetVersionId, userId, reason): void

EngagementQueryService (public)
  + getEngagementsByTenant(tenantId): List<EngagementReadModel>
  + getEngagement(engagementId): EngagementReadModel
```

### `ds` — Diff & Summary
```
DiffSummaryService (public)
  + getSummary(templateId, fromVersionId, toVersionId): Summary
```

### `dashboard` — Practitioner Dashboard
```
(no public interface — pure consumer, not called by other contexts)
```

---

## Event Payloads

| Event | Producer | Key Fields |
| :--- | :--- | :--- |
| `EngagementCreated` | `em` | `engagementId`, `tenantId`, `clientId`, `templateId`, `currentVersionId`, `timestamp` |
| `UpdateDecisionRecorded` | `em` | `engagementId`, `tenantId`, `decision`, `targetVersionId`, `userId`, `timestamp` |
| `TemplatePublished` | `tm` | `templateId`, `versionId`, `previousVersionId`, `locationKey`, `timestamp` |

---

## Extracting to Microservices Later

Because no context crosses schema or internal boundaries, extracting a context into a separate service only requires:

1. Replace in-process synchronous event dispatch with a message broker (e.g. SNS/SQS, Kafka) — handlers become async consumers with durable delivery, DLQs, and retries.
2. Replace direct sync service calls with HTTP or gRPC.
3. Split the DB schema into its own database instance.

No business logic changes are needed.
