# Engagement Template Update Management System — Technical Test

This repository contains the architecture and design work for the CaseWare take-home technical test.

---

## How to Read This Work

**Start here:** [`architecture/design-document.md`](./architecture/design-document.md)

This is the main deliverable. It covers the high-level architecture, implementation plan, testing strategy, observability approach, and failure modes & tradeoffs — all in one place, with links to the supporting files below.

**Supporting documents** (referenced from the design document):

| File | What it covers |
| :--- | :--- |
| [`architecture/context-map.md`](./architecture/context-map.md) | Bounded contexts, their relationships, and DDD patterns (ACL, OHS, etc.) |
| [`architecture/erd.md`](./architecture/erd.md) | Full data model — all schemas (`tm`, `em`, `ds`, `sys`) with field definitions and notes |
| [`architecture/business-flows.md`](./architecture/business-flows.md) | Step-by-step walkthroughs of each use case (UC-1 through UC-7) |
| [`architecture/communication-patterns.md`](./architecture/communication-patterns.md) | Sync vs async communication rules, event payloads, and inter-context contracts |
| [`architecture/glossary.md`](./architecture/glossary.md) | Ubiquitous language — definitions for all domain terms used across documents |
| [`architecture/domain-examples.md`](./architecture/domain-examples.md) | Concrete JSON examples for templates, versions, and engagements |
| [`architecture/spec.md`](./architecture/spec.md) | Progress tracker, key decisions log, and open items |

**The original test brief** is available as [`Senior Developer, SE - Take-Home Test.pdf`](./Senior%20Developer%2C%20SE%20-%20Take-Home%20Test.pdf).
