# ADR-0001 — Modular monolith for transactional document core

**Status: Proposed (teaching example, НЕ real production decision)**  
**Date: 2026-11-19**  
**Owners:** fictitious reporting-platform engineering team

## Context

Хипотетична административна система:
- едни и същи транзакционни invariants за document/signature lifecycle;
- един екип, общ Oracle OLTP data ownership;
- high-read search и background export;
- няма доказана нужда отделните бизнес modules да се deploy-ват independently;
- operational team може да поддържа един application и един/два workers, но distributed debugging има реална цена.

**Unknowns:** реални p99, peak traffic distribution, DB lock waits, global RTO/RPO, authorizations, deployment cadence. Те трябва да бъдат измерени/договорени.

## Options

| Option | За | Против |
| --- | --- | --- |
| A — simple layered monolith | least moving parts | трудно налагане на business boundaries при растеж |
| B — **modular monolith** with feature-owned data & explicit APIs | local transactions, clear boundaries, simpler deploy than many services | discipline, package/module test rules, one deploy lifecycle |
| C — microservices per business capability | independent deployment and scaling when needed | HTTP failures, interservice contracts, tracing, consistency, DevOps overhead |

## Decision

**Предложение:** B, при current assumptions. Вътрешни modules: `documents`, `search`, `exports`, `audit`. Auth/tenant boundaries се налагат във всяка owning capability. Дългите exports се отделят в bounded async worker path **при измерен timeout/latency проблем**; ако jobs стават durable, изискваме job lifecycle/status и retry semantics.

**НЕ:** не разделяй `Document` и `Signature` в два services само заради две таблици. Не поставяй Kafka без доставка/retry requirements.

## Consequences

**Benefits:** местна ACID транзакция за signing invariants, traceable boundaries, по-малко production operational overhead от multi-service topology.  
**Costs:** един общ runtime deployment, модули споделят resource ceilings, дисциплина в imports/DB access, migrations must be coordinated.  
**Risk:** architectural drift към „споделена база за всички“; модулна граница само на хартия.  
**Alternative:** остани на опростен layered monolith при минимални dependencies; не строи интерфейси „за всеки случай“.

## Verification

- ArchUnit/Spring Modulith dependency tests без forbidden imports/cycles;
- transaction integration test за signature state and rollback (виж [DDD lab](../../spring/architecture/strategic-ddd/README.md));
- representative load test и разбор на Oracle connection waits;
- export worker capacity/queue age + duplicate request tests;
- authorization negative tests, including cross-tenant object lookup.

## Revisit if

- най-малко два teams имат независим release cadence и repeatedly block each other;
- измерена нужда от независим scaling за конкретна capability;
- замърсяване на domain ownership, доказано с failing architecture fitness functions;
- system SLOs не могат да се постигнат с monolith + separated workers без непропорционална цена.

**Primary references:** [Martin Fowler — Monolith First](https://martinfowler.com/bliki/MonolithFirst.html); [Cockburn — Hexagonal](https://alistair.cockburn.us/hexagonal-architecture/); [UK GDS — ADR process](https://gds-way.digital.cabinet-office.gov.uk/standards/architecture-decisions.html).
