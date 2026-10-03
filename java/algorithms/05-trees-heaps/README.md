# 05. Trees & Heaps

Това е петият модул от [`java/algorithms`](../README.md).

Темата събира две tree-shaped families, които изглеждат сходни визуално, но решават **различни проблеми**:

```text
BST / AVL
→ пазим ordering relation
→ търсим / insert-ваме / delete-ваме по key
→ можем да получим sorted traversal

Binary Heap / Priority Queue
→ пазим само parent/child priority relation
→ многократно взимаме next-min / next-max
→ arbitrary lookup остава скъп
```

Основната цел е да не смесваме:

```text
Binary Tree
Binary Search Tree
Balanced BST / AVL
Binary Heap
Priority Queue
```

---

# 1. Реалните казуси

## Казус A: ordered dynamic index

Имаме динамичен набор от numeric keys и искаме:

- insert;
- lookup;
- delete;
- ordered traversal.

Ordinary BST изглежда естествено.

Но ако данните пристигат вече sorted:

```text
1, 2, 3, 4, 5, ...
```

получаваме:

```text
1
 \
  2
   \
    3
     \
      4
```

Tree-shaped структурата практически е станала linked list.

Следователно:

```text
search / insert / delete
→ O(h)
→ h може да стане n
→ O(n)
```

AVL купува по-сложен insert/delete, за да държи height около `O(log n)`.

## Казус B: priority scheduler

Имаме 1 000 задачи и многократно искаме:

> „Дай следващата задача с най-висок приоритет.“

Наивно можем да държим задачите в list:

```text
pollNext
→ scan all remaining tasks
→ choose best
```

Ако извадим всички задачи:

```text
(n-1) + (n-2) + ... + 1
→ O(n²) comparisons
```

Binary heap е data structure, проектирана точно за:

```text
insert
→ O(log n)

peek best
→ O(1)

extract best
→ O(log n)
```

Това е production-like problem shape за scheduler, event queue, Top-K и next-best processing.

---

# 2. Какво ще научим

След модула трябва да можеш:

- да разграничиш Binary Tree от Binary Search Tree;
- да обясниш root / parent / child / leaf / subtree / depth / height;
- да обясниш BST ordering invariant;
- да реализираш search / insert / remove;
- да обясниш leaf / one-child / two-child deletion;
- да различиш inorder / preorder / postorder / level-order;
- да обясниш защо BST operations са `O(h)`, а не автоматично `O(log n)`;
- да разбереш защо sorted inserts могат да дегенерират ordinary BST;
- да обясниш AVL balance factor и rotations;
- да разбереш LL / RR / LR / RL rebalancing;
- да обясниш защо AVL пази logarithmic height;
- да разграничиш Priority Queue ADT от Binary Heap implementation;
- да обясниш complete binary tree и array representation;
- да обясниш sift-up и sift-down;
- да разбереш защо arbitrary heap lookup е `O(n)`;
- да избираш BST/AVL срещу Heap/Priority Queue според problem shape.

---

# 3. Голямата картина

```text
Binary Tree
└── structural restriction:
    max 2 children

Binary Search Tree
└── Binary Tree + ordering invariant

AVL Tree
└── BST + height-balance invariant

Priority Queue
└── ADT: next item by priority

Binary Heap
└── common Priority Queue implementation
    + complete tree shape
    + local heap invariant
```

Критично правило:

> Tree shape сама по себе си не определя какви операции са efficient. Важен е invariant-ът, който структурата пази.

---

# README → код

| Концепция | Код | Доказателство |
| --- | --- | --- |
| BST search / insert / remove / traversals | [`IntBinarySearchTree.java`](./src/main/java/bg/hristomanov/education/algorithms/trees/bst/IntBinarySearchTree.java) | [`BinarySearchTreeTest.java`](./src/test/java/bg/hristomanov/education/algorithms/trees/BinarySearchTreeTest.java) |
| AVL balancing / rotations | [`IntAvlTree.java`](./src/main/java/bg/hristomanov/education/algorithms/trees/avl/IntAvlTree.java) | [`AvlTreeTest.java`](./src/test/java/bg/hristomanov/education/algorithms/trees/AvlTreeTest.java) |
| Binary Heap | [`BinaryHeap.java`](./src/main/java/bg/hristomanov/education/algorithms/trees/heap/BinaryHeap.java) | [`BinaryHeapTest.java`](./src/test/java/bg/hristomanov/education/algorithms/trees/BinaryHeapTest.java) |
| Naive priority scheduler | [`LinearScanTaskScheduler.java`](./src/main/java/bg/hristomanov/education/algorithms/trees/scheduler/bad/LinearScanTaskScheduler.java) | [`PrioritySchedulerTradeOffTest.java`](./src/test/java/bg/hristomanov/education/algorithms/trees/PrioritySchedulerTradeOffTest.java) |
| Heap-backed priority scheduler | [`HeapTaskScheduler.java`](./src/main/java/bg/hristomanov/education/algorithms/trees/scheduler/good/HeapTaskScheduler.java) | [`PrioritySchedulerTradeOffTest.java`](./src/test/java/bg/hristomanov/education/algorithms/trees/PrioritySchedulerTradeOffTest.java) |

