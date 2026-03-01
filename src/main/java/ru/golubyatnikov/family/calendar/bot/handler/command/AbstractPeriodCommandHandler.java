package ru.golubyatnikov.family.calendar.bot.handler.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.domain.reminder.ReminderSchedulingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.DateTimeFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.EventFormattingService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Status.ERROR;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.bold;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.escape;

/**
 * Абстрактный базовый класс для обработчиков команд отображения событий по периодам.
 * Содержит общую логику для команд /today, /week, /month.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-03-02
 */
@RequiredArgsConstructor
@Slf4j
public abstract class AbstractPeriodCommandHandler implements CommandHandler {

    protected final EventService eventService;
    protected final ReminderSchedulingService reminderSchedulingService;
    protected final EventFormattingService eventFormattingService;
    protected final DateTimeFormattingService dateTimeFormattingService;

    /**
     * Обрабатывает команду отображения событий за период.
     *
     * @param message сообщение от пользователя с командой
     * @param user пользователь, отправивший команду
     *
     * @return сообщение для отправки пользователю
     */
    @Override
    public String handle(Message message, @NonNull User user) {
        if (message == null) {
            throw new IllegalArgumentException("Сообщение не может быть null");
        }

        if (!user.hasFamily()) {
            return buildNoFamilyMessage();
        }

        try {
            Long familyId = user.getFamily().getId();
            LocalDate startDate = calculateStartDate(user);
            LocalDate endDate = calculateEndDate(user, startDate);
            int daysInPeriod = calculateDaysInPeriod(startDate, endDate);

            // Получаем события за период
            List<Event> periodEvents = eventService.getUpcomingEvents(familyId, daysInPeriod, user.getZoneId());

            // Фильтруем события (личные + общие)
            List<Event> filteredEvents = periodEvents.stream()
                    .filter(event -> !event.getIsPersonal() || event.belongsToUser(user.getId()))
                    .collect(Collectors.toList());

            if (filteredEvents.isEmpty()) {
                return buildNoEventsMessage(startDate, endDate);
            }

            return buildEventsListMessage(filteredEvents, user, startDate, endDate);

        } catch (Exception e) {
            log.error("Ошибка при обработке команды {} для пользователя ID={}", getCommand(), user.getId(), e);
            return escape(ERROR + " Произошла ошибка при получении событий. Попробуйте позже.");
        }
    }

    /**
     * Вычисляет начальную дату периода.
     *
     * @param user пользователь
     * @return начальная дата периода
     */
    protected abstract LocalDate calculateStartDate(@NonNull User user);

    /**
     * Вычисляет конечную дату периода.
     *
     * @param user пользователь
     * @param startDate начальная дата периода
     * @return конечная дата периода
     */
    protected abstract LocalDate calculateEndDate(@NonNull User user, @NonNull LocalDate startDate);

    /**
     * Вычисляет количество дней в периоде.
     *
     * @param startDate начальная дата периода
     * @param endDate конечная дата периода
     * @return количество дней в периоде
     */
    protected abstract int calculateDaysInPeriod(@NonNull LocalDate startDate, @NonNull LocalDate endDate);

    /**
     * Возвращает эмодзи для команды.
     *
     * @return эмодзи команды
     */
    protected abstract String getCommandEmoji();

    /**
     * Возвращает название периода для заголовка.
     *
     * @return название периода (например, "События на неделю")
     */
    protected abstract String getPeriodTitle();

    /**
     * Возвращает текст для сообщения об отсутствии событий.
     *
     * @param dateRange диапазон дат
     * @return текст сообщения
     */
    protected abstract String getNoEventsText(@NonNull String dateRange);

    /**
     * Формирует сообщение об отсутствии семьи у пользователя.
     *
     * @return сообщение с инструкциями для пользователя без семьи
     */
    protected @NonNull String buildNoFamilyMessage() {
        return String.format("""
                        %s %s
                        
                        Вы не принадлежите ни одной семье.
                        
                        Для просмотра событий необходимо быть членом семьи. \
                        Обратитесь к администратору для добавления в семью.""",
                ERROR, bold("Ошибка"));
    }

    /**
     * Формирует сообщение об отсутствии предстоящих событий.
     *
     * @param startDate начальная дата периода
     * @param endDate конечная дата периода
     *
     * @return отформатированное сообщение об отсутствии событий
     */
    protected String buildNoEventsMessage(@NonNull LocalDate startDate,
                                          @NonNull LocalDate endDate) {
        String dateRange = formatDateRange(startDate, endDate);
        return eventFormattingService.formatNoEventsMessage(
                getCommandEmoji(),
                getPeriodTitle(),
                getNoEventsText(dateRange)
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
    protected @NonNull String buildEventsListMessage(@NonNull List<Event> filteredEvents,
                                                     @NonNull User user,
                                                     @NonNull LocalDate startDate,
                                                     @NonNull LocalDate endDate) {

        String dateRange = formatDateRange(startDate, endDate);
        String header = eventFormattingService.formatCommandHeader(getCommandEmoji(), getPeriodTitle(), dateRange);

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
     * Форматирует диапазон дат.
     *
     * @param startDate начальная дата
     * @param endDate конечная дата
     *
     * @return отформатированный диапазон дат
     */
    protected abstract @NonNull String formatDateRange(@NonNull LocalDate startDate, @NonNull LocalDate endDate);

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
