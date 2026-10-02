# 01. Algorithmic Thinking & Complexity

Това е първият модул от [`java/algorithms`](../README.md).

Целта му е да изгради фундаменталния начин на мислене, върху който после стъпват arrays, hashing, trees, graphs и algorithmic patterns. Преди да питаме „кой алгоритъм да използвам?“, трябва да можем да опишем **какъв е проблемът, какъв е размерът на входа и как расте цената на решението**.

---

# 1. Реалният казус: валидираме order lines срещу продуктов каталог

Имаме batch от order lines и продуктов каталог. За всеки `OrderLine` трябва да проверим дали product ID-то сочи към активен продукт.

Наивният flow изглежда естествено:

```text
за всеки order line
    обхождай продуктите
        ако намериш съвпадение → готово
```

При 10 order lines и 20 продукта няма проблем.

Но при:

```text
50 000 order lines
10 000 products
```

най-лошият случай може да стигне до:

```text
50 000 × 10 000 = 500 000 000 comparisons
```

Това не е Java syntax проблем. Това е **algorithmic shape** (форма на алгоритъма) проблем.

- Naive: [`NestedLoopProductValidator.java`](./src/main/java/bg/hristomanov/education/algorithms/complexity/bad/NestedLoopProductValidator.java)
- Improved: [`IndexedProductValidator.java`](./src/main/java/bg/hristomanov/education/algorithms/complexity/good/IndexedProductValidator.java)

---

# 2. Какво ще научим

След модула трябва да можеш:

- да разграничиш computational problem, algorithm, ADT и concrete data structure;
- да определиш какво означава `n` за конкретен метод;
- да анализираш последователни и вложени loops;
- да работиш с независими input sizes като `n` и `m`;
- да обясниш `O(1)`, `O(log n)`, `O(n)`, `O(n log n)`, `O(n²)`;
- да различиш Big-O, Big-Theta и Big-Omega на практично ниво;
- да мислиш за time и space complexity отделно;
- да разбираш best / average / worst case;
- да обясниш amortized complexity;
- да различаваш complexity analysis от benchmark/profiling;
- да разпознаеш кога различна data structure променя complexity формата.

---

# 3. Mental model: problem → algorithm → ADT → data structure → implementation

## Computational problem

Описва:

```text
input
→ desired output
→ constraints
```

Например:

```text
Input: order lines + products
Output: invalid product IDs
Constraint: работи коректно и при голям batch
```

## Algorithm

Algorithm е процесът, чрез който решаваме problem-а.

За един и същ problem можем да имаме:

```text
nested scan
vs
build lookup index + membership checks
```

## Abstract Data Type (ADT)

ADT (абстрактен тип данни) описва **поведението и операциите**, без да фиксира конкретната implementation.

Например `Set` conceptually казва „уникални стойности + membership operation“. Реализацията може да е hash-based, tree-based и т.н.

## Data structure

Data structure организира данните така, че определени операции да имат желаната цена.

> Не избирай data structure по навик. Избери я според операциите, които problem-ът изисква най-често.

---

# README → код

| Концепция | Production-like пример | Доказателство / тест |
| --- | --- | --- |
| `O(n × m)` repeated lookup | [`NestedLoopProductValidator.java`](./src/main/java/bg/hristomanov/education/algorithms/complexity/bad/NestedLoopProductValidator.java) | [`ProductValidationComplexityTest.java`](./src/test/java/bg/hristomanov/education/algorithms/complexity/ProductValidationComplexityTest.java) |
| Индекс + membership lookup | [`IndexedProductValidator.java`](./src/main/java/bg/hristomanov/education/algorithms/complexity/good/IndexedProductValidator.java) | [`ProductValidationComplexityTest.java`](./src/test/java/bg/hristomanov/education/algorithms/complexity/ProductValidationComplexityTest.java) |
| Linear / quadratic / logarithmic growth | [`OperationGrowth.java`](./src/main/java/bg/hristomanov/education/algorithms/complexity/demo/OperationGrowth.java) | [`OperationGrowthTest.java`](./src/test/java/bg/hristomanov/education/algorithms/complexity/OperationGrowthTest.java) |
| Amortized resize cost | [`GrowingIntBuffer.java`](./src/main/java/bg/hristomanov/education/algorithms/complexity/demo/GrowingIntBuffer.java) | [`GrowingIntBufferTest.java`](./src/test/java/bg/hristomanov/education/algorithms/complexity/GrowingIntBufferTest.java) |

