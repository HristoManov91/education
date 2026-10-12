# Learning Progress — актуален отчет

**Snapshot дата:** 2026-10-12 (Europe/Sofia)  
**Цел:** системна диагностика от Junior fundamentals към уверен Mid+ и Early Senior.  
**Източник на оценките:** действителни отговори в диалог + обоснована обратна връзка. Не попълвай проценти без проведена проверка.  
**Протокол:** [ASSESSMENT-PROTOCOL.md](ASSESSMENT-PROTOCOL.md) · **План:** [ROADMAP.md](ROADMAP.md)

## Overview по области

| Област | Приоритет | Диагностика | Доказана степен |
| --- | --- | --- | --- |
| Java Core, Collections, Concurrency, JVM | **P0** | **Започната** — Java fundamentals | Все още **неопределена** |
| SQL, Database Performance, Persistence | **P0** | Не е започната | Непроверено |
| Spring Core / Web / Security | **P0** | Не е започната | Непроверено |
| Testing & Reliability | **P0** | Не е започната | Непроверено |
| System Design, Distributed Systems | P1 | Не е започната | Непроверено |
| JVM Diagnostics / Observability | P1 | Не е започната | Непроверено |
| DS&A | P1 | Не е започната тази диагностика | Непроверено |
| Caching, Containers, AI Engineering | P1 | Не е започната тази диагностика | Непроверено |
| Design Patterns / Advanced Architecture | P2 | Не е започната тази диагностика | Непроверено |

> Има изградени лаборатории/обучителни материали и по други теми, но **наличието им не е доказателство**, че знанията вече са проверени или усвоени.

## Текущо: Java Fundamentals и OOP — адаптивна диагностика

| Поле | Състояние |
| --- | --- |
| Активна родителска тема | **JAVA-OOP-03 — Q11 (отговорен 2026-10-12):** Java records, component equality, compact constructors, defensive copying и Spring DTO design |
| Последна активност | **2026-10-12** — Q11: 6/6 верни реда, правилно `record` accessor/equals/hashCode/toString и защитно `List.copyOf`, компактен конструктор частично изяснен; добавен дизайн анализ за DTO vs Entity |
| Статус | **JAVA-OOP-03 — TARGETED_REVIEW за Q11** (точна механика на compact canonical constructor; deep vs shallow immutability; DTO дизайн), Q10 има коригирано 9.5/10 разбиране и е готов за recall; всички непроведени recalls са отложени по изрично желание |
| Вече отговорени | Q1–Q5 и checkpoint №1; **Q6** — constructors/final/invariants; **Q7** — arrays/clone (5/5); **Q8** — inheritance/field hiding/casting (5/5); **Q9** — overload/dispatch/downcast (6/6 преди exception); **Q10** — interfaces/abstract classes (4/4); **Q11** — record/compact constructor/copyOf/equals (6/6) |
| Въпрос 1 — ориентировъчно | **90% само за Q1** (от предишната оценка); правилни изходи: `10`, `20`, `AB`, `AB` |
| Въпрос 2 — оценка от обратната връзка | **9.5/10 само за Q2**; правилни `Maria` и `20`, правилно обяснение на mutation и reassignment |
| Въпрос 3 — оценка от обратната връзка | **8/10 разбиране; 8.5/10 комуникация — само Q3**; правилни `Hello` и `Hello World` и правилно обяснение за `concat()`; допуснато неточно обяснение на `StringBuilder.append()` |
| Въпрос 3.1 / Q3b — частична оценка | **9/10 техническо разбиране само за Q3b**; верен mental model за `append()`, `==` и reassignment; разменен ред на последните две стойности: действителният изход е `false`, `C`, `ABD` |
| Въпрос 4 — финална оценка | **8.5/10 техническо разбиране; 8.5/10 комуникация — само Q4.** Първоначалното `a == b` е сбъркано (`true` вместо `false`); след корекция потребителят правилно обясни новия екземпляр от `new String` и интернирания литерал. Потвърди, че `END` не се изпълнява след NPE. Каза, че познава `Object.equals()` и `Objects.equals()`, но не ги е споменал първоначално. |
| Терминология за затвърждаване | Q7: reference value/final/shallow copy; Q8–Q9: static type vs runtime type, overloading vs dispatch; Q10: interface fields и Java 25 flexible constructors; **Q11: compact canonical constructor НЕ извиква втори canonical constructor; component fields се инициализират неявно след тялото; няма обща забрана на `this`, но няма директно присвояване на component fields; unmodifiable vs deep immutable; record equality semantics и DTO/value object design** |
| Следващо действие | **Q11 приключен с обратна връзка (9/10 техническо и 9/10 изказ само за Q11).** Изчакай изрично съгласие за нов въпрос. Непроверените варианти (изход без `copyOf`, дълбока неизменяемост) и всички recalls остават за по-късен етап, без фиксирана дата. |
| Финална оценка на Java Fundamentals | **Няма достатъчно данни** — оценките са само за отделни въпроси |

### Какво вече доказаха отговорите

