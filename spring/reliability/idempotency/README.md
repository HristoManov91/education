# Idempotency — safe retries without duplicate side effects

Този модул започва reliability/distributed-systems частта от backend patterns roadmap-а.

Реалният проблем е прост:

```text
client sends POST /payments
server creates payment
response is lost / client times out
client cannot know whether operation succeeded
client retries
```

Без idempotency вторият POST може да създаде второ плащане.

---

# 1. Idempotent operation mental model

Операция е idempotent, ако повторното ѝ изпълнение със същия logical request няма допълнителен ефект след първото успешно изпълнение.

```text
apply once  → state S1
apply twice → state S1
apply N times → state S1
```

Това не означава непременно, че HTTP response е byte-for-byte еднакъв. Contract-ът трябва изрично да дефинира какво се replay-ва.

---

# 2. Защо POST retry е опасен

GET по HTTP semantics е safe/idempotent by design intent. POST обикновено не е.

Payment creation:

```text
POST #1
→ payment P1

POST #2 retry
→ payment P2   ← BUG
```

Client timeout не означава server failure.

```text
server: COMMIT success
network: response lost
client: sees timeout
```

Retry е естествен, но side effect-ът вече може да е изпълнен.

---

# 3. Idempotency key

Client изпраща unique logical-operation key:

```http
Idempotency-Key: payment-demo-001
```

Server използва key-а, за да разпознае retries.

В лабораторията еднакъв key + еднакъв logical payload връща съществуващия payment.

```text
first request
→ create payment 42

retry with same key
→ return payment 42
→ do NOT create payment 43
```

---

# 4. Важна standards бележка

`Idempotency-Key` е широко използвана API convention, но към датата на този урок IETF документът за header-а е expired Internet-Draft, а не публикуван RFC.

Следователно API-то трябва да документира собствените си semantics:

- scope на key-а;
- allowed length/format;
- retention period;
- payload mismatch behavior;
- response replay behavior;
- concurrent-request behavior.

Не разчитаме на невалидно предположение, че всички API-та имат еднаква idempotency semantics.

---

# 5. Request fingerprint

Key-ът не трябва да може да се reuse-не за различна business operation.

Пример:

```text
key = abc
amount = 100 EUR

после:

key = abc
amount = 999 EUR
```

Това не е retry. Това е conflict.

[`PaymentRequestFingerprint.java`](./src/main/java/bg/hristomanov/education/idempotency/service/PaymentRequestFingerprint.java) прави stable SHA-256 fingerprint върху canonical payload.

Canonicalization:

```text
120.00 EUR
120.0 eur
```

се третират като еднакъв logical payload.

[`PaymentRequestFingerprintTest.java`](./src/test/java/bg/hristomanov/education/idempotency/PaymentRequestFingerprintTest.java) доказва това.

---

# README → код

| Концепция | Код | Доказателство |
| --- | --- | --- |
| HTTP request | [`CreatePaymentRequest.java`](./src/main/java/bg/hristomanov/education/idempotency/api/CreatePaymentRequest.java) | service tests |
| DB correctness barrier | [`Payment.java`](./src/main/java/bg/hristomanov/education/idempotency/domain/Payment.java) | concurrency test |
| Fingerprint | [`PaymentRequestFingerprint.java`](./src/main/java/bg/hristomanov/education/idempotency/service/PaymentRequestFingerprint.java) | fingerprint test |
| Transactional create | [`PaymentCreationService.java`](./src/main/java/bg/hristomanov/education/idempotency/service/PaymentCreationService.java) | service tests |
| Idempotency orchestration | [`IdempotentPaymentService.java`](./src/main/java/bg/hristomanov/education/idempotency/service/IdempotentPaymentService.java) | [`IdempotentPaymentServiceTest.java`](./src/test/java/bg/hristomanov/education/idempotency/IdempotentPaymentServiceTest.java) |
| HTTP endpoint | [`PaymentController.java`](./src/main/java/bg/hristomanov/education/idempotency/api/PaymentController.java) | [`idempotency-demo.http`](./http/idempotency-demo.http) |

---

# 6. Naive check-then-act race

Наивният flow е:

```text
if key does not exist:
    create payment
else:
    return existing
```

При concurrency:

```text
T1: SELECT key → absent
T2: SELECT key → absent

T1: INSERT payment
T2: INSERT payment
```

