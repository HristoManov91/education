# P1 · Software Gardening — зависимости, security updates и безопасни миграции

> **Практическа P1 operational глава към [Reproducible Maven](README.md) и [CRA security lifecycle](../../../docs/security/cra/README.md).** Източник: [Christian Schabetsberger — CRAcking Compliance](https://www.youtube.com/watch?v=QQcsZNf0eWs), ~36–43 мин, предоставен SRT. Не създава нова Maven dependency или автоматично обновяване на целия Education reactor.

## 1. Реалният казус — критична CVE и шест major версии назад

Години наред екипът е отлагал Maven upgrade-и. Текущият `main` build още е зелен; но когато се появи уязвимост, vendor-ът поддържа само последната major линия. Изведнъж са нужни 6 major upgrade-а, промени по API, regression, Oracle driver и runtime compatibility — под time pressure.

**Bad:** „Ще направим модернизация веднъж на 3 години“ или `auto-merge` на **всички** dependency PR-и. Първото натрупва migration debt; второто може да вкара проблемен release и да активира голям CI разход без преглед.

**Good:** малки, редовни, проследими updates, проверени в CI и с rollback. Security patch PR-ите следват отделен urgent path; breaking changes изискват допълнителна оценка. „Software Gardening“ означава **постоянен жизнен цикъл**, не безкрайна мания за latest версия.

## 2. Набор от решения

| Инструмент / подход | Какъв проблем решава | Кога / кога НЕ | Печалба и компромис |
| --- | --- | --- | --- |
| Dependabot | Автоматично открива версия и предлага PR | GitHub repo, ясен ownership; не е migration test suite | Нисък setup, но PR noise + Actions minutes |
| Renovate | Гъвкаво групиране, schedules, policies и dashboards | Много modules/ecosystems; не за проект, където няма кой да triage-ва | Висок control, но сложност на config/hosting |
| OpenRewrite | Повтаряеми кодови миграции с recipes | API refactor, голямо Java codebase; не за произволна нетестирана рецепта | Спестява механичен труд, но API semantics се доказват с тестове |
| Review + manual PR | Минимално решение | Малък проект, редки зависимости | Ниска automation цена, но човешка дисциплина |
| Remove dependency | Намалява maintenance/attack surface | Излишен или вече в JDK API | По-малко updates; **не** копирай чужд код без лиценз и security review |

## 3. Конфигурации — **само примери, НЕ се активират**

- [Dependabot sample](examples/dependabot.yml) — weekly Maven checks, limit от 2 PR, групиране на non-major. Не е `.github/dependabot.yml`, затова няма да създава PR-и.
- [Renovate sample](examples/renovate.json) — `dependencyDashboardApproval` за major, `minimumReleaseAge` за нормални version updates, ограничение на PR concurrency. Не е в root, затова не се включва автоматично.

**Към ноември 2026:** GitHub Dependabot има **3-дневен default cooldown само за version updates**; **security updates не са забавени** от този механизъм. Включването на още един глобален cooldown за спешни vulnerability fixes може да е опасно. [GitHub docs](https://docs.github.com/en/code-security/concepts/supply-chain-security/dependabot-version-updates) и [официален changelog 14.07.2026](https://github.blog/changelog/2026-07-14-dependabot-version-updates-introduce-default-package-cooldown/). Renovate `minimumReleaseAge` има собствена семантика и ограничения за registry timestamps; не я приравнявай автоматично към security SLA ([официална документация](https://docs.renovatebot.com/key-concepts/minimum-release-age/)).

**Никога не включвай auto-merge само защото patch е „small“**. Дори patch може да е компрометиран; сравни changelog, advisory, compatibility, artifact identity и coverage. Никакъв auto-merge не е активиран в примерите.

## 4. Основен workflow — от bot PR до production

1. **Inventory:** `mvn dependency:tree` + supported-version map за Java, Spring Boot, JDBC driver, Maven plugins и image/JDK. Провери **effective POM**; Spring Boot BOM транзитивно определя версии.
2. **Classify:** security-fix, normal patch/minor, breaking major, unmaintained. За активно експлоатирана версия не прилагай механично „чакай 72 часа“.
3. **Small PR:** една тематична група, release notes, причина и impacted modules; отдели runtime от test tooling upgrades.
4. **Verify:** локално `mvn -pl <module> -am test`; интеграционни/HTTP security тестове, API контракт, DB миграции, JDK/container compatibility.
5. **Prove:** сравни dependency tree и SBOM **преди и след**; свържи CI green със source commit и крайния artifact digest. [Reproducible build lab](README.md) ≠ автоматична supply-chain защита.
6. **Release:** canary/controlled deployment, metrics/rollback plan, archived release evidence и reopen на изключения.
7. **Revisit:** advisory feed и dependency support lifecycle продължават и след merge.

### Реален Java 25 / Maven пример

```bash
# От root: фокусирани проверки на нашите реални modules
mvn -pl java/build/reproducible-maven dependency:tree
mvn -pl java/build/reproducible-maven help:effective-pom -Doutput=target/effective-pom.xml
mvn -pl java/build/reproducible-maven clean test
mvn -pl spring/security/threat-modeling test

# Предложени tool-assisted refactors (само след pin на версия и recipe review):
# mvn org.openrewrite.maven:rewrite-maven-plugin:<verified-version>:dryRun
# НЕ пускай rewrite:run върху main без patch review и regression.
```

При Java 25/JDK migrations `OpenRewrite` може да е полезен, но **конкретните recipes и license/distribution условия** трябва да се проверят за реалната версия, преди да твърдим „един command мигрира всичко“. [Официален Maven plugin](https://docs.openrewrite.org/reference/rewrite-maven-plugin).

## 5. Тестове и доказателства — no false green

| Предпоставка | Отрицателен сценарий | Evidence / test |
| --- | --- | --- |
| Patch трябва да не променя поведение | Валиден build, счупен endpoint contract | [Spring Security negative HTTP tests](../../security/threat-modeling/src/test/java/bg/hristomanov/education/threatmodel/ThreatModelHttpTest.java) |
| Детерминиран output | Два build-а, различни SHA | [Reproducible Maven lab](README.md) и [release hash CI](../../../.github/workflows/reproducible-maven.yml) |
| Обновен SBOM | Нова версия липсва в final image | [SBOM accuracy fixtures + tests](../../../spring/testing/testcontainers/SBOM-ACCURACY.md) |
| Не затрупваме CI | Bot пуска 50 PR-а | Примерните limits, review на Actions minutes; **няма активирани bots** |
| Security exception има lifecycle | `--ignore-unfixed` прикрива issue | [CVE triage register](../../../spring/testing/testcontainers/JAVA-IMAGE-HARDENING.md) |

**Граница:** не сме конфигурирали действителен bot нито сме изпълнили OpenRewrite migration в този PR. Тези стъпки са ръчни/конфигурационни примери; HTTP/JUnit/Python тестовете доказват конкретни invariant-и, но не гарантират успешен външен update PR.

## 6. Кога НЕ и какво губим

**Не използвай** централен Renovate server за 1 учебен Maven модул само за да избегнеш ръчно upgrade-ване. Не групирай runtime Spring major, JDBC driver и всички test dependencies в един огромен PR, ако не можеш да локализираш регресии. Не замествай добре поддържан security компонент със собствен криптографски код „за да премахнем зависимост“; това често **увеличава**, а не намалява риска.

**Печелим:** по-малко натрупан upgrade debt, по-бързи целеви security fixes, traceable change history. **Губим:** review време, GitHub Actions minutes, version pinning maintenance, постоянно наблюдение, възможни регресии и churn.

За малък проект достатъчна алтернатива е **седмична ръчна проверка + един малък PR + тестове**, ако има конкретен owner. При по-голям екип групирай PR-и и използвай dashboard, но измервай реално обема работа и CI cost.

## 7. Финален decision contract и упражнения

- **Проблем:** release използва неподдържана библиотека и hotfix изисква разрушителна миграция.
- **Кога:** постоянно за поддържани системи, най-вече при сигурност/дълъг CRA support.
- **Кога НЕ:** не автоматизирай напълно merge без достатъчно e2e, ownership и rollback.
- **По-просто:** ръчна планирана проверка и обозрими PR-и.
- **Proof:** сравнени dependency graph + BOM + unit/HTTP tests + deployed digest, не само green bot status.

**Практически упражнения:** (1) Избери зависимост в текущ POM и намери кой я управлява в effective POM; (2) предложи безопасен minor PR и изискваните тестове; (3) как ще реагираш при urgent CVE, ако cooldown още не е изтекъл? (4) когато нов Spring Boot major променя SecurityFilterChain, кои отрицателни тестове са задължителни? (5) как ще докажеш, че продукционният image е нов, а не стар mutable tag?

**Изход:** не чакаме „голямо обновяване“. Поддържаме малки промени с proof, отделяме normal version updates от security urgency, не пренасяме blindly лекционен advice за auto-merge/cooldowns върху всяка система.
