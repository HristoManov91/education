# 04. Searching & Sorting

Това е четвъртият модул от [`java/algorithms`](../README.md).

Темата свързва две идеи, които често се учат отделно, но practically са силно зависими:

```text
как са подредени данните
→ как можем да ги търсим
→ дали preprocessing чрез sorting си струва
```

Целта не е да запаметяваме implementation на осем алгоритъма, а да разбираме **search space, input assumptions, preprocessing cost, stability, memory trade-offs и причината различните sorting family-та да съществуват**.

---

# 1. Реалният казус

Имаме read-mostly каталог от няколко хиляди numeric IDs. Каталогът вече е sorted, а по време на една операция трябва многократно да проверим дали дадени IDs съществуват.

Наивният вариант:

```text
за всяка заявка
→ scan от началото
→ O(n) comparisons
```

Но sorted order ни дава допълнителна информация:

```text
middle value < target
→ цялата лява половина вече не може да съдържа target
```

Така search space-ът се свива наполовина при всяка стъпка.

Отделно възниква по-общият въпрос:

> Струва ли си първо да подредим данните, за да направим следващите операции по-евтини?

Този trade-off свързва searching и sorting.

---

# 2. Какво ще научим

След модула трябва да можеш:

- да сравниш linear search и binary search;
- да обясниш защо binary search е `O(log n)`;
- да формулираш sorted / monotonic search-space requirement-а;
- да работиш правилно с `low`, `high`, `middle`;
- да обработиш missing values, empty input и duplicates;
- да разпознаеш off-by-one risks;
- да прецениш sorting/preprocessing cost спрямо броя бъдещи searches;
- да обясниш insertion sort, merge sort и quick sort mental model-а;
- да сравниш stability и extra-memory trade-offs;
- да обясниш защо comparison sorting има `Ω(n log n)` lower bound за general ordering;
- да обясниш защо counting/radix могат да бъдат linear-like само при по-силни input assumptions;
- да избираш алгоритъм според characteristics на входа, а не по една Big-O стойност.

---

# 3. Голямата картина

```text
Unsorted data
├── one/few searches
│   └── linear search може да е достатъчен
└── many searches / ordering required
    └── sort once
        └── binary search many times

Comparison sorting
→ learns order through comparisons
→ general-purpose
→ around n log n for efficient algorithms

Linear / non-comparison sorting
→ exploits key structure / bounded range
→ can reach O(n + k)-style behavior
→ stronger assumptions
```

---

# README → код

| Концепция | Код | Доказателство |
| --- | --- | --- |
| Linear search | [`LinearSearch.java`](./src/main/java/bg/hristomanov/education/algorithms/searching/LinearSearch.java) | [`SearchAlgorithmsTest.java`](./src/test/java/bg/hristomanov/education/algorithms/searching/SearchAlgorithmsTest.java) |
| Binary search | [`BinarySearch.java`](./src/main/java/bg/hristomanov/education/algorithms/searching/BinarySearch.java) | [`SearchAlgorithmsTest.java`](./src/test/java/bg/hristomanov/education/algorithms/searching/SearchAlgorithmsTest.java) |
| Repeated linear lookup | [`RepeatedLinearCatalogLookup.java`](./src/main/java/bg/hristomanov/education/algorithms/searching/bad/RepeatedLinearCatalogLookup.java) | [`RepeatedCatalogLookupTest.java`](./src/test/java/bg/hristomanov/education/algorithms/searching/RepeatedCatalogLookupTest.java) |
| Repeated binary lookup | [`RepeatedBinaryCatalogLookup.java`](./src/main/java/bg/hristomanov/education/algorithms/searching/good/RepeatedBinaryCatalogLookup.java) | [`RepeatedCatalogLookupTest.java`](./src/test/java/bg/hristomanov/education/algorithms/searching/RepeatedCatalogLookupTest.java) |
| Insertion sort | [`InsertionSort.java`](./src/main/java/bg/hristomanov/education/algorithms/sorting/comparison/InsertionSort.java) | [`ComparisonSortingTest.java`](./src/test/java/bg/hristomanov/education/algorithms/sorting/ComparisonSortingTest.java) |
| Merge sort | [`MergeSort.java`](./src/main/java/bg/hristomanov/education/algorithms/sorting/comparison/MergeSort.java) | [`ComparisonSortingTest.java`](./src/test/java/bg/hristomanov/education/algorithms/sorting/ComparisonSortingTest.java) |
| Quick sort | [`QuickSort.java`](./src/main/java/bg/hristomanov/education/algorithms/sorting/comparison/QuickSort.java) | [`ComparisonSortingTest.java`](./src/test/java/bg/hristomanov/education/algorithms/sorting/ComparisonSortingTest.java) |
| Counting sort | [`CountingSort.java`](./src/main/java/bg/hristomanov/education/algorithms/sorting/linear/CountingSort.java) | [`LinearSortingTest.java`](./src/test/java/bg/hristomanov/education/algorithms/sorting/LinearSortingTest.java) |
| Radix sort | [`RadixSort.java`](./src/main/java/bg/hristomanov/education/algorithms/sorting/linear/RadixSort.java) | [`LinearSortingTest.java`](./src/test/java/bg/hristomanov/education/algorithms/sorting/LinearSortingTest.java) |

