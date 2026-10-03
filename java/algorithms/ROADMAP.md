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
### 4.2 Comparison Sorting
### 4.3 Linear Sorting

## 5. Trees & Heaps
### 5.1 Trees & Binary Search Trees
### 5.2 Balanced Trees & AVL
### 5.3 Binary Heaps & Priority Queues

## 6. Graph Fundamentals
### 6.1 Graphs & Breadth-First Search
### 6.2 Depth-First Search & Traversal

## 7. Graph Algorithms
### 7.1 Weighted Shortest Paths
### 7.2 Dijkstra
### 7.3 Bellman-Ford
### 7.4 Union-Find & Minimum Spanning Trees
### 7.5 APSP & Johnson [optional / deep dive]

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
