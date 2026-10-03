# 06. Graph Fundamentals

Това е шестият модул от [`java/algorithms`](../README.md).

Темата изгражда основата за graph algorithms, които ще дойдат след това: weighted shortest paths, Dijkstra, Bellman-Ford, Union-Find и MST.

Основният mental model е:

```text
objects / states
→ vertices

relationships / transitions
→ edges

traversal
→ systematic exploration of the graph
```

Целта не е да запомним BFS/DFS templates. Искаме да можем да разпознаем **кога проблемът е graph problem**, как representation choice влияе на cost-а и защо BFS/DFS имат различно поведение.

---

# 1. Реалният казус: зависимости между backend компоненти

Имаме dependency graph:

```text
api
→ billing
→ database
→ api

billing
→ audit
```

Това не е list и не е tree:

- един component може да има няколко dependencies;
- dependency може да се споделя;
- могат да съществуват cycles;
- няма задължително един root;
- graph-ът може да има disconnected groups.

Наивен recursive traversal:

```text
visit node
→ recurse into every dependency
```

изглежда логично, но при cycle:

```text
api → billing → database → api → ...
```

няма естествен край.

Това е фундаменталната причина graph traversal почти винаги да има **visited state**.

- Bad: [`CycleBlindDependencyWalker.java`](./src/main/java/bg/hristomanov/education/algorithms/graphs/dependencies/bad/CycleBlindDependencyWalker.java)
- Good: [`VisitedDependencyWalker.java`](./src/main/java/bg/hristomanov/education/algorithms/graphs/dependencies/good/VisitedDependencyWalker.java)
- Proof: [`DependencyWalkerTest.java`](./src/test/java/bg/hristomanov/education/algorithms/graphs/DependencyWalkerTest.java)

---

# 2. Какво ще научим

След модула трябва да можеш:

- да обясниш vertex, edge, path, cycle, degree и connected component;
- да различиш directed / undirected graph;
- да различиш weighted / unweighted graph;
- да сравниш adjacency list и adjacency matrix;
- да обясниш sparse vs dense graph;
- да обясниш BFS като layer-by-layer traversal;
- да обясниш защо BFS използва Queue;
- да обясниш защо BFS намира shortest path в unweighted graph;
- да обясниш DFS като depth-first traversal;
- да свържеш recursive DFS с call stack и iterative DFS с explicit Stack;
- да обясниш visited tracking;
- да намираш connected components;
- да разпознаваш directed cycles;
- да анализираш traversal complexity като `O(V + E)` при adjacency list;
- да избираш BFS vs DFS според property-то на проблема, а не по шаблон.

---

# 3. Graph terminology

## Vertex / Node

Обект или state в graph-а.

Примери:

```text
service
user
city
workflow state
web page
build module
```

## Edge

Връзка между два vertices.

```text
A → B
```

може да означава:

- A depends on B;
- user A follows user B;
- state A can transition to B;
- service A calls service B.

## Directed graph

Edge има посока:

```text
A → B
```

не означава автоматично:

```text
B → A
```

Dependency graph обикновено е directed.

## Undirected graph

Връзката е двупосочна:

```text
A — B
```

Road/network-like relation може да е undirected, ако моделът го позволява.

## Weighted graph

Edge носи cost:

```text
A --5--> B
```

Например distance, latency, price.

Този модул работи основно с **unweighted graphs**. Weighted shortest paths са следваща тема.

---

# README → код

