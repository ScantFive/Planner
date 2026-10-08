# Голосовой ввод, виджет, тепловая карта — план реализации

> **Для исполнителей-агентов:** ОБЯЗАТЕЛЬНЫЙ навык: superpowers:subagent-driven-development (рекомендуется) или superpowers:executing-plans. Шаги отмечаются чекбоксами (`- [ ]`).

**Цель:** быстрое добавление задач голосом или одной фразой, виджет с календарём на рабочем столе и тепловая карта близости сроков.

**Архитектура:**
- Чистая логика без Android — разбор фразы (`QuickAddParser`) и тепловая карта (`Heatmap.kt`) — покрыта JVM-тестами.
- UI построен на существующем Compose-стеке.
- Виджет — `AppWidgetProvider` с картинкой месяца, нарисованной на `Canvas` по той же раскладке, что календарь в приложении.

**Стек:** Kotlin 2.0, Jetpack Compose (Material 3), Room, `RecognizerIntent`, `AppWidgetProvider` + `RemoteViews`.

**Спецификация:** `docs/superpowers/specs/2026-10-08-voice-widget-heatmap-design.md`

## Глобальные ограничения
- Схема БД не меняется (версия 3).
- Разрешения не добавляются: ни INTERNET, ни RECORD_AUDIO.
- До **5** точек в клетке, и в приложении, и в виджете.
- Веса тепловой карты: `1.0 / 0.5 / 0.25`. Пороги уровней: `0 | (0;1] | (1;2] | (2;3] | >3`. Прозрачность заливки: `level × 0.10`.
- Три PR в `main` строго по порядку: Часть A, затем B, затем C. Следующая часть уходит на GitHub только после влития предыдущей.
- **Локальная проверка чистой логики:** `gradle test` в `$SCRATCH/jvmcheck`. Это JVM-проект, который компилирует `Task.kt`, `TaskColors.kt`, `CalendarModel.kt`, `Heatmap.kt`, `QuickAddParser.kt` и тесты. Android-код проверяется только в CI.

## На что смотреть при ревью
1. Фраза вообще без даты («Купить хлеб») — задача без срока, название = фраза.
2. Фраза, где всё распознано, а название пустое («завтра в 15») — «Сохранить» неактивна.
3. Название с цифрами, похожими на время («Позвонить 2 раза маме») — «2» не превращается в время, потому что время требует «в» или формат `ЧЧ:ММ`.
4. «в пятницу» в пятницу после указанного часа — переносится на следующую неделю, а не теряет срок. Это уточнение спецификации в пользу пользователя.
5. Повторное нажатие на микрофон в виджете, когда приложение уже открыто, — окно открывается через `onNewIntent`, второй экземпляр активити не создаётся (`launchMode="singleTask"`).

---

## Часть A — голосовой ввод (PR 1)

### Задача A1: `QuickAddParser`
**Файлы:**
- Создать: `app/src/main/java/com/scantfive/planner/quickadd/QuickAddParser.kt`
- Тест: `app/src/test/java/com/scantfive/planner/QuickAddParserTest.kt`

**Интерфейсы — Produces:**
```kotlin
data class ParsedTask(val title: String, val dueAt: LocalDateTime?, val endDate: LocalDate?, val color: Int?)
object QuickAddParser { fun parse(text: String, now: LocalDateTime): ParsedTask }
```

