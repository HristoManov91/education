# 02. Linear Data Structures

Това е вторият модул от [`java/algorithms`](../README.md).

Темата покрива arrays, dynamic arrays, linked lists и Stack / Queue / Deque като Abstract Data Types (ADT). Целта не е да запаметяваме complexity таблица, а да свържем **memory layout → allowed operations → реална цена → правилен избор на структура**.

---

# 1. Реалният казус

Имаме backend компонент, който пази временен ordered snapshot от 1 000 елемента и периодично го чете по index:

```text
0, 1, 2, 3, ... n-1
```

На пръв поглед и dynamic array, и linked list могат да пазят поредица от стойности.

Но ако използваме linked list като random-access structure:

```text
for each index
→ traverse to node
→ read value
```

получаваме многократно traversal work.

Същевременно при друг problem shape — имаме **вече известен node handle** и искаме да го unlink-нем — linked list може да направи операцията без traversal.

Това е основният урок:

> Няма „по-добра структура“ по принцип. Има структура, която пасва на операциите на конкретния проблем.

Допълнително имаме task processing сценарии:

- FIFO → Queue;
- LIFO → Stack;
- работа от двата края → Deque.

Важно: Stack / Queue / Deque са **behavioral contracts**, а не задължително linked-list implementations.

---

# 2. Какво ще научим

След модула трябва да можеш:

- да обясниш contiguous memory mental model-а на array;
- да разграничиш size от capacity при dynamic array;
- да обясниш resize + copy и защо append може да бъде amortized O(1);
- да обясниш защо insert/delete в средата на array изисква shifting;
- да обясниш node/reference mental model-а на linked list;
- да разграничиш singly и doubly linked list;
- да обясниш кога linked-list insert/remove е O(1) и кога първо плащаме traversal;
- да разбереш memory/cache-locality trade-off-а;
- да разграничиш Stack, Queue и Deque като ADT-та;
- да разпознаеш LIFO / FIFO / double-ended problem shape;
- да разбереш как circular array може да реализира Deque;
- да избираш структура според операциите, а не според едно число от complexity table.

---

# 3. Голямата картина

```text
Array
→ contiguous elements
→ direct indexed access
→ expensive middle shifts

Dynamic Array
→ array + spare capacity
→ occasional resize/copy
→ amortized cheap append

Linked List
→ nodes + references
→ traversal for lookup
→ cheap unlink if node is already known

Stack
→ LIFO behavior

Queue
→ FIFO behavior

Deque
→ operations at both ends
→ can model Stack or Queue
```

---

# README → код

| Концепция | Production-like / educational code | Доказателство |
| --- | --- | --- |
| Dynamic array, capacity, resize, shifts | [`EducationalDynamicArray.java`](./src/main/java/bg/hristomanov/education/algorithms/linear/array/EducationalDynamicArray.java) | [`DynamicArrayTest.java`](./src/test/java/bg/hristomanov/education/algorithms/linear/DynamicArrayTest.java) |
| Doubly linked list + known-node removal | [`EducationalDoublyLinkedList.java`](./src/main/java/bg/hristomanov/education/algorithms/linear/list/EducationalDoublyLinkedList.java) | [`DoublyLinkedListTest.java`](./src/test/java/bg/hristomanov/education/algorithms/linear/DoublyLinkedListTest.java) |
| Queue / Stack / Deque върху circular array | [`CircularArrayDeque.java`](./src/main/java/bg/hristomanov/education/algorithms/linear/deque/CircularArrayDeque.java) | [`CircularArrayDequeTest.java`](./src/test/java/bg/hristomanov/education/algorithms/linear/CircularArrayDequeTest.java) |
| Bad indexed reads върху linked list | [`LinkedListIndexedSnapshot.java`](./src/main/java/bg/hristomanov/education/algorithms/linear/bad/LinkedListIndexedSnapshot.java) | [`IndexedSnapshotTradeOffTest.java`](./src/test/java/bg/hristomanov/education/algorithms/linear/IndexedSnapshotTradeOffTest.java) |
| Good indexed reads върху dynamic array | [`DynamicArrayIndexedSnapshot.java`](./src/main/java/bg/hristomanov/education/algorithms/linear/good/DynamicArrayIndexedSnapshot.java) | [`IndexedSnapshotTradeOffTest.java`](./src/test/java/bg/hristomanov/education/algorithms/linear/IndexedSnapshotTradeOffTest.java) |

