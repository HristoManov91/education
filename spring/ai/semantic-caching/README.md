# Semantic Caching — exact key match vs meaning similarity

Този module е отделен от класическите Redis/Spring caching labs, защото решава различен problem:

> **Можем ли да reuse-нем скъп AI/LLM response, когато новият prompt не е textually identical, но е semantically equivalent?**

Основният видео източник е:

- JavaOne 2026 / Inside Java — **Caching for Agentic Java Systems: Internal, Distributed, and Semantic**  
  https://youtu.be/YPxMiaXToWs

Допълнителен контекст:

- SpringDeveloper — Spring & Redis with Raphael De Lio  
  https://www.youtube.com/live/KUNNslg-RQw

---

# ВХОД В ТЕМАТА

## 1. Exact cache

Traditional cache:

```text
key = exact input
```

Пример:

```text
"What is the capital of France?"
```

и:

```text
"Tell me France's capital city"
```

са различни strings.

Exact cache:

```text
query A
→ HIT only for query A

query B
→ MISS
```

дори business answer-ът да е същият.

---

# 2. Semantic cache

Semantic cache прави:

```text
query
→ embedding vector
→ similarity search
→ closest cached query
→ threshold check
```

Ако similarity е достатъчно висока:

```text
reuse cached AI response
```

без нов model call.

Potential benefits:

- по-ниска latency;
- по-ниска inference цена;
- по-малко model traffic;
- по-consistent answers за equivalent questions.

---

# 3. Executable lab без external AI API

CI не трябва да зависи от:

- OpenAI/Anthropic key;
- network;
- paid inference;
- stochastic embeddings.

Затова executable lab-ът използва:

- [`KeywordEmbeddingModel.java`](./src/main/java/bg/hristomanov/education/semanticcache/KeywordEmbeddingModel.java) — deterministic educational embedding;
- [`CountingAiModel.java`](./src/main/java/bg/hristomanov/education/semanticcache/CountingAiModel.java) — fake expensive model с call counter;
- [`SemanticResponseCache.java`](./src/main/java/bg/hristomanov/education/semanticcache/SemanticResponseCache.java) — inspectable linear vector search.

Това НЕ е production vector database.

Идеята е да докажем semantics-а стабилно.

Production mapping е описан по-долу със Spring AI 2.0.1 + Redis Semantic Cache.

---

# README → код

| Концепция | Код | Test |
| --- | --- | --- |
| Exact baseline | [`ExactResponseCache.java`](./src/main/java/bg/hristomanov/education/semanticcache/ExactResponseCache.java) | [`SemanticCachingTest.java`](./src/test/java/bg/hristomanov/education/semanticcache/SemanticCachingTest.java) |
| Context identity | [`CacheContext.java`](./src/main/java/bg/hristomanov/education/semanticcache/CacheContext.java) | same |
| Embedding abstraction | [`TextEmbeddingModel.java`](./src/main/java/bg/hristomanov/education/semanticcache/TextEmbeddingModel.java) | same |
| Deterministic embedding | [`KeywordEmbeddingModel.java`](./src/main/java/bg/hristomanov/education/semanticcache/KeywordEmbeddingModel.java) | same |
| Vector cache | [`SemanticResponseCache.java`](./src/main/java/bg/hristomanov/education/semanticcache/SemanticResponseCache.java) | same |
| Expensive model fixture | [`CountingAiModel.java`](./src/main/java/bg/hristomanov/education/semanticcache/CountingAiModel.java) | same |
| Orchestration | [`SemanticCachingService.java`](./src/main/java/bg/hristomanov/education/semanticcache/SemanticCachingService.java) | same |

---

# 4. Paraphrase hit

Test:

`exactCacheMissesParaphraseButSemanticCacheHitsIt()`

Input A:

```text
What is the capital of France?
```

Input B:

```text
Tell me France's capital city
```

Exact cache:

```text
B → MISS
```

Semantic cache:

```text
embedding(A)
embedding(B)
cosine similarity > 0.95
→ HIT
```

Това е basic value proposition.

---

# 5. Similarity threshold е correctness policy

Threshold:

```text
0.99
→ много strict
→ по-малко false hits
→ повече misses

0.70
→ по-lenient
→ повече hits
→ по-голям risk от wrong answer reuse
```

Hit rate сам по себе си не е quality metric.

Semantic cache трябва да optimize-ва:

```text
cost + latency
subject to
answer correctness
```

---

# 6. False semantic hit

Това е най-важният AI caching risk.

Test:

`lenientThresholdCanCreateFalseSemanticHit()`

Cached query:

```text
What is my account balance?
```

New query:

```text
What was my account balance yesterday?
```

Embedding similarity може да е висока, защото:

```text
account
balance
```

са почти еднакви.

Но business semantics са различни:

```text
current state
≠
historical state
```

При lenient threshold test-ът нарочно получава wrong cached answer.

При stricter threshold същата заявка става MISS.

