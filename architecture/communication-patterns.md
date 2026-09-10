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
| `em` | `um` | Async event | `EngagementCreated` | Practitioner creates a new engagement |
| `em` | `um` | Async event | `EngagementOpened` | Practitioner opens an existing engagement (reconciliation) |
| `em` | `um` | Async event | `UpdateDecisionRecorded` | Practitioner accepts or declines an update |
| `tm` | `um` | Async event | `TemplatePublished` | Content team publishes a new template version |
| `um` | `tm` | Sync call | `TemplateQueryService.getVersionChain(templateId, fromVersion)` | `um` needs the ordered list of versions between current and latest |
| `um` | `ds` | Async event | `DiffSummaryRequested` | `um` needs a summary for a version gap (cache miss path) |
| `ds` | `um` | Async event | `SummaryGenerated` | Diff & Summary Engine finishes generating a summary |
| `dashboard` | `um` | Sync call | `ReadModelQueryService.getEngagementsByFirm(firmId)` | Dashboard UI loads update status list |
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
  + recordDecision(engagementId, decision, targetVersion, userId): void
```

### `um` — Update Management
```
ReadModelQueryService (public)
  + getEngagementsByFirm(firmId): List<EngagementReadModel>
  + getEngagement(engagementId): EngagementReadModel
```

### `ds` — Diff & Summary
```
DiffSummaryService (public)
  + requestSummary(templateId, fromVersion, toVersion): void  ← async, result comes back via SummaryGenerated event
```

### `dashboard` — Practitioner Dashboard
```
(no public interface — pure consumer, not called by other contexts)
```

---

## Event Payloads

| Event | Producer | Key Fields |
| :--- | :--- | :--- |
| `EngagementCreated` | `em` | `engagementId`, `firmId`, `templateId`, `initialTemplateVersion`, `timestamp` |
| `EngagementOpened` | `em` | `engagementId`, `firmId`, `rehydratedTemplateVersion`, `timestamp` |
| `UpdateDecisionRecorded` | `em` | `engagementId`, `firmId`, `decision`, `targetVersion`, `userId`, `timestamp` |
| `TemplatePublished` | `tm` | `templateId`, `version`, `previousVersion`, `checksum`, `storageUri`, `timestamp` |
| `DiffSummaryRequested` | `um` | `templateId`, `fromVersion`, `toVersion` |
| `SummaryGenerated` | `ds` | `summaryId`, `templateId`, `fromVersion`, `toVersion`, `narrative`, `diffHash` |

---

## Extracting to Microservices Later

Because no context crosses schema or internal boundaries, extracting a context into a separate service only requires:

1. Replace in-process event bus calls with a message broker (e.g. SNS/SQS, Kafka).
2. Replace direct sync service calls with HTTP or gRPC.
3. Split the DB schema into its own database instance.

No business logic changes are needed.
