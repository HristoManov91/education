# P1 · Stream Gatherers — New Intermediate Operations (Java 25)

> **Source:** Devoxx 2026, [2:05:00–2:13:00](https://youtu.be/zC2tbq7S5UE?t=7500). Stream Gatherers финализирани чрез [JEP 485 в Java 24](https://openjdk.org/jeps/485). Тук използваме stable Java 25 API без preview.

## Production-like problem

Обработваме event stream: трябва да правим **пакети**, **sliding windows**, **running totals**, **dedupe по business key** и евентуално да обогатяваме записите чрез I/O с **ограничен concurrency**, без да губим encounter order.

Наивно правим `stream.toList()` и после imperative loops, смесващи input, mutable accumulator, batching и side effects. Това материализира цялото съдържание предварително, губи composability и може да наруши bounds.

**Цел:** използвай междинни stream transformations с ясна семантика. `collect()` е terminal operation; `gather()` е **intermediate**, което позволява последващи `filter/map/limit`.

## 1. Mental model

```text
Stream<T>
   .filter(...)          ordinary intermediate
   .gather(gatherer)    custom/built-in intermediate (T -> zero/one/many R)
   .map(...)            another intermediate
   .toList()            terminal
```

`Gatherer<T,A,R>` има концептуално:
- **initializer**: ново (възможно mutable) state A за едно изпълнение;
- **integrator**: вход T → обновява state и може да `downstream.push(R)` 0/1/many пъти;
- **combiner**: ако има реална коректна стратегия за parallel split/merge;
- **finisher**: optional emit при край на upstream.

**Важен детайл:** `Gatherer.ofSequential` няма combiner; дори upstream stream да е parallel, това не прави custom mutable state автоматично thread-safe/parallelizable. Не добавяй unsafe combiner само за скорост.

## 2. Five built-in gatherers in Java 25

| API | Input | Output | Важен edge case |
| --- | --- | --- | --- |
| `windowFixed(n)` | A B C D E | [A,B] [C,D] [E] | Последният window може да е непълен |
| `windowSliding(n)` | 1 2 3 4, n=3 | [1,2,3] [2,3,4] | При малък stream може да има един partial window |
| `scan(initial, op)` | 1 2 3 4 | 1 3 6 10 | Emits each **prefix**, not just final |
| `fold(initial, op)` | A B C | "ABC" | Emits final aggregate (one result) |
| `mapConcurrent(n, mapper)` | a b c | transformed a b c | Concurrent mapping с virtual threads, ограничено n и запазен encounter order |

**Забележки:**
- Window lists са unmodifiable; не разчитай на implementation type/serialization.
- Огромни window sizes могат да използват ненужно много памет.
- Мутации / I/O side effects в mapper не стават транзакционни; cancellation е best-effort.
- `mapConcurrent` **не е** general-purpose executor scheduler с retry/timeout policies. Реалните downstream capacity limits, backpressure и connection pools остават отделни concerns.

## 3. Code → proof

Основен class: [StreamGatheringExercises](src/main/java/bg/hristomanov/education/gatherers/StreamGatheringExercises.java). Всички примери имат JUnit проверки в [StreamGatheringExercisesTest](src/test/java/bg/hristomanov/education/gatherers/StreamGatheringExercisesTest.java).

### Fixed vs sliding

```java
Stream.of(1, 2, 3, 4, 5)
    .gather(Gatherers.windowFixed(2))
    .toList();
// [[1,2], [3,4], [5]]
```

При sliding от 4 елемента с размер 3:

```java
List.of(1, 2, 3, 4).stream()
    .gather(Gatherers.windowSliding(3))
    .toList();
// [[1,2,3], [2,3,4]]
```

### Scan ≠ Fold ≠ Collect

- **scan**: нужен е всеки междинен prefix, например cumulative spending / running metric;
- **fold**: нужен е един final value след последния input;
- **collect**: terminal reduction с договор за accumulator/combiner/finisher; `Collector` не може да emit-ва междинни stream елементи към следващото stage.

В нашия `runningTotals` използваме `Math.addExact`. Ако потокът е [Integer.MAX_VALUE, 1], тестът доказва, че overflow **хвърля exception**, вместо да замаскира грешка.

## 4. Custom Gatherer: distinctBy classifier

```java
public static <T, K> Gatherer<T, ?, T> distinctBy(
        Function<? super T, ? extends K> classifier) {
    return Gatherer.<T, Set<K>, T>ofSequential(
            HashSet::new,
            (seen, item, downstream) -> {
                if (seen.add(classifier.apply(item))) {
                    return downstream.push(item);
                }
                return true;
            });
}
```

Input: ["ant", "bear", "cat", "door", "eagle"], classifier=String::length. Output: ["ant","bear","eagle"].

**Защо state не е static HashSet:** всеки stream evaluation трябва да има ново `seen`. Статично shared state би въвело data races, мръсни резултати след повторен run и невалидни assumptions при parallel tests.

Test:
- повтори **същия Gatherer instance** върху две отделни streams → резултатите са еднакви;
- compose `distinctBy(String::length)` с `windowFixed(2)` → [[ant,bear],[eagle]].

**Типичен капан:** да добавиш parallel combiner чрез `left.addAll(right)` и да твърдиш, че запазва "първия срещнат" елемент. При различни stream partitions това изисква правилна encounter-order semantics и cannot simply be assumed.

## 5. Ordered bounded concurrency

```java
input.stream()
     .gather(Gatherers.mapConcurrent(2, mapper))
     .toList();
```

Mapper-ът може да работи върху virtual threads, но резултатите се връщат в encounter order. Test проверява result order и actual max in-flight не надвишава 2.

**Trade-off:** ако downstream service има 5 DB connections, `mapConcurrent(200)` не прави DB по-бърза; bottleneck и queue просто се преместват. Ако една mapping операция се провали, трябва да анализираш cancellation и кой външен side effect може вече да се е случил. Виж [Temporal Saga](../../concurrency/project-loom/README.md) за concurrent request boundaries и [Idempotency](../../../spring/reliability/idempotency/README.md) за retried effects.

## 6. When NOT to use Gatherers

- Ако можеш да напишеш лесно `map/filter/flatMap` без state → не създавай custom Gatherer;
- Ако просто ти трябва окончателна map/group/sum → `collect` е достатъчно;
- Ако workload има complex async streams/backpressure/subscriptions → Gatherers **не** заменят Reactive Streams едно към едно;
- Ако custom Gatherer е stateful → не добавяй combiner преди да докажеш deterministic semantics.

## 7. Execution

```bash
mvn -pl java/streams/stream-gatherers test
```

Lab exercise:
1. Имплементирай `distinctBy` наивно чрез static HashSet и докажи с тест, че вторият stream вече губи елементи.
2. Върни instance-local state и провери `parallelStream` поведението спрямо order guarantee.
3. Добави `mapConcurrent` за simulated I/O, без Thread.sleep в assertions; измери реален concurrency max и discuss short-circuit/cancellation.
4. Докажи, че `windowFixed` за 5 елемента и n=2 връща partial last batch.

## Interview / code-review checkpoint

- Защо `Gatherer` е intermediate, а `Collector` terminal?
- Кога е нужен finisher?
- Какво означава липсващ combiner за parallel stream?
- Защо mutable state трябва да е per evaluation?
- Каква е цената на mapConcurrent при blocking IO и downstream capacity?
- Как ще гарантираш, че gatherer не задържа downstream/state references след invocation?

**Mental model:** Gatherer е composable transducer, а не просто по-умен Collector. Удобният API не отменя алгоритмичната сложност, подредбата и concurrency guarantees.

## Original sources

- [Devoxx 2026](https://youtu.be/zC2tbq7S5UE?t=7500)
- [JEP 485 — Stream Gatherers (Final)](https://openjdk.org/jeps/485)
- [Java 25 Gatherer API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/stream/Gatherer.html)
- [Java 25 Gatherers API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/stream/Gatherers.html)
- [Oracle Java 25 Stream Gatherers Guide](https://docs.oracle.com/en/java/javase/25/core/stream-gatherers.html)
