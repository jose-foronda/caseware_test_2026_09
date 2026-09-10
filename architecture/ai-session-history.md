# AI Tooling Session History

**Tool used:** Amazon Q Developer (IDE plugin — VS Code)
**Session purpose:** Domain understanding and architecture documentation for the Engagement Template Update Management System take-home challenge.

---

## Session Summary

The PDF challenge document was analyzed with AI assistance prior to this session to produce an initial `glossary.md`. This session continued from that point, focusing on deepening domain understanding and producing supporting documentation.

---

## Exchanges

### 1. Clarifying the EMS / Rehydration Constraint

**Question asked:**
> "I do not understand this part in the document: 'Engagement files currently store the template ID and version used for their creation. However, the stored form of an engagement is not a directly queryable record of its current state — opening one rehydrates it into memory in the pods serving that session, where volatile financial fields are established. Reading the template information therefore requires loading the engagement, which is a slow operation (~1 minute per file; this should be treated as a hard constraint). The same system which loads engagements is also responsible for handling engagement creation, and for processing the user's accept/decline decision'"

**Key clarifications obtained:**
- Engagement files are compressed/serialized blobs on disk, not queryable rows — you cannot ask "what template version is this on?" without fully loading the file
- Rehydration (~1 minute per file) is required even to read simple metadata like `template_version`
- The Engagement Management System (EMS) has three responsibilities: engagement creation, rehydration/opening, and recording accept/decline decisions
- Because EMS is slow and stateful, dashboards cannot query it directly — the Engagement Read Model exists specifically to bypass this constraint
- The Engagement Read Model is kept in sync asynchronously via EMS lifecycle events

**Artifact produced:**
- Updated `glossary.md` entries for `Customer-Specific Engagement DB`, `Engagement Rehydration`, `Engagement Management System (EMS)`, and `Engagement Read Model` with plain-language explanations

---

### 2. Understanding the File Formats

**Question asked:**
> "Those templates and engagement files are PDF or what are they?"

**Key clarifications obtained:**
- Product Templates are zip archives of structured JSON files — not PDFs
- Engagement Files are also stored as compressed/serialized binary blobs — not human-readable on disk
- PDFs are not part of the working system; they would only appear if a firm exports a final report for a client
- The JSON inside templates defines schemas, workflows, checklists, financial layouts, and regulatory rules

---

### 3. Concrete JSON Examples for Product Templates

**Question asked:**
> "Give me an example with JSON for how it can have all these: schemas, workflow definitions, checklists, financial statement layouts, regulatory rules"

**Key clarifications obtained:**
- A template zip contains multiple JSON files, each responsible for a different concern
- `manifest.json` — identity and versioning metadata
- `workflows/audit_workflow.json` — ordered phases and steps (planning, fieldwork, completion)
- `checklists/risk_assessment.json` — questions practitioners must answer, with response types and regulatory guidance references
- `financial_statements/balance_sheet_layout.json` — row definitions, groupings, and calculation rules (no numbers at template level)
- `regulatory_rules/ifrs_rules.json` — conditional compliance validation rules (e.g., IFRS 16, IFRS 9)

---

### 4. Concrete JSON Examples for Engagement Files

**Question asked:**
> "Then what would be an Engagement File? What would they do on product templates? Give me an example."

**Key clarifications obtained:**
- An Engagement File starts as a copy of the template and is progressively filled in with real client data
- The structure mirrors the template exactly, with practitioner responses and client numbers added alongside the original schema fields
- Engagement Files contain an additional `audit_trail/decisions.json` file with no equivalent in the template — recording which template update decisions were made, by whom, and when
- The template update problem becomes concrete: if `v1.2.0` adds a new checklist item `RA-03`, every engagement on `v1.1.0` needs to be detected, notified, and given the option to accept or decline — across potentially hundreds of files per firm

---

### 5. Capturing Examples as a Project Artifact

**Decision made:**
The JSON examples and explanations were captured into a permanent project file for ease of domain understanding by anyone reading the architecture documentation.

**Artifact produced:**
- Created `architecture/domain-examples.md` with three sections:
  1. Product Template structure and JSON examples
  2. Engagement File structure and JSON examples
  3. The Template Update Problem made concrete — including a diff example and explanation of why the Engagement Read Model is necessary

---

## Artifacts Produced

| File | Description |
| :--- | :--- |
| `architecture/glossary.md` | Updated with plain-language clarifications for EMS, Rehydration, Engagement Read Model, and Customer-Specific Engagement DB |
| `architecture/domain-examples.md` | New file — concrete JSON examples illustrating Product Templates, Engagement Files, and the template update problem |
| `architecture/ai-session-history.md` | This file |

---

### Session 2 — Architecture Documentation

**Artifacts produced:** `context-map.md`, `business-flows.md`, `erd.md`, `communication-patterns.md`, `spec.md`

**Key decisions made:**
- Deployment model: modular monolith, single DB, schema-separated by bounded context
- `um` schema removed — Update Tracking has no tables; writes into `em` via event handlers
- `em` owns read model and update decisions (per PDF: EMS handles creation, loading, and decisions)
- `tenant_id` is a correlation ID from external auth — not owned by any schema
- Cumulative diff only (`current → latest`) — one decision per accumulated version span
- No audit log table — `em_update_decision` is append-only and serves as the decision trail
- Version references use `version_id UUID` throughout — version strings are display values only
- `em_engagement.status` is coarse lifecycle only: `ACTIVE`, `ARCHIVED`, `DELETED`