---

# 4. Input size: `n` не е магическа променлива

При:

```java
for (OrderLine orderLine : orderLines) {
    // work
}
```

естественият `n` е броят на `orderLines`.

В нашия bad пример обаче имаме два независими входа:

```text
n = брой order lines
m = брой products
```

затова анализът е:

```text
O(n × m)
```

а не механично `O(n²)`.

**Не преименувай всички входове на `n`, ако са независими.**

---

# 5. Основните growth classes

| Complexity | Mental model |
| --- | --- |
| `O(1)` | work-ът не расте с input size |
| `O(log n)` | problem space се намалява с constant factor |
| `O(n)` | 10× input → ~10× work |
| `O(n log n)` | типично при добри comparison sorts / divide-and-conquer |
| `O(n²)` | 10× input → ~100× work |
| exponential | много бързо става непрактично |

[`OperationGrowth.java`](./src/main/java/bg/hristomanov/education/algorithms/complexity/demo/OperationGrowth.java) не е benchmark. Той брои deterministic logical steps, за да покаже growth shape без шум от JIT, GC, CPU cache и OS scheduling.

Тестът показва например:

```text
halving:
1 024      → 10 steps
1 048 576  → 20 steps
```

Input-ът става 1024× по-голям, а добавяме само 10 стъпки.

---

# 6. Последователни loops ≠ вложени loops

```java
for (...) { ... } // n
for (...) { ... } // n
```

е:

```text
O(n + n)
→ O(n)
```

Докато:

```java
for (...) {
    for (...) {
    }
}
```

при независими bounds `n × n` е:

```text
O(n²)
```

Причината не е броят на `for` keywords. Въпросът е **колко пъти реално се изпълнява work-ът спрямо input-а**.

---

# 7. Big-O, Big-Theta и Big-Omega

- **Big-O** — asymptotic upper bound;
- **Big-Omega** — asymptotic lower bound;
- **Big-Theta** — tight bound, когато upper и lower growth съвпадат.

Ако loop минава точно през всички `n` елемента, можем да кажем `Θ(n)`.

В engineering разговор често ще чуеш просто `O(n)`; важното е да знаеш какво точно твърди notation-ът.

---

# 8. Best, average и worst case

При [`NestedLoopProductValidator`](./src/main/java/bg/hristomanov/education/algorithms/complexity/bad/NestedLoopProductValidator.java):

- best case — matching product е първият;
- worst case — product липсва или е последен;
- average case — зависи от разпределението на input-а.

Когато complexity се променя според case-а, казвай кой case обсъждаш.

---

# 9. Time vs space complexity

Good вариантът изгражда индекс:

```java
Set<Long> activeProductIds = new HashSet<>();
```

Това е trade-off:

```text
повече auxiliary memory
→ по-малко repeated search work
```

Няма „безплатна“ оптимизация.

---

# 10. Bad вариантът: repeated scan

[`NestedLoopProductValidator`](./src/main/java/bg/hristomanov/education/algorithms/complexity/bad/NestedLoopProductValidator.java) е функционално коректен и четим.

Проблемът е scale:

```text
n order lines × m products
→ O(n × m)
```

Code-review signal:

> collection lookup вътре в loop върху друга collection.

Това не е автоматично грешка — малки bounded collections може да са напълно ОК — но изисква complexity въпрос.

---

# 11. Good вариантът: lookup index

