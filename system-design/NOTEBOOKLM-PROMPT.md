# NotebookLM — System Design & Software Architecture: deep technical audio course

> Ползвай **само** актуалните GitHub материали и източниците им. Целта е 20–30 минутен обяснителен аудио урок; NotebookLM не трябва да измисля production measurements, trade-off numbers или лични оценки.

## Кои sources да добавиш

1. [Entry curriculum](README.md) + [Requirements/SLO](01-REQUIREMENTS-SLOS.md).
2. [Capacity & Performance](02-CAPACITY-PERFORMANCE.md) + [Java test-backed lab](capacity-lab/README.md).
3. [Architecture Styles & DDD](03-STYLES-BOUNDARIES.md) + [Strategic DDD](../spring/architecture/strategic-ddd/README.md).
4. [Data/Communication](04-DATA-COMMUNICATION.md) + [CQRS](../spring/architecture/cqrs/README.md) + [Outbox/Inbox](../spring/messaging/outbox-inbox/README.md).
5. [Reliability & Operations](05-FAILURE-AND-OPERATIONS.md), [C4 & ADR](06-C4-ADR-REVIEWS.md).
6. [Reporting Platform capstone](07-REPORTING-PLATFORM-CASE.md), [ADR example](decisions/0001-modular-monolith-for-core.md).
7. [Primary sources & proof map](SOURCES-AND-EVIDENCE.md); Google SRE NALSD and SLO chapters, Simon Brown C4.
8. Optional transcript/lecture (само с проверено авторство); не твърди, че е използван конкретен transcript, ако не е предоставен.

## Prompt за NotebookLM (copy/paste)

```text
Направи задълбочен български аудио урок за backend engineer (Java 25, Spring Boot, Oracle/JPA, Redis),
който иска да мисли като software architect, а не да запомня технологии.

Започни от казус: административна reporting система с подписване, търсене и тежък export.
Първо нека двамата водещи уточнят functional/NFR, constraints, SLI/SLO и assumptions.
Покажи три итерации:
1) най-простата работеща архитектура;
2) измерен bottleneck и предпазване от overload;
3) ако и само ако се налага, asynchronous export / distributed boundaries.

За всеки избор: какъв проблем решава; кога се използва; кога НЕ; плюсове;
загуби; по-просто решение; как се доказва с тест/измерване.
Включи точно разликите:
- System Design vs Software Architecture;
- Mean RPS vs peak RPS, Little's Law и p99;
- Layered vs modular monolith vs microservices;
- Sync vs async, 202 Accepted vs completed;
- Load shedding vs rate limiting vs queue;
- Idempotency vs retries, outbox and duplicate delivery;
- C4 L1/L2 vs ADR vs runnable fitness functions;
- RTO vs RPO, SLO vs SLA.

Използвай източниците Google SRE, AWS Well-Architected, C4 official website, Fowler,
UK GDS и runnable Java lab от Education. Не обявявай математическите toy estimates
за production benchmark или гаранция за 6 pods/99.9%.
Завърши с 6 въпроса за самостоятелна диагностика, БЕЗ отговорите им.
Тон: подробен, инженерно точен, с реални trade-offs и кодово/оперативни примери.
```
