# 07. Graph Algorithms

Това е седмият модул от [`java/algorithms`](../README.md).

След [`06-graph-fundamentals`](../06-graph-fundamentals/README.md) вече знаем graph representation, BFS, DFS, visited state и unweighted shortest paths. Тук добавяме **weights** и се появяват няколко различни problem families:

```text
single-source shortest paths
├── non-negative weights → Dijkstra
└── negative weights     → Bellman-Ford

dynamic connectivity
→ Union-Find / DSU

minimum total network
→ Kruskal + Union-Find

all-pairs shortest paths
→ Johnson [deep dive]
```

Основната цел е да не избираме algorithm по име, а по **problem contract + edge constraints**.

---

# 1. Реалният казус: маршрут с цена, не само с брой стъпки

Имаме weighted routing graph:

```text
A --10--> D

A --2--> B --2--> C --2--> D
```

Ако използваме BFS mental model от предишната тема:

```text
A → D
```

е само **1 edge** и изглежда най-краткият path.

Но weighted cost е:

```text
A → D = 10

A → B → C → D
= 2 + 2 + 2
= 6
```

Следователно:

> minimum hops ≠ minimum weighted cost.

- Naive/wrong-for-this-requirement: [`FewestHopsRouter.java`](./src/main/java/bg/hristomanov/education/algorithms/graphalgorithms/routing/bad/FewestHopsRouter.java)
- Weighted solution: [`DijkstraShortestPaths.java`](./src/main/java/bg/hristomanov/education/algorithms/graphalgorithms/shortestpath/DijkstraShortestPaths.java)
- Proof: [`ShortestPathAlgorithmsTest.java`](./src/test/java/bg/hristomanov/education/algorithms/graphalgorithms/ShortestPathAlgorithmsTest.java)

Това е първият ключов transition:

```text
BFS
→ minimizes number of edges

weighted shortest-path algorithm
→ minimizes sum of weights
```

---

# 2. Какво ще научим

След модула трябва да можеш:

- да обясниш weighted path и path weight;
- да обясниш distance estimate и predecessor;
- да обясниш edge relaxation;
- да реконструираш shortest path;
- да обясниш защо Dijkstra изисква non-negative edges;
- да свържеш Dijkstra с Priority Queue / Binary Heap;
- да обясниш practical stale-entry strategy вместо decrease-key;
- да обясниш Bellman-Ford като repeated global relaxation;
- да различиш negative edge от negative cycle;
- да откриеш reachable negative cycle;
- да обясниш Union-Find / Disjoint Set Union;
- да обясниш union-by-size и path compression;
- да различиш shortest path от Minimum Spanning Tree;
- да обясниш Kruskal;
- да разбереш Johnson като Bellman-Ford preprocessing + reweighting + repeated Dijkstra;
- да избираш algorithm според graph assumptions.

---

# 3. Mental model: relaxation

За edge:

```text
u --w--> v
```

ако вече знаем добър path до `u`:

```text
dist[u]
```

получаваме candidate за `v`:

```text
dist[u] + w
```

Ако:

```text
dist[u] + w < dist[v]
```

можем да подобрим известния path до `v`.

Това е **edge relaxation** (опит да подобрим текущата оценка за distance чрез конкретна edge).

И Dijkstra, и Bellman-Ford правят relaxation.

Те се различават основно по:

> **В какъв ред relax-ваме edges и кога можем да считаме distance за окончателно?**

---

# README → код