[`IndexedProductValidator`](./src/main/java/bg/hristomanov/education/algorithms/complexity/good/IndexedProductValidator.java):

```text
products
→ build active-product index
→ one membership check per order line
```

Conceptually:

```text
O(m) index build
+
O(n) logical membership checks
→ O(n + m) average
```

Тук `HashSet` е готов lookup structure. Hashing internals ще се учат в отделния hashing module.

[`ProductValidationComplexityTest`](./src/test/java/bg/hristomanov/education/algorithms/complexity/ProductValidationComplexityTest.java) доказва growth формата:

```text
bad:
100 × 100     → 10 000 comparisons
1000 × 1000   → 1 000 000 comparisons

good logical work:
100 + 100       → 200
1000 + 1000     → 2000
```

10× увеличение и на двата inputs дава 100× work при nested scan, но 10× logical work при indexed варианта.

---

# 12. Amortized complexity

[`GrowingIntBuffer`](./src/main/java/bg/hristomanov/education/algorithms/complexity/demo/GrowingIntBuffer.java) е минимална dynamic-array-like демонстрация.

Когато capacity се запълни:

```text
allocate 2× larger array
→ copy old elements
→ continue appending
```

Един resize е `O(n)`.

Но при doubling strategy resize-ите копират:

```text
1 + 2 + 4 + ... + 512 = 1023
```

за 1024 append-а.

Затова общото copy work остава linear за linear брой append-и и говорим за **amortized O(1) append**.

[`GrowingIntBufferTest`](./src/test/java/bg/hristomanov/education/algorithms/complexity/GrowingIntBufferTest.java) доказва това.

> Amortized `O(1)` не означава, че всяка отделна операция е `O(1)`.

---

# 13. Big-O не е benchmark

Два алгоритъма със същата asymptotic complexity могат да имат различна реална цена заради:

- constants;
- allocation;
- memory locality;
- CPU cache;
- branch behavior;
- JIT;
- GC;
- database / network I/O;
- synchronization.

Използвай различни въпроси:

```text
Complexity:
Как расте work-ът при по-голям input?

Benchmark / profiling:
Колко струва реално на тази JVM, hardware и workload?
```

---

# 14. Чести грешни твърдения

- „Имам два loops → `O(n²)`.“ — не задължително.
- „Hash lookup е `O(1)`, значи е безплатен.“ — не.
- „`O(n)` винаги е по-бързо от `O(n log n)`.“ — не за всеки конкретен `n`.
- „Big-O казва milliseconds.“ — не.
- „Щом input-ът днес е малък, complexity няма значение.“ — само ако bounded assumption е съзнателен.

---

# 15. Production considerations

Дори добър algorithmic shape не премахва външни bottlenecks.

Например:

```text
O(n) algorithm
+
1 database query на елемент
```

може да е много по-лош от CPU-local `O(n²)` върху малък input.

Питай:

1. Какъв е input size?
2. Кое расте с него?
3. Има ли DB/network call inside loop?
4. Има ли repeated collection search?
5. Плащаме ли memory, за да спестим work?
6. Реално bounded ли е input-ът?

---

# 16. Кога оптимизацията не си струва

Не усложнявай code-а само защото Big-O изглежда по-добре, ако:

- input-ът е строго малък и bounded;
- кодът не е hot path;
- real bottleneck е I/O;
- memory/risk trade-off-ът е лош;
- няма scale scenario, който оправдава промяната.

---

# 17. Как да стартираме тестовете

От repository root:

```bash
mvn -pl java/algorithms/01-algorithmic-thinking-complexity -am test
```

От `java/algorithms`:

```bash
mvn -pl 01-algorithmic-thinking-complexity test
```

Това е чист Java 25 lab — без Spring context, database или external services.

---

# 18. Какво точно решихме

В началото имахме:

```text
repeated linear search
→ O(n × m)
```

Променихме data organization-а:

```text
build lookup index once
→ membership lookup per order
→ O(n + m) average logical growth
```

