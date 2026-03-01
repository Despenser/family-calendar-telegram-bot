package ru.golubyatnikov.family.calendar.bot.handler.command;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.domain.reminder.ReminderSchedulingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.DateTimeFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.EventFormattingService;

import java.time.LocalDate;
import java.util.List;

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Commands.TODAY;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.escape;

/**
 * Обработчик команды /today для отображения событий на текущий день.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-08
 */
@Component
public class TodayCommandHandler extends AbstractPeriodCommandHandler {

    public TodayCommandHandler(EventService eventService,
                               ReminderSchedulingService reminderSchedulingService,
                               EventFormattingService eventFormattingService,
                               DateTimeFormattingService dateTimeFormattingService) {

        super(eventService, reminderSchedulingService, eventFormattingService, dateTimeFormattingService);
    }

    @Override
    protected LocalDate calculateStartDate(@NonNull User user) {
        return user.getCurrentDate();
    }

    @Override
    protected LocalDate calculateEndDate(@NonNull User user, @NonNull LocalDate startDate) {
        return startDate; // Для today начальная и конечная дата совпадают
    }

    @Override
    protected int calculateDaysInPeriod(@NonNull LocalDate startDate, @NonNull LocalDate endDate) {
        return 1;
    }

    @Override
    protected String getCommandEmoji() {
        return TODAY;
    }

    @Override
    protected String getPeriodTitle() {
        return "События на сегодня";
    }

    @Override
    protected String getNoEventsText(@NonNull String dateRange) {
        return "На сегодня событий не запланировано.";
    }

    @Override
    protected @NonNull String formatDateRange(@NonNull LocalDate startDate, @NonNull LocalDate endDate) {
        // Для today показываем дату с днем недели
        return dateTimeFormattingService.formatDateWithDayOfWeek(startDate);
    }

    /**
     * Переопределяем метод для today, чтобы не добавлять заголовок дня,
     * так как дата уже указана в основном заголовке.
     */
    @Override
    protected @NonNull String buildEventsListMessage(@NonNull List<Event> filteredEvents,
                                                     @NonNull User user,
                                                     @NonNull LocalDate startDate,
                                                     @NonNull LocalDate endDate) {

        String dateRange = formatDateRange(startDate, endDate);
        String header = eventFormattingService.formatCommandHeader(getCommandEmoji(), getPeriodTitle(), dateRange);

        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append(header);
        messageBuilder.append(escape("\n\n"));

        // Для команды /today не добавляем заголовок дня, сразу выводим события
        filteredEvents.forEach(event -> {
            boolean hasReminders = reminderSchedulingService.hasActiveReminders(event.getId());
            messageBuilder.append(eventFormattingService.formatEvent(event, user, hasReminders));
        });

        // Добавляем счетчик событий
        messageBuilder.append(eventFormattingService.formatEventCounter(filteredEvents.size()));

        return messageBuilder.toString();
    }

    @Override
    public String getCommand() {
        return "/today";
    }

    @Override
    public String getDescription() {
        return "Показать события на сегодня";
    }
}