- **Q1:** независимо копиране на примитивна стойност; две референции към един споделен изменяем обект. Корекция: и примитивни стойности, и референции, и обекти заемат памет; не твърдим фиксирано stack/heap правило.
- **Q2:** `p` и `person` са независими променливи; копира се стойността на референцията; `person.name = "Maria"` изменя споделения обект; `person = new Person("Peter")` пренасочва само локалния параметър; `age = 30` променя локалното копие.
- **Q3:** `String.concat(" World")` не изменя `String` обекта; локалното `text` не променя `main`; `builder` от `main` става `Hello World`. Първоначално беше обяснено неточно поведението на `append()`.
- **Q3b / 3.1:** в последващия отговор вече правилно е обяснено, че `append()` връща същия builder, `second` продължава да сочи стария обект при пренасочване на `first`, а `==` е `false`. **Единствена грешка в отговора:** разменени са отпечатванията на `first` и `second`; изходът е `false`, `C`, `ABD`.
- **Q4:** първоначално правилно са обяснени `equals()` и `null`, но `a == b` е сгрешено; след корекция потребителят правилно обясни отделния обект от `new String`, а редът на декларациите е новонаучена особеност. Потвърди, че NPE спира изпълнението преди `END`, и че знае за `Object.equals()` и `Objects.equals()`. Частична оценка само за Q4: **8.5/10 техническо разбиране и 8.5/10 комуникация**.
- **Q7 (2026-10-12):** вярно компилиране и 5/5 изхода; правилни aliasing, primitive-array `clone()`, пренасочване, `==` identity и compile-time error при `original = snapshot`. Целеви езикови корекции: `final` е за променливата, не за обекта; Java reference не се свежда непременно до суров адрес. Обяснена е и shallow-copy спецификата на масив от обекти, **не е независимо изпитана**.
- **Q8 (2026-10-12):** вярно компилиране и 5/5 изхода `Animal`, `Woof`, `Animal: Woof`, `Dog`, `Loud Woof`. Правилно обяснява разликата между field access и overriding в `description()`, както и че `pet.speak("Loud")` без cast не се компилира. **Концептуална неточност:** твърди, че cast променя поведението на обекта; коригирано е, че cast променя compile-time типа на израза след runtime type check, но не променя обекта. **Терминологична неточност:** „отляво стои поведението“ вместо „статичен тип на променливата“.
- **Q9 (2026-10-12):** 6/6 правилни изхода преди ClassCastException: `Dog -> Animal`, `Dog -> Dog`, `Dog -> Animal`, `Dog -> Dog`, `true`, `BEFORE`. Правилно обяснява compile-time видимите overload-и и ефекта на cast върху static type на receiver/argument, а също и alias identity. **Корекция:** `Animal` е по-общ superclass, а `Dog` по-специфичен subclass; `Animal → Dog` е downcast, не widening. **Доизказване:** compile-time изборът на overload и runtime изборът на override implementation са два отделни етапа. Q9 техническа оценка **9.5/10**, изказ **8.5/10**.
- **Q11 (2026-10-12):** кодът компилира и 6/6 реда са правилни: `[Book]`, `[Book]`, `true`, `false`, `10`, `BLOCKED`. Обяснява record accessors (без `get`), auto `equals/hashCode/toString`, списъчно равенство по елементи и ред, `==` identity, unmodifiable `List.copyOf`, UOE runtime. Compact constructor е разпознат като място за validation/normalization, но формулиран като „продължава към нормалния constructor“ — уточнено: това е canonical constructor, не извиква друг; компонентните полета се присвояват неявно след тялото. За `this` е посочена несигурност; JLS забранява изрично присвояване на component fields, но не употребата на `this` във всеки контекст. **Не са независимо отговорени**: точен изход без `copyOf`, разграничение shallow/deep и архитектурен дизайн за Spring DTO — обяснени след отговора, без да се представят като доказано овладени. Грешка не се отчита за неизказани резултати. Локална оценка **9/10 техническо, 9/10 изказ**.
- **Q10 (2026-10-12; уточнение след feedback):** 4/4 верни изхода (`EMAIL:I:42`, `I:42`, `S:99`, `99`). Верен constructor chain/abstract class модел, default inheritance vs overriding, `Task.code()` и static type check за `second.info()`. След cast към `Task` — валиден метод и резултат `SMS:S:99` (потребителят не даде този конкретен низ, но правилно прогнозира работа). Първоначално е казано „интерфейсът има само статични методи или константи“, но в следващ отговор изрично уточни, че е имал предвид **само статични полета**, не методи, и вече знае липсата на interface instance fields. Това е **лапсус/неточен изказ**, а не доказан концептуален пропуск: вече беше обяснил правилно `default` instance метода. Потвърди, че знае невъзможността да се намалява видимостта при overriding. **Новост за него**, а не фундаментален пропуск, е Java 25 Flexible Constructor Bodies, позволяващ допустими инструкции преди `super(...)`. Коригирана частична оценка за Q10: **9.5/10 техническо, 9/10 изказ**. Включи само новите детайли в по-късен recall.

**Постоянни материали за преговор и NotebookLM:**
[Q1 — Values, References & Aliasing](concepts/01-values-references-aliasing.md) ·
[Q2 — Method Arguments: Pass-by-Value](concepts/02-method-arguments-pass-by-value.md) ·
[Q3 — String & StringBuilder](concepts/03-string-immutability-stringbuilder.md) ·
[Q4 — Object Identity, Equality & Null](concepts/04-object-identity-equality-null.md) ·
[Q5, част 1 — Numeric Promotions, Overflow & Compound Assignment](concepts/05-numeric-promotions-overflow-compound-assignment.md) ·
[Q5, част 2 — Autoboxing, Unboxing & Integer Caching](concepts/06-autoboxing-unboxing-integer-cache-null.md) ·
[Checkpoint №1 — Java Fundamentals Q1–Q5](concepts/07-java-fundamentals-integrated-checkpoint.md) ·
[Q6 — OOP Fields, Constructors, private, final & Encapsulation](concepts/08-oop-fields-constructors-final-encapsulation.md) ·
[Q7 — Arrays, Clone & Final References](concepts/09-arrays-clone-final-reference.md) ·
[Q8–Q9 — Inheritance, Overloading, Overriding & Polymorphism](concepts/10-inheritance-overriding-polymorphism.md) ·
[Q10 — Abstract Classes, Interfaces & Default Methods](concepts/11-abstract-classes-interfaces-default-methods.md) ·
[Q11 — Records, Immutability & Defensive Copying](concepts/12-records-immutability-defensive-copying.md).

### Recall queue — поправено знание и професионален изказ (пауза по молба от 2026-10-12)

