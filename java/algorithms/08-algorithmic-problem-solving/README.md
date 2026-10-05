# 08. Algorithmic Problem Solving & Dynamic Programming

Това е осмият модул от [`java/algorithms`](../README.md).

Първите седем теми изградиха toolkit:

```text
complexity
arrays / lists / stacks / queues
hashing
searching / sorting
trees / heaps
BFS / DFS
weighted graph algorithms
```

Тук сменяме фокуса:

> Вече не питаме „как работи тази структура?“, а „как от нов problem statement стигам до правилния pattern, state и algorithm?“

Това е преходът от **каталог от алгоритми** към **systematic problem solving**.

---

# 1. Реалният казус: една и съща задача, огромна разлика в work-а

Имаме planning problem:

```text
targetUnits = 20
allowed batch sizes = [1, 4, 6]

goal:
построй точно 20 units
с minimum number of batches
```

Naive recursion:

```text
remaining 20
├── choose 1 → solve 19
├── choose 4 → solve 16
└── choose 6 → solve 14
```

После:

```text
solve 19
├── solve 18
├── solve 15
└── solve 13

solve 16
├── solve 15   ← repeated subproblem
├── solve 12
└── solve 10
```

Един и същ state `remaining = 15` се решава отново и отново.

- Bad: [`RecursiveMinimumBatchPlanner.java`](./src/main/java/bg/hristomanov/education/algorithms/problemsolving/planning/bad/RecursiveMinimumBatchPlanner.java)
- Good: [`DynamicProgrammingMinimumBatchPlanner.java`](./src/main/java/bg/hristomanov/education/algorithms/problemsolving/planning/good/DynamicProgrammingMinimumBatchPlanner.java)
- Proof: [`MinimumBatchPlannerTradeOffTest.java`](./src/test/java/bg/hristomanov/education/algorithms/problemsolving/MinimumBatchPlannerTradeOffTest.java)

И двете решения връщат същия business answer.

Разликата е:

```text
naive recursion
→ same state solved many times

DP
→ each state solved once
→ reuse result
```

---

# 2. Какво ще научим

След модула трябва да можеш:

- да започваш от constraints и brute force, не от запомнен template;
- да разпознаваш repeated lookup / two-pointers / sliding-window / prefix-sum / backtracking signals;
- да формулираш invariant;
- да различаваш DFS от backtracking;
- да разбереш кога preprocessing купува по-евтини repeated operations;
- да дефинираш DP state;
- да извеждаш transition от decisions;
- да различаваш memoization и tabulation;
- да оценяваш `states × work per state`;
- да решаваш LCS/LIS/Coin-style state-design problems;
- да разбираш interval DP;
- да разбираш Subset Sum като pseudopolynomial DP;
- да различаваш polynomial in numeric value от polynomial in encoded input size;
- да преценяваш дали DP state space е practically feasible.

---

# 3. Универсалният problem-solving flow

При нов algorithmic problem:

```text
1. Какъв е input-ът?
2. Какъв е output-ът?
3. Какви са constraints?
4. Как изглежда brute force?
5. Къде brute force повтаря work?
6. Има ли ordering?
7. Има ли contiguous region?
8. Има ли repeated lookup?
9. Има ли graph/state-space?
10. Има ли optimization/counting objective?
11. Какъв invariant мога да поддържам?
12. Каква complexity е допустима?
```

Важно:

> Pattern recognition не е keyword matching.

„substring“ не означава автоматично Sliding Window.

„minimum“ не означава автоматично DP.

Constraints + operations + required result определят подхода.

---

# README → код