| Концепция | Код | Доказателство |
| --- | --- | --- |
| Weighted directed graph | [`WeightedDirectedGraph.java`](./src/main/java/bg/hristomanov/education/algorithms/graphalgorithms/weighted/WeightedDirectedGraph.java) | shortest-path tests |
| Dijkstra | [`DijkstraShortestPaths.java`](./src/main/java/bg/hristomanov/education/algorithms/graphalgorithms/shortestpath/DijkstraShortestPaths.java) | [`ShortestPathAlgorithmsTest.java`](./src/test/java/bg/hristomanov/education/algorithms/graphalgorithms/ShortestPathAlgorithmsTest.java) |
| Bellman-Ford | [`BellmanFordShortestPaths.java`](./src/main/java/bg/hristomanov/education/algorithms/graphalgorithms/shortestpath/BellmanFordShortestPaths.java) | [`ShortestPathAlgorithmsTest.java`](./src/test/java/bg/hristomanov/education/algorithms/graphalgorithms/ShortestPathAlgorithmsTest.java) |
| Bad minimum-hop routing on weighted graph | [`FewestHopsRouter.java`](./src/main/java/bg/hristomanov/education/algorithms/graphalgorithms/routing/bad/FewestHopsRouter.java) | weighted route test |
| Union-Find | [`UnionFind.java`](./src/main/java/bg/hristomanov/education/algorithms/graphalgorithms/unionfind/UnionFind.java) | [`UnionFindAndKruskalTest.java`](./src/test/java/bg/hristomanov/education/algorithms/graphalgorithms/UnionFindAndKruskalTest.java) |
| Kruskal / MST | [`KruskalMinimumSpanningTree.java`](./src/main/java/bg/hristomanov/education/algorithms/graphalgorithms/mst/KruskalMinimumSpanningTree.java) | [`UnionFindAndKruskalTest.java`](./src/test/java/bg/hristomanov/education/algorithms/graphalgorithms/UnionFindAndKruskalTest.java) |
| Johnson APSP | [`JohnsonAllPairsShortestPaths.java`](./src/main/java/bg/hristomanov/education/algorithms/graphalgorithms/apsp/JohnsonAllPairsShortestPaths.java) | [`JohnsonAllPairsShortestPathsTest.java`](./src/test/java/bg/hristomanov/education/algorithms/graphalgorithms/JohnsonAllPairsShortestPathsTest.java) |

---

# 4. Weighted shortest paths

При unweighted graph:

```text
path cost
≈ number of edges
```

При weighted graph:

```text
path cost
= sum(edge weights)
```

Пример:

```text
A --4--> B
A --1--> C
C --2--> B
```

Direct path до B:

```text
4
```

Path през C:

```text
1 + 2 = 3
```

Relaxation на `C → B` подобрява:

```text
dist[B]: 4 → 3
```

---

# 5. Distance и path са различни outputs

Shortest-path algorithm може да даде:

```text
distance[A→D] = 6
```

но често искаме и actual route:

```text
A → B → C → D
```

Затова [`ShortestPathResult.java`](./src/main/java/bg/hristomanov/education/algorithms/graphalgorithms/shortestpath/ShortestPathResult.java) пази:

- `distances`;
- `predecessors`.

При successful relaxation:

```text
dist[v] = better candidate
predecessor[v] = u
```

После вървим backwards:

```text
target
→ predecessor
→ predecessor
→ source
```

и обръщаме path-а.

---

# 6. Dijkstra

Dijkstra работи за weighted graph с:

```text
all relevant edge weights >= 0
```

Mental model:

```text
source dist = 0
others = infinity

Priority Queue:
always take smallest tentative distance

poll u
→ relax outgoing edges of u
→ push improved candidates
→ repeat
```

[`DijkstraShortestPaths.java`](./src/main/java/bg/hristomanov/education/algorithms/graphalgorithms/shortestpath/DijkstraShortestPaths.java) използва Java `PriorityQueue`.

---

# 7. Защо non-negative weights са критични

Когато poll-нем най-малкия current candidate:

```text
u with distance d
```

при non-negative remaining edges няма бъдещ route през по-далечен vertex, който магически да върне distance под `d`.

Negative edge може да наруши това reasoning.

Затова implementation-ът **валидира input contract-а** и отказва да стартира, ако намери negative weight.

[`ShortestPathAlgorithmsTest.java`](./src/test/java/bg/hristomanov/education/algorithms/graphalgorithms/ShortestPathAlgorithmsTest.java) доказва това и показва същия graph през Bellman-Ford.

---

# 8. Priority Queue и stale entries

Теоретичните описания често използват decrease-key.

Java `PriorityQueue` няма директно decrease-key API.

Practical strategy:

```text
old:
(B, 10)

later:
better distance found
→ add (B, 7)

queue contains:
(B, 7)
(B, 10)
```

Когато старият entry `(B,10)` бъде poll-нат:

```text
10 != current dist[B]
→ stale
→ skip
```

Това е реализирано explicit-но и се брои в [`DijkstraMetrics.java`](./src/main/java/bg/hristomanov/education/algorithms/graphalgorithms/shortestpath/DijkstraMetrics.java).

---

# 9. Dijkstra complexity

С adjacency list + binary heap:

```text
roughly O((V + E) log V)
```

Често при connected graph ще видиш:

```text
O(E log V)
```

`log V` идва от Priority Queue operations.

Това е мястото, където темите ни се свързват:

```text
05 Binary Heap / Priority Queue
→ 07 Dijkstra frontier
```

---

# 10. Bellman-Ford

Bellman-Ford не finalize-ва greedy най-добрия vertex.

Mental model:

```text
relax ALL edges
→ again
→ again
→ ...
```