---

# 4. Tree terminology

Пример:

```text
        8
      /   \
     3     12
    / \    / \
   1   6  10  14
```

- `8` е **root** (корен);
- `3` и `12` са children на `8`;
- `8` е parent на `3` и `12`;
- `1`, `6`, `10`, `14` са leaves;
- subtree на `3` съдържа `3,1,6`;
- depth измерва колко edges сме под root;
- height измерва най-дългия path надолу към leaf.

В нашите implementations:

```text
empty tree height = 0
leaf height       = 1
```

---

# 5. Binary Tree ≠ Binary Search Tree

Binary Tree казва само:

> Един node има най-много две children.

Не казва:

```text
left < node < right
```

BST добавя ordering invariant.

За уникални values в този модул:

```text
all values in left subtree  < node.value
all values in right subtree > node.value
```

Този invariant е причината да можем да изхвърляме цели subtrees при search.

---

# 6. BST search

При:

```text
target = 10

        8
      /   \
     3     12
          /
         10
```

стъпките са:

```text
10 > 8
→ go right

10 < 12
→ go left

10 == 10
→ found
```

Work-ът зависи от height:

```text
O(h)
```

Не от общия брой nodes директно.

---

# 7. BST insert

Insert следва същия search path, докато намери празно място.

[`IntBinarySearchTree.add(...)`](./src/main/java/bg/hristomanov/education/algorithms/trees/bst/IntBinarySearchTree.java) използва unique-values policy:

```text
duplicate
→ ignored
→ add() returns false
```

Това е policy на учебната implementation, не универсално правило за всички BST-та.

---

# 8. BST removal

Има три важни cases.

## Leaf

```text
  3
 /
1
```

remove `1`:

```text
parent link → null
```

## Един child

```text
14
/
13
```

remove `14`:

```text
parent link → 13
```

## Две children

Трябва да запазим BST ordering.

Един стандартен подход:

```text
successor = minimum in right subtree
→ copy successor value
→ remove successor from right subtree
```

Това е реализирано в [`IntBinarySearchTree.remove(...)`](./src/main/java/bg/hristomanov/education/algorithms/trees/bst/IntBinarySearchTree.java).

---

# 9. Traversals

## Inorder

```text
left
→ node
→ right
```

При BST дава sorted values.

## Preorder

```text
node
→ left
→ right
```

Полезен при parent-before-children processing и structural serialization ideas.

## Postorder

```text
left
→ right
→ node
```

Полезен, когато child work трябва да приключи преди parent work.

## Level-order

```text
level 0
→ level 1
→ level 2
→ ...
```

Използва queue и естествено ни свързва с бъдещия BFS module.

[`BinarySearchTreeTest.java`](./src/test/java/bg/hristomanov/education/algorithms/trees/BinarySearchTreeTest.java) доказва четирите order-а върху едно и също tree.

---

# 10. Защо ordinary BST може да деградира

Ако insert-ваме:

```text
1,2,3,4,5,...
```

BST invariant не е нарушен.

Но shape-ът става:

```text
1
 \
  2
   \
    3
     \
      ...
```

[`BinarySearchTreeTest.java`](./src/test/java/bg/hristomanov/education/algorithms/trees/BinarySearchTreeTest.java) insert-ва 1 000 sorted values и доказва:

```text
height = 1000
```

Следователно:

```text
search / insert / delete
→ O(h)
→ O(n) worst case
```

Твърдението:

> „BST operations са O(log n).“

е непълно без height/balance assumption.

---

# 11. AVL: защо съществува

AVL добавя още един invariant:

```text
за всеки node
|height(left) - height(right)| <= 1
```

