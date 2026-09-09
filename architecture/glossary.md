# Ubiquitous Language & Domain Glossary

This glossary defines the ubiquitous language for the **Engagement Template Update Management System**, establishing consistent terminology across product management, engineering, audit practitioners, and AI tooling.

---

## 1. Core Domain Concepts

| Term | Definition | Context / Notes |
| :--- | :--- | :--- |
| **Product Template** | A standardized, market-specific foundational definition for an audit, review, tax, or compliance engagement. Stored as a zip archive of structured JSON schemas and files defining workflows, checklists, financial statement layouts, and guidance. Maintained by Caseware content teams. | Shared globally across all accounting firms. Identified by `template_id`. |
| **Product Template Version** | An immutable, versioned release of a Product Template (e.g., `v1.2.0`). Contains structural schemas, regulatory rules, and standard workpapers. | Stored in the centralized Product Template Database. Updated approximately weekly per product. |
| **Engagement File** | The digital workspace and record of all audit and assurance work performed by an accounting firm for a specific end-client over a period (typically one fiscal year). Created from a baseline Product Template Version. | Owned by a specific customer firm. Stored in firm-isolated databases. |
| **Accounting Firm (Tenant)** | The organization/firm using Caseware software to deliver audit and assurance services to their clients. | Multi-tenant boundary. Each firm manages ~100s of active engagement files across multiple products. |
| **Client (End-Client)** | The company, entity, or individual being audited or serviced by the Accounting Firm. An Engagement File is tied to one client for a designated fiscal year/period. | Distinguish clearly from "Customer" (the accounting firm). |
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
| **Update Status** | The state of template alignment for an engagement: `UP_TO_DATE`, `UPDATE_AVAILABLE` (Pending), `UPDATE_APPLIED`, `UPDATE_DECLINED`, or `UPDATE_DEFERRED`. | Maintained in the optimized read model. |

---

## 3. Architecture & Technical Components

| Term | Definition | Context / Notes |
| :--- | :--- | :--- |
| **Product Template DB** | The shared, global database containing all versions of all product templates. Read-heavy during engagement creation; written to during template publishing (~1/week/product). | Does **not** store any engagement data or firm data. |
| **Customer-Specific Engagement DB** | Isolated tenant database where engagement raw states are stored. | Direct queries against engagement current runtime state are not possible due to hydration constraints. |
| **Engagement Rehydration** | The runtime process of decompressing and hydrating an engagement file into memory within pod sessions, establishing volatile financial fields and active state. | **Hard Constraint:** Takes ~1 minute per file. Querying the live engagement state cannot be done synchronously on user dashboard loads. |
| **Engagement Read Model (Projection)** | A lightweight, queryable projection storing metadata for every engagement (e.g., `firm_id`, `engagement_id`, `template_id`, `current_template_version`, `status`, `last_evaluated_version`). | Updated asynchronously via event hooks to bypass the 1-minute rehydration bottleneck. |
| **Engagement Management System (EMS)** | The existing core platform service that handles engagement lifecycle (creation, rehydration/opening, and recording accept/decline choices). | Emits lifecycle events (`EngagementCreated`, `UpdateDecisionRecorded`). |
| **Template Publishing Service** | The content service responsible for packaging, verifying, and publishing new Product Template versions. | Emits `TemplatePublished` events. |
| **Diff & Summary Engine (AI Augmented)** | The component that takes two template versions, extracts the structural JSON diff, and transforms it into a practitioner-oriented executive summary using LLMs with deterministic guardrails. | Caches summaries by `(template_id, from_version, to_version)` so an identical summary is generated once and shared across thousands of engagements. |
| **Audit Log / Traceability Record** | Immutable record documenting which practitioner made the update decision, timestamp, rationale, engagement version jump, and the summary shown at decision time. | Critical for professional defensibility during external peer reviews and regulatory audits. |

---

## 4. Key Domain Events

| Event Name | Producer | Payload / Description |
| :--- | :--- | :--- |
| `TemplatePublished` | Template Storage System | `template_id`, `version`, `previous_version`, `timestamp`, `checksum`, `storage_uri` |
| `EngagementCreated` | Engagement Management System | `firm_id`, `engagement_id`, `template_id`, `initial_template_version`, `timestamp` |
| `EngagementOpened` | Engagement Management System | `firm_id`, `engagement_id`, `rehydrated_template_version` *(reconciliation hook)* |
| `UpdateDecisionRecorded` | Engagement Management System | `firm_id`, `engagement_id`, `decision` (`APPLIED` \| `DECLINED`), `target_version`, `user_id`, `timestamp` |
| `SummaryGenerated` | Diff & Summary Service | `template_id`, `from_version`, `to_version`, `summary_id`, `hash` |
