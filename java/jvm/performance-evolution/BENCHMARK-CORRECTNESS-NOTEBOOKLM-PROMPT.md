# NotebookLM — P1 Benchmark Correctness / Performance Measurement

Това е **един фокусиран 30–40-минутен аудио урок на български**, не още една обща лекция по JFR/GC.

## Sources to add

1. Основно видео: [Devoxx Belgium 2026 — When Benchmarks Go Bad](https://www.youtube.com/watch?v=yePre1hOvHE).
2. [Нашият основен P1 урок](BENCHMARK-CORRECTNESS.md).
3. [Практическите Java лаборатории](BENCHMARK-CORRECTNESS-LABS.md).
4. [BenchmarkValidityLab.java](src/main/java/bg/hristomanov/education/performance/benchmark/BenchmarkValidityLab.java) и [JUnit tests](src/test/java/bg/hristomanov/education/performance/benchmark/BenchmarkValidityLabTest.java).
5. [Quarkus/Spring real investigation](https://quarkus.io/blog/hidden-cost-rootless-container-networking/) и [original open-source benchmark](https://github.com/quarkusio/spring-quarkus-perf-comparison).
6. [Brendan Gregg USE Method](https://www.brendangregg.com/usemethod.html), [Gatling open/closed models](https://docs.gatling.io/testing-concepts/workload-models/) и [HdrHistogram](https://github.com/HdrHistogram/HdrHistogram).

## Audio Overview prompt

```text
Създай задълбочен 30–40-минутен технически аудио урок на естествен български за Java/Spring Mid+ → Senior backend developer.

Основна тема: "When Benchmarks Go Bad — как доказваме, че измерваме правилното нещо?". Това НЕ е общо обяснение какво е JMH или как се пуска Docker. Целта е инженерно мислене: валиден measurement contract, правилен workload, reproducibility, realism, relevance, осъзнато разграничаване на системните bottlenecks.

1. Започни с реален казус: два benchmark-а на Spring/Java service са зелени, но единият съобщава p99=10ms, а другият p99=1830ms. Защо и двата могат да мерят вярно собственото си поведение, но само един да съответства на production arrival pattern?

2. Обясни подробно open-loop срещу closed-loop load generation, intended arrival vs actual send vs completion, scheduling, queueing и Coordinated Omission. Използвай нашия точен симулиран Java пример: 200 заявки, интервал 100ms, service 10ms, server stall 1000–3000ms. Покажи причинно-следствено защо open-loop p99=1830ms, а closed-loop p99=10ms, въпреки че максималната closed-loop latency е 1910ms. Това е детерминистична учебна симулация, НЕ емпиричен benchmark; сценарии имат различен offered load и това е целта.

3. Обясни как HdrHistogram correction работи концептуално и кога би довел до подвеждане: known expected interval, modeled missed samples, double correction, inability to turn unissued requests into real traffic. Closed-loop може да е правилен, ако production е наистина closed — не го представяй като универсално погрешен.

4. Разкажи реалното Quarkus vs Spring Boot разследване със специфични стойности 1.19x на локална pasta/nftables среда, 1.80x на host networking, 1.97x без nftables и 2.08x на perf-lab hardware. Покажи как proxy single-core ceiling може да влияе асиметрично и защо тези числа НЕ са универсална класация на framework-ите.

5. Обясни Active Benchmarking и Brendan Gregg USE Method (utilization/saturation/errors), и RED за API service. Проследи една request flow диагностика: load generator → network proxy → Tomcat/Netty → JDBC pool → database → response. Как ще докажеш на кой ресурс пада ограничението чрез CPU/per-core, queue, network, DB waits и errors?

6. Разграничи offered traffic от successful throughput. Използвай 1000 attempts, 900 successes, 100 failures, 1s; защо 1000 observed outcomes/s НЕ означава 1000 successful business operations/s. Обясни p99 само на успешните заявки и timeouts/failed sampling; heap vs RSS/container memory, startup vs first successful HTTP response, warm-up.

7. Дай конкретен falsifiable experiment contract за Java 21/25, Loom vs Reactive, G1 vs ZGC и Oracle19c vs PostgreSQL. Обясни why "same laptop" не означава same CPU/cache/scheduler/DB images/data state и как повторяемостта може да е в конфликт с production realism. Не измисляй JMH/JFR benchmark numbers.

8. Завърши с 7–10 senior-level review questions и практичен checklist. За всеки важен проблем използвай формат bad approach → root cause → better approach → how to prove it → trade-off. Не чети Java код символ по символ; обясни execution flow и причината.

Поддържай точна терминология на български и обяснявай техническите английски термини. Приоритет е да мога да оспорвам или защитавам performance цифри пред колеги, архитекти или на интервю. Не приписвай резултати от чужди измервания на нашия education repository.
```

## After listening

Използвай [диагностичния протокол](../../../docs/learning/ASSESSMENT-PROTOCOL.md): един конкретен benchmark review scenario, отговор на български с правилни термини, професионална корекция, преговор на D+1/D+7/D+30. Създаденият курс **не е доказателство, че материалът е лично усвоен**.
