# P1 — Overhead на Java Agent: как да измерим, без да се подведем

> Съпътстваща глава към [Java Agents / Byte Buddy](README.md), по [Bruce Bujon](https://www.youtube.com/watch?v=Lf_m5dIjjqw). Числата от сцената не са universални.

## 1. Какво точно сравняваме

| Dimension | Как го измерваме | Не бива да заключаваме |
| --- | --- | --- |
| JVM process startup | ProcessBuilder от стартиране до exit | че това е HTTP service readiness |
| First requests / cold phase | реални HTTP calls веднага след readiness | че отражението е същото при steady state |
| Steady throughput, p95/p99 | стабилен load generator, еднакъв workload | от average към tail latency |
| CPU и allocations | JFR/JMC/OS metrics | wall-clock waiting == CPU работа |
| Heap, GC, RSS | `jcmd`, JFR и OS metrics | heap == RSS |
| Exporter costs | сравнение `none` срещу реален OTLP | че console exporter е production baseline |

**Конкретен измерим lab:**

```bash
mvn -pl java/jvm/java-agent-instrumentation verify
cd java/jvm/java-agent-instrumentation

java -cp target/classes \
  bg.hristomanov.education.instrumentation.benchmark.AgentStartupComparison \
  target/java-agent-instrumentation-1.0-SNAPSHOT-agent.jar
```

[`AgentStartupComparison`](src/main/java/bg/hristomanov/education/instrumentation/benchmark/AgentStartupComparison.java) използва реални child JVM процеси с и без агент, контрол на `TOTAL_CENTS=2100`, dry warm-up, 10 редуващи се AB/BA двойки и median. [JUnit](src/test/java/bg/hristomanov/education/instrumentation/benchmark/AgentStartupComparisonTest.java) проверява median contract.

**Ограничение:** това е *process wall-clock launch → exit*, не CPU, RSS, реален Spring startup, throughput или latency p99. Нашият agent логва в stderr, което влияе на сравнението. Няма фиксирана „очаквана“ промяна.

## 2. Production-like протокол за истински OTel Java Agent

За [Spring Boot HTTP/Redis demo](../../../spring/observability/otel-agent-demo/README.md):

1. Фиксирай app commit/artifact, JDK 25 build, OS/CPU/memory limits, heap, Redis state, agent version, exporter, request mix и concurrency.
2. Измери поне 20 cold starts, при еднакъв readiness endpoint. Не бъркай port bound с напълно ready услуга.
3. За steady load направи повторяеми runs с базова correctness проверка, p95/p99/throughput + ресурсни counters. Следи за coordinated omission при генератора.
4. Различавай **Redis miss** и **hit** (различен downstream graph), **agent on/off** и **OTLP vs none vs console** — не сменяй два фактора в една проба.
5. Запази всички raw results, а не само „agent is X% slower“. Няма валиден универсален threshold от чужда конференция.

Вече имаме [Benchmark Correctness](../performance-evolution/BENCHMARK-CORRECTNESS.md), [JFR](../jfr-troubleshooting/README.md) и [Leyden](../performance-evolution/README.md). Не копираме техните runnable labs.

## 3. Рискове, които трябва да преценим

- **Tracing:** agent добавя без код поддържани spans, но не гарантира пълен coverage. При missing span провери version/support и sampling.
- **Security:** без secrets, cookies, tokens, UIN, customer data или request bodies по подразбиране. Configured collector auth/TLS и retention budget са задължителни в production.
- **Cardinality / sampling:** висока cardinality струва памет, processing и пари. Head sampling не обещава запазване на всяка error trace; tail sampling струва collector buffering/CPU.
- **Classloaders:** agent classes се зареждат рано и могат да създадат `NoClassDefFoundError`/конфликти. Use minimal targeted matchers and integration tests.
- **Dynamic attach:** `premain` не е `agentmain`. Не обещавай свободно пренаписване на вече заредени класове.
- **Build / JDK:** Java upgrade, Leyden/CDS cache, native-image и agent compatibility искат отделна test matrix. Rollback = махане на `-javaagent` и рестарт.
- **Diagnostics:** `net.bytebuddy.dump` и console spans са за локални лаборатории, не production default.

## 4. Senior checkpoint и checklist

- Защо Spring `@AfterReturning` не вижда request, който пада при Spring MVC validation преди controller?
- Как `ClassFileTransformer` е различен от Spring AOP proxy? Кои са constraints за class redefinition?
- Ако decompiler показва 8 пъти повече редове, можем ли да заключим 8 пъти RAM? Защо не?
- Как доказваме един и същ trace ID през inbound/outbound HTTP?
- При твърдение „OpenTelemetry добавя 50%“ кои пет параметъра на експеримента ще поискаш?

**Преди merge:** [ ] business invariants; [ ] agent/JDK compatibility; [ ] no secrets; [ ] sampling/export costs; [ ] repeatable measurements; [ ] rollback path.

## Оригинални източници

- [Bruce Bujon — оригиналното видео](https://www.youtube.com/watch?v=Lf_m5dIjjqw).
- [OpenTelemetry Java configuration](https://opentelemetry.io/docs/zero-code/java/agent/configuration/).
- [Java 25 ClassFileTransformer](https://docs.oracle.com/en/java/javase/25/docs/api/java.instrument/java/lang/instrument/ClassFileTransformer.html).
- [Byte Buddy](https://github.com/raphw/byte-buddy), [Benchmark Correctness](../performance-evolution/BENCHMARK-CORRECTNESS.md).