---

# 4. Linear Search

Linear search не прави assumption за ordering:

```text
[A][B][C][D][E]
 ↑
 compare
    ↑
    compare
       ↑
       ...
```

Worst case:

```text
target е последен
или
target липсва
→ n comparisons
→ O(n)
```

Това не го прави „лош“ алгоритъм.

Ако:

- input-ът е малък;
- имаме само едно търсене;
- data не е sorted;
- sorting би струвало повече от самото търсене;

linear search може да е правилният избор.

---

# 5. Binary Search

Binary search изисква search space, от който можем безопасно да изхвърлим половината.

Класическият случай е ascending sorted array:

```text
[1][4][7][9][13][20][25][31]

target = 20

middle = 9
20 > 9
→ discard left half
```

Следващата стъпка работи само върху останалата половина.

[`BinarySearch.java`](./src/main/java/bg/hristomanov/education/algorithms/searching/BinarySearch.java) използва:

```java
int middle = low + (high - low) / 2;
```

вместо naïve:

```java
(low + high) / 2
```

за да избегне integer-overflow risk при големи positive indices.

---

# 6. Защо O(log n)

Ако при всяка стъпка останалият search space се дели приблизително на 2:

```text
1024
→ 512
→ 256
→ 128
→ ...
→ 1
```

имаме около 10 стъпки.

[`SearchAlgorithmsTest.java`](./src/test/java/bg/hristomanov/education/algorithms/searching/SearchAlgorithmsTest.java) доказва:

```text
linear search over 1024 missing values
→ 1024 comparisons

binary search over same sorted space
→ <= 11 comparisons
```

---

# 7. Binary Search е по-общ от „търси число“

По-силният mental model е:

> Имам monotonic decision space — от някаква граница нататък condition-ът променя стойността си предвидимо.

Пример:

```text
capacity too small?  true true true true false false false
                                  ↑
                            boundary
```

Binary search може да търси boundary-то, не само exact value.

Този module показва exact-value и first-occurrence variants; binary-search-on-answer ще се върне като pattern по-късно.

---

# 8. Duplicates и first occurrence

При duplicates:

```text
[1][2][2][2][5]
```

намирането на произволен `2` не е същото като намиране на **първия** `2`.

[`BinarySearch.firstOccurrence(...)`](./src/main/java/bg/hristomanov/education/algorithms/searching/BinarySearch.java) не спира при първия match.

Вместо това:

```text
found
→ remember index
→ continue searching left half
```

Това е пример как малка промяна в requirement-а променя invariant-а на algorithm-а.

---

# 9. Bad / good: repeated lookup върху sorted каталог

Bad:

[`RepeatedLinearCatalogLookup.java`](./src/main/java/bg/hristomanov/education/algorithms/searching/bad/RepeatedLinearCatalogLookup.java)

```text
query 1 → scan
query 2 → scan
query 3 → scan
...
```

Good:

[`RepeatedBinaryCatalogLookup.java`](./src/main/java/bg/hristomanov/education/algorithms/searching/good/RepeatedBinaryCatalogLookup.java)

```text
already sorted catalog
→ binary search per query
```

[`RepeatedCatalogLookupTest.java`](./src/test/java/bg/hristomanov/education/algorithms/searching/RepeatedCatalogLookupTest.java) доказва еднакъв business result с много по-малко comparisons.

Важно:

> Ако catalog-ът НЕ беше sorted и имахме само една заявка, sorting + binary search може да е по-скъпо от един linear scan.

---

# 10. Sorting като preprocessing

Sorting струва work, но може да отвори по-евтини последващи операции:

```text
sort once
→ many binary searches
→ easier merge / grouping / range reasoning
```

Decision въпросът не е:

> Binary Search по-бърз ли е от Linear Search?

а:

> Общата цена на preprocessing + всички бъдещи операции по-добра ли е?

---

# 11. Comparison Sorting

Comparison sort научава order-а чрез въпроси от типа:

```text
A <= B ?
```

За arbitrary input permutations general comparison sorting има lower bound `Ω(n log n)`.

Интуицията е, че алгоритъмът трябва да различи между огромен брой възможни orderings, а всяко binary comparison дава ограничено количество информация.

---

# 12. Insertion Sort

