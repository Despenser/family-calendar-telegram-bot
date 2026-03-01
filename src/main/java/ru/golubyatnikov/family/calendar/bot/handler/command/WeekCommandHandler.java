package ru.golubyatnikov.family.calendar.bot.handler.command;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.domain.reminder.ReminderSchedulingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.DateTimeFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.EventFormattingService;
import java.time.LocalDate;

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Commands.WEEK;

/**
 * Обработчик команды /week для отображения событий на текущую неделю.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-08
 */
@Component
public class WeekCommandHandler extends AbstractPeriodCommandHandler {

    public WeekCommandHandler(EventService eventService,
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
        return startDate.plusDays(6);
    }

    @Override
    protected int calculateDaysInPeriod(@NonNull LocalDate startDate, @NonNull LocalDate endDate) {
        return 7;
    }

    @Override
    protected String getCommandEmoji() {
        return WEEK;
    }

    @Override
    protected String getPeriodTitle() {
        return "События на неделю";
    }

    @Override
    protected String getNoEventsText(@NonNull String dateRange) {
        return "На период " + dateRange + " событий не запланировано.";
    }

    @Override
    protected @NonNull String formatDateRange(@NonNull LocalDate startDate, @NonNull LocalDate endDate) {
        return dateTimeFormattingService.formatDate(startDate) + " - " +
                dateTimeFormattingService.formatDate(endDate);
    }
    
    @Override
    public String getCommand() {
        return "/week";
    }
    
    @Override
    public String getDescription() {
        return "Показать события на неделю";
    }
}