| Концепция | Код | Доказателство |
| --- | --- | --- |
| Two Pointers | [`TwoPointersPairSum.java`](./src/main/java/bg/hristomanov/education/algorithms/problemsolving/patterns/TwoPointersPairSum.java) | [`ProblemSolvingPatternsTest.java`](./src/test/java/bg/hristomanov/education/algorithms/problemsolving/ProblemSolvingPatternsTest.java) |
| Sliding Window | [`SlidingWindowLongestDistinct.java`](./src/main/java/bg/hristomanov/education/algorithms/problemsolving/patterns/SlidingWindowLongestDistinct.java) | pattern tests |
| Prefix Sum | [`PrefixSumRangeQuery.java`](./src/main/java/bg/hristomanov/education/algorithms/problemsolving/patterns/PrefixSumRangeQuery.java) | pattern tests |
| Backtracking | [`BacktrackingCombinations.java`](./src/main/java/bg/hristomanov/education/algorithms/problemsolving/patterns/BacktrackingCombinations.java) | pattern tests |
| Naive recursion | [`RecursiveMinimumBatchPlanner.java`](./src/main/java/bg/hristomanov/education/algorithms/problemsolving/planning/bad/RecursiveMinimumBatchPlanner.java) | [`MinimumBatchPlannerTradeOffTest.java`](./src/test/java/bg/hristomanov/education/algorithms/problemsolving/MinimumBatchPlannerTradeOffTest.java) |
| Bottom-up DP | [`DynamicProgrammingMinimumBatchPlanner.java`](./src/main/java/bg/hristomanov/education/algorithms/problemsolving/planning/good/DynamicProgrammingMinimumBatchPlanner.java) | planner trade-off test |
| Memoization vs tabulation | [`FibonacciDynamicProgramming.java`](./src/main/java/bg/hristomanov/education/algorithms/problemsolving/dp/fundamentals/FibonacciDynamicProgramming.java) | [`DynamicProgrammingFundamentalsTest.java`](./src/test/java/bg/hristomanov/education/algorithms/problemsolving/DynamicProgrammingFundamentalsTest.java) |
| LCS | [`LongestCommonSubsequence.java`](./src/main/java/bg/hristomanov/education/algorithms/problemsolving/dp/classic/LongestCommonSubsequence.java) | [`ClassicDynamicProgrammingTest.java`](./src/test/java/bg/hristomanov/education/algorithms/problemsolving/ClassicDynamicProgrammingTest.java) |
| LIS | [`LongestIncreasingSubsequence.java`](./src/main/java/bg/hristomanov/education/algorithms/problemsolving/dp/classic/LongestIncreasingSubsequence.java) | classic DP tests |
| Coin Change | [`CoinChangeDynamicProgramming.java`](./src/main/java/bg/hristomanov/education/algorithms/problemsolving/dp/classic/CoinChangeDynamicProgramming.java) | classic DP tests |
| Subset Sum | [`SubsetSumDynamicProgramming.java`](./src/main/java/bg/hristomanov/education/algorithms/problemsolving/dp/advanced/SubsetSumDynamicProgramming.java) | [`AdvancedDynamicProgrammingTest.java`](./src/test/java/bg/hristomanov/education/algorithms/problemsolving/AdvancedDynamicProgrammingTest.java) |
| Interval DP | [`MatrixChainMultiplication.java`](./src/main/java/bg/hristomanov/education/algorithms/problemsolving/dp/advanced/MatrixChainMultiplication.java) | advanced DP tests |

---

# 4. Pattern recognition: reuse the toolkit

Не дублираме предишните модули.

Вместо отново да имплементираме HashMap, Heap, BFS и Binary Search, тук питаме:

> Какъв signal в problem-а ни насочва към вече познатата техника?

## Repeated membership / grouping / counting

```text
nested search
→ repeated lookup
→ HashMap / HashSet candidate
```

Виж [`03-hashing`](../03-hashing/README.md).

## Ordered / monotonic search space

```text
can discard half?
→ Binary Search candidate
```

Виж [`04-searching-sorting`](../04-searching-sorting/README.md).

## Repeated next-best / Top-K

```text
need smallest/largest repeatedly?
→ Heap / Priority Queue candidate
```

Виж [`05-trees-heaps`](../05-trees-heaps/README.md).

## State-space minimum steps

```text
unweighted transitions
→ BFS candidate
```

Виж [`06-graph-fundamentals`](../06-graph-fundamentals/README.md).

---

# 5. Two Pointers

[`TwoPointersPairSum.java`](./src/main/java/bg/hristomanov/education/algorithms/problemsolving/patterns/TwoPointersPairSum.java) работи върху sorted array.

Brute force:

```text
for each i
  for each j
→ O(n²)
```

Two pointers:

```text
left = smallest
right = largest

sum too small
→ left++

sum too large
→ right--

sum == target
→ found
```

Invariant-ът е възможен **заради ordering-а**.

Ако array не е sorted, движението на pointer не позволява безопасно да отхвърлим всички пропуснати candidates.

---

# 6. Sliding Window