Balance factor:

```text
height(left) - height(right)
```

Ако magnitude стане над 1, правим rotation.

Целта е:

```text
keep height O(log n)
→ keep search/insert/remove O(log n)
```

---

# 12. Rotation mental model

Rotation променя **shape-а**, но запазва sorted BST ordering.

Пример за right rotation:

```text
      30                  20
     /                   /  \
    20        →         10   30
   /
  10
```

Sorted order преди и след:

```text
10,20,30
```

не се променя.

Това е критичното нещо за запомняне.

---

# 13. LL / RR / LR / RL

## LL

Heavy path:

```text
left → left
```

→ right rotation.

## RR

```text
right → right
```

→ left rotation.

## LR

```text
left → right
```

→ left rotation върху child, после right rotation върху parent.

## RL

```text
right → left
```

→ right rotation върху child, после left rotation върху parent.

[`AvlTreeTest.java`](./src/test/java/bg/hristomanov/education/algorithms/trees/AvlTreeTest.java) проверява и четирите shapes и показва един и същ balanced root.

---

# 14. AVL insert и remove

Insert:

```text
normal BST insert
→ update height
→ detect imbalance
→ rotate
```

Remove:

```text
normal BST delete
→ subtree height може да намалее
→ update ancestors
→ може да има rebalancing нагоре
```

Deletion е по-сложен, защото height decrease може да се разпространи нагоре по ancestor chain-а.

[`IntAvlTree.java`](./src/main/java/bg/hristomanov/education/algorithms/trees/avl/IntAvlTree.java) пази stored height за всеки node и има `isValidAvl()` проверка за:

- BST ordering;
- правилни stored heights;
- balance condition.

---

# 15. Plain BST vs AVL: доказателството

[`AvlTreeTest.java`](./src/test/java/bg/hristomanov/education/algorithms/trees/AvlTreeTest.java) insert-ва последователно:

```text
1..1000
```

Plain BST:

```text
height = 1000
```

AVL:

```text
height <= 12
```

и запазва същия inorder sorted result.

Това е цената/ползата:

```text
more bookkeeping + rotations
→ bounded height
→ predictable logarithmic operations
```

---

# 16. Priority Queue е ADT

Priority Queue не означава „heap“ по дефиниция.

ADT contract-ът е:

```text
insert item with priority
peek best item
extract best item
```

Binary Heap е една много подходяща implementation.

В production Java типичният standard-library representative е `PriorityQueue`, но API/JDK details ще се разглеждат отделно в Java Collections.

---

# 17. Binary Heap

Binary Heap съчетава:

1. **complete binary tree shape**;
2. **local heap invariant**.

Min heap:

```text
parent <= children
```

Max heap:

```text
parent >= children
```

Важно:

> Heap-ът НЕ е globally sorted tree.

---

# 18. Complete tree → array representation

Complete tree запълва levels отляво надясно.

Това позволява compact array layout без explicit node references.

Пример:

```text
array:
[2, 5, 3, 9, 8, 7]

tree:

        2
      /   \
     5     3
    / \   /
   9   8 7
```

За zero-based indices:

```text
parent(i) = (i - 1) / 2
left(i)   = 2*i + 1
right(i)  = 2*i + 2
```

[`BinaryHeap.java`](./src/main/java/bg/hristomanov/education/algorithms/trees/heap/BinaryHeap.java) използва точно този layout.

---

# 19. Heap insert: sift-up

Insert:

```text
append at end
→ complete-tree shape remains valid
→ heap property may be broken
→ compare with parent
→ swap upward
→ repeat
```

Path length е bounded от tree height:

```text
O(log n)
```

---

# 20. Heap remove-root: sift-down

```text
take root
→ move last element to root
→ remove last slot
→ compare with best child
→ swap downward
→ repeat
```

Отново:

```text
O(log n)
```

Peek root:

```text
O(1)
```

---

# 21. Защо arbitrary search в Heap не е O(log n)

В min heap:

```text
parent <= children
```

Но между sibling/subtree nodes няма BST ordering.

Пример:

```text
        2
      /   \
     5     3
    / \   /
   9   8 7
```

Ако търсим `8`, не можем от root да кажем коя половина да изхвърлим.

[`BinaryHeap.containsLinear(...)`](./src/main/java/bg/hristomanov/education/algorithms/trees/heap/BinaryHeap.java) нарочно scan-ва array-а.

