# 06 — Architecture Testing: ArchUnit + Taikai, без „празни зелени“ тестове

> **Devoxx Belgium 2026 · P2 · Java 25 · Spring Boot 4.1.1 · ArchUnit + Taikai 2.3.0**  
> Оригинална лекция: [Testing Architecture Made Simple — Gunter Rotsaert](https://www.youtube.com/watch?v=asAxisu6EQ0).  
> Основа на анализа: транскриптът, предоставен от обучаващия се. Примерите и проверките са адаптирани към нашия `education` проект и актуалната документирана API версия на Taikai. Това **не** е транскрипция на слайдовете или копие на демонстрационния repository.

## 1. Реален входен казус — зелен код, счупена архитектура

Имаме Spring Boot приложение със слоеве:

```text
API / Controller
       |
       v
Application / Service
       |
       v
Persistence / Repository

Domain rules: без зависимости от Spring, JPA или transport DTO
```

Първоначално екипът се е договорил за тези граници. След няколко спешни промени нов `Controller` започва директно да използва `Repository`; `domain` клас получава Spring dependency; появява се сложен цикъл `A → B → C → D → A`. Business и HTTP тестовете са зелени, защото поведението на този конкретен request още работи. Диаграмите и договорената архитектура обаче вече не съответстват на кода.

**Важното уточнение от лектора (~02:20–02:40):** директен Controller → Repository не е универсална грешка. В малък CRUD може да е напълно приемлив. Архитектурно нарушение е, когато **конкретният екип е избрал и разчита на граница**, която новият код прескача.

Тук не търсим „максимална архитектура“, а **изпълними архитектурни решения**. Вместо code review да разчита на човешка памет, превръщаме съществените инварианти в бързи проверки при всеки PR.

## 2. Точен mental model: какво проверява всеки инструмент?

| Инструмент | Въпросът, на който отговаря | Какво НЕ доказва |
| --- | --- | --- |
| JUnit unit test | Правилна ли е business логиката за даден вход? | Layering и dependency cycles |
| Spring MVC / HTTP integration | Работят ли routes, filters, сериализация, validation, реални HTTP contracts? | Architecture governance на целия проект |
| ArchUnit | Спазваме ли декларираните зависимости, packages и layering? | Бизнес коректност на извикване или DB concurrency |
| **Taikai** | Можем ли по-кратко да поддържаме обичайните ArchUnit правила с готови конфигурируеми checks? | Пълна архитектурна коректност без човешки избор на правила |
| Spring Modulith | Валидни ли са module dependency и explicit interface boundaries? | Коректно бизнес поведение или всички stylistic стандарти |
| SonarQube | Налице ли са качествени и security проблеми от конфигурираните анализатори? | Архитектурните решения, които не сме описали като правила |

**Taikai надгражда ArchUnit**, не го заменя с различен bytecode analyser. Той добавя готови, fluently configurable правила. Архитектурните тестове са fitness functions — автоматично доказателство за **избраните свойства на системата**, не доказателство, че самият архитектурен избор е правилен.

За реалното ни начало виж:
- [DomainBoundaryArchitectureTest — ArchUnit с DDD boundaries](../../architecture/strategic-ddd/integration-lab/src/test/java/bg/hristomanov/education/strategicddd/integration/DomainBoundaryArchitectureTest.java);
- [HarbourModulithArchitectureTest — Spring Modulith module verification](../../architecture/strategic-ddd/modulith-lab/src/test/java/bg/hristomanov/education/portmodulith/HarbourModulithArchitectureTest.java);
- [AI Guardrails — независим test oracle и качество](03-AI-GUARDRAILS.md).

Тези вече изпълними лаборатории остават **без промяна**. Новият урок не ги дублира.

## 3. Изпълнимият ни Taikai lab

Една нова **test-only** Maven зависимост в [pom.xml](pom.xml):

```xml
<dependency>
    <groupId>com.enofex</groupId>
    <artifactId>taikai</artifactId>
    <version>2.3.0</version>
    <scope>test</scope>
</dependency>
```

Реалният `pom.xml` централизира версията с property `taikai.version`. Taikai включва ArchUnit транзитивно, така че **тук не добавяме второ ArchUnit dependency**.

В [ArchitectureFitnessTest.java](src/test/java/bg/hristomanov/education/aitesting/architecture/ArchitectureFitnessTest.java) използваме:

- съществуващите `bg.hristomanov.education.aitesting.*` production classes, **не** измислени „успешни“ fixtures;
- reusable `SHARED_JAVA_RULES` profile: публични instance полета, `System.out/err` и dependency cycles;
- project-specific Spring правила: без field injection и без `@RestController → @Repository` зависимости;
- **custom ArchUnit** правило чрез `TaikaiRule.of(...)`: `domain` да не зависи от Spring/JPA;
- `failOnEmpty(true)`: празна селекция да не мине незабелязано;
- отрицателен test-only fixture, който нарочно нарушава **две** правила и остава извън `src/main`.

### Кратка карта README → код → доказателство

| Решение | Код | Доказателство |
| --- | --- | --- |
| Team baseline + project-specific policy | [ArchitectureFitnessTest](src/test/java/bg/hristomanov/education/aitesting/architecture/ArchitectureFitnessTest.java) | `realApplicationRespectsSharedAndProjectSpecificBoundaries` |
| Custom ArchUnit rule | Същият тест, `TaikaiRule.of(...)` | Domain cannot depend on Spring/JPA |
| `check()` vs `checkAll()` | Test-only `DirectRepositoryController` и `DemoRepository` | `checkStopsEarlyWhileCheckAllReportsEveryBrokenRule` |
| Fail-on-empty | `ClassCriteria.simpleNameMatching(...)` | `failOnEmptyPreventsSilentlyPassingMisspelledSelectors` |
| Тясно изключение без скриване | `TaikaiRule.Configuration.of(...)` | `aNarrowDocumentedExceptionDoesNotHideOtherViolations` |

**Execution:** самостоятелен JUnit test, без Spring context, без Docker и без production storage.

```bash
# От root на education — само архитектурната проверка:
mvn -pl spring/testing/ai-assisted-testing -Dtest=ArchitectureFitnessTest test

# Пълният testing модул:
mvn -pl spring/testing/ai-assisted-testing verify

# Всички модули в education:
mvn --no-transfer-progress verify
```

Тестовете за *намерено нарушение* са успешни, само ако `assertThrows(AssertionError.class, ...)` действително получи правилното failure. **Не допускаме broken production code в `main`** и не изключваме policy проверките от CI.

## 4. `check()`, `checkAll()` и „фалшиво зелено“

Лекторът показва:

- `check()` — прекратява при първото нарушено правило;
- `checkAll()` — изпълнява всички и обобщава нарушенията наведнъж.

В нашия fixture има едновременно:
- публично instance поле;
- `@RestController`, зависещ директно от `@Repository`.

Така можем да докажем и двете failure descriptions в **един aggregated report**. Това е полезно при първоначалния migration audit, когато имаме множество архитектурни отклонения. При всяка следваща промяна отчетът дава цялата картина, не само първата находка.

### Капан: съвпадение с нула класове

Примерна грешка:

```java
Taikai.builder()
    .namespace("bg.hristomanov.education.aitesting")
    .java(java -> java.classesShouldBeRecords(
        ClassCriteria.simpleNameMatching("NeverExistingClass")))
    .build().checkAll();
```

Ако погрешната селекция няма matches, ArchUnit може да третира правилото като успешно, в зависимост от конфигурацията. **Затова използваме `failOnEmpty(true)`**, когато очакваме правилото действително да обхваща класове. В негативния тест това е отделно доказано, вместо само декларирано в README.

Внимание: `failOnEmpty` не гарантира, че всички желани класове са обхванати. Например неправилен regex може да намери един случаен клас и пак да изпусне ключовите. За критичните правила проверявай границата на селекцията.

## 5. Shared rule profiles vs project-specific policy

Проблем от лекцията (~16:20): всеки проект има собствен огромен архитектурен тест и хората вече не знаят защо някое правило съществува.

По-добър подход:

```text
Shared / team baseline
  - no public instance fields
  - no System.out/err
  - no cycles

Project-local rules
  - controller must not use repository directly (chosen layering)
  - domain package must be framework independent
  - exceptions for a *specific* justified legacy class
```

Taikai поддържа споделени `Customizer<JavaConfigurer>` конфигурации и адаптиране на конфигурация чрез `toBuilder()`. Нашият тест **демонстрира reusable profile** в едно приложение; не твърдим, че сме създали фирмен Maven archetype или мултирепо rule distribution.

**Принцип:** shared policy трябва да е достатъчно малка и стабилна, за да я приемат екипите. По-строгите правила се добавят **след** обосновка и тест върху реалния проект.

## 6. Легитимни изключения, а не „изключи всичко“

Лекторът показва клас, в който Spring Security API налага `throws Exception` — общата забрана за generic exceptions може да се окаже прекалено строга.

Също така generated код от OpenAPI/друг генератор не бива да нарушава нашите *ръчни* naming/convention правила, когато не можем директно да го редактираме.

### Разлика между два вида изключения

| Подход | Обхват | Кога е правилен |
| --- | --- | --- |
| `excludeClasses(...)` | Глобално изключване при namespace import | Само при доказано generated package / изрично външен код |
| `Configuration.of(List.of(LegacyClass.class))` | Конкретно правило, конкретен клас | Legacy compatibility при временна миграция |

**Изпълним тест:** изключваме `DirectRepositoryController` само за едно правило. Вторият счупен `StillBrokenController` остава и **продължава да генерира violation**. Това е доказателство, че изключението е ограничено.

За production exception запиши: **защо**, **собственик**, **условие за премахване** и **дата/issue за преглед**. Не използвай широк regex, който скрива всички бъдещи класове.

**Нюанс от Taikai документацията:** global `excludeClasses(...)` се прилага само при `namespace(...)` imports. При изрично `classes(...)` се използват подадените класове; тясното правило трябва да подаде собствените изключения чрез `Configuration`.

## 7. Spring Configuration Properties: една конвенция, не „annotation roulette“

На ~10:40–13:40 лекторът описва как неограниченият избор на `@Component`, `@EnableConfigurationProperties`, `@ConfigurationProperties` и `@Configuration` води до разнопосочни patterns. Неговата предпочитана конвенция:

- `@ConfigurationPropertiesScan` върху основния Boot class (с конкретен config package);
- immutable `record` с `@ConfigurationProperties`;
- еднакво именуване и package организация;
- Taikai built-in плюс custom ArchUnit правила, ако готовите не са достатъчни.

**Възможен стил за *ново* приложение** (илюстрация, не production код в нашия lab):

```java
@SpringBootApplication
@ConfigurationPropertiesScan("com.example.billing.config")
public class BillingApplication { }

@ConfigurationProperties(prefix = "billing")
public record BillingProperties(Duration timeout, URI endpoint) { }
```

Taikai има готови проверки за `properties.shouldBeRecords()` и naming; сканирането и забраната на противоречиви annotations може да изискват допълнителен custom ArchUnit rule. **Не включваме това като задължителен критерий в съществуващия ни testing модул**, защото там няма този model/setup за проверяване, а „празно зелено“ правило би било лош пример.

Конвенцията на лектора е обоснована опция за конкретна организация, **не универсална забрана** на останалите валидни Spring Boot конфигурации.

## 8. AI-generated код, SonarQube и бърза обратна връзка

На ~19:20 лекторът споделя конкретна практика: AI генерира код, PR build е успешен, но SonarQube quality gate се проваля. Екипът използва SonarQube MCP, за да открива повтарящи се нарушения, и създава допълнителни ArchUnit правила, така че feedback да идва по-рано.

```text
AI / developer proposes change
           |
           v
unit + architecture checks (fast)
           |
           v
integration checks + SonarQube gate
           |
           v
human review of architecture / business intent
```

**Не създавай автоматично правила за всички SonarQube warnings.** Някои са runtime/data-flow анализи, които не са естествено изразими чрез статичните dependency правила на ArchUnit. И AI може да генерира неточен rule. Нужни са: идентифицирано нарушение → човешки преглед → минимално правило → отрицателен тест, който *го улавя* → green test след поправка.

Това допълва, а не заменя [AI Guardrails](03-AI-GUARDRAILS.md): независими acceptance критерии, качествени negative tests, mutation testing, Sonar и code review.

### Ако искаме Taikai в много проекти

Лекторът (~20:20) препоръчва да добавим общите правила в Maven archetype/project template. Това е идея за бъдеща стандартизация, а **не е реализирана в този PR**. Преди това трябва да изчистим базовия набор правила и да предотвратим неволни breaking updates между екипите.

## 9. Защо НЕ мигрираме целия education към Taikai

Вече имаме правилни и изпълними boundary tests с ArchUnit и Spring Modulith. Смяната им само заради по-кратък синтаксис не решава реален проблем. Taikai е полезен при голям набор повторяеми conventions, но добавя нова зависимост, API и versioning lifecycle.

Лекторът изрично обръща внимание на small-maintainer риска (~20:50–22:10). Към подготовката използваме **конкретно проверена Maven Central версия 2.3.0**, test scope, а не динамичен `LATEST`. Преглеждаме upgrades отделно; новата зависимост не влиза в runtime classpath на приложението.

**Ако Taikai спре да се поддържа:** правилата могат да се пренапишат с чист ArchUnit. Това е migration effort, не нулева цена.

## 10. Оригинални източници и версия

1. [Gunter Rotsaert — Testing Architecture Made Simple, Devoxx Belgium 2026](https://www.youtube.com/watch?v=asAxisu6EQ0), предоставен transcript (~00:00–23:21).
2. [Официална програма на Devoxx Belgium 2026](https://m.devoxx.com/events/dvbe26/talks/8196/testing-architecture-made-simple).
3. [Taikai — официална документация](https://enofex.github.io/taikai/documentation/), `check`/`checkAll`, `failOnEmpty`, reusable profiles, API.
4. [Taikai — GitHub repository](https://github.com/enofex/taikai) и [tag v2.3.0](https://github.com/enofex/taikai/tree/v2.3.0); тестовете ни използват точния `com.enofex:taikai:2.3.0` API.
5. [ArchUnit — User Guide](https://www.archunit.org/userguide/html/000_Index.html).
6. [Spring Modulith — Verification](https://docs.spring.io/spring-modulith/reference/verification.html).
7. [Spring Boot — Configuration Properties](https://docs.spring.io/spring-boot/reference/features/external-config.html).
8. [SonarQube — Quality Gates](https://docs.sonarsource.com/sonarqube-server/latest/quality-standards-administration/managing-quality-gates/introduction-to-quality-gates/).

**Оригинален demo GitHub repository на лектора:** в транскрипта се споменава линк, но конкретният URL **не е потвърден**. Не е измислен или заменен с чужд примерен repository.

## 11. Финална проверка и упражнения

**Какво доказваме:** в CI реалният testing модул няма избраните цикли, нежелани framework dependencies и директна Controller → Repository зависимост. Също доказваме, че когато умишлено ги нарушим в isolated test fixture, проверките наистина се провалят.

**Какво не доказваме:** че архитектурата е оптимална за всеки проект, че няма business дефекти, че всички SonarQube правила са покрити, че всяко класово dependency е правилно или че AI авторството изчезва от code review.

Практически задачи за 20–30 минути:

1. Предвиди резултата на `check()` и `checkAll()` върху `DirectRepositoryController`. После отвори failure messages от isolated JUnit negative test.
2. Промени в отделен branch `CustomerController` да приема `@Repository`-ан клас. Какво ще открие нашият built-in Taikai rule? Защо не е универсален критерий за всички CRUD приложения?
3. Премахни единствено `failOnEmpty(true)` и въведи умишлено несъществуващ class selector. Кога е възможен false-green?
4. Изключи само един legacy клас, но провери, че новият също счупен клас продължава да се открива. Защо е опасен `excludeClasses(".*")`?
5. Преразгледай идеята за `ConfigurationProperties` records. Кой custom rule е оправдан и кои други валидни конфигурации **не** трябва да забраняваме?
6. Ако CI вече има SonarQube и ArchUnit, каква конкретна повторяема болка би оправдала допълнителното Taikai dependency?

### Senior review checklist

- [ ] **Изрично избрана** архитектурна граница, не универсална догма.
- [ ] Прецизна namespace/class селекция + fail-on-empty там, където очакваме matches.
- [ ] Negative test, който доказва, че правилото хваща реално нарушение.
- [ ] Избор на `check()`, `checkAll()` или independent dynamic tests с ясна цел.
- [ ] Team baseline + local rules без безконтролен policy explosion.
- [ ] Изключенията са минимални, обяснени и временни.
- [ ] Generated code е отделен от нашия ръчно поддържан source.
- [ ] AI-generated правила са валидирани срещу реален failure и добър oracle.
- [ ] Новата зависимост е test-only, pin-ната и CI доказано зелена.
- [ ] **Архитектурен тест не замества unit/integration/security тест.**

**За запомняне:** *Архитектурните решения са полезни, когато можем да ги обясним, измерим и предотвратим неволното им нарушаване. Те не трябва да замразяват всяка имплементационна подробност.*