| Концепция | Код | Доказателство |
| --- | --- | --- |
| Adjacency List | [`AdjacencyListGraph.java`](./src/main/java/bg/hristomanov/education/algorithms/graphs/representation/AdjacencyListGraph.java) | [`GraphRepresentationTest.java`](./src/test/java/bg/hristomanov/education/algorithms/graphs/GraphRepresentationTest.java) |
| Adjacency Matrix | [`AdjacencyMatrixGraph.java`](./src/main/java/bg/hristomanov/education/algorithms/graphs/representation/AdjacencyMatrixGraph.java) | [`GraphRepresentationTest.java`](./src/test/java/bg/hristomanov/education/algorithms/graphs/GraphRepresentationTest.java) |
| BFS | [`BreadthFirstSearch.java`](./src/main/java/bg/hristomanov/education/algorithms/graphs/traversal/BreadthFirstSearch.java) | [`BreadthFirstSearchTest.java`](./src/test/java/bg/hristomanov/education/algorithms/graphs/BreadthFirstSearchTest.java) |
| DFS recursive/iterative | [`DepthFirstSearch.java`](./src/main/java/bg/hristomanov/education/algorithms/graphs/traversal/DepthFirstSearch.java) | [`DepthFirstSearchTest.java`](./src/test/java/bg/hristomanov/education/algorithms/graphs/DepthFirstSearchTest.java) |
| Bad cycle-blind dependency traversal | [`CycleBlindDependencyWalker.java`](./src/main/java/bg/hristomanov/education/algorithms/graphs/dependencies/bad/CycleBlindDependencyWalker.java) | [`DependencyWalkerTest.java`](./src/test/java/bg/hristomanov/education/algorithms/graphs/DependencyWalkerTest.java) |
| Good visited-aware traversal | [`VisitedDependencyWalker.java`](./src/main/java/bg/hristomanov/education/algorithms/graphs/dependencies/good/VisitedDependencyWalker.java) | [`DependencyWalkerTest.java`](./src/test/java/bg/hristomanov/education/algorithms/graphs/DependencyWalkerTest.java) |

---

# 4. Tree vs Graph

Tree е специален graph с допълнителни ограничения.

Типичен rooted tree:

- има root;
- всеки non-root node има точно един parent;
- няма cycles;
- има един unique path от root до всеки node.

General graph няма тези гаранции.

Пример:

```text
A → B
A → C
B → D
C → D
D → A
```

Тук:

- D има повече от един incoming path;
- има cycle;
- структурата не е tree.

---

# 5. Adjacency List

Mental model:

```text
A → [B, C]
B → [D]
C → [D]
D → []
```

Пазим само реално съществуващите edges.

[`AdjacencyListGraph.java`](./src/main/java/bg/hristomanov/education/algorithms/graphs/representation/AdjacencyListGraph.java) използва:

```text
vertex
→ neighbors
```

Това е естествен representation за **sparse graph** — graph с малко edges спрямо всички възможни двойки vertices.

Storage грубо е:

```text
O(V + E)
```

---

# 6. Adjacency Matrix

Mental model:

```text
      A  B  C  D
A     0  1  1  0
B     0  0  0  1
C     0  0  0  1
D     0  0  0  0
```

Matrix cell:

```text
matrix[from][to]
```

казва директно дали edge съществува.

[`AdjacencyMatrixGraph.java`](./src/main/java/bg/hristomanov/education/algorithms/graphs/representation/AdjacencyMatrixGraph.java) демонстрира trade-off-а:

```text
edge lookup
→ O(1)

storage
→ O(V²)
```

[`GraphRepresentationTest.java`](./src/test/java/bg/hristomanov/education/algorithms/graphs/GraphRepresentationTest.java) прави sparse graph с:

```text
100 vertices
99 directed edges
```

Adjacency list пази:

```text
99 edge references
```

Matrix резервира:

```text
100 × 100 = 10 000 cells
```

---

# 7. Representation choice влияе на algorithm cost

Твърдението:

> BFS е O(V + E).

предполага adjacency-list-like representation, при което iterate-ваме само реалните neighbors.

При adjacency matrix, за да намерим neighbors на един vertex, често трябва да scan-нем целия row:

```text
V cells per vertex
→ O(V²)
```

Следователно complexity не принадлежи само на „алгоритъма“. Тя зависи и от representation-а.

---

# 8. Breadth-First Search

