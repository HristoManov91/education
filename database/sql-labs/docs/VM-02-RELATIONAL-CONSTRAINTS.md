# VM-02 — Релационен модел, ограничения и цена на integrity

**Реален казус:** два паралелни backend instance-а създават поръчка с един и същ `external_ref` за същия клиент. Java проверка „провери дали съществува, после insert“ допуска race. Отделно, batch import вмъква поръчка за несъществуващ клиент.

**Решение:** business invariants, които базата може да гарантира, принадлежат в `PRIMARY KEY`, `UNIQUE`, `FOREIGN KEY`, `NOT NULL` и `CHECK`.

## Основни понятия

| Constraint | Гаранция | Подводен камък |
| --- | --- | --- |
| `PRIMARY KEY` | Уникален, non-null идентификатор | Избор между natural и surrogate key променя schema и индекси |
| `UNIQUE(customer_id,external_ref)` | Не дублираме customer order reference | Гаранцията идва от DB concurrency control, не от precheck |
| `FOREIGN KEY(customer_id)` | Child сочи към наличен parent | Индекс на **referencing** колоните не винаги се създава автоматично |
| `CHECK(total >= 0)` | Няма отрицателни totals | `CHECK` не отхвърля UNKNOWN: добавяме `NOT NULL` |
| `NOT NULL` | Стойността действително присъства | Oracle третира empty string `''` като `NULL` за character fields |

Прегледай реалните [schema.sql](../src/main/resources/sql/postgresql/schema.sql) и [seed.sql](../src/main/resources/sql/postgresql/seed.sql).

## Bad → защо привидно работи → good

**Bad: само application-level validation.**

~~~java
if (!repository.existsByCustomerIdAndExternalRef(customerId, reference)) {
    repository.save(order);
}
~~~

Два concurrent request-а могат да изпълнят `exists=false`, преди някой да запише. Това е TOCTOU race; не го премахваме чрез по-бърза заявка или `synchronized` само в един JVM.

**Good: DB-level UNIQUE constraint + превод на конфликт към бизнес грешка**; precheck може да остане само за UX, но DB constraint е последната гаранция.

~~~sql
CONSTRAINT uq_order_customer_reference UNIQUE (customer_id, external_ref),
CONSTRAINT fk_purchase_order_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
CONSTRAINT ck_order_total CHECK (total >= 0)
~~~

Приложението хваща constraint error и го превежда на подходящ API response. Не твърди, че всички SQLState/ORA error codes са еднакви.

В [H2 negative tests](../src/test/java/bg/hristomanov/education/sql/H2SqlSemanticsTest.java) опитваме duplicate email, orphan child и invalid status/amount. В [PostgreSQL negative tests](../src/test/java/bg/hristomanov/education/sql/PostgresSqlLabIT.java) проверяваме `23503` (FK) и `23514` (CHECK) върху **реален PostgreSQL**, не върху H2.

## Моделиране: surrogate vs natural key

- Surrogate `id BIGINT` стабилизира FK референциите при промяна на business атрибути.
- Natural `email` има бизнес смисъл, но може да се промени; пазим `UNIQUE` върху него, без да го използваме за PK.
- За системи с multi-tenancy често е нужен съставен uniqueness contract (пример `tenant_id` + business reference), а не глобална уникалност.

**Не създавай индекс „за всеки FK“ сляпо:** изследвай DML trade-off, използването на JOIN и parent delete/update пътищата. Някои FK колони се нуждаят от индекс, други не — причината е workload.

## Decision contract

| Въпрос | Нашият избор |
| --- | --- |
| Решаваме | Идентичност, referential integrity и race-proof business invariants |
| Кога | При ясни cross-row uniqueness и parent-child relationship rules |
| Кога НЕ | Не изразявай сложни multi-row агрегатни invariants с `CHECK` върху други редове; ползвай транзакционен модел/процедура според случая |
| Печелим | Коректност при множество инстанции, import jobs и direct SQL |
| Губим | Допълнителни индекси и write overhead; миграциите изискват валидиране на legacy data |
| Алтернатива | Java validation е добра за бърз feedback, но не замества constraint под concurrency |
| Доказваме | H2 + PostgreSQL negative integration tests и специфични SQLState |

## PostgreSQL / Oracle граница

- PostgreSQL [UNIQUE/PK](https://www.postgresql.org/docs/17/ddl-constraints.html) автоматично създават supporting unique B-tree index, но FK **referencing** колоната не получава автоматично индекс. Не дублирай PK/UNIQUE индекс без причина.
- Oracle 19c [constraint management](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/constraint.html): различни типове, error codes, lifecycle; Oracle `VARCHAR2` `''` се третира като NULL.
- [Oracle manual DDL](../sql/oracle/schema.sql) е учебен пример и **не е проверен с Oracle integration test**.
- Native concurrency демонстрацията с две транзакции е планирана в VM-06/VM-07; не смесваме този initial constraints урок с недоказана isolation лаборатория.

## README → код и връщане към инцидента

- [schema.sql](../src/main/resources/sql/postgresql/schema.sql) — точното invariant definition.
- [PostgresSqlLabIT](../src/test/java/bg/hristomanov/education/sql/PostgresSqlLabIT.java) — query failure е позитивното доказателство, че DB отказва недопустимо състояние.
- [SqlReadRepository](../src/main/java/bg/hristomanov/education/sql/SqlReadRepository.java) — чете валидния релационен модел; Java не дублира constraint enforcement.

**Code review checklist:** ключовете стабилни ли са? Има ли нужната съставна уникалност? `CHECK` + `NOT NULL`? Съществува ли риск от application-only race? Дублираме ли индекс? Как API превежда duplicate/FK грешките?

### Оригинални източници

- Vlad Mihalcea — [SQL tutorials: keys/indexing](https://vladmihalcea.com/tutorials/sql/), [Index Selectivity](https://vladmihalcea.com/index-selectivity/).
- [PostgreSQL 17 constraints](https://www.postgresql.org/docs/17/ddl-constraints.html), [unique indexes](https://www.postgresql.org/docs/17/indexes-unique.html).
- [Oracle Database 19c SQL Constraints](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/constraint.html).
