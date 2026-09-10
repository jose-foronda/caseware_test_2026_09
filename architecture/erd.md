# Entity Relationship Diagram

Data model for the **Engagement Template Update Management System**.
Deployment assumption: **modular monolith** — single database, schema-separated by bounded context.
Cross-context references are correlation IDs only (no enforced FK constraints across schemas).

For terminology see [glossary.md](./glossary.md).
For business flows see [business-flows.md](./business-flows.md).
For the context map see [context-map.md](./context-map.md).

---

## Schema Ownership

| Schema | Bounded Context | Owns |
| :--- | :--- | :--- |
| `tm` | Template Management | Templates and their versions |
| `em` | Engagement Management | Clients, engagement blobs, read model, decisions |
| `ds` | Diff & Summary | Diff summaries |

> `tenant_id` is a correlation ID sourced from an external identity/auth system — not owned by any schema.

---

## Diagram

```mermaid
erDiagram

    %% ─── tm (Template Management) ──────────────────────────────────────

    tm_product_template {
        UUID     template_id PK
        UUID     product_id
        string   name
        string   created_by
        datetime created_at
        string   updated_by
        datetime updated_at
    }

    tm_product_template_version {
        UUID     version_id          PK
        UUID     template_id         FK
        string   version
        UUID     previous_version_id
        string   location_key
        string   created_by
        datetime created_at
    }

    %% ─── em (Engagement Management) ────────────────────────────────────

    em_client {
        UUID     client_id  PK
        UUID     tenant_id
        string   name
        string   created_by
        datetime created_at
        string   updated_by
        datetime updated_at
    }

    em_engagement {
        UUID     engagement_id      PK
        UUID     client_id          FK
        UUID     tenant_id
        UUID     template_id
        UUID     current_version_id
        string   location_key
        string   status
        string   created_by
        datetime created_at
        string   updated_by
        datetime updated_at
    }

    em_engagement_read_model {
        UUID     engagement_id           PK
        UUID     tenant_id
        UUID     template_id
        UUID     current_version_id
        UUID     latest_version_id
        UUID     last_decided_version_id
        string   update_status
    }

    em_update_decision {
        UUID     decision_id       PK
        UUID     engagement_id
        UUID     template_id
        UUID     from_version_id
        UUID     target_version_id
        string   decision
        UUID     summary_id
        string   decided_by
        string   reason
        datetime decided_at
    }

    %% ─── ds (Diff & Summary) ────────────────────────────────────────────

    ds_diff_summary {
        UUID     summary_id      PK
        UUID     template_id
        UUID     from_version_id
        UUID     to_version_id
        string   narrative
        string   created_by
        datetime created_at
    }

    %% ─── Relationships (within schema only) ────────────────────────────

    tm_product_template      ||--o{ tm_product_template_version : "has versions"

    em_client                ||--o{ em_engagement               : "has engagements"
    em_engagement            ||--o{ em_update_decision           : "has decisions"

```

---

## Notes

- FK constraints are only enforced **within** the same schema. Cross-schema `template_id`, `version_id`, and `tenant_id` fields are correlation IDs kept consistent via domain events, not DB constraints.
- `tenant_id` is sourced from an external identity/auth system — it appears as a correlation ID in `em` but is never owned by this system.
- `em_engagement.status` represents coarse lifecycle state only: `ACTIVE`, `ARCHIVED`, `DELETED`. Workflow state lives inside the blob and requires rehydration.
- `em_engagement_read_model` is a projection rebuilt from events (`EngagementCreated`, `UpdateDecisionRecorded`, `TemplatePublished`). It is never the source of truth. `update_status` values: `PENDING_UPDATES` | `UPDATES_REVIEWED`.
- `em_update_decision` is append-only — no updates or deletes. It serves as the immutable decision trail.