[`SlidingWindowLongestDistinct.java`](./src/main/java/bg/hristomanov/education/algorithms/problemsolving/patterns/SlidingWindowLongestDistinct.java) решава:

> longest contiguous substring with at most K distinct chars.

Mental model:

```text
expand right
→ add new element

while window invalid:
    shrink left

record best valid window
```

Core invariant:

```text
current window contains <= K distinct values
```

Sliding Window е особено силен, когато:

- region е contiguous;
- left boundary може monotonic-но да се движи напред;
- можем incremental-но да update-ваме window state.

---

# 7. Prefix Sum

[`PrefixSumRangeQuery.java`](./src/main/java/bg/hristomanov/education/algorithms/problemsolving/patterns/PrefixSumRangeQuery.java) показва preprocessing trade-off:

```text
build prefix once
→ O(n)

range sum query
→ O(1)
```

Ако:

```text
prefix[i]
= sum of values before i
```

тогава:

```text
sum(left..right)
= prefix[right + 1] - prefix[left]
```

Това е същият broader principle, който вече видяхме при sorting и indexing:

> плати preprocessing веднъж, ако после ще имаш много заявки.

---

# 8. Backtracking

[`BacktrackingCombinations.java`](./src/main/java/bg/hristomanov/education/algorithms/problemsolving/patterns/BacktrackingCombinations.java):

```text
choose
→ explore
→ undo
→ choose next
```

Ключовата разлика:

## DFS

Traversal strategy върху вече дефиниран graph/state-space.

## Backtracking

Ние implicit-но **строим decision tree**, променяме текущ candidate state и след exploration го възстановяваме.

Backtracking често има exponential search space.

Pruning (ранно отрязване на branch, който не може да даде валиден/по-добър result) е practically критично.

---

# 9. Dynamic Programming: кога да го заподозрем

Typical signals:

- problem се разбива на smaller subproblems;
- едни и същи subproblems се появяват по много paths;
- result на големия problem се комбинира от smaller results;
- искаме min/max/count/feasibility;
- state space е достатъчно малък за caching/table.

Но:

> Recursion сама по себе си не означава DP.

Ако subproblems не се повтарят, memoization може да не даде полза.

---

# 10. DP state

Най-важният въпрос:

> Какво минимално трябва да знам, за да опиша един независим subproblem?

В batch planner:

```text
state = remaining units
```

или bottom-up:

```text
state = exact units already built
```

DP value:

```text
minimum number of batches required for this state
```

Ако state не съдържа достатъчно information:

→ два различни subproblems могат погрешно да споделят един cache entry.

Ако съдържа излишна информация:

→ state space става unnecessarily огромен.

---

# 11. Decision → Transition

За batch planner:

```text
state = current target x

decision:
choose batch b

dependency:
x - b

transition:
dp[x] = min(dp[x], dp[x-b] + 1)
```

Transition не трябва да се запаметява като формула.

Той трябва да се **derive-не от meaning-а на state-а и decision-а**.

---

# 12. Naive recursion → repeated subproblems

[`RecursiveMinimumBatchPlanner.java`](./src/main/java/bg/hristomanov/education/algorithms/problemsolving/planning/bad/RecursiveMinimumBatchPlanner.java) е реалистичен bad вариант.

За всеки remaining amount опитва всеки batch.

Различни paths стигат до едно и също remaining amount:

```text
20
→ 19
   → 15

20
→ 16
   → 15
```

Но naive recursion решава `15` отново.

Това е repeated work, не correctness bug.

---

# 13. Memoization

Top-down:

```text
solve(state):
    if cached:
        return cached

    compute from dependent states
    cache result
    return result
```

[`FibonacciDynamicProgramming.java`](./src/main/java/bg/hristomanov/education/algorithms/problemsolving/dp/fundamentals/FibonacciDynamicProgramming.java) използва Fibonacci само като microscope.

[`DynamicProgrammingFundamentalsTest.java`](./src/test/java/bg/hristomanov/education/algorithms/problemsolving/DynamicProgrammingFundamentalsTest.java) доказва:

```text
naive recursion
→ thousands of calls

memoized / tabulated
→ work proportional to number of useful states
```

Fibonacci не е крайната цел. Той само прави repeated-state explosion лесен за виждане.

---

# 14. Tabulation

Bottom-up:

```text
base states
→ next states
→ ...
→ target state
```