BFS обхожда layer by layer.

Пример:

```text
        A
      /   \
     B     C
    / \     \
   D   E     F
```

Order:

```text
A
→ B, C
→ D, E, F
```

Основната data structure е **Queue**.

[`BreadthFirstSearch.java`](./src/main/java/bg/hristomanov/education/algorithms/graphs/traversal/BreadthFirstSearch.java):

```text
discover
→ mark visited
→ enqueue

dequeue
→ inspect neighbors
```

---

# 9. Защо mark visited при discovery

Представи си:

```text
A → B
A → C
B → D
C → D
```

Ако D се mark-не visited чак когато бъде dequeue-нат:

```text
B discovers D → enqueue D
C discovers D → enqueue D again
```

Затова good BFS обикновено mark-ва visited **когато discover-не node-а**.

Това гарантира, че всеки vertex влиза в queue най-много веднъж.

---

# 10. BFS shortest path в unweighted graph

BFS посещава:

```text
distance 0
→ distance 1
→ distance 2
→ distance 3
```

Следователно първото discovery на target е по path с минимален брой edges.

[`BreadthFirstSearch.shortestPath(...)`](./src/main/java/bg/hristomanov/education/algorithms/graphs/traversal/BreadthFirstSearch.java) пази parent map:

```text
child → parent
```

и реконструира path обратно от target към start.

Важно:

> Това property е за unweighted graph или equal-weight edges.

При arbitrary weights ще ни трябват други algorithms.

---

# 11. BFS complexity

При adjacency list:

- всеки reachable vertex се processing-ва веднъж;
- всеки edge се inspect-ва bounded брой пъти.

Затова:

```text
O(V + E)
```

където:

- `V` = vertices;
- `E` = edges.

При undirected adjacency list logical edge обикновено се пази в двете посоки, но constant factor не променя asymptotic complexity.

---

# 12. Depth-First Search

DFS следва един branch възможно най-надълбоко:

```text
A
→ B
→ D
→ F
→ backtrack
→ C
→ E
```

После се връща назад, когато няма непосетен neighbor.

DFS може да се реализира чрез:

- recursion;
- explicit Stack.

И двата варианта са в [`DepthFirstSearch.java`](./src/main/java/bg/hristomanov/education/algorithms/graphs/traversal/DepthFirstSearch.java).

---

# 13. Recursive DFS

Recursion implicit-но използва call stack.

Mental model:

```text
dfs(A)
  dfs(B)
    dfs(D)
      dfs(F)
    return
  return
  dfs(C)
    ...
```

Предимство:

- кодът често е директен и близък до recursive definition-а.

Риск:

- много дълбок graph може да изчерпи Java call stack-а.

Това е practically important при graph, който може да съдържа path с десетки хиляди vertices.

---

# 14. Iterative DFS

Explicit stack:

```text
push start
while stack not empty:
    pop
    visit
    push neighbors
```

Тук stack ownership е в heap memory, не в Java call stack-а.

Traversal order може да се различава според реда, в който push-ваме neighbors.

[`DepthFirstSearch.traverseIterative(...)`](./src/main/java/bg/hristomanov/education/algorithms/graphs/traversal/DepthFirstSearch.java) обръща neighbor list-а преди push, за да демонстрира същия deterministic order като recursive варианта в теста.

---

# 15. Visited set

В tree traversal visited set често не е нужен, защото tree няма cycles и child има един parent.

В general graph:

```text
A → B → C → A
```

без visited:

```text
A
→ B
→ C
→ A
→ B
→ ...
```

[`CycleBlindDependencyWalker.java`](./src/main/java/bg/hristomanov/education/algorithms/graphs/dependencies/bad/CycleBlindDependencyWalker.java) е нарочно naive вариант.

Има `depthLimit` само за да не позволим учебният тест да стигне до `StackOverflowError`.

