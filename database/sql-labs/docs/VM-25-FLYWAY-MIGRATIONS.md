# VM-25 — Flyway versioned schema migrations, checksums и безопасен DDL lifecycle

## Production казус

Developer променя Entity mapping и разрешава `hibernate.hbm2ddl.auto=update` в production. При следващо deploy схемата е променена без преглед, а два service instance-а стартират едновременно. Историята на schema evolution липсва и не знаем защо production е различна от test.

Друг разработчик редактира `V1__create.sql`, въпреки че тя вече е приложена. **Това трябва да бъде засечено**, не silently overwritten.

## Bad → good

**Bad:** implicit ORM schema mutation при boot, ръчни SQL инструкции без version control, редактиране на приложени migration файлове.

**Good:** immutable versioned migrations с Flyway, explicit schema history, checksum validation, review и отделно application-level schema validation.

Фикстури:

- [V1__create_vm25_account.sql](../src/test/resources/vm25/sql/V1__create_vm25_account.sql) създава таблица с PK, UNIQUE и CHECK.
- [V2__add_active_flag.sql](../src/test/resources/vm25/sql/V2__add_active_flag.sql) добавя nullable-safe `BOOLEAN NOT NULL DEFAULT TRUE`.
- [PostgresFlywayMigrationIT](../src/test/java/bg/hristomanov/education/sql/PostgresFlywayMigrationIT.java) стартира реален PostgreSQL 17, прилага миграциите и проверява history, migration count, `active` default, second run с 0 migrations.
- Negative experiment: копие на `V1` в temporary directory, `migrate`, после промяна на checksum: `validateWithResult()` трябва да върне failure. **Не редактираме истинските миграции**, за да постигнем теста.

## Mental model

```text
Review SQL + app compatibility
    ↓
Flyway schema_history & validation
    ↓
Apply pending V1 → V2 migrations, one-by-one
    ↓
Application starts with validated schema
    ↓
Monitor migration result and rollback strategy
```

Версиите са immutable след application. Нови промени са **нови Vn файлове**, а не overwrite на V1.

## Migration rollback не е същото като бизнес rollback

PostgreSQL обичайно поддържа transactional DDL, но някои команди изискват специален режим (например `CREATE INDEX CONCURRENTLY`). Oracle има различни DDL commit semantics — не обещавай, че всички schema migrations могат да се върнат с `ROLLBACK`. Продукционен rollback често е **forward fix**, backup/restore plan, feature toggles и expand/contract deployment.

## Decision contract

| Въпрос | Решение |
|---|---|
| Проблем | Schema drift, недокументирани DDL, редактирани applied migrations |
| Кога | Всяко дългосрочно приложение с versioned relational schema |
| Кога НЕ | Няма универсално auto-rollback на destructive DDL; не ползвай `clean` върху production |
| Печалба | Reproducible schema history, checksum safety и code review |
| Загуба | Миграции, deployment coordination, backward compatibility discipline |
| Алтернатива | Liquibase е валидна алтернатива; manual SQL само при добре управляван процес |
| Доказателство | [Flyway PostgreSQL IT](../src/test/java/bg/hristomanov/education/sql/PostgresFlywayMigrationIT.java) |

## Стартиране

`mvn -pl database/sql-labs -Ppostgres-integration verify`

## Production checklist

Миграциите напред съвместими ли са с предишното приложение? Коя услуга ги изпълнява? Права за DDL? Backup и rollback/forward-fix plan? Индекси без блокиране? Еднакви `schema_history` и checksum на всички среди? За JPA: предпочитай `ddl-auto=validate`, не `update/create-drop` в production.

## Оригинални източници

- [Flyway PostgreSQL driver support](https://documentation.red-gate.com/flyway/reference/database-driver-reference/postgresql-database)
- [Spring Boot Database Initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html)
- [PostgreSQL 17 transactional DDL](https://www.postgresql.org/docs/17/sql-begin.html)
- [Vlad Mihalcea SQL tutorials](https://vladmihalcea.com/tutorials/sql/)
