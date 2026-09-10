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
| `em` | Engagement Management | Firms and engagement blobs |
| `um` | Update Management | Read model, decisions, audit log |
| `ds` | Diff & Summary | Diff summaries and cache |

---

## Diagram

```mermaid
erDiagram

    %% ─── tm (Template Management) ──────────────────────────────────────

    tm_product_template {
        string template_id PK
        string name
        string market
        string standard
    }

    tm_product_template_version {
        string version_id       PK
        string template_id      FK
        string version
        string previous_version
        string checksum
        string storage_uri
        datetime published_at
    }

    %% ─── em (Engagement Management) ────────────────────────────────────

    em_accounting_firm {
        string firm_id PK
        string name
    }

    em_engagement {
        string engagement_id            PK
        string firm_id                  FK
        string template_id
        string initial_template_version
        string status
        datetime created_at
    }

    %% ─── um (Update Management) ─────────────────────────────────────────

    um_engagement_read_model {
        string engagement_id            PK
        string firm_id
        string template_id
        string current_template_version
        string latest_template_version
        string update_status
        datetime last_evaluated_at
    }

    um_update_decision {
        string decision_id   PK
        string engagement_id
        string template_id
        string from_version
        string target_version
        string decision
        string summary_id
        string decided_by
        datetime decided_at
    }

    um_audit_log {
        string log_id        PK
        string decision_id   FK
        string engagement_id
        string firm_id
        string user_id
        string action
        string detail
        datetime timestamp
    }

    %% ─── ds (Diff & Summary) ────────────────────────────────────────────

    ds_diff_summary {
        string summary_id   PK
        string template_id
        string from_version
        string to_version
        string narrative
        string diff_hash
        datetime generated_at
    }

    %% ─── Relationships (within schema only) ────────────────────────────

    tm_product_template         ||--o{ tm_product_template_version : "has versions"

    em_accounting_firm          ||--o{ em_engagement               : "owns"

    um_update_decision          ||--|| um_audit_log                : "recorded in"
```

---

## Notes

- FK constraints are only enforced **within** the same schema. Cross-schema `template_id`, `engagement_id`, and `firm_id` fields are correlation IDs kept consistent via domain events, not DB constraints.
- `em_engagement` stores the raw serialized blob — `template_id` and `initial_template_version` are the only metadata columns; all other engagement state requires rehydration.
- `um_engagement_read_model` is a projection rebuilt from events (`EngagementCreated`, `EngagementOpened`, `UpdateDecisionRecorded`, `TemplatePublished`). It is never the source of truth.
- `ds_diff_summary` is keyed by `(template_id, from_version, to_version)` — generated once, shared across all engagements on the same version gap.
- `um_audit_log` is append-only — no updates or deletes.
