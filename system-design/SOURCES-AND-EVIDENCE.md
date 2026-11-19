# System Design & Architecture — authoritative sources and evidence matrix

> **Verified primary/official conceptual sources**, различни по функция от **собствени executable tests**. Външна статия не доказва, че нашият код гарантира p99, throughput или RTO. Тест със toy inputs не е production capacity evidence.

## Primary sources

| Source | Original/official URL | What it supports | Guardrail |
| --- | --- | --- | --- |
| Google SRE Workbook — Implementing SLOs | https://sre.google/workbook/implementing-slos/ | user-centric SLI/SLO/error budgets, time windows, monitoring | не означава, че примерният SLO е обещание |
| Google SRE — Non-Abstract Large System Design (NALSD) | https://sre.google/workbook/non-abstract-design/ | start from requirements, capacity estimates, feasibility, resilience | estimates are model assumptions, not measured replicas |
| AWS Well-Architected, six pillars | https://docs.aws.amazon.com/wellarchitected/latest/framework/the-pillars-of-the-framework.html | security, reliability, operational excellence, performance, cost, sustainability | cloud vendor framing, not universal tech selection |
| AWS REL05 — distributed failure interactions | https://docs.aws.amazon.com/wellarchitected/latest/framework/rel05.html | bounded queues, client timeouts, throttle, fail fast, statelessness | does not prescribe HTTP status or rate limit algorithm universally |
| AWS Builders' Library — Using load shedding to avoid overload | https://builder.aws.com/content/3Eun1EEyX6p2e3VYNyRLSJzLuMV/using-load-shedding-to-avoid-overload | goodput, overload amplification, avoiding wasted work | local concurrency gate is only a small illustration |
| Microsoft — Queue-based load leveling pattern | https://learn.microsoft.com/en-us/azure/architecture/patterns/queue-based-load-leveling | smooth bursts by decoupling arrival and work | async acceptance does not equal completed business result |
| Microsoft — Design to scale out | https://learn.microsoft.com/en-us/azure/architecture/guide/design-principles/scale-out | scaling dimensions, contention and shared bottlenecks | need actual test for horizontal scaling gain |
| Simon Brown — official C4 model | https://c4model.com/ | L1 context, L2 containers, L3 components, L4 code and dynamic/deployment diagrams | C4 containers are NOT necessarily Docker containers |
| Martin Fowler — Monolith First | https://martinfowler.com/bliki/MonolithFirst.html | microservice premium and monolith-first argument | architecture depends on organizational context, not universal ban |
| Martin Fowler — CQRS | https://martinfowler.com/bliki/CQRS.html | complexity and read/write asymmetry | CQRS does not require event sourcing/broker |
| Alistair Cockburn — Hexagonal Architecture | https://alistair.cockburn.us/hexagonal-architecture/ | purpose-driven port/adapter boundaries and testability | hexagonal ≠ microservices |
| UK Government Digital Service — Architecture decisions | https://gds-way.digital.cabinet-office.gov.uk/standards/architecture-decisions.html | ADR format and decision lifecycle | ADR ≠ complete C4 architecture description |
| Martin Fowler / Microservices Guide | https://martinfowler.com/microservices/ | trade-offs, organizational and operational complexity | anecdotal historical trends ≠ scientific proof |

## Which new claim can actually be proved?

| Claim | Material / code | Evidence and limitation |
| --- | --- | --- |
| 8.64M/day = 100 average RPS; 5× peak => 500 peak; mean concurrency estimate = 100 | [CapacityPlanner](capacity-lab/src/main/java/bg/hristomanov/education/systemdesign/CapacityPlanner.java) | [JUnit deterministic arithmetic](capacity-lab/src/test/java/bg/hristomanov/education/systemdesign/CapacityPlannerTest.java), NOT load proof |
| Six initial replicas if measured sustainable 150 RPS each & planned utilization 60% | [Capacity modeling](02-CAPACITY-PERFORMANCE.md) | `ceil(500/(150×0.6))=6`, but 150 is **hypothetical measured input** requiring real benchmarking |
| Local cap prevents second work from running while first owns permit | [BoundedAdmissionGate](capacity-lab/src/main/java/bg/hristomanov/education/systemdesign/BoundedAdmissionGate.java) | [JUnit concurrency + exception release](capacity-lab/src/test/java/bg/hristomanov/education/systemdesign/BoundedAdmissionGateTest.java); NOT distributed quota |
| Architectural modules have enforceable boundaries | [Strategic DDD](../spring/architecture/strategic-ddd/README.md) | existing ArchUnit/Spring Modulith tests; **NOT** testing new proposed reporting app |
| Outbox/inbox reduce dual-write and duplicate side-effect risk | [Outbox/Inbox lab](../spring/messaging/outbox-inbox/README.md) | existing integration tests; **NOT** exactly-once delivery guarantee |
| CQRS read staleness can be observed/tested | [CQRS lab](../spring/architecture/cqrs/README.md) | existing deferred projection tests; workload and user consistency policies still needed |
| p99 500ms / 99.9% SLO | [Requirements](01-REQUIREMENTS-SLOS.md) | **proposed illustrative targets only**, no production telemetry |
| Multi-AZ or replicas imply specific SLO/RTO | [Failure](05-FAILURE-AND-OPERATIONS.md) | **NO proof** without failover/restore drills & measurement |

## Review quality bar

- For each new module, add local `Problem / When / When NOT / Gain / Cost / Alternatives / Evidence`.
- If no runnable lab exists, label it **concept-only / architectural workshop** and link existing executable proof where applicable.
- Never claim a throughput number, availability percentage, exact DB behavior or security property without measuring it in a representative environment.
- For an assessment, only demonstrated user answers update [PROGRESS](../docs/learning/PROGRESS.md); CI green is not a personal learning score.
