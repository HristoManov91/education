# JAVA-EXC-02 — Try-with-resources, AutoCloseable, close order и suppressed exceptions

**Термини:** `AutoCloseable`, `close()`, try-with-resources (TWR), automatic resource management, reverse declaration order, LIFO, primary exception, suppressed exception, `Throwable.getSuppressed()`, exception propagation.

## Защо това е важно

Ресурс като файл, поток, JDBC statement или connection трябва да бъде затворен надеждно и когато основната операция се провали. Самото затваряне също може да хвърли изключение. Правилният mental model е важен, за да не изгубим първоначалната грешка и за да знаем кои ресурси реално се освобождават.

## Mental model за 30 секунди

- Ресурсите в заглавката на `try (...)` трябва да бъдат `AutoCloseable` (или негов подтип). След напускане на тялото Java автоматично извиква `close()` върху **успешно инициализираните** ресурси.
- Редът на затваряне е **обратният на декларирането**. При `first`, после `second` затварянето е `second`, после `first`. Това е LIFO-подобна логика на вложени `try/finally` блокове и често е естествена за зависими ресурси.
- Изключение в тялото на `try` става **primary exception**. Ако `close()` също хвърли изключение, то се прикрепя като **suppressed exception** към primary и не го заменя.
- Ако тялото завърши нормално, но първият изпълнен `close()` хвърли изключение, **това изключение става primary**. Следващи грешки от затваряне стават suppressed.
- `getSuppressed()` връща **`Throwable[]`**, не `Collection<Throwable>`. Това са потиснатите изключения на **конкретния** `Throwable`, не глобален регистър на грешките.
- **Cause** (`getCause()`) и **suppressed exceptions** (`getSuppressed()`) са различни отношения между изключения: wrapping запазва причината, а try-with-resources запазва и допълнителните грешки при cleanup.

## Изпълним пример — Q13, 2026-10-13

```java
import java.io.IOException;

public class ResourceTest {

    static final class Connection implements AutoCloseable {
        private final String name;

        Connection(String name) {
            this.name = name;
            System.out.println("OPEN " + name);
        }

        @Override
        public void close() throws IOException {
            System.out.println("CLOSE " + name);
            throw new IOException("close-" + name);
        }
    }

    public static void main(String[] args) {
        try (
            Connection first = new Connection("A");
            Connection second = new Connection("B")
        ) {
            System.out.println("WORK");
            throw new IOException("operation");
        } catch (IOException e) {
            System.out.println("CAUGHT " + e.getMessage());

            for (Throwable suppressed : e.getSuppressed()) {
                System.out.println(
                    "SUPPRESSED " + suppressed.getMessage()
                );
            }
        }

        System.out.println("END");
    }
}
```

**Изход:**

```text
OPEN A
OPEN B
WORK
CLOSE B
CLOSE A
CAUGHT operation
SUPPRESSED close-B
SUPPRESSED close-A
END
```

## Причина и последица — проследяване стъпка по стъпка

1. Успешно се инициализират `first` (`OPEN A`) и `second` (`OPEN B`).
2. Изпълнява се тялото (`WORK`) и то хвърля `IOException("operation")`. Това е **primary exception**.
3. Напускането на `try` активира затварянето **преди** изпълнението на `catch`.
4. `second.close()` отпечатва `CLOSE B` и хвърля `IOException("close-B")`. Изключението се прикрепя като suppressed към `operation`.
5. Въпреки неуспешното затваряне на `second`, се извиква `first.close()`; той отпечатва `CLOSE A`, а `IOException("close-A")` също се прикрепя към същия primary exception.
6. `catch (IOException e)` получава **оригиналното** `operation`. `e.getSuppressed()` връща масив с `close-B` и `close-A` в реда на добавяне.
7. `catch` завършва нормално; печата се `END`.

**Важно:** че и двата `close()` метода се извикват е вярно **в точно този код**, където и двата ресурса са инициализирани успешно. Не означава, че `close()` ще се извика за ресурс, чиято инициализация е неуспешна.

## Вариант: тялото не хвърля изключение

Премахваме само `throw new IOException("operation");`. Тогава след `WORK`:

```text
CLOSE B
CLOSE A
CAUGHT close-B
SUPPRESSED close-A
END
```

Няма грешка от операцията. Първото хвърлено изключение е от `second.close()` и то става primary. Грешката от `first.close()` се прикрепя като suppressed.

## Практически важни детайли

- `close()` се извиква и при нормално изпълнение, и при излизане чрез `return` или изключение (в стандартния поток на управление).
- Последният деклариран ресурс се затваря първи; това може да е важно, когато един ресурс зависи от друг.
- `AutoCloseable.close()` допуска checked exceptions (общо `Exception`). Конкретният override може да декларира по-тесен тип, както `IOException` в примера.
- В реални системи запази original stack trace и suppressed exceptions при диагностика. Не заменяй първоначалната грешка единствено с грешката от cleanup.
- За `getSuppressed()` използвай точния тип: `Throwable[] suppressed = e.getSuppressed();`.

## Професионално обяснение — интервю / колега

> Java `try-with-resources` управлява ресурсите, които имплементират `AutoCloseable`. След приключване на тялото Java автоматично затваря успешно инициализираните ресурси в обратен ред на деклариране. Ако тялото е хвърлило изключение, то остава основно, а грешките от `close()` се прикрепят като suppressed чрез механизма на exception suppression. Ако тялото приключи без изключение, първата грешка при затваряне става основна, а останалите — suppressed. Връщаме ги чрез `getSuppressed()`, което има тип `Throwable[]`.

## Текст за слушане

Представи си, че отваряме два ресурса: A, после B. Програмата отпечатва OPEN A и OPEN B, влиза в основната работа и отпечатва WORK. След това възниква IOException с текст operation. Тялото на try приключва по изключение, но преди catch Java трябва да затвори успешно създадените ресурси. Затварянето става в обратен ред: първо B, после A. При затварянето на B се отпечатва CLOSE B и възниква грешка close-B. Java не заменя първоначалната operation с тази нова грешка, а я добавя като потиснато изключение. Същото се случва при затварянето на A. След това catch вижда operation като основно изключение и може да извлече потиснатите грешки от масива, който връща getSuppressed. Затова програмата отпечатва последователно CAUGHT operation, SUPPRESSED close-B и SUPPRESSED close-A. Ако махнем хвърлянето на operation, първото изключение възниква при затваряне на B и то става основно; close-A остава потиснато. Важният извод е, че try-with-resources пази първопричината, без да загуби сведенията за проблеми при затварянето.

## Recall без подсказване

1. Ако `second` не успее да се инициализира, кое от вече създадените ресурси може да бъде затворено и как ще се третира грешка при това затваряне?
2. Каква е разликата между `getCause()` и `getSuppressed()` и какъв точно тип връща вторият метод?

## Официални източници

- [Java Language Specification SE 25, §14.20.3 — try-with-resources](https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.20.3)
- [Java SE 25 API — AutoCloseable](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/AutoCloseable.html)
- [Java SE 25 API — Throwable](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Throwable.html)