| Кога | Първоначален ориентир (Europe/Sofia) | Състояние | Проверка |
| --- | --- | --- | --- |
| D+1 | **2026-10-09** | **COMPLETED — targeted correction verified** | Първи отговор: 4/4 верни изхода (`Java`, `A!`, `false`, `true`), правилни pass-by-value, mutation/reassignment, String Pool; първоначална грешка за default `Object.equals()`. След обяснение потребителят самостоятелно и правилно различи `Object.equals()` (**identity**) от override-натия `String.equals()` (**съдържание/последователност от символи**). Първи отговор: **8.5/10 техническо, 8/10 комуникация**; follow-up: **10/10**; не изчислявай изкуствена обща оценка. |
| D+7 | **2026-10-15** | **DEFERRED_BY_USER** (без нова дата) | Променен сценарий с параметър, alias, `String` и `StringBuilder`, плюс `==`, `equals()` и `null` проверка |
| D+30 | **2026-11-07** | **DEFERRED_BY_USER** (без нова дата) | Кратка интеграционна задача с refs, method args, String/Builder, object equality и професионално обяснение |
| Q5.1 · D+1 | **2026-10-10** | **DEFERRED_BY_USER** (без нова дата) | Кратък изменен пример: тип на `byte` аритметика, `+=` срещу `=`, кога настъпва implicit cast. Без подсказване. |
| Q5.1 · D+7 | **2026-10-16** | **DEFERRED_BY_USER** (без нова дата) | Пример с `short`/`byte`, бинарно числово повишаване, допустимо/недопустимо присвояване и overflow. |
| Q5.1 · D+30 | **2026-11-08** | **DEFERRED_BY_USER** (без нова дата) | Интеграционна мини-задача с `int`/`long`, overflow, widening/narrowing, compound assignment. |
| Q5.2 · D+1 | **2026-10-10** | **DEFERRED_BY_USER** (без нова дата) | Нов случай `Integer` срещу `Integer` и `int`; boxing cache и автоматичен unboxing без подсказване. |
| Q5.2 · D+7 | **2026-10-16** | **DEFERRED_BY_USER** (без нова дата) | Обясни wrapper cache гаранции и как типът на операндите променя `==`, плюс safe handling при `null`. |
| Q5.2 · D+30 | **2026-11-08** | **DEFERRED_BY_USER** (без нова дата) | Реалистичен DTO/ORM сценарий с nullable `Integer`, числово сравнение и причинно-следствен анализ на NPE. |
| Checkpoint · D+1 | **2026-10-11** | **DEFERRED_BY_USER** (без нова дата) | Без подсказване: два aliases към един `Integer`, локален `++` върху единия — кои референции се променят, кой обект остава immutable и как работи post-increment. |
| Checkpoint · D+7 | **2026-10-17** | **DEFERRED_BY_USER** (без нова дата) | Нов случай: `Integer++` в метод, други aliases и nullable wrapper; различи reassignment и mutation с точни термини. |
| Checkpoint · D+30 | **2026-11-09** | **DEFERRED_BY_USER** (без нова дата) | Комбиниран пример `Integer`/`StringBuilder`, pass-by-value и wrapper increment с проверка на ефекта в caller. |
| OOP-01 · D+1 | **2026-10-11** | **DEFERRED_BY_USER** (без нова дата) | Precondition versus invariant, checked addition and safe update order. |
| OOP-01 · D+7 | **2026-10-17** | **DEFERRED_BY_USER** (без нова дата) | New public method scenario, ensure state valid and no overflow. |
| OOP-01 · D+30 | **2026-11-09** | **DEFERRED_BY_USER** (без нова дата) | Constructor, encapsulation and invariant scenario. |
| Q7 arrays · D+1 | **2026-10-13** | **DEFERRED_BY_USER** (без нова дата) | Формулирай със свои думи aliasing vs array cloning, и `final` variable vs mutable object. |
| Q7 arrays · D+7 | **2026-10-19** | **DEFERRED_BY_USER** (без нова дата) | Масив от mutable обекти: кои обекти са shared след `clone()` и какво се изменя при `append` vs slot assignment? |
| Q7 arrays · D+30 | **2026-11-11** | **DEFERRED_BY_USER** (без нова дата) | API scenario за defensive copy, `final` reference, reassignment и mutation. |
| Q8 inheritance · targeted recall | **Без нова дата** | **DEFERRED_BY_USER** | Нов сценарий: `Animal pet = new Dog()` и cast/field hiding; обясни защо cast не променя обекта. Да се провери по-късно, не веднага. |
| Q9 overload/override · targeted recall | **Без нова дата** | **DEFERRED_BY_USER** | Overload resolution по статичен тип на аргумента срещу runtime dispatch по действителен тип на получателя; Animal superclass → Dog subclass като downcast. Да се провери в по-късен етап. |
| Q10 abstract/interface · targeted recall | **Без нова дата** | **DEFERRED_BY_USER** | По-късно провери новия Java 25 Flexible Constructor Bodies и, при необходимост, различните interface methods. Понятието за статични интерфейсни полета е вече правилно и изяснено; не третирай първоначалния лапсус като непознато знание. |
| Q11 records · targeted recall | **Без нова дата** | **DEFERRED_BY_USER** | Как работи compact canonical constructor и неявното присвояване; промени в `source` със/без `List.copyOf`; разлика между unmodifiable list и deep immutability; `record` DTO срещу JPA entity. |

**Решение от 2026-10-12:** ученикът изрично поиска да отложим текущите преговори, защото материалът още му е пресен, и да продължим с нови въпроси. Всички непроведени recall проверки Q1–Q7 остават **непроведени, не са преминати и не са отменени като учебна цел**, но първоначалните дати са исторически ориентир, не активни срокове. Върни ги в по-късен учебен етап (след нови концепции) според желанието му, а не при следващото влизане в чата. Няма автоматични reminders. Q8 и Q9 от JAVA-OOP-02 са **вече отговорени на 2026-10-12** и получили обратна връзка. След Q9 остава за по-късно терминологична проверка на типова йерархия и formal compile-time selection vs runtime dispatch. Няма независимо възпроизвеждане след последната корекция.

### Точка на спиране след завършен Q4

**Q4 е приключен на 2026-10-08.** На D+1 (2026-10-09) String Pool е възпроизведен правилно; след корекция `Object.equals()` също е **потвърден самостоятелно**. На D+7/D+30 провери и двете, в нов контекст.

