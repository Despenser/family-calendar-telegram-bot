package ru.golubyatnikov.family.calendar.bot.handler.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.DateTimeFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.domain.reminder.ReminderSchedulingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.EventFormattingService;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Обработчик команды /month для Telegram бота семейного календаря.
 *
 * @author Golubyatnikov Aleksey
 * @since 2025-12-30
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class MonthCommandHandler implements CommandHandler {
    
    private final EventService eventService;
    private final ReminderSchedulingService reminderSchedulingService;
    private final EventFormattingService eventFormattingService;
    private final DateTimeFormattingService dateTimeFormattingService;

    /**
     * Обрабатывает команду /month от пользователя.
     *
     * @param message входящее сообщение от Telegram, содержащее команду /month
     * @param user пользователь из базы данных, запросивший список событий.
     *             Не может быть null, так как команда требует авторизации.
     *
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
     * @param startDate начальная дата
     * @return конечная дата периода
     */
    private @NonNull LocalDate calculateEndDate(@NonNull LocalDate startDate) {
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
    private @NonNull String buildNoFamilyMessage() {
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
     * @param startDate начальная дата периода
     * @param endDate конечная дата периода
     *
     * @return отформатированное сообщение об отсутствии событий
     */
    private String buildNoEventsMessage(@NonNull LocalDate startDate,
                                        @NonNull LocalDate endDate) {

        String dateRange = formatDateRange(startDate, endDate);
        return eventFormattingService.formatNoEventsMessage(
                "🗓️",
                "События на месяц",
                "На период " + dateRange + " событий не запланировано."
        );
    }

    /**
     * Формирует список предстоящих событий с форматированием и группировкой по дням.
     *
     * @param filteredEvents список отфильтрованных событий для форматирования
     * @param user текущий пользователь для определения создателя событий
     * @param startDate начальная дата периода
     * @param endDate конечная дата периода
     *
     * @return отформатированное сообщение со списком событий, сгруппированных по дням
     */
    private @NonNull String buildEventsListMessage(@NonNull List<Event> filteredEvents,
                                                   @NonNull User user,
                                                   @NonNull LocalDate startDate,
                                                   @NonNull LocalDate endDate) {

        String dateRange = formatDateRange(startDate, endDate);
        String header = eventFormattingService.formatCommandHeader("🗓️", "События на месяц", dateRange);
        
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
                    messageBuilder.append(eventFormattingService.formatDaySeparator());
                    messageBuilder.append(escape("\n\n"));
                }
                firstDay = false;
                
                // Добавляем заголовок дня
                messageBuilder.append(eventFormattingService.formatDayHeader(date, today));
                
                // Добавляем события дня
                dayEvents.forEach(event -> {
                    boolean hasReminders = reminderSchedulingService.hasActiveReminders(event.getId());
                    messageBuilder.append(eventFormattingService.formatEvent(event, user, hasReminders));
                });
            }
        }
        
        // Добавляем счетчик событий
        messageBuilder.append(eventFormattingService.formatEventCounter(filteredEvents.size()));
        
        return messageBuilder.toString();
    }

    /**
     * Форматирует диапазон дат в формате "dd.MM.yyyy - dd.MM.yyyy".
     *
     * @param startDate начальная дата
     * @param endDate конечная дата
     *
     * @return отформатированный диапазон дат
     */
    private @NonNull String formatDateRange(@NonNull LocalDate startDate, @NonNull LocalDate endDate) {
        return dateTimeFormattingService.formatDateRange(startDate) + " - " + 
               dateTimeFormattingService.formatDateRange(endDate);
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
     * @return true, так как команда требует авторизации
     */
    @Override
    public boolean requiresAuth() {
        return true;
    }
}
