# P1 · Parallel Stream Starvation: shared ForkJoinPool срещу bounded virtual-thread mapping

> Devoxx Belgium 2026 — [Marit van Dijk & Piotr Przybył, 42:00–45:15](https://www.youtube.com/watch?v=QBQ11aL2aZc&t=2520s). **Изпълнима Java 25 / JUnit лаборатория.** Времена от сцената (~10s / ~30s) НЕ са наши benchmarks.

## 1. Реален казус: два независими backend процеса си пречат

Search service обогатява 5 заявки към външен endpoint; някой заменя `stream()` с `parallelStream()` за ускорение. В същата JVM друга batch задача използва parallel stream за тежко изчисление. Изведнъж първата задача стартира бавно, въпреки че самият API не е променен. `parallelStream()` обикновено използва shared `ForkJoinPool.commonPool()`, а от ForkJoinPool worker може да използва съответния pool. Това е **споделен ресурс**, не изолиран per-request executor. Освен contention, blocking I/O в ForkJoinPool може да изчерпи worker capacity.

```text
request A: parallelStream() ─┐
                             ├── shared worker budget / contention
batch B: parallelStream() ───┘

request A: stream().gather(mapConcurrent(n, ...))
                  └── own bounded virtual-thread mapping, shared downstream limits still apply
```

## 2. Какво доказваме, без да пишем flaky timing test

**[ParallelStreamContentionLab.java](src/main/java/bg/hristomanov/education/gatherers/ParallelStreamContentionLab.java)** съдържа двата подхода; **[ParallelStreamContentionLabTest.java](src/test/java/bg/hristomanov/education/gatherers/ParallelStreamContentionLabTest.java)** е детерминистичният контрол:

1. Създава **специален `ForkJoinPool(2)`**, без да променя глобалния `commonPool` на останалите тестове.
2. Две задачи задържат двамата workers с `CountDownLatch`; тестът проверява, че и двата са влезли.
3. Подаваме `parallelStream()` в този pool. Докато worker-ите са заети, задачата не приключва (очаквано `TimeoutException` в контролния прозорец).
4. Извън pool-а извикваме `Gatherers.mapConcurrent(2, ...)` с идентични вход/функция: резултатите са коректни, encounter order е съхранен.
5. Освобождаваме блокерите и `parallelStream` работата завършва. `finally` освобождава latch и shutdown-ва pool-а дори при неуспех.

Това доказва **качественото поведение при наситен конкретен pool**, не числа за production commonPool, CPU speedup или fairness. Различни версии на ForkJoinPool, managed blockers и CPU конфигурация могат да променят реалната картина.

```bash
mvn -pl java/streams/stream-gatherers -Dtest=ParallelStreamContentionLabTest test
```

### Защо `mapConcurrent` е смислен избор именно за blocking I/O

```java
List<String> response = queries.stream()
    .gather(Gatherers.mapConcurrent(5, query -> remoteCall(query)))
    .toList();
```

- Virtual threads могат да освобождават carrier при поддържан blocking I/O; не дават допълнителни CPU cores.
- `mapConcurrent(5, ...)` има **конкретен upper bound на едновременните mapper-и**. Не е rate limiter и не удължава DB pool.
- Encounter order е запазен, дори когато завършването на child tasks е в различен ред.
- Deadline, retry, error propagation, circuit breaker, cancellation и идемпотентност на side effects са **отделни въпроси**. Не приемай, че външен request ще бъде отменен незабавно.

## 3. Decision contract

| Въпрос | Избор |
| --- | --- |
| Проблем | Независими parallel-stream задачи се конкурират за ограничен workers budget; blocking mapper може да изчерпи capacity. |
| Кога | Имаме **bounded I/O enrichment** в stream pipeline и измерено contention/latency. |
| Кога НЕ | За чисти CPU операции, алгоритми с голям shared mutable state или когато `map()` е достатъчен. |
| Печалба | Явно ограничение на concurrency, отделяне от ForkJoinPool workers при нормален blocking I/O, четим pipeline. |
| Цена | Virtual thread lifecycle, heap, downstream connections, error/cancellation semantics, различен scheduling модел. |
| По-просто | Sequential mapping, bounded `ExecutorService`, async HTTP client, explicit batch; за CPU работа — измерен ForkJoin workload. |
| Доказателство | [JUnit с наситен pool](src/test/java/bg/hristomanov/education/gatherers/ParallelStreamContentionLabTest.java) + отделно реално latency/CPU/JFR измерване. |

## 4. Senior exercise и изход

- Защо `parallelStream` не е „един thread за всеки елемент“?
- Ако 5 mapper-а чакат Oracle, а pool-ът допуска 10 connection-а, ще помогне ли `mapConcurrent(100)`?
- Как бихме доказали cancellation при частично изпълнена външна POST операция?
- Защо тестът използва собствен ForkJoinPool, а не изкуствено блокира `commonPool` на Maven/JUnit?

**Mental model:** Не избирай concurrency abstraction само по синтаксис; виж shared resource, blocking model, ownership, tail latency и cancellation. Ако кодът е CPU-bound, сравни последователно и паралелно с измерване. Ако е I/O-bound с ясно ограничение, използвай explicit bounded execution и тествай failure semantics.

## Оригинални източници

- [Devoxx лекция, 42:00](https://youtu.be/QBQ11aL2aZc?t=2520) (предоставен Tactiq транскрипт).
- [Примерен код DeepDiveJava25](https://github.com/pioorg/DeepDiveJava25).
- [JEP 485 — Stream Gatherers](https://openjdk.org/jeps/485).
- [JDK 25 Gatherers.mapConcurrent](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/stream/Gatherers.html).
- [ForkJoinPool API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/ForkJoinPool.html).
- [Existing Gatherers lab](README.md) и [Loom](../../concurrency/project-loom/README.md).
