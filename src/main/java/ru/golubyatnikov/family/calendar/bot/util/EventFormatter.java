package ru.golubyatnikov.family.calendar.bot.util;

import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Утилитный класс для единообразного форматирования событий в командах списка событий.
 * 
 * <p>Этот класс обеспечивает централизованное форматирование событий для команд
 * /today, /week и /upcoming_events, гарантируя единообразный пользовательский опыт.</p>
 * 
 * <p>Класс предоставляет методы для:</p>
 * <ul>
 *   <li>Форматирования событий в едином формате для всех команд</li>
 *   <li>Форматирования заголовков команд</li>
 *   <li>Форматирования сообщений об отсутствии событий</li>
 *   <li>Форматирования счетчиков событий</li>
 *   <li>Форматирования заголовков дней (для /week)</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 3.1, 3.2, 3.3,
 * 4.1, 4.2, 4.3, 4.4, 5.1, 5.2, 5.3, 6.1, 6.2, 6.3, 6.4, 6.5, 7.1, 7.2, 7.3, 7.4, 7.5,
 * 8.1, 8.2, 8.3, 8.4, 8.5</p>
 * 
 * @author Family Calendar Bot Team
 * @version 2.0.0
 * @since 2026-01-25
 * @see Event
 * @see User
 * @see MarkdownFormatter
 */
public final class EventFormatter {
    
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter FULL_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy (EEEE)", new Locale("ru"));
    private static final DateTimeFormatter SHORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM (EEEE)", new Locale("ru"));
    private static final DateTimeFormatter DATE_RANGE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    
    /**
     * Приватный конструктор для предотвращения создания экземпляров утилитного класса.
     * 
     * @throws UnsupportedOperationException всегда, так как это утилитный класс
     */
    private EventFormatter() {
        throw new UnsupportedOperationException("Это утилитный класс и не может быть инстанцирован");
    }
    
    /**
     * Форматирует событие в едином компактном формате для всех команд.
     * 
     * <p>Формат вывода:</p>
     * <pre>
     * [иконка типа] [название]
     * 🕐 Время: [время]
     * 📝 Описание: [описание]
     * 👤 Создал: [имя]
     * 
     * </pre>
     * 
     * <p>Порядок элементов: иконка типа и название жирным (первая строка) → 
     * время с эмодзи "🕐" (вторая строка без отступа, если есть) → 
     * описание с эмодзи "📝" (третья строка без отступа, если есть) → 
     * создатель с эмодзи "👤" (четвертая строка без отступа, если не текущий пользователь) →
     * пустая строка для разделения событий.</p>
     * 
     * <p>Примеры:</p>
     * <pre>
     * 👨‍👩‍👧‍👦 Встреча с врачом
     * 🕐 Время: 14:30 - 15:00
     * 📝 Описание: Не забыть взять карту
     * 👤 Создал: Мария
     * 
     * 🔒 Утренняя пробежка
     * 🕐 Время: 09:00
     * 
     * 👨‍👩‍👧‍👦 День рождения мамы
     * 📝 Описание: Празднование
     * 👤 Создал: Алексей
     * 
     * </pre>
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 3.1, 3.2, 3.3, 3.4, 6.1, 6.2, 6.3, 6.4, 6.5, 8.1, 8.2, 8.3, 8.4</p>
     * 
     * @param event событие для форматирования, не может быть null
     * @param currentUser текущий пользователь (для определения, показывать ли создателя), не может быть null
     * @return отформатированная строка с информацией о событии, завершающаяся пустой строкой
     * @throws IllegalArgumentException если event или currentUser равны null
     */
    public static String formatEvent(Event event, User currentUser) {
        if (event == null) {
            throw new IllegalArgumentException("Событие не может быть null");
        }
        if (currentUser == null) {
            throw new IllegalArgumentException("Текущий пользователь не может быть null");
        }
        
        StringBuilder sb = new StringBuilder();
        
        // Иконка типа события и название на первой строке
        sb.append(escape(getEventTypeIcon(event)));
        sb.append(bold(event.getTitle()));
        
        // Время события (на новой строке без отступа)
        String timeStr = formatEventTime(event);
        if (timeStr != null && !timeStr.isEmpty()) {
            sb.append(escape("\n")).append(timeStr);
        }
        
        // Описание события (на новой строке без отступа)
        String descriptionStr = formatEventDescription(event);
        if (descriptionStr != null && !descriptionStr.isEmpty()) {
            sb.append(escape("\n")).append(descriptionStr);
        }
        
        // Создатель события (на новой строке без отступа, если не текущий пользователь)
        String creatorInfo = formatCreatorInfo(event, currentUser);
        if (!creatorInfo.isEmpty()) {
            sb.append(escape("\n")).append(creatorInfo);
        }
        
        // Добавляем пустую строку после каждого события для разделения
        sb.append(escape("\n\n"));
        
        return sb.toString();
    }
    