[`DynamicProgrammingMinimumBatchPlanner.java`](./src/main/java/bg/hristomanov/education/algorithms/problemsolving/planning/good/DynamicProgrammingMinimumBatchPlanner.java) построява:

```text
dp[0]
dp[1]
dp[2]
...
dp[target]
```

Evaluation order трябва да гарантира:

> Когато изчисляваме state, dependencies вече са готови.

Това е DAG/topological-order mental model.

---

# 15. Memoization vs Tabulation

| Property | Memoization | Tabulation |
| --- | --- | --- |
| Direction | top-down | bottom-up |
| Natural shape | recursive | iterative/table |
| Computes unreachable states | често не | може |
| Recursion stack | да | не |
| Evaluation order | implicit | explicit |
| Easy initial derivation | често | зависи |

Не избирай по догма.

Избери според state graph-а и implementation constraints.

---

# 16. Complexity: states × work per state

Това е най-полезната DP complexity формула:

```text
Time
≈ number of unique states
× transition work per state
```

Batch planner:

```text
states = target + 1
work per state = number of allowed batch sizes

→ O(target × batchTypes)
```

LCS:

```text
states ≈ n × m
work per state = O(1)

→ O(nm)
```

LIS O(n²) formulation:

```text
states = n
work per state = scan previous n states

→ O(n²)
```

---

# 17. LCS: 2D state

[`LongestCommonSubsequence.java`](./src/main/java/bg/hristomanov/education/algorithms/problemsolving/dp/classic/LongestCommonSubsequence.java):

```text
state = (i, j)
```

Meaning:

> best common subsequence using suffixes starting at i and j.

If characters match:

```text
take char
→ move both indices
```

If they do not:

```text
skip from first
or
skip from second
→ take better result
```

Тук state има две dimensions, защото трябва да знаем position и в двете sequences.

---

# 18. LIS: state meaning controls complexity

[`LongestIncreasingSubsequence.java`](./src/main/java/bg/hristomanov/education/algorithms/problemsolving/dp/classic/LongestIncreasingSubsequence.java):

```text
dp[i]
= length of best increasing subsequence ending exactly at i
```

Transition:

```text
look at j < i
if values[j] < values[i]:
    candidate = dp[j] + 1
```

States:

```text
n
```

Work per state:

```text
up to n previous positions
```

→ `O(n²)`.

По-късно може да има по-бърз `O(n log n)` LIS algorithm, но този module нарочно показва DP state reasoning.

---

# 19. Coin Change: same input, different objective

[`CoinChangeDynamicProgramming.java`](./src/main/java/bg/hristomanov/education/algorithms/problemsolving/dp/classic/CoinChangeDynamicProgramming.java) има две задачи.

## Minimum coins

```text
dp[amount]
= minimum number of coins
```

Aggregation:

```text
min(...)
```

## Number of combinations

```text
ways[amount]
= number of ways to construct amount
```

Aggregation:

```text
sum(...)
```

Същите:

- coin values;
- target amount.

Но objective е различен.

Следователно:

> same input ≠ same DP.

---

# 20. Iteration order can change semantics

При count-combinations:

```text
for coin:
    for amount:
```

брои combinations, където ordering на coins не създава нов answer.

Друг iteration order може да брои sequences/permutations.

Това показва:

> Bottom-up loop order не е cosmetic detail; той трябва да отговаря на state/transition semantics.

---

# 21. Advanced DP: interval state

[`MatrixChainMultiplication.java`](./src/main/java/bg/hristomanov/education/algorithms/problemsolving/dp/advanced/MatrixChainMultiplication.java) е compact interval-DP example.

State:

```text
(left, right)
```

Meaning:

> minimum cost to evaluate matrices from left through right.

Decision:

```text
where do we split?
```

Transition:

```text
cost(left, split)
+
cost(split+1, right)
+
combine cost
```

Pattern signal:

> problem asks for best way to parenthesize/split a contiguous interval.

---

# 22. Subset Sum

[`SubsetSumDynamicProgramming.java`](./src/main/java/bg/hristomanov/education/algorithms/problemsolving/dp/advanced/SubsetSumDynamicProgramming.java) използва:

```text
reachable[sum]
```

State meaning:

> може ли exact sum да бъде построена от processed elements?

Decision:

```text
skip value
or
use value
```

1D optimization iterate-ва sums backwards, за да не използваме един input element повече от веднъж в същия iteration.

---

