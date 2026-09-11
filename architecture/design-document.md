# Design Document — Engagement Template Update Management System

---

## Assumptions & Constraints

### Hard constraints (from the test brief)

| # | Constraint | Where it bites |
| :--- | :--- | :--- |
| 1 | Opening an engagement takes **~1 minute** (rehydration) — this is a hard constraint. | The dashboard must never trigger rehydration; all reads go through the read model. |
| 2 | Stored engagements are **not a directly queryable record** — only `template_id` + version are stored in customer-specific DBs. | No live derivation of engagement state; status must be pre-materialized. |
| 3 | Product templates are **zip archives of structured JSON**. | Diff/narrative layer must understand JSON structure, not a DB schema. |
| 4 | The template DB is **shared between firms** and holds **no engagement information**. | Engagement tracking lives only in `em`; template DB is read-only from our perspective. |
| 5 | A quick, reliable **JSON diff extractor exists**; users are **non-technical** and want human-readable output. | `ds` must translate JSON diff → narrative; raw JSON is the fallback, not the default. |
| 6 | We may add **hooks/events** to template publishing and the engagement management system. | This is the only integration surface — no direct DB writes to the engagement blob store. |
| 7 | **Applying** updated template content is **out of scope**. | The system stops at surfacing updates and recording accept/decline decisions. |

### Assumptions (stated where the brief was silent)

| # | Assumption | Rationale |
| :--- | :--- | :--- |
| 1 | Stack: **Java 21 + Spring Boot 3**, PostgreSQL, deployed as one ECS service + RDS on **AWS**. | Brief notes AWS shop; JVM matches the existing EMS deployment model (modular monolith). |
| 2 | Scale is small: **~100s of engagements per firm**, **~1 template update/week/product**. | Fan-out and diff-summary cache are cheap; a distributed pipeline would be over-engineering. |
| 3 | **In-process, synchronous events** are sufficient for now. | Producer work and read-model projection commit/roll back together — nothing dropped. Brokered async (SQS/SNS) is deferred; the event contract is broker-ready. |
| 4 | `tenant_id` is a **correlation ID from external auth**; tenant provisioning is out of scope. | Inferred from "customer-specific databases" — isolation is a given, identity is external. |
| 5 | Version identity is a **UUID**; version strings (e.g. `"1.2.0"`) are display-only. | Stable PKs for joins; human-friendly labels for the UI. |
| 6 | `em` can **sync-call `tm` for the version chain** to validate a decision target. | Needed to guarantee the target lies in `current → latest`; deferred in the slice but assumed for production. |
| 7 | Accumulated updates are decided **as spans**: one decision per `current → target` jump; practitioners may resolve intermediate versions one by one. | Matches "multiple updates accumulate" without a per-version decision matrix. |
| 8 | Diff narratives are shared across tenants (keyed by version gap), not per engagement. | Same template gap reads the same for every firm — caching is a natural win. |

---

## 1. High-Level Architecture

The system is designed as a **modular monolith** — a single deployable with a single database, schema-separated by bounded context. This matches the existing EMS deployment model and avoids distributed systems complexity while keeping context boundaries clean and extractable to microservices later.

Three bounded contexts, each owning its schema:

| Context | Schema | Responsibility |
| :--- | :--- | :--- |
| Template Management | `tm` | Stores templates and immutable version index. Emits `TemplatePublished`. |
| Engagement Management *(core)* | `em` | Creates engagements, records decisions, owns the read model and decision trail. |
| Diff & Summary | `ds` | Generates and caches human-readable diff narratives per version pair. |

**The key design constraint** is that opening an engagement takes ~1 minute. The dashboard must never trigger rehydration. This is solved by `em_engagement_read_model` — a lightweight projection maintained by synchronous, in-transaction event handlers, always queryable without rehydration.

**Update status** is derived at read time from two fields on the read model: `last_decided_version_id == latest_version_id` → `UPDATES_REVIEWED`, otherwise `PENDING_UPDATES`. No stored status column — no drift risk.

**Diff summaries** are lazy and synchronous — generated on demand when a practitioner clicks "Compare", cached in `ds_diff_summary` keyed by `(template_id, from_version_id, to_version_id)` and shared across all engagements on the same version gap.

