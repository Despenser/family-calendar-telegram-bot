package ru.golubyatnikov.family.calendar.bot.handler.command;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.event.EventService;
import ru.golubyatnikov.family.calendar.bot.util.EventFormatter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Обработчик команды /month для Telegram бота семейного календаря.
 * 
 * <p>Команда /month позволяет пользователям просматривать все предстоящие
 * события их семьи на месяц (от текущей даты до той же даты следующего месяца) в едином компактном формате. Она выполняет следующие функции:</p>
 * <ul>
 *   <li>Получает список предстоящих событий семьи пользователя</li>
 *   <li>Форматирует события с использованием {@link EventFormatter} для единообразия с другими командами</li>
 *   <li>Отображает название, время, описание и автора каждого события в компактном формате без отступов</li>
 *   <li>Сортирует события по дате и времени</li>
 *   <li>Отправляет соответствующее сообщение, если событий нет</li>
 * </ul>
 * 
 * <p>Команда требует авторизации - пользователь должен быть зарегистрирован
 * в системе и принадлежать семье.</p>
 * 
 * <p><b>Требования:</b> 1.3, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 3.1, 3.2, 3.3, 4.3, 5.1, 5.2, 6.1, 6.2, 6.3, 6.4, 6.5, 8.1, 8.2, 8.3, 8.4, 8.5</p>
 * 
 * <p><b>Пример использования:</b></p>
 * <pre>
 * Пользователь отправляет: /month
 * 
 * Если есть события:
 * Бот отвечает: "📅 **Предстоящие события** (30 дней)
 *                
 *                👨‍👩‍👧‍👦 День рождения мамы
 *                🕐 Время: 18:00
 *                📝 Описание: Празднование дня рождения
 *                👤 Создал: Иван Иванов
 *                
 *                👨‍👩‍👧‍👦 Поход в кино
 *                🕐 Время: 20:00
 *                📝 Описание: Смотрим новый фильм
 *                👤 Создал: Мария Петрова
 *                
 *                _Всего событий: 2_"
 * 
 * Если событий нет:
 * Бот отвечает: "📅 **Предстоящие события**
 *                
 *                На ближайшие 30 дней событий не запланировано."
 * </pre>
 * 
 * @see CommandHandler
 * @see EventService
 * @see EventFormatter
 * @see Event
 * @see User
 * @author Family Calendar Bot Team
 * @version 2.0.0
 * @since 2025-12-30
 */
@Component
@Slf4j
public class MonthCommandHandler implements CommandHandler {

    private static final DateTimeFormatter DATE_RANGE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    
    private final EventService eventService;
    private final ru.golubyatnikov.family.calendar.bot.service.reminder.ReminderSchedulingService reminderSchedulingService;

    /**
     * Конструктор для внедрения зависимостей.
     * 
     * @param eventService сервис для работы с событиями
     * @param reminderSchedulingService сервис для работы с напоминаниями
     */
    public MonthCommandHandler(EventService eventService, 
                                       ru.golubyatnikov.family.calendar.bot.service.reminder.ReminderSchedulingService reminderSchedulingService) {
        this.eventService = eventService;
        this.reminderSchedulingService = reminderSchedulingService;
    }