**Q5 части 1–2 и targeted follow-up за Q5.2 са проведени на 2026-10-09.** На 2026-10-10 е завършен интеграционен checkpoint №1 върху Q1–Q5, с 7/7 верни изхода след корекция на лапсус и една поправка на mental model: `Integer++` не изменя wrapper обекта. **Не третирай Q5 или целия Java Fundamentals като MASTERED** преди допълнителните recall проверки. Не започвай нова тема без изрично съгласие.

## Детайлен progress по микро-теми

| ID | Status | Доказателства до момента | Изказ/корекция | Последна активност | Следващо действие |
| --- | --- | --- | --- | --- | --- |
| JAVA-FND-01 | `TARGETED_REVIEW` | Q1: верни резултати; D+1: правилен модел; **Q7 2026-10-12: 5/5 изхода, верен alias/`clone()`/`final`** | Казвай „копирана стойност на референцията“, а не физически адрес; `final` променлива, не „final обект“. Shallow копието на масив от обекти е показано, но още не е изпитано. | 2026-10-12 | Recalls Q1–Q7 са отложени по желание на потребителя, без нов срок |
| JAVA-FND-02 | `READY_FOR_RECALL` | Q2: 9.5/10; на D+1 правилно различава локално reassignment от видима mutation | Pass-by-value: копие на стойността на референцията | 2026-10-09 | D+7/D+30 recall |
| JAVA-FND-03 | `READY_FOR_RECALL` | Q4: 8.5/10 техн. и 8.5/10 изказ; D+1: верен `String Pool`, първоначална грешка за default `Object.equals()` и **успешно независимо повторно обяснение (10/10)** | Базовият `Object.equals()` сравнява идентичност; `String.equals()` override-ва за сравнение на съдържанието (последователност от символи, без фиксиран алгоритъм) | 2026-10-09 | D+7/D+30 recall с различен пример |
| JAVA-FND-04 | `READY_FOR_RECALL` | Q3: 8/10, Q3b: 9/10; D+1: 4/4 верни изхода и вярна разлика между `concat()` и `append()` | `String` immutable не гарантира нов екземпляр за всяко извикване | 2026-10-09 | D+7/D+30 recall |
| JAVA-FND-05 | `TARGETED_REVIEW` (Q5 и checkpoint №1) | Q5.1: 8/10 техн., Q5.2 първи отговор 6.5/10 техн., targeted follow-up 9/10 техн.; checkpoint №1 на 2026-10-10: правилни всички 7 резултата, **8.5/10 техническо и 8.5/10 изказ**. | При `attempts++` Java изпълнява `Integer → int`, `+1`, `int → Integer` и **reassignment**, не mutation; оригиналният `Integer(127)` не се изменя. Други механизми — правилно. | 2026-10-10 | Непроведените recall проверки са отложени по искане на потребителя на 2026-10-12; първоначалните дати са запазени в queue |
| JAVA-OOP-01 | `READY_FOR_RECALL` | Q6 completed on 2026-10-10; constructor, private and final correct, negative amount and overflow identified in follow-up. | 9/10 technical and 8.5/10 communication. Validate amount before arithmetic, guard overflow, then assign. | 2026-10-10 | Recall отложен по искане на потребителя на 2026-10-12, без нова дата |
| JAVA-OOP-02 | `TARGETED_REVIEW` | Q8: 5/5 изхода, **9/10 техническо и 8.5/10 изказ**. Q9: 6/6 изхода и правилен `ClassCastException`, **9.5/10 техническо и 8.5/10 изказ**, пълно обяснение за 4 overload/override извиквания. | Cast не променя обекта; superclass `Animal` е по-общ от subclass `Dog`; `Animal → Dog` е downcast; избор на overload при компилация и dispatch при runtime. | 2026-10-12 | Изчакай изрично съгласие за нов въпрос. Terminology recall по-късно, без фиксирана дата. |
| JAVA-OOP-03 | `TARGETED_REVIEW` (Q11) | Q10: 4/4 и уточнено правилно разбиране, **9.5/10 техн., 9/10 изказ**. Q11: 6/6 правилни изхода, верен `equals`, record accessor и `List.copyOf`, **9/10 техн., 9/10 изказ** за Q11. | Q10: новост Java 25 flexible constructors. Q11: compact е форма на canonical, component fields се присвояват неявно след тялото; record е shallow immutable, не непременно deep; дизайн DTO vs Entity. | 2026-10-12 | Изчакай съгласие за нов въпрос. Targeted recall по-късно, без фиксирана дата. |

## Дневник на заниманията

### 2026-10-12 · Q11 — JAVA-OOP-03: Records, compact constructors, copying и DTO дизайн

**Условие:** `OrderView` record с компоненти `long id` и `List<String> items`, compact constructor с `id > 0` и `items = List.copyOf(items)`. Създаване от `ArrayList` и `List.of`, промяна на `source`, сравнения `equals/==` и опит за `add()` през accessor.

- **Резултат:** компилира се; изход `[Book]`, `[Book]`, `true`, `false`, `10`, `BLOCKED` — всички **6/6 правилни**.
- **Правилно разбрано:** Record има component accessors `id()/items()` вместо JavaBeans getter имена, предоставя `equals/hashCode/toString`, полетата са private/final. Извикването на record `equals()` сравнява компонентите, а `List.equals()` е по елементи/ред; `==` е object identity. `List.copyOf` връща unmodifiable list, а опитът за mutation хвърля `UnsupportedOperationException` по време на изпълнение. Compact constructor е за validation/normalization.
- **Уточнение:** „compact constructor продължава към нормалния canonical“ е неточен mental model: compact **е** canonical constructor. При нормално завършване компилаторът осигурява присвояванията към полетата от актуалните параметри. Не е правилно да се каже „`this` е изцяло забранен“; забранени са explicit assignments към component fields в compact constructor; преди инициализация директното им четене също не е допустимо.
- **Допълнение за `equals()`:** не приемаме буквално генериране на `Objects.equals()`; правилата са семантични. За floating primitives използват wrapper `compare`; масивните компоненти ползват array `equals` (референтна идентичност), освен ако не пишем собствен `equals`.
- **Не са дадени самостоятелни отговори** за точния изход без `List.copyOf`, deep immutability и архитектурния избор record/ordinary DTO; по молба е дадено подробно професионално обяснение и примери. Не отчитай тези аспекти като грешка или като вече изпитано усвоени.
- **Локална оценка само за Q11:** **9/10 техническо разбиране, 9/10 техническа комуникация**. Важните нови подробности са показани, но още не са независимо потвърдени.
- **Concept Note:** [Records, Immutability & Defensive Copying](concepts/12-records-immutability-defensive-copying.md).
- **Recall:** DEFERRED_BY_USER, без фиксирана дата. Не започвай нов въпрос без изрично съгласие.