Ако няма relevant negative cycle, shortest simple path има най-много:

```text
V - 1 edges
```

затова максимум `V - 1` full passes са достатъчни.

[`BellmanFordShortestPaths.java`](./src/main/java/bg/hristomanov/education/algorithms/graphalgorithms/shortestpath/BellmanFordShortestPaths.java) има и early stop:

```text
whole pass changed nothing
→ done
```

---

# 11. Negative edge ≠ negative cycle

Negative edge:

```text
C → B = -10
```

може да е напълно валидна.

Negative cycle:

```text
A → B = 1
B → C = -3
C → A = 1

total = -1
```

може да бъде обхождан многократно:

```text
-1
-2
-3
...
```

Тогава finite shortest distance към засегнатата reachable region не съществува.

---

# 12. Negative-cycle detection

Bellman-Ford:

```text
V - 1 passes
→ distances should be settled if no negative cycle

one extra conceptual pass
→ if any reachable edge can still relax:
   negative cycle is reachable
```

[`BellmanFordResult.java`](./src/main/java/bg/hristomanov/education/algorithms/graphalgorithms/shortestpath/BellmanFordResult.java) expose-ва `reachableNegativeCycle`.

Важно:

> Negative cycle, който не е reachable от текущия source, не прави single-source result-а невалиден за този source.

Това е покрито с отделен тест.

---

# 13. Dijkstra vs Bellman-Ford

| Property | Dijkstra | Bellman-Ford |
| --- | --- | --- |
| Negative edges | не | да |
| Negative-cycle detection | не | да |
| Main strategy | greedy frontier | repeated all-edge relaxation |
| Key structure | Priority Queue | edge list |
| Typical complexity | около `E log V` | `VE` |
| Practical default | когато weights >= 0 | когато negative weights са допустими |

По-общият algorithm не е автоматично по-добрият.

---

# 14. Union-Find / Disjoint Set Union

Друг graph problem:

> Два елемента в един и същ connected component ли са?

Union-Find не пази graph traversal structure.

Той пази partition на elements в disjoint sets.

Основни operations:

```text
find(x)
→ representative/root на component-а

union(a,b)
→ merge components
```

[`UnionFind.java`](./src/main/java/bg/hristomanov/education/algorithms/graphalgorithms/unionfind/UnionFind.java) е generic implementation.

---

# 15. Union by size

Naive union може да направи:

```text
A → B → C → D → E
```

дълга parent chain.

Union-by-size attach-ва по-малкия component към по-големия root:

```text
smaller tree
→ larger tree
```

Това пази structure shallow.

---

# 16. Path compression

Първи `find(x)` може да мине:

```text
x → p1 → p2 → root
```

След compression:

```text
x → root
p1 → root
p2 → root
```

Комбинацията:

```text
union by size
+
path compression
```

дава practically almost-constant amortized operations.

Формално complexity често се описва чрез `α(n)` — inverse Ackermann function — но за practically sized inputs тя е изключително малка.

---

# 17. Minimum Spanning Tree

Имаме connected weighted **undirected** graph.

Искаме:

- да свържем всички vertices;
- без cycles;
- с `V - 1` edges;
- с minimum total selected weight.

Това е Minimum Spanning Tree (MST).

---

# 18. MST ≠ shortest-path tree

Това е една от най-важните разлики в темата.

Shortest paths:

> минимизирай route cost от source към target(s).

MST:

> минимизирай total cost на цялата свързваща network structure.

MST не гарантира shortest route между всяка двойка.

Dijkstra не решава MST.

Kruskal не решава shortest paths.

---

# 19. Kruskal

[`KruskalMinimumSpanningTree.java`](./src/main/java/bg/hristomanov/education/algorithms/graphalgorithms/mst/KruskalMinimumSpanningTree.java):

```text
sort edges ascending by weight

for each edge (u,v):
    if u and v already connected:
        skip
    else:
        select edge
        union(u,v)

stop after V - 1 selected edges
```

Union-Find отговаря на:

```text
ще създаде ли тази edge cycle?
```

ако endpoints вече са в един component.

---

# 20. Kruskal complexity

Dominant work:

```text
sort E edges
→ O(E log E)
```

Union-Find operations са почти constant amortized.

Затова practically sorting често доминира.

[`UnionFindAndKruskalTest.java`](./src/test/java/bg/hristomanov/education/algorithms/graphalgorithms/UnionFindAndKruskalTest.java) доказва:

- connectivity;
- spanning tree edge count;
- minimum total weight;
- disconnected graph → spanning forest, не MST.