**Stack:** Java 21 + Spring Boot 3, PostgreSQL (schema-separated), deployed as a single service on AWS (ECS + RDS). Events are published via Spring `ApplicationEventPublisher` with `@EventListener` — handlers run **synchronously inside the originating transaction**, so producer work (e.g. the decision row) and the read-model projection commit or roll back together. Nothing is dropped; no async window. If a message broker with durable delivery and retries is adopted later, consumption becomes async without changing the event contract.

> Supporting diagrams: [context-map.md](./context-map.md) · [erd.md](./erd.md) · [business-flows.md](./business-flows.md) · [communication-patterns.md](./communication-patterns.md)

---

## 2. Implementation Plan

**Stage 1 — Schema & Entities**
- `tm` schema: `tm_product_template`, `tm_product_template_version`
- `em` schema: `em_client`, `em_engagement`, `em_engagement_read_model`, `em_update_decision`
- `ds` schema: `ds_diff_summary`
- `sys` schema: `sys_error_log`

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
- Event handler failure rate: failed `TemplatePublished` and `UpdateDecisionRecorded` handler executions are written to `sys_error_log` with `status = PENDING_RETRY` or `FAILED`. A failed handler means the read model is silently stale.
- `sys_error_log` row count by status: queryable at any time for operational visibility without external tooling.
- Diff summary cache hit rate: low hit rate signals version pair diversity or cache eviction issues.
- `DiffSummaryService` p95 latency on cache miss (blob reads + LLM call) — expected to be slow; set user expectation via UI loading state.

**Structured logging**
- Every event handler logs `engagementId`, `templateId`, `versionId`, `tenantId`, and processing duration.
- Every decision records `decidedBy`, `decision`, `fromVersionId`, `targetVersionId` — already in `em_update_decision`.

**APM (e.g. Datadog)**
- Instrument the service with a Datadog APM agent to track API endpoint response times, DB query latency, and slow query detection across all schemas.
- Dashboard query traces (`getEngagementsByTenant()`) and fan-out handler traces (`TemplatePublished`) are the primary spans to monitor.
- DB-level metrics (query throughput, connection pool saturation, index hit rate) surfaced via Datadog's PostgreSQL integration — no custom instrumentation needed.

**Alerting**
- `sys_error_log` rows with `status = FAILED` — alert on new entries. A missed `TemplatePublished` fan-out means engagements silently show stale status.
- `PENDING_RETRY` rows are retried by a background job. `FAILED` status requires manual intervention.
- LLM call failures in `DiffSummaryService` — fall back gracefully (return raw diff or error message), never block the decision flow.

---

## 5. Failure Modes & Tradeoffs

| Failure | Impact | Mitigation |
| :--- | :--- | :--- |
| `TemplatePublished` handler fails mid-fan-out | Handler failure rolls back the publishing transaction — no partial fan-out | Handler is idempotent. `sys_error_log` is written in a separate transaction (`REQUIRES_NEW`) for diagnosis; the operation is retried by the publisher or manually. A broker (DLQ + retry) would automate this later. Fan-out is a bulk update — wrap in a transaction per batch. |
| `DiffSummaryService` LLM call fails | Practitioner cannot view narrative | Return raw JSON diff as fallback. Decision flow is unblocked — narrative is informational only. |

**Key tradeoffs**

- **Synchronous in-process events vs message broker (SQS/SNS)**: consuming events synchronously in-transaction (`@EventListener`) is the most reliable option without a broker — producer work and projection commit or roll back together, so events can never be dropped or half-applied. Cost: the handler runs on the request thread, so slow handlers add latency, and there is no automatic retry. A message broker would give durable delivery, DLQs, and retry out of the box, at the cost of distributed systems complexity, eventual consistency, and async failure semantics. Chosen for simplicity, atomicity, and deployment fit — **the code publishes events today; swapping the dispatch for a broker (async consumption) later requires no business logic changes.**
- **Lazy diff generation vs pre-computation**: pre-computing on `TemplatePublished` would add latency to the publish flow and waste compute for version pairs nobody compares. Lazy + cache is the right tradeoff given infrequent publishes and shared cache across tenants.
- **Stored read model vs live query**: live derivation would require rehydration (~1 min/engagement) — not viable at scale. The read model projection is the only practical approach given the hard constraint.