---

### 2026-10-12 · Q10 — JAVA-OOP-03: Abstract Classes, Interfaces & Default Methods

**Условие:** `Task first = new EmailTask("42"); Describable second = new SmsTask("99");`, interface `Describable` с `code()` и `default description()`, abstract `Task` със `private final code`, конструктор, concrete `code()`, abstract `type()` и concrete `info()`. Проверка на 4 изхода, поведение на конструктори, default/override, `second.info()` и каст, плюс избор на дизайн.

- **Верен изход (4/4):** `EMAIL:I:42`, `I:42`, `S:99`, `99`; кодът се компилира.
- **Вярно обяснен механизъм:** абстрактният клас не може да бъде инстанциран директно, но има конструктор, извикван при създаване на конкретен наследник. `Task` реализира abstract `code()` от интерфейса. `EmailTask` наследява default описание, докато `SmsTask` го override-ва. `first.info()` извиква `EmailTask.type()`, default `Describable.description()` и `Task.code()`. `second.info()` не се компилира за static type `Describable`, но `((Task) second).info()` работи (резултат `SMS:S:99`, добавен при обратната връзка).
- **Изяснен лапсус след feedback:** първоначално казаното „Интерфейсът има само статични методи или константи“ е било неточно формулирано. Потребителят изрично уточни, че е имал предвид **само статични полета**, че знае за липсата на interface instance fields, и вече беше обяснил правилно `default` instance метода. Не го отчитай като непознаване на видовете интерфейсни методи; методите могат да бъдат abstract/default/static/private, а полетата са implicit `public static final`.
- **Нов езиков детайл:** „`super(...)` е задължително първи ред“ вече не е универсално вярно от Java 25 — JEP 513 Flexible Constructor Bodies позволява определени statements преди constructor invocation. Това не е основен пропуск по наследяването.
- **Дизайн:** правилно разграничени много интерфейси спрямо един superclass и необходимостта от instance поле в abstract class. Добавена е мотивацията договор vs споделено състояние/логика vs composition.
- **Оценки само за Q10 (коригирани след уточнението):** **9.5/10 техническо разбиране, 9/10 техническа комуникация**. Декларираната липса на instance fields и правилното разбиране за default methods са доказани в отговора и потвърдени като замисъл след feedback. Знанието за Java 25 Flexible Constructor Bodies е ново; статус **READY_FOR_RECALL**, не MASTERED.
- **Concept Note:** [Abstract Classes, Interfaces & Default Methods](concepts/11-abstract-classes-interfaces-default-methods.md).
- **Следващо:** нов въпрос само след изрично съгласие, а повторението на **новите** Java 25 особености е отложено по изрично желание, без срок.

---

### 2026-10-12 · Q9 — JAVA-OOP-02: overloading, overriding, downcasting (приключен)

**Условие:** Receiver и аргументите с различни static types: `Animal first = new Dog(); Dog second = new Dog(); Animal secondAsAnimal = second;`. Четири извиквания на `interact(Animal)`/`interact(Dog)` с cast на получателя/аргумента; `second == secondAsAnimal`; последвано от `Dog wrong = (Dog) ordinary` за `ordinary = new Animal()`.

- **Верни всички нормални резултати (6/6):** `Dog -> Animal`, `Dog -> Dog`, `Dog -> Animal`, `Dog -> Dog`, `true`, `BEFORE`. Правилно: кодът се компилира, изпълнението стига до необработен `ClassCastException`, `AFTER` не се извежда.
- **Силна страна:** ученикът сам посочи, че при `first.interact(second)` аргументът `Dog` не е основание компилаторът да намери `interact(Dog)` през статичния тип `Animal` на получателя. Разграничи второ/трето/четвърто извикване според cast на получателя или аргумента и правилно определи равенството на референциите.
- **Терминологична корекция:** при описание на `Animal → Dog` е обърнал „по-тесен“ и „по-широк“. Правилно: `Animal` = superclass, по-общ; `Dog` = subclass, по-специфичен; `Animal → Dog` = downcasting/narrowing reference conversion.
- **Корекция за формулиране:** „интересува го референцията, не обектът“ не е достатъчно прецизно — overload resolution е compile-time според static type на аргументите, а override implementation е runtime според runtime type на получателя.
- **Оценка само за Q9:** **9.5/10 техническо разбиране, 8.5/10 техническа комуникация**. Не е обща оценка за JAVA-OOP-02. Независим follow-up след тези корекции **не е проведен**.
- **Concept Note:** [Inheritance, Overriding & Polymorphism — Q8–Q9](concepts/10-inheritance-overriding-polymorphism.md) (обновена на място, без дублираща тема).
- **Следващо:** при изрично съгласие — нов въпрос/микро-тема. Повторението на терминологията е отложено по желание за по-късен етап, без фиксирана дата.

---

### 2026-10-12 · Q8 — JAVA-OOP-02: inheritance, overriding, overloading, field hiding & cast

**Условие:** `Animal pet = new Dog();` със скрито поле `name`, override на `speak()`, наследен метод `description()`, overload `speak(String)`, `Dog dog = (Dog) pet` и въпрос дали `pet.speak("Loud")` се компилира.

