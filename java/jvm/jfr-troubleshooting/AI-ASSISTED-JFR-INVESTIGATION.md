# P2 — AI-Assisted JFR Investigation: Java 25 Contextual Events и evidence guardrails

> **Казус от [Ron Pressler — The Year of the Runtime, Devoxx 2026](https://www.youtube.com/watch?v=tle2mA7EwWU)**, auto-generated SRT 11:20–12:40; концептуално продължение на [основния JFR курс](README.md).  
> Изпълнимият Java25 код **не е от видеото**, а наша собствена лаборатория по [официалния `jdk.jfr.Contextual` API](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.jfr/jdk/jfr/Contextual.html). Само **синтетични, локални** данни и реален `RecordingFile`/JUnit proof, без облачен AI API, без нов Maven модул.

## 1. Реален казус — „AI каза, че причината е GC“

След release една Spring Boot услуга получава p99 от 300 ms до 1.8 s. Metrics показват CPU spike, но самите HTTP logs не казват коя operation/нишка е засегната. Колега изпраща голям production `.jfr` файл на AI агент и получава отговор „добави още RAM — проблемът е GC“.

**Това е лошо решение**, защото:
- няма доказан GC root cause, а p99 може да е lock contention, IO, DB wait, queueing или невалидна benchmark методология;
- JFR може да съдържа JVM args, system properties, command-line values, file paths, business IDs и custom event fields;
- единично AI заключение не е експеримент, няма counter-evidence, rollback или повторно измерване.

**По-добрият процес:** събери оторизиран recording → прочети го локално → извлечи конкретни **allowlisted, агрегирани** данни → формулирай проверими хипотези → провери с JFR CLI/JMC и metrics → поправка + ново измерване.

Лекторът представя възможностите на JFR и хипотезата, че AI може да помогне за по-лесен анализ. Той **не** показва тази конкретна Java25 JUnit лаборатория или доказан автоматичен production root-cause agent.

## 2. Важната Java 25 новост: `@Contextual`

`jdk.jfr.Contextual` е **анотация върху поле в custom JFR Event**, добавена в Java25. При анализ с `jfr print`/поддържащ viewer полето е контекст за други events **на същата нишка**, които попадат в интервала на span event-а (начало → край). <https://docs.oracle.com/en/java/javase/25/docs/api/jdk.jfr/jdk/jfr/Contextual.html>

Съществените граници:
- Не е `ThreadLocal` и не е автоматично HTTP header propagation.
- Не създава автоматично distributed traces. При няколко Java процеса **приложението** трябва да пренася същия trace ID (например чрез OpenTelemetry).
- Context, прикрепен към един thread, не се появява автоматично при работа на друг thread или външна услуга.
- Всеки запис носи чувствителни диагностични данни; `@Contextual` **не** означава автоматично redaction.
- Концептуалното „контекст в child event“ е резултат от интерпретацията на **event metadata + time interval + thread**, а не поле `traceId` вътре във всеки друг event.

```text
JAVA 25 JFR RECORDING                SAME JVM + SAME THREAD

RuntimeTraceEvent:
  traceId = "lab-checkout-a"          @Contextual   <-- begin
  operation = "synthetic-cpu"
        |
        +-------- RuntimeWorkEvent:
                    phase="checksum"
                    iterations=150000
                    checksum=<deterministic value>
                                                        <-- end/commit
RecordingFile reads both events. JFR-aware tooling can associate
the child interval with the surrounding contextual trace interval.
```

**Защо `@Contextual` вместо записване на traceId във всички events?** Една обща scope метаданна намалява ръчното дублиране и позволява на tools да покажат контекст върху JVM events (например monitor contention). Цена: parser-ът трябва да следи state и времевите граници; не трябва да маркираме произволни high-cardinality/PII полета като контекст.

## 3. README → runnable код → доказателство

| Компонент | Какво прави | Проверка |
| --- | --- | --- |
| [RuntimeTraceEvent.java](src/main/java/bg/hristomanov/education/jfr/context/RuntimeTraceEvent.java) | `@Contextual String traceId`, bounded synthetic scope | Real JFR metadata: `ValueDescriptor.getAnnotation(Contextual.class)` |
| [RuntimeWorkEvent.java](src/main/java/bg/hristomanov/education/jfr/context/RuntimeWorkEvent.java) | Nested phase/iterations/checksum без собствен traceId | Same thread + nested time interval |
| [RuntimeContextLab.java](src/main/java/bg/hristomanov/education/jfr/context/RuntimeContextLab.java) | Пуска два вложени events, deterministic checksum, синтетични ID-та и ограничен input | Invalid/secret-like IDs + bounded-work JUnit |
| [RuntimeContextDemo.java](src/main/java/bg/hristomanov/education/jfr/context/RuntimeContextDemo.java) | Създава истински локален `.jfr`, отпечатва само агрегирани данни | Manual `jfr print` |
| [JfrEvidenceSummary.java](src/main/java/bg/hristomanov/education/jfr/context/JfrEvidenceSummary.java) | Чете trusted local file по events (cap 10k), допуска само **двата известни type names** и извежда count/duration | Regression: trace ID/друг sensitive event не попада в summary |
| [RuntimeContextRecordingTest.java](src/test/java/bg/hristomanov/education/jfr/context/RuntimeContextRecordingTest.java) | `Recording` → `dump` → `RecordingFile.readAllEvents` | Metadata, same-thread nesting, 2 traces/2 phases, input rejection, sanitized summary |

**Какво доказват JUnit тестовете?** Че event-ите действително се записват, JFR metadata действително носи `@Contextual`, nested event-ът е в същата нишка/времеви диапазон, input е ограничен и **текстът на нашия summary** не съдържа сурови ID-та или друг event payload. Това **не** доказва корелация между два различни процеса, голяма trace инфраструктура или производителността на production приложение.

## 4. Стартиране — само съществуващия Maven модул

Трябва JDK 25, Maven, с код от корена на Education:

```bash
# Targeted tests: без пълен Spring / Redis / Kubernetes reactor
mvn --no-transfer-progress -pl java/jvm/jfr-troubleshooting -am test

# Малка ръчна лаборатория — след компилацията:
cd java/jvm/jfr-troubleshooting
java -cp target/classes bg.hristomanov.education.jfr.context.RuntimeContextDemo target/context-demo.jfr
```

Изходът е подобен на:

```text
Source: synthetic local JFR lab
trace_events=2
work_events=2
total_work_duration_ms=<real value from current run>
scanned_events=<actual count>
truncated=false
Interpretation: counts and duration only; no root cause or speedup inferred.
```

**Това е локална тестова JFR следа. Не я качвай в AI сервиз.** Може да я прегледаш със същия JDK 25:

```bash
jfr summary target/context-demo.jfr
jfr print --events bg.hristomanov.education.jfr.RuntimeWork target/context-demo.jfr
jfr view hot-methods target/context-demo.jfr
```

Провери дали `jfr print` показва contextual `RuntimeTrace.traceId` върху вложените `RuntimeWork` events в твоя JDK25 build; това е **ръчна end-to-end CLI проверка**. Не фиксираме текстовата CLI форма в CI, понеже formatter/output може да се развива. Отделният JUnit тест валидира **metadata и timeline**, което е стабилният contract на лабораторията.

**Важно:** няма обещание, че 150 000 synthetic iterations ще генерират полезен CPU sampling профил; това е доказателство за event correlation, не workload benchmark. Ако `jfr view hot-methods` не показва значими samples, това само по себе си не е bug.

### Защо analyzer-ът е умишлено ограничен

`JfrEvidenceSummary.analyze`:
- обработва само *локален доверен* файл (не arbitrary remote JFR);
- използва streaming `RecordingFile`, а не зарежда всички events наведнъж;
- обхожда максимум 10 000 events и маркира `truncated=true` при прекъсване;
- брои само собствените `RuntimeTrace`/`RuntimeWork` events и сумарна продължителност;
- **никога не отпечатва** traceId, event values от друг type, JVM arguments, paths и secrets;
- не изпраща данни по мрежа и не извежда автоматична диагноза.

Ограничения: не замества организация за data classification/redaction при production и **не гарантира**, че произволен непроверен `.jfr` файл е безопасен за parsing. `truncated=true` означава, че aggregation не описва всички events; AI не трябва да я използва за causal conclusion.

## 5. Готов prompt за AI анализ с доказателства

Копирай **само текстовия allowlisted report**, когато данните са одобрени за обработка. Не копирай суров `.jfr`.

> Работиш като Java 25/JFR incident investigator. Следва одобрен агрегат от **синтетична** JFR лаборатория. Раздели изхода на **Факти**, **Хипотези**, **Какви доказателства липсват**, **Експерименти за опровержение** и **Следваща безопасна стъпка**. Не твърди root cause, throughput, p99 или GC проблем само от event counts и cumulative duration. Не измисляй липсващи измервания. Ако отчетът е `truncated=true`, маркирай го като непълен. Използвай само входния текст, а не въображаеми runtime traces. За production би трябвало да поискаме CPU/GC/lock/wait, HTTP latency и deployment evidence в рамките на одобрена privacy политика.

**Примерен допустим отговор на AI:** „Има 2 synthetic trace scopes и 2 measured work phases. Нямаме runtime CPU sampling и GC/lock evidence, нито HTTP p99. Можем да предложим нов recording с тези events, но причината за production regression е неизвестна.“

**Примерен недопустим отговор:** „2 work events доказват GC pause. Увеличи `-Xmx`.“

## 6. Decision contract

| Техника | Какво решава | Кога да | Кога НЕ | Печалба | Цена / алтернатива | Proof |
| --- | --- | --- | --- | --- | --- | --- |
| `@Contextual` scope | Runtime events без бизнес контекст | Има определим scope на същата нишка | Очакваш автоматично multi-service tracing | Contextual enrichment за JFR viewers | Parser state, event design/overhead; алтернатива explicit IDs в logs/OTel spans | Real JFR metadata JUnit |
| Local `jfr print` / JMC | Неясен CPU/GC/lock/I/O симптом | Имаш recording + права + конкретна хипотеза | Няма данни или симптомът е в SQL/remote system | Runtime evidence | Sampling blind spots; алтернатива metrics/logs | JFR CLI/JMC |
| AI на allowlisted summary | По-бърз review на измервания и хипотези | Sanitized synthetic/authorized evidence | Raw production trace, секрети, липсващо разрешение | Структурирано triage | Hallucinations/false causality; алтернатива човек + `jfr view` | Summary leakage tests |
| Distributed trace ID | Проследяване през няколко услуги | HTTP/async message flow и OTel propagation | Очакваш `@Contextual` да изпрати headers | Cross-service correlation | Context boundary/privacy/OTel operations; алтернатива локален trace | Отделни OTel integration tests |

## 7. Senior checkpoint и въпроси

1. Ако `traceId` има `@Contextual`, ще се появи ли върху event от друга виртуална нишка? Защо?
2. Каква е разликата между `@Contextual` и `@Relational`? Къде би сложил само correlation ID?
3. Как ще докажеш, че 1.8 s p99 е CPU-bound, а не DB wait, само от JFR `RuntimeWork` count? (Не можеш.)
4. Кога summary-ът може да пропусне важни events? Как се държим при `truncated=true`?
5. Кои exact данни е безопасно да покажем на външен AI агент и кой ги одобрява?
6. Ако CI JFR тестът е зелен, защо не бива да заявяваме „готов production observability“?
7. Какво трябва да променим, ако искаме да проследяваме trace през Gateway → Spring service → Oracle? Как проверяваме права, headers и secret redaction?

Свързани: [JFR Java25 CPU/Method Timing](JDK-25-JFR.md) · [JDK27 JFR Redaction](JDK-27-JFR-REDACTION.md) · [JMC guide](JMC-GUIDE.md) · [Runtime-first стратегия](../performance-evolution/RUNTIME-FIRST-AI-ERA.md) · [NotebookLM](../performance-evolution/YEAR-OF-RUNTIME-NOTEBOOKLM-PROMPT.md).

**Оригинални източници:** [Devoxx лекцията](https://www.youtube.com/watch?v=tle2mA7EwWU), предоставеният SRT, [Java25 `@Contextual`](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.jfr/jdk/jfr/Contextual.html), [Oracle JDK25 release notes](https://www.oracle.com/java/technologies/javase/25-relnote-issues.html) и [JFR event reading](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.jfr/jdk/jfr/consumer/RecordingFile.html).
