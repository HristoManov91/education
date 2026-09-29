# Data Structures & Algorithm Patterns — Java 25 roadmap

Този раздел е отделен от `design-patterns/`.

Причината е важна:

```text
Design pattern
→ структура на objects/components/responsibilities

Algorithm pattern
→ computational shape + data-structure/complexity решение
```

Основният входен материал е:

- freeCodeCamp / AlgoMonster — **Data Structure and Algorithm Patterns for LeetCode Interviews – Tutorial**
  https://www.youtube.com/watch?v=Z_c4byLrNBU

Видеото покрива arrays, strings, sets, Big O, hash maps, two pointers, sliding window, binary search, BFS, DFS, backtracking и priority queue/heap.

---

# Как ще учим algorithms

Не искаме repository от random LeetCode решения. За всеки pattern:

```text
problem shape
→ brute force
→ защо complexity-то не скалира
→ recognition signals
→ optimized invariant / mental model
→ Java 25 implementation
→ time/space complexity
→ edge cases
→ tests
→ кога pattern-ът НЕ е приложим
```

Целта е да разпознаем структурата на нов problem, преди да започнем да кодиране наизуст.

---

# Curriculum

## 1. Complexity fundamentals

- time/space complexity;
- amortized complexity;
- worst vs average case;
- recursion stack;
- защо nested loops не означава автоматично O(n²);
- реална цена на copying и allocations.

Big O е модел, не benchmark.

## 2. Hash Map / Frequency Map

Recognition signals: броене, membership, complement, grouping, deduplication.

Типичен преход:

```text
nested search O(n²)
→ remember previous values
→ HashMap O(n) average
```

## 3. Two Pointers

Подходящ при sorted data, pair relations, opposite ends, in-place compaction и fast/slow pointer problems.

## 4. Sliding Window

Recognition signals: subarray/substring, continuous region, longest/shortest/max/min при constraint.

Ще покрием fixed и dynamic windows:

```text
expand right
while invalid:
    shrink left
```

## 5. Binary Search

Не само lookup в sorted array. По-силният mental model е monotonic search space и binary search on answer.

## 6. BFS

Shortest path в unweighted graph, level-order traversal, minimum steps, spreading processes и multi-source BFS.

## 7. DFS

Tree/graph traversal, connected components, path exploration, recursive vs explicit stack и cycle/visited semantics.

## 8. Backtracking

```text
choose
→ explore
→ undo
→ choose next
```

Фокусът ще е върху pruning, а не memorization на recursion template.

## 9. Priority Queue / Heap

Top-K, next-best candidate, schedulers, merge sorted streams и bounded heap.

## 10. Pattern combinations

```text
Sliding Window + HashMap
BFS + HashSet
Heap + HashMap
Binary Search + feasibility check
DFS + Backtracking
```

---

# Proposed structure

```text
java/algorithms/
├── README.md
├── COMPLEXITY.md
├── PATTERN-RECOGNITION.md
├── arrays-hashmaps/
├── two-pointers/
├── sliding-window/
├── binary-search/
├── bfs-dfs/
├── backtracking/
└── heaps/
```

Ще бъдат малки Java 25 labs с unit tests, не Spring Boot приложения.

---

# Връзка с backend curriculum

```text
Specification → query/design composition
Sliding Window → algorithmic optimization
Outbox → distributed consistency
Heap → data-structure choice
```

Това са различни категории знания и затова ги държим отделени.

---

# Anti-pattern: memorizing solutions

Не искаме:

```text
problem title → remember code
```

Искаме:

```text
constraints
→ recognize shape
→ derive invariant
→ choose data structure
→ derive complexity
→ implement
```

---

# Learning order

```text
1. Big O + arrays/strings/hash maps
2. Two Pointers
3. Sliding Window
4. Binary Search
5. BFS
6. DFS
7. Backtracking
8. Heap / Priority Queue
9. Combined-pattern problems
```

---

# Оригинални източници

- freeCodeCamp / AlgoMonster — Data Structure and Algorithm Patterns for LeetCode Interviews:
  https://www.youtube.com/watch?v=Z_c4byLrNBU
- Java Collections Framework documentation
- Java `PriorityQueue` API
- Допълнителни authoritative algorithm references ще се добавят при реализацията на конкретните labs.