[`VisitedDependencyWalker.java`](./src/main/java/bg/hristomanov/education/algorithms/graphs/dependencies/good/VisitedDependencyWalker.java) пази един `visited` set за целия traversal.

---

# 16. Connected Components

Undirected graph може да има няколко независими groups:

```text
A — B — C

D — E

F
```

Algorithm:

```text
for each vertex:
    if not visited:
        run DFS
        one DFS = one connected component
```

[`DepthFirstSearch.connectedComponents(...)`](./src/main/java/bg/hristomanov/education/algorithms/graphs/traversal/DepthFirstSearch.java) връща component groups.

[`DepthFirstSearchTest.java`](./src/test/java/bg/hristomanov/education/algorithms/graphs/DepthFirstSearchTest.java) доказва три components.

---

# 17. Directed Cycle Detection

За directed graph само visited/unvisited не е достатъчно да различим:

- edge към вече напълно обработен node;
- edge обратно към ancestor в текущия DFS path.

Затова използваме three-state model:

```text
UNVISITED
→ VISITING
→ VISITED
```

Ако срещнем edge към `VISITING` node:

```text
back edge
→ directed cycle
```

[`DepthFirstSearch.hasDirectedCycle(...)`](./src/main/java/bg/hristomanov/education/algorithms/graphs/traversal/DepthFirstSearch.java) реализира точно този mental model.

---

# 18. BFS vs DFS

| Property | BFS | DFS |
| --- | --- | --- |
| Main working structure | Queue | Stack / recursion |
| Exploration | layer-by-layer | depth-first |
| Unweighted shortest path | да | не е guarantee |
| Connected components | може | може |
| Cycle exploration | може | може |
| Memory pressure | wide frontier може да е голям | deep path може да е голям |
| Natural fit | minimum steps / layers | structure / components / backtracking-like exploration |

Не използвай таблицата като абсолютна рецепта.

И двата са general graph traversal algorithms.

Въпросът е кое property ти трябва.

---

# 19. Bad / good dependency traversal

Bad:

```text
visit
→ recurse every neighbor
→ no visited state
```

При cycle work-ът се повтаря без естествен край.

Good:

```text
if already visited:
    stop this branch

mark visited
→ process
→ recurse neighbors
```

[`DependencyWalkerTest.java`](./src/test/java/bg/hristomanov/education/algorithms/graphs/DependencyWalkerTest.java) доказва:

- bad walker посещава `api` многократно;
- good walker обработва `api, billing, database, audit` точно по веднъж.

---

# 20. Реални software/backend graph problems

## Service dependencies

```text
service → downstream service
```

Въпроси:

- reachable ли е dependency?
- има ли cycle?
- кои services са засегнати?

## Workflow / state transitions

```text
state → allowed next state
```

BFS може да отговори:

> минимален брой transitions до target state?

## Permissions / ownership relations

```text
user/group/role → relationships
```

Traversal може да търси reachability.

## Build/module dependencies

DFS може да открива cycles и dependency structure.

---

# 21. Production considerations

Внимавай за:

- huge graphs;
- unbounded traversal;
- recursive DFS върху много дълбоки paths;
- graph mutation по време на traversal;
- неправилна directed/undirected semantics;
- duplicate edges;
- memory на visited/frontier;
- tenant/security boundary при traversing real domain relationships.

При distributed system graph traversal често data не е локално в memory. Ако всеки edge означава network/database call, algorithmic complexity е само част от реалната цена.

---

# 22. Кога graph abstraction не си струва

Не моделирай problem като graph само защото „има връзки“.

Ако структурата естествено е:

- simple list;
- strict tree;
- direct key lookup;
- relational query, която database-ът решава по-ясно;

graph abstraction може само да усложни решението.

---

# 23. Как да стартираме тестовете

От repository root:

```bash
mvn -pl java/algorithms/06-graph-fundamentals -am test
```

От `java/algorithms`:

```bash
mvn -pl 06-graph-fundamentals test
```

---

# 24. Какво точно решихме