Ако няма database uniqueness, можем да получим два business side effects.

Това е класически check-then-act race.

---

# 7. Database UNIQUE constraint е correctness barrier

`Payment` има unique constraint върху `idempotency_key`.

```text
T1: INSERT key K → success
T2: INSERT key K → UNIQUE violation
```

Така database-ът serialized-ва конкуриращата се претенция върху logical key-а.

Първият `findByIdempotencyKey()` в service-а е optimization за нормалния replay path.

Той НЕ е correctness guarantee.

Correctness идва от atomic database uniqueness.

---

# 8. Защо PaymentCreationService е отделен bean

[`PaymentCreationService.java`](./src/main/java/bg/hristomanov/education/idempotency/service/PaymentCreationService.java) има отделна `REQUIRES_NEW` transaction.

Причината:

```text
attempt INSERT
→ constraint violation
→ creation transaction rolls back
→ outer idempotency flow catches exception
→ reload winner
```

Ако се опитаме да продължим да работим вътре в transaction, която вече е marked rollback-only след database exception, можем да получим объркани transaction semantics.

Отделният bean също избягва Spring proxy self-invocation проблема.

---

# 9. Concurrent duplicate flow

[`IdempotentPaymentService.java`](./src/main/java/bg/hristomanov/education/idempotency/service/IdempotentPaymentService.java) прави:

```text
validate key + payload
→ calculate fingerprint
→ fast lookup for existing key

if existing:
    fingerprint equal?
      yes → replay
      no  → conflict

if absent:
    try INSERT in transaction
      success → created
      unique violation → reload winner → replay/conflict
```

Concurrency test-ът стартира 8 callers с един key.

Очакване:

```text
8 responses
1 original creation
7 replays
1 unique payment id
1 DB row
```

---

# 10. Same key + different payload

Това е misuse, не replay.

Lab behavior:

```text
same key
+ different fingerprint
→ IdempotencyKeyConflictException
→ HTTP 409
```

Това предпазва от случайно reuse-ване на key за нов business request.

---

# 11. Validation failure не трябва да consume-ва key-а

Ако request е invalid преди business execution:

```text
amount = -1
→ 400
→ no payment
→ key is still usable for corrected request
```

Тестът доказва точно това.

Idempotency lifecycle трябва да бъде свързан с реалното започване/резултат на operation-а, не само с факта, че HTTP request е пристигнал.

---

# 12. Какво точно replay-ваме?

Има няколко валидни design-а.

## В този lab

Пазим business result в payment row и reconstruct-ваме response:

```text
payment id
status
amount
currency
```

## По-строг API replay

Може да пазим:

- original HTTP status;
- response body;
- headers;
- error result.

Stripe например документира replay на първия status code/body, включително определени failure responses.

Няма универсална policy — contract-ът трябва да е explicit.

---

# 13. Key scope

В lab-а key е globally unique.

В production scope често е:

```text
tenant + endpoint + idempotency key
```

или:

```text
merchant + operation type + key
```

Иначе двама tenants могат случайно да collide-нат върху еднакъв client-generated key.

---

# 14. Key retention / expiry

Idempotency records не могат винаги да растат безкрайно.

Трябва policy:

```text
key valid for N hours/days
→ cleanup after retention
```

Но след cleanup reuse на стар key може да създаде нова operation.

Client и server трябва да знаят retention contract-а.

---

# 15. Natural idempotency

Не всяка operation има нужда от отделен idempotency table/key.

Ако business operation вече има immutable unique identifier:

```text
orderId
invoiceNumber
externalTransactionId
```

unique constraint върху него може да даде natural idempotency.

Не добавяме допълнителен mechanism, ако domain identity вече решава проблема.

---

# 16. Idempotency НЕ означава exactly-once execution

Това е фундаментално.

Можем да имаме:

```text
multiple attempts
multiple deliveries
multiple executions of guards
```

и все пак **един logical effect**.

По-полезният target в distributed systems често е:

> at-least-once attempts + idempotent effects

а не магическо exactly-once execution.

---

# 17. External irreversible side effect

Текущият lab създава DB payment record.

Ако реално извикваме card provider:

```text
charge external card
→ then INSERT idempotency row
```

unique constraint идва твърде късно — вече можем да сме charge-нали два пъти.

Production options:

