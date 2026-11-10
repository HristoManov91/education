# P1 — Java Agents и OpenTelemetry: runtime bytecode instrumentation

> **Основен източник:** [The Magic of OpenTelemetry: Rewriting Your App in Production — Bruce Bujon](https://www.youtube.com/watch?v=Lf_m5dIjjqw), ~30 мин., по двата предоставени SRT транскрипта. Кодът и тестовете по-долу са наше учебно разширение, не препис на оригиналния demo. Java 25 LTS, Byte Buddy 1.18.17.

## 1. Реалният проблем и какво ще научим

Имаме Spring Boot услуга, която прави HTTP и Redis заявки. При стартиране с `-javaagent:opentelemetry-javaagent.jar` виждаме spans (интервали на работа в един trace), без да сме добавили код за tracing. Как е възможно? И защо JVM startup или CPU може да се промени, въпреки че business source е същият?

**Naive обяснението:** „OpenTelemetry е библиотека, която автоматично логва“. То скрива важната JVM граница: Java Agent може да замени bytecode на клас при зареждането му, преди JVM да го дефинира. Това е фундаментално различно от добавен Java import или Spring AOP proxy.

**Цел:** разбираме `premain`, manifest `Premain-Class`, `Instrumentation`, `ClassFileTransformer`, Byte Buddy `Advice` (вмъкнати инструкции), dump/decompile и цената на инструментацията. Показваме success **и** exception без промяна на business semantics.

## 2. Кое точно е от лекцията

| Ориентировъчно време | Какво показва Bruce | Нашето разширение |
| --- | --- | --- |
| 01:00–08:50 | OpenTelemetry concepts, агент и HTTP/Redis demo | Spring Boot auto-instrumentation lab |
| 09:00–15:20 | `premain`, JVM `ClassFileTransformer` и промяна на байтовете | собствен минимален startup agent |
| 15:20–21:20 | Meta-agent, сравнение на оригинални/променени класове | Byte Buddy dump + `javap` + child-JVM тест |
| 21:20–28:10 | Class/CPU/startup overhead, JVM compatibility | честен AB/BA startup experiment + decision guide |

Видеото не доказва универсални числа за производителност. Повече редове в декомпилиран class **не означава** същия процент увеличение на heap/RSS; локален PetClinic benchmark не е гаранция за нашето приложение.

## 3. JVM lifecycle — mental model

```text
java -javaagent:...-agent.jar -cp target/classes PriceApplication
    ├─ JVM чете Premain-Class от MANIFEST.MF
    ├─ PriceTimingAgent.premain(..., Instrumentation)    [преди main()]
    ├─ регистрира transformer за PricingService
    ├─ JVM зарежда PricingService.class
    ├─ Byte Buddy вмъква timer/print instructions около quoteCents()
    └─ дефинира transformed class -> main() -> business method
```

`ClassFileTransformer` може да върне нови bytes или `null` (без трансформация). Оригиналният `target/classes/PricingService.class` не се изменя. **Този lab е `premain` (startup).** `agentmain` (runtime dynamic attach), retransformation и redefinition са различни capabilities с version/security ограничения — не ги представяме като едно и също.

### README → код → проверка

| Концепция | Код | Тест |
| --- | --- | --- |
| `premain`, transformer, `Advice` | [PriceTimingAgent.java](src/main/java/bg/hristomanov/education/instrumentation/agent/PriceTimingAgent.java) | [AgentProcessIT.java](src/test/java/bg/hristomanov/education/instrumentation/agent/AgentProcessIT.java) |
| Business logic без зависимост от агента | [PricingService.java](src/main/java/bg/hristomanov/education/instrumentation/sample/PricingService.java), [PriceApplication.java](src/main/java/bg/hristomanov/education/instrumentation/sample/PriceApplication.java) | [PricingServiceTest.java](src/test/java/bg/hristomanov/education/instrumentation/sample/PricingServiceTest.java) |
| Процесен startup benchmark | [AgentStartupComparison.java](src/main/java/bg/hristomanov/education/instrumentation/benchmark/AgentStartupComparison.java) | [AgentStartupComparisonTest.java](src/test/java/bg/hristomanov/education/instrumentation/benchmark/AgentStartupComparisonTest.java) |
| Истински OpenTelemetry agent | [Spring Boot / Redis demo](../../../spring/observability/otel-agent-demo/README.md) | [реален HTTP тест](../../../spring/observability/otel-agent-demo/src/test/java/bg/hristomanov/education/otel/TelemetryDemoHttpTest.java), [smoke script](../../../spring/observability/otel-agent-demo/scripts/otel-smoke.sh) |

[Maven конфигурацията](pom.xml) генерира **standalone agent JAR** с Maven Assembly (Byte Buddy и `Premain-Class`), вместо да разчита на application classpath да предоставя зависимостите на агента.

## 4. Лаборатория A: реален Java Agent, не имитация

**Bad/naive:** слагаме `System.nanoTime()` във всеки service или Spring `@Around` аспект. Това е разумно за собствените Spring bean-ове, но не покрива library code и има proxy/self-invocation ограничения.

**Good за този проблем:** [PriceTimingAgent](src/main/java/bg/hristomanov/education/instrumentation/agent/PriceTimingAgent.java) таргетира точно `PricingService.quoteCents` и добавя `Advice.OnMethodExit(onThrowable=Throwable.class)`. Изчислението не знае за агента. Собствени грешки на advice се suppress-ват, но business exceptions не се поглъщат. Console output е **само учебен marker**, не production tracing implementation.

Изисквания: JDK **25**, Maven, без Docker. От repo root:

```bash
mvn -pl java/jvm/java-agent-instrumentation verify
cd java/jvm/java-agent-instrumentation

# Контрол (без агент)
java -cp target/classes bg.hristomanov.education.instrumentation.sample.PriceApplication
# TOTAL_CENTS=2100

# С реален agent:
java -javaagent:target/java-agent-instrumentation-1.0-SNAPSHOT-agent.jar \
  -cp target/classes bg.hristomanov.education.instrumentation.sample.PriceApplication
# TOTAL_CENTS=2100
# stderr: LAB_AGENT quoteCents status=OK elapsedNanos=<динамично>
```

**Доказателство:** [AgentProcessIT](src/test/java/bg/hristomanov/education/instrumentation/agent/AgentProcessIT.java) проверява manifest, пуска отделни JVM процеси, сравнява бизнес резултата, доказва, че marker има **само** при `-javaagent`, проверява exception path и че реално е dump-нат transformed class. `mvn test` изпълнява само unit tests; използвай `mvn verify` за Failsafe IT след `package`.

## 5. Лаборатория B: виж действителния bytecode

```bash
# От module директория:
mkdir -p target/bytecode-dump
java -Dnet.bytebuddy.dump=target/bytecode-dump \
  -javaagent:target/java-agent-instrumentation-1.0-SNAPSHOT-agent.jar \
  -cp target/classes bg.hristomanov.education.instrumentation.sample.PriceApplication

# Оригинален class:
javap -c -p -classpath target/classes bg.hristomanov.education.instrumentation.sample.PricingService

# Намери променения class (Byte Buddy дава dynamic filename):
find target/bytecode-dump -name '*PricingService*.class'
# После: javap -c -p <действителния път до dump .class>
```

Сравни вмъкнатите timer/print инструкции, без да извеждаш performance извод от броя decompiled редове. Byte Buddy dump е лабораторен инструмент и не бива да е включен безконтролно в production.

## 6. Изход и decision contract

**Преди:** не знаехме защо runtime tracing се появява без source промени. **Root cause:** при class loading agent може да трансформира bytecode. **След:** собствен agent, процесен regression test, bytecode inspection и [benchmark/decision guide](BENCHMARK-AND-DECISIONS.md). **Ограничение:** тези тестове не доказват production latency, privacy policy или OTel coverage за всеки framework.

| Подход | Кога да | Кога НЕ | Печалба | Загуба/алтернатива |
| --- | --- | --- | --- | --- |
| OTel Java Agent | many services, cross-HTTP/Redis/JDBC traces | без съвместимост/telemetry policy | zero-code spans/propagation | startup/CPU/RSS, exporter, sampling; алтернатива manual OTel |
| Собствен Byte Buddy agent | обучение/специфична инструментализация | като универсален production tracing framework | пълен контрол | classloader/version риск; алтернатива AOP/JFR |
| Spring AOP / Filter | Spring method / HTTP lifecycle | чужди library класове извън Spring proxy | по-прост и явен код | ограничен boundary, self-invocation |
| JFR | JVM CPU/GC/locks/allocations | вместо distributed traces | runtime profiling | няма trace context по подразбиране |

**Mental model:** `-javaagent` е JVM lifecycle feature, не Java dependency. Source и реално дефинираният class може да се различават. Цена на observability и business correctness се доказват поотделно.

### Code review checklist

- [ ] Нужен ли е JVM Agent, или са достатъчни Filter/AOP/JFR/manual spans?
- [ ] Matcher-ът и версиите на библиотеките съвместими ли са?
- [ ] Потвърдени ли са резултатът **и** exception contract с агент?
- [ ] Има ли classloader / JDK / CDS / Leyden compatibility tests и rollback?
- [ ] Изключени ли са tokens, passwords и чувствителни данни от spans?
- [ ] Измерени ли са startup/CPU/RSS/p95 и exporter cost при еднакъв workload?

## Оригинални източници

- [Bruce Bujon — The Magic of OpenTelemetry](https://www.youtube.com/watch?v=Lf_m5dIjjqw) — двата предоставени SRT.
- [OpenTelemetry Java agent getting started](https://opentelemetry.io/docs/zero-code/java/agent/getting-started/) и [upstream source](https://github.com/open-telemetry/opentelemetry-java-instrumentation).
- [Java 25 java.lang.instrument](https://docs.oracle.com/en/java/javase/25/docs/api/java.instrument/java/lang/instrument/package-summary.html), [ClassFileTransformer](https://docs.oracle.com/en/java/javase/25/docs/api/java.instrument/java/lang/instrument/ClassFileTransformer.html).
- [Byte Buddy](https://github.com/raphw/byte-buddy) и [release notes](https://github.com/raphw/byte-buddy/blob/master/release-notes.md).
- [JEP 451 — Dynamic Agent Loading](https://openjdk.org/jeps/451), [JFR](../jfr-troubleshooting/README.md), [JVM Performance/Leyden](../performance-evolution/README.md).