---

# 4. Arrays

Array пази елементите логически един до друг.

Mental model:

```text
index:   0    1    2    3
       +----+----+----+----+
value: | A  | B  | C  | D  |
       +----+----+----+----+
```

Когато знаем index, можем директно да изчислим къде се намира елементът.

Затова indexed access е `O(1)`.

## Trade-off

Това, което е чудесно за random access, прави middle insert/delete по-скъп:

```text
[A][B][C][D]

insert X at index 1

[A][ ][B][C][D]
    ↑ shift B,C,D
```

Трябва да преместим част от останалите елементи → `O(n)` worst case.

---

# 5. Dynamic Arrays

Dynamic array не е „array, който магически расте“.

Той има:

```text
size     = колко елемента реално имаме
capacity = колко места са резервирани
```

Пример:

```text
size = 3
capacity = 4

[A][B][C][ ]
```

Когато capacity се запълни:

```text
allocate larger array
→ copy old elements
→ append new element
```

[`EducationalDynamicArray.java`](./src/main/java/bg/hristomanov/education/algorithms/linear/array/EducationalDynamicArray.java) използва doubling strategy, за да направи този механизъм видим.

## Защо append е amortized O(1)

Един resize може да бъде `O(n)`.

Но resize не се случва при всеки append.

При capacity:

```text
2 → 4 → 8 → 16 → 32 ...
```

общото copy work през много append-и остава linear спрямо броя добавени елементи.

Това е **amortized complexity** — скъпите редки операции се разпределят върху много евтини операции.

---

# 6. Cache locality

Array-based structure има добра **cache locality** (данните са компактно разположени и CPU cache по-лесно зарежда съседни елементи).

Това е причината complexity table да не е цялата история.

Две операции могат да имат еднаква Big-O категория, но реалният хардуер да предпочете структура с по-добър memory layout.

---

# 7. Linked Lists

Linked list пази nodes:

```text
[A] → [B] → [C] → [D]
```

При doubly linked list:

```text
null ← [A] ⇄ [B] ⇄ [C] ⇄ [D] → null
```

Всеки node има value + references.

Това означава:

- няма direct indexed access;
- traversal следва references node по node;
- има допълнителен memory overhead;
- locality обикновено е по-лоша от array.

---

# 8. Кога Linked List delete е O(1)

Тук има честа грешка.

Твърдението:

> „Linked list delete е O(1).“

е вярно **само ако вече имаме reference към node-а**.

[`EducationalDoublyLinkedList.remove(Node)`](./src/main/java/bg/hristomanov/education/algorithms/linear/list/EducationalDoublyLinkedList.java) не прави traversal.

Но ако имаме само:

```text
index = 700
```

или:

```text
value = customerId
```

първо трябва да намерим node-а.

Това може да е `O(n)`.

Следователно реалният flow често е:

```text
find node O(n)
+
unlink O(1)
→ overall O(n)
```

---

# 9. Bad вариант: Linked List като random-access structure

[`LinkedListIndexedSnapshot.java`](./src/main/java/bg/hristomanov/education/algorithms/linear/bad/LinkedListIndexedSnapshot.java):

```java
for (int i = 0; i < source.size(); i++) {
    values.add(source.get(i));
}
```

Изглежда безобидно.

Но `get(i)` върху linked structure означава traversal.

Повтарянето за всички индекси създава quadratic-shaped work.

[`IndexedSnapshotTradeOffTest.java`](./src/test/java/bg/hristomanov/education/algorithms/linear/IndexedSnapshotTradeOffTest.java) използва 1 000 елемента и доказва:

```text
dynamic array indexed snapshot:
~1 000 logical accesses

linked-list indexed snapshot:
>200 000 logical access/traversal steps
```

Business резултатът е един и същ. Access shape-ът не е.

---

# 10. Good вариант: Dynamic Array за indexed snapshot

[`DynamicArrayIndexedSnapshot.java`](./src/main/java/bg/hristomanov/education/algorithms/linear/good/DynamicArrayIndexedSnapshot.java) използва direct indexed access.

За `n` елемента:

