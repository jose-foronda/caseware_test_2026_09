# Communication Patterns

How bounded contexts interact in the **modular monolith** deployment.
For context boundaries see [context-map.md](./context-map.md).
For data ownership see [erd.md](./erd.md).

---

## Rules

1. No context imports a repository or accesses a DB table from another schema.
2. Synchronous calls are only made to the **public service interface** of another context — never to internal classes.
3. Asynchronous communication uses an **in-process event bus** (same decoupling as a message broker, no network overhead).
4. If a context needs data owned by another context, it either calls its public service or maintains its own projection via events — never a cross-schema join.

---

## Patterns

### Synchronous — Direct In-Process Call

Used when the caller needs an answer before it can proceed.

```
CallerContext.SomeService  →  calls  →  TargetContext.PublicServiceInterface
```

### Asynchronous — In-Process Event Bus

Used when the producer does not need a response. The event bus dispatches to all subscribers in-process.

```
ProducerContext.SomeService  →  publishes  →  EventBus  →  ConsumerContext.SomeHandler
```

---

## Interaction Map

| Producer | Consumer | Pattern | Event / Method | Trigger |
| :--- | :--- | :--- | :--- | :--- |
| `em` | `em` | Async event | `EngagementCreated` | Practitioner creates a new engagement |
| `em` | `em` | Async event | `UpdateDecisionRecorded` | Practitioner accepts or declines an update |
| `tm` | `em` | Async event | `TemplatePublished` | Content team publishes a new template version |
| `em` | `tm` | Sync call | `TemplateQueryService.getVersionChain(templateId, fromVersion)` | `em` needs the ordered list of versions between current and latest |
| `em` | `ds` | Async event | `DiffSummaryRequested` | `em` needs a summary for a version gap (cache miss path) |
| `ds` | `em` | Async event | `SummaryGenerated` | Diff & Summary Engine finishes generating a summary |
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
  + requestSummary(templateId, fromVersionId, toVersionId): void  ← async, result comes back via SummaryGenerated event
```

### `dashboard` — Practitioner Dashboard
```
(no public interface — pure consumer, not called by other contexts)
```

---

## Event Payloads

| Event | Producer | Key Fields |
| :--- | :--- | :--- |
| `EngagementCreated` | `em` | `engagementId`, `tenantId`, `templateId`, `currentVersionId`, `timestamp` |
| `UpdateDecisionRecorded` | `em` | `engagementId`, `tenantId`, `decision`, `targetVersionId`, `userId`, `timestamp` |
| `TemplatePublished` | `tm` | `templateId`, `versionId`, `previousVersionId`, `locationKey`, `timestamp` |
| `DiffSummaryRequested` | `em` | `templateId`, `fromVersionId`, `toVersionId` |
| `SummaryGenerated` | `ds` | `summaryId`, `templateId`, `fromVersionId`, `toVersionId`, `narrative` |

---

## Extracting to Microservices Later

Because no context crosses schema or internal boundaries, extracting a context into a separate service only requires:

1. Replace in-process event bus calls with a message broker (e.g. SNS/SQS, Kafka).
2. Replace direct sync service calls with HTTP or gRPC.
3. Split the DB schema into its own database instance.

No business logic changes are needed.
