# P2 Hands-on · Green Maven Builds / CI Efficiency

> Съпътстваща лаборатория към [Green Maven Builds — Efficient CI](CI-EFFICIENCY.md), Java 25 + Maven. Командите не включват production optimization, release или внедряване на външни инструменти.

## Реален сценарий

Един Maven module с business тестове и CI policy. Искаме да демонстрираме **една компилация**, два независими набора tests и отрицателен контрол за стар/подменен bytecode. Целта не е да получим най-ниското време на всяка цена, а да знаем **какво доказва** всеки stage.

## README → код

| Стъпка | Source | Доказателство |
| --- | --- | --- |
| CI event decision policy | [CiWorkPlanner.java](src/main/java/bg/hristomanov/education/build/ci/CiWorkPlanner.java) | [CiWorkPlannerTest.java](src/test/java/bg/hristomanov/education/build/ci/CiWorkPlannerTest.java) |
| Concurrent-but-safe test fixtures | [ParallelCiFixtureTest.java](src/test/java/bg/hristomanov/education/build/ci/ParallelCiFixtureTest.java) | `@ResourceLock` + isolated state |
| SHA/checksum build receipt | [compiled-artifact.sh](ci/compiled-artifact.sh) | Expected rejection на подменен class |
| GitHub Actions DAG | [ci-efficiency-lab.yml](../../../.github/workflows/ci-efficiency-lab.yml) | `compile-once → unit-tests / parallel-fixture` |

## 1. Първо: функционални JUnit проверки

От repository root:

```bash
mvn -B --no-transfer-progress -pl java/build/reproducible-maven test
```

Expected: всички `ReleaseMetadataTest`, `CiWorkPlannerTest` и `ParallelCiFixtureTest` минават; не съществува assertion за бързина.

Прегледай `CiWorkPlannerTest` — той доказва:
- docs-only PR е евтин **само в учебния модел**;
- Java/POM/workflow/неизвестна промяна води до пълни проверки;
- main и release **не** пропускат тестовете;
- superseded PR може да се cancel-не, main/release — не;
- същият commit с различен JDK/POM **не** позволява artifact reuse;
- върнатият plan е immutable.

### Контролирано включване на JUnit parallelism

```bash
mvn -B --no-transfer-progress -pl java/build/reproducible-maven \
  -Dtest=ParallelCiFixtureTest \
  -Djunit.jupiter.execution.parallel.enabled=true \
  -Djunit.jupiter.execution.parallel.mode.default=concurrent \
  -Djunit.jupiter.execution.parallel.config.strategy=fixed \
  -Djunit.jupiter.execution.parallel.config.fixed.parallelism=3 test
```

**Windows PowerShell/IntelliJ:** добави тези Java/JUnit `-D` свойства в Maven Run Configuration; Git Bash/WSL работи с горния multiline shell. Не активирай глобално parallel execution за всички Spring Boot tests. След измерване отново изключи експерименталните параметри.

**Отрицателен мисловен тест:** премахни `@ResourceLock` от двата теста със static counter и повтори няколко пъти. Тестът *може* да фейлне, но липсата на failure **не доказва** thread-safety — race conditions са недетерминирани. Върни lock-а преди commit.

## 2. Compile once + local receipt

След успешния test compile изпълни:

```bash
mvn -B --no-transfer-progress -pl java/build/reproducible-maven test-compile
cd java/build/reproducible-maven

# Bash/Git Bash/WSL:
bash ci/compiled-artifact.sh record
bash ci/compiled-artifact.sh verify
```

Expected message:

```text
Recorded source/toolchain identity and class checksums
PASS: bytecode unchanged and bound to compilation inputs
```

Разгледай `target/ci-build-receipt.txt` и `target/ci-classes.sha256`. Първият описва git/JDK/Maven/POM; вторият съдържа checksums на main/test class files. Не commit-вай generated `target` директорията.

### Negative control: фалшив build artifact

Създай резервно копие на един компилиран class и го промени, след което провери:

```bash
# Изпълнява се в директорията на модула:
class_file="$(find target/classes -name '*.class' -type f -print -quit)"
cp "$class_file" target/original.class
printf 'stale-build' >> "$class_file"

# ОЧАКВАН НЕУСПЕХ: checksum проверката трябва да върне nonzero exit.
if bash ci/compiled-artifact.sh verify; then
  echo "BUG: stale output was incorrectly accepted"
  exit 1
fi

cp target/original.class "$class_file"
bash ci/compiled-artifact.sh verify
```

**Защо е важен:** ако Maven test job сляпо приеме прехвърлени `target/classes`, можем да проверим различен binary от това, което ревюираме. Receipt и checksums правят разликата наблюдаема. Те не са signed provenance.

## 3. Manual GitHub Actions lab — без промяна на required CI

След публикуване на [.github/workflows/ci-efficiency-lab.yml](../../../.github/workflows/ci-efficiency-lab.yml) в default branch:

GitHub → Actions → **CI Efficiency Maven Lab (manual)** → **Run workflow**. Избери branch, остави `run_parallel_fixture=true` и стартирай.

Workflow съдържа:

```text
compile-once [JDK 25, mvn test-compile]
      |
      +------ unit-tests [Surefire direct; verify before/after; negative class control]
      |
      +------ parallel-fixture [Surefire direct + explicit JUnit concurrency]
```

Всяко downstream job:
1. checkout-ва същия SHA;
2. поставя същия JDK 25;
3. сваля compile artifact в `target/`;
4. проверява commit/JDK/Maven/POM и bytes;
5. извиква `surefire:test` **без** стандартните compile phases;
6. проверява, че class files не са променени.

Сравни Maven logs и виж дали second stage извиква `maven-compiler-plugin`. Ако се появи compilation goal, прегледай реалната конфигурация — не приемай демонстрацията за вярна без evidence. При отрицателния сценарий очаквай отказ на `sha256sum -c` и след това успешен return към оригиналния bytecode.

**Важно:** manual pipeline проверява *избрани* тестове и **не замества** пълния `mvn verify` за production branches. Потребителското `workflow_dispatch` действие не е автоматичен PR trigger. Ако workflow не се вижда в GitHub Actions преди merge, това е очаквано: ръчните workflows обикновено се регистрират след наличност в default branch.

## 4. CI resource measurement worksheet

Запиши реално измерените резултати (празни стойности са умишлени, не ги измисляй):

| Metric | Baseline (full Verify) | Candidate (manual lab) |
| --- | --- | --- |
| Commit SHA + JDK/Maven | … | … |
| GitHub runner type | … | … |
| Jobs and total runner-minutes | … | … |
| Setup & transfer overhead | … | … |
| Number of Java compilations | … | … |
| Number of unit/integration tests | … | … |
| Failures/flaky reruns | … | … |
| Wall time p50 / variation across repeated runs | … | … |
| Artifacts downloaded MB | … | … |

Тези pipelines **не са с еднакъв test scope**, следователно времето им само по себе си **не доказва** спестяване при еквивалентен coverage. За production решение избери сравним набор checks, направи 5–7 контролирани повторения и сравни resource cost и reliability.

## 5. Въпроси за самопроверка

1. Защо `mvn surefire:test` не е универсален заместител на `mvn verify`?
2. Кога commit SHA не е достатъчен за безопасно artifact reuse?
3. Защо JUnit `@ResourceLock` и Maven Surefire forks решават **различни** проблеми?
4. Какво ще стане с docs-only PR, ако required GitHub check е пропуснат от `paths` trigger?
5. Може ли 2× по-нисък wall time да означава **по-високи** billed runner-minutes?
6. Кога би включил EcoCI/Kepler и как би проверил assumptions за точността им?

**Изход:** покажи кога ефективността оправдава допълнителната CI сложност и кога по-простият пълен `mvn verify` остава правилният избор.

## Оригинални източници

- [Jan Boonen — Green Maven Builds](https://www.youtube.com/watch?v=Mdu5_gndYn0).
- [GitHub Actions artifacts](https://docs.github.com/en/actions/using-workflows/storing-workflow-data-as-artifacts).
- [JUnit parallel execution](https://docs.junit.org/current/user-guide/#writing-tests-parallel-execution).
- [Maven Surefire](https://maven.apache.org/surefire/maven-surefire-plugin/plugin-info.html).
- [Main P2 decision guide](CI-EFFICIENCY.md).
