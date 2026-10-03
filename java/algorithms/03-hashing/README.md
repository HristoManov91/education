# 03. Hashing

Това е третият модул от [`java/algorithms`](../README.md).

Темата изгражда mental model-а:

```text
key
→ hash function
→ hash value
→ bucket / initial index
→ collision strategy
→ entry / value
```

Целта не е да запомним „HashMap е O(1)“, а да разберем **защо average lookup може да е близо до constant-time, какво се случва при collisions и защо load factor / resize / deletion policy са част от correctness и performance поведението**.

---

# 1. Реалният казус

Имаме backend компонент с lookup по business key:

```text
productCode → cached metadata
tenantId    → configuration
userId      → session/state
```

Наивният вариант е:

```text
List<Entry>
→ scan from beginning
→ compare every key
```

Това е `O(n)` lookup.

Hash table се опитва да използва key-а, за да стигне директно до малка част от структурата:

```text
key
→ hash
→ bucket/index
→ very small local search
```

Но има фундаментален проблем:

> Различни keys могат да попаднат на една и съща позиция.

Това е **collision** (колизия).

Hash table е правилна структура само ако има коректна стратегия как да пази и намира всички collided entries.

---

# 2. Какво ще научим

След модула трябва да можеш:

- да обясниш key → hash → index/bucket flow-а;
- да разграничиш hash function от table index calculation;
- да обясниш защо collisions са неизбежни;
- да обясниш separate chaining;
- да обясниш open addressing;
- да различиш linear probing, quadratic probing и double hashing;
- да обясниш primary clustering;
- да разбереш load factor;
- да обясниш resize / rehash;
- да обясниш защо average `O(1)` не е guaranteed `O(1)`;
- да обясниш защо naive deletion при open addressing може да счупи lookup;
- да обясниш tombstone/deleted marker;
- да сравниш memory overhead и locality между chaining и open addressing;
- да разпознаеш кога hash-based structure е естествен избор.

---

# 3. Основният mental model

## Hash function

Hash function преобразува key в integer-like hash representation.

Conceptually:

```text
"customer-123"
→ hash(...)
→ 1 847 293 ...
```

После table-ът го map-ва към capacity:

```text
bucketIndex = hash → [0 .. capacity-1]
```

В нашите implementations използваме Java `hashCode()` като входен hash и малък spread step, но **Java-specific HashMap internals не са предметът на този модул**.

## Collision

Ако:

```text
key A → bucket 4
key B → bucket 4
```

това не означава, че hash function е счупена.

При finite table и много възможни keys collisions са неизбежни.

Въпросът е:

> Как съхраняваме и намираме и A, и B?

---

# README → код

| Концепция | Код | Доказателство |
| --- | --- | --- |
| Separate chaining | [`SeparateChainingHashTable.java`](./src/main/java/bg/hristomanov/education/algorithms/hashing/chaining/SeparateChainingHashTable.java) | [`SeparateChainingHashTableTest.java`](./src/test/java/bg/hristomanov/education/algorithms/hashing/SeparateChainingHashTableTest.java) |
| Open addressing | [`OpenAddressingHashTable.java`](./src/main/java/bg/hristomanov/education/algorithms/hashing/openaddressing/OpenAddressingHashTable.java) | [`OpenAddressingHashTableTest.java`](./src/test/java/bg/hristomanov/education/algorithms/hashing/OpenAddressingHashTableTest.java) |
| Linear / quadratic / double hashing | [`ProbeStrategy.java`](./src/main/java/bg/hristomanov/education/algorithms/hashing/openaddressing/ProbeStrategy.java) | [`OpenAddressingHashTableTest.java`](./src/test/java/bg/hristomanov/education/algorithms/hashing/OpenAddressingHashTableTest.java) |
| Broken deletion | [`BrokenLinearProbingHashTable.java`](./src/main/java/bg/hristomanov/education/algorithms/hashing/bad/BrokenLinearProbingHashTable.java) | [`BrokenDeletionTest.java`](./src/test/java/bg/hristomanov/education/algorithms/hashing/BrokenDeletionTest.java) |
| Metrics | [`HashTableMetrics.java`](./src/main/java/bg/hristomanov/education/algorithms/hashing/model/HashTableMetrics.java) | collision/probe/rehash assertions в тестовете |