- **Верен резултат:** кодът се компилира и отпечатва `Animal`, `Woof`, `Animal: Woof`, `Dog`, `Loud Woof` — **5/5**.
- **Вярна причинност:** `pet.name` се свързва със статичния тип `Animal`; `speak()` е override-нат в `Dog` и се диспечира динамично; `Animal.description()` комбинира `Animal.name` с динамично извикване на `speak()`. Cast-ването не създава обект. `pet.speak("Loud")` без cast не се компилира, защото в `Animal` липсва този overload.
- **Нужна смислова корекция:** „cast променя поведението на обекта“ — неправилно; той е checked reference conversion, предоставя различен compile-time тип на израза, без промяна на обекта или неговия runtime клас.
- **Нужна терминологична корекция:** „отляво стои поведението“ → отляво е **declared/static type** на променливата, докато `new Dog()` отдясно създава обект с runtime тип `Dog`; „референцията Animal не знае за Dog“ → компилаторът извършва method lookup по static type на израза.
- **Локална оценка само за Q8 от обратната връзка:** **9/10 техническо разбиране, 8.5/10 техническа комуникация**. Това не е окончателна оценка на цялото OOP или Java Core.
- **Concept Note:** [Inheritance, Overriding & Polymorphism](concepts/10-inheritance-overriding-polymorphism.md).
- **Следващо:** изчакай изрично потвърждение за нов въпрос; кратък recall на разликата между cast, compile-time type и runtime object на по-късен етап, без активен срок според желанието на ученика.

---

### 2026-10-12 · Решение за recall и поставен Q8

- Потребителят каза, че все още помни материала от предишните въпроси и иска текущите преговори да бъдат **отложени за по-късен етап**.
- Всички все още непроведени Q1–Q7 recall проверки са маркирани **DEFERRED_BY_USER**, без фиксирана нова дата. Това не е доказателство за успешно recall и не променя оценките.
- Изрично поиска **следващ нов въпрос**. Поставен е Q8 за **JAVA-OOP-02**: `Animal pet = new Dog()`, поле `name`, overridden `speak()`, inherited `description()`, cast към `Dog`, overloading `speak(String)` и статичен срещу runtime тип. **Въпросът е зададен, но отговор още няма.** Не публикувай решението предварително.

---

### 2026-10-12 · Q7 — JAVA-FND-01 (arrays extension): array aliasing, clone и final

**Условие:** `final int[] original = {1, 2, 3}`, `alias = original`, `snapshot = original.clone()`; мутации през alias/snapshot, пренасочване на alias и 5 `println` резултата. Допълнително: какво става при `original = snapshot`.

- **Верни изходи (5/5):** `[10, 2, 3]`, `[1, 20, 30]`, `[1, 20, 30]`, `true`, `false`. Правилно е предвидено, че първоначалният код се компилира.
- **Верни причинни обяснения:** масивите са обекти; `alias = original` копира референцията, `clone()` създава нов `int[]`; първата промяна на `alias[0]` засяга original, последната след `alias = snapshot` — snapshot; повторно присвояване на `final` original е compilation error.
- **Уточнен технически изказ:** не твърдим, че Java референцията е непременно физически адрес. `final` е модификатор на *променливата*, който забранява пренасочването ѝ след инициализация, но не прави масива immutable. Ученика използва „final обект“ и противоречивата формулировка „не може да променяме обекта, но може да подменяме стойностите“; отговорът показва правилната идея за mutation на елементите.
- **Допълнение, обяснено от асистента, не проверено:** `clone()` прави shallow copy: при `StringBuilder[]` се копират референциите към вложените builders; `append()` върху споделен елемент се вижда и през другия масив.
- **Оценка само за Q7:** **9/10** в обратната връзка; правилни резултати и механизмът, две езиково-технически корекции. Няма доказателство за успешно възпроизвеждане след корекцията; статус **TARGETED_REVIEW**, не MASTERED.
- **Бележка:** [Arrays, Clone & Final References](concepts/09-arrays-clone-final-reference.md).
- **Recall:** D+1 2026-10-13, D+7 2026-10-19, D+30 2026-11-11. Следващ въпрос само след изрично съгласие.

---

### 2026-10-10 · Q6 — JAVA-OOP-01: classes, constructors, private/final и encapsulation (завършен)

**Условие:** `Order order = new Order(42L, 5); order.addItems(3);` с грешен конструктор `quantity = quantity;` и mutable поле `int quantity`.

- **Q6.1:** кодът се компилира, изход `42`, `3` — **правилно**. Constructor parameter `quantity` засенчва полето, което остава с default `0`; `addItems(3)` го прави `3`.
- **Q6.2:** правилно разпознато self-assignment на параметъра и необходимостта от `this.quantity = quantity`. Само тази промяна би дала `42`, `8` в оригиналния тест.
- **Q6.3:** `private` описано правилно за ограничаване на директния достъп; добре разграничени `final` primitive value и final reference, при която mutable обект може да се променя. **Уточнения:** getter/setter не са задължителни; instance initializer `{ }` и static initializer `static { }` са различни; instance blank final може да се инициализира в field initializer/instance initializer/constructor, а static final — в static field initializer/static block.
- **Q6.4 първоначално:** посочени проверки при конструиране и setter; пропуснат е addItems. В последващия отговор са разпознати отрицателен аргумент и overflow. Проверка само на отрицателния резултат не е достатъчна за бизнес валиден аргумент.
- **Q6 follow-up и оценка:** правилно разпознати опасностите от отрицателно количество и int overflow; предложен временен резултат, проверка и запис накрая. Уточнено: отрицателен amount може да даде положителен резултат, затова input validation е отделно от object invariant. Безопасна алтернатива е Math.addExact. **9/10 техническо, 8.5/10 комуникация; READY_FOR_RECALL.**
- **Документация:** [OOP Fields, Constructors & Encapsulation](concepts/08-oop-fields-constructors-final-encapsulation.md) — изложени са вече обсъдените механизми; решението на незавършения invariant follow-up **не е публикувано**.
- **Следващо:** Q6 е READY_FOR_RECALL (D+1 2026-10-11; D+7 2026-10-17; D+30 2026-11-09). Следваща микро-тема само с разрешение.

