# System Design & Software Architecture — Java/Spring Engineering Track

> **P1 · Mid → Early Senior · учебна програма с практическо мислене, ADR, диаграми, executable Java 25 lab и казуси.** Това НЕ е още един каталог с „сложи Kafka / Redis / Kubernetes“. **Статус:** началният системен curriculum е разработен; наличните модули за DDD/CQRS/Saga/DB/Redis/Testing вече съществуват. Наличието на материал/CI ≠ лична диагностика на обучавания.

## Проблемът: знам архитектурни технологии, но мога ли да проектирам система?

Услугата за административни справки започва като един Spring Boot application с Oracle база. Нарастват заявките, справките стават бавни, export jobs заемат threads и DB connections. Един екип предлага Kafka и микросървиси, друг — повече Redis, трети — „просто още CPU“.

Нямаме основание да решаваме, преди да знаем:
- **критични business journeys:** регистрация/подписване, търсене, генериране на отчети;
- **nonfunctional requirements:** p95/p99 latency, availability, RPO/RTO, freshness, privacy, audit, peak RPS;
- **разпределение на натоварването:** read/write ratio, peak vs mean, payload sizes, heavy requests;
- **failure model:** DB outage, slow downstream, queue backlog, duplicate request, auth failure;
- **operability:** SLI, alert, incident response, deployment & rollback, cost of running;
- **constraints:** екип, бюджет, release cadence, legacy schema и data ownership.

**Основен принцип:** първо изисквания/числа и граници; после избор и архитектура; накрая доказателства.

## System Design ≠ Software Architecture

| System Design | Software Architecture |
| --- | --- |
| Нефункционални изисквания, размер на натоварването, capacity, data flow, reliability, bottlenecks и operating cost | Обособяване на компоненти, dependency direction, bounded contexts, стил на системата, ownership, evolvability |
| „Можем ли да обслужим 500 RPS при p99 цел и failover?“ | „Кое трябва да бъде в един deployable, кой притежава данните и кой има право да го променя?“ |
| Load testing, SLO dashboards, DB pool measurements, failure drills | C4, ADR, module boundaries, ArchUnit, integration contracts |

В production двете се проектират **заедно**. Не ги учим като отделни „технологии“.

## Учебен ред (микро-теми)

| ID / priority | Раздел | Крайна проверка | Материал |
| --- | --- | --- | --- |
| SD-01 / P1 | Problem framing; functional vs NFR; constraints | Мога да задам изискванията, без веднага да избера Kafka | [Requirements & SLO](01-REQUIREMENTS-SLOS.md) |
| SD-02 / P1 | SLI/SLO, latency percentiles, error budget | Правилно дефинирам numerator/denominator, latency window | [Requirements & SLO](01-REQUIREMENTS-SLOS.md) |
| SD-03 / P1 | Workload/RPS/peak, write pressure, egress | Изчислявам трафик от дадени допускания и ги маркирам | [Capacity](02-CAPACITY-PERFORMANCE.md) |
| SD-04 / P1 | Little's Law, resource limits, DB/Hikari headroom | Различавам математически estimate от measured service capacity | [Capacity & Java lab](02-CAPACITY-PERFORMANCE.md) |
| SD-05 / P1 | Vertical/horizontal scaling, hotspots, replicas, caching | Казвам кое НЕ скалира с допълнителни replicas | [Capacity](02-CAPACITY-PERFORMANCE.md) |
| SD-06 / P1 | Layered vs modular monolith vs microservices | Давам минималния архитектурен избор и разходите му | [Architecture styles](03-STYLES-BOUNDARIES.md) |
| SD-07 / P1 | Domain ownership/DDD, team vs deploy boundaries | Извеждам bounded contexts от business change, не таблици | [Architecture](03-STYLES-BOUNDARIES.md) + [DDD lab](../spring/architecture/strategic-ddd/README.md) |
| SD-08 / P1 | API/data boundaries: sync/async, caching consistency | Описвам consistency, timeouts, retries, stale reads | [Data & communication](04-DATA-COMMUNICATION.md) |
| SD-09 / P1 | DB scaling, pagination, projections, read vs write | Избирам query plan/read model без premature sharding | [Data & communication](04-DATA-COMMUNICATION.md) |
| SD-10 / P1 | Bounded work, admission, backpressure, queueing | Доказвам overload reject без side effects | [Reliability](05-FAILURE-AND-OPERATIONS.md) + [Java lab](capacity-lab/README.md) |
| SD-11 / P1 | Failure modes, retries, idempotency, outbox | Отделям transient failure от duplicate side effects | [Reliability](05-FAILURE-AND-OPERATIONS.md) |
| SD-12 / P1 | Availability, redundancy, DR, RTO/RPO | Не обещавам „99.99%“ на базата на две реплики | [Reliability](05-FAILURE-AND-OPERATIONS.md) |
| SD-13 / P2 | Security/tenancy, audit and compliance as architectural constraints | Намирам trust boundary, data access leak, blast radius | [Reliability](05-FAILURE-AND-OPERATIONS.md) |
| SD-14 / P1 | C4 context/container, sequences, deployment | Диаграма с ясни responsibilities и ownership | [Architecture communication](06-C4-ADR-REVIEWS.md) |
| SD-15 / P1 | ADRs, trade-offs, architecture fitness functions | Пиша decision с alternatives и revisit trigger | [Architecture communication](06-C4-ADR-REVIEWS.md) |
| SD-16 / P1 | End-to-end design & senior interview/code review | Защитавам конкретна архитектура с failure/cost rationale | [Reporting platform case study](07-REPORTING-PLATFORM-CASE.md) |

