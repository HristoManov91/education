# P1 — The Year of the Runtime: Java в ерата на AI

> **Ron Pressler — [The Year of the Runtime, Devoxx Belgium 2026](https://www.youtube.com/watch?v=tle2mA7EwWU)** · изслушан 14:49 запис, проверен предоставен auto-generated SRT (377 cues).  
> **Място:** P1 инженерно допълнение към [Big Runtimes Make Efficient Programs](BIG-RUNTIMES-JIT-ABSTRACTIONS.md), [Project Leyden](README.md) и [JFR Troubleshooting](../jfr-troubleshooting/README.md); **НЕ нов Maven module** и **НЕ втора JVM performance лекция**.
>
> Основната идея на говорителя е, че по-важен от синтаксиса може да стане **runtime-ът, който позволява на софтуера да се развива без загуба на производителност, сигурност и видимост**. Твърдението „Java is the best runtime“ е **мнение на лектора**, не измерено универсално сравнение.

## 1. Engineering казус — AI PR с 100% зелени тестове

Имаме Java/Spring backend, който обработва протоколи и отчети. AI агент изпраща PR, минава всички налични JUnit тестове и отстранява видимия bug. Но променя пет несвързани service-а, премахва интерфейс с цел „скорост“, добавя reflection helper и прави диагностиката трудно проследима.

Можем ли автоматично да заявим, че кодът е **готов за дългосрочна поддръжка**? Не.

**[00:30–02:28](https://youtu.be/tle2mA7EwWU?t=30)**: Pressler разделя *механично проверима коректност в дадени условия* от *еволюируемост* — способност да добавим функции, да поправяме security vulnerabilities, да сменяме hardware и да запазим quality attributes. Това е **теза от лекцията**; не доказва, че всички LLM или тестови системи имат еднакви ограничения.

**Конкретен review въпрос:** ако утре добавим още един тип протокол, променяме ли един локален handler/contract, или трябва да редактираме централен `switch`, serializer, gateway и пет неочаквани места?

### Практическа матрица за AI-assisted Java code review

| Измерение | Какво трябва да е вярно | Доказателство | Кога „зелено“ не стига |
| --- | --- | --- | --- |
| **Correctness** | Стари и нови domain invariants са запазени | Unit/integration/negative tests + independent oracle | Тестовете само повтарят допусканията на AI |
| **Evolvability** | Промяната е локална и има стабилна abstraction boundary | Diff/map на променени класове; subtype/contract regression test | За всеки нов handler редактираме много несвързани места |
| **Performance** | Новият код е в допустим budget при реална натовареност | JMH за локална хипотеза; p95/p99, CPU, RSS, JFR за service | „Няма reflection“ или „по-малко редове“ се приема за speedup |
| **Security / Integrity** | Няма привилегирован reflection escape, неочаквани secrets или mutation на final state | Access/negative tests, dependency/version review, threat model | Passing happy path не проверява привилегии и leak |
| **Observability** | При инцидент имаме timeline, корелация и критерий за опровержение | Metrics + synthetic JFR custom events + безопасна анализна извадка | AI произнася root cause без сурови метрики и процес на проверка |
| **CI feedback cost** | Проверяваме точно нужния scope, без да пропускаме критични gates | targeted module test и main branch full Verify | Ускоряване чрез `-DskipTests` или слепи CI rerun-и |

**Евтина начална алтернатива:** преди нов framework, AOT, JFR или codegen — направи малка ясна промяна, запиши invariants и добави тест, който би станал червен при нарушението им.

## 2. Защо runtime-ът има значение

**[02:25–04:50](https://youtu.be/tle2mA7EwWU?t=145)**. Абстракция = клиентският код **не знае** всички implementation details; бъдещи промени остават локални. Но runtime оптимизаторите търсят именно тези детайли (receiver types, call frequencies, allocation patterns) и могат да направят speculative inline и deoptimization.

```text
Source / API abstraction
        |
        v
JVM bytecode + runtime class loading
        |
        +---- profile actual call sites ----+
        |                                   |
        +---- JIT specialization / deopt ---+---> machine code / execution
        |
        +---- moving GC, memory management, observability
```

**Trade-off:** JVM самата е голяма и има warm-up, GC CPU и runtime complexity. AOT може да измести част от тази цена към build/training, но добавя съвместимост, version и deployment задачи. Нито JIT, нито Native Image, нито Quarkus/Spring AOT са автоматично най-бързи за всички workload-и.

Съществуващото [P1 Big Runtimes практическо обучение](BIG-RUNTIMES-JIT-ABSTRACTIONS.md) вече има `ShapeDispatch`, JUnit, JMH, Spring HTTP и JFR. **Не ги дублираме.**

## 3. Какво наистина казва лекцията за JVM подобренията

| Момент | Теза от SRT | Проверен version boundary / къде вече учим |
| --- | --- | --- |
| [05:00–06:38](https://youtu.be/tle2mA7EwWU?t=300) | Moving generational GC намалява allocation cost; ZGC атакува GC pauses | [JVM GC / G1+ZGC](README.md). **Ниски паузи ≠ нула паузи във всеки deployment**; проверявай JFR и реалната latency |
| [06:40–07:16](https://youtu.be/tle2mA7EwWU?t=400) | Adaptive ZGC heap sizing според workload/ресурсна конкуренция | [JEP 546 — JDK 28 EA, opt-in](JDK28-ZGC-ADAPTIVE-HEAP.md); **не** налично в JDK 25 |
| [07:17–08:07](https://youtu.be/tle2mA7EwWU?t=437) | Compact headers, Valhalla и pointer chasing | [JDK 26/27 GC/headers](JDK26-27-GC-UPGRADE.md), [JDK 28 Valhalla preview](../../language/data-oriented-programming/VALHALLA-VALUE-OBJECTS.md). Flattening **не** е гарантирано за всяка структура |
| [08:10–09:45](https://youtu.be/tle2mA7EwWU?t=490) | JIT профилите се събират при runtime; Leyden намалява warm-up и кешира machine code | [Java 25 Leyden](README.md) + [JDK 28 EA JEP 544 AOT Code](LEYDEN-JDK28-AOT-CODE.md). Пази GC/CPU/runtime compatibility |
| [09:50–10:44](https://youtu.be/tle2mA7EwWU?t=590) | `jlink` за по-малък runtime; идея за self-contained executable | [JVM in Containers](JVM-IN-CONTAINERS.md); „single native executable с runtime вътре“ е **работа в развитие**, не стандартен JDK25 `jlink` резултат |
| [10:45–11:20](https://youtu.be/tle2mA7EwWU?t=645) | Integrity by Default/по-силни guarantees | [JEP 500 Final-Field Integrity](../final-field-integrity/README.md). При Java 25 това не е universal final-field ban |
| [11:20–12:42](https://youtu.be/tle2mA7EwWU?t=680) | JFR е rich diagnostic telemetry; нови contextual events, CPU profiling, CLI views; AI може да помогне | [JFR Java 25](../jfr-troubleshooting/JDK-25-JFR.md), **[нашият нов Java25 @Contextual JFR lab](../jfr-troubleshooting/AI-ASSISTED-JFR-INVESTIGATION.md)** |
| [12:45–14:45](https://youtu.be/tle2mA7EwWU?t=765) | AI е полезен за ограничени задачи, но големи evolving systems остават трудни; agents печелят от бързи build/test цикли | [CI Efficiency](../../build/reproducible-maven/CI-EFFICIENCY.md), [AI-assisted Testing](../../../spring/testing/ai-assisted-testing/README.md) |

**Важно:** видеото е стратегически обзор, не подробно benchmark изследване. Netflix ZGC графиката, AOT startup plot-овете и `jlink` размерите от слайдовете **не са наши измервания**; нямаме основание да ги прилагаме към произволно Spring Boot/Oracle приложение.

## 4. Decision contract — кога да използваме runtime техники

| Подход | Решава | Кога използваме | Кога **НЕ** | Печалба | Цена / по-прост вариант | Proof |
| --- | --- | --- | --- | --- | --- | --- |
| JVM + JIT | Еволюируем код с performance от runtime feedback | Дългоживеещи Java услуги с разнообразни реални кодови пътища | Ако cold startup dominаtes и профилите не се подгряват | Inlining, allocation оптимизации | Warm-up/GC/diagnostics; започни със стандартен JVM и метрики | [JMH/JFR](BIG-RUNTIMES-JIT-ABSTRACTIONS.md) |
| Leyden AOT cache | Startup / warm-up overhead | Измерен бавен readiness или warm-up | При несъвместими JDK/CPU/GC или без representative training | По-малко cold work | Build/cache lifecycle и invalidation; обикновен JVM | [Java25 lab](README.md), [JDK28 JEP544](LEYDEN-JDK28-AOT-CODE.md) |
| ZGC tuning | Tail latency/GC pressure | GC е съществена причина в recording-а | DB/network wait определя p99 | GC pauses, CPU/RAM trade-off | По-малка/различна heap конфигурация и конкурентен CPU | [GC lab](JDK28-ZGC-ADAPTIVE-HEAP.md) |
| `jlink` | Runtime image съдържа ненужни JDK modules | Има измерим size/deployment мотив + complete dependency tests | JDBC/TLS/ServiceLoader/diagnostic modules не са отчетени | По-малък runtime surface | Поддръжка на customized image; алтернатива стандартен JRE | [container checklist](JVM-IN-CONTAINERS.md) |
| JFR + AI помощник | Недостатъчно данни за performance/root cause | Имаме локален recording и проверими хипотези | Когато няма съгласие за достъп/секрети, или AI няма evidence | Може да съкрати investigative review | Privacy, sampling gaps, false causality; алтернатива JFR CLI/JMC без AI | [real JFR Java25 tests](../jfr-troubleshooting/AI-ASSISTED-JFR-INVESTIGATION.md) |

## 5. Особено важно: „JFR + AI“ ≠ „AI знае root cause“

Pressler **казва** ([11:45–12:25](https://youtu.be/tle2mA7EwWU?t=705)), че AI agents могат много добре да анализират JFR. Това е негово наблюдение, **не** доказана гаранция, че конкретен модел или connector може директно да извлича суров production `.jfr` или правилно да диагностицира инцидента.

Изискваме:
1. **Симптом:** точно измерен p95/p99, CPU, error rate и timeframe.
2. **Evidence:** JFR events + sampling configuration и runtime версия; не raw private data към AI.
3. **Хипотези:** какво *може* да е причинило проблема и какво данните *не* показват.
4. **Противопроверка:** JFR/JMC, metrics, profiler/CPU graph, repeatable regression test.
5. **Решение:** code/config change с rollback + ново измерване на същия workload.

Новият [P2 AI-Assisted JFR Investigation](../jfr-troubleshooting/AI-ASSISTED-JFR-INVESTIGATION.md) дава истински Java25 `@Contextual` event и **allowlist-only summary** на собствен синтетичен recording.

**Специфична граница:** Java25 `jdk.jfr.Contextual` обозначава контекст на event field в **същата thread** за времетраенето на span-а. За distributed tracing между JVM-и HTTP headers трябва да се пренасят от приложението/OTel; JFR сам **не** пренася trace IDs по мрежата.

## 6. Практически упражнения и code review

1. Избери реална Spring Boot промяна от последния месец. Сравни correctness tests срещу diff locality. Къде промяната се е разляла в непредвидени модули?
2. „Ще премахна интерфейсите и ще използвам switch, защото AI така генерира по-малко код.“ Защо това не е доказателство за performance или maintainability? Кой JUnit/JMH/JFR експеримент е необходим?
3. Имаш 1-vCPU/512MiB pod с p99=2 s. Защо `jlink` или AOT кеш не доказват, че проблемът е решен?
4. Анализирай [синтетичния JFR recording](../jfr-troubleshooting/AI-ASSISTED-JFR-INVESTIGATION.md). Кое е **факт** и кое **хипотеза**, ако AI каже „проблемът е GC“ само от броя events?
5. Как бихме обвързали trace ID между HTTP Gateway и service? Защо JFR `@Contextual` не замества стандартен trace propagation?
6. Направи минимален review checklist за бъдещ AI PR: correctness, evolvability, runtime cost, security, diagnostics и CI, без неизмерени твърдения.

**Изход:** Java runtime позволява да разделим стабилен високонадежден source/API design от runtime оптимизации и наблюдаемост. Печалбите не идват безплатно; ползваме ги след evidence и version checks, не като магически flags.

**Аудио преговор:** [20–25 мин NotebookLM prompt](YEAR-OF-RUNTIME-NOTEBOOKLM-PROMPT.md).