---

# 21. Johnson — All-Pairs Shortest Paths [Deep Dive]

Single-source:

```text
one source
→ all targets
```

All-pairs:

```text
every source
→ every target
```

Наивно:

```text
run shortest-path algorithm from every vertex
```

Проблемът:

- Dijkstra е efficient, но не допуска negative edges;
- Bellman-Ford допуска negative edges, но е по-скъп.

Johnson комбинира силните им страни.

---

# 22. Johnson mental model

[`JohnsonAllPairsShortestPaths.java`](./src/main/java/bg/hristomanov/education/algorithms/graphalgorithms/apsp/JohnsonAllPairsShortestPaths.java):

```text
1. Bellman-Ford-style potentials
2. detect any negative cycle
3. reweight all edges to non-negative equivalents
4. run Dijkstra from every source
5. convert distances back
```

В кода super-source идеята е реализирана еквивалентно чрез:

```text
initial h(v) = 0 for every vertex
```

сякаш synthetic source има zero-weight edge към всеки vertex.

---

# 23. Reweighting

Johnson не просто „махва minus знаците“.

Reweight:

```text
w'(u,v) = w(u,v) + h(u) - h(v)
```

Потенциалите са избрани така, че:

- reweighted edges да са non-negative;
- path comparisons да останат еквивалентни след distance correction.

По path:

```text
h(intermediate)
```

термините се cancel-ват telescopically.

Остават само endpoint potentials.

Това е причината да можем после да преобразуваме reweighted distance обратно.

---

# 24. Johnson proof by comparison

[`JohnsonAllPairsShortestPathsTest.java`](./src/test/java/bg/hristomanov/education/algorithms/graphalgorithms/JohnsonAllPairsShortestPathsTest.java):

- използва negative edges;
- няма negative cycle;
- пуска Johnson;
- пуска Bellman-Ford от всеки source;
- сравнява **всяка source→target distance pair**.

Това е stronger proof от проверка на един конкретен route.

---

# 25. Decision guide

| Problem | Algorithm / structure |
| --- | --- |
| Unweighted minimum steps | BFS |
| Weighted, all weights >= 0 | Dijkstra |
| Weighted, negative edges possible | Bellman-Ford |
| Detect reachable negative cycle | Bellman-Ford |
| Repeated connectivity merge/query | Union-Find |
| Minimum total network cost | Kruskal + Union-Find |
| All-pairs, sparse graph, negative edges but no negative cycle | Johnson |

---

# 26. Production considerations

## Dijkstra

Внимавай за:

- negative weights;
- stale Priority Queue entries;
- huge frontier memory;
- overflow при accumulated cost;
- path reconstruction memory.

## Bellman-Ford

Внимавай за:

- `O(VE)` cost;
- negative-cycle semantics;
- unreachable components;
- edge ordering може да влияе на броя passes до early stop, но не на correctness.

## Union-Find

Внимавай за:

- dynamic vertex creation policy;
- component identity vs business identity;
- unsupported delete/split operations.

Union-Find е excellent за merge/connectivity, но не е structure за arbitrary graph updates.

## MST

Внимавай:

- graph трябва да е undirected за стандартния MST problem;
- disconnected input дава forest;
- MST objective не е routing objective.

## Johnson

Това е deep-dive algorithm. В production първо провери дали problem-ът реално изисква all-pairs preprocessing.

---

# 27. Кога не си струва

Не използвай:

- Dijkstra върху unweighted graph, ако BFS е достатъчен;
- Bellman-Ford при гарантирано non-negative graph без друга причина;
- Union-Find, ако ти трябва actual path;
- Kruskal, ако problem-ът е source-to-target routing;
- Johnson за еднократен single-source query.

Algorithm selection трябва да намалява complexity на system-а, не само да показва по-сложна техника.

---

# 28. Как да стартираме тестовете

От repository root:

```bash
mvn -pl java/algorithms/07-graph-algorithms -am test
```

От `java/algorithms`:

```bash
mvn -pl 07-graph-algorithms test
```

---

# 29. Какво точно решихме

Първоначално имахме weighted routing problem и използвахме minimum-hop thinking.

Причината за грешния result беше:

```text
BFS objective
→ minimize edges

business objective
→ minimize sum of weights
```

Добавихме shortest-path relaxation и избрахме:

- Dijkstra за non-negative weights;
- Bellman-Ford за negative weights / cycle detection.

После разгледахме друг problem family:

```text
minimum total connectivity
≠
shortest route
```

и използвахме Union-Find + Kruskal.