Mental model:

```text
sorted prefix | unsorted rest
[A C F]       | [B E ...]

take B
→ shift larger values
→ insert B in correct position
```

[`InsertionSort.java`](./src/main/java/bg/hristomanov/education/algorithms/sorting/comparison/InsertionSort.java):

- stable;
- in-place;
- simple;
- добър за малки / nearly-sorted inputs;
- worst case `O(n²)`.

Тестът върху reverse input показва:

```text
100 values  → 4 950 comparisons
1000 values → 499 500 comparisons
```

---

# 13. Merge Sort

Mental model:

```text
divide
→ sort left
→ sort right
→ merge sorted halves
```

[`MergeSort.java`](./src/main/java/bg/hristomanov/education/algorithms/sorting/comparison/MergeSort.java):

- stable;
- worst-case `O(n log n)`;
- predictable;
- използва auxiliary buffer `O(n)`.

Това е класически time/memory trade-off.

---

# 14. Quick Sort

Mental model:

```text
choose pivot
→ partition smaller / larger values
→ recursively sort partitions
```

[`QuickSort.java`](./src/main/java/bg/hristomanov/education/algorithms/sorting/comparison/QuickSort.java) е compact educational implementation.

Практически свойства:

- average `O(n log n)`;
- in-place-style partitioning;
- обикновено не е stable;
- poor pivot / pathological partitions могат да дадат `O(n²)`.

Следователно не казваме просто:

> Quick Sort = O(n log n).

Case-ът има значение.

---

# 15. Stability

Stable sort пази relative order-а на elements с equal key.

Пример:

```text
(A, score=10)
(B, score=10)

stable sort by score
→ A остава преди B
```

Това е важно при multi-stage sorting.

Пример:

```text
sort by name
then stable sort by department
```

може да запази secondary ordering-а в equal department groups.

---

# 16. In-place vs auxiliary memory

Не гледай само time complexity.

Пример:

```text
Merge Sort
→ O(n log n) time
→ O(n) extra buffer

Quick Sort
→ O(n log n) average
→ much smaller explicit auxiliary storage
→ but worse pathological case
```

Algorithm selection е trade-off, не класация.

---

# 17. Linear / Non-comparison Sorting

Как counting/radix могат да бъдат по-бързи от comparison lower bound?

Защото **не решават същия general problem само чрез comparisons**.

Използват допълнителна структура на keys.

---

# 18. Counting Sort

[`CountingSort.java`](./src/main/java/bg/hristomanov/education/algorithms/sorting/linear/CountingSort.java) приема:

```text
non-negative integer keys
known maxKey
```

Mental model:

```text
values
→ count frequency for each possible key
→ reconstruct sorted output
```

Complexity:

```text
O(n + k)
```

където `k` е размерът на key range-а.

Ако:

```text
n = 100
k = 1 000 000 000
```

този подход е ужасен.

Следователно `O(n + k)` не означава автоматично „по-добър“.

---

# 19. Radix Sort

[`RadixSort.java`](./src/main/java/bg/hristomanov/education/algorithms/sorting/linear/RadixSort.java) използва digit representation на non-negative integers.

Mental model:

```text
ones digit
→ stable distribution

tens digit
→ stable distribution

hundreds digit
→ ...
```

Тук key representation-ът позволява да избегнем arbitrary pairwise comparisons.

Нашата реализация нарочно ограничава input-а до non-negative integers, за да бъде assumption-ът видим и тестируем.

---

# 20. Comparison vs Linear Sorting

| Property | Comparison sorting | Counting / Radix style |
| --- | --- | --- |
| General comparable values | да | не |
| Learns order via comparisons | да | не |
| Typical efficient bound | `O(n log n)` | `O(n + k)` / digit-dependent |
| Input assumptions | по-слаби | по-силни |
| Extra memory | algorithm-dependent | често key-range / output buffers |
| Best use | general ordering | structured bounded keys |

---

# 21. Production examples

## Repeated read-mostly lookup

```text
sorted version IDs
→ binary search
```

## Ranking

```text
general objects
→ stable/general comparison sort
```

## Merge of already sorted data

Sorting/order can simplify downstream processing.

## Small bounded integer codes

```text
status / bucket IDs within tiny range
→ counting-like strategy may make sense
```

Но production Java почти винаги трябва първо да използва standard-library sorting/search utilities, освен ако имаме измерима причина за custom implementation.

---

# 22. Кога оптимизацията не си струва

Не sort-вай само за да използваш binary search, ако:

- имаш една единствена заявка;
- input-ът е малък;
- order-ът не може да се запази;
- sorting cost доминира;
- data се променя непрекъснато.

Не използвай counting/radix само защото asymptotic notation изглежда по-добре, ако key assumptions не пасват.

---

