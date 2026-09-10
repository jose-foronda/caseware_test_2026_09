# Ubiquitous Language & Domain Glossary

This glossary defines the ubiquitous language for the **Engagement Template Update Management System**, establishing consistent terminology across product management, engineering, audit practitioners, and AI tooling.

---

## 1. Core Domain Concepts

| Term | Definition | Context / Notes |
| :--- | :--- | :--- |
| **Product Template** | A standardized, market-specific foundational definition for an audit, review, tax, or compliance engagement. Stored as a zip archive of structured JSON schemas and files defining workflows, checklists, financial statement layouts, and guidance. Maintained by Caseware content teams. | Shared globally across all accounting firms. Identified by `template_id`. |
| **Product Template Version** | An immutable, versioned release of a Product Template (e.g., `v1.2.0`). Contains structural schemas, regulatory rules, and standard workpapers. | Stored in the centralized Product Template Database. Updated approximately weekly per product. |
| **Engagement File** | The digital workspace and record of all audit and assurance work performed by an accounting firm for a specific end-client over a period (typically one fiscal year). Created from a baseline Product Template Version. | Owned by a specific customer firm. Stored in firm-isolated databases. |
| **Accounting Firm (Tenant)** | The organization/firm using Caseware software to deliver audit and assurance services to their clients. | Multi-tenant boundary. Each firm manages ~100s of active engagement files across multiple products. Identified by `tenant_id`, a correlation ID sourced from an external identity/auth system — not owned by this system. |
| **Client (End-Client)** | The company, entity, or individual being audited or serviced by the Accounting Firm. An Engagement File is tied to one client for a designated fiscal year/period. Represented as `em_client`, scoped to a tenant. | Distinguish clearly from "Customer" (the accounting firm). |
| **Content Team** | Caseware internal domain experts and regulatory specialists who author, update, and publish Product Templates according to changing standards (e.g., IFRS, US GAAP, ISA). | External to the tenant firm; publishers of template releases. |
| **Practitioner / Engagement Team** | Auditors, accountants, and engagement partners responsible for managing and conducting work within an Engagement File. | Non-technical end-users who make the business/regulatory decision to accept or decline updates. |

---

## 2. Update Lifecycle & State Machine Concepts

| Term | Definition | Context / Notes |
| :--- | :--- | :--- |
| **Template Update (Release)** | A new version of a Product Template published to the Template Catalog, containing bug fixes, regulatory changes, or structural workflow improvements. | Triggers evaluation workflows across all active engagements based on that template. |
| **Pending Update** | A condition where an Engagement File's current template version is older than the latest published version of its parent Product Template, awaiting a practitioner's review. | Requires immediate visibility on firm-level dashboards ("at a glance"). |
| **Accumulated Updates (Version Drift / Delta Span)** | When multiple sequential versions have been released (e.g., `v1.0 -> v1.1 -> v1.2`) before a practitioner acts on them. | The system must synthesize the composite delta between current engagement version `v_curr` and target version `v_target`. |
| **Cumulative Diff (JSON Diff)** | The machine-level structural difference between two template zip archives / JSON trees. | Deterministic and reliable, but too technical and raw for practitioners to read directly. |
| **Change Summary (Human-Readable Narrative)** | A synthesized, practitioner-friendly explanation of inbound template changes, highlighting regulatory impact, new disclosures, risk changes, or deleted sections. | AI-augmented or template-generated narrative enabling practitioners to make defensible decisions without inspecting raw JSON. |
| **Update Decision** | The explicit choice made by an authorized practitioner regarding pending template updates for an engagement: **Apply** or **Decline**. | Mandatory audit trail required for compliance defensibility. Applying template content itself is out-of-scope for this exercise. |
| **Update Status** | A derived value for an engagement: `PENDING_UPDATES` or `UPDATES_REVIEWED`. Not stored — computed at read time as `last_decided_version_id == latest_version_id` → `UPDATES_REVIEWED`, otherwise `PENDING_UPDATES`. |

---

## 3. Architecture & Technical Components

| Term | Definition | Context / Notes |
| :--- | :--- | :--- |
| **`tm` Schema (Template Management)** | The database schema owning `tm_product_template` and `tm_product_template_version`. Read-heavy during engagement creation; written to during template publishing (~1/week/product). | Does **not** store any engagement or tenant data. |
| **`em` Schema (Engagement Management)** | The database schema owning `em_client`, `em_engagement`, `em_engagement_read_model`, and `em_update_decision`. All engagement metadata, read model projections, and decisions live here. | Single schema in the modular monolith DB. Tenant isolation is enforced via `tenant_id` at the application layer, not via separate databases. |
| **Engagement Rehydration** | The runtime process of decompressing and hydrating an engagement file into memory within pod sessions, establishing volatile financial fields and active state. Even reading simple metadata (like the current template version) requires a full rehydration — there is no shortcut. | **Hard Constraint:** Takes ~1 minute per file. With hundreds of engagements per firm, this makes it completely impractical to query engagement state synchronously on dashboard loads. Any solution for fast reads must work *around* rehydration, not through it. |
| **Engagement Read Model (`em_engagement_read_model`)** | A lightweight, queryable projection storing metadata for every engagement (`engagement_id`, `tenant_id`, `template_id`, `current_version_id`, `latest_version_id`, `last_decided_version_id`, `update_status`). Exists to avoid both rehydration and cross-schema joins on dashboard loads. | Kept in sync by consuming domain events (`EngagementCreated`, `UpdateDecisionRecorded`, `TemplatePublished`). Dashboards always read from here. |
| **Engagement Management System (EMS)** | The existing core platform service that handles three responsibilities: (1) **engagement creation**, (2) **rehydration/opening** of existing engagements, and (3) **recording the practitioner's accept/decline decision** on template updates. Also owns the read model and update decisions. | Emits lifecycle events (`EngagementCreated`, `UpdateDecisionRecorded`). Dashboard reads bypass rehydration entirely by querying `em_engagement_read_model`. |
| **Template Publishing Service** | The content service responsible for packaging, verifying, and publishing new Product Template versions. | Emits `TemplatePublished` events. |
| **Diff & Summary Engine (AI Augmented)** | The component that takes two template versions, extracts the structural JSON diff, and transforms it into a practitioner-oriented executive summary using LLMs with deterministic guardrails. | Caches summaries in `ds_diff_summary` keyed by `(template_id, from_version_id, to_version_id)` — generated once and shared across all engagements on the same version gap. |

---

## 4. Key Domain Events

| Event Name | Producer | Payload / Description |
| :--- | :--- | :--- |
| `TemplatePublished` | Template Publishing Service | `templateId`, `versionId`, `previousVersionId`, `locationKey`, `timestamp` |
| `EngagementCreated` | Engagement Management System | `engagementId`, `tenantId`, `clientId`, `templateId`, `currentVersionId`, `timestamp` |
| `UpdateDecisionRecorded` | Engagement Management System | `engagementId`, `tenantId`, `decision` (`APPLIED` \| `DECLINED`), `targetVersionId`, `userId`, `timestamp` |