- [ ] Тесты. `now = 2026-10-08T10:20` (четверг). Пары «вход → ожидаемое»:
  - «Купить хлеб» → без срока;
  - «Созвон с Аней завтра в 15» → `2026-10-09T15:00`, название «Созвон с Аней»;
  - «в 15:30», «в 15.30» → сегодня 15:30;
  - «в 9» → завтра 09:00, потому что 9:00 сегодня уже прошло;
  - «в 8 вечера» → 20:00; «в 1 дня» → 13:00; «в 2 ночи» → завтра 02:00;
  - «вечером» → 19:00; «утром» → завтра 09:00; «днём» и «днем» → 13:00;
  - «послезавтра» → 10-10 09:00;
  - «12 октября» → 2026-10-12 09:00; «1 октября» → 2027-10-01; «12.10» → 2026-10-12; «12.10.2027» → 2027-10-12;
  - «во вторник» → 10-13; «в четверг» → сегодня 11:00 (ближайший круглый час); «в чт в 9» → 10-15 09:00;
  - «через 2 часа» → 12:20; «через час» → 11:20; «через три дня» → 10-11 09:00; «через неделю» → 10-15 09:00;
  - «Отпуск с 12 по 20 октября» → начало 10-12 09:00, конец 10-20;
  - «с 28 декабря по 3 января» → конец 2027-01-03;
  - «Поездка на 3 дня» → начало сегодня 11:00, конец 10-10; «на 1 день» → без даты окончания;
  - «красным», «Зелёный» → цвет из `TaskColors.palette`: красный = индекс 5, зелёный = 3, синий = 1;
  - «Позвонить 2 раза маме» → без срока, название не меняется;
  - «завтра в 15» → название «»;
  - регистр и «ё»: «ЗАВТРА» распознаётся.
- [ ] Прогнать: `gradle test` в jvmcheck → тесты падают (нет класса).
- [ ] Реализация:
  - нормализация — нижний регистр и `ё`→`е`, длина строки сохраняется;
  - регулярки с границами `(?<![\p{L}\d])…(?![\p{L}\d])`;
  - распознанные диапазоны вырезаются из исходной строки;
  - висящие по краям предлоги (в, во, на, к, до, с, по) и знаки препинания срезаются;
  - первая буква названия заглавная;
  - порядок разбора: цвет → «с…по» → «на N дней» → «через N …» → слова-дни → дни недели → явные даты → время;
  - разрешение даты и времени — по правилам спецификации.
- [ ] Тесты зелёные. Коммит `feat: quick-add phrase parser`.

### Задача A2: окно быстрого добавления, распознавание, точки входа
**Файлы:**
- Создать:
  - `quickadd/QuickAddDialog.kt`
  - `quickadd/Speech.kt`
  - `res/drawable/ic_mic.xml`
- Изменить:
  - `MainActivity.kt` — хост окна, `ACTION_QUICK_ADD`, `onNewIntent`;
  - `AndroidManifest.xml` — `<queries>`, `launchMode="singleTask"`;
  - `ui/TaskEditViewModel.kt` и `PlannerApp.kt` — черновик для «Подробнее»;
  - `ui/TaskListScreen.kt`, `ui/calendar/CalendarScreen.kt` — кнопка в верхней панели;
  - `strings.xml`.

**Интерфейсы:**
- Consumes: `QuickAddParser.parse`.
- Produces:
  - `const val ACTION_QUICK_ADD = "com.scantfive.planner.action.QUICK_ADD"` в `MainActivity.kt`;
  - `fun ParsedTask.toTask(zone: ZoneId = ZoneId.systemDefault()): Task`;
  - `fun isSpeechAvailable(context: Context): Boolean`;
  - `AppContainer.draft: DraftHolder` с `fun put(task: Task)` и `fun take(): Task?`.

- [ ] Тест `toTask`: перевод в миллисекунды, `endDate` → `endDay`, цвет `null` → `TaskColors.DEFAULT`. Файл тот же — `QuickAddParserTest`.
- [ ] Окно быстрого добавления:
  - `AlertDialog`: поле ввода, кнопка микрофона (только если `isSpeechAvailable`), предпросмотр (цветная точка, название, срок / диапазон);
  - кнопки «Отмена», «Подробнее», «Сохранить»; «Сохранить» неактивна при пустом названии;
  - распознавание запускается при открытии, если окно открыли голосом; флаг через `rememberSaveable`, чтобы не перезапускать после поворота.
- [ ] Кнопки в верхней панели «Календарь» и «Задачи»:
  - иконка `ic_mic`, если распознавание доступно, иначе `Icons.Default.Create`;
  - нажатие открывает окно в голосовом режиме.
- [ ] «Подробнее»: `draft.put(task)`, затем переход на `edit/-1`. `TaskEditViewModel` для новой задачи забирает черновик через `take()`.
- [ ] Прогнать jvmcheck. Коммит, пуш, PR. Дождаться зелёного CI.

---

## Часть B — виджет (PR 2)