---

### 2026-10-10 · Checkpoint №1 — Java Fundamentals (Q1–Q5)

**Решен интеграционен код:** `process(int count, String status, StringBuilder audit, Integer attempts)`, `Integer` сравнения, `String` identity/equality, `int` overflow, `byte += 1`.

- **Изход — потвърден 7/7:** `3:NEW-SENT:128`; `2:NEW:start|sent:127`; `true`; `true`; `false`; `true`; `-2147483648:-128`.
- **Първоначален лапсус:** в последния ред е написано `-127`, но същият отговор устно е описал резултат `-128`; при поискана корекция изходът е поправен изрично на `-2147483648:-128`.
- **Силни страни:** точно обяснени pass-by-value, `String.concat()` и immutable `String`, `StringBuilder.append()` mutation и локално reassignment, Integer cache и примитивно сравнение, различните механизми на `String ==`/`equals()`, `int` overflow преди widening и `byte` narrowing след промоция до `int`.
- **Критична неточност:** при `Integer attempts++` правилно посочени **unboxing → int+1 → autoboxing**, но погрешно е заключено, че **оригиналният `Integer` обект остава същият, а стойността му се променя**. Корекция: `Integer` е immutable; boxing на `128` дава различен обект от стария `Integer(127)` (може да е кеширан), а локалният параметър се пренасочва. Оригиналният caller аргумент остава `127`.
- **Postfix:** правилно разпознат `attempts++` като post-increment; уточнено е, че стойността на самия postfix израз е старата числова стойност, а променливата се увеличава.
- **Частична оценка само за checkpoint №1:** **8.5/10 техническо разбиране и 8.5/10 техническа комуникация**. Резултатът не е обща оценка на целия Java Fundamentals или доказателство за MASTERED.
- **Трайна бележка:** [Java Fundamentals Integrated Checkpoint](concepts/07-java-fundamentals-integrated-checkpoint.md); тематичната [Autoboxing & Unboxing бележка](concepts/06-autoboxing-unboxing-integer-cache-null.md) е актуализирана с `Integer++`.
- **Recall:** Q5.1 и Q5.2 D+1 са изискуеми на 2026-10-10, но не са отметнати като отделни успешни recall проверки. За новата корекция checkpoint D+1: 2026-10-11; D+7: 2026-10-17; D+30: 2026-11-09.
- **Следващо:** при съгласие първо due recall върху wrapper increment и числовите правила, без да се повтаря същият решен checkpoint като нов тест.

---

### 2026-10-09 · Q5.2 targeted follow-up — wrapper identity, cache и mixed numeric equality

**Зададен код:** `Integer a = 100; Integer b = 100; long c = 100L; System.out.println(a == b); System.out.println(a == c);`

- **Резултати:** верни **2/2**: `true`, `true`.
- **Първи механизъм:** правилно обяснен `Integer == Integer` reference identity при `100` и споделена кеширана референция.
- **Втори механизъм:** първоначално изразена несигурност за boxing/unboxing, но самостоятелно е предположен **правилният** алгоритъм: `Integer a` се unbox-ва до `int`, разширява се до `long`, после се сравнява като `long`. Резултатът `true` е правилен.
- **Остава за затвърждаване:** потребителят смята, че всички стойности, побиращи се в `short`, задължително ползват един и същ `Integer` обект. Уточнен е **гарантираният** диапазон `-128..127` при boxing на константи, а извън него споделянето може да се различава по реализация; не е потвърден с независим нов отговор след корекцията.
- **Разяснено:** при числово `==` сравнение `Integer == int` и `int == Integer` действат симетрично — unboxing, независимо от реда. При `Integer == long` следва widening на `int` до `long`; в други контексти типът на оператора/целевия тип определя boxing/unboxing.
- **Частична оценка на targeted follow-up:** **9/10 техническо разбиране**, **2/2 верни резултата**; няма общ резултат за целия Java Fundamentals.
- **Следващо:** D+1 2026-10-10, D+7 2026-10-16, D+30 2026-11-08 за Q5.2 и Q5.1. Следващ групов checkpoint само при изрично съгласие.

---

### 2026-10-09 · Q5.2 — Autoboxing & Unboxing (първоначален отговор и feedback)

**Зададен код:** `Integer first = 127, second = 127; Integer boxed = 1000; int primitive = 1000; Integer missing = null;`, след което шест `println` извиквания.

- **Кодът се компилира.** Истинският изход е `true`, `true`, `false`, `true`, след което `missing == 0` хвърля `NullPointerException`, а `"END"` не се отпечатва.
- **Правилно от ученика:** компилиране; `first.equals(primitive)` = `false` и автоматично boxing на аргумента; `missing == null` = `true`; unboxing на `null` при `missing == 0` → `NullPointerException` и стоп на потока.
- **Грешка:** `first == second` е посочено като `false`, защото не е отчетено задължителното споделяне на референции при boxing на константни `int` стойности от `-128` до `127`; действителният резултат е `true`.
- **Несигурност:** при `boxed == primitive` ученикът грешно смята, че `Integer → int` изисква изричен cast, и не дава окончателен резултат. Java прави автоматичен unboxing; сравнението на числовите стойности `1000 == 1000` връща `true`.
- **Корекция и терминология:** `Integer == Integer` при две wrapper референции е сравнение на **identity**; `Integer == int` задейства **unboxing** и числово сравнение; `Integer.equals()` е логическо равенство по число; unboxing на `null` хвърля NPE. Кеширане извън гарантирания диапазон е implementation-dependent; не ползвай wrapper `==` за логическо равенство.
- **Частична оценка само за Q5.2 (преди целевата проверка):** **6.5/10 техническо разбиране, 7.5/10 техническа комуникация**. Оценката не е обща за JAVA-FND-05. Не твърди, че пропускът е независимо потвърден след обяснението.
- **Concept Note:** [Autoboxing, Unboxing, Integer Caching & Null Safety](concepts/06-autoboxing-unboxing-integer-cache-null.md).
- **Следващо:** targeted follow-up е проведен на 2026-10-09 (виж секцията по-горе); остава D+1 2026-10-10, D+7 2026-10-16 и D+30 2026-11-08.

