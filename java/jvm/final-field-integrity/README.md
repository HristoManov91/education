# P1 · JEP 500 — Prepare to Make Final Mean Final (Java 25 vs 26)

> **Java 25/26 single-file runnable CLI + negative tests, без Maven/JUnit зависимост и без preview syntax.** Свързано с [Deserialization and Encapsulation](../../language/data-oriented-programming/DESERIALIZATION-AND-ENCAPSULATION.md). Източник: [Richard Groß, Destructuring Is the Future of Java's Encapsulation](https://www.youtube.com/watch?v=wcghmWdu-HY), ~05–10 мин, SRT. **Версионни правила са сверени с официалния [JEP500](https://openjdk.org/jeps/500), а не с условна прогноза на лектора.**

## 1. Реалният казус

Имаме `private final int ageLimit=21` с constructor invariant. Legacy framework използва `Field#setAccessible(true)` и `Field#setInt(obj,17)`. Това не минава през constructor и нарушава invariant. В Java 25 за **обикновено instance final поле** такава мутация може да успее; при record и други категории полета има допълнителни ограничения. JDK26 въвежда warnings и opt-in controls за reflective final-field mutation, за да се подготвим за бъдещи по-строги defaults.

## 2. Code → proof

| Елемент | Изпълним файл | Проверка |
| --- | --- | --- |
| `final` example и reflective modification | [FinalFieldMutationDemo.java](FinalFieldMutationDemo.java) | `Field#getInt` доказва, че value става 17, или остава 21 при deny |
| Cross-JDK runs и negative diagnostics | [run-lab.sh](run-lab.sh) | Java25; Java26 default, debug, deny, allowlisted caller |
| Path-limited CI с две JDK | [amber-final-field-integrity.yml](../../../.github/workflows/amber-final-field-integrity.yml) | Отделни Java25/26 jobs, *не* Maven global default |
| Record field винаги е специален | [RecordAdult Java25 test](../../language/data-oriented-programming/src/test/java/bg/hristomanov/education/dop/amber/DeserializationBoundaryLabTest.java) | `IllegalAccessException` при опит за final component mutation |

От root с активен **JDK 25 или JDK 26**:

```bash
bash java/jvm/final-field-integrity/run-lab.sh
```

Скриптът компилира демото с активния `javac` и създава само `target/final-field-integrity` логове. За Java 26 валидира:

| Параметър | Очаквано |
| --- | --- |
| default (без flags) | `MUTATION_ALLOWED` + `WARNING: Final field...` |
| `--illegal-final-field-mutation=debug` | `MUTATION_ALLOWED` + warning и stack trace |
| `--illegal-final-field-mutation=deny` | `MUTATION_DENIED` с `IllegalAccessException` |
| `--enable-final-field-mutation=ALL-UNNAMED --illegal-final-field-mutation=deny` | `MUTATION_ALLOWED`, защото caller-ът е изрично enable-нат |
| JDK25 без flags | `MUTATION_ALLOWED`; JEP500 default warnings все още няма |

**Важно:** `--enable-final-field-mutation` е permission *за module-а, който извършва write*, не „отвори всички private полета“. За deep reflection също трябва declaring package да е **open to caller**; `--add-opens` и `--enable-final-field-mutation` не са едно и също. `ALL-UNNAMED` е подходящ **само за учебния classpath example**, за production може да е прекалено широка привилегия. Пази ownership и срок на изключението.

## 3. Production migration / Decision contract

| Въпрос | Отговор |
| --- | --- |
| Какво решава? | Открива framework, който нарушава field invariant след construction |
| Кога? | Java 25→26 upgrade, serialization/reflection-heavy приложения, тестове на Hibernate/Jackson/bytecode tooling |
| Кога НЕ? | Не добавяй глобален permission flag „за всеки случай“; не прави цялото приложение mutable |
| Печалби | По-силен object integrity, migration signal, по-малко неявни reflection hacks |
| Загуби | Някои стари serializers/builders/frameworks изискват upgrade или явни factories/constructors |
| По-просто | Public creator/factory, record DTO, ръчна reconstruction вместо private final writes |
| Proof | [CLI](run-lab.sh) в **два действителни JDK**, плюс [Java25 record tests](../../language/data-oriented-programming/DESERIALIZATION-AND-ENCAPSULATION.md) |

**Code review:** кой module прави `Field#set`? Защо mutates final? Има ли поддържан constructor-based path? `opens` нужно ли е за read или за write? Може ли да се подмени library? Има ли предвидим rollback при `deny`?

**Изход:** не изтриваме нужни framework-и и не превръщаме приложението в JPMS проект само заради JEP500. Първо диагностицираме, после мигрираме към валидиращи конструктори, а permission flags остават документирани временни изключения.

## Официални източници

- [JEP 500 — Prepare to Make Final Mean Final](https://openjdk.org/jeps/500).
- [Oracle Java 26 Migration: Preparing for final Field Mutation Restrictions](https://docs.oracle.com/en/java/javase/26/migrate/preparing-final-field-mutation-restrictions.html).
- [Oracle Java 26 Mutation Methods](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/reflect/doc-files/MutationMethods.html).
- [JPMS Encapsulation — existing Education lab](../jpms-encapsulation/README.md).
