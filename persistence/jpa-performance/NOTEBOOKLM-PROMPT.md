# NotebookLM — аудио урок по Spring Boot / Hibernate performance (P1)

## Източници за импортиране

1. [Основният урок](./README.md) — това е **водещият** учебен материал.
2. [Практическите лаборатории](./EXERCISES.md) — за проверки, измервания и задачи.
3. [Оригиналната лекция от Devoxx](https://www.youtube.com/watch?v=ePBmAdRYl10).
4. [FunFactStore на Jos Roseboom](https://github.com/JosRoseboom/funfactstore).
5. [Spring Boot SQL Databases](https://docs.spring.io/spring-boot/reference/data/sql.html), [Hibernate ORM](https://docs.hibernate.org/orm/7.1/userguide/html_single/Hibernate_User_Guide.html) и [Spring Data Projections](https://docs.spring.io/spring-data/jpa/reference/4.0/repositories/projections.html) за проверка на технически твърдения.

## Prompt за Audio Overview

> Създай **задълбочен аудио урок на български език за Mid+ → Early Senior Java/Spring backend developer**. Не прави общо резюме на конференционното видео. Следвай реален инцидент: Spring Boot endpoint чете база, чака външна услуга и пак чете база; оттам стигни до connection pool exhaustion, transaction boundary, OSIV, lazy physical checkout, N+1 и DTO projections.
>
> **Структура:** 1) входен производствен проблем и защо се вижда само под натоварване; 2) 10–15 минути как точно работят JDBC, HikariCP, Hibernate, transaction и persistence context като различни lifecycle-и; 3) 10–15 минути fetch plans: EAGER/LAZY, Identity Map, N+1, JOIN FETCH, EntityGraph, DTO; 4) 5–10 минути Senior diagnosis и измервания; 5) кратко финално повторение и практически въпроси.
>
> За всяко подобрение използвай схема **наивен код → защо е привлекателен → точен root cause → improved code/flow → как го доказваме → limitations**. Обяснявай причинно-следствено и с правилните английски Java/Spring термини, като ги поясняваш на български при първа употреба.
>
> Бъди прецизен: `@Transactional` не е физическа връзка; OSIV ≠ задължително physical connection през цялата заявка; `connection-fetch=lazy` отлага checkout само до първото SQL statement и не освобождава вече взет connection между две statements; `@Transactional(readOnly=true)` не гарантира липса на SQL; persistence context Identity Map ≠ DB `REPEATABLE READ`; LAZY не предотвратява N+1; JOIN FETCH на големи collections + pagination може да вреди; projection vs entity е избор по use case.
>
> **Примери:** използвай Java 25 style с explicit types, Spring Boot 4.1 property и PostgreSQL/Oracle caveats. Авторският FunFactStore използва Java 26 и Spring Boot 4.1.0 — обясни разликата с основния education проект. Събития, timings и SQL counts от демото са илюстративни за средата на автора; не ги обобщавай като benchmark за всеки проект.
>
> Вмъкни две кратки симулации на code review: (а) SQL → remote call → SQL в `@Transactional`; (б) `findAll()`, после loop през `getAdmin().getDomain()`. Нека единият водещ играе backend developer, който първоначално предлага „повече connections“ или „сложи EAGER навсякъде“, а другият да обясни точно защо това може да е грешно и как да се измери.
>
> Завърши с **3 диагностични въпроса без готови отговори** и 60-секундна схема за production troubleshooting. Не казвай, че обучаемият е усвоил материала, преди да го е демонстрирал с отговори и тестове. Не измисляй детайли, които не присъстват в източниците.

## Как се използва

Прочети основната глава, после чуй аудиото, а след това реши [Lab 6](./EXERCISES.md) **без да гледаш решението**. Запис на прогрес се прави само след реална проверка по [диагностичния протокол](../../docs/learning/ASSESSMENT-PROTOCOL.md).