Ключов извод:

> **Semantic similarity не доказва business equivalence.**

---

# 7. Context isolation

Query meaning не е единственият input към LLM response.

Answer може да зависи от:

```text
tenant
system prompt
model version
RAG / knowledge-base version
locale
permissions
tools
conversation context
```

Lab context:

- [`CacheContext.java`](./src/main/java/bg/hristomanov/education/semanticcache/CacheContext.java)

прави fingerprint върху:

```text
tenantId
systemPromptVersion
modelVersion
knowledgeBaseVersion
locale
```

Test:

`contextFingerprintPreventsCrossTenantOrConfigurationHits()`

доказва:

```text
same semantic query
+ different tenant
→ MISS

same semantic query
+ newer knowledge base
→ MISS
```

Cross-tenant semantic hit би бил data leak, не cache optimization.

---

# 8. Model version

Ако:

```text
model-v1
→ answer A
```

и после deploy-нем:

```text
model-v2
```

стар cache response може вече да не представлява новия model behavior.

Затова model identity/version често е част от semantic cache context.

---

# 9. System prompt version

Промяна:

```text
You are a concise assistant
```

към:

```text
You are a detailed technical tutor
```

може да промени response дори user query да е същият.

Следователно:

```text
user prompt only
```

често е недостатъчен cache identity.

---

# 10. RAG / knowledge-base version

Semantic cached answer може да е correct вчера и stale днес.

Пример:

```text
KB v1
→ cached answer

new documents indexed
→ KB v2
```

Ако cache context не знае за KB version, можем да продължим да връщаме стар answer.

Options:

- knowledge base version in context;
- TTL;
- event-driven invalidation;
- document-version dependency tracking.

---

# 11. TTL остава важен

Semantic cache не отменя time freshness.

Test:

`ttlRemovesSemanticEntry()`

показва:

```text
weather answer
TTL = 5 min
→ HIT before expiration
→ MISS after expiration
```

Time-sensitive questions трябва да имат по-кратка lifetime policy.

General knowledge може да има по-дълга.

---

# 12. Cost proof

Test:

`semanticHitAvoidsSecondExpensiveModelCall()`

Flow:

```text
query A
→ semantic MISS
→ model call #1
→ cache response

paraphrase B
→ semantic HIT
→ model call НЕ се изпълнява
```

Assertion:

```text
model call count = 1
```

Това е operational reason за semantic caching.

---

# 13. Cosine similarity

Lab използва cosine similarity:

```text
similarity(A, B) =
dot(A,B) / (|A| * |B|)
```

Range:

```text
1.0
→ vectors point in same direction

0.0
→ unrelated/orthogonal in simplified model
```

Production embedding space е много по-високо-dimensional и nuanced.

Но threshold problem-ът остава същият.

---

# 14. Linear scan vs vector index

Lab:

```text
for every cached embedding
→ cosine similarity
→ choose best
```

Complexity:

```text
O(N * dimensions)
```

Това е умишлено за teaching.

Production:

```text
HNSW
FLAT
vector range/KNN search
```

Redis vector search / Spring AI скриват тази indexing layer.

---

# 15. Spring AI 2.0.1 SemanticCache

Към текущия stable Spring AI **2.0.1** има first-class:

```text
SemanticCache
DefaultSemanticCache
SemanticCacheAdvisor
Redis semantic-cache auto-configuration
```

Official API поддържа:

- semantic retrieval;
- TTL;
- context hash;
- Redis vector backing store.

Conceptual mapping:

```text
our CacheContext.fingerprint()
≈
Spring AI contextHash
```

и:

```text
our SemanticResponseCache
≈
production DefaultSemanticCache backed by Redis vector search
```

---

# 16. Spring AI Redis production shape

Official starter:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-vector-store-redis-semantic-cache</artifactId>
</dependency>
```

Typical flow:

```text
ChatClient
→ SemanticCacheAdvisor
→ query embedding
→ Redis vector search

hit
→ cached ChatResponse