### Задължителни prerequisites и вече готови лаборатории

| Въпрос | Съществуващ модул — **не дублираме** |
| --- | --- |
| Dependency direction & clean boundaries | [Hexagonal Architecture](../spring/architecture/hexagonal-architecture/README.md) |
| Service boundaries / context mapping | [Strategic DDD](../spring/architecture/strategic-ddd/README.md) |
| Read/write asymmetry, projections | [CQRS](../spring/architecture/cqrs/README.md) |
| Reliable distributed workflows | [Saga](../spring/messaging/saga/README.md), [Outbox/Inbox](../spring/messaging/outbox-inbox/README.md), [Idempotency](../spring/reliability/idempotency/README.md) |
| Failure containment, retry budgets | [Fault Tolerance](../spring/reliability/fault-tolerance/README.md) |
| Sync vs async and commands vs events | [Integration Patterns](../spring/messaging/integration-patterns/01-INTEGRATION-DECISION-GUIDE.md) |
| Cache invalidation / distributed consistency | [Caching Curriculum](../spring/caching/README.md) |
| DB transactions and realistic reads | [Repository/UoW](../persistence/repository-unit-of-work/README.md), [SQL labs](../database/sql-labs/README.md) |
| Deployment probes, containers and resource limits | [Kubernetes lab](../infrastructure/kubernetes/README.md) |
| Security threat model and observability | [Threat Modeling](../spring/security/threat-modeling/README.md), [OpenTelemetry demo](../spring/observability/otel-agent-demo/README.md) |

## Научи чрез 3 проекта

1. **Single service review:** user journey + SLO + workload estimate + batch export concurrency; доказателството е [capacity Java lab](capacity-lab/README.md).
2. **Architecture evolution:** един Spring application с отделни modules, Oracle OLTP, read DTOs, background export; документирай кога NOT to split; виж [worked case](07-REPORTING-PLATFORM-CASE.md).
3. **Distributed upgrade case:** async processing + outbox/inbox + retry/idempotency and backpressure **само при реално изискване**; повторно използвай вече готовите runnable модули.

## За всеки архитектурен избор — задължителен decision contract

1. Какъв проблем/бизнес риск решава и при какъв измерим сигнал?
2. Кога го използваме — и **кога НЕ**?
3. Коя е най-простата достатъчна алтернатива?
4. Печалба (correctness, maintainability, latency, isolation) и цена (coupling, distributed failure, ops cost)?
5. Ownership/consistency/security/failure boundaries?
6. Какво ще **измерим или тестваме**, за да го потвърдим? Ако е документация без изпълним test — пишем го.

## Практичен изпит, не memorization

Използвай [16 диагностични сценария](ASSESSMENT-CASES.md), [практическо end-to-end упражнение](07-REPORTING-PLATFORM-CASE.md) и [ADR template](06-C4-ADR-REVIEWS.md). Един въпрос наведнъж, без отговор предварително. След реален отговор техническа оценка + професионален изказ, D+1/D+7/D+30 — [assessment protocol](../docs/learning/ASSESSMENT-PROTOCOL.md). Не създавай фалшиви оценки в PROGRESS.

**Източници:** [първични/официални стандарти и evidence map](SOURCES-AND-EVIDENCE.md) — Google SRE, AWS Well-Architected, Microsoft Azure Architecture Center, Simon Brown C4, Martin Fowler, Cockburn, Government Digital Service ADR. Новите сметки са **примерни**, не реално измерен production traffic.
