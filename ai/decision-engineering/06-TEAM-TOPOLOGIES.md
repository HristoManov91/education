# AI-DE-06 · Team Topologies, Engineering Bottlenecks & Knowledge Diffusion (P2)

## Казус

Team Gamma пилотира автоматизиран refactor. Други екипи искат същото. Ако Gamma стане единствен изпълнител на заявки, създаваме нов queue. Ако всяка група копира agents/scripts, нарастват maintenance, cognitive load и разходите.

**Лекция (~17:13–21:22, 29:29–36:36, 37:00–41:40):** успешен team capability се развива съвместно с DevEx platform, после става service; AI platform помага при costs и learning; архитектите имат невидима работа (firefighting, operational, strategic), част от която е възможност за учене, не безполезен toil; knowledge diffusion чрез съществуващи communities of practice, не непременно нова структура.

### Team Topologies — взаимодействия (допълнение от официалния източник)

| Mode | Пример | Изход |
| --- | --- | --- |
| Collaboration | Gamma + platform откриват общ contract за refactor | Time-boxed; установена граница |
| X-as-a-Service | platform предлага self-service pipeline с documented contract | Team ownership, adoption, SLO/support |
| Facilitating | enabling team обучава claims team в archaeology | Екипът придобива умение; не permanent dependency |

Не е задължително всеки екип да мине през всички modes. Използвай stream-aligned ownership; не създавай platform преди повторяемо потребление, нито централизирай business decisions. Enabling team не е централен approval board.

### Flow experiment (non-runnable)

Карта: idea → SME approval → make → review → move → maintain. Запиши work time, queue/wait, handoffs, rework, WIP за сходни items; провери действителната причина с хората. Отделно diary за architect tasks: firefighting, operational, strategic; маркирай автоматизируемо, learning-rich и high-risk. Не автоматизирай learning-rich задача без алтернатива за придобиване на knowledge.

Пилот: time-boxed collaboration за една reusable refactor pipeline, после две доброволни consuming teams. Мери onboarding time, task success, support load, cost/success, escaped defects, satisfaction и lead time. Ако platform създава по-голяма опашка, върни ownership/промени интерфейса. Разпространи наученото с кратък decision note и workshop.

Decision contract: за споделен повтаряем capability и доказани handoff bottlenecks; не при един малък екип или стабилен инструмент с нисък overhead. Печелим reuse, learning, по-нисък cognitive load; губим platform staffing, coordination, versioning и възможен central bottleneck. Алтернатива: споделен script и community of practice. Доказателство: карта, pilot + baseline; няма измерен организационен ефект в Education.

## Оригинални източници

[Лекция](https://www.youtube.com/watch?v=2pSyF44THEc) · [Team Topologies interaction modes](https://teamtopologies.com/key-concepts-content/team-interaction-modeling-with-team-topologies) · [DORA platform engineering](https://dora.dev/capabilities/platform-engineering/) · [DORA metrics](https://dora.dev/guides/dora-metrics/).
