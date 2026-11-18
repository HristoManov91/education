# NotebookLM — The Year of the Runtime (Ron Pressler, Devoxx 2026)

## Кои източници да заредим

1. [The Year of the Runtime — Ron Pressler](https://www.youtube.com/watch?v=tle2mA7EwWU), оригинално видео, и предоставения auto-generated SRT (377 cues, 14:49). Разграничавай реториката на лектора от технически проверимите твърдения.
2. [P1 Runtime-first Java in the AI era](RUNTIME-FIRST-AI-ERA.md).
3. [Big Runtimes — JIT/GC/abstraction cost](BIG-RUNTIMES-JIT-ABSTRACTIONS.md), [Java25 Leyden](README.md), [JDK 28 EA AOT Code](LEYDEN-JDK28-AOT-CODE.md), [JEP 546 ZGC](JDK28-ZGC-ADAPTIVE-HEAP.md).
4. [P2 AI-Assisted JFR Investigation](../jfr-troubleshooting/AI-ASSISTED-JFR-INVESTIGATION.md), [RuntimeTraceEvent.java](../jfr-troubleshooting/src/main/java/bg/hristomanov/education/jfr/context/RuntimeTraceEvent.java), [RuntimeContextLab.java](../jfr-troubleshooting/src/main/java/bg/hristomanov/education/jfr/context/RuntimeContextLab.java), [JfrEvidenceSummary.java](../jfr-troubleshooting/src/main/java/bg/hristomanov/education/jfr/context/JfrEvidenceSummary.java), [RuntimeContextRecordingTest.java](../jfr-troubleshooting/src/test/java/bg/hristomanov/education/jfr/context/RuntimeContextRecordingTest.java).
5. [Официален Java25 JFR `@Contextual`](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.jfr/jdk/jfr/Contextual.html), [JDK25 release notes](https://www.oracle.com/java/technologies/javase/25-relnote-issues.html), [JFR consumer](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.jfr/jdk/jfr/consumer/RecordingFile.html).
6. [Spring Boot AI testing guardrails](../../../spring/testing/ai-assisted-testing/README.md) и [CI efficiency](../../build/reproducible-maven/CI-EFFICIENCY.md).

## Готов prompt за 20–25 минути българско аудио

```text
Създай 20–25-минутна задълбочена, но компактна разговорна техническа лекция на БЪЛГАРСКИ за Java 25 + Spring Boot backend разработчик, който използва AI/Codex и се стреми към устойчив, ефективен CI.

Основен източник е Ron Pressler, The Year of the Runtime, Devoxx 2026, 14:49. Запази трите му тематични части: why evolvability matters, JVM runtime improvements, why that matters in the AI age. Говорителят представя възглед, а не независим benchmark; "Java is the best runtime" е мнение. Видеото НЕ показва нашия custom Java/JUnit JFR lab.

Първо кажи какво твърди лекцията:
- [00:30–04:40] AI работи с езици като интерфейс; механична проверимост не гарантира software evolvability. Абстракциите позволяват локална промяна, а JVM може да оптимизира runtime конкретиката с профили.
- [05:00–07:20] Moving generational GC, ZGC и upcoming Adaptive ZGC. Поясни, че "без GC паузи" е реторично, а JEP 546 е JDK28 EA opt-in, не Java25.
- [07:20–08:10] Compact Object Headers JDK27 и preview Valhalla JDK28. Не обещавай универсално flattening.
- [08:10–09:50] JIT warm-up и Leyden JDK25 AOT cache срещу JDK28 JEP544 cached code; различни compatibility constraints.
- [09:50–11:20] jlink и integrity by default; когато по-малък runtime е полезен и кога липсващи TLS/JDBC/JFR modules създават риск.
- [11:20–12:45] JFR събира rich telemetry, custom contextual fields, CPU sampling, CLI views; AI може да подпомогне анализа, но не доказва причинно-следствена връзка автоматично.
- [12:45–14:45] AI генерира self-contained tasks, но software evolution и maintenance изискват инженерни invariants. Fast builds/tests помагат на iteration, без да прескачаме проверки.

После преведи в практическа лаборатория:
1. Показвай проблем → кога/кога НЕ → ползи и загуби → по-проста алтернатива → код и тест.
2. Покажи Java25 RuntimeTraceEvent (@Contextual поле) и вложен RuntimeWorkEvent на същата нишка; не твърди distributed propagation от самия JFR.
3. Покажи как JUnit реално записва .jfr, прочита го с RecordingFile и проверява @Contextual metadata, timestamps и thread identity.
4. Обясни позволените граници за trace IDs, bounded CPU work и JfrEvidenceSummary. Отчитаме броеве и duration без raw IDs, paths или arbitrary event payload, само от синтетичен локален запис.
5. Измисли един BAD AI отговор ("2 events значи проблем с GC") и GOOD отговор със "Факти; Хипотези; Липсващи доказателства; Експерименти".
6. Поясни sampling limitations, redaction/privacy, защо JFR не замества HTTP metrics, DB profiling или distributed traces.
7. Завърши с senior code review сценарий за AI PR в Spring Boot: correctness, evolvability, performance, security, observability, CI cost, rollback.

Не измисляй benchmark цифри, Java feature GA версии и успешни CI резултати, които източниците не съдържат. Не добавяй нови практики на Ron Pressler, ако те са наши собствени adaptation decisions.

Финал: пет въпроса за активно припомняне, които трябва да се отговорят без гледане на документацията. Не отбелязвай урока като "усвоен", преди това да се случи.
```

## След аудиото

Първо реши упражненията от [Runtime-first P1](RUNTIME-FIRST-AI-ERA.md#6-практически-упражнения-и-code-review) и [AI JFR P2](../jfr-troubleshooting/AI-ASSISTED-JFR-INVESTIGATION.md#7-senior-checkpoint-и-въпроси). После пусни `mvn -pl java/jvm/jfr-troubleshooting -am test`. Използвай локален JDK25 за реалния `@Contextual` експеримент.