    /**
     * Форматирует заголовок команды списка событий.
     * 
     * <p>Формат вывода:</p>
     * <pre>
     * 📅 **[Название]** ([дополнительная информация])
     * </pre>
     * 
     * <p>Примеры:</p>
     * <pre>
     * 📅 **События на сегодня** (25.01.2026 (Воскресенье))
     * 📅 **События на неделю** (25.01.2026 - 31.01.2026)
     * 📅 **Предстоящие события** (30 дней)
     * </pre>
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 1.3, 1.4</p>
     * 
     * @param commandName название команды (например, "События на сегодня"), не может быть null или пустым
     * @param additionalInfo дополнительная информация в скобках (например, дата или диапазон), не может быть null или пустым
     * @return отформатированный заголовок команды
     * @throws IllegalArgumentException если commandName или additionalInfo равны null или пустым
     */
    public static String formatCommandHeader(String commandName, String additionalInfo) {
        if (commandName == null || commandName.isBlank()) {
            throw new IllegalArgumentException("Название команды не может быть null или пустым");
        }
        if (additionalInfo == null || additionalInfo.isBlank()) {
            throw new IllegalArgumentException("Дополнительная информация не может быть null или пустой");
        }
        
        return escape("📅 ") + bold(commandName) + escape(" (") + escape(additionalInfo) + escape(")");
    }
    
    /**
     * Форматирует сообщение об отсутствии событий.
     * 
     * <p>Формат вывода:</p>
     * <pre>
     * 📅 **[Название команды]**
     * 
     * [Сообщение об отсутствии]
     * </pre>
     * 
     * <p>Примеры:</p>
     * <pre>
     * 📅 **События на сегодня**
     * 
     * На сегодня событий не запланировано.
     * 
     * 📅 **События на неделю**
     * 
     * На ближайшую неделю событий не запланировано.
     * </pre>
     * 
     * <p><b>Требования:</b> 4.1, 4.2, 4.3, 4.4</p>
     * 
     * @param commandName название команды (например, "События на сегодня"), не может быть null или пустым
     * @param message сообщение об отсутствии событий, не может быть null или пустым
     * @return отформатированное сообщение об отсутствии событий
     * @throws IllegalArgumentException если commandName или message равны null или пустым
     */
    public static String formatNoEventsMessage(String commandName, String message) {
        if (commandName == null || commandName.isBlank()) {
            throw new IllegalArgumentException("Название команды не может быть null или пустым");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Сообщение не может быть null или пустым");
        }
        
        return escape("📅 ") + bold(commandName) + escape("\n\n") + escape(message);
    }
    
    /**
     * Форматирует счетчик событий.
     * 
     * <p>Формат вывода:</p>
     * <pre>
     * _Всего событий: N_
     * </pre>
     * 
     * <p>Пример:</p>
     * <pre>
     * _Всего событий: 5_
     * </pre>
     * 
     * <p><b>Требования:</b> 5.1, 5.2, 5.3</p>
     * 
     * @param count количество событий, должно быть больше 0
     * @return отформатированный счетчик событий курсивом
     * @throws IllegalArgumentException если count меньше или равен 0
     */
    public static String formatEventCounter(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Количество событий должно быть больше 0");
        }
        