---

### 2026-10-09 · Q5 — numeric conversions, overflow, compound assignment (част 1)

**Първоначален код:** `int max = Integer.MAX_VALUE; long a = max + 1; long b = (long) max + 1; byte count = 127; count += 1;`.

- **Правилни резултати (3/3):** компилира се, `-2147483648`, `2147483648`, `-128`.
- **Правилно обяснение:** при `max + 1` има `int` overflow преди присвояване към `long`; при `(long) max + 1` операцията е в `long`. Разпознат е диапазонът `byte` от `-128` до `127`.
- **Уточняващ въпрос 5.1:** `byte x = 127; x += 1; byte y = 127; y = y + 1;`.
- **Отговор:** правилно, че `x += 1` се компилира и `y = y + 1` не се компилира. Частично обяснение: `+=` е свързан с implicit cast, но погрешно е поставено преобразуването **преди** събирането и не е посочено, че `byte + int` дава `int`.
- **Обратна връзка:** разяснени са `binary numeric promotion`, изчисление `127 + 1` като `int 128`, последващо implicit narrowing към `byte -128`; за обикновено `=` е необходим изричен cast на неконстантен `int` израз. Обяснено е, че отрицателният резултат **не е int overflow**, а narrowing conversion.
- **Оценка само за Q5, част 1:** **8/10 техническо разбиране; 8/10 техническа комуникация**. Прогнозата е вярна, но не е имало независимо повторно обяснение на момента на cast след корекцията; затова **TARGETED_REVIEW**, не `MASTERED`.
- **Трайна бележка:** [Numeric Promotions, Overflow & Compound Assignment](concepts/05-numeric-promotions-overflow-compound-assignment.md).
- **Recall:** D+1 2026-10-10, D+7 2026-10-16, D+30 2026-11-08. Q5 част 2, autoboxing/unboxing, **още не е тествана**.

---

### 2026-10-09 · D+1 recall — Q1–Q4

- **Код:** верни 4/4 реда `Java`, `A!`, `false`, `true`; сценарият комбинира `String`, `StringBuilder`, `new String()` и извикване на метод.
- **Вярно обяснено:** Java pass-by-value, разликата mutation/reassignment и защо `append()` влияе на оригиналния mutable обект, но локалният `builder = new ...` не променя caller променливата.
- **Терминологични уточнения:** подава се копие на *стойността на референцията* (не pass-by-reference); референцията не трябва да се нарича гарантиран физически адрес; `String` immutable не значи, че всяко API извикване прави нов екземпляр (пример: `concat("")`).
- **Съществен пропуск в първия отговор (поправен):** заявено е, че наследеният от `Object` `equals()` по подразбиране сравнява полета/стойности. Показан е пример с два отделни `Person` обекта с еднакви полета, без override. При последващ независим отговор потребителят правилно обясни: `Object.equals()` сравнява identity, докато override-натият `String.equals()` сравнява последователността от символи (10/10 за тази кратка проверка).
- **Оценка на първия recall отговор:** **8.5/10 техническо разбиране, 8/10 комуникация**. Уточняващият въпрос е **10/10** и затваря пропуска; **статус D+1: COMPLETED**. Това не е обща оценка на Java Fundamentals.
- **Следващо:** Q5 още не е зададен; започва само след изрично съгласие. D+7: 2026-10-15; D+30: 2026-11-07.

---

### 2026-10-08 · Java Fundamentals · Session 1 (Q1–Q4)

| Въпрос | Проверка | Реално показан резултат | Корекция |
| --- | --- | --- | --- |
| Q1 | `int` copy; `StringBuilder` aliasing | `10`, `20`, `AB`, `AB`, правилни; асистентът даде 90% ориентировъчно само за Q1 | Примитиви, референции и обекти всички имат представяне в паметта |
| Q2 | `Person` argument, field mutation, reassignment, primitive parameter | `Maria`, `20`, правилни; асистентът даде 9.5/10 за Q2 | „Копира се стойността на референцията“, не „подава се самата променлива“ |
| Q3 | `String.concat` vs `StringBuilder.append` | `Hello`, `Hello World`, правилни; 8/10 техн. + 8.5/10 комуникация за Q3 | `append()` изменя съществуващия builder и връща него; GC не е автоматична гаранция |
| Q3b / 3.1 | `append()` identity, reassignment, `==` | 9/10 разбиране само за Q3b; вярно разсъждение за двата обекта и `false`; разменени `C` и `ABD` в отговора | Следи реда на `println`: `false`, `C`, `ABD` |
| Q4 | reference equality, String Pool, `equals()`, `null`, exception flow | 8.5/10 разбиране, 8.5/10 изказ след корекция; NPE прекъсва преди `END`; поправена е грешката за `a == b` | Не бъркай интерниран литерал и `new String`; `Object.equals()` vs `String.equals()`; `Objects.equals()` |

- **Силна страна:** добър mental model за независима примитивна променлива, копирана референция и локално пренасочване.
- **За затвърждаване:** на D+1 поведението на `new String`/String Pool е възпроизведено правилно; `Object.equals()` default identity е успешно потвърдено с независим follow-up; остава преговор на D+7/D+30.
- **Материали:** [Concept Notes библиотеката](concepts/README.md) вече има четири самостоятелни Markdown обяснения с аудио текст; Q4 материалът е за вече дадения и обсъден отговор.
- **Recall:** D+1 е приключен успешно на 2026-10-09 след корекция и независимо потвърждение на `Object.equals()`; D+7/D+30 предстоят. Не присвоявай `MASTERED` преди следващите проверки.
- **Следваща стъпка:** D+1 е завършен; Q5 (`numeric conversions`, `overflow`, `autoboxing`) само след изрично съгласие.

---

**Правило за поддръжка:** след всяка действително проведена сесия актуализирай overview, детайлната таблица, review queue и дневника. Оценките са само за реални отговори. Concept Notes се поддържат отделно като трайна техническа документация.