### Задача B1: рендер месяца и виджет
**Файлы:**
- Создать:
  - `widget/MonthBitmapRenderer.kt`
  - `widget/CalendarWidgetProvider.kt`
  - `widget/WidgetUpdater.kt`
  - `res/layout/widget_calendar.xml`
  - `res/xml/calendar_widget_info.xml`
  - `res/drawable/widget_background.xml`
  - `res/values-night/colors.xml`
- Изменить:
  - `AndroidManifest.xml` — receiver;
  - `data/TaskRepository.kt` — `onChanged: () -> Unit = {}` вызывается после save и delete, плюс `suspend fun allTasks(): List<Task>`;
  - `PlannerApp.kt` — передать `WidgetUpdater::requestUpdate`;
  - `strings.xml`.

**Интерфейсы — Produces:**
```kotlin
class MonthBitmapRenderer(private val density: Float) {
  fun render(month: YearMonth, today: LocalDate, events: List<CalendarEvent>, firstDayOfWeek: DayOfWeek,
             widthPx: Int, heightPx: Int, palette: WidgetPalette): Bitmap
}
data class WidgetPalette(val text: Int, val muted: Int, val accent: Int, val onAccent: Int, val heat: Int)
object WidgetUpdater { fun requestUpdate(context: Context) }
```

- [ ] Рендер:
  - строка дней недели, затем недели, высота делится поровну;
  - в каждой клетке: заливка тепловой карты (для части C; в части B — без неё), номер дня, кружок «сегодня», до 3 дорожек полос через `weekSegments`, до 5 точек через `dotsFor`;
  - у полосы скругляется край, только если `roundStart` / `roundEnd`.
- [ ] Провайдер:
  - `onUpdate` и `onAppWidgetOptionsChanged` → `goAsync` → `allTasks()` → `render` → `RemoteViews`;
  - заголовок — «Октябрь 2026»;
  - картинка открывает приложение, микрофон шлёт `ACTION_QUICK_ADD`;
  - размер берётся из `OPTION_APPWIDGET_MIN_WIDTH` / `MAX_HEIGHT` в dp, минус шапка 40dp, с ограничением ≤ 1 000 000 пикселей.
- [ ] Метаданные виджета: `minWidth="180dp"`, `minHeight="110dp"`, `targetCellWidth=4`, `targetCellHeight=3`, `resizeMode="horizontal|vertical"`, `updatePeriodMillis="1800000"`.
- [ ] Лимит точек 5 в приложении: `MAX_DOTS = 5` в `CalendarScreen.kt`.
- [ ] Коммит, PR, зелёный CI.

---

## Часть C — тепловая карта (PR 3)

### Задача C1: `Heatmap.kt` + отрисовка
**Файлы:**
- Создать: `ui/calendar/Heatmap.kt`
- Тест: `app/src/test/java/com/scantfive/planner/HeatmapTest.kt`
- Изменить: `ui/calendar/CalendarScreen.kt` (фон клетки), `widget/MonthBitmapRenderer.kt` (заливка).

**Интерфейсы — Produces:**
```kotlin
fun heatScore(day: LocalDate, events: List<CalendarEvent>): Double
fun heatLevel(day: LocalDate, events: List<CalendarEvent>): Int   // 0..4
fun heatAlpha(level: Int): Float                                  // level * 0.10f
```

- [ ] Тесты:
  - нет событий → 0;
  - одно завершается сегодня → score 1.0, level 1;
  - одно завтра → 0.5, level 1;
  - одно послезавтра → 0.25, level 1;
  - через 3 дня → 0;
  - 2 сегодня + 1 завтра → 2.5, level 3;
  - 4 сегодня → level 4;
  - выполненное не считается;
  - у протяжённого 10-06…10-09 считается только последний день: для 10-08 вклад 0.5, для 10-06 → 0;
  - ровно 1.0 → level 1, ровно 2.0 → level 2, ровно 3.0 → level 3.
- [ ] Реализация. Тесты зелёные в jvmcheck.
- [ ] Фон клетки в приложении (`primary` с `heatAlpha`) и в виджете.
- [ ] Обновить `docs/IDEA_AND_PLAN.md`. Коммит, PR, зелёный CI.
