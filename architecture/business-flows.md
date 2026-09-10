# Business Flows

Data flow descriptions for all use cases represented in the [Context Map](./context-map.md).

---

## UC-1 — Content Team Publishes a New Template Version

**Actor:** Content Team (Caseware Internal)

1. Content Team uploads a new template version to the **Template Publishing Service (TPS)**.
2. TPS writes the zip archive to storage and records a new `tm_product_template_version` row with `version`, `previous_version_id`, and `location_key`.
3. TPS emits a `TemplatePublished` event (carrying `templateId`, `versionId`, `previousVersionId`, `locationKey`) consumed by EMS.
4. EMS updates `em_engagement_read_model` for all engagements on that template — setting `latest_version_id` and `update_status = UPDATE_AVAILABLE` where `current_version_id != versionId`.

---

## UC-2 — Practitioner Creates a Client

**Actor:** Practitioner (Accounting Firm User)

1. Practitioner creates a client record in EMS, providing the client name.
2. EMS inserts a new `em_client` row with a generated `client_id` and the `tenant_id` correlation ID from the auth context.

---

## UC-3 — Practitioner Creates a New Engagement

**Actor:** Practitioner (Accounting Firm User)

1. Practitioner creates an engagement for an existing client, selecting a product template.
2. EMS resolves the latest `version_id` for the selected template from `tm_product_template_version`.
3. EMS creates the engagement blob, stores it in blob storage, and inserts a new `em_engagement` row with `client_id`, `tenant_id`, `template_id`, `current_version_id`, and `location_key`.
4. EMS inserts a corresponding `em_engagement_read_model` row with `update_status = UP_TO_DATE`.
5. EMS emits an `EngagementCreated` event.

---

## UC-4 — Practitioner Opens an Existing Engagement

**Actor:** Practitioner (Accounting Firm User)

1. Practitioner opens an engagement — EMS rehydrates the blob from `em_engagement.location_key` (~1 minute).
2. `em_engagement.current_version_id` is the authoritative version — no reconciliation needed.

> The Dashboard never triggers rehydration. It always reads from `em_engagement_read_model` directly.

---

## UC-5 — Practitioner Views Update Status on the Dashboard

**Actor:** Practitioner (Accounting Firm User)

1. Practitioner opens the **Dashboard UI**.
2. Dashboard UI calls `EngagementQueryService.getEngagementsByTenant(tenantId)`, which reads from `em_engagement_read_model`.
3. Dashboard UI renders the list of engagements with their `update_status` and version gap (`current_version_id` vs `latest_version_id`).
4. Practitioner clicks on an engagement with `update_status = UPDATE_AVAILABLE` to open the **Engagement Update View**.

---

## UC-6 — Practitioner Compares Template Versions (Diff Summary)

**Actor:** Practitioner (Accounting Firm User)
**Trigger:** Practitioner is in the **Engagement Update View** and clicks "Compare" on a specific version pair.

> The Engagement Update View shows all versions between `current_version_id` and `latest_version_id` (resolved via `tm_product_template_version.previous_version_id` chain). Each version pair can be compared independently.

1. Engagement Update View calls the API with `templateId`, `fromVersionId`, `toVersionId` for the selected pair.
2. API calls `EngagementQueryService.getSummary(templateId, fromVersionId, toVersionId)` on EMS.
3. EMS calls `DiffSummaryService.getSummary(templateId, fromVersionId, toVersionId)` synchronously.
4. `DiffSummaryService` checks `ds_diff_summary` for an existing row matching `(template_id, from_version_id, to_version_id)`:
   - **Cache hit:** returns the existing narrative immediately.
   - **Cache miss:** reads both zip archives using `location_key` from `tm_product_template_version`, computes a JSON diff, generates an LLM narrative, inserts a new `ds_diff_summary` row, returns the narrative.
5. Engagement Update View displays the narrative to the practitioner.

---

## UC-7 — Practitioner Accepts or Declines a Template Update

**Actor:** Practitioner (Accounting Firm User)

1. Practitioner reviews the diff summary in the **Engagement Update View** and submits a decision (accept / decline) with an optional `reason`.
2. Dashboard UI sends the decision to the **Dashboard API**.
3. Dashboard API calls `EngagementService.recordDecision(engagementId, decision, targetVersionId, userId, reason)`.
4. EMS inserts a new `em_update_decision` row (append-only) with `from_version_id`, `target_version_id`, `decision`, `summary_id`, `decided_by`, and `reason`.
5. EMS emits an `UpdateDecisionRecorded` event.
6. EMS updates `em_engagement_read_model.update_status`:
   - `APPLIED` → `update_status = UP_TO_DATE`, `current_version_id = target_version_id`
   - `DECLINED` → `update_status = UPDATE_DECLINED`
7. If `APPLIED`, EMS also updates `em_engagement.current_version_id = target_version_id`.

---

## Data Flow Summary

```
Content Team ──► TPS ──► tm_product_template_version
                           │
                           └──► TemplatePublished event ──► EMS ──► em_engagement_read_model

Practitioner ──► EMS ──► em_client
                      ──► em_engagement (blob + metadata)
                      ──► em_engagement_read_model

Practitioner clicks "Compare" ──► Dashboard API ──► EMS ──► DiffSummaryService (sync)
                                                               ├── cache hit  ──► returns narrative
                                                               └── cache miss ──► S3 reads + LLM ──► ds_diff_summary ──► returns narrative

Practitioner ──► Dashboard UI ──► Dashboard API ──► em_engagement_read_model (reads)
                                               └──► EMS.recordDecision ──► em_update_decision
                                                                       └──► em_engagement (current_version_id)
```
