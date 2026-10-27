# 1.5 — Mutation Testing: от оцелял mutant до инженерно решение

> **Tech Radar P1 / TEST-AI-06 · Java 25 · JUnit 5 · PIT**  
> **Източник:** [Dave Aronson — Kill All Mutants! Intro to Mutation Testing, Devoxx Belgium 2026](https://www.youtube.com/watch?v=AsVt_dzTmN4) (49:41).  
> **Предпоставки:** [Test selection](01-TEST-SELECTION.md), [AI test oracle и основен PIT пример](03-AI-GUARDRAILS.md), [Runnable labs](04-PRACTICAL-LABS.md).

## 1. Production-like ситуация: 100% изпълнение на редовете, но пропуснато поведение

Имаме Java service, който изпраща payload на части. Тестът с `"ABC"` извиква метода, проверява получените три байта и минава. Дори всички редове в някакъв малък метод да са **покрити**, това не означава, че са проверени значимите му поведенчески варианти. При частичен запис или по-голям payload могат да се загубят байтове.

Друг пример е business правило: потребител може да се регистрира **точно на 18-ия си рожден ден**. Тест само с 30-годишен потребител не различава `age >= 18` от `age > 18`.

**Цел:** не само да знаем, че тестовете изпълняват кода, а да проверим дали биха открили малки, съществени промени в поведението.

## 2. Mental model: какво измерва mutation testing?

1. Първо имаме работеща версия на кода и **зелена baseline test suite**.
2. Mutator-ът прави малка промяна на **копие** на кода (един mutant), например `<` вместо `<=`, премахване на резултат или промяна на boolean условие.
3. Изпълняват се тестовете, които са релевантни за засегнатия код.
4. Ако тест засече промяната и се провали, mutant-ът е **KILLED**. Ако релевантните тестове минават, той е **SURVIVED**.
5. Разработчикът проучва причината: липсващ сценарий, слаб assertion, equivalent mutant, ненужен production code или нерелевантен за тестване ефект.

**Смислов модел:** mutant = контролиран експеримент „ако този детайл беше различен, кой тест щеше да го забележи?“. Убитият mutant е доказателство **само за откриване на конкретната промяна**, а не за цялостна correctness.

| Метрика | Какво може да каже | Какво не доказва |
| --- | --- | --- |
| Line/branch coverage | Изпълнени редове / branch-ове в измерените сценарии | Валидни assertions, правилен business oracle, липса на бъгове |
| Mutation score / test strength | Според конкретния PIT отчет: открити спрямо генерирани/покрити мутации | Пълнота на mutation operators, correctness за немоделирани изисквания |
| Review на оцелелите mutants | Защо определена промяна е останала незабелязана | Че всеки survivor е реален дефект |

**Не приравнявай** `NO_COVERAGE` на `SURVIVED`: първото означава, че няма изпълняващ реда тест; второто — че има, но не открива мутацията. `TIMED_OUT` може да сочи цикъл или нестабилност; `RUN_ERROR`, `NON_VIABLE` и `MEMORY_ERROR` са отделни категории за диагностика. Не наричай всяко отклонение „слаб тест“.

## 3. Технически механизъм — Java/PIT срещу общата теория

Лекторът използва **AST (abstract syntax tree)** като общ модел за много инструменти/езици. В Java нашият избран инструмент **PIT мутира компилиран JVM bytecode в паметта**, не AST и не production source файловете; затова първо трябва да има компилиран код и tests на classpath. PIT събира coverage за selection на тестове и приоритизира релевантните бързи проверки. Не твърдим, че всички нарисувани в лекцията мутации се поддържат от PIT.

Примерни *категории* промени (точният набор зависи от PIT mutator configuration):

- **Condition boundary:** `age >= 18` → `age > 18`; тестът трябва да включва точно 18.
- **Negate condition:** `acceptedTerms` → `!acceptedTerms`; тестът трябва да изразява contract и в двете посоки.
- **Math/increment:** `offset += written` → друг update; тестът трябва да наблюдава *целия* изпратен payload.
- **Return values:** резултат или boolean може да се подмени; тестът не бива да игнорира return contract.

Промяна на изходните `while` към `if` по-долу е **ръчна учебна мутация**, не обещание, че стандартните PIT mutators ще генерират точно тази трансформация. За PIT се доверяваме само на **действителния HTML/XML report**.

Единична мутация се анализира по-лесно; множество едновременни промени могат да се маскират и комбинаторно увеличават броя варианти. Това е близко до *competent programmer hypothesis*: много грешки са малки отклонения. Хипотезата не покрива всички production defects.

## 4. Пример A: boundary mutant и independent oracle

Business contract: `age == 18` означава **ELIGIBLE**. Мутация `>=` → `>` ще преживее тест с `age == 30`, но няма да преживее тест с `age == 18`. Използваме реалния [RegistrationPolicy.java](src/main/java/bg/hristomanov/education/aitesting/domain/RegistrationPolicy.java) и [RegistrationPolicyTest.java](src/test/java/bg/hristomanov/education/aitesting/domain/RegistrationPolicyTest.java), които са на база `LocalDate` и фиксиран `Clock`. Не заменяй имплементацията с `int age` — краткият пример е само обяснение на **contract boundary**.

**Разсъждение:** input → original outcome → mutant outcome → наблюдаван assertion. Ако двата изхода са еднакви за дадения вход, тестът не може да различи тази мутация. Не „лекуваме“ отчета с фиктивна assert проверка или пренаписване на business contract.

## 5. Пример B: chunked send — пропуснат сценарий *или* ненужен loop?

[ChunkedSender.java](src/main/java/bg/hristomanov/education/aitesting/mutation/ChunkedSender.java) пише байтове през `ChunkWriter`: при всяка итерация иска максимум `maxChunkSize`, а реално може да получи **partial write** (`0 < written <= requested`). Нулев или невалиден напредък е error, за да няма тихо загубени данни или безкраен цикъл.

[ChunkedSenderTest.java](src/test/java/bg/hristomanov/education/aitesting/mutation/ChunkedSenderTest.java) доказва:
- small payload и exactly-at-limit → коректен single write;
- *над лимита + partial writes* → накрая са възстановени **всички** байтове и исканията не надвишават лимита;
- empty payload и invalid writer progress имат предвидимо поведение.

**Ръчен mutation experiment:**
1. Запази baseline и пусни `mvn -pl spring/testing/ai-assisted-testing -Dtest=ChunkedSenderTest test`.
2. Временно промени само `while (offset < message.length)` на `if (offset < message.length)`; това е компилируемо и ще направи **само едно** изпращане.
3. Пусни целия `ChunkedSenderTest`: тестът за голям payload с partial writes трябва да е red, докато малкият payload остава green.
4. **Върни оригиналния код**; никога не commit-вай ръчната мутация като решение. Повтори PIT opt-in run и анализирай *реално генерираните* mutants.

**Двете диагнози за оцелял loop mutant:**

- **Missing test:** protocol допуска големи payload-и или partial writes, но тестовете подават само малки payload-и. Добавяме наблюдаем assertion за цялото съдържание и границата на chunk-а, не само `verify(writer).called()`.
- **Redundant production code:** ако *доказаният* domain/protocol contract гарантира payload `<= maxChunkSize` **и** всяко `write` записва всичко наведнъж, loop-ът може да е излишен. След contract review може да се опрости кодът, вместо да се измисля несъществуващ тестов сценарий. **Това не важи за текущия `ChunkWriter`, който изрично разрешава partial writes.**

Никога не премахвай retry/loop само защото текущите тестови fixtures са малки. Първо провери реалните ограничения и failure behavior на downstream клиента.

## 6. Survivor triage — професионална процедура

За всеки значим `SURVIVED`:

1. **Намери точната mutation и source location** в отчета; виж дали мутаторът реално променя публично наблюдаемо поведение.
2. **Провери coverage / execution path.** `NO_COVERAGE` изисква различна реакция: липсващ тест за този path или съзнателно изключване.
3. **Формулирай контрапример:** вход/състояние, за което original и mutant дават различни външни резултати. Ако не можеш, анализирай equivalent/non-observable mutation.
4. **Свери с business contract**, не с текущата реализация. Има ли изобщо изискване, което mutant нарушава?
5. **Избери действие:** (а) add/strengthen test и assertion; (б) отстрани доказано dead/redundant code; (в) обоснови equivalent/irrelevant mutation; (г) отбележи липсваща системна гаранция, която unit test не може да докаже.
6. **Провери regression:** original code green; targeted PIT rerun + diff review. Не „убивай“ мутанти с mocks на самото тествано поведение.

Типични сигнали: `SURVIVED` при променено сравнение → липсва boundary; `SURVIVED` при премахване на side effect → слаб assertion; `SURVIVED` в недостижим branch → уточни reachability/domain invariants; `TIMED_OUT` при промяна в loop → провери тестова стабилност и mutant effect.

## 7. Equivalent mutants и practical limits

**Equivalent mutant** има същото наблюдаемо поведение за всички допустими входове. Например при доказана инварианта `x >= 2` сравненията `x > 0` и `x >= 1` съвпадат: нов тест с недопустимо `x == 0` само заради показателя би бил грешно обучение. *Не всеки survivor е equivalent*: това е заключение след анализ, не удобен етикет.

Мутации, които засягат само незначим debug/logging текст, често имат нисък приоритет. Изключванията трябва да бъдат **тесни и обосновани**, иначе скриват пропуски. Mutation testing не замества integration с истинска DB, concurrency проверки, fuzz/property-based testing, contract testing или security reviews. Fuzzing променя *входните данни*; mutation testing променя *кода*; chaos testing инжектира *операционни откази*.

## 8. Изпълнение с PIT в `education`

От root на репото, с **JDK 25, Maven и компилирани класове**:

```bash
# Нормални unit/slice тестове — не включват автоматично PIT:
mvn -pl spring/testing/ai-assisted-testing test

# RegistrationPolicy: съществуващият строго ограничен PIT профил:
mvn -pl spring/testing/ai-assisted-testing -Pmutation test pitest:mutationCoverage

# ChunkedSender: отделен opt-in PIT профил, без промяна на глобалната CI политика:
mvn -pl spring/testing/ai-assisted-testing -Pmutation-chunking test pitest:mutationCoverage
```

HTML/XML под `spring/testing/ai-assisted-testing/target/pit-reports/` **само след успешен PIT run**. При два последователни run-а отчетите може да се презапишат — пази отделен evidence snapshot, ако сравняваш резултати. Не твърди конкретен брой KILLED/SURVIVED, score или build duration без действително изпълнение.

**Важно:** PIT mutator selection не е идентичен на учебните ръчни промени. Ако в отчета няма mutant, съответстващ на `while` → `if`, това не опровергава тестовия експеримент.

## 9. CPU/CI trade-offs и качествени gates

Mutation testing изпълнява много тестове, потенциално за много mutants. Цена `~ брой mutations × среден брой тестове на mutant` е *rough model*, не SLA или измерено време.

1. **Локално / PR:** целеви package или клас с критични business decisions, както двата opt-in профила тук.
2. **По-широко периодично:** само ако има измерен полезен ефект и ресурсен бюджет. В PIT има history/incremental analysis, но то има предположения/ограничения при променени зависимости и **не е еквивалент на безгрешен Git diff test selection**.
3. **Gate за нов/променен код:** първо събери baseline, дешифрирай survivor-ите, дефинирай scope/exceptions и допустима цена; тогава въвеждай threshold. Не налагай 100% на legacy монолит или на equivalent mutants.
4. **Инженерният review** проверява конкретните оцелели мутации, coverage holes, flakiness, timeout и CI logs. `SKIPPED` не е `PASS`.

**Ние не включваме mutation gate в стандартната CI конфигурация.** Няма доказателства за throughput/score преди реално изпълнение.

## 10. Senior-level диагностика (отговори с causal explanation)

1. Как `100% line coverage` може да съществува заедно с лоша проверка на логиката? Дай пример без assertion.
2. Кога `SURVIVED` и `NO_COVERAGE` налагат различни действия?
3. Защо тест с `age == 30` не убива `>= 18` → `> 18`? Кой е минималният контрапример?
4. При `ChunkedSender` тест с payload 3 байта минава и при `while` → `if`. Кои доказателства са нужни преди да изтриеш loop-а?
5. Как доказваш, че мутантът е equivalent, вместо да прикриеш непокрит scenario?
6. Защо PIT за Java не трябва да се описва като AST/source mutation tool?
7. Може ли `TIMED_OUT` автоматично да се отчете като коректно убит mutant? Какво проверяваш?
8. Какъв би бил **разумен** quality gate за нов financial business rule при бавен build и legacy baseline?
9. В кои случаи mutation testing няма как да докаже истинска transaction/SQL/integration correctness?
10. Как би обяснил на колега разликата между coverage, mutation score и business correctness, без да се позоваваш само на проценти?

**Професионална формулировка:** „Mutation testing оценява способността на тестовия набор да открива конкретни, автоматично въведени промени в изпълнимата логика. Оцелял mutant е повод за анализ на contract, assertions и production code, а не автоматично доказателство за бъг.“

## 11. Връщане към началния казус и code-review checklist

Пропуснатите байтове при голям payload остават невидими за малкия happy-path test; проверката за частичен запис прави поведението наблюдаемо. Ако реалният договор гарантира single write, вместо да трупаме безполезни тестове, опростяваме излишната логика след review.

- [ ] Baseline тестовете са зелени и determinisitic; тестовете имат independent oracle.
- [ ] Проверени са важни boundaries, negative paths и partial/timeout cases.
- [ ] Анализирано е **защо** всеки приоритетен mutant оцелява.
- [ ] Equivalent/irrelevant cases са документирани, а не масово изключени.
- [ ] PIT конфигурацията е ограничена по класове/тестове и има измерен cost budget.
- [ ] Няма фиктивни проценти или твърдения, че нестартиран CI/PIT е PASS.
- [ ] Крайният критерий остава business behavior, не „убий 100% от mutants“.

## Източници

- [Dave Aronson, Devoxx 2026 — Kill All Mutants!](https://www.youtube.com/watch?v=AsVt_dzTmN4) — особено 1:40–5:50 (coverage vs strictness), 25:50–34:30 (survivor triage vs redundant code), 36:25–42:45 (cost/CI).
- [PIT — basic concepts, statuses and equivalent mutations](https://pitest.org/quickstart/basic_concepts/).
- [PIT — Maven quickstart](https://pitest.org/quickstart/maven/).
- [PIT — FAQ: bytecode, test selection, scope](https://pitest.org/faq/).
- [PIT — incremental analysis and its limits](https://pitest.org/quickstart/incremental_analysis/).