        return italic("Всего событий: " + count);
    }
    
    /**
     * Форматирует заголовок дня для команды /week.
     * 
     * <p>Формат вывода зависит от даты:</p>
     * <ul>
     *   <li>Сегодня: 📍 **Сегодня** (dd.MM (День недели))</li>
     *   <li>Завтра: 🔜 dd.MM (Завтра - День недели)</li>
     *   <li>Другие дни: 📆 dd.MM (День недели)</li>
     * </ul>
     * 
     * <p>Заголовок завершается двойным переносом строки для создания визуального отступа
     * между заголовком дня и первым событием в списке.</p>
     * 
     * <p>Примеры:</p>
     * <pre>
     * 📍 **Сегодня** (25.01 (Воскресенье))
     * 
     * 🔜 26.01 (Завтра - Понедельник)
     * 
     * 📆 27.01 (Вторник)
     * 
     * </pre>
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 1.3, 1.4, 1.5, 2.1, 2.2, 3.1, 3.2, 3.3, 7.1, 7.2, 7.3, 7.4, 7.5</p>
     * 
     * @param date дата для форматирования, не может быть null
     * @param today текущая дата (для определения "сегодня" и "завтра"), не может быть null
     * @return отформатированный заголовок дня с двойным переносом строки в конце
     * @throws IllegalArgumentException если date или today равны null
     */
    public static String formatDayHeader(LocalDate date, LocalDate today) {
        if (date == null) {
            throw new IllegalArgumentException("Дата не может быть null");
        }
        if (today == null) {
            throw new IllegalArgumentException("Текущая дата не может быть null");
        }
        
        StringBuilder sb = new StringBuilder();
        
        if (date.equals(today)) {
            // Формат для сегодняшнего дня: 📍 ДД.ММ (сегодня - день_недели)
            DateTimeFormatter todayDateFormatter = DateTimeFormatter.ofPattern("dd.MM", new Locale("ru"));
            DateTimeFormatter dayOfWeekFormatter = DateTimeFormatter.ofPattern("EEEE", new Locale("ru"));
            
            sb.append(escape("📍 "));
            sb.append(escape(date.format(todayDateFormatter)));
            sb.append(escape(" (сегодня - "));
            // День недели со строчной буквы
            String dayOfWeek = date.format(dayOfWeekFormatter);
            dayOfWeek = dayOfWeek.toLowerCase();
            sb.append(escape(dayOfWeek));
            sb.append(escape(")"));
        } else if (date.equals(today.plusDays(1))) {
            // Формат для завтрашнего дня: 🔜 ДД.ММ (завтра - день_недели)
            DateTimeFormatter tomorrowDateFormatter = DateTimeFormatter.ofPattern("dd.MM", new Locale("ru"));
            DateTimeFormatter dayOfWeekFormatter = DateTimeFormatter.ofPattern("EEEE", new Locale("ru"));
            
            sb.append(escape("🔜 "));
            sb.append(escape(date.format(tomorrowDateFormatter)));
            sb.append(escape(" (завтра - "));
            // День недели со строчной буквы
            String dayOfWeek = date.format(dayOfWeekFormatter);
            dayOfWeek = dayOfWeek.toLowerCase();
            sb.append(escape(dayOfWeek));
            sb.append(escape(")"));
        } else {
            sb.append(escape("📆 "));
            sb.append(escape(date.format(SHORT_DATE_FORMATTER)));
        }
        
        sb.append(escape("\n\n"));
        return sb.toString();
    }
    
    /**
     * Форматирует разделитель между днями для команды /week.
     * 
     * <p>Формат вывода:</p>
     * <pre>
     * ─────────────────────
     * </pre>
     * 
     * <p>Разделитель используется для визуального отделения событий разных дней
     * в команде /week. Добавляется между группами событий разных дней.</p>
     * 
     * <p><b>Требования:</b> 7.6</p>
     * 
     * @return отформатированный разделитель дней
     */
    public static String formatDaySeparator() {
        return escape("─────────────────────");
    }
    
    /**
     * Форматирует событие для результатов поиска с полной информацией.
     * 
     * <p>Формат вывода:</p>
     * <pre>
     * 📌 [название]
     * 📅 Дата: DD.MM.YYYY
     * 🕐 Время: [время]
     * [иконка типа] Тип: [Семейное/Личное]
     * 📝 Описание: [описание]
     * 👤 Создал: [имя]
     * </pre>
     * 
     * <p>Примеры:</p>
     * <pre>
     * 📌 Встреча с врачом
     * 📅 Дата: 26.01.2026
     * 🕐 Время: 14:30 - 15:00
     * 👨‍👩‍👧‍👦 Тип: Семейное
     * 📝 Описание: Не забыть взять карту
     * 👤 Создал: Мария
     * 
     * 📌 Утренняя пробежка
     * 📅 Дата: 27.01.2026
     * 🕐 Время: 09:00
     * 👤 Тип: Личное
     * 
     * 📌 День рождения мамы
     * 📅 Дата: 28.01.2026
     * 👨‍👩‍👧‍👦 Тип: Семейное
     * 📝 Описание: Празднование
     * 👤 Создал: Алексей
     * </pre>
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 4.1, 4.3</p>
     * 
     * @param event событие для форматирования, не может быть null
     * @param currentUser текущий пользователь (для определения, показывать ли создателя), не может быть null
     * @return отформатированная строка с информацией о событии для результатов поиска
     * @throws IllegalArgumentException если event или currentUser равны null
     */
    public static String formatSearchResult(Event event, User currentUser) {
        if (event == null) {
            throw new IllegalArgumentException("Событие не может быть null");
        }
        if (currentUser == null) {
            throw new IllegalArgumentException("Текущий пользователь не может быть null");
        }
        
        StringBuilder sb = new StringBuilder();
        
        // Эмодзи 📌 и название события
        sb.append(escape("📌 "));
        sb.append(bold(event.getTitle()));
        sb.append(escape("\n"));
        
        // Дата события
        sb.append(escape("📅 Дата: "));
        sb.append(escape(event.getEventDate().format(DATE_FORMATTER)));
        sb.append(escape("\n"));
        
        // Время события (если есть)
        if (event.getEventTime() != null) {
            sb.append(escape("🕐 Время: "));
            if (event.getEndTime() != null) {
                sb.append(escape(event.getEventTime().format(TIME_FORMATTER) + " - " + event.getEndTime().format(TIME_FORMATTER)));
            } else {
                sb.append(escape(event.getEventTime().format(TIME_FORMATTER)));
            }
            sb.append(escape("\n"));
        }
        
        // Тип события
        if (event.getIsPersonal()) {
            sb.append(escape("👤 Тип: Личное"));
        } else {
            sb.append(escape("👨‍👩‍👧‍👦 Тип: Семейное"));
        }
        sb.append(escape("\n"));
        
        // Описание события (если есть)
        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            sb.append(escape("📝 Описание: "));
            sb.append(escape(event.getDescription()));
            sb.append(escape("\n"));
        }
        
        // Создатель события (если не текущий пользователь)
        if (!event.belongsToUser(currentUser.getId())) {
            sb.append(escape("👤 Создал: " + event.getUser().getFirstName()));
            sb.append(escape("\n"));
        }
        
        return sb.toString();
    }
    
    /**
     * Возвращает иконку типа события.
     * 
     * <p>Иконки:</p>
     * <ul>
     *   <li>👤 - для персональных событий (isPersonal = true)</li>
     *   <li>👨‍👩‍👧‍👦 - для семейных событий (isPersonal = false)</li>
     * </ul>
     * 
     * @param event событие, не может быть null
     * @return иконка типа события
     */
    private static String getEventTypeIcon(Event event) {
        return event.getIsPersonal() ? "👤 " : "👨‍👩‍👧‍👦 ";
    }
    
    /**
     * Форматирует время события без отступа.
     * 
     * <p>Формат вывода:</p>
     * <ul>
     *   <li>Если есть время окончания: 🕐 Время: HH:mm - HH:mm</li>
     *   <li>Если только время начала: 🕐 Время: HH:mm</li>
     *   <li>Если времени нет: null</li>
     * </ul>
     * 
     * <p>Примеры:</p>
     * <pre>
     * 🕐 Время: 14:30 - 15:00
     * 🕐 Время: 09:00
     * null
     * </pre>
     * 
     * <p><b>Требования:</b> 2.2, 2.3, 3.2, 6.3, 8.2</p>
     * 
     * @param event событие, не может быть null
     * @return отформатированное время без отступа или null, если время не указано
     */
    private static String formatEventTime(Event event) {
        if (event.getEventTime() == null) {
            return null;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(escape("🕐 Время: "));
        
        if (event.getEndTime() != null) {
            sb.append(escape(event.getEventTime().format(TIME_FORMATTER) + " - " + event.getEndTime().format(TIME_FORMATTER)));
        } else {
            sb.append(escape(event.getEventTime().format(TIME_FORMATTER)));
        }
        
        return sb.toString();
    }
    
    /**
     * Форматирует описание события без отступа.
     * 
     * <p>Формат вывода:</p>
     * <pre>
     * 📝 Описание: [текст]
     * </pre>
     * 
     * <p>Пример:</p>
     * <pre>
     * 📝 Описание: Не забыть взять карту
     * </pre>
     * 
     * <p><b>Требования:</b> 2.4, 3.3, 6.4, 8.3</p>
     * 
     * @param event событие, не может быть null
     * @return отформатированное описание без отступа или null, если описание не указано
     */
    private static String formatEventDescription(Event event) {
        if (event.getDescription() == null || event.getDescription().isBlank()) {
            return null;
        }
        
        return escape("📝 Описание: ") + escape(event.getDescription());
    }
    
    /**
     * Форматирует информацию о создателе события без отступа.
     * 
     * <p>Возвращает "👤 Создал: Имя" создателя, если событие создано не текущим пользователем.
     * Если событие создано текущим пользователем, возвращает пустую строку.</p>
     * 
     * <p>Примеры:</p>
     * <pre>
     * "👤 Создал: Мария" - если событие создано другим пользователем
     * "" - если событие создано текущим пользователем
     * </pre>
     * 
     * <p><b>Требования:</b> 2.5, 2.6, 3.4, 6.5, 8.4</p>
     * 
     * @param event событие, не может быть null
     * @param currentUser текущий пользователь, не может быть null
     * @return "👤 Создал: Имя" создателя без отступа или пустая строка
     */
    private static String formatCreatorInfo(Event event, User currentUser) {
        if (event.belongsToUser(currentUser.getId())) {
            return "";
        }
        return escape("👤 Создал: " + event.getUser().getFirstName());
    }
}
