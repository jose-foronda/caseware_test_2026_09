# Design Document — Engagement Template Update Management System

---

## 1. High-Level Architecture

The system is designed as a **modular monolith** — a single deployable with a single database, schema-separated by bounded context. This matches the existing EMS deployment model and avoids distributed systems complexity while keeping context boundaries clean and extractable to microservices later.

Three bounded contexts, each owning its schema:

| Context | Schema | Responsibility |
| :--- | :--- | :--- |
| Template Management | `tm` | Stores templates and immutable version index. Emits `TemplatePublished`. |
| Engagement Management *(core)* | `em` | Creates engagements, records decisions, owns the read model and decision trail. |
| Diff & Summary | `ds` | Generates and caches human-readable diff narratives per version pair. |

**The key design constraint** is that opening an engagement takes ~1 minute. The dashboard must never trigger rehydration. This is solved by `em_engagement_read_model` — a lightweight projection maintained by async event handlers, always queryable without rehydration.

**Update status** is derived at read time from two fields on the read model: `last_decided_version_id == latest_version_id` → `UPDATES_REVIEWED`, otherwise `PENDING_UPDATES`. No stored status column — no drift risk.

**Diff summaries** are lazy and synchronous — generated on demand when a practitioner clicks "Compare", cached in `ds_diff_summary` keyed by `(template_id, from_version_id, to_version_id)` and shared across all engagements on the same version gap.

**Stack:** Java 21 + Spring Boot 3, PostgreSQL (schema-separated), deployed as a single service on AWS (ECS + RDS). In-process async via Spring `ApplicationEventPublisher` with `@TransactionalEventListener` — handlers fire only after the originating transaction commits, preventing read model updates on rollback.

> Supporting diagrams: [context-map.md](./context-map.md) · [erd.md](./erd.md) · [business-flows.md](./business-flows.md) · [communication-patterns.md](./communication-patterns.md)

---

## 2. Implementation Plan

**Stage 1 — Schema & Entities**
- `tm` schema: `tm_product_template`, `tm_product_template_version`
- `em` schema: `em_client`, `em_engagement`, `em_engagement_read_model`, `em_update_decision`
- `ds` schema: `ds_diff_summary`

**Stage 2 — Skeleton (Controller + Service + Repository, no logic)**
- `tm`: `TemplateQueryService` + repository
- `em`: `EngagementService`, `EngagementQueryService` + repositories
- `ds`: `DiffSummaryService` + repository
- `dashboard`: `DashboardController` wired to `em` services

**Stage 3 — CRUDs**
- Template + version CRUD (`tm`)
- Client + engagement CRUD (`em`)
- Diff summary CRUD (`ds`)

**Stage 4 — Aggregated Flows**
- UC-1: `TemplatePublished` handler → read model fan-out
- UC-3: `EngagementCreated` handler → read model insert
- UC-6: `getSummary()` with cache hit/miss logic + LLM
- UC-7: `recordDecision()` + `UpdateDecisionRecorded` handler → read model update

Each stage is independently deployable and testable. Stages 1 and 2 unblock parallel frontend and backend work immediately.

---

## 3. Testing Strategy

**Unit tests**
- Event handlers: given a `TemplatePublished` event, assert correct `latest_version_id` fan-out on read model rows.
- Decision handler: assert `last_decided_version_id` and `current_version_id` updated correctly for both APPLIED and DECLINED.
- `update_status` derivation: assert `UPDATES_REVIEWED` / `PENDING_UPDATES` for all combinations of `last_decided_version_id` vs `latest_version_id`.

**Integration tests**
- Full UC-1 → UC-7 flow against a real DB (test schema): publish template, create engagement, verify read model, submit decision, verify read model update.
- Cache hit vs cache miss paths in `DiffSummaryService`.

**What is not tested here**
- Actual template content application (out of scope per spec).
- LLM narrative quality — validated separately via prompt evaluation, not unit tests.

**Load & Performance Testing (recommended)**

Using **Gatling** (JVM-based, tests as code, CI-friendly) against a staging environment seeded with realistic data (100s of engagements per tenant, multiple tenants, multiple products):

- *Fan-out scenario*: publish a template update and assert all read model rows for that template are updated within an acceptable time window.
- *Concurrent dashboard reads*: simulate N tenants hitting `getEngagementsByTenant()` simultaneously — read model is a simple indexed query, p95 latency should remain low.
- *Concurrent decision submissions*: multiple practitioners submitting decisions at the same time — assert no lost updates on `em_update_decision` and correct final read model state.

Gatling reports p95/p99 latency out of the box, which maps directly to the alerting thresholds defined in §4.

---

## 4. Evaluation & Observability

**Key metrics to instrument**
- Read model staleness: time between `TemplatePublished` event emission and read model update completion. Alert if > 30s.
- Decision handler lag: time between `UpdateDecisionRecorded` emission and read model update. Alert if > 10s.
- Diff summary cache hit rate: low hit rate signals version pair diversity or cache eviction issues.
- `DiffSummaryService` p95 latency on cache miss (blob reads + LLM call) — expected to be slow; set user expectation via UI loading state.

**Structured logging**
- Every event handler logs `engagementId`, `templateId`, `versionId`, `tenantId`, and processing duration.
- Every decision records `decidedBy`, `decision`, `fromVersionId`, `targetVersionId` — already in `em_update_decision`.

**Alerting**
- Dead-letter queue (or equivalent) for failed event handler processing — a missed `TemplatePublished` fan-out means engagements silently show stale status.
- LLM call failures in `DiffSummaryService` — fall back gracefully (return raw diff or error message), never block the decision flow.

---

## 5. Failure Modes & Tradeoffs

| Failure | Impact | Mitigation |
| :--- | :--- | :--- |
| `TemplatePublished` handler fails mid-fan-out | Some engagements show stale `latest_version_id` | Idempotent handler + retry. Fan-out is a bulk update — wrap in a transaction per batch. |
| `DiffSummaryService` LLM call fails | Practitioner cannot view narrative | Return raw JSON diff as fallback. Decision flow is unblocked — narrative is informational only. |
| Read model row missing for an engagement | Dashboard shows no entry | `EngagementCreated` handler is idempotent — re-emit or backfill on detection. |
| Practitioner declines all versions, new version published | Status correctly flips to `PENDING_UPDATES` | Derived from `last_decided_version_id != latest_version_id` — no special handling needed. |
| Large tenant with 100s of engagements on same template | Fan-out on `TemplatePublished` is a bulk write | Batched update with configurable page size. Acceptable given ~1/week publish frequency. |

**Key tradeoffs**

- **Modular monolith vs microservices**: chosen for simplicity and deployment fit. The context boundaries are clean — extractable later by swapping in-process event bus for a message broker and splitting schemas.
- **Lazy diff generation vs pre-computation**: pre-computing on `TemplatePublished` would add latency to the publish flow and waste compute for version pairs nobody compares. Lazy + cache is the right tradeoff given infrequent publishes and shared cache across tenants.
- **Stored read model vs live query**: live derivation would require rehydration (~1 min/engagement) — not viable at scale. The read model projection is the only practical approach given the hard constraint.