---

# 4. Separate Chaining

Separate chaining пази bucket, който може да съдържа повече от един entry:

```text
bucket[0] → null
bucket[1] → [K1,V1] → [K8,V8] → null
bucket[2] → [K4,V4] → null
```

При collision:

```text
same bucket
→ traverse local chain
→ compare actual key
```

[`SeparateChainingHashTable.java`](./src/main/java/bg/hristomanov/education/algorithms/hashing/chaining/SeparateChainingHashTable.java) използва linked chain във всеки bucket.

## Lookup

Average mental model:

```text
hash
→ one bucket
→ short chain
```

Ако hash distribution е добро и load factor е разумен, chain-овете остават къси.

Worst case:

```text
many keys
→ same bucket
→ one long chain
→ O(n)
```

---

# 5. Load Factor

Load factor показва колко entries имаме спрямо table capacity.

Conceptually:

```text
load factor = size / capacity
```

При separate chaining може да имаме load factor над 1, но дългите chains увеличават lookup cost.

При open addressing load factor е още по-чувствителен, защото всеки occupied slot намалява свободните probe destinations.

> Load factor не е само memory number. Той влияе върху collision probability и lookup work.

---

# 6. Resize / Rehash

Когато table capacity се промени:

```text
old capacity
→ allocate larger table
→ recompute bucket/index for every existing key
→ move entries
```

Не можем просто да copy-нем entries на същите physical indices, защото:

```text
index depends on capacity
```

Това е **rehash**.

И двете good implementations в модула пазят `rehashes` metric, за да може тестът да доказва този lifecycle.

---

# 7. Open Addressing

При open addressing няма linked bucket chains.

Всички entries живеят директно в table array-а:

```text
[ ][K1][K8][ ][K4][ ][ ]
```

При collision търсим друга позиция по **probe sequence** (последователност от позиции).

[`OpenAddressingHashTable.java`](./src/main/java/bg/hristomanov/education/algorithms/hashing/openaddressing/OpenAddressingHashTable.java) позволява три стратегии чрез [`ProbeStrategy.java`](./src/main/java/bg/hristomanov/education/algorithms/hashing/openaddressing/ProbeStrategy.java).

---

# 8. Linear Probing

Mental model:

```text
initial index
→ +1
→ +1
→ +1
→ ...
```

Пример:

```text
hash → index 3

index 3 occupied
index 4 occupied
index 5 free
→ insert at 5
```

## Primary clustering

Ако много keys създадат contiguous occupied region:

```text
[ ][ ][X][X][X][X][ ][ ]
```

следващите collisions все по-често попадат около този cluster.

Това увеличава probes.

---

# 9. Quadratic Probing

Quadratic probing променя offset-а приблизително като:

```text
0², 1², 2², 3², ...
```

Идеята е да не проверяваме само последователни physical slots.

Това намалява primary clustering, но probe behavior зависи от table size и load factor.

В educational implementation използваме **prime capacity** и държим load factor под 0.5, за да пазим поведението предвидимо.

Не е нужно да запаметяваш формулата. По-важно е да разбереш:

> collision strategy определя кои alternate positions ще проверим.

---

# 10. Double Hashing

Double hashing използва втори hash-derived step:

```text
initialIndex
step = secondHash(key)

initial
→ initial + step
→ initial + 2*step
→ ...
```

Целта е различни keys да имат по-различни probe sequences и да се намали clustering.

Това **не означава**, че съхраняваме две hash стойности като business data.

---

# 11. Най-опасната част: deletion

Представи си linear probing:

```text
index 3 → A
index 4 → B
```

A и B имат collision и B е отишъл на следващия slot.

Lookup за B:

```text
3 occupied by other key
→ continue
4 contains B
→ found
```

Ако remove(A) направи index 3 директно EMPTY:

```text
index 3 → EMPTY
index 4 → B
```

lookup(B) вижда EMPTY на 3 и логично заключава:

> „Ако key-ът е бил inserted по тази probe sequence, щях да го срещна преди първия never-used empty slot.“

И спира.

B става невидим.

---

# 12. Bad вариант: EMPTY вместо tombstone

[`BrokenLinearProbingHashTable.java`](./src/main/java/bg/hristomanov/education/algorithms/hashing/bad/BrokenLinearProbingHashTable.java) нарочно прави точно тази грешка.

[`BrokenDeletionTest.java`](./src/test/java/bg/hristomanov/education/algorithms/hashing/BrokenDeletionTest.java) доказва broken semantics:

```text
put A
put B  // collided after A
get B → works

remove A
get B → null  // BUG
```

Това е correctness bug, не просто performance issue.

---

# 13. Good вариант: tombstone

Good implementation има три slot states:

```text
EMPTY
OCCUPIED
TOMBSTONE
```

Tombstone означава:

> „Тук е имало entry. Lookup-ът трябва да продължи.“

Затова:

```text
TOMBSTONE
→ continue probing
```

но при insert можем да reuse-нем tombstone slot.

[`OpenAddressingHashTableTest.java`](./src/test/java/bg/hristomanov/education/algorithms/hashing/OpenAddressingHashTableTest.java) доказва, че след deletion collided entries остават reachable.

---

# 14. Chaining vs Open Addressing

| Trade-off | Separate Chaining | Open Addressing |
| --- | --- | --- |
| Entry storage | buckets + nodes | directly in table array |
| Collision | local chain | probe another slot |
| Extra object/reference overhead | по-висок | по-нисък |
| Cache locality | по-слаба | обикновено по-добра |
| High load sensitivity | по-мека | силна |
| Deletion | сравнително проста | tombstone/rehash complexity |
| Clustering | chain length | probe clustering |
| Implementation complexity | по-ниска | по-висока |

Няма универсално „по-добро“.

---

# 15. Average O(1) ≠ guaranteed O(1)

Hash table performance зависи от:

- hash distribution;
- capacity;
- load factor;
- collision strategy;
- resize policy;
- key equality cost;
- memory/cache behavior.

При добри условия:

```text
lookup / insert / remove
→ average O(1)
```

Но worst case може да деградира.

Следователно:

> `O(1)` е growth claim при конкретни assumptions, не обещание за една CPU инструкция.

---

# 16. Java key equality — само необходимият минимум

Нашите generic tables използват:

- `hashCode()` за initial placement;
- `equals()` за проверка дали намереният entry е действително същият key.

Това е неизбежна част от Java example-а.

Но пълният Java contract на `equals/hashCode`, mutable keys, JDK `HashMap` treeification и concrete implementation details ще се разглеждат в отделната **Java Collections** тема.

---

# 17. Реални backend problem shapes

Hash-based structures са естествени при:

## Membership

```text
blockedIds.contains(id)
```

## Deduplication

```text
seen.add(key)
```

## Frequency counting

```text
key → count
```

## Grouping

```text
customerId → list of orders
```

## Lookup

```text
id → object/configuration/cache entry
```

---

# 18. Production considerations

Внимавай за:

- mutable keys;
- adversarial / pathological collision patterns;
- огромен load factor;
- repeated resize;
- memory overhead;
- unbounded key cardinality;
- expensive `equals()`;
- assumptions, че hash lookup е „free“.

Ако key space е малък integer range, array/direct indexing може да бъде по-просто и по-евтино от hash table.

---

# 19. Кога hashing НЕ е естественият избор

Не избирай hash-based structure автоматично, ако ти трябва:

- sorted order;
- range queries;
- predecessor/successor;
- min/max по ред;
- ordered traversal.

Тогава tree/sorted structures може да са по-подходящи.

---

# 20. Как да стартираме тестовете

От repository root:

```bash
mvn -pl java/algorithms/03-hashing -am test
```

От `java/algorithms`:

```bash
mvn -pl 03-hashing test
```

---

# 21. Какво точно решихме

В началото искахме:

```text
key → fast lookup
```

Но direct placement създава collision problem.