- downstream provider също приема същия idempotency key;
- claim/idempotency state machine преди external call;
- durable workflow/outbox;
- reconciliation;
- provider transaction reference с uniqueness.

Idempotency трябва да обхване **истинския side effect**, не само последния DB INSERT.

---

# 18. Idempotent API vs Idempotent Consumer

HTTP idempotency:

```text
client retry
→ same logical API effect
```

Message consumer idempotency:

```text
broker redelivery
→ duplicate message
→ same logical consumer effect
```

Идеята е една, но storage/lifecycle semantics са различни.

Idempotent Consumer ще се върне при Outbox/Inbox модула.

---

# 19. Retry зависи от idempotency

Следващият reliability lab ще покрие Retry/Timeout/Circuit Breaker/Bulkhead.

Ключовата връзка е:

```text
Retry
+ non-idempotent side effect
= duplicate-risk multiplier
```

Затова Idempotency идва **преди Retry** в curriculum-а.

---

# 20. Как да стартираме

От root:

```bash
mvn -pl spring/reliability/idempotency -am test
```

Стартиране:

```bash
mvn -pl spring/reliability/idempotency spring-boot:run
```

IntelliJ HTTP Client:

- [`http/idempotency-demo.http`](./http/idempotency-demo.http)

---

# Какво доказват тестовете

[`IdempotentPaymentServiceTest.java`](./src/test/java/bg/hristomanov/education/idempotency/IdempotentPaymentServiceTest.java) доказва:

```text
same key + same payload
→ same payment id
→ one DB row

same key + different payload
→ conflict

invalid first attempt
→ key not consumed

8 concurrent duplicate requests
→ exactly one DB payment row
→ exactly one original response
→ all responses point to same payment
```

---

# Mental model за запомняне

1. **Timeout не означава failure.**
2. **Retry без idempotency може да удвои side effect-а.**
3. **SELECT-before-INSERT не е concurrency guarantee.**
4. **Database uniqueness е силна atomic correctness barrier за DB-local idempotency.**
5. **Same key + different payload трябва да бъде explicit conflict.**
6. **Idempotency трябва да обхване истинския side effect.**
7. **Целта е един logical effect, не непременно едно execution attempt.**

---

# Code-review checklist

```text
[ ] Operation naturally idempotent ли е?
[ ] Как client идентифицира logical retry?
[ ] Key scope включва ли tenant/operation boundary?
[ ] Same key + different payload какво прави?
[ ] Има ли DB UNIQUE/atomic claim, а не само check-then-act?
[ ] Concurrent duplicates покрити ли са с test?
[ ] Invalid request consume-ва ли key-а?
[ ] Какво точно replay-ваме?
[ ] Какъв е retention/expiry policy?
[ ] External side effect защитен ли е от duplicate execution?
[ ] Retry policy знае ли кои operations са idempotent?
```

---

# Упражнения

1. Направи scope `(tenantId, idempotencyKey)`.
2. Добави retention timestamp и cleanup job.
3. Запази exact HTTP response за replay.
4. Добави PROCESSING/COMPLETED/FAILED state machine.
5. Симулирай crash след key claim, преди completion.
6. Добави downstream gateway, който приема същия key.
7. Добави PostgreSQL Testcontainers concurrency test.
8. Добави Oracle implementation с equivalent unique constraint behavior.
9. Направи idempotent message consumer.
10. После добави Retry и докажи, че duplicate effect няма.

---

# Оригинални източници

- Stripe — Idempotent requests: https://docs.stripe.com/api/idempotent_requests
- AWS Builders Library — Making retries safe with idempotent APIs: https://aws.amazon.com/builders-library/making-retries-safe-with-idempotent-APIs/
- IETF HTTPAPI — Idempotency-Key draft source: https://github.com/ietf-wg-httpapi/idempotency
- IETF Datatracker — expired draft-ietf-httpapi-idempotency-key-header-07: https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/
- Microservices.io — Idempotent Consumer: https://microservices.io/patterns/communication-style/idempotent-consumer.html
- Microsoft Azure Architecture Center — Idempotent Consumer Pattern: https://learn.microsoft.com/azure/architecture/patterns/idempotent-consumer

---

# Изходен въпрос

Когато някой каже:

> ще retry-нем request-а

първият въпрос трябва да е:

> **Какво гарантира, че вторият attempt няма да произведе втори business side effect?**
