# NotebookLM P1 — Valhalla Value Objects, Identity и JDK 28 Migration

**Източници за качване:**
- [Nicolai Parlog — Java Next, Devoxx 2026](https://www.youtube.com/watch?v=ufhs9THbYZI) и предоставения Gemini SRT (автоматично генериран — възможни грешки).
- [P1 Valhalla урокът](VALHALLA-VALUE-OBJECTS.md) + [JDK 28 EA compatibility lab](JDK28-EA-LAB.md).
- [OpenJDK JEP 401](https://openjdk.org/jeps/401) и [официалното предупреждение за JDK 28 compatibility от 27.10.2026](https://inside.java/2026/10/27/quality-heads-up/).
- [P2 Amber предложенията](AMBER-FUTURE-DESIGN.md), [Leyden JDK 28](../../jvm/performance-evolution/LEYDEN-JDK28-AOT-CODE.md) и [P3 Tech Radar](../../JAVA-NEXT-TECH-RADAR.md).

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