Тестовете доказват growth shape чрез deterministic operation counts, а не чрез случайни milliseconds.

Отделно показахме защо occasional expensive resize може да участва в amortized constant-time operation.

---

# 19. Mental model за запомняне

1. **Първо дефинирай input size, после говори за complexity.**
2. **Брой how work scales, не броя `for` statements.**
3. **Data structure choice може да промени algorithmic complexity.**
4. **Big-O е growth model; benchmark-ът е measurement.**
5. **Amortized O(1) не означава, че всяка операция е O(1).**

---

# 20. Как да разпозная казуса в реален проект

Търси:

- `contains` / `find` / `stream().filter(...).findFirst()` вътре в друг loop;
- nested collection scans;
- repeated recomputation;
- code review аргумент „работи бързо при мен“ без size assumptions;
- два големи независими input-а, но analysis само като `O(n)`;
- recursive code без мисъл за depth и stack usage.

---

# 21. Практичен checklist за code review

```text
[ ] Какво е input size?
[ ] Има ли независими n, m, ...?
[ ] Кои операции се повтарят спрямо input-а?
[ ] Има ли nested / repeated search?
[ ] Намалява ли се search space?
[ ] Какъв е worst case?
[ ] Каква auxiliary memory използваме?
[ ] Може ли по-подходяща data structure да спести repeated work?
[ ] Bounded ли е input-ът или само днес е малък?
[ ] Трябва ли ни complexity analysis, benchmark, profiler — или комбинация?
[ ] Запазихме ли business semantics след optimization?
```

---

# 22. Упражнения

1. Промени теста така, че matching product да е винаги първи, после винаги последен. Сравни comparisons.
2. Промени growth strategy на `GrowingIntBuffer` от ×2 на +1 и наблюдавай total copied elements.
3. Добави fake external-call counter inside loop и сравни algorithmic work срещу external-call count.
4. Обясни защо `stream()` не променя автоматично complexity спрямо loop със същата работа.

---

# Оригинални източници

## MIT 6.006 — Introduction to Algorithms, Spring 2020

- **Lecture 1 — Algorithms and Computation**  
  https://ocw.mit.edu/courses/6-006-introduction-to-algorithms-spring-2020/resources/lecture-1-algorithms-and-computation/
- **Problem Session 1 — Asymptotic Behavior of Functions and Double-ended Sequence Operations**  
  https://www.youtube.com/watch?v=IPSaG9RRc-k
- **Lecture 19 — Complexity**  
  https://ocw.mit.edu/courses/6-006-introduction-to-algorithms-spring-2020/resources/lecture-19-complexity/
- Full course:  
  https://ocw.mit.edu/courses/6-006-introduction-to-algorithms-spring-2020/resources/lecture-videos/

## William Fiset — Data Structures playlist

- **Data structures introduction**  
  https://www.youtube.com/watch?v=Qmt0QwzEmh0
- **Abstract data types**  
  https://www.youtube.com/watch?v=2USMAwcRWHE
- **Introduction to Big-O**  
  https://www.youtube.com/watch?v=zUUkiEllHG0
- Playlist:  
  https://www.youtube.com/playlist?list=PLDV1Zeh2NRsB6SWUrDFW2RmDotAfPbeHu

## Overview

- Codist — **Every Data Structure Simply Explained in 25 Minutes!**  
  https://www.youtube.com/watch?v=vVL6NFzr0Rg

---

## Финална проверка

```text
[ ] Мога ли да дефинирам input size?
[ ] Мога ли да обясня O(1), O(log n), O(n), O(n log n), O(n²)?
[ ] Разбирам ли n срещу n,m?
[ ] Мога ли да обясня защо nested scan е O(n × m)?
[ ] Мога ли да обясня time-space trade-off-а на indexed lookup?
[ ] Мога ли да обясня amortized O(1) коректно?
[ ] Разграничавам ли complexity от benchmark?
[ ] Мога ли да намеря repeated work в реален Java method?
```
