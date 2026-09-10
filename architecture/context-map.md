# Context Map

Bounded context relationships for the **Engagement Template Update Management System**.
For terminology see [glossary.md](./glossary.md).
For end-to-end use case walkthroughs see [business-flows.md](./business-flows.md).
For the data model see [erd.md](./erd.md).
For inter-context communication see [communication-patterns.md](./communication-patterns.md).

---

## DDD Relationship Legend

| Label | Meaning |
| :--- | :--- |
| `U` | Upstream — defines the contract, publishes events |
| `D` | Downstream — consumes, adapts to upstream |
| `OHS` | Open Host Service — upstream exposes a stable published API/event contract |
| `CF` | Conformist — downstream accepts upstream model as-is |
| `ACL` | Anti-Corruption Layer — downstream translates upstream model into its own |
| `PL` | Published Language — shared event/message schema both sides agree on |

---

## Diagram

```mermaid
graph TD

    %% ─── External Actors ────────────────────────────────────────────────
    ContentTeam(["👤 Content Team\n(Caseware Internal)"])
    Practitioner(["👤 Practitioner\n(Accounting Firm User)"])

    %% ─── Bounded Contexts ───────────────────────────────────────────────

    subgraph TM["Template Management Context [U]"]
        TPS["Template Publishing Service"]
        TemplateDB[("tm schema\n(template + version index)")]
        BlobStore[/"Template Blob Store\n(immutable, versioned — impl. agnostic)"/]
    end

    subgraph EM["Engagement Management Context [U / CORE DOMAIN]"]
        EMS["Engagement Management System (EMS)\n(create / rehydrate / record decision / read model)"]
        EngagementDB[("em schema\n(blobs + read model + decisions)")]
    end

    subgraph DS["Diff & Summary Context [U]"]
        DiffEngine["Diff & Summary Engine\n(JSON diff + LLM narrative)"]
        SummaryStore[("ds schema\n(ds_diff_summary)")]
    end

    subgraph Dashboard["Practitioner Dashboard Context [D]"]
        DashboardAPI["Dashboard API"]
        DashboardUI["Dashboard UI"]
    end

    %% ─── Relationships ───────────────────────────────────────────────────

    %% Content team publishes templates
    ContentTeam -->|"publishes new version"| TPS
    TPS -->|"writes zip"| BlobStore
    TPS -->|"writes metadata"| TemplateDB
    TPS -->|"TemplatePublished event\n[OHS / PL]"| EMS

    %% EMS owns engagement lifecycle and read model
    EMS <-->|"reads/writes blobs + metadata"| EngagementDB
    EMS -->|"queries version chain"| TemplateDB

    %% EMS requests diff summaries
    EMS -->|"getSummary(templateId, fromVersionId, toVersionId)\n[sync call — ACL translates to DS model]"| DiffEngine

    %% Diff & Summary is upstream to EMS
    DiffEngine -->|"getVersionChain(templateId, fromVersion)\n[sync call — to resolve location_keys]"| TPS
    DiffEngine -->|"reads zips via resolved location_keys"| BlobStore
    DiffEngine <-->|"reads/writes summaries"| SummaryStore

    %% Practitioner interacts with Dashboard
    Practitioner -->|"views update status\nmakes accept/decline decision"| DashboardUI
    DashboardUI -->|"queries"| DashboardAPI
    DashboardAPI -->|"reads read model\n[CF — conforms to read model schema]"| EMS
    DashboardAPI -->|"records decision\n[ACL — translates to EMS contract]"| EMS

    %% ─── Styles ──────────────────────────────────────────────────────────
    classDef core        fill:#4a90d9,stroke:#2c5f8a,color:#fff
    classDef upstream    fill:#6db56d,stroke:#3d7a3d,color:#fff
    classDef downstream  fill:#e8a838,stroke:#a06a10,color:#fff
    classDef store       fill:#f0f0f0,stroke:#999,color:#333
    classDef actor       fill:#fff,stroke:#333,color:#333

    class EMS,EngagementDB core
    class TPS,DiffEngine upstream
    class DashboardAPI,DashboardUI downstream
    class TemplateDB,TemplateS3,SummaryStore store
    class ContentTeam,Practitioner actor
```

---

## Key Design Decisions Visible in This Map

**EMS is the core domain.**
It owns engagement creation, rehydration, decision recording, the read model, and update decisions — all within the `em` schema. The separate Update Management context was merged into EMS as it owns all three responsibilities per the system spec.

**The Dashboard calls EMS directly for both reads and writes.**
For reads it calls `EngagementQueryService` which reads from `em_engagement_read_model` — no rehydration involved. For writes it calls `EngagementService.recordDecision` via an ACL. EMS is never queried via rehydration from the Dashboard path.

**Template Management uses S3 + the `tm` schema.**
S3 holds the immutable zip archives. The `tm` schema holds the queryable version index with `location_key` pointers. The Diff & Summary Engine reads zips directly from S3.

**Diff & Summary is upstream to EMS.**
Summaries are generated once per `(template_id, from_version_id, to_version_id)` UUID tuple and stored in `ds_diff_summary`. EMS calls `DiffSummaryService.getSummary()` synchronously via an ACL, translating its internal model into the Diff & Summary contract. No events are involved in this flow.

**The Dashboard is a pure downstream conformist for reads.**
It conforms to the read model schema for queries — no translation needed. For writes (decisions) it uses an ACL to translate into EMS's contract.
