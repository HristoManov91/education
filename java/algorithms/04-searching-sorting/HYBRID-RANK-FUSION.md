# P3 · Hybrid Search — Reciprocal Rank Fusion (RRF) без Elasticsearch dependency

> Devoxx 2026, [20:50–30:00](https://youtu.be/QBQ11aL2aZc?t=1250). **Малък runnable Java25 / JUnit алгоритмичен пример** към [Searching & Sorting](README.md). Не настройваме Elasticsearch/embedding model: предметът е **fusion на вече получени ranked lists**.

## 1. Реална задача

В каталог за снимки един engine връща keyword matches (по filename/metadata), а втори намира semantic nearest neighbors от multimodal embeddings. Raw scores не са съпоставими — единият използва lexical scoring, другият cosine/distance. Наивното сумиране на scores може да привилегирова едната система.

**RRF:** комбинира **позиции**, не raw scores. За всеки документ `d`:

```text
RRF(d) = Σ over rankings containing d: 1 / (k + rank(d))
rank е 1-based; k е положителна ranking constant (често 60)
```

Пример classic `[A,B,C]` и semantic `[B,C,D]`:

```text
B: 1/(60+2) + 1/(60+1)  → най-високо
C: 1/(60+3) + 1/(60+2)  → второ
A: 1/(60+1)              → трето
D: 1/(60+3)              → четвърто
```

## 2. README → код → test

| Тема | Код | Доказателство |
| --- | --- | --- |
| RRF formula, deterministic tie break, limit | [ReciprocalRankFusion.java](src/main/java/bg/hristomanov/education/algorithms/searching/ReciprocalRankFusion.java) | [ReciprocalRankFusionTest.java](src/test/java/bg/hristomanov/education/algorithms/searching/ReciprocalRankFusionTest.java) |
| Duplicates in a single ranking | същият class (fail-fast) | negative test |
| Неравностойни raw scores | ranks only — без API за embedding similarity | expected ordering |

```bash
mvn -pl java/algorithms/04-searching-sorting -am -Dtest=ReciprocalRankFusionTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Изчислението е deterministic: при равни RRF scores печели по-рано срещнатият ID. Входът е изрично scoped към един tenant и вече филтриран за authorization; RRF НЕ е механизъм за security filtering.

## 3. Decision contract

| Въпрос | Отговор |
| --- | --- |
| Проблем | Два search retriever-а имат несъвместими raw scores. |
| Кога | Hybrid keyword + semantic results, когато можеш да сравниш rank order и да измериш relevance quality. |
| Кога НЕ | Един search retriever е достатъчен; нужни са калибрирани weights/raw score semantics; липсват достатъчно candidates; няма evaluation dataset. |
| Печалби | Лесна, независима от score ranges fusion стратегия. |
| Загуби | Игнорира magnitude на scores, нужен е tuning на k, допълнителни заявки/latency, ranking quality не е гарантирана. |
| По-просто | Само BM25/keyword или само vector search; library/backend-native RRF вместо собствен код. |
| Доказателство | [Deterministic JUnit](src/test/java/bg/hristomanov/education/algorithms/searching/ReciprocalRankFusionTest.java) за семантиката; в production се измерват NDCG/Recall@k, p95 и tenant isolation. |

**Изход:** първо оцени search relevance и latency; едва после реши дали да добавиш multimodal model и RRF. Тази Java имплементация е teaching oracle, **не** цел да заменим оптимизираното търсене на Elasticsearch.

## Оригинални източници

- [Devoxx, 20:50–30:00](https://youtu.be/QBQ11aL2aZc?t=1250) (Tactiq transcript).
- [DeepDiveJava25 demo repository](https://github.com/pioorg/DeepDiveJava25).
- [Original RRF paper — Cormack, Clarke, Büttcher, 2009](https://plg.uwaterloo.ca/~gvcormac/cormacksigir09-rrf.pdf).
- [Elasticsearch RRF overview](https://www.elastic.co/guide/en/elasticsearch/reference/current/rrf.html).