Реализирахме два основни отговора:

```text
Separate Chaining
→ same bucket, multiple entries

Open Addressing
→ same table, probe another slot
```

След това доказахме критичен correctness detail:

```text
open-addressing deletion
≠ simply make slot EMPTY

must preserve probe chain
→ TOMBSTONE
```

---

# 22. Mental model за запомняне

1. **Hash не е index; hash-ът се map-ва към текущата table capacity.**
2. **Collisions са нормални и неизбежни.**
3. **Average O(1) зависи от distribution, load factor и collision handling.**
4. **Open addressing trades pointer overhead за probe behavior/locality.**
5. **Deletion при open addressing е част от correctness — tombstone пази probe chain-а.**

---

# 23. Как да разпозная hashing казус в code review

Търси:

- repeated list scan за membership;
- duplicate detection с nested loops;
- grouping/counting чрез repeated search;
- key-based lookup без нужда от sorted order;
- hash table с load factor, който никога не trigger-ва resize;
- open-addressing deletion без deleted state;
- code, който предполага „no collisions“;
- mutable key state.

---

# 24. Практичен checklist

```text
[ ] Какъв е key-ът?
[ ] Как се изчислява hash/index?
[ ] Какво става при collision?
[ ] Какъв е load factor?
[ ] Кога resize-ваме?
[ ] Rehash-ваме ли entries след resize?
[ ] Как работи delete?
[ ] При open addressing пазим ли probe chain-а?
[ ] Какъв е average и какъв е worst case?
[ ] Трябва ли ми hash table или ми трябва ordering/range behavior?
```

---

# 25. Упражнения

1. Направи `TestKey` с всички keys с един forced hash и наблюдавай collision metrics.
2. Сравни probes при LINEAR, QUADRATIC и DOUBLE_HASH за една и съща collision група.
3. Вдигни open-addressing MAX_LOAD_FACTOR и наблюдавай probe count.
4. Замени tombstone behavior с EMPTY и виж кой тест се чупи.
5. Добави tombstone cleanup policy при resize.
6. Добави direct-index array решение за bounded integer key space и го сравни концептуално с hashing.

---

# Оригинални източници

## MIT 6.006 — Introduction to Algorithms, Spring 2020

- **Lecture 4 — Hashing**  
  https://ocw.mit.edu/courses/6-006-introduction-to-algorithms-spring-2020/resources/lecture-4-hashing/

## William Fiset — Data Structures

### Hashing fundamentals
- **Hash table hash function**  
  https://www.youtube.com/watch?v=2E54GqF0H4s
- **Hash table separate chaining**  
  https://www.youtube.com/watch?v=T9gct6Dx-jo
- **Hash table separate chaining source code**  
  https://www.youtube.com/watch?v=Av9kwXkuQFw

### Open addressing
- **Hash table open addressing**  
  https://www.youtube.com/watch?v=xIejolxzZS8
- **Hash table linear probing**  
  https://www.youtube.com/watch?v=Ma9XOInZJWM
- **Hash table quadratic probing**  
  https://www.youtube.com/watch?v=b0858c55TGQ
- **Hash table double hashing**  
  https://www.youtube.com/watch?v=H5e9V5x92vI
- **Hash Table Open Addressing Removals**  
  https://www.youtube.com/watch?v=7eLDTtbzX4M
- **Hash table open addressing code**  
  https://www.youtube.com/watch?v=eer6yW4S4Ts

- Playlist:  
  https://www.youtube.com/playlist?list=PLDV1Zeh2NRsB6SWUrDFW2RmDotAfPbeHu

---

# Финална проверка

```text
[ ] Мога ли да обясня key → hash → bucket/index?
[ ] Разбирам ли защо collision не е грешка?
[ ] Мога ли да обясня separate chaining?
[ ] Мога ли да обясня open addressing?
[ ] Различавам ли linear / quadratic / double hashing?
[ ] Разбирам ли load factor и resize/rehash?
[ ] Разбирам ли average vs worst case?
[ ] Мога ли да обясня tombstone deletion bug?
[ ] Знам ли кога hashing не е правилната структура?
```
