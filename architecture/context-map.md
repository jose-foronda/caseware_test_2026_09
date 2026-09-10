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

    %% ─── External Actors ───────────────────────────────────────────────
    ContentTeam(["👤 Content Team\n(Caseware Internal)"])
    Practitioner(["👤 Practitioner\n(Accounting Firm User)"])

    %% ─── Bounded Contexts ──────────────────────────────────────────────

    subgraph TM["Template Management Context [U]"]
        TPS["Template Publishing Service"]
        TemplateDB[("Product Template DB\n(metadata + version index)")]
        TemplateS3[("Template Zip Store\n(S3 — immutable, versioned)")]
    end

    subgraph EM["Engagement Management Context [U]"]
        EMS["Engagement Management System (EMS)\n(create / rehydrate / record decision)"]
        EngagementDB[("Customer Engagement DB\n(serialized blobs, per tenant)")]
    end

    subgraph UM["Update Management Context [CORE DOMAIN]"]
        UpdateSvc["Update Tracking Service"]
        ReadModel[("Engagement Read Model\n(queryable projection)")]
        AuditLog[("Audit Log\n(immutable decision trail)")]
    end

    subgraph DS["Diff & Summary Context [U]"]
        DiffEngine["Diff & Summary Engine\n(JSON diff + LLM narrative)"]
        SummaryCache[("Summary Cache\n(keyed by template_id+from+to)")]
    end

    subgraph Dashboard["Practitioner Dashboard Context [D]"]
        DashboardAPI["Dashboard API"]
        DashboardUI["Dashboard UI"]
    end

    %% ─── Relationships ──────────────────────────────────────────────────

    %% Content team publishes templates
    ContentTeam -->|"publishes new version"| TPS
    TPS -->|"writes zip"| TemplateS3
    TPS -->|"writes metadata"| TemplateDB
    TPS -->|"TemplatePublished event\n[OHS / PL]"| UpdateSvc

    %% EMS is upstream to Update Management
    EMS -->|"EngagementCreated event\n[OHS / PL]"| UpdateSvc
    EMS -->|"EngagementOpened event\n[OHS / PL — reconciliation hook]"| UpdateSvc
    EMS -->|"UpdateDecisionRecorded event\n[OHS / PL]"| UpdateSvc
    EMS <-->|"reads/writes blobs"| EngagementDB

    %% Update Management consumes events and maintains read model
    UpdateSvc -->|"projects metadata"| ReadModel
    UpdateSvc -->|"queries version index"| TemplateDB
    UpdateSvc -->|"writes decision record"| AuditLog
    UpdateSvc -->|"requests diff summary\n[ACL — translates to DS model]"| DiffEngine

    %% Diff & Summary is upstream to Update Management
    DiffEngine -->|"reads zip archives"| TemplateS3
    DiffEngine -->|"SummaryGenerated event\n[OHS / PL]"| UpdateSvc
    DiffEngine <-->|"reads/writes cache"| SummaryCache

    %% Practitioner interacts with Dashboard
    Practitioner -->|"views update status\nmakes accept/decline decision"| DashboardUI
    DashboardUI -->|"queries"| DashboardAPI
    DashboardAPI -->|"reads\n[CF — conforms to read model schema]"| ReadModel
    DashboardAPI -->|"forwards decision\n[ACL — translates to EMS contract]"| EMS

    %% ─── Styles ─────────────────────────────────────────────────────────
    classDef core        fill:#4a90d9,stroke:#2c5f8a,color:#fff
    classDef upstream    fill:#6db56d,stroke:#3d7a3d,color:#fff
    classDef downstream  fill:#e8a838,stroke:#a06a10,color:#fff
    classDef store       fill:#f0f0f0,stroke:#999,color:#333
    classDef actor       fill:#fff,stroke:#333,color:#333

    class UpdateSvc,ReadModel,AuditLog core
    class TPS,EMS,DiffEngine upstream
    class DashboardAPI,DashboardUI downstream
    class TemplateDB,TemplateS3,EngagementDB,SummaryCache store
    class ContentTeam,Practitioner actor
```

---

## Key Design Decisions Visible in This Map

**Update Management is the core domain.**
It owns no raw data of its own — it projects a read model from upstream events. This keeps it decoupled from both EMS and Template Management.

**EMS is upstream but not queryable.**
The Dashboard never calls EMS directly. It reads from the Engagement Read Model (maintained by Update Management via EMS events) and forwards decisions back to EMS through an ACL. This is the architectural response to the 1-minute rehydration constraint.

**Template Management uses S3 + a metadata DB.**
S3 holds the immutable zip archives (cheap, durable, versioned by key). The Product Template DB holds the queryable index of versions, checksums, and `storage_uri` pointers. The Diff & Summary Engine reads zips directly from S3.

**Diff & Summary is upstream to Update Management.**
Summaries are generated once per `(template_id, from_version, to_version)` tuple and cached. Update Management requests them via an ACL, translating its internal model into the Diff & Summary contract — so neither context leaks into the other.

**The Dashboard is a pure downstream conformist for reads.**
It conforms to the Read Model schema for queries (no translation needed — the Read Model is designed for this consumer). For writes (decisions), it uses an ACL to translate into EMS's contract.
