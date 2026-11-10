# P1 — Java 25 → 26 → 27: GC, object headers и безопасен performance upgrade

> Основа: [Per-Åke Minborg — The Yearly Java Performance Update for 2026](https://www.youtube.com/watch?v=rNOzf12RORI), анализ по предоставения auto-generated SRT (приблизително 17–21, 28–34 и 38–40 мин). Конкретни JEP/JDK факти са сверени с Inside Java. **Основният код и JUnit са Java 25**; JDK 27 comparison се изпълнява отделно. Това е допълнение към [JVM Performance](README.md) и [JVM в контейнери](JVM-IN-CONTAINERS.md), не нов GC курс.

## 1. Реален входен казус

Имаме Spring Boot услуга с много малки DTO и обектни референции, ограничена до **1 vCPU и 512 MiB**. След upgrade от JDK 25 към 27 бизнес кодът и входните заявки са същите, но startup, CPU, GC паузи и RSS са различни. „Новият JDK трябва да е по-бърз“ е непроверена хипотеза: от JDK 27 някои **default JVM решения** са променени, а JDK 26 е добавил GC write-barrier оптимизации.

**Задачата:** първо проверяваме какво JVM действително е избрала; после доказваме, че бизнес резултатът е същият; накрая измерваме честно ресурсите. Не поставяме „очаквано +10%“ в JUnit.

## 2. Защо обикновен reference store може да струва повече

```java
order.customer = updatedCustomer; // на вид едно присвояване
```

За G1 това може да включва **write barrier** (допълнителен код за поддръжка на информация за препратки между heap regions). JEP 522 в **JDK 26** използва две card tables, така че mutator threads (нишките на приложението) и background refinement (GC помощната работа) да намалят необходимата синхронизация. Тази оптимизация е **в JVM**, не изисква нови Java API-та. Тя има най-голям потенциал при много reference updates, не при приложение, което стои 95% в JDBC waiting.

В [`ReferenceUpdateWorkload.java`](src/main/java/bg/hristomanov/education/performance/gc/ReferenceUpdateWorkload.java) има действителни записи на `Node.next`; [`ReferenceUpdateWorkloadTest.java`](src/test/java/bg/hristomanov/education/performance/gc/ReferenceUpdateWorkloadTest.java) проверява детерминизъм и invalid input. **Това е поведенчески контрол, не JEP 522 microbenchmark.** Сравнение на две JDK версии променя много неща едновременно и не изолира една JEP оптимизация.

## 3. Version matrix — какво действително се променя

| Механизъм | JDK 25 (LTS) | JDK 26 | JDK 27 | Риск / проверка |
| --- | --- | --- | --- | --- |
| **JEP 522 G1 two card tables** | стар write-barrier implementation | новата оптимизация | наследена | benefit главно reference-write workloads, измери CPU/p99 |
| **JEP 523 default GC** | при ограничени ресурси е възможен Serial GC | зависи от JVM ergonomics | **G1 default във всички среди** | малки containers може да сменят GC без нов flags |
| **JEP 519/534 Compact Object Headers** | product feature, **opt-in** `-XX:+UseCompactObjectHeaders` | opt-in | **включени по подразбиране** | малки обекти/heap layout; A/B чрез `-XX:-UseCompactObjectHeaders` |
| **Object Monitor Table** | не е default | специфична реализация | **default** (JDK-8379782) | contention и monitor regression checks, `-XX:-UseObjectMonitorTable` е временен deprecated fallback |
| **G1 lazy init** | по-ранна инициализация | — | допълнително отложени marking структури/нишки | startup и RSS; не сравнявай само време до `main()` |

**Важно:** Compact Object Headers са 8 вместо типичните 12 bytes metadata върху 64-bit HotSpot. Размерът на **целия обект** зависи от fields и alignment. Не следва, че всяко приложение спестява 33% heap. G1 не е „по-добрият collector за всички задачи“ — Serial остава вариант при много малък процес.

## 4. Изпълнима проверка — Java 25 срещу Java 27

Първо компилирай [Java 25 lab](pom.xml):

```bash
mvn -pl java/jvm/performance-evolution verify
```

После посочи двата **реални** `java` binaries. Скриптът [`compare-jdks.sh`](scripts/compare-jdks.sh) не инсталира JDK и не скрива невъзможността за сравнение, когато липсва версия:

```bash
bash java/jvm/performance-evolution/scripts/compare-jdks.sh \
  "$JDK25_HOME/bin/java" "$JDK27_HOME/bin/java"
```

Скриптът стартира еднакъв workload с 1 видим CPU, `Xms=Xmx=128m`, еднакви входни данни, извежда действителните `UseG1GC`/`UseSerialGC`/`UseCompactObjectHeaders` flags, запазва GC логове и **изисква идентичен CHECKSUM**. Извеждането на flags НЕ гарантира достатъчно статистика за throughput/p99; за тях следва отделен HTTP production-like experiment.

Изолирай конкретни решения чрез manual control, без да смесваш версии и flags:

```bash
# JDK 27 default G1 срещу явно Serial (само когато сравняваме GC choice):
"$JDK27_HOME/bin/java" -XX:+UseSerialGC -Xms128m -Xmx128m \
  -cp java/jvm/performance-evolution/target/classes \
  bg.hristomanov.education.performance.gc.ReferenceUpdateWorkload 2048 500000 923

# Compact header ON/OFF в една и съща JDK 27 версия (отделен експеримент):
"$JDK27_HOME/bin/java" -XX:-UseCompactObjectHeaders -Xms128m -Xmx128m \
  -cp java/jvm/performance-evolution/target/classes \
  bg.hristomanov.education.performance.gc.ReferenceUpdateWorkload 2048 500000 923
```

За production услуга: repeated cold starts, readiness (не само `main()`), warmup, steady throughput, p95/p99, OS RSS/GC logs, CPU throttling, allocation profile и business correctness. Виж [Benchmark Correctness](BENCHMARK-CORRECTNESS.md) и [JFR](../jfr-troubleshooting/README.md). Няма benchmark „резултат“ без изпълнение на target машина.

## 5. Decision contract — кое и кога има смисъл

| Решава | Кога да | Кога НЕ | Печалба | Загуба/компромис | По-проста алтернатива | Proof |
| --- | --- | --- | --- | --- | --- | --- |
| Проверка на GC defaults след JDK upgrade | 1 vCPU/малки containers или след измерена regression | при липса на симптом без да е измерено | ясна причина за разлика в CPU/pause | GC tuning и разходи за тест матрица | първо standard JDK defaults + metrics | [script](scripts/compare-jdks.sh), JFR |
| Компактни headers | милиони малки обекти, измерена heap/GC цена | при проблем в direct/native memory | потенциално по-малък heap и по-добра locality | layout/agent compatibility и performance tradeoffs | остави default на JDK27 | flags + реални heap/RSS измервания |
| JEP 522 reference-write optimization | write-heavy graph/collections | когато bottleneck е DB latency | потенциално по-малко write-barrier CPU | трудно изолиране; GC tradeoffs | JVM upgrade без custom tuning | [Java25 workload + JUnit](src/test/java/bg/hristomanov/education/performance/gc/ReferenceUpdateWorkloadTest.java) + repeated JDK measurements |

### Изход — връщане към казуса

**Преди:** „JDK 27 е по-бърз, защото е по-нов“. **След:** знаем, че default GC и object headers може да се сменят, а G1 write barriers са различни. Доказваме бизнес резултата, откриваме реалните flags и планираме измерване на същия workload. **Не доказваме** универсално ускорение от дадена JEP.

### Code review checklist

- [ ] Същият binary artifact и входни данни ли изпълняваме на двете JDK версии?
- [ ] Знаем ли кой GC реално е избран; има ли explicit flags, които скриват разликата?
- [ ] Heap/RSS/native allocations и CPU limits измерени ли са отделно?
- [ ] При JDK27 включени ли са compact headers и object monitor table; съвместими ли са агентите?
- [ ] Има ли negative controls, повторения, rollback JDK/image и резултатите без exporter/CI шум?
- [ ] Отчетен ли е типът workload: reference writes, allocations, JDBC wait или locks?

## Оригинални източници

- [Per-Åke Minborg, Devoxx 2026](https://www.youtube.com/watch?v=rNOzf12RORI) — предоставен SRT; отделяме demo наблюдения от JDK спецификацията.
- [Inside Java — JDK 26 Performance Improvements](https://inside.java/2026/06/09/jdk-26-performance-improvements/).
- [Inside Java — JDK 27 Performance Improvements](https://inside.java/2026/09/28/performance-update-jdk27/).
- [JEP 522 — G1 GC: Improve Throughput by Reducing Synchronization](https://openjdk.org/jeps/522).
- [JEP 523 — Make G1 the Default Garbage Collector in All Environments](https://openjdk.org/jeps/523).
- [JEP 534 — Compact Object Headers by Default](https://openjdk.org/jeps/534).
- [JDK-8379782 — Object Monitor Table default](https://bugs.openjdk.org/browse/JDK-8379782).
