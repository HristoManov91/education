# P1 — Record reconstruction: валидни състояния и атомарно обновяване на компонентите

> **Основа:** [Gavin Bierman, What’s happening with Project Amber?, Devoxx Belgium 2026](https://www.youtube.com/watch?v=Go76-9Q51wU), частта за record reconstruction (~36–41 мин) от предоставения автоматичен SRT. **Кодът за TaskBatch е собствен Java 25 учебен пример**, не код на лектора. [Brian Goetz: Updates to Derived Record Creation (09.03.2026)](https://www.mail-archive.com/amber-spec-experts%40openjdk.org/msg00877.html) описва развиваща се идея, **не финален JDK 25 синтаксис**.

## 1. Реален backend проблем

Система държи пакет задачи с идентификатори и приоритети. Два списъка трябва да имат **еднаква дължина**, всеки `taskId` да е уникален, а priority да е 1–10. Имаме валиден snapshot с 2 задачи и трябва да го сменим с 3.

Наивно изглежда разумно да създадем нов snapshot чрез две последователни `withX()` операции. Но ако всяка `withX()` минава през canonical constructor, първата промяна създава **невалидно междинно състояние**. Ако вместо това мутираме field-овете, губим record invariants и immutable snapshot contract.

```text
BEFORE: ids=[A,B], priorities=[3,4]                  valid
BAD 1: ids=[A,B,C], priorities=[3,4]                invalid
BAD 2: mutate original lists                        aliasing, unsafe invariants
GOOD:  new Record(ids=[A,B,C], priorities=[3,4,5])  constructor validates final state
BEST:  [TaskPriority(A,3),TaskPriority(B,4),TaskPriority(C,5)]
       no independent list lengths to get out of sync
```

## 2. README → source → тестове

| Проблем / договор | Java 25 код | JUnit 5 proof |
| --- | --- | --- |
| Неразрешена промяна на единия списък срещу успешна реконструкция **на двата компонента наведнъж** | [RecordReconstructionInvariantLab.java](src/main/java/bg/hristomanov/education/dop/amber/RecordReconstructionInvariantLab.java) — `ParallelTaskBatch.replaceOnlyTaskIdsForContrast` vs `replaceTogether` | [RecordReconstructionInvariantLabTest.java](src/test/java/bg/hristomanov/education/dop/amber/RecordReconstructionInvariantLabTest.java) — първи тест |
| `List.copyOf`, immutable snapshot, duplicate/blank IDs, null и invalid priority | същият canonical record constructor | negative тестове за всеки случай |
| По-добър data model — `record TaskPriority(id, priority)` | `ItemizedTaskBatch`, `normalize`, `changePriority` | structural pair invariants, source object unchanged, unknown ID rejection |
| Сходен реален модел, който вече имаме | [PaymentSnapshot.withAmount](src/main/java/bg/hristomanov/education/dop/valhalla/PaymentSnapshot.java) | [ValhallaReadinessTest.java](src/test/java/bg/hristomanov/education/dop/valhalla/ValhallaReadinessTest.java) |

Изпълни от корена:

```bash
mvn -pl java/language/data-oriented-programming \
  -Dtest=RecordReconstructionInvariantLabTest test
```

### Реалният Java 25 пример

```java
ParallelTaskBatch old = new ParallelTaskBatch(
        List.of("A", "B"), List.of(3, 4));

// BAD: не можем временно да добавим task без priority.
// old.replaceOnlyTaskIdsForContrast(List.of("A", "B", "C")); // throws

// GOOD: canonical constructor получава едновременно финалните компоненти.
ParallelTaskBatch revised = old.replaceTogether(
        List.of("A", "B", "C"), List.of(3, 4, 5));

// BETTER: моделирай самата връзка като тип.
ItemizedTaskBatch normalized = RecordReconstructionInvariantLab.normalize(revised);
ItemizedTaskBatch reprioritized = normalized.changePriority("C", 8);
```

**Защо това е по-силно от два независими `with` метода?** Не създаваме междинен обект с нарушен invariant. Освен това `List.copyOf` прави unmodifiable snapshot на списъка. Това е **shallow**, не deep immutability, но е достатъчно, когато елементите също са immutable `String`, `Integer` и `TaskPriority`.

**И все пак:** `ParallelTaskBatch` *допуска да подадеш* несъвпадащи списъци, преди конструкторът да ги отхвърли. `ItemizedTaskBatch(List<TaskPriority>)` изобщо няма независимо измерение на дължината — прави определен вид невалидно състояние **структурно непредставимо**. Все още проверяваме уникалност, ID и priority валидност.

## 3. Какво НЕ обещава предложението за record reconstruction

Gavin описва бъдеща синтактична форма за създаване на нов record, като се променят няколко компонента. Ценността е удобна реконструкция **със запазен constructor contract**, а не заобикаляне на validation. Към 11.11.2026 JEP 468 и дискусиите за `with`/`record.new` са evolving design. **Не компилираме** предложения синтаксис с Java 25 и не го представяме като стандартен feature. Java25 кодът е ръчен, затова е преносим.

Тази „атомарност“ е **за създаването на един immutable обект**, не е database transaction, concurrency control, optimistic locking или атомарна замяна на snapshot в shared mutable cache. Последните изискват собствена координация.

## 4. Decision contract

| Подход | Кога | Кога НЕ | Какво печелим | Цена | По-проста алтернатива |
| --- | --- | --- | --- | --- | --- |
| `replaceTogether` | няколко взаимозависими компонента в съществуващ record | не трябва да се променя бизнес aggregate директно | constructor invariants, предсказуемо ново състояние | explicit boilerplate и copy | public constructor/factory с всички параметри |
| `List<TaskPriority>` | компоненти логически образуват единичен домейн факт | силни технически причини за parallel arrays и measured performance | невъзможен count mismatch | повече малки value objects, schema migration | immutable list от малък `record` |
| Предложен Amber reconstruction syntax | след final release + migration review | production Java25, нефинализиран API | по-малко redundant код | evolving syntax, maintenance risk | ръчни constructors |

**Край на задачата:** знаем, че удобната промяна на field-овете не бива да нарушава record invariants. Избираме модел, който не създава незаконни междинни стойности, доказваме negative paths с JUnit и не чакаме непубликуван Java syntax.

## Оригинални източници

- [Gavin Bierman — What’s happening with Project Amber? (Devoxx 2026)](https://www.youtube.com/watch?v=Go76-9Q51wU), provided SRT.
- [JEP 468 — Derived Record Creation (proposal)](https://openjdk.org/jeps/468).
- [Brian Goetz — Updates to Derived Record Creation, March 2026](https://www.mail-archive.com/amber-spec-experts%40openjdk.org/msg00877.html).
- [Project Amber — Beyond Records](https://www.mail-archive.com/amber-spec-experts%40openjdk.org/msg00829.html).
- [JEP 395 — Records](https://openjdk.org/jeps/395).