```text
n indexed reads
→ O(n)
```

Това е пример как **choice of data structure** може да промени algorithmic cost без да променя бизнес семантиката.

---

# 11. Stack

Stack е LIFO:

```text
Last In
First Out
```

Типични операции:

- push;
- pop;
- peek.

Типични problem shapes:

- undo;
- nested parsing;
- DFS;
- backtracking;
- call-stack-like behavior.

Stack не означава „linked list“.

Може да бъде реализиран върху array/deque.

---

# 12. Queue

Queue е FIFO:

```text
First In
First Out
```

Типични операции:

- enqueue;
- dequeue;
- peek/front.

Типични problem shapes:

- work processing;
- buffering;
- producer/consumer;
- BFS;
- request/event pipelines.

---

# 13. Deque

Deque = **double-ended queue**.

Позволява операции и от двата края:

```text
addFirst
addLast
removeFirst
removeLast
```

Затова:

```text
Deque used from one end
→ Stack behavior

Deque addLast + removeFirst
→ Queue behavior
```

[`CircularArrayDeque.java`](./src/main/java/bg/hristomanov/education/algorithms/linear/deque/CircularArrayDeque.java) показва array-based implementation с circular indexing.

---

# 14. Circular array mental model

Вместо физически да местим всички елементи при removeFirst:

```text
physical array:
[ ][B][C][D]

head = 1
```

head просто се премества.

Когато стигнем края:

```text
... last index
→ wrap
→ index 0
```

Това е основната идея на circular buffer/deque.

---

# 15. Сравнение

| Structure / ADT | Indexed access | End operations | Middle insert/delete | Memory locality | Основна идея |
| --- | --- | --- | --- | --- | --- |
| Array | O(1) | fixed size | O(n) | excellent | compact indexed storage |
| Dynamic Array | O(1) | amortized O(1) append | O(n) | excellent | growable array |
| Linked List | O(n) | O(1) with head/tail | O(1) only with known node | weaker | nodes + references |
| Stack | not the point | push/pop | — | implementation-dependent | LIFO |
| Queue | not the point | enqueue/dequeue | — | implementation-dependent | FIFO |
| Deque | not the point | both ends | — | implementation-dependent | double-ended |

---

# 16. Как да избера

## Искам random/indexed reads

Първо мисли за array-based structure.

## Искам append-heavy sequence

Dynamic array често е добър default.

## Искам unlink на вече известен node

Linked structure може да е естествена.

## Искам FIFO

Queue.

## Искам LIFO

Stack.

## Искам и двата края

Deque.

---

# 17. Какво Java Collections ще покрие отделно

Тук учим data-structure concepts.

Отделната Java Collections тема ще разглежда:

- `ArrayList`;
- `LinkedList`;
- `ArrayDeque`;
- API contracts;
- JDK implementation details;
- iterators;
- `equals/hashCode`;
- `Comparable/Comparator`;
- concurrent collections.

Така не дублираме теорията.

---

# 18. Production considerations

## Array-based structures

Внимавай за:

- големи resize копирания;
- memory over-allocation;
- middle insert/delete;
- huge contiguous allocations.

## Linked structures

Внимавай за:

- node overhead;
- pointer/reference chasing;
- poor locality;
- accidental indexed access;
- assumption, че `O(1)` delete включва и намирането на node-а.

## Queue / Deque

Внимавай за:

- unbounded growth;
- producer faster than consumer;
- resource limits;
- queue length като symptom, не като solution.

---

# 19. Кога НЕ си струва custom структура

Production Java код почти винаги трябва да започва от standard library collection.

Нашите classes са **учебни implementations**, за да видим механизма.

Не пишем собствен `ArrayList` или `ArrayDeque` в production само защото вече знаем как работи.

---

# 20. Как да стартираме тестовете

От repository root:

```bash
mvn -pl java/algorithms/02-linear-data-structures -am test
```

От `java/algorithms`:

```bash
mvn -pl 02-linear-data-structures test
```

---

# 21. Какво точно решихме

В началото имахме indexed snapshot върху linked list:

```text
for each index
→ traverse nodes
→ repeated traversal
```

Променихме структурата към dynamic array:

```text
for each index
→ direct access
```

Получихме същия business result с различен access shape.

Отделно доказахме:

