# P2 — Spring Boot + SolverManager: асинхронен lifecycle (архитектурен договор)

> **Status:** архитектурно допълнение към [P1 runnable Java 25](README.md), НЕ изпълним Spring Boot endpoint в този PR. Реалният P1 код използва `SolverFactory.buildSolver().solve()` умишлено за CLI/functional tests. За реална backend услуга `SolverManager` е препоръчителният Timefold API; без това рискуваме HTTP timeout и неконтролирани CPU workers.

## 1. Казус

Операторът изпраща 500 задачи, solver оптимизира десетки секунди, фронтенд трябва да продължи да работи. Грешно е `@PostMapping` директно да извиква `new TaskScheduleSolver(...).solve(...)` и да държи HTTP thread до финален отговор.

## 2. Предложен API contract

```text
POST /plans                 -> 202 Accepted + jobId
GET  /plans/{jobId}         -> status: QUEUED / SOLVING / COMPLETE / FAILED / CANCELLED
GET  /plans/{jobId}/result  -> 200 score + assignments + omissions, или 409 ако не е готов
POST /plans/{jobId}/cancel  -> graceful terminateEarly
POST /plans/{jobId}/replan  -> new snapshot/version, pin committed assignments
```

`SolverManager` се създава/инжектира singleton и е конфигуриран с ограничен parallel solver count според CPU бюджета; уникален `jobId` идентифицира проблема. Timefold 2.x използва builder API `solveBuilder().withProblemId(id).withProblem(problem).run()`. Действителните job state и failure results трябва да се съхраняват извън in-process solver, ако приложението трябва да преживява restart.

## 3. Correctness/security правила

- Валидирай input counts, IDs, authorization scope, data source freshness и бюджет, **преди** да подадеш input.
- Не публикувай snapshot, ако `score.hardScore() < 0`. Ако `mediumScore() < 0`, резултатът е частично изпълнен — покажи unassigned tasks и искай потребителско решение по политика.
- `@PlanningPin` защитава committed assignments в **следващия** batch snapshot; не го променяй на working plan от друга нишка.
- За real-time `ProblemChange` промените се изпращат към solver thread чрез ProblemChangeDirector (не пряко mutation).
- `SolverManager` консумира CPU и има ограничения; request cancellation, concurrency quotas, rate limits, job retention, PII/security и audit са приложение-level отговорности.
- Не обещавай глобален оптимум; публикувай score + solver version + resource limits + timestamp, когато operational correctness го изисква.
- Не използвай Community module в предположение, че Enterprise `ScoreAnalysis` е налично — в 2.x някои explainability features са комерсиални.

## 4. Защо не реализираме HTTP тук

Изпълнимият P1 модел е framework-agnostic Java 25 и вече се валидира с solver и JUnit. HTTP/job persistence ще изискват отделен Spring Boot service module, JSON contracts, auth, lifecycle tests, server resource limits и реални user/tenant IDs. Тези неща не присъстват в оригиналната лекция и биха удвоили scope-а. **Тази страница е non-runnable архитектурен контракт** за бъдеща интеграция.

**Източници:** [Timefold 2.8 SolverManager](https://docs.timefold.ai/timefold-solver/latest/running-timefold-solver/library/library-integration) · [Spring Boot integration (4.x)](https://docs.timefold.ai/timefold-solver/latest/running-timefold-solver/library/spring-boot) · [Real-time planning](https://docs.timefold.ai/timefold-solver/latest/responding-to-change/responding-to-change) · [лекцията](https://www.youtube.com/watch?v=lsvVJaigNp8).