# 23. Pseudopolynomial complexity

Subset Sum с target `W` има typical DP:

```text
O(nW)
```

На пръв поглед това изглежда polynomial.

Но ако `W` е integer encoded in binary:

```text
W = 1,000,000
```

се представя с около:

```text
20 bits
```

DP table с:

```text
1,000,001 states
```

е linear спрямо numeric value `W`, но exponential-like спрямо броя bits, нужни да запишем `W`.

Това наричаме **pseudopolynomial**.

---

# 24. State-space feasibility

Преди DP implementation пресметни:

```text
number of states
× memory per state
× work per state
```

Пример:

```text
n = 1,000
W = 1,000,000
```

2D boolean table:

```text
~1 billion states
```

може да е theoretically definable и practically неприемлива.

Затова advanced DP винаги изисква feasibility analysis.

---

# 25. Space optimization

Ако transition използва само:

```text
previous row
или
last few states
```

не е задължително да пазим цялата table.

Examples:

- Fibonacci → 2 values;
- Subset Sum → 1D reachable array;
- some 2D DPs → rolling rows.

Но оптимизирай memory **след като state semantics са ясни**.

Не започвай със space trick и после да се опитваш да разбереш какво прави кодът.

---

# 26. Bad / good planner доказателството

[`MinimumBatchPlannerTradeOffTest.java`](./src/test/java/bg/hristomanov/education/algorithms/problemsolving/MinimumBatchPlannerTradeOffTest.java) сравнява:

```text
target = 20
batch sizes = [1,4,6]
```

И двете версии дават:

```text
minimum batches = 4
```

Но:

```text
naive recursion
→ repeated calls explode

bottom-up DP
→ target × batchTypes transition checks
```

Това е точният DP promise:

> не магически по-добър answer, а същият correct answer без repeated state work.

---

# 27. DP не е винаги правилният отговор

Не използвай DP автоматично, ако:

- subproblems не се повтарят;
- greedy property дава доказано по-просто решение;
- state space е прекалено голям;
- input constraints позволяват brute force;
- graph algorithm формулира problem-а по-естествено;
- state definition изисква почти целия history и няма meaningful compression.

---

# 28. Pattern combinations

Real problems често комбинират techniques.

Например:

```text
Sliding Window + HashMap
BFS + HashSet
Heap + HashMap
Binary Search + feasibility check
DFS + Backtracking
DP + Binary Search
```

Целта не е „един problem = един pattern“.

Целта е да разпознаем **какви invariants и repeated operations има във всяка част**.

---

# 29. Production / engineering considerations

Algorithmic problem solving се появява и извън interviews:

- batch planning;
- scheduling;
- deduplication;
- rate/window analysis;
- dependency traversal;
- pricing/optimization;
- diff/LCS-like comparisons;
- resource allocation.

Но production context добавя:

- I/O cost;
- memory limits;
- concurrency;
- latency SLO;
- data freshness;
- database/query engine capabilities.

Не заменяй database query optimizer с ръчно написан DP само защото problem-ът математически може да се формулира така.

---

# 30. Как да стартираме тестовете

От repository root:

```bash
mvn -pl java/algorithms/08-algorithmic-problem-solving -am test
```

От `java/algorithms`:

```bash
mvn -pl 08-algorithmic-problem-solving test
```

---

# 31. Какво точно решихме

В началото имахме minimum-batch planner с repeated recursive subproblems.

Причината беше:

```text
different decision paths
→ reach same remaining state
→ solve it again
```

Променихме mental model-а:

```text
state = exact target amount
→ solve each state once
→ reuse result
```

После разширихме това към:

- 1D state;
- 2D sequence state;
- interval state;
- counting vs optimization semantics;
- pseudopolynomial numeric state spaces.

А pattern layer-ът показа как още преди DP можем да разпознаваме по-прости invariants като two pointers, sliding window и preprocessing.

---

# 32. Mental model за запомняне

1. **Първо brute force и constraints; после pattern.**
2. **Pattern recognition = recognition на invariant/problem shape, не keyword matching.**
3. **DP state трябва минимално и достатъчно да описва subproblem-а.**
4. **Transition се derive-ва от decisions, не се запаметява като formula.**
5. **DP time ≈ unique states × work per state.**
6. **Same input с различен objective може да има различно DP.**
7. **Pseudopolynomial means polynomial in numeric value, не непременно в encoded input size.**