- resize/copy behavior на dynamic array;
- shifting при middle insert/delete;
- O(1)-подобно unlink поведение при already-known linked node;
- Stack / Queue / Deque semantics върху circular array.

---

# 22. Mental model за запомняне

1. **Array печели при indexed access и locality.**
2. **Dynamic array плаща рядък resize, за да има евтини append-и.**
3. **Linked-list O(1) insert/delete предполага, че node-ът вече е известен.**
4. **Stack / Queue / Deque са behaviors, не конкретни classes.**
5. **Избирай структура според операциите, които доминират workload-а.**

---

# 23. Как да разпозная казуса в code review

Търси:

- `get(i)` върху linked structure;
- repeated traversal;
- insert/delete complexity, цитирана без cost-а за намиране;
- queue, която расте без bound;
- stack implementation с legacy/неподходящ structure;
- unnecessary linked nodes при read-heavy workload;
- custom data structure там, където JDK collection е достатъчна.

---

# 24. Практичен checklist

```text
[ ] Трябва ли indexed/random access?
[ ] Кои операции са най-чести: read, append, insert, delete?
[ ] Ако linked delete е O(1), откъде идва node reference?
[ ] Важна ли е memory locality?
[ ] Има ли bounded capacity?
[ ] Нужна ли е FIFO, LIFO или работа от двата края?
[ ] Имаме ли queue backpressure/resource limit?
[ ] Има ли причина да не използваме standard Java collection?
[ ] Доказали ли сме semantics с тест?
```

---

# 25. Упражнения

1. Промени `EducationalDynamicArray` да расте с +1 вместо ×2 и измери copied elements.
2. Добави `remove(int index)` към linked list и сравни traversal + unlink.
3. Добави fixed-capacity mode към `CircularArrayDeque` и избери policy при overflow.
4. Имплементирай Stack adapter върху `CircularArrayDeque` без нова storage структура.
5. Обясни защо `LinkedList` не е автоматично добър избор за Queue само защото remove-at-head е евтин.

---

# Оригинални източници

## MIT 6.006 — Introduction to Algorithms, Spring 2020

- **Lecture 2 — Data Structures and Dynamic Arrays**  
  https://www.youtube.com/watch?v=CHhwJjR0mZA

## William Fiset — Data Structures

- **Dynamic and Static Arrays**  
  https://www.youtube.com/watch?v=PEnFFiQe1pM
- **Dynamic Array Code**  
  https://www.youtube.com/watch?v=tvw4v7FEF1w
- **Linked Lists Introduction**  
  https://www.youtube.com/watch?v=-Yn5DU0_-lw
- **Doubly Linked List Code**  
  https://www.youtube.com/watch?v=m-8ZBO2ywaU
- **Stack Introduction**  
  https://www.youtube.com/watch?v=L3ud3rXpIxA
- **Stack Implementation**  
  https://www.youtube.com/watch?v=RAMqDLI6_1c
- **Stack Code**  
  https://www.youtube.com/watch?v=oiZssCfk4_U
- **Queue Introduction**  
  https://www.youtube.com/watch?v=KxzhEQ-zpDc
- **Queue Implementation**  
  https://www.youtube.com/watch?v=EoisnPvUkOA
- **Queue Code**  
  https://www.youtube.com/watch?v=HV-hpvuGaC4
- Playlist:  
  https://www.youtube.com/playlist?list=PLDV1Zeh2NRsB6SWUrDFW2RmDotAfPbeHu

## Deque

- GeeksforGeeks — **Introduction to Deque**  
  https://www.geeksforgeeks.org/dsa/deque-data-structure/
- Jenny's Lectures — **DEQUE in Data Structure | Introduction to DEQue**  
  https://www.youtube.com/watch?v=pqg0SOPRlJ4

---

# Финална проверка

```text
[ ] Разбирам ли size vs capacity?
[ ] Мога ли да обясня resize и amortized append?
[ ] Разбирам ли shifting при array insert/delete?
[ ] Разбирам ли node/reference mental model-а?
[ ] Знам ли кога linked remove е O(1) и кога не?
[ ] Разбирам ли locality trade-off-а?
[ ] Разграничавам ли Stack, Queue и Deque като ADT?
[ ] Мога ли да избера структура според workload operations?
```