miss
→ ChatModel
→ store query + response embedding
```

Този module не pin-ва Spring AI dependency, защото искаме основният semantic-cache algorithm да остава runnable без Redis Stack/model download/API credentials.

README обаче е versioned към Spring AI 2.0.1 current stable API.

---

# 17. Context hash не решава всичко

Context isolation пази:

```text
tenant A
от
tenant B
```

но не разбира автоматично semantic nuance вътре в query.

Current vs historical balance example пак може да е false hit в един и същ context.

Трябват и:

- threshold;
- domain-specific exclusions;
- metadata filters;
- temporal intent detection;
- endpoint-specific policy.

---

# 18. Кога НЕ трябва да semantic-cache-ваме

High-risk examples:

- account balance;
- permissions;
- authorization;
- medical status;
- legal/financial decisions;
- highly personalized live data;
- requests with hidden tool/context dependencies.

Причината:

```text
near meaning
може да не означава
same correct answer
```

Exact cache или no cache може да е по-безопасно.

---

# 19. Semantic cache vs RAG

RAG:

```text
query
→ retrieve source documents
→ model generates answer
```

Semantic cache:

```text
query
→ retrieve previous similar query/response
→ skip generation
```

RAG подобрява grounding.

Semantic cache reuse-ва previous result.

Могат да се комбинират, но не са едно и също.

---

# 20. Semantic cache vs agent memory

Cache:

```text
performance optimization
reusable result
disposable
system трябва да остане correct при cache loss
```

Memory:

```text
information agent should remember
part of future behavior/context
deleting it can change semantics
```

Ако user preference е essential memory, не го моделираме просто като cache entry.

---

# 21. Semantic cache vs exact cache layering

Common hierarchy:

```text
L1 exact local cache
↓ miss
L2 exact distributed cache
↓ miss
semantic vector cache
↓ miss
LLM
```

Exact matches са:

- по-евтини;
- по-сигурни;
- deterministic.

Semantic layer се използва само когато exact cache не помогне.

---

# 22. Metrics

Не гледай само:

```text
semantic hit rate
```

Нужни са:

- exact hit rate;
- semantic hit rate;
- model-call avoidance;
- latency saved;
- cost saved;
- similarity distribution;
- false-hit / answer-quality evaluation;
- cache age;
- invalidations;
- hits by context/model/KB version.

Semantic cache с 95% hit rate може да е ужасен, ако 5% от hits са wrong in critical domain.

---

# 23. Evaluation dataset

Threshold не трябва да се избира на око.

Направи labelled pairs:

```text
query A
query B
should reuse same answer? yes/no
```

После measure:

- true semantic hits;
- false semantic hits;
- false misses;
- cost/latency improvement.

Това превръща threshold tuning от intuition в experiment.

---

# Какво доказват тестовете

[`SemanticCachingTest.java`](./src/test/java/bg/hristomanov/education/semanticcache/SemanticCachingTest.java)

## Exact vs semantic

```text
paraphrase
→ exact MISS
→ semantic HIT
```

## Context isolation

```text
different tenant/KB version
→ MISS
```

## False hit

```text
current balance
vs historical balance
→ high similarity
→ lenient threshold wrong HIT
```

## TTL

```text
entry expires
→ semantic MISS
```

## Cost

```text
two semantically equivalent prompts
→ one model call
```

---

# Как да стартираме

Няма external services:

```bash
mvn -pl spring/ai/semantic-caching -am test
```

---

# Mental model за запомняне

1. **Exact cache сравнява identity; semantic cache сравнява meaning approximation.**
2. **Similarity score не е proof за answer equivalence.**
3. **Threshold е correctness/cost policy.**
4. **Context трябва да включва inputs, които променят answer semantics.**
5. **Tenant/permission isolation е security requirement.**
6. **Model/system-prompt/KB version могат да invalidират old responses.**
7. **TTL остава нужен за time-sensitive knowledge.**
8. **Semantic cache е disposable optimization, не agent memory.**
9. **Vector index е implementation detail; semantic policy е по-важна.**
10. **False-hit evaluation е по-важна от максимален hit rate.**

---

# Code-review checklist

```text
[ ] Exact cache проверява ли се преди semantic cache?
[ ] Какъв embedding model/version използваме?
[ ] Threshold empirical ли е?
[ ] Има ли labelled false-hit evaluation?
[ ] Tenant/user permission context включен ли е?
[ ] System prompt version включен ли е?
[ ] Model version включен ли е?
[ ] RAG/KB version включен ли е?
[ ] Locale/tool context влияят ли на answer-а?
[ ] TTL съответства ли на freshness requirement?
[ ] Time-sensitive/critical prompts изключени ли са?
[ ] Metrics броят ли false semantic hits?
[ ] Cache loss променя ли correctness? Ако да, може би това е memory/state, не cache.
```

---

# Оригинални източници

- Inside Java / JavaOne — Caching for Agentic Java Systems: Internal, Distributed, and Semantic:  
  https://inside.java/2026/05/18/javaone-caching-agentic-ai/
- Видео:  
  https://youtu.be/YPxMiaXToWs
- Spring AI 2.0.1 — SemanticCache API:  
  https://docs.spring.io/spring-ai/docs/2.0.x/api/org/springframework/ai/chat/cache/semantic/SemanticCache.html
- Spring AI — Redis Vector Store / Semantic Caching:  
  https://docs.spring.io/spring-ai/reference/api/vectordbs/redis.html
- Spring AI releases — 2.0.1 current stable line:  
  https://github.com/spring-projects/spring-ai/releases

---

# Изходен въпрос

Когато semantic cache каже:

> similarity = 0.93, това е HIT

питай:

> **0.93 similarity означава ли действително, че същият answer е correct при същия tenant, system prompt, model, knowledge version, time semantics и permissions — или само че изреченията звучат подобно?**
