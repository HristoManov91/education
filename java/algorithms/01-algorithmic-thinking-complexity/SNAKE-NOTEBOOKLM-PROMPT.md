# NotebookLM — Snake in 10 Lines: учим повече, като опростяваме проблема

## Източници за зареждане

1. [Guus de Wit — Snake in 10 Lines: Learning More by Coding Less](https://www.youtube.com/watch?v=LD2tKMpToYg), Devoxx 2026 + потребителският auto-generated SRT `[English (auto-generated)] Snake in 10 Lines Learning More by Coding Less  by Guus de Wit [DownSub.com].srt` (ако е наличен в NotebookLM).
2. [Нашият миниурок и decision contract](SNAKE-CONSTRAINT-DRIVEN-DESIGN.md).
3. [SnakeGame.java — Java 25 engine](src/main/java/bg/hristomanov/education/algorithms/complexity/snake/SnakeGame.java), [SnakeTextDemo.java](src/main/java/bg/hristomanov/education/algorithms/complexity/snake/SnakeTextDemo.java).
4. [SnakeGameTest.java](src/test/java/bg/hristomanov/education/algorithms/complexity/snake/SnakeGameTest.java) + [SnakeReferenceParityTest.java](src/test/java/bg/hristomanov/education/algorithms/complexity/snake/SnakeReferenceParityTest.java).
5. [Algorithmic Thinking & Complexity](README.md) и [Linear Data Structures](../02-linear-data-structures/README.md), за head/tail, Deque, Set и bounded search.

## Готов prompt за 20–25 мин разговорна аудио лекция на български

~~~text
Направи кратък, но сериозен 20–25-минутен разговорен аудио урок на БЪЛГАРСКИ за Java 25/Spring Boot backend инженер. Задачата е constraint-driven design и алгоритмично мислене, а не game development или Kotlin/Swing tutorial.

Използвай точния контекст от предоставения оригинален SRT: Guus de Wit започва с шест думи, описващи Snake, приема 10 функционални реда, максимум 130 символа на ред, без външни библиотеки; основната демонстрация е с Kotlin/Swing и в Q&A има Java вариант Jake. Трите изводa: learn by doing, constraints lead to creativity, conciseness brings understanding. Не приписвай нашите Java класове или JUnit тестове на лектора.

1. Входен казус: feature request на backend екип без минимален contract; човек веднага почва да прави абстракции, REST, storage и event bus. Върни се към six-word statement и инварианти.
2. Обясни защо първоначалната реализация мести всички body segments (O(L)), а едно логическо движение изисква само add head + remove tail. Направи аналогия с queue/rolling window.
3. Обясни 2D (x,y) срещу 1D индекс y*W+x и покажи row-wrap капана (x==W-1, +1). Разграничение между data representation и геометричен contract.
4. Изпълним walkthrough на нашия SnakeGame: Deque head-first, occupied HashSet, Direction, Tick, Status, View. Средна O(1) цена на normal tick и bounded O(W×H) избор на храна след ядене.
5. Включи капаните от оригиналния SRT: връщане на 180°, неочаквано прехвърляне на реда, статична храна и пълна дъска с безкрайно random търсене. Обясни защо нашият модел избира terminal WON при липса на свободни клетки.
6. Обясни tricky rule: moving into the departing tail is legal when not eating, otherwise collision. Защо са нужни редът на операциите и подходящ Set?
7. Покажи README → реални Java files → JUnit доказателства. Разграничи тези тестове от играта на сцената; не казвай, че авторът е използвал нашата структура.
8. Изследвай test-only 2D Reference2d срещу production 1D+Deque/HashSet, 300 seeded scenarios. Това е differential verification на поведение, не абсолютна формална гаранция или performance benchmark.
9. Decision contract за six-word spec, Deque+Set, 1D индекс, bounded food spawn и optional 10-line code golf: проблем, кога да/не, ползи, trade-offs, по-проста алтернатива, тест.
10. Преведи урока към реален backend case: търсене на свободен resource/slot, bounded retries, cancellation, observability и що е истинска простота.
11. Завърши с 5 отворени въпроса и практическо упражнение (първо решения, после сравнителни тестове). Не маркирай знанието като усвоено автоматично.

Избягвай неосновани твърдения за JVM performance и че нашият optimized engine винаги е по-бърз. Тук няма GUI/Swing test, няма JMH speedup, няма System.exit(score) и не се въвежда нов Maven module. Обяснявай техническите термини на български при първа употреба.
~~~

## След аудиото

Пусни `mvn -pl java/algorithms/01-algorithmic-thinking-complexity -am test` и отговори самостоятелно на упражненията в [основния материал](SNAKE-CONSTRAINT-DRIVEN-DESIGN.md). При спънка прегледай конкретен failing JUnit test и invariant, вместо да търсиш още едно видео.