В началото имахме cyclic dependency graph и naive recursive traversal без visited tracking.

Причината за проблема беше:

```text
general graph
→ може да има multiple paths и cycles
→ traversal без global visited state повтаря work или не завършва
```

Добавихме visited semantics:

```text
discover once
→ process once
→ traverse neighbors
```

След това изградихме двата основни traversal mental model-а:

```text
BFS
→ Queue
→ layers
→ unweighted shortest path

DFS
→ Stack / recursion
→ depth + backtracking
→ components / cycle structure
```

И доказахме, че representation choice променя storage и traversal cost-а.

---

# 25. Mental model за запомняне

1. **Graph = vertices + edges; tree е специален graph, не обратното.**
2. **Visited state е част от correctness при cyclic graphs.**
3. **BFS = Queue + layers; DFS = Stack/recursion + depth/backtracking.**
4. **BFS shortest path guarantee е за unweighted/equal-weight graph.**
5. **O(V + E) предполага representation, който iterate-ва реалните edges ефективно.**

---

# 26. Как да разпозная graph казус в code review

Търси:

- arbitrary relationships между entities;
- dependency traversal;
- multiple paths към един object/state;
- cycle possibility;
- queue/stack traversal over neighbors;
- recursion без visited state;
- BFS shortest-path claim върху weighted graph;
- adjacency matrix върху огромен sparse graph;
- recursive DFS върху potentially very deep input.

---

# 27. Практичен checklist

```text
[ ] Кои са vertices?
[ ] Кои са edges?
[ ] Directed или undirected е relation-ът?
[ ] Weighted или unweighted?
[ ] Sparse или dense е graph-ът?
[ ] Adjacency list или matrix е по-естествена?
[ ] Има ли cycles?
[ ] Къде живее visited state-ът?
[ ] Трябва ли shortest number of edges → BFS?
[ ] Трябва ли depth/structure/components → DFS?
[ ] Recursive DFS безопасен ли е за expected depth?
[ ] Complexity смята ли и E, а не само V?
```

---

# 28. Упражнения

1. Добави `distanceFromStart` map към BFS traversal.
2. Добави iterative path-existence DFS.
3. Добави cycle detection за undirected graph с parent tracking.
4. Добави multi-source BFS с няколко стартови vertices.
5. Сравни BFS върху adjacency list и adjacency matrix чрез броене на inspected cells/edges.
6. Направи dependency graph от няколко реалистични backend modules и намери cycle.
7. Симулирай path с 100 000 vertices и сравни recursive срещу iterative DFS risk-а.

---

# Оригинални източници

## MIT 6.006 — Introduction to Algorithms, Spring 2020

- **Lecture 9 — Breadth-First Search**  
  https://ocw.mit.edu/courses/6-006-introduction-to-algorithms-spring-2020/resources/lecture-9-breadth-first-search/
- **Lecture 10 — Depth-First Search**  
  https://ocw.mit.edu/courses/6-006-introduction-to-algorithms-spring-2020/resources/lecture-10-depth-first-search/

## Visual overview

- freeCodeCamp — **Learn Data Structures and Algorithms Visually — Crash Course**  
  https://www.youtube.com/watch?v=RpLnQnurpLY

---

# Финална проверка

```text
[ ] Разграничавам ли graph от tree?
[ ] Знам ли vertex / edge / path / cycle / component?
[ ] Разбирам ли directed vs undirected?
[ ] Разбирам ли adjacency list vs adjacency matrix trade-off-а?
[ ] Мога ли да обясня BFS чрез Queue?
[ ] Мога ли да обясня DFS чрез Stack/recursion?
[ ] Знам ли защо visited е критично?
[ ] Разбирам ли O(V + E)?
[ ] Знам ли защо BFS shortest path е само за unweighted/equal-weight graph?
[ ] Мога ли да намеря connected components?
[ ] Разбирам ли directed cycle detection mental model-а?
```
