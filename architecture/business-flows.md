# Business Flows

Data flow descriptions for all use cases represented in the [Context Map](./context-map.md).

---

## UC-1 — Content Team Publishes a New Template Version

**Actor:** Content Team (Caseware Internal)

1. Content Team uploads a new template version to the **Template Publishing Service (TPS)**.
2. TPS writes the zip archive to the **Template Zip Store (S3)** — immutable, versioned by key.
3. TPS writes version metadata (version number, checksum, `storage_uri`) to the **Product Template DB**.
4. TPS emits a `TemplatePublished` event `[OHS / PL]` (carrying version metadata) consumed by the **Update Tracking Service**.
5. Update Tracking Service uses the event data to mark engagements on older versions as "update available" and projects that state into the **Engagement Read Model**.

---

## UC-2 — Practitioner Creates a New Engagement

**Actor:** Practitioner (Accounting Firm User)

1. Practitioner creates an engagement via the **Engagement Management System (EMS)**.
2. EMS persists the engagement blob to the **Customer Engagement DB**.
3. EMS emits an `EngagementCreated` event `[OHS / PL]` consumed by the **Update Tracking Service**.
4. Update Tracking Service projects the new engagement's metadata (current template version, tenant, etc.) into the **Engagement Read Model**.

---

## UC-3 — Practitioner Opens an Existing Engagement (Reconciliation)

**Actor:** Practitioner (Accounting Firm User)

1. Practitioner opens an engagement in EMS (rehydration from blob — up to ~1 minute).
2. EMS emits an `EngagementOpened` event `[OHS / PL — reconciliation hook]` consumed by the **Update Tracking Service**.
3. Update Tracking Service uses this event as a reconciliation signal to verify the Read Model is consistent with the current state of the engagement (e.g. catches any missed events).

> This flow exists because EMS rehydration is expensive. The Dashboard never calls EMS directly — it reads from the Read Model instead, avoiding the rehydration cost on every query.

---

## UC-4 — Practitioner Views Update Status on the Dashboard

**Actor:** Practitioner (Accounting Firm User)

1. Practitioner opens the **Dashboard UI**.
2. Dashboard UI queries the **Dashboard API**.
3. Dashboard API reads from the **Engagement Read Model** `[CF — conforms to read model schema]` — no translation needed, the Read Model is designed for this consumer.
4. Dashboard UI renders the list of engagements with their update status (up-to-date, update available, update pending decision, etc.).

---

## UC-5 — Diff Summary is Generated for a Template Update

**Trigger:** Update Tracking Service detects an engagement is behind a new template version (via UC-1 or UC-2).

1. **Update Tracking Service** requests a diff summary from the **Diff & Summary Engine** `[ACL — translates internal model into DS contract]`.
2. Diff & Summary Engine checks the **Summary Cache** (keyed by `template_id + from_version + to_version`).
   - **Cache hit:** returns the cached summary immediately.
   - **Cache miss:** reads both zip archives from the **Template Zip Store (S3)**, computes a JSON diff, generates an LLM narrative summary, writes the result to the Summary Cache.
3. Diff & Summary Engine emits a `SummaryGenerated` event `[OHS / PL]` consumed by the **Update Tracking Service**.
4. Update Tracking Service projects the summary reference into the **Engagement Read Model** so the Dashboard can display it.

---

## UC-6 — Practitioner Accepts or Declines a Template Update

**Actor:** Practitioner (Accounting Firm User)

1. Practitioner reviews the diff summary on the **Dashboard UI** and makes a decision (accept / decline).
2. Dashboard UI sends the decision to the **Dashboard API**.
3. Dashboard API translates the decision into the EMS contract `[ACL]` and forwards it to the **Engagement Management System (EMS)**.
4. EMS records the decision and emits an `UpdateDecisionRecorded` event `[OHS / PL]` consumed by the **Update Tracking Service**.
5. Update Tracking Service writes an immutable record to the **Audit Log** and updates the **Engagement Read Model** to reflect the new decision state.

---

## Data Flow Summary

```
Content Team ──► TPS ──► S3 / Template DB ──► UpdateSvc ──► Read Model ──► Dashboard
                                                   ▲
                                              DiffEngine
                                              (S3 reads,
                                               cache writes)

Practitioner ──► EMS ──► EngagementDB
                  │
                  └──► UpdateSvc (events) ──► Read Model / Audit Log

Practitioner ──► Dashboard UI ──► Dashboard API ──► Read Model (reads)
                                                └──► EMS (decision writes via ACL)
```