Накрая deep dive-ът показа как Johnson reuse-ва Bellman-Ford и Dijkstra за APSP.

---

# 30. Mental model за запомняне

1. **Relaxation = опит да подобрим dist[v] чрез вече известен path до u.**
2. **Dijkstra може да finalize-ва greedy само при non-negative weights.**
3. **Bellman-Ford печели generality чрез repeated edge relaxation и по-висока цена.**
4. **Union-Find решава connectivity, не paths.**
5. **MST минимизира total network cost, не source-to-target distance.**
6. **Johnson = Bellman-Ford potentials + reweighting + repeated Dijkstra.**

---

# 31. Как да разпозная казуса в code review

Търси:

- BFS върху graph, в който weights имат business значение;
- Dijkstra без validation на negative weights;
- Priority Queue implementation, която предполага decrease-key без такава operation;
- stale entries, които не се филтрират;
- Bellman-Ford без negative-cycle check;
- connectivity query, реализиран с repeated full graph traversal;
- Kruskal, използван като shortest-path algorithm;
- MST, интерпретиран като shortest-path guarantee;
- Union-Find, използван за path reconstruction;
- APSP preprocessing за problem, който има само един source query.

---

# 32. Практичен checklist

```text
[ ] Какво точно минимизирам: hops, weighted path или total network cost?
[ ] Single-source или all-pairs?
[ ] Има ли negative edges?
[ ] Има ли възможен negative cycle?
[ ] Ако използвам Dijkstra, всички weights >= 0 ли са?
[ ] Как пазя predecessor/path?
[ ] Priority Queue implementation има ли decrease-key или използвам stale entries?
[ ] Ако ми трябва само connectivity, нужен ли е Union-Find?
[ ] MST ли решавам или shortest path?
[ ] Graph-ът connected ли е?
[ ] Directed или undirected е problem contract-ът?
[ ] Deep-dive APSP наистина ли е оправдан?
```

---

# 33. Упражнения

1. Добави explicit negative-cycle vertices reporting към Bellman-Ford.
2. Добави early-exit Dijkstra, когато target бъде settled.
3. Сравни stale-entry Dijkstra с custom indexed priority queue.
4. Добави union-by-rank variant и сравни с union-by-size.
5. Добави Prim MST и сравни problem/result с Kruskal.
6. Добави path reconstruction към Johnson.
7. Направи routing example, в който MST path е по-скъп от Dijkstra shortest path.

---

# Оригинални източници

## MIT 6.006 — Introduction to Algorithms, Spring 2020

- **Lecture 11 — Weighted Shortest Paths**  
  https://ocw.mit.edu/courses/6-006-introduction-to-algorithms-spring-2020/resources/lecture-11-weighted-shortest-paths/
- **Lecture 12 — Bellman-Ford**  
  https://ocw.mit.edu/courses/6-006-introduction-to-algorithms-spring-2020/resources/lecture-12-bellman-ford/
- **Lecture 13 — Dijkstra**  
  https://ocw.mit.edu/courses/6-006-introduction-to-algorithms-spring-2020/resources/lecture-13-dijkstra/index.html
- **Lecture 14 — APSP and Johnson**  
  https://ocw.mit.edu/courses/6-006-introduction-to-algorithms-spring-2020/resources/lecture-14-apsp-and-johnson/

## William Fiset — Union-Find

- **Union Find Introduction**  
  https://www.youtube.com/watch?v=ibjEGG7ylHk
- **Union Find - Union and Find Operations**  
  https://www.youtube.com/watch?v=0jNmHPfA_yE
- **Union Find Path Compression**  
  https://www.youtube.com/watch?v=VHRhJWacxis
- **Union Find Kruskal's Algorithm**  
  https://www.youtube.com/watch?v=JZBQLXgSGfs
- **Union Find Code**  
  https://www.youtube.com/watch?v=KbFlZYCpONw

## Java mapping reference

- Java 25 `PriorityQueue` API:  
  https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/PriorityQueue.html

---

# Финална проверка

```text
[ ] Разбирам ли edge relaxation?
[ ] Мога ли да обясня Dijkstra invariant-а?
[ ] Знам ли защо negative edges са проблем за Dijkstra?
[ ] Разбирам ли Bellman-Ford V-1 passes?
[ ] Различавам ли negative edge от negative cycle?
[ ] Мога ли да обясня find / union / path compression?
[ ] Знам ли как Kruskal използва Union-Find?
[ ] Разграничавам ли MST от shortest path?
[ ] Разбирам ли Johnson reweighting mental model-а?
[ ] Мога ли да избера algorithm от problem constraints?
```
