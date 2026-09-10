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
| `em` | Engagement Management | Clients and engagement blobs |
| `um` | Update Management | Read model, decisions, audit log |
| `ds` | Diff & Summary | Diff summaries and cache |

> `tenant_id` is a correlation ID sourced from an external identity/auth system — not owned by any schema.

---

## Diagram

```mermaid
erDiagram

    %% ─── tm (Template Management) ──────────────────────────────────────

    tm_product_template {
        UUID   template_id PK
        UUID   product_id
        string name
    }

    tm_product_template_version {
        UUID     version_id      PK
        UUID     template_id     FK
        string   version
        string   previous_version
        string   location_key
        datetime published_at
    }

    %% ─── em (Engagement Management) ────────────────────────────────────

    em_client {
        UUID   client_id  PK
        UUID   tenant_id
        string name
    }

    em_engagement {
        UUID     engagement_id           PK
        UUID     client_id               FK
        UUID     tenant_id
        UUID     template_id
        string   initial_template_version
        string   location_key
        string   status
        datetime created_at
    }

    %% ─── um (Update Management) ─────────────────────────────────────────

    um_engagement_read_model {
        UUID     engagement_id            PK
        UUID     tenant_id
        UUID     template_id
        string   current_template_version
        string   latest_template_version
        string   update_status
        datetime last_evaluated_at
    }

    um_update_decision {
        UUID     decision_id  PK
        UUID     engagement_id
        UUID     template_id
        string   from_version
        string   target_version
        string   decision
        UUID     summary_id
        string   decided_by
        datetime decided_at
    }

    um_audit_log {
        UUID     log_id       PK
        UUID     decision_id  FK
        UUID     engagement_id
        UUID     tenant_id
        string   user_id
        string   action
        string   detail
        datetime timestamp
    }

    %% ─── ds (Diff & Summary) ────────────────────────────────────────────

    ds_diff_summary {
        UUID     summary_id  PK
        UUID     template_id
        string   from_version
        string   to_version
        string   narrative
        datetime generated_at
    }

    %% ─── Relationships (within schema only) ────────────────────────────

    tm_product_template      ||--o{ tm_product_template_version : "has versions"

    em_client                ||--o{ em_engagement               : "has engagements"

    um_update_decision       ||--|| um_audit_log                : "recorded in"
```

---

## Notes

- FK constraints are only enforced **within** the same schema. Cross-schema `template_id`, `engagement_id`, and `tenant_id` fields are correlation IDs kept consistent via domain events, not DB constraints.
- `tenant_id` is sourced from an external identity/auth system — it appears as a correlation ID in `em`, `um`, and `um_audit_log` but is never owned by this system.
- `em_engagement` stores metadata columns plus a `location_key` pointing to the serialized blob in storage. All other engagement state beyond these metadata fields requires rehydration.
- `um_engagement_read_model` is a projection rebuilt from events (`EngagementCreated`, `EngagementOpened`, `UpdateDecisionRecorded`, `TemplatePublished`). It is never the source of truth.
- `ds_diff_summary` is keyed by `(template_id, from_version, to_version)` — generated once, shared across all engagements on the same version gap.
- `um_audit_log` is append-only — no updates or deletes.