[`BinaryHeapTest.java`](./src/test/java/bg/hristomanov/education/algorithms/trees/BinaryHeapTest.java) доказва 1 000 checks при missing arbitrary value в heap с 1 000 elements.

---

# 22. Bad вариант: list-based priority scheduler

[`LinearScanTaskScheduler.java`](./src/main/java/bg/hristomanov/education/algorithms/trees/scheduler/bad/LinearScanTaskScheduler.java):

```text
schedule
→ append O(1)

pollNext
→ scan all remaining tasks O(n)
```

При extraction на всички 1 000 tasks:

```text
999 + 998 + ... + 1
= 499 500 priority comparisons
```

И това дори не брои ArrayList shifting cost при remove.

---

# 23. Good вариант: heap-backed scheduler

[`HeapTaskScheduler.java`](./src/main/java/bg/hristomanov/education/algorithms/trees/scheduler/good/HeapTaskScheduler.java):

```text
schedule
→ heap add
→ O(log n)

pollNext
→ heap poll
→ O(log n)
```

При equal priority използваме `sequence`, за да пазим FIFO order като explicit scheduler policy.

[`PrioritySchedulerTradeOffTest.java`](./src/test/java/bg/hristomanov/education/algorithms/trees/PrioritySchedulerTradeOffTest.java) доказва:

- еднакъв business order;
- 499 500 comparisons при naive list scheduler;
- под 30 000 heap priority comparisons при 1 000 tasks.

Това е пример как правилният data-structure invariant променя work shape-а.

---

# 24. BST / AVL / Heap — decision guide

| Нужда | Естествен кандидат |
| --- | --- |
| ordered lookup + sorted traversal | BST / balanced BST |
| predictable logarithmic ordered operations | AVL / друга balanced BST |
| repeatedly get minimum/maximum | Heap / Priority Queue |
| arbitrary key lookup без ordering | Hash table |
| range / predecessor / successor semantics | ordered tree family |
| Top-K / scheduler / next-best | Heap / Priority Queue |

---

# 25. Production considerations

## BST / AVL

Внимавай за:

- duplicate-key policy;
- recursion depth;
- balancing/bookkeeping complexity;
- comparator consistency при generic implementations;
- дали реално ти трябва ordering.

## Heap / Priority Queue

Внимавай за:

- mutable priority след insert;
- unbounded queue growth;
- tie-breaking policy;
- arbitrary lookup/remove-by-value cost;
- producer rate > consumer capacity.

## Standard library first

Нашите implementations са учебни.

Production code обикновено трябва да започне от standard Java collections, освен ако има доказана причина за custom structure.

---

# 26. Кога не си струва

Не използвай AVL само защото е „по-балансирано“, ако:

- ти трябва само exact membership → hash structure може да е по-проста;
- dataset е малък;
- няма ordering/range requirement;
- implementation complexity няма business value.

Не използвай heap, ако ти трябва:

- arbitrary fast lookup;
- sorted traversal на всички elements;
- complex range query.

---

# 27. Как да стартираме тестовете

От repository root:

```bash
mvn -pl java/algorithms/05-trees-heaps -am test
```

От `java/algorithms`:

```bash
mvn -pl 05-trees-heaps test
```

---

# 28. Какво точно решихме

Първият problem беше ordinary BST, който приема sorted inserts.

Причината за performance degradation беше:

```text
BST ordering preserved
but
height grows to n
```

AVL добави height-balance invariant и rotations:

```text
same sorted values
→ logarithmic height
```

Вторият problem беше repeated best-priority extraction.

Naive list:

```text
scan all remaining items on every poll
→ quadratic total comparison work
```

Heap:

```text
maintain local priority invariant incrementally
→ O(log n) add/poll
```

---

# 29. Mental model за запомняне

1. **BST operations са O(h), не автоматично O(log n).**
2. **AVL пази height чрез rotations, без да нарушава BST ordering.**
3. **Heap пази local parent/child priority, не global sorted order.**
4. **Priority Queue е ADT; Binary Heap е implementation.**
5. **BST е за ordered lookup; Heap е за repeated next-best extraction.**

---

# 30. Как да разпозная този казус в code review

Търси:

- plain BST с predictable sorted insertion pattern;
- claim „BST lookup is always O(log n)“;
- priority scheduler, който scan-ва list при всяко poll;
- heap, използван за arbitrary lookup;
- Priority Queue с mutable priority fields;
- code, който предполага, че heap traversal е sorted;
- unnecessary custom tree там, където standard collection е достатъчна.

