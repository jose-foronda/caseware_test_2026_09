# Specification — Engagement Template Update Management System

Current state of all architecture decisions and open items.

---

## Progress Tracker

### Part 1: Architecture & Design

| Section | Status | Notes |
| :--- | :--- | :--- |
| High-Level Architecture | ✅ Done | context-map, ERD, communication-patterns, business-flows, glossary |
| Implementation Plan | ✅ Done | design-document.md §2 |
| Testing Strategy | ✅ Done | design-document.md §3 |
| Evaluation & Observability | ✅ Done | design-document.md §4 |
| Failure Modes & Tradeoffs | ✅ Done | design-document.md §5 |
| Assumptions & Constraints | ✅ Done | design-document.md §0 (above §1) |

### Part 2: Targeted Implementation

| Section | Status | Notes |
| :--- | :--- | :--- |
| Code slice | ✅ Done | UC-7 vertical slice in [`engagement-template/`](../engagement-template) — see [implementation-spec.md](./implementation-spec.md) |

---

## Deliverables

Checklist against the "Deliverables" section of the test brief:

| # | Required deliverable | Where to find it |
| :--- | :--- | :--- |
| 1 | **Design document** (PDF or Markdown) — High-Level Architecture, Implementation Plan, Testing Strategy, Evaluation & Observability, Failure Modes & Tradeoffs | [`design-document.md`](./design-document.md) |
| 2 | **Code / repository** *(optional)* — Part 2 targeted implementation (UC-7 vertical slice, contract + correctness focus, with tests) | [`engagement-template/`](../engagement-template) — see [`implementation-spec.md`](./implementation-spec.md) |
| 3 | **Session history for AI tooling** *(optional)* | [`ai-session-history.md`](./ai-session-history.md) |
| 4 | **Diagrams** — bounded context map & entity-relationship diagrams (Mermaid) | [`context-map.md`](./context-map.md) · [`erd.md`](./erd.md) |

> The original test brief is available at the repository root as [`Senior Developer, SE - Take-Home Test.pdf`](../Senior%20Developer%2C%20SE%20-%20Take-Home%20Test.pdf).

---

## Deployment Model

- **Modular monolith** — single deployable, single database, schema-separated by bounded context.
- Contexts communicate via events (published in-process, consumed **synchronously in-transaction**) or public service interfaces (sync).
- No cross-schema DB joins. Cross-context references are correlation IDs only.
- Extractable to microservices later with no business logic changes (swap in-process events → message broker with async consumption + retries, sync calls → HTTP/gRPC, split DB schemas).

---

## Schemas

| Schema | Bounded Context | Owns |
| :--- | :--- | :--- |
| `tm` | Template Management | Templates and their versions |
| `em` | Engagement Management | Clients, engagement blobs, read model, decisions |
| `ds` | Diff & Summary | Diff summaries |

> `tenant_id` is a correlation ID from an external identity/auth system — not owned by any schema.

---

## Key Decisions

| # | Decision | Rationale |
| :--- | :--- | :--- |
| 1 | Read model lives in `em` schema | EMS owns creation, loading, and decision processing per the spec. Read model is a projection maintained by EMS event handlers. |
| 2 | `um` schema removed | Update Tracking logic has no tables of its own — it writes into `em` via event handlers. |
| 3 | Decisions span `current → target`, resolved one version at a time | A practitioner can accept/decline intermediate versions one by one (or jump straight to `latest`). `from_version_id` is always the current version, so each decision is an incremental span. |
| 4 | `tenant_id` is a correlation ID | Tenant provisioning is out of scope — sourced from external auth. |
| 5 | `em_engagement.status` is coarse lifecycle only | `ACTIVE`, `ARCHIVED`, `DELETED`. Workflow state lives in the blob and requires rehydration. |
| 6 | No audit log table | `em_update_decision` is append-only and already captures who decided, when, what version, what summary was shown, and an optional reason. |
| 7 | Version references use `version_id UUID` | Version strings (e.g. `"1.2.0"`) are display values only — all cross-table references use the stable UUID PK. |
| 8 | `ds_diff_summary` keyed by `(template_id, from_version_id, to_version_id)` | Generated once, shared across all engagements on the same version gap. |
| 9 | Events consumed **synchronously in-transaction** | In-process `@EventListener` — producer work and projection commit/roll back together, nothing is dropped. Async consumption via a message broker with durable delivery and retries is deferred until those guarantees are required. |