    /**
     * Обрабатывает команду /month от пользователя.
     * 
     * <p>Метод получает список предстоящих событий семьи пользователя
     * на месяц (от текущей даты до той же даты следующего месяца) и форматирует их в едином компактном формате с использованием
     * {@link EventFormatter} для обеспечения единообразия с другими командами списка событий.</p>
     * 
     * <p>Если у пользователя нет семьи, возвращается сообщение об ошибке.
     * Если событий нет, возвращается соответствующее информационное сообщение.</p>
     * 
     * @param message входящее сообщение от Telegram, содержащее команду /month
     * @param user пользователь из базы данных, запросивший список событий.
     *             Не может быть null, так как команда требует авторизации.
     * @return текст со списком предстоящих событий в компактном формате или сообщение об их отсутствии
     * @throws IllegalArgumentException если message равен null
     * @throws IllegalStateException если пользователь не принадлежит ни одной семье
     */
    @Override
    public String handle(Message message, User user) {
        if (message == null) {
            log.error("Получено null сообщение в MonthCommandHandler");
            throw new IllegalArgumentException("Сообщение не может быть null");
        }

        if (user == null) {
            log.error("Получен null пользователь в MonthCommandHandler");
            throw new IllegalArgumentException("Пользователь не может быть null");
        }

        Long telegramId = message.getFrom().getId();
        String username = message.getFrom().getUserName();

        log.info("Обработка команды /month: telegramId={}, username={}, userId={}", 
                telegramId, username, user.getId());

        // Проверяем наличие семьи у пользователя
        if (!user.hasFamily()) {
            log.warn("Пользователь ID={} не принадлежит ни одной семье", user.getId());
            return buildNoFamilyMessage();
        }

        Long familyId = user.getFamily().getId();
        log.debug("Получение предстоящих событий для семьи ID={}", familyId);

        // Вычисляем диапазон дат: от текущей даты до той же даты следующего месяца
        LocalDate startDate = user.getCurrentDate();
        LocalDate endDate = calculateEndDate(startDate);
        int daysInPeriod = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;

        log.debug("Диапазон дат для месяца: {} - {} ({} дней)", startDate, endDate, daysInPeriod);

        // Получаем предстоящие события семьи
        List<Event> upcomingEvents = eventService.getUpcomingEvents(
            familyId, daysInPeriod, user.getZoneId());

        log.debug("Найдено {} событий до фильтрации для семьи ID={}", 
                upcomingEvents.size(), familyId);

        // ========== ФИЛЬТРАЦИЯ ПЕРСОНАЛЬНЫХ СОБЫТИЙ ==========
        // Применяется единая логика фильтрации для обеспечения корректного отображения событий:
        //
        // Правила видимости:
        // 1. Семейные события (isPersonal = false) - видны ВСЕМ членам семьи
        // 2. Персональные события (isPersonal = true) - видны ТОЛЬКО создателю
        //
        // Логика фильтра: !event.getIsPersonal() || event.belongsToUser(user.getId())
        // - Если событие НЕ персональное (!event.getIsPersonal()) -> показываем
        // - ИЛИ если событие принадлежит текущему пользователю (event.belongsToUser(user.getId())) -> показываем
        // - В остальных случаях (персональное событие другого пользователя) -> скрываем
        //
        // Требования: 1.4, 4.1, 4.2, 5.4
        // =====================================================
        List<Event> filteredEvents = upcomingEvents.stream()
                .filter(event -> !event.getIsPersonal() || event.belongsToUser(user.getId()))
                .collect(Collectors.toList());

        log.info("После фильтрации осталось {} предстоящих событий для пользователя ID={}, семья ID={}", 
                filteredEvents.size(), user.getId(), familyId);

        if (filteredEvents.isEmpty()) {
            return buildNoEventsMessage(startDate, endDate);
        }

        return buildEventsListMessage(filteredEvents, user, startDate, endDate);
    }

    /**
     * Вычисляет конечную дату периода (та же дата следующего месяца).
     * 
     * <p>Если в следующем месяце нет такого же числа (например, 31 января → 28/29 февраля),
     * берется последний день следующего месяца.</p>
     * 
     * @param startDate начальная дата
     * @return конечная дата периода
     */
    private LocalDate calculateEndDate(LocalDate startDate) {
        LocalDate nextMonth = startDate.plusMonths(1);
        
        // Проверяем, существует ли такое же число в следующем месяце
        int dayOfMonth = startDate.getDayOfMonth();
        int lastDayOfNextMonth = nextMonth.lengthOfMonth();
        
        if (dayOfMonth > lastDayOfNextMonth) {
            // Если в следующем месяце нет такого числа, берем последний день месяца
            return nextMonth.withDayOfMonth(lastDayOfNextMonth);
        }
        
        return nextMonth;
    }

    /**
     * Формирует сообщение об отсутствии семьи у пользователя.
     * 
     * @return сообщение с инструкциями для пользователя без семьи
     */
    private String buildNoFamilyMessage() {
        return String.format("""
                        ❌ %s
                        
                        Вы не принадлежите ни одной семье.
                        
                        Для просмотра событий необходимо быть членом семьи. \
                        Обратитесь к администратору для добавления в семью.""",
               bold("Ошибка"));
    }