---

# 31. Практичен checklist

```text
[ ] Какъв invariant ми трябва?
[ ] Нужен ли е ordered traversal?
[ ] Нужен ли е repeated min/max extraction?
[ ] Какъв е tree height?
[ ] Балансът гарантиран ли е?
[ ] Какво става при sorted insertion order?
[ ] Heap ли е това или BST problem?
[ ] Трябва ли arbitrary lookup?
[ ] Какво е tie-breaking policy-то?
[ ] Може ли priority/key да се mutate-не след insert?
[ ] Има ли стандартна Java collection, която вече решава проблема?
```

---

# 32. Упражнения

1. Добави `min()` / `max()` към BST и обясни complexity спрямо height.
2. Добави duplicate-count policy вместо ignore-duplicates policy.
3. Логвай AVL rotation type (LL/RR/LR/RL) при insert.
4. Имплементирай bottom-up heapify и сравни с repeated add.
5. Добави Top-K lab с bounded max/min heap.
6. Добави remove-by-value към heap и обясни защо не е естествена O(log n) операция без допълнителен index.

---

# Оригинални източници

## MIT 6.006 — Introduction to Algorithms, Spring 2020

- **Lecture 6 — Binary Trees, Part 1**  
  https://ocw.mit.edu/courses/6-006-introduction-to-algorithms-spring-2020/resources/lecture-6-binary-trees-part-1/
- **Lecture 7 — Binary Trees, Part 2: AVL**  
  https://ocw.mit.edu/courses/6-006-introduction-to-algorithms-spring-2020/resources/lecture-7-binary-trees-part-2-avl/
- **Lecture 8 — Binary Heaps**  
  https://ocw.mit.edu/courses/6-006-introduction-to-algorithms-spring-2020/resources/lecture-8-binary-heaps/

## William Fiset — Binary Search Trees

- **Binary Search Tree Introduction**  
  https://www.youtube.com/watch?v=JfSdGQdAzq8
- **Binary Search Tree Insertion**  
  https://www.youtube.com/watch?v=LwpLXm3eb6A
- **Binary Search Tree Removal**  
  https://www.youtube.com/watch?v=8K7EO7s_iFE
- **Binary Search Tree Traversals**  
  https://www.youtube.com/watch?v=k7GkEbECZK0
- **Binary Search Tree Code**  
  https://www.youtube.com/watch?v=QwrZcySUxK8

## William Fiset — Balanced Trees / AVL

- **Balanced binary search tree rotations**  
  https://www.youtube.com/watch?v=q4fnJZr8ztY
- **AVL tree insertion**  
  https://www.youtube.com/watch?v=1QSYxIKXXP4
- **AVL tree removals**  
  https://www.youtube.com/watch?v=g4y2h70D6Nk
- **AVL tree source code**  
  https://www.youtube.com/watch?v=tqFZzXkbbGY

## William Fiset — Priority Queues / Binary Heaps

- **Priority Queue Introduction**  
  https://www.youtube.com/watch?v=wptevk0bshY
- **Priority Queue Min Heaps and Max Heaps**  
  https://www.youtube.com/watch?v=HCEr35qpawQ
- **Priority Queue Inserting Elements**  
  https://www.youtube.com/watch?v=QOJ-CmQiXko
- **Priority Queue Removing Elements**  
  https://www.youtube.com/watch?v=eVq8CmoC1x8
- **Priority Queue Code**  
  https://www.youtube.com/watch?v=GLIRnUhknP0

## Java mapping reference

- Java 25 `PriorityQueue` API:  
  https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/PriorityQueue.html

---

# Финална проверка

```text
[ ] Разграничавам ли Binary Tree от BST?
[ ] Мога ли да обясня BST invariant-а?
[ ] Знам ли трите BST delete cases?
[ ] Мога ли да обясня inorder/preorder/postorder?
[ ] Знам ли защо BST operation е O(h)?
[ ] Разбирам ли защо sorted inserts дегенерират plain BST?
[ ] Разбирам ли balance factor и rotations?
[ ] Мога ли да обясня как AVL пази O(log n) height?
[ ] Разграничавам ли BST, Heap и Priority Queue?
[ ] Разбирам ли array representation на heap?
[ ] Мога ли да обясня sift-up / sift-down?
[ ] Знам ли защо arbitrary heap search е O(n)?
```
