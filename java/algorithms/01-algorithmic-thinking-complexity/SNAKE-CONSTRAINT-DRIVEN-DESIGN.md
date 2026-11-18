# P2 — Snake in 10 Lines: constraint-driven design, не code golf

> **Източник:** [Guus de Wit — Snake in 10 Lines: Learning More by Coding Less](https://www.youtube.com/watch?v=LD2tKMpToYg), Devoxx 2026, предоставен auto-generated SRT (~48:28). **Лабораторията е наша собствена Java 25 адаптация**, не копие на неговия Kotlin/Swing код. Разширява [01 Algorithmic Thinking & Complexity](README.md), без нов Maven модул.

## 1. Реален проблем: какво всъщност е нужно?

В лекцията (03:51–05:20) Guus свежда Snake до *“Snake moves, steers, eats, grows, and dies.”* От това извежда game state, логическа стъпка, user input, правило за храна и край на играта. Това е полезен инструмент за **изясняване на минималната спецификация** и в backend: първо описваме външното поведение, после имплементацията.

**Наш explicit contract:**
- Правоъгълна дъска (width × height); позиции (x,y); поредица head → tail.
- На всеки tick змията се движи с една клетка; не се разрешава непосредствен обратен завой на 180° за дължина >1.
- Без ядене: добавяме нова глава, махаме опашка. При храна: добавяме глава, **запазваме опашка**, score +1, нова свободна храна.
- Стена или тялото (освен опашната клетка, която се освобождава) → LOST.
- Пълна дъска → WON и **няма** нов избор на храна; терминалните стъпки не променят нищо.
- Нашият модел **НЕ** позволява прехвърляне в следващия ред (wraparound). Лекторът обсъжда този ефект като оставен edge case, а тук изрично го забраняваме.

Това са **допълнителни учебни гаранции**. Не приписваме тези конкретни JUnit правила на оригиналната 10-line игра.

## 2. Картата от лекцията към кода

| Оригинален SRT | Инженерна идея | Нашият пример |
| --- | --- | --- |
| 01:50–03:00 | 10 функционални реда, max 130 символа на ред, без външни библиотеки | Code golf е **доброволна финална задача**, не стандарт за production |
| 03:51–05:20 | Шестте думи и минималният scope | Изрични state и transition invariants |
| 14:10–14:50 | Learn by doing | Java 25 CLI + JUnit, без GUI |
| 15:00–20:15 | Не движим всеки сегмент: **нова глава, махаме опашка** | `Deque` |
| 22:20–25:55 | 2D coordinates → 1D index | `index = y * width + x`; x/y bounds check преди encoding |
| 25:49–26:40 | Constraints lead to creativity | По-добър алгоритмичен модел, не просто по-кратки имена |
| 36:00–42:10 | Reverse, row wrap, static food, full-board kill screen | Негативни сценарии, bounded food placement и explicit WON |
| 42:15–47:00 | Conciseness brings understanding | Отделен game engine, renderer и independent reference oracle |

**Лекторът започва с по-дълга Kotlin игра**, използва Swing за UI, демонстрира вариант на Java `Jake` в Q&A (47:35–48:16). Нашият scope не включва Swing или клавиатурен event loop, защото там няма нова алгоритмична поука.

## 3. BAD и GOOD — какво променяме

**BAD 1 — местим целия body:** при всеки tick копираме/пресмятаме всички L сегмента, O(L). Това е коректен прост начин и **запазваме го като тестов oracle**.

**BAD 2 — безкраен random retry за храна:**

~~~java
while (occupied.contains(randomCell())) {
    // При напълно заета дъска няма решение.
}
~~~

Лекторът показва точно този failure mode след като запълни дъската (около 41–42 мин). По-важна от редовете е гаранцията за приключване.

**GOOD — компактно състояние, ясни операции:**

~~~text
W=4, H=3
x=0  1  2  3
y=0 0  1  2  3
y=1 4  5  6  7      head=(2,1) => index=6
y=2 8  9 10 11

body (head first)    6 → 5 → 4
move RIGHT           7 → 6 → 5    (added 7, removed 4)
~~~

[`SnakeGame.java`](src/main/java/bg/hristomanov/education/algorithms/complexity/snake/SnakeGame.java) използва `ArrayDeque<Integer>` за head/tail и `HashSet<Integer>` за бърза проверка на заетите клетки.

- Обикновен tick: expected O(1) операции по Deque/HashSet; hashing worst-case не е задължително O(1).
- Tick с ядене: новата храна се избира с **bounded scan O(W×H)** на свободните клетки, вместо случайни повторения до успех.
- Snapshot: O(L), защото създаваме independent immutable list за UI/тестове.
- Памет на engine: O(L) за body/occupied (без ASCII renderer, който отделно използва board grid).

**Тънкост:** движение в **точно опашната** клетка е законно при движение *без* храна (опашката се освобождава). Навлизането в друг зает сегмент е загуба. Това е лесен случай за пропускане при пренасяне на алгоритъм от 2D към 1D.

## 4. README → код → независима проверка

| Какво упражняваме | Изпълним Java код | Proof |
| --- | --- | --- |
| State, 1D индекси, head/tail, score, terminal state | [SnakeGame.java](src/main/java/bg/hristomanov/education/algorithms/complexity/snake/SnakeGame.java) | [SnakeGameTest.java](src/test/java/bg/hristomanov/education/algorithms/complexity/snake/SnakeGameTest.java) |
| Reverse, wall/no row wrap, self collision | `advance(Direction)` | Негативни JUnit тестове за всеки сценарий |
| Tail-vacating, growth, пълна дъска и незацикляща храна | `chooseFood()` / `Status.WON` | JUnit near-full и already-full тестове |
| Без GUI, timer или exit code | [SnakeTextDemo.java](src/main/java/bg/hristomanov/education/algorithms/complexity/snake/SnakeTextDemo.java) | ASCII `@` head, `o` body, `*` food, `.` empty |
| Същото поведение в друга реализация | Изолиран тестов 2D oracle, който копира целия body | [SnakeReferenceParityTest.java](src/test/java/bg/hristomanov/education/algorithms/complexity/snake/SnakeReferenceParityTest.java), 300 seeded scenarios |

Паритетният тест сравнява **всеки** `Tick` и целия `View` между оптимизирания модел и независим 2D List reference. Това е ограничено, детерминистично сравнително покритие — не математическо доказателство и не JMH benchmark.

`SnakeGame.fromState(...)` валидира подготвени сценарии (граници, уникалност и съседство на сегментите, посока от neck към head, food not overlapping). `newGame(...)` създава стартова 3-клетъчна змия. `View` е immutable, самият `SnakeGame` има един собственик и **не е thread-safe**.

### Java 25 — изпълнение в съществуващия Maven модул

От корена на Education:

~~~bash
mvn -pl java/algorithms/01-algorithmic-thinking-complexity -am test
java -cp java/algorithms/01-algorithmic-thinking-complexity/target/classes \
  bg.hristomanov.education.algorithms.complexity.snake.SnakeTextDemo
~~~

Примерен сценарий в собствен код:

~~~java
SnakeGame game = SnakeGame.newGame(7, 5, new java.util.Random(2026));
SnakeGame.Tick tick = game.advance(SnakeGame.Direction.RIGHT);
SnakeGame.View snapshot = game.view();
~~~

Не се включват Redis, Spring context, Kubernetes или тежки integration tests **само за този модул**. Общият CI вече го компилира през стандартния reactor.

## 5. Кога, кога не, печалби и загуби

| Решение | Проблем | Кога да / кога НЕ | Печалба | Компромис и по-прост вариант | Доказателство |
| --- | --- | --- | --- | --- | --- |
| **Шест думи + constraints** | Неясен scope | Да: начало на feature; НЕ: вместо бизнес критерии | Избистря същността | Прекаленото съкращаване крие правила → запиши invariants | README + JUnit |
| **Deque + Set** | O(L) преместване и collision lookup | Да: много ticks, растяща змия; НЕ: tiny toy с достатъчно бърза List | Expected O(1) обикновен tick | Две структури трябва да останат синхронизирани; алтернатива е simple List | 2D oracle parity |
| **1D index** | Прекалено много coordinate state | Да: rectangular grid; НЕ: general geometry | По-прост encoding | Незабележим row wrap без x/y checks | Wall test |
| **Bounded spawn** | Random retry без край | Да: finite slots; НЕ: за огромни дъски с много frequent spawns без memory budget | Гарантира termination | O(W×H) spawn; алтернатива: free-cell index с повече bookkeeping | Full-board tests |
| **Code golf** | Творческо учебно ограничение | Да: упражнение; НЕ: production код | Провокира нов design | Четимост, дебъг и поддръжка; алтернатива: структурирано refactoring | Parity преди/след |

## 6. Упражнения: не маркираме „усвоено“ автоматично

1. Опиши собствена backend функционалност в 6–8 думи. После формулирай 5 invariants. Защо краткото описание **не** заменя тестовия contract?
2. Нарисувай 5 сегмента след завой. Защо `addFirst` + `removeLast` е достатъчно?
3. За W=4, x=3, y=1: защо `index+1` е валиден int, но е **невалидна стъпка надясно**?
4. Защо навлизането в освобождаваща се опашка е позволено, но не и в останалото тяло?
5. Защо случайно търсене на храна не може да приключи на пълна дъска? Какъв terminal статус е нужен?
6. Промени в scratch branch начина на обновяване и провери дали паритетният тест хваща грешката.
7. **Optional:** версия с 10 функционални реда / 130 символа на ред без външни libs. **Не пренасяй** еднобуквените имена и скритите side effects обратно в основния код.

**Senior checkpoint:** в backend услуга търсим случаен свободен slot до успех. Всички slots са заети и API request увисва. Предложи bounded termination, timeout/cancellation, metrics, по-ефективен free-slot index и тест за 0 свободни слота. Оцени trade-offs, не LOC.

## 7. Какво запазваме от лекцията

Трите заключения на лектора: **learn by doing**, **constraints lead to creativity** и **conciseness brings understanding**. Внимателното инженерно тълкуване е „махни ненужната работа и зависимостите“, **не** „събери всички операции в 10 нечетими реда“. Не използваме `System.exit(score)`, умишлен 1D row wrap, nonterminating random retry или UI-bound game logic като production шаблони.

За български аудио урок с активни въпроси използвай [NotebookLM prompt](SNAKE-NOTEBOOKLM-PROMPT.md).