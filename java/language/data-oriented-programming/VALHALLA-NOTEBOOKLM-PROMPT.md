# NotebookLM P1 — Valhalla Value Objects, Identity и JDK 28 Migration

**Източници за качване:**
- **Нов deep dive:** [José Paumard и Rémi Forax — Will Valhalla Fix Tony's Billion Dollar Mistake?](https://www.youtube.com/watch?v=vLDbzmiT7Xs), предоставен auto-generated SRT (~2:40:57); основно 90–115 мин (atomic flattening/benchmarks), 115–123 мин (class-file descriptors), 129–158 мин (future null restrictions и primitive classes).
- [Nicolai Parlog — Java Next, Devoxx 2026](https://www.youtube.com/watch?v=ufhs9THbYZI) и предоставения Gemini SRT (автоматично генериран — възможни грешки).
- [P1 Valhalla урокът](VALHALLA-VALUE-OBJECTS.md) + [JDK 28 EA compatibility lab](JDK28-EA-LAB.md).
- [OpenJDK JEP 401](https://openjdk.org/jeps/401) и [официалното предупреждение за JDK 28 compatibility от 27.10.2026](https://inside.java/2026/10/27/quality-heads-up/).
- [P2 Amber предложенията](AMBER-FUTURE-DESIGN.md), [Leyden JDK 28](../../jvm/performance-evolution/LEYDEN-JDK28-AOT-CODE.md) и [P3 Tech Radar](../../JAVA-NEXT-TECH-RADAR.md).

## Готов prompt за аудио — 35–45 мин, два гласа, на български

**Разширение P1/P2/P3 — използвай оригиналната лекция на Paumard/Forax, не дублирай стария обзор.** Добави след основното аудио отделен 15–20-минутен segment за следните проверими въпроси:

1. **P1 nullable flattening:** разграничение между JIT scalarization (възможно елиминиране на алокации) и heap flattening (inline storage); nullable marker и atomicity за mutable field/array. Обясни конкретния Song compressed-oops пример **32-bit ref + short 16 + marker 8 = 56 bits**, срещу int **32+32+8 = 72 bits**. Подчертай **различните layout правила** за strictly initialized `final` field и `NULLABLE_NON_ATOMIC_FLAT`; лимитът 64 bits НЕ е универсална Java language граница.
2. **Performance парадокс:** малък 100-item array може да загуби от unpacking, голям 1-million-item array може да спечели чрез locality. Това е **конференционен workload**, НЕ наше JMH измерване. Обясни защо nullable и generics/Streams влияят на representation/boxing и защо не сменяме domain int с short заради спекулативен speedup.
3. **P2 референтен JDK28 EA proof:** покажи [JDK28-EA-LAB.md](JDK28-EA-LAB.md), [LoadableDescriptorsProbe.java](jdk28-ea/LoadableDescriptorsProbe.java), [NullableFlatLayoutPreview.java](jdk28-ea/NullableFlatLayoutPreview.java) и [verify-valhalla-layout.sh](jdk28-ea/verify-valhalla-layout.sh). Еднакъв Java25 source с legacy/preview classfile: `javap -verbose` доказва metadata `LoadableDescriptors`, а двата main-а запазват бизнес поведението. **Не** обещавай physical flattening от `javap`.
4. **P3 null future watch:** изследваният `Song!` синтаксис **не е Java28 feature** и може да наруши overload resolution/API migration при повторна компилация; primitive classes/default values/operator overload остават proposals. Разграничи nullable JEP401 `value record` от null-restricted storage и `Objects.requireNonNull` днес.
5. **Практическо сравнение:** кога и защо Java25 record + JUnit е по-добър избор от experimental preview; разходи на CI/EA workflow, version pins и backwards compatibility.

Изисквай за всяка техника **проблем, кога, кога НЕ, печалби, загуби, проста алтернатива, код и тест**. Не извеждай абсолютни benchmark ns/op от auto-generated субтитри, когато цифрите се виждат само на слайд. Не маркирай знанието като усвоено без преговор и самостоятелни отговори.

## Готов prompt за аудио — 35–45 мин, два гласа, на български

Създай задълбочена практическа Java backend аудиолекция за Mid+ → Early Senior с ясна структура, техническа терминология на английски и кратки обяснения на български при първа употреба. Не преразказвай целия Devoxx обзор; **главната тема е Project Valhalla, value objects, identity-free semantics и реални compatibility рискове**.

Входен сценарий: бизнес backend с payments/protocol snapshots; поддържаме Java 25 LTS и миграцията към нов JDK. Стар код използва == на LocalDate, WeakReference за date keys и implicit assumptions за record identity. Защо това е грешно, при какви условия ще се прояви и как да го докажем?

1. 5 мин: различи value object, value-based class, ordinary record, value record, equals, statewise ==.
2. 7 мин: обясни защо JEP 401 е JDK 28 **preview**, а не JDK 25, и защо 30 JDK класа стават value classes само при включен preview, дори за стар bytecode.
3. 10 мин: премини през README→код: MoneySnapshot, PaymentSnapshot.withAmount, ValueSafeOperations, ValhallaReadinessTest; обясни validation, errors, currency semantics, arithmetic overflow и immutability.
4. 8 мин: сравни с JDK28-EA-LAB: един и същ Java 25 bytecode с preview OFF/ON, LocalDate ==, WeakReference IdentityException, а отделно компилирания value record ValueMoney/ValuePrice. Много важно: за ValuePrice с identity String fields record.equals може да е true, но value == false. Няма benchmark твърдения.
5. 5 мин: защо flattening, specialized generics, null restrictions, user-defined operators още НЕ са гарантирани от JEP401. Кога не използваме preview и каква е по-простата стабилна алтернатива.
6. 4 мин: практични steps за библиотека/framework и CI dual-mode test, record constructor compatibility, reflection, weak maps, monitor locks.
7. 2–4 мин: senior-level checklist, 4 въпроса за устна самопроверка и изричен decision contract (проблем, кога, кога НЕ, какво печелим/губим, код и тест).

Кратък appendix (максимум 3 минути): JDK 28 Leyden AOT code compilation надгражда cache, Amber withers са proposal, Babylon/Detroit остават P3 Watch. Без повторение на Loom/Panama/JFR курсовете.

**Правила за достоверност:** не измисляй резултати от benchmark, не твърди, че всеки Java 25 record е value object, не представяй proposed withers/carrier classes като компилируем syntax и не приемай JDK 28 EA за production GA. Разграничавай какво казва лекторът от актуализираното OpenJDK QA предупреждение. Покажи как проверяваме с реалните source и тестове, а не само теория.
