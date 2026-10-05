# Data Structures & Algorithms — roadmap

Roadmap-ът е подробна карта на знанията. Номерацията съвпада с NotebookLM curriculum-а и служи за ориентация, но **не диктува 1:1 Maven modules или Studio artifacts**.

## 1. Algorithms & Complexity

### 1.1 Foundations — Algorithms, Data Structures & ADT
- computational problem;
- algorithm and correctness;
- Abstract Data Type;
- data structure vs implementation;
- time/memory trade-offs.

### 1.2 Complexity & Big-O
- input size;
- time and space complexity;
- O(1), O(log n), O(n), O(n log n), O(n²), exponential growth;
- Big-O, Big-Theta, Big-Omega;
- best / average / worst case;
- independent input sizes `n` and `m`;
- recursion stack;
- amortized complexity;
- Big-O vs real runtime performance.

## 2. Linear Data Structures

### 2.1 Arrays & Dynamic Arrays
- contiguous memory mental model;
- indexed access;
- size vs capacity;
- resize / copying;
- amortized append;
- insert/delete shifting;
- cache locality.

### 2.2 Linked Lists
- nodes and references;
- singly vs doubly linked;
- head / tail;
- traversal;
- insert/delete when a node is already known;
- cost of finding the node first;
- memory overhead and poor locality.

### 2.3 Stacks, Queues & Deques
- ADT vs concrete implementation;
- LIFO / FIFO / double-ended access;
- array/circular-array vs linked implementation;
- stack, queue and deque recognition signals;
- practical software/backend use cases.

## 3. Hashing

### 3.1 Hashing Fundamentals
- key → hash → bucket/index mental model;
- properties of a useful hash function;
- unavoidable collisions;
- separate chaining;
- capacity / load factor;
- average vs worst-case complexity;
- resize / rehash.

### 3.2 Open Addressing & Collision Strategies
- open addressing;
- probe sequence;
- linear probing and primary clustering;
- quadratic probing;
- double hashing;
- tombstones / deletion;
- load-factor sensitivity and locality trade-offs.

### 3.3 Hash Table Implementation Lab
- separate-chaining implementation;
- open-addressing implementation;
- deterministic collision keys;
- broken deletion demonstration;
- probe/collision metrics;
- resize/rehash tests.

## 4. Searching & Sorting

### 4.1 Searching Fundamentals
- linear search;
- sorted / monotonic search space;
- binary search;
- low / high / mid boundaries;
- missing values and duplicates;
- off-by-one and overflow-safe midpoint;
- preprocessing cost vs repeated searches.

### 4.2 Comparison Sorting
- why sorting is useful as preprocessing;
- insertion sort;
- merge sort;
- quick sort;
- stability;
- in-place vs extra memory;
- average vs worst case;
- why comparison sorting naturally trends toward O(n log n).

### 4.3 Linear Sorting
- counting sort;
- radix sort;
- key-domain assumptions;
- O(n + k) style complexity;
- why linear sorting is not a free replacement for comparison sorting.

## 5. Trees & Heaps

### 5.1 Trees & Binary Search Trees
- root / parent / child / leaf / subtree;
- depth and height;
- binary tree vs Binary Search Tree;
- BST ordering invariant;
- search / insert / remove;
- leaf / one-child / two-child removal;
- inorder / preorder / postorder / level-order traversals;
- complexity as O(h);
- skewed-tree degeneration.

### 5.2 Balanced Trees & AVL
- why balancing exists;
- balance factor;
- height maintenance;
- left / right rotations;
- LL / RR / LR / RL cases;
- rebalancing after insert;
- rebalancing after remove;
- preserving BST ordering while restoring logarithmic height.

### 5.3 Binary Heaps & Priority Queues
- Priority Queue as ADT;
- complete binary tree;
- min heap / max heap;
- array representation;
- parent/child index relationships;
- sift-up / bubble-up;
- sift-down / bubble-down;
- peek vs arbitrary search;
- scheduler / Top-K / next-best use cases;
- BST vs Heap decision signals.

## 6. Graph Fundamentals

### 6.1 Graphs & Breadth-First Search
- vertex / edge / path / cycle / degree;
- directed vs undirected;
- weighted vs unweighted;
- adjacency list vs adjacency matrix;
- sparse vs dense graph trade-offs;
- BFS with Queue;
- visited set;
- O(V + E) traversal with adjacency list;
- shortest path in unweighted graphs.

### 6.2 Depth-First Search & Traversal
- recursive DFS;
- iterative DFS with explicit Stack;
- visited tracking in cyclic graphs;
- connected components;
- path existence;
- directed cycle detection;
- recursion depth risk;
- BFS vs DFS decision signals.

## 7. Graph Algorithms

### 7.1 Weighted Shortest Paths
- path weight;
- single-source shortest paths;
- distance estimates;
- edge relaxation;
- predecessor/path reconstruction;
- shortest-path tree vs Minimum Spanning Tree;
- negative edges and negative-cycle intuition.

### 7.2 Dijkstra
- non-negative edge requirement;
- tentative vs settled distances;
- Priority Queue frontier;
- stale-entry strategy instead of decrease-key;
- O((V + E) log V) with adjacency-list + binary heap;
- path reconstruction.

### 7.3 Bellman-Ford
- repeated full-edge relaxation;
- why V - 1 passes are sufficient without relevant negative cycle;
- early stopping;
- reachable negative-cycle detection;
- O(VE) trade-off vs Dijkstra.

### 7.4 Union-Find & Minimum Spanning Trees
- Disjoint Set Union / Union-Find;
- find / union;
- union by size;
- path compression;
- connectivity queries;
- Kruskal;
- cycle prevention;
- MST vs shortest-path distinction.

### 7.5 APSP & Johnson [optional / deep dive]
- all-pairs shortest paths;
- Bellman-Ford potentials;
- edge reweighting;
- repeated Dijkstra;
- negative-cycle rejection;
- sparse-graph motivation.

## 8. Algorithmic Problem Solving

### 8.1 Common Problem-Solving Patterns
- hash lookup;
- two pointers;
- sliding window;
- prefix/suffix ideas;
- binary-search pattern;
- stack patterns;
- heap / Top-K;
- BFS / DFS;
- recursion / backtracking.

### 8.2 Dynamic Programming — Fundamentals
### 8.3 Dynamic Programming — Classic Patterns
### 8.4 Advanced Dynamic Programming [optional]

## Финална цел

```text
constraints
→ recognize problem shape
→ choose data structure / algorithm
→ reason about complexity
→ implement
→ prove behavior
```