# 23. Как да стартираме тестовете

От repository root:

```bash
mvn -pl java/algorithms/04-searching-sorting -am test
```

От `java/algorithms`:

```bash
mvn -pl 04-searching-sorting test
```

---

# 24. Какво точно решихме

В началото имахме repeated searches върху вече sorted catalog.

Наивно:

```text
each query
→ linear scan
```

Използвахме ordering-а:

```text
each query
→ binary search
→ halve search space
```

След това разгледахме как самото sorting решение зависи от:

- input size;
- order characteristics;
- stability;
- memory;
- worst-case requirement;
- key representation.

Накрая показахме защо counting/radix могат да заобиколят comparison lower bound само като използват по-силни assumptions за keys.

---

# 25. Mental model за запомняне

1. **Binary search работи, когато search space-ът позволява безопасно да изхвърлим половината.**
2. **O(log n) search не прави sorting cost-а безплатен.**
3. **Sorting често е preprocessing, който купува по-евтини бъдещи операции.**
4. **Comparison sorts са general-purpose; linear sorts използват допълнителна структура на keys.**
5. **Избирай algorithm по assumptions и trade-offs, не само по Big-O.**

---

# 26. Как да разпозная този казус в code review

Търси:

- repeated linear search върху sorted data;
- sort inside repeatedly executed loop;
- binary search върху unsorted collection;
- incorrect low/high updates;
- off-by-one boundary bugs;
- quick sort, описан като guaranteed `O(n log n)`;
- counting sort с огромен sparse key range;
- stability requirement, който никой не е обсъдил;
- custom sort/search там, където JDK utility е достатъчен.

---

# 27. Практичен checklist

```text
[ ] Данните sorted ли са или могат ли да бъдат подредени веднъж?
[ ] Колко searches ще има?
[ ] Каква е цената на preprocessing?
[ ] Search space-ът monotonic ли е?
[ ] Обработени ли са empty/missing/duplicate cases?
[ ] Има ли off-by-one risk?
[ ] Нужна ли е stable ordering?
[ ] Допустима ли е extra memory?
[ ] Worst-case guarantee важна ли е?
[ ] Има ли bounded integer key range?
[ ] Assumptions на linear sort реално валидни ли са?
[ ] Нужна ли е custom implementation изобщо?
```

---

# 28. Упражнения

1. Добави `lastOccurrence()` чрез binary search.
2. Добави `lowerBound()` / insertion-position variant.
3. Измери comparison count на insertion sort за sorted, reverse и random input.
4. Смени QuickSort pivot policy и наблюдавай pathological inputs.
5. Разшири CountingSort за signed bounded range чрез offset.
6. Обясни кога `Arrays.sort()` е правилният production избор вместо учебните implementations тук.

---

# Оригинални източници

## Searching

- Khan Academy — **Binary search**  
  https://www.khanacademy.org/computing/computer-science/algorithms/binary-search/a/binary-search
- Khan Academy — **Running time of binary search**  
  https://www.khanacademy.org/computing/computer-science/algorithms/binary-search/a/running-time-of-binary-search
- Khan Academy — **Measuring an algorithm's efficiency**  
  https://www.khanacademy.org/computing/ap-computer-science-principles/algorithms-101/evaluating-algorithms/a/measuring-an-algorithms-efficiency
- Computerphile — **Binary Search Algorithm**  
  https://www.youtube.com/watch?v=hDn8iOc30Tk
- Computerphile — **Bug in Binary Search**  
  https://www.youtube.com/watch?v=_eS-nNnkKfI

## Sorting

- MIT 6.006 — **Lecture 3: Sets and Sorting**  
  https://ocw.mit.edu/courses/6-006-introduction-to-algorithms-spring-2020/resources/lecture-3-sets-and-sorting/
- MIT 6.006 — **Lecture 5: Linear Sorting**  
  https://ocw.mit.edu/courses/6-006-introduction-to-algorithms-spring-2020/resources/lecture-5-linear-sorting/

## Overview

- freeCodeCamp — **Learn Data Structures and Algorithms Visually — Crash Course**  
  https://www.youtube.com/watch?v=RpLnQnurpLY

---

# Финална проверка

```text
[ ] Мога ли да обясня linear vs binary search?
[ ] Разбирам ли sorted/monotonic precondition-а?
[ ] Мога ли да обясня O(log n) чрез shrinking search space?
[ ] Разбирам ли duplicates / first occurrence?
[ ] Разбирам ли sorting като preprocessing?
[ ] Мога ли да сравня insertion / merge / quick sort trade-offs?
[ ] Разбирам ли stability и auxiliary memory?
[ ] Мога ли да обясня защо counting/radix могат да са linear-like?
[ ] Мога ли да кажа какви assumptions правят linear sorts?
```
