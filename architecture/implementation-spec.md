# Implementation Spec — UC-7 Vertical Slice

Status tracker for the Part 2 targeted implementation of the CaseWare take-home test.
This is a working note so the implementation can be resumed with no context loss.

---

## Goal

Per the brief: *"Focus on interfaces, contracts, and correctness. Tests or validation logic are a plus. This is not a full application build."*

Implemented one vertical slice — **UC-7 (Practitioner accepts/declines a template update)** —
in the `engagement-template` Spring Boot project (package `com.caseware.engagement`),
demonstrating:

1. The **public service contracts** (`EngagementService`, `EngagementQueryService`)
2. The **event contract** (`UpdateDecisionRecorded`)
3. The **in-transaction (synchronous) read-model projection** handler
4. **Correctness logic** (decision validation + `update_status` derivation) with unit tests

Legacy `com.decisionengine` code is untouched; it will be deleted by the user.

---

## What we are doing

### Flow implemented (UC-7)

```
DashboardController POST /api/v1/dashboard/engagements/{id}/decisions
  → EngagementService.recordDecision(engagementId, decision, targetVersionId, userId, reason)
      ├─ validates: decision/target/userId present; engagement exists
      ├─ validates: pending update exists (latest != decided base)  [spec decision #3]
      ├─ validates: target is ahead of current version  [resolve one by one — intermediate targets allowed]
      ├─ appends em.em_update_decision (append-only trail)
      └─ publishes UpdateDecisionRecorded
→ UpdateDecisionRecordedHandler (@EventListener, in-transaction, synchronous)
       ├─ APPLIED  → read_model.current_version_id = target, last_decided = target
       │            engagement.current_version_id = target
       └─ DECLINED → read_model.last_decided_version_id = target  (current untouched)

DashboardController GET /api/v1/dashboard/engagements?tenantId=
  → EngagementQueryService.getEngagementsByTenant(tenantId)  (reads read model only)
  → update_status derived at read time: UPDATES_REVIEWED | PENDING_UPDATES
```

### Files created (all under `engagement-template`)

| Layer | Files |
| :--- | :--- |
| Schema | `docker/init.sql` — `em` schema (3 tables, indexes, grants) + demo seed (2 engagements, 2 read models, 1 decision) |
| Entry point | `src/main/java/com/caseware/EngagementApplication.java` (scans `com.caseware`) |
| model | `com.caseware.engagement.model`: `Engagement`, `EngagementReadModelEntity`, `UpdateDecision`, `DecisionType`, `UpdateStatus` |
| repository | `EngagementRepository`, `EngagementReadModelRepository`, `UpdateDecisionRepository` |
| event | `com.caseware.engagement.event.UpdateDecisionRecorded` (record: engagementId, tenantId, decision, targetVersionId, userId, timestamp) |
| dto | `RecordDecisionRequest`, `EngagementReadModel` (projection incl. derived `updateStatus`) |
| service | `EngagementService` + `EngagementServiceImpl`; `EngagementQueryService` + `EngagementQueryServiceImpl` |
| handler | `handler.UpdateDecisionRecordedHandler` (`@EventListener`, in-transaction — atomic with the decision insert) |
| controller | `controller.DashboardController` (`GET /engagements`, `GET /engagements/{id}`, `POST /engagements/{id}/decisions`) |
| tests | `DashboardControllerTest`, `EngagementServiceImplTest`, `UpdateDecisionRecordedHandlerTest`, `EngagementQueryServiceImplTest` |
| build | `build.gradle` — `bootRun`/`bootJar` `mainClass = com.caseware.EngagementApplication` |

### Key correctness rules encoded

- No decision allowed when there is no pending update (`latest == lastDecided`, i.e. the span was already decided — even if `current` lags after a decline).
- `from_version_id` = current version (the version the engagement is actually on), **not** last decided — after a decline `current` lags behind `lastDecided`.
- Target may be any version **ahead of current** (user resolves pending versions one by one), not necessarily `latest`. Full chain-level validation (target is a real version in `current → latest`) is deferred to the real `tm` version-chain lookup.
- Events are consumed **synchronously in-transaction** (`@EventListener`) — atomic with the decision insert. Async consumption via a message broker with guaranteed retries is deferred; swap when a broker is adopted.
- `update_status` derivation: last decided == latest → `UPDATES_REVIEWED`, else `PENDING_UPDATES` (null last decided → `PENDING_UPDATES`).

### Status: compilation + tests

`gradlew test --tests "com.caseware.engagement.*"` → **BUILD SUCCESSFUL** (all new code + tests pass).

---

## Pending — pickup tomorrow

1. **Optional smoke test** — boot against Postgres (`engagement-template/docker`):
   - `docker compose up` (or `docker-compose up`)
   - Start app: `.\gradlew.bat bootRun`
   - Manual checks:
     - GET `/api/v1/dashboard/engagements?tenantId=99999999-9999-9999-9999-999999999991` → 1 row `PENDING_UPDATES`
     - POST `/api/v1/dashboard/engagements/11111111-1111-1111-1111-111111111111/decisions` with `{"decision":"APPLIED","targetVersionId":"00000000-0000-0000-0000-000000000002","userId":"demo-user"}` → 204; re-GET → `UPDATES_REVIEWED`
2. **Anything you decide after review** (e.g., package name `com.caseware`, 202 vs 204 response, adding a global exception handler).

---

## Open items / decisions to confirm

| Item | Current choice | Alternatives |
| :--- | :--- | :--- |
| Package root | `com.caseware.engagement` | `com.lender.*` |
| POST decision response | `204 No Content` — decision applied synchronously, nothing queued | ~~`202 Accepted`~~ (implies async queueing) |
| Error handling | `IllegalArgumentException` (no `@RestControllerAdvice` yet) | Add advice mapping to 400/404 |
| DB schema | `em` schema in `init.sql` (Postgres) | Flyway migration `V3` |