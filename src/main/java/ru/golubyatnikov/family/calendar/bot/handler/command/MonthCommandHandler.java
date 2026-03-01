package ru.golubyatnikov.family.calendar.bot.handler.command;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.domain.reminder.ReminderSchedulingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.DateTimeFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.EventFormattingService;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Commands.MONTH;

/**
 * Обработчик команды /month для Telegram бота семейного календаря.
 *
 * @author Golubyatnikov Aleksey
 * @since 2025-12-30
 */
@Component
public class MonthCommandHandler extends AbstractPeriodCommandHandler {

    public MonthCommandHandler(EventService eventService,
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

    @Override
    protected int calculateDaysInPeriod(@NonNull LocalDate startDate, @NonNull LocalDate endDate) {
        return (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    @Override
    protected String getCommandEmoji() {
        return MONTH;
    }

    @Override
    protected String getPeriodTitle() {
        return "События на месяц";
    }

    @Override
    protected String getNoEventsText(@NonNull String dateRange) {
        return "На период " + dateRange + " событий не запланировано.";
    }

    @Override
    protected @NonNull String formatDateRange(@NonNull LocalDate startDate, @NonNull LocalDate endDate) {
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
}
