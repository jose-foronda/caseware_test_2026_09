# Domain Examples

Concrete examples to illustrate how Product Templates and Engagement Files relate to each other.
For terminology definitions see [glossary.md](./glossary.md).

---

## 1. Product Template — What It Looks Like

A Product Template is a **zip archive of structured JSON files** — a blank, reusable blueprint for a type of audit. It contains no client data whatsoever.

```
template_audit-ifrs-standard_v1.2.0.zip
├── manifest.json
├── workflows/
│   └── audit_workflow.json
├── checklists/
│   └── risk_assessment.json
├── financial_statements/
│   └── balance_sheet_layout.json
└── regulatory_rules/
    └── ifrs_rules.json
```

**manifest.json** — identity and versioning
```json
{
  "template_id": "audit-ifrs-standard",
  "version": "1.2.0",
  "previous_version": "1.1.0",
  "market": "international",
  "standard": "IFRS"
}
```

**workflows/audit_workflow.json** — ordered phases and steps
```json
{
  "phases": [
    { "id": "planning",   "steps": ["risk_assessment", "materiality_calculation"] },
    { "id": "fieldwork",  "steps": ["substantive_testing", "controls_testing"] },
    { "id": "completion", "steps": ["final_review", "sign_off"] }
  ]
}
```

**checklists/risk_assessment.json** — questions practitioners must answer (no answers yet)
```json
{
  "checklist_id": "risk_assessment",
  "items": [
    {
      "id": "RA-01",
      "question": "Has management identified significant risks of material misstatement?",
      "response_type": "yes_no_na",
      "required": true,
      "guidance": "Refer to ISA 315 paragraph 28"
    },
    {
      "id": "RA-02",
      "question": "Document the entity's risk assessment process",
      "response_type": "text",
      "required": true
    }
  ]
}
```

**financial_statements/balance_sheet_layout.json** — row definitions and calculation rules (no numbers yet)
```json
{
  "statement": "balance_sheet",
  "sections": [
    {
      "label": "Current Assets",
      "rows": [
        { "id": "cash",               "label": "Cash and cash equivalents", "type": "input" },
        { "id": "accounts_receivable","label": "Accounts receivable",       "type": "input" },
        { "id": "total_current_assets","label": "Total Current Assets",     "type": "sum", "refs": ["cash", "accounts_receivable"] }
      ]
    }
  ]
}
```

**regulatory_rules/ifrs_rules.json** — compliance validation rules
```json
{
  "standard": "IFRS",
  "rules": [
    {
      "id": "IFRS-16-01",
      "description": "Lease liabilities must be disclosed separately",
      "condition": "lease_liabilities > 0",
      "action": "require_disclosure",
      "disclosure_ref": "note_leases"
    },
    {
      "id": "IFRS-09-01",
      "description": "Financial instruments must be classified at fair value",
      "condition": "has_financial_instruments == true",
      "action": "require_checklist",
      "checklist_ref": "financial_instruments_classification"
    }
  ]
}
```

---

## 2. Engagement File — What It Looks Like

An Engagement File is created when a firm starts an audit for a specific client. It starts as a **copy of the template** and is progressively filled in with real client data by practitioners.

> Analogy: the Product Template is a blank tax form. The Engagement File is that same form filled out for a specific client.

```
engagement_acme_corp_2024.zip
├── manifest.json
├── workflows/
│   └── audit_workflow.json         ← from template, tracks completion progress
├── checklists/
│   └── risk_assessment.json        ← from template + practitioner answers
├── financial_statements/
│   └── balance_sheet_layout.json   ← from template + real client numbers
├── regulatory_rules/
│   └── ifrs_rules.json             ← from template, evaluated against client data
└── audit_trail/
    └── decisions.json              ← engagement-specific, does not exist in template
```

**manifest.json** — now references both the template and the client
```json
{
  "engagement_id": "eng-00123",
  "firm_id": "firm-deloitte-ca",
  "client": "Acme Corp",
  "fiscal_year": "2024",
  "template_id": "audit-ifrs-standard",
  "template_version": "1.1.0",
  "status": "in_progress"
}
```

**checklists/risk_assessment.json** — same structure as template, now with practitioner responses
```json
{
  "checklist_id": "risk_assessment",
  "items": [
    {
      "id": "RA-01",
      "question": "Has management identified significant risks of material misstatement?",
      "response_type": "yes_no_na",
      "required": true,
      "guidance": "Refer to ISA 315 paragraph 28",
      "response": "yes",
      "answered_by": "j.smith@deloitte.ca",
      "answered_at": "2024-11-03T14:22:00Z",
      "notes": "Management provided risk register dated Oct 2024"
    },
    {
      "id": "RA-02",
      "question": "Document the entity's risk assessment process",
      "response_type": "text",
      "required": true,
      "response": "Acme uses a quarterly risk committee process..."
    }
  ]
}
```

**financial_statements/balance_sheet_layout.json** — same layout, now with real client numbers
```json
{
  "statement": "balance_sheet",
  "sections": [
    {
      "label": "Current Assets",
      "rows": [
        { "id": "cash",                "label": "Cash and cash equivalents", "type": "input", "value": 1250000 },
        { "id": "accounts_receivable", "label": "Accounts receivable",       "type": "input", "value": 870000  },
        { "id": "total_current_assets","label": "Total Current Assets",      "type": "sum",   "value": 2120000 }
      ]
    }
  ]
}
```

**audit_trail/decisions.json** — engagement-specific record of template update decisions
```json
{
  "template_updates": [
    {
      "from_version": "1.0.0",
      "to_version": "1.1.0",
      "decision": "APPLIED",
      "decided_by": "p.jones@deloitte.ca",
      "decided_at": "2024-09-15T09:00:00Z",
      "summary_shown": "summary-id-abc123"
    }
  ]
}
```

---

## 3. The Template Update Problem — Made Concrete

Acme Corp's engagement (`eng-00123`) is on template `v1.1.0`. The content team publishes `v1.2.0`, which adds a new checklist item `RA-03` due to a new IFRS regulation.

**What changed in the template (the diff):**
```json
// checklists/risk_assessment.json — added in v1.2.0
{
  "id": "RA-03",
  "question": "Has the impact of IFRS 18 presentation changes been assessed?",
  "response_type": "yes_no_na",
  "required": true,
  "guidance": "Refer to IFRS 18 effective January 2027"
}
```

**What the system must do:**
1. Detect that `eng-00123` is behind (`v1.1.0` < `v1.2.0`) — for potentially hundreds of engagements across the firm
2. Generate a human-readable summary of the change for the practitioner
3. Let the practitioner **accept** (new `RA-03` item is merged into their engagement) or **decline** (they stay on `v1.1.0` with a documented reason)

**Why this is hard:**
The engagement file is a compressed binary blob on disk — not a queryable record. To read even the `template_version` field from `manifest.json`, the system would have to fully rehydrate the engagement (~1 minute). With hundreds of engagements per firm, doing this on every dashboard load is completely impractical.

That is the core reason the **Engagement Read Model** exists — a separate lightweight store kept in sync via events, so the dashboard can answer "which engagements are behind?" instantly, without ever touching the engagement files directly.