---

# 33. Как да разпозная този казус в code review

Търси:

- nested search, който може да стане remembered lookup;
- sorted input + pair search;
- repeated contiguous-region scan;
- many range queries без preprocessing;
- recursion, която повтаря едни и същи arguments;
- memo key, който не съдържа достатъчно state;
- DP table с огромна numeric dimension;
- tabulation loop order, който не отговаря на semantics;
- exponential backtracking без pruning;
- „DP“ решение, което всъщност няма overlapping subproblems.

---

# 34. Практичен checklist

```text
[ ] Какъв е brute force?
[ ] Къде се повтаря work?
[ ] Какви constraints имам?
[ ] Има ли ordering?
[ ] Има ли contiguous window?
[ ] Мога ли да preprocess-на?
[ ] Какъв invariant поддържам?
[ ] Ако е DP: какво точно означава state?
[ ] Какви decisions имам?
[ ] Какъв transition следва от тях?
[ ] Какви са base cases?
[ ] Колко unique states има?
[ ] Колко работа правя per state?
[ ] Каква memory table ми трябва?
[ ] Complexity polynomial ли е спрямо encoded input size?
[ ] Има ли по-прост greedy / graph / search solution?
```

---

# 35. Упражнения

1. Добави fixed-size sliding window example и сравни с dynamic window.
2. Добави monotonic-stack problem към pattern package.
3. Преработи minimum-batch planner в top-down memoized variant.
4. Направи LCS memory optimization с rolling rows.
5. Имплементирай Coin Change permutations и сравни loop order-а.
6. Добави 0/1 Knapsack като 2D, после 1D DP.
7. Изчисли memory cost за Subset Sum при target = 10⁶, 10⁸ и 10⁹.
8. Добави reconstruction към Matrix Chain Multiplication, за да върне оптималното parenthesization.
9. Вземи нов problem и напиши само: brute force → state/invariant → complexity, преди да пишеш Java код.

---

# Оригинални източници

## Problem-Solving Patterns

- freeCodeCamp / AlgoMonster — **Data Structure and Algorithm Patterns for LeetCode Interviews – Tutorial**  
  https://www.youtube.com/watch?v=Z_c4byLrNBU

## MIT 6.006 — Dynamic Programming

- **Lecture 15 — Dynamic Programming, Part 1: SRTBOT, Fib, DAGs, Bowling**  
  https://ocw.mit.edu/courses/6-006-introduction-to-algorithms-spring-2020/resources/lecture-15-dynamic-programming-part-1-srtbot-fib-dags-bowling/
- **Lecture 16 — Dynamic Programming, Part 2: LCS, LIS, Coins**  
  https://ocw.mit.edu/courses/6-006-introduction-to-algorithms-spring-2020/resources/lecture-16-dynamic-programming-part-2-lcs-lis-coins/
- **Lecture 17 — Dynamic Programming, Part 3: APSP, Parens, Piano**  
  https://ocw.mit.edu/courses/6-006-introduction-to-algorithms-spring-2020/resources/lecture-17-dynamic-programming-part-3-apsp-parens-piano/
- **Lecture 18 — Dynamic Programming, Part 4: Rods, Subset Sum, Pseudopolynomial**  
  https://ocw.mit.edu/courses/6-006-introduction-to-algorithms-spring-2020/resources/lecture-18-dynamic-programming-part-4-rods-subset-sum-pseudopolynomial/

- Full course lecture videos:  
  https://ocw.mit.edu/courses/6-006-introduction-to-algorithms-spring-2020/resources/lecture-videos/

---

# Финална проверка

```text
[ ] Мога ли да започна от brute force, без да търся template по име?
[ ] Разпознавам ли Two Pointers / Sliding Window / Prefix Sum signals?
[ ] Разграничавам ли DFS от Backtracking?
[ ] Мога ли да дефинирам DP state с едно точно изречение?
[ ] Мога ли да derive-на transition от decisions?
[ ] Разбирам ли memoization vs tabulation?
[ ] Мога ли да пресметна states × work per state?
[ ] Разбирам ли LCS / LIS state design?
[ ] Разбирам ли защо Coin Change min и count са различни DP задачи?
[ ] Разбирам ли interval DP?
[ ] Мога ли да обясня pseudopolynomial complexity?
[ ] Мога ли да преценя дали state space е practically feasible?
```
