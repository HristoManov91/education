# P2 · Green Maven Builds — Efficient and Sustainable CI

> **Mid+ → Early Senior · 40–50 мин. урок + runnable Java 25/JUnit/GitHub Actions labs.**  
> Източник: [Jan Boonen — Green Maven Builds: Sustainable CI in Practice, Devoxx 2026](https://www.youtube.com/watch?v=Mdu5_gndYn0) и предоставения автоматично генериран SRT (~40 мин.). Времената са ориентировъчни. Примерите с GitLab от лекцията са адаптирани към GitHub Actions, не са представени като идентични APIs.  
> Този материал **разширява** [Reproducible Maven Builds](README.md) и [Spring TestContext CI Performance](../../../spring/testing/ai-assisted-testing/02-CONTEXT-CACHING-CI.md). Не създаваме нов Maven модул или NotebookLM аудио prompt.

## 1. Реален казус: зелен pipeline, който върши ненужна работа

Нашият Java/Spring backend се компилира, след това unit tests се изпълняват, а отделен quality job отново извиква Maven lifecycle и може да компилира същото. Междувременно нов commit стартира нов PR pipeline, докато старият още работи. Ако просто пропуснем компилацията или включим всички тестове паралелно, ще спестим време, но рискуваме **false-green** (зелена проверка, която не е тествала действителните изходни файлове).

**Цел:** да премахнем повторната работа **без** да отслабим проверки за correctness, release integrity и тестова изолация. Не твърдим измерено ускоряване на Education — числата на лектора са за неговата среда, не за нашето repository.

**Какво научаваме:** compilation reuse, JUnit 5 parallelism, Maven compiler granularity, job dependency/condition правила, безопасен PR cancellation и разликата между runtime/energy/carbon estimates.

## 2. README → код → доказателства

| Концепция | Файл | Проверка |
| --- | --- | --- |
| Избор на PR/main/release проверки; fail-closed политика | [CiWorkPlanner.java](src/main/java/bg/hristomanov/education/build/ci/CiWorkPlanner.java) | [CiWorkPlannerTest.java](src/test/java/bg/hristomanov/education/build/ci/CiWorkPlannerTest.java) |
| Изолирани и shared-resource паралелни тестове | [ParallelCiFixtureTest.java](src/test/java/bg/hristomanov/education/build/ci/ParallelCiFixtureTest.java) | Sequential default + изричен opt-in parallel |
| Compile once + независими consumer jobs | [ci-efficiency-lab.yml](../../../.github/workflows/ci-efficiency-lab.yml) | Само `workflow_dispatch`, не required CI |
| Компилиран output срещу source/toolchain provenance | [compiled-artifact.sh](ci/compiled-artifact.sh) | Receipt, SHA-256 и expected tampering rejection |
| Команди, сценарии и отрицателни контроли | [CI-EFFICIENCY-LABS.md](CI-EFFICIENCY-LABS.md) | Изпълними lab сценарии |
| Вече наличният Maven reproducibility gate | [README.md](README.md), [reproducible-maven.yml](../../../.github/workflows/reproducible-maven.yml) | Два clean checkout-а и идентични JAR bytes |
| Основният required pipeline | [verify.yml](../../../.github/workflows/verify.yml) | Пълен `mvn verify`; **не е променен** |

## 3. Разграничи три вида reuse

| Нещо | Какво кешираме / споделяме | Какво НЕ следва |
| --- | --- | --- |
| Maven dependency cache (`~/.m2/repository`) | Външни JARs и Maven plugins | Нашите `.class` файлове не се компилират автоматично от кеша |
| GitHub Actions artifact | Изрично архивирани `target/classes` и `target/test-classes` от job | Не знаем дали е правилният commit без validation |
| Spring TestContext cache | Жив `ApplicationContext` в **една** JVM | Не се споделя между Maven forks и отделни CI jobs |

Една от често срещаните грешки е да наричаме тези три механизма „същия кеш“. Те имат съвсем различни lifecycle, ownership и failure modes.

## 4. Основен урок: build/test jobs без повторна compilation

[Лекция 14:30–26:30](https://youtu.be/Mdu5_gndYn0?t=870).

**BAD (но реалистичен):** first job изпълнява `mvn verify`, second job — `mvn verify sonar:sonar` или друга цел, която задейства lifecycle отново. Отделните runners не споделят `target/`. Maven може да компилира/test-ва повторно.

**GOOD в конкретен ограничен сценарий:**

```text
manual GitHub Actions run
         |
         v
compile-once (mvn test-compile)
         |
         +--------> unit-tests
         |          (restore classes → verify receipt → surefire:test)
         |
         +--------> parallel-fixture (opt-in)
                    (restore same classes → verify → surefire:test)
```

[Ръчният workflow](../../../.github/workflows/ci-efficiency-lab.yml) прави това с `needs`, `upload-artifact` и `download-artifact`. Прехвърля **само output от един commit**, не обща Maven dependency cache.

Критичната команда е `surefire:test`, извикана **директно**: тя изпълнява вече компилираните JUnit тестове, без да стартира `compile` или `test-compile` lifecycle. Това е подходящо **само** за този изолиран пример. За integration tests с Failsafe, generated sources, lifecycle bindings или различни profiles може да пропусне важна подготовка.

### Artifact identity, а не доверие в timestamps

[compiled-artifact.sh](ci/compiled-artifact.sh) записва пълен Git commit SHA, JDK banner, Maven version, SHA-256 на `pom.xml` и SHA-256 на всички компилирани main/test класове.

Всеки consumer проверява identity **преди** тестовете и класовете отново **след** тях. Negative control умишлено променя един `.class` и очаква отказ. Самото изискване за clean checkout предотвратява „същият commit, но непубликувани local changes“.

Това **не е** независима криптографска supply-chain attestation. Това е защита срещу случайно използване на стар/mismatched artifact **в рамките на доверен CI run**.

### Кога НЕ използваме compile-once pipeline

- Compile е евтин, но download/upload и стартирането на допълнителен runner са скъпи.
- Job-овете използват различен JDK, компилатор, OS или source-generation configuration.
- Нужни са Maven lifecycle phases, които не се изпълняват с direct Surefire.
- Integration tests разчитат на реална DB/Kafka/PostgreSQL, migrations или Testcontainers.
- Нямаме сигурен начин да свържем binary output със source/toolchain inputs.

**По-проста алтернатива:** един `mvn verify` с ясни job logs. Оптимизацията трябва да се **измери**, а не да се приема по интуиция.

## 5. JUnit parallel execution: най-бързите тестове не са непременно най-надеждните

[Лекция 07:00–11:30](https://youtu.be/Mdu5_gndYn0?t=420).

При независими тестове паралелизиране може да намали wall-clock време. Но споделени DB, фиксирани ports, общи mocks, Spring `ApplicationContext` и static mutable fixtures могат да създадат flaky тестове.

[ParallelCiFixtureTest.java](src/test/java/bg/hristomanov/education/build/ci/ParallelCiFixtureTest.java) използва:
- `@Execution(CONCURRENT)` — **само при изрично enabled parallel execution**;
- `@ResourceLock("ci-shared-counter")` — взаимно изключване за двата теста, които променят static counter;
- независими локални данни за останалите тестове.

Това демонстрира **correctness**, не доказва конкретен speedup. При Spring тестовете виж [Context Caching & CI](../../../spring/testing/ai-assisted-testing/02-CONTEXT-CACHING-CI.md): forks изолират JVM и също премахват shared TestContext cache. `@DirtiesContext` не е решение за чистене на тестова DB.

**Кога:** тестовете имат независим state и реален bottleneck. **Кога не:** тестовете използват обща DB/schema или global lifecycle без доказана изолация. **Печалба ↔ загуба:** възможно по-ниско wall time срещу повече RAM, contention и риск от flakiness.

## 6. `lastModGranularityMs`: не скривай реална промяна

[Лекция 14:30–18:00](https://youtu.be/Mdu5_gndYn0?t=870).

Maven Compiler Plugin параметърът `staleMillis` (user property `lastModGranularityMs`) управлява допускането за filesystem timestamps. В отделен checkout времената на файловете могат да се различават от тези в прехвърления binary output. Увеличаването на tolerance може да спести recompilation в тесен сценарий, но **може да пропусне действителна промяна**.

Официалната документация предупреждава и за `useIncrementalCompilation=false`: клас, който зависи от променен друг клас, може да остане некомпилиран и да се счупи при runtime.

**Нашият избор:** нито променяме тези флагове в default POM, нито приемаме timestamp като доказателство. [CiWorkPlannerTest.java](src/test/java/bg/hristomanov/education/build/ci/CiWorkPlannerTest.java) отхвърля различен commit/JDK/POM, а manual CI проверява actual class bytes.

## 7. Workflows: кога проверяваме и кога прекъсваме

[Лекция 26:30–31:30](https://youtu.be/Mdu5_gndYn0?t=1590).

[CiWorkPlanner.java](src/main/java/bg/hristomanov/education/build/ci/CiWorkPlanner.java) е **само модел**, не active PR gate. Той избира:

| Сценарий | Примерно решение | Trade-off |
| --- | --- | --- |
| PR с документация в познати директории | Само documentation checks | Нужно е да запазим стабилните required statuses |
| PR с Java/POM/YAML/scripts или непознато съдържание | Пълни checks (fail closed) | По-скъпо, но няма тихо пропускане на тестове |
| Main push | Пълни checks независимо от changed paths | Доказано състояние на main |
| Release | Пълни checks + release integrity | Release не е обикновен PR |
| Superseded PR commit | Може да се cancel-не | Вече не проверяваме актуалния source |
| Main / release branch / tag | Не cancel-ваме автоматично | По-важна е историята на release verification |

[Manual workflow](../../../.github/workflows/ci-efficiency-lab.yml) използва отделна concurrency група за конкретния workflow и branch. Cancel-ва само feature branches, **не** main/release/tag. Не сме променили repository `Verify` или branch protection правила.

**Внимание:** GitHub `paths` може да не създаде required status за docs-only PR. Преди production оптимизация е необходим постоянен статус/check contract; иначе PR може да остане блокиран или да заобиколи очакван gate.

## 8. Логове, енергия и устойчивост

[Лекция 11:30–14:30](https://youtu.be/Mdu5_gndYn0?t=690) и [31:30–38:45](https://youtu.be/Mdu5_gndYn0?t=1890).

Намаляването на Maven progress/log spam с `-B --no-transfer-progress` може да улесни обработката на CI logs. **Не губи failure details**, test reports и diagnostics само за да спестиш няколко KB output.

- `Wall time` ≠ сумарни `runner-minutes`. Два паралелни runners може да завършат по-рано, но да използват повече общи ресурси.
- `EcoCI` оценява енергия/емисии чрез assumptions, а не измерва директно ватове на GitHub-hosted runner.
- `Kepler` е за Kubernetes power/energy estimation, не за инсталация по подразбиране в този GitHub Actions проект.
- Carbon intensity зависи от регион, време, хардуер и енергиен микс. Не измисляме точни грамове CO₂ или проценти savings за Education.

**P3 резултат:** записваме какво и как бихме измерили, без да добавяме нова външна услуга или plugin.

## 9. Before/after и измерване на CI

| Измерване | Сегашен required pipeline | Manual experiment |
| --- | --- | --- |
| Scope | Пълен `mvn verify` на repository | Само избрани тестове от Maven lab |
| Compile | Преглеждаме Maven logs | Един `test-compile`, после директно `surefire:test` |
| Correctness | Всички tests/integration gates | Изолиран proof, **не еквивалентен test coverage** |
| Source identity | Контролирани Maven/Git входове | Receipt за SHA/JDK/Maven/POM/classes |
| Resource cost | Sum job minutes/CPU/failed retries | Sum compile + 2 consumer jobs + transfer |
| Security | Задължителен workflow и permissions | Ръчен opt-in с `contents:read` и 1-day artifact retention |

**Не сравнявай директно runtime на различни по съдържание test suites и не твърди „по-зелен“ само от по-бърз wall time.** За честно сравнение приложи един и същ test contract, идентични toolchains, повторения, и разход на всички runners.

## 10. Decision contract / Senior code review checklist

| Подход | Кога използваме | Кога НЕ | Полза ↔ цена |
| --- | --- | --- | --- |
| Artifact reuse | Скъпа компилация и няколко независими consumers | Несъвместими jobs / дребен build | По-малко compile ↔ network + runner overhead |
| Direct Surefire | Същите precompiled tests и configuration | Failsafe/generated code/complex lifecycle | По-малко Maven phases ↔ skipped setup риск |
| JUnit parallel | Изолирани fixtures и измерен bottleneck | Shared mutable fixtures/DB без правила | Wall time ↔ RAM/flakiness |
| Selective checks | Безопасен impact analysis + required status contract | Неизвестен тип промяна | По-малко работа ↔ false-green риск |
| Cancel stale PR work | Само superseded PR revisions | Main/release checks | Runner time ↔ изгубени logs за стар run |
| EcoCI/Kepler | Има същински sustainability metrics goal | „Да имаме още tool“ без решение | Видимост ↔ estimate uncertainty/поддръжка |

**Практични задачи** са в [CI-EFFICIENCY-LABS.md](CI-EFFICIENCY-LABS.md).

**Изход към първоначалния казус:** избираме най-евтината верига, която *все още проверява необходимите инварианти*. Reproducibility пита „същият source → същите bytes?“, CI efficiency — „правим ли излишна работа?“, а correctness — „проверили ли сме правилното поведение?“. Трябват ни и трите.

Checklist: [ ] същите source/toolchain [ ] verified artifact [ ] tests не са отпаднали [ ] CI required statuses [ ] isolated parallel fixtures [ ] сравнен sum runner cost [ ] rollback към пълен Maven verify.

## Оригинални източници

- [Jan Boonen — Green Maven Builds, Devoxx 2026](https://www.youtube.com/watch?v=Mdu5_gndYn0).
- [GitHub Actions workflow syntax, needs/concurrency](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax).
- [GitHub Actions upload/download artifacts](https://docs.github.com/en/actions/using-workflows/storing-workflow-data-as-artifacts).
- [Maven Compiler Plugin — staleMillis and incremental compilation](https://maven.apache.org/plugins/maven-compiler-plugin/compile-mojo.html).
- [Maven Surefire goals](https://maven.apache.org/surefire/maven-surefire-plugin/plugin-info.html).
- [JUnit 5 parallel execution and ResourceLock](https://docs.junit.org/current/user-guide/#writing-tests-parallel-execution).
- [EcoCI](https://github.com/green-coding-solutions/eco-ci-energy-estimation) · [Kepler](https://github.com/sustainable-computing-io/kepler).
- [Existing Reproducible Maven](README.md) · [Spring TestContext cache](../../../spring/testing/ai-assisted-testing/02-CONTEXT-CACHING-CI.md) · [Benchmark Correctness](../../jvm/performance-evolution/BENCHMARK-CORRECTNESS.md).
