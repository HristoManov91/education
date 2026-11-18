# VM-05..VM-07 — Проверка на знанията

## A. Connection lifecycle

1. Дай конкретен пример как pool=10 се изчерпва при нисък DB CPU. Защо увеличаване на pool не е автоматично решение?
2. Как се различават timeout при checkout, query timeout и SQL deadlock?
3. В [ConnectionLeaseTest](src/test/java/bg/hristomanov/education/tx/ConnectionLeaseTest.java) обясни защо първият checkout блокира втория и защо няма SQL по време на блокирането.

## B. MVCC / Isolation

1. Предскажи резултата от 2 SELECT в \`READ COMMITTED\`, когато друга transaction commit-не UPDATE между тях. После в \`REPEATABLE READ\`.
2. \`@Transactional(readOnly=true)\` гарантира ли \`REPEATABLE READ\`? Каква е разликата между entity-level first-level cache и DB snapshot?
3. Какво точно трябва да се retry-не при SQLSTATE 40001?

## C. Lost updates / locking

1. Имаме 10 бройки; 2 клиента правят \`read → +1 → write\`: защо резултатът може да е 11?
2. Какво означава \`executeUpdate() == 0\` в [ConnectionWork](src/main/java/bg/hristomanov/education/tx/ConnectionWork.java)?
3. Защо \`SKIP LOCKED\` е подходящ за batch job queue, но не и за справка с всички активни поръчки?
4. Начертай deadlock graph от \`id=1\` и \`id=2\`. Кой SQLSTATE очакваме в PostgreSQL?

## VM-18 — Streaming и statement cache

1. Защо положителен JDBC fetchSize не доказва малка heap памет, ако consumer добавя всеки ред в `List`?
2. Какво се променя при `autoCommit=true` за pgJDBC cursor-based retrieval?
3. Какъв риск имаме, когато обработката на stream отнеме минути и една DB connection стои заемана?
4. Как бих сравнил statement caching в pgJDBC с Oracle implicit caching без да измислям числа?
5. Изпълни [PostgresFetchingLabIT](src/test/java/bg/hristomanov/education/tx/PostgresFetchingLabIT.java) и обясни кои характеристики НЕ измерва.

## Команди

```bash
mvn -pl database/transaction-labs test
mvn -pl database/transaction-labs -Ppostgres-integration verify
```

**Задача за senior:** предложи 2 алтернативи за едновременно прехвърляне на пари между сметки: pessimistic locking с глобален lock order или SERIALIZABLE с bounded transaction retries; опиши correctness trade-offs, latency и failure handling. Не извиквай външна система между двата UPDATE в същата транзакция.

**Изход:** тестовете са доказателство за система, не автоматична оценка на знанието. Само проверени отговори означават `learned`.
