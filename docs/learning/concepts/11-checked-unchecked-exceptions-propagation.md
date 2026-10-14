# JAVA-EXC-01 — Checked/unchecked exceptions, propagation и wrapping

**Термини:** checked exception, unchecked exception, `throw`, `throws`, exception propagation, stack unwinding, exception wrapping, cause, catch-or-declare.

## Реалният проблем

Когато по-дълбок метод не успее да изпълни операция (например четене на файл), трябва да разбираме кой метод е отговорен за реакцията, кои изключения компилаторът изисква да бъдат обработени или декларирани и как да предадем полезната информация за грешката към горните слоеве. Грешното разбиране води до компилационни грешки, загубени root causes и неочаквано пропуснат код.

## Mental model за 30 секунди

- `IOException` е **checked exception**: извикващият код трябва да го **прихване** (в подходящ `catch`) **или да го декларира** чрез `throws`. Декларирането не го обработва; само допуска по-нататъшно разпространение.
- `IllegalStateException` наследява `RuntimeException` и е **unchecked exception**: компилаторът не изисква `catch` или `throws`, но можем да го прихванем или декларираме доброволно.
- По-общо `RuntimeException` и `Error`, заедно с техните наследници, са unchecked. Останалите типове, които наследяват `Throwable`, са checked.
- `throw` е **statement**, което хвърля конкретно изключение; `throws` стои в декларацията на метод и посочва типове изключения, които може да се разпространят извън него.
- Хвърлянето прекратява нормалния поток на изпълнение; управлението преминава към подходящ `catch` по веригата от извиквания (*exception propagation / stack unwinding*).
- При `new IllegalStateException("Processing failed", e)` оригиналният `IOException` се запазва като **cause**. Това е *exception wrapping*, а не изгубване на първоначалната грешка.

## Изпълним пример — диагностичен Q12, 2026-10-13

```java
import java.io.IOException;

public class ExceptionTest {

    static void readFile() throws IOException {
        System.out.println("A");
        throw new IOException("File not found");
    }

    static void process() {
        try {
            readFile();
            System.out.println("B");
        } catch (IOException e) {
            System.out.println("C");
            throw new IllegalStateException("Processing failed", e);
        }
    }

    public static void main(String[] args) {
        try {
            process();
            System.out.println("D");
        } catch (IllegalStateException e) {
            System.out.println("E");
            System.out.println(e.getCause().getClass().getSimpleName());
        }

        System.out.println("F");
    }
}
```

**Изход:**

```text
A
C
E
IOException
F
```

### Причина и последица

1. `main()` влиза в `try` и извиква `process()`, който на свой ред извиква `readFile()`.
2. `readFile()` отпечатва `A`, след което хвърля `IOException`. Методът не продължава по нормалния си път.
3. Изпълнението се връща по веригата до подходящия `catch (IOException e)` в `process()`. Редът с `B` се пропуска.
4. `process()` отпечатва `C` и хвърля **нов** `IllegalStateException` с оригиналното изключение като причина. Затова `process()` също не завършва нормално.
5. `main()` пропуска `D` и прихваща `IllegalStateException`; отпечатва `E`. `getCause()` връща запазения `IOException`, а `getClass().getSimpleName()` — `IOException`.
6. `catch` в `main()` приключва нормално. След него се изпълнява и `System.out.println("F")`.

В конкретния пример `getCause()` не е `null`, защото конструкторът на `IllegalStateException` е получил причина. В общия случай извикване на `getCause().getClass()` **без проверка** може да предизвика `NullPointerException`.

## Какво става без catch в process()

```java
static void process() {
    readFile(); // compilation error: unreported exception IOException
}
```

Единият коректен вариант е `process()` да прихване `IOException`; другият е да го декларира:

```java
static void process() throws IOException {
    readFile();
}
```

Във втория вариант **и извикващият метод** трябва да изпълни съответното изискване: да прихване `IOException` или да го декларира. Старият `catch (IllegalStateException e)` в `main()` **не е достатъчен**, защото `IOException` не е негов наследник. Дори `main()` може да декларира `throws IOException`; това позволява компилация, **но не означава**, че изключението непременно ще бъде прихванато — при необработено изключение потокът приключва с uncaught exception.

## Професионален отговор — за интервю/колега

> Java разделя типовете изключения на checked и unchecked. При checked exception, например `IOException`, компилаторът проверява правилото *catch or declare*: извикващият метод трябва да го прихване или да декларира съвместим тип в `throws`. При unchecked exception, като `IllegalStateException`, това не е задължително. `throw` хвърля конкретно изключение, докато `throws` е част от декларацията на метод. Когато изключение бъде хвърлено, нормалният поток се прекъсва и се търси подходящ обработващ блок по веригата от извиквания. Ако преобразуваме нискониво изключение в по-смислено за текущия слой, е полезно да запазим оригиналната причина чрез exception chaining.

**Инженерен нюанс:** не обвивай всяко checked изключение механично в `RuntimeException`. Прецени дали имаш смислено обработване, дали API контрактът трябва да експонира грешката и как да запазиш контекста и оригиналния cause.

## Текст за слушане

Представи си три метода: main извиква process, а process извиква readFile. Последният отпечатва буквата A и хвърля проверявано изключение IOException. Това означава, че изпълнението не продължава към следващия нормален ред в process. Вместо това Java търси подходящ catch. В process има такъв catch, който отпечатва C и хвърля ново, непроверявано изключение IllegalStateException. Оригиналното изключение не се губи, защото е подадено като причина на новото. Така в main се активира catch за IllegalStateException, отпечатва се E, след това се прочита класът на оригиналната причина: IOException. След края на catch изпълнението продължава и се отпечатва F. Никога не се отпечатват B и D, защото те са след извиквания, които не завършват нормално. Важно е да помним, че checked не означава задължително да обработим грешката точно тук. Можем и да я декларираме чрез throws и така да я предадем на извикващия код. Unchecked означава, че компилаторът не налага правилото catch or declare, а не че грешката е неуловима.

## Recall без подсказване

1. Ако `process()` декларира `throws IOException`, но в `main()` остава само `catch (IllegalStateException e)`, какво ще стане при компилация и защо?
2. Каква е разликата между това да хвърлим `new IllegalStateException("Failed")` и `new IllegalStateException("Failed", original)`, когато диагностиката зависи от първоначалната причина?

## Официални източници

- [Java Language Specification SE 25, Chapter 11 — Exceptions](https://docs.oracle.com/javase/specs/jls/se25/html/jls-11.html)
- [Java SE 25 API — Throwable](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Throwable.html)
- [Java SE 25 API — RuntimeException](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/RuntimeException.html)
