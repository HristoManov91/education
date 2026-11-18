# VM-16 — Spring Data JPA pitfalls: findAll, findById, getReferenceById, Page/Slice

## Начален проблем

Фронтендът извиква „дай 20 поръчки“, а backend използва `repository.findAll()` и `stream().limit(20)`. SQL изтегля всички редове; при 2 милиона записа API-то е бавно и консумира много памет. Друг service иска само да закачи foreign key към съществуващ customer, но винаги извиква `findById` и изпълнява ненужен SELECT.

**Spring Data JPA намалява boilerplate, но не замества умението да мислим за SQL.**

## Път 1 — ограничение в DB, не в Java

~~~java
// Bad: няма SQL limit, създава/хидратира всички managed entities.
List<PerfOrder> firstTwenty = repository.findAll().stream().limit(20).toList();

// Good: Page е bounded; ако total не трябва, предпочети Slice.
Page<PerfOrder> page = repository.findAll(PageRequest.of(0, 20, Sort.by("id")));
~~~

**Page vs Slice:** `Page` предлага `totalElements/totalPages` и обичайно изпълнява **допълнителен COUNT**. `Slice` знае само `hasNext` и може да прочете `pageSize + 1` без COUNT. В [PerfOrderRepository](../src/main/java/bg/hristomanov/education/hperf/repository/PerfOrderRepository.java) има derived method, връщащ `Slice<PerfOrder>`. [Тест](../src/test/java/bg/hristomanov/education/hperf/HibernatePerformanceTest.java) измерва 2 prepared SQL за Page и 1 за Slice върху фикстура с 7 поръчки.

## Път 2 — findById vs getReferenceById

- `findById(id)` зарежда entity (може да използва първо ниво cache, ако е вече managed).
- `getReferenceById(id)` може да върне lazy proxy, без да изпълни SELECT **в момента на извикването**. Достъп до non-ID поле може да trigger-не SELECT; несъществуващ ID може да хвърли exception при initialization или FK грешка при flush.
- За свързване на FK към вече известен/валиден customer може да е подходящ `getReferenceById`. Ако бизнесът иска да валидира наличието/достъпа — ползвай `findById` / explicit query, не proxy като проверка.

[Spring Data test](../src/test/java/bg/hristomanov/education/hperf/HibernatePerformanceTest.java) прави `getReferenceById` + четене на ID при 0 SQL, после `findById` при 1 SQL (след clear на persistence context). Това доказва конкретния Hibernate/H2 mapping, не всеки вид bytecode enhancement.

## Път 3 — save() и DTO

`save()` върху managed entity може да не е нужен: Hibernate dirty checking ще изпрати UPDATE при flush/commit. `save()` за **нова entity** е валиден; за detached entity може да доведе до `merge` semantics. Не превръщай тези случаи в една догма.

Често в read-only REST endpoint най-добрият изход е **DTO projection**, а не managed entity. Виж [VM-13](VM-13-DTO-PAGINATION.md) с `OrderRow` record. Така Jackson няма случайно да обходи LAZY association.

## Decision contract

| Въпрос | Отговор |
|---|---|
| Проблем | Unbounded findAll, скрит COUNT, ненужен SELECT и грешни proxy assumptions |
| Кога Page | Когато UI трябва точен total/number of pages |
| Кога НЕ Page | Infinite scroll / next-page UX — Slice или keyset може да е по-подходящ |
| Печалба | Ограничен SQL result, предсказуеми API контракти |
| Загуба | COUNT cost, deep OFFSET, допълнителни query/DTO класове |
| Алтернатива | Explicit JPQL/SQL projection, Keyset, Slice, getReference при валиден FK |
| Доказателство | [PerfOrderRepository](../src/main/java/bg/hristomanov/education/hperf/repository/PerfOrderRepository.java) + [H2 test](../src/test/java/bg/hristomanov/education/hperf/HibernatePerformanceTest.java) |

## Изходен checklist

- Кой ограничава резултата — DB или Java?
- Нужен ли е точен total, или само hasNext?
- Предвиждам ли SELECT от getReference и какво става при невалиден FK?
- Managed ли е entity преди да извикам save?
- Може ли endpoint-ът да върне само DTO вместо entity graph?

### Оригинални източници

- Vlad — [Spring Data findAll anti-pattern](https://vladmihalcea.com/spring-data-findall-anti-pattern/); [Spring Data JPA findById anti-pattern](https://vladmihalcea.com/spring-data-jpa-findbyid/).
- [Spring Data JPA paging and counting](https://docs.spring.io/spring-data/jpa/reference/repositories/query-methods-details.html).
- [Spring Data projections](https://docs.spring.io/spring-data/jpa/reference/repositories/projections.html).
