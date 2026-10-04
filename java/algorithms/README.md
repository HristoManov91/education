# Data Structures & Algorithms — Java 25

Това е главният учебен проект за **структури от данни, алгоритми и problem-solving patterns** в `education`.

Целта не е repository от случайни LeetCode решения и не е запаметяване на готови шаблони. Искаме да можем да минем през:

```text
problem / constraints
→ input size
→ naive solution
→ complexity bottleneck
→ data-structure / algorithm choice
→ invariant / mental model
→ Java implementation
→ tests / proof
→ trade-offs
```

Кодът е на Java 25. По-големите теми са малки Maven modules с README, production-like `bad` / `good` примери и тестове.

## Roadmap

Подробната карта е в [`ROADMAP.md`](./ROADMAP.md).

Roadmap-ът е **checklist какво трябва да научим**, а не означава, че всяка подточка ще бъде отделен Maven module или отделна Notebook лекция.

## Модули

### 01. Algorithmic Thinking & Complexity

- [`01-algorithmic-thinking-complexity`](./01-algorithmic-thinking-complexity/README.md)

Покрива computational problem, algorithm, ADT, data structure, time/space complexity, growth rates, Big-O/Θ/Ω, multiple input sizes и amortized complexity.

### 02. Linear Data Structures

- [`02-linear-data-structures`](./02-linear-data-structures/README.md)

Покрива arrays, dynamic arrays, linked lists, stack/queue/deque ADT-та, memory layout, resize/copy cost, traversal, cache locality и избора на структура според операциите.

### 03. Hashing

- [`03-hashing`](./03-hashing/README.md)

Покрива hash functions, buckets, collisions, separate chaining, open addressing, linear/quadratic probing, double hashing, load factor, resize/rehash и tombstone deletion.

### 04. Searching & Sorting

- [`04-searching-sorting`](./04-searching-sorting/README.md)

Покрива linear search, binary search, comparison sorting, linear/non-comparison sorting, stability, memory trade-offs и input assumptions.

### 05. Trees & Heaps

- [`05-trees-heaps`](./05-trees-heaps/README.md)

Покрива binary trees, BST operations и traversals, AVL balancing/rotations, binary heaps, Priority Queue semantics и избора между ordered lookup и repeated min/max extraction.

### 06. Graph Fundamentals

- [`06-graph-fundamentals`](./06-graph-fundamentals/README.md)

Покрива graph terminology, adjacency list/matrix, BFS, DFS, visited tracking, unweighted shortest path, cycle detection и connected components.

### 07. Graph Algorithms

- [`07-graph-algorithms`](./07-graph-algorithms/README.md)

Покрива weighted shortest paths, relaxation, Dijkstra, Bellman-Ford, negative cycles, Union-Find, Kruskal/MST и Johnson APSP като deep dive.

Следващите модули ще се добавят **само когато реално стигнем до тях**, вместо предварително да създаваме празни директории.

## Как ще учим всяка тема

```text
Notebook / videos / authoritative docs
→ mental model
→ реален backend-like казус
→ naive implementation
→ complexity / correctness proof
→ improved implementation
→ tests
→ revision checklist
```

### Implementation labs

Не имплементираме структура от данни от нулата само заради упражнението. Правим собствена implementation, когато тя доказва важна идея под капака — например resize при dynamic array, collision resolution при hash table или heap invariant.

### Java Collections

Algorithmic темите могат да използват `ArrayList`, `HashSet`, `PriorityQueue` и други standard collections като реални представители на концепция, но Java-specific API и JDK implementation details ще се разглеждат отделно в `java/collections`.

## Оригинални източници

Източниците се описват локално във всеки модул, за да може конкретната тема да се чете и обновява самостоятелно.