---

## Assumptions & Constraints

Full details in [design-document.md](./design-document.md#assumptions--constraints). Summary:

**Hard constraints (from the brief):**
1. Opening an engagement takes ~1 minute (rehydration) — dashboard must never trigger it.
2. Stored engagements are not directly queryable; only `template_id` + version are stored.
3. Templates are zip archives of structured JSON.
4. Template DB is shared across firms and holds no engagement info.
5. JSON diff is available but users are non-technical — need human-readable output.
6. Only integration surface is adding hooks/events to template publishing & EMS.
7. Applying template content is out of scope.

**Assumptions (stated where the brief was silent):**
1. Java 21 + Spring Boot 3, PostgreSQL, single ECS service + RDS on AWS.
2. Scale is small (~100s engagements/firm, ~1 update/week/product) — no distributed pipeline.
3. In-process synchronous events; broker deferred but event contract is broker-ready.
4. `tenant_id` is a correlation ID from external auth; tenant provisioning out of scope.
5. Version identity = UUID; version strings are display-only.
6. `em` sync-calls `tm` for the version chain to validate decision targets.
7. Decisions span `current → target`; practitioners resolve accumulated updates one by one.
8. Diff narratives are shared across tenants (keyed by version gap).

---

## Data Model Summary

See [erd.md](./erd.md) for full field definitions.

| Table | Schema | Notes |
| :--- | :--- | :--- |
| `tm_product_template` | `tm` | One row per template product |
| `tm_product_template_version` | `tm` | Immutable — append only. `location_key` points to zip in storage. |
| `em_client` | `em` | Tenant-scoped. `tenant_id` is a correlation ID. |
| `em_engagement` | `em` | Metadata + `location_key` to blob. `current_version_id` updated on decision applied. |
| `em_engagement_read_model` | `em` | Projection for dashboard queries. Never source of truth. |
| `em_update_decision` | `em` | Append-only decision trail. Includes `reason` (nullable). |
| `ds_diff_summary` | `ds` | Cached per version gap. `created_by` = system identity. |

---

## Communication Patterns Summary

See [communication-patterns.md](./communication-patterns.md) for full details.

| Type | Used for |
| :--- | :--- |
| Event, sync in-tx | `TemplatePublished`, `EngagementCreated`, `UpdateDecisionRecorded` |
| Sync call | Dashboard → `em` (read model queries, record decision), `em` → `tm` (version chain lookup), `em` → `ds` (diff summary) |

---

## Open Items

- [ ] Decide on the technology stack (language, framework, DB engine).
- [ ] Define the API contract for the Dashboard (REST vs GraphQL).
- [ ] Define how `tenant_id` is passed and validated (JWT claim? header?).
- [ ] Decide whether `em_engagement_read_model` is a DB table or a materialized view.
- [x] Handle the version chain gap question in the Dashboard UI — resolved: show intermediate versions; practitioners resolve one by one, `from_version_id` = current version.

---

## Reference Documents

| File | Purpose |
| :--- | :--- |
| [context-map.md](./context-map.md) | Bounded context relationships and DDD patterns |
| [erd.md](./erd.md) | Full data model per schema |
| [business-flows.md](./business-flows.md) | End-to-end use case walkthroughs |
| [communication-patterns.md](./communication-patterns.md) | Inter-context communication rules and event payloads |
| [glossary.md](./glossary.md) | Ubiquitous language definitions |
| [domain-examples.md](./domain-examples.md) | Concrete JSON examples for templates and engagements |
| [ai-session-history.md](./ai-session-history.md) | AI-assisted session log |
| [implementation-spec.md](./implementation-spec.md) | UC-7 vertical slice status, correctness rules, open items |
