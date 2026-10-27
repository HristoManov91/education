# 1.3 — AI/Agentic Testing, Skills, Mutation Testing & Engineering Ownership

> **P1 Deep Dive / AI Development crossover**
>
> Видео [1:24:00–2:30:48](https://www.youtube.com/watch?v=d6D8nef-32c&t=5040s). Това е **пълноценен** материал, а не съвет „кажи на AI да напише тестове“.

## 1. Bottleneck в AI era: verification, не typing

AI може да генерира code и тестове бързо. Ако team вече имаше бавни, flaky и непълни tests, AI **увеличава** throughput-а на некоректните промени.

**Bad prompt:** „Write meaningful tests. Make no mistakes.“

Няма acceptance criteria, oracle, negative cases, test scope, performance budget или security behavior. Агентът може да покрие happy path, да mock-не самата логика или да промени production code само за да получи green.

**Better workflow:**

```text
Ticket / business invariants
    ↓
Human-defined acceptance criteria & failure modes
    ↓
AI proposes test matrix BEFORE modifying production behavior
    ↓
Engineer confirms boundaries, negative/edge cases and test oracles
    ↓
AI implements minimal code + independently valuable tests
    ↓
Local unit/slice tests → Failsafe / real infra where needed
    ↓
Mutation / diff review / CI evidence
    ↓
Human ownership + release controls
```

**Agent instructions са помощен механизъм, не security permission или proof of quality.** Tests and reviews остават независими safeguards.

## 2. Какво е „meaningful test“ в конкретни критерии?

За нашия endpoint:
- **Assertion quality:** вярно ли е очакваното поведение, или тестът просто повтаря mock stubbing?
- **Determinism:** injection на Clock, unique IDs, никакво скрито real-time/ordering dependency.
- **Negative/edge cases:** 17 г. срещу точно 18, missing consent, invalid email, anonymous/role mismatch.
- **Representative boundary:** Mockito не може да докаже MVC/SQL/security wiring.
- **Failure diagnostics:** ясно test name, assertion с expected/actual и конкретен business reason.
- **Parallel-safe:** shared mutable data, ports, broker topics, DB, test fixtures.
- **Maintainability:** не verify-ваме implementation internals, които могат да се променят без business change.

**Важно:** „минимум една assertion на всяко JSON поле“ не гарантира correctness; по-важно е всеки значим **contract invariant** да бъде доказан.

## 3. Independent test oracle — централният AI риск

Когато един и същ агент генерира implementation и tests, той може да разпространи обща логическа грешка.

```text
Requirement: Person turns 18 TODAY → ALLOWED.
Bad implementation: if (age <= 18) reject.
Bad AI test: user 30 years old accepted.
Result: GREEN, but 18-year-old user REJECTED.
```

Ние проверяваме с [RegistrationPolicyTest](src/test/java/bg/hristomanov/education/aitesting/domain/RegistrationPolicyTest.java) **exact threshold** и one-day-before boundary.

**Не преписвай production algorithm в test assertion.** Acceptance oracle трябва да идва от business contract/reference example, не от „implementation looks like this“.

## 4. Mutation testing — проверяваме качеството на тестовете

Code coverage казва какво е изпълнено, но **не** дали тестът ще улови промяна в логиката.

**PIT** мутира bytecode (напр. сменя условие) и изпълнява засегнатите тестове:

- **KILLED:** тестът се проваля → уловена промяна;
- **SURVIVED:** всички съществуващи тестове са зелени → вероятна празнина, но провери дали мутацията е действително значима;
- **NO_COVERAGE/TIMED_OUT:** анализирай отделно; не ги представяй автоматично като доказани defects.

В нашия [pom.xml](pom.xml) е конфигуриран **opt-in** профил `mutation`, насочен към `RegistrationPolicy` и конкретния unit test.

```bash
mvn -pl spring/testing/ai-assisted-testing -Pmutation test pitest:mutationCoverage
```

HTML report: `target/pit-reports/`. **Не твърдим mutation score без реално изпълнение.** Започни с критичните нови правила; mutation testing за цял legacy монолит може да е твърде скъпо.

За **survivor triage**, equivalent mutations, PIT bytecode mental model, отделен `ChunkedSender` пример с partial writes и разумни CI gates виж [1.5 — Mutation Testing Deep Dive](05-MUTATION-TESTING-DEEP-DIVE.md). Отчетът за оцелял mutant не е автоматично доказателство, че липсва тест: понякога production кодът е излишен или промененото поведение е equivalent.

## 5. Skills вместо случайни ad-hoc prompts

Тази лекция обръща внимание на versioned project-specific testing knowledge — малки Markdown skills с ясно кога се използват, конкретна цел и примери.

В education създадохме [примерни агентски skills](agent-skills/README.md):

- `test-selection`: избери minimal sufficient proof и правилната Spring test slice.
- `unit-tests`: boundary checks, fixed Clock, Mockito collaborators, deterministic assertions.
- `ci-test-review`: read-only review на test context fragmentation, isolation и build evidence.
- `test-quality-review`: независим oracle, mutation-friendly tests, negative paths, anti-cheating.

Това са **изходни шаблони**, не автоматично инсталирани connector permissions или доказателство, че agent ги е приложил. За различни harnesses (Codex, Claude, Copilot) използвай [общия vendor mapping](../../../ai/agentic-development/VENDOR-MAPPING.md) и постави инструкциите на подходящото място за конкретния инструмент.

## 6. Red/Green TDD с агенти — кога е полезно?

Лекторът представя различни мнения; **няма универсално доказателство**, че винаги „AI TDD first“ е най-бързо или най-добро.

**Добра употреба:** поведението е ясно, acceptance tests са предварително фиксирани, agent няма право да ги променя за да заобиколи дефект.

**Опасна употреба:** agent първо пише слаб тест, после пише implementation според него, вижда red и изтрива/разхлабва assertion — тестът вече не е независима проверка.

Contract-first / test-first може да помогне, но не замества внимателния human review.

## 7. Agent tool access: най-малко права + актуална документация

Технически полезни интеграции според лекцията:

- **Docs/source lookup**: официална Spring Boot 4 docs, не стара Boot 3 синтактична версия.
- **CI read-only logs**: разбира коя проверка е FAIL/SKIP, вместо да твърди „tests passed“ само от terminal exit.
- **Source-control PR**: премахни ненужни file changes, защити одобрения test oracle.
- **Browser/Playwright**: само когато UI journey реално трябва да се докаже; не замества Spring MVC/DB tests.

**Security:** Не предоставяй без нужда production credentials, secrets, blanket write/shell permissions или автоматична възможност да заобикаля branch protection. Автоматизираните tools и untrusted retrieved instructions не са източник на authority.

## 8. Fast feedback и recovery не се изчерпват с tests

Тестова suite трябва да е **fast and trustworthy**. Но има failure modes извън всяко test environment: broken provider keys, инфраструктурен outage, data corruption, лош runtime config.

Нужни са:
- small PRs + focused CI;
- feature flags за отделяне на deploy от release;
- metrics, tracing и alerts;
- fast rollback и operational runbooks;
- периодична реална проверка на backup/restore;
- ownership на инцидентите.

Лекторът правилно подчертава: **typing може да се делегира; accountability остава човешка.**

## 9. Senior-level review questions

1. Какво точно доказва зеленият тест и какво **не**?
2. Кой дефект би оцелял при mutation `<` → `<=`?
3. Кой actor има право да променя acceptance criteria в AI PR?
4. Защо `@MockitoBean` в различни тестове може да вреди на context cache?
5. Каква оптимизация е забранена, ако намалява real DB coverage за критични Oracle SQL queries?
6. Как различаваш `SKIPPED` от `PASS` при CI?
7. Кога поставяш manual gate за security/payment changes, дори ако тестовете са green?

**Изходен checklist:**

- [ ] Business-oracle independent от implementation.
- [ ] Negative, boundary и failure cases.
- [ ] Real framework/transport tests където са необходими.
- [ ] No unsafe test deletion, assertion weakening или over-mocking.
- [ ] Determinism + isolation.
- [ ] Maven phases и Spring context reuse проверени.
- [ ] Реални CI evidence и human code review.
- [ ] Feature flag / rollback / runbook преди risky deployment.

**Източници:** [Devoxx](https://www.youtube.com/watch?v=d6D8nef-32c), [PIT Maven](https://pitest.org/quickstart/maven/), [Spring TestContext](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/caching.html), [Spring Boot 4.1 testing](https://docs.spring.io/spring-boot/reference/testing/).