    /**
     * Формирует сообщение об отсутствии предстоящих событий.
     * 
     * <p>Использует {@link EventFormatter#formatNoEventsMessage(String, String)}
     * для обеспечения единообразия с другими командами списка событий.</p>
     * 
     * <p><b>Требования:</b> 4.3</p>
     * 
     * @param startDate начальная дата периода
     * @param endDate конечная дата периода
     * @return отформатированное сообщение об отсутствии событий
     */
    private String buildNoEventsMessage(LocalDate startDate, LocalDate endDate) {
        String dateRange = startDate.format(DATE_RANGE_FORMATTER) + " - " + endDate.format(DATE_RANGE_FORMATTER);
        return EventFormatter.formatNoEventsMessage(
                "🗓️",
                "События на месяц",
                "На период " + dateRange + " событий не запланировано."
        );
    }

    /**
     * Формирует список предстоящих событий с форматированием и группировкой по дням.
     * 
     * <p>Использует {@link EventFormatter#formatCommandHeader(String, String)},
     * {@link EventFormatter#formatDayHeader(LocalDate, LocalDate)},
     * {@link EventFormatter#formatDaySeparator()},
     * {@link EventFormatter#formatEvent(Event, User)} и
     * {@link EventFormatter#formatEventCounter(int)} для обеспечения единообразия
     * с другими командами списка событий.</p>
     * 
     * <p>События группируются по датам, для каждой даты добавляется заголовок дня,
     * между группами разных дней добавляются визуальные разделители.</p>
     * 
     * <p><b>Требования:</b> 1.3, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 3.1, 3.2, 3.3, 5.1, 5.2, 6.1, 6.2, 6.3, 6.4, 6.5, 8.1, 8.2, 8.3, 8.4, 8.5</p>
     * 
     * @param filteredEvents список отфильтрованных событий для форматирования
     * @param user текущий пользователь для определения создателя событий
     * @param startDate начальная дата периода
     * @param endDate конечная дата периода
     * @return отформатированное сообщение со списком событий, сгруппированных по дням
     */
    private String buildEventsListMessage(List<Event> filteredEvents, User user, LocalDate startDate, LocalDate endDate) {
        String dateRange = startDate.format(DATE_RANGE_FORMATTER) + " - " + endDate.format(DATE_RANGE_FORMATTER);
        String header = EventFormatter.formatCommandHeader("🗓️", "События на месяц", dateRange);
        
        // Группировка событий по датам
        Map<LocalDate, List<Event>> eventsByDate = filteredEvents.stream()
                .collect(Collectors.groupingBy(Event::getEventDate));
        
        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append(header);
        messageBuilder.append(escape("\n\n"));
        
        // Сортировка дат и вывод событий по дням
        LocalDate today = user.getCurrentDate();
        boolean firstDay = true;
        
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            List<Event> dayEvents = eventsByDate.get(date);
            
            if (dayEvents != null && !dayEvents.isEmpty()) {
                // Добавляем разделитель перед каждым днем, кроме первого
                if (!firstDay) {
                    messageBuilder.append(EventFormatter.formatDaySeparator());
                    messageBuilder.append(escape("\n\n")); // Пустая строка ПОСЛЕ разделителя
                }
                firstDay = false;
                
                // Добавляем заголовок дня
                messageBuilder.append(EventFormatter.formatDayHeader(date, today));
                
                // Добавляем события дня
                for (Event event : dayEvents) {
                    boolean hasReminders = reminderSchedulingService.hasActiveReminders(event.getId());
                    messageBuilder.append(EventFormatter.formatEvent(event, user, hasReminders));
                }
            }
        }
        
        // Добавляем счетчик событий
        messageBuilder.append(EventFormatter.formatEventCounter(filteredEvents.size()));
        
        return messageBuilder.toString();
    }

    /**
     * Возвращает команду, которую обрабатывает этот handler.
     * 
     * @return строка "/month"
     */
    @Override
    public String getCommand() {
        return "/month";
    }

    /**
     * Возвращает описание команды для отображения в справке.
     * 
     * @return описание команды
     */
    @Override
    public String getDescription() {
        return "Показать события на месяц";
    }

    /**
     * Определяет, требуется ли авторизация для выполнения этой команды.
     * 
     * <p>Команда /month требует авторизации, так как она отображает
     * события семьи, к которой принадлежит пользователь.</p>
     * 
     * @return true, так как команда требует авторизации
     */
    @Override
    public boolean requiresAuth() {
        return true;
    }
}
