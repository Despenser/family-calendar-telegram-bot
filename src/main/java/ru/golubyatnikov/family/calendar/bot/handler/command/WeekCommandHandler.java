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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Обработчик команды /week для отображения событий на текущую неделю.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-08
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WeekCommandHandler implements CommandHandler {

    private static final int WEEK_DAYS = 7;
    
    private final EventService eventService;
    private final ReminderSchedulingService reminderSchedulingService;
    private final EventFormattingService eventFormattingService;
    private final DateTimeFormattingService dateTimeFormattingService;

    /**
     * Обрабатывает команду /week.
     * 
     * @param message сообщение от пользователя с командой
     * @param user пользователь, отправивший команду
     *
     * @return сообщение для отправки пользователю
     */
    @Override
    public String handle(Message message, @NonNull User user) {
        try {
            List<Event> weekEvents = eventService.getUpcomingEvents(user.getFamily().getId(), WEEK_DAYS, user.getZoneId());
            
            List<Event> filteredEvents = weekEvents.stream()
                .filter(event -> !event.getIsPersonal() || event.belongsToUser(user.getId()))
                .toList();
            
            if (filteredEvents.isEmpty()) {
                return eventFormattingService.formatNoEventsMessage(
                    "📆",
                    "События на неделю",
                    "На ближайшую неделю событий не запланировано."
                );
            }
            
            // Группировка событий по датам
            Map<LocalDate, List<Event>> eventsByDate = filteredEvents.stream()
                .collect(Collectors.groupingBy(Event::getEventDate));
            
            // Формирование сообщения с событиями
            StringBuilder messageBuilder = new StringBuilder();
            
            // Заголовок команды
            LocalDate startDate = user.getCurrentDate();
            LocalDate endDate = startDate.plusDays(6);
            String dateRange = dateTimeFormattingService.formatDate(startDate) + " - " + dateTimeFormattingService.formatDate(endDate);
            messageBuilder.append(eventFormattingService.formatCommandHeader("📆", "События на неделю", dateRange));
            messageBuilder.append(escape("\n\n"));
            
            // Сортировка дат и вывод событий по дням
            LocalDate today = user.getCurrentDate();
            boolean firstDay = true;
            int displayedEventsCount = 0;
            
            for (int i = 0; i < WEEK_DAYS; i++) {
                LocalDate date = today.plusDays(i);
                List<Event> dayEvents = eventsByDate.get(date);
                
                if (dayEvents != null && !dayEvents.isEmpty()) {
                    // Добавляем разделитель перед каждым днем, кроме первого
                    if (!firstDay) {
                        messageBuilder.append(eventFormattingService.formatDaySeparator());
                        messageBuilder.append(escape("\n\n"));
                    }
                    firstDay = false;
                    
                    messageBuilder.append(eventFormattingService.formatDayHeader(date, today));
                    
                    for (Event event : dayEvents) {
                        boolean hasReminders = reminderSchedulingService.hasActiveReminders(event.getId());
                        messageBuilder.append(eventFormattingService.formatEvent(event, user, hasReminders));
                        displayedEventsCount++;
                    }
                }
            }
            
            // Добавляем счетчик только отображенных событий
            messageBuilder.append(eventFormattingService.formatEventCounter(displayedEventsCount));

            return messageBuilder.toString();
            
        } catch (Exception e) {
            log.error("Ошибка при обработке команды /week для пользователя ID={}", user.getId(), e);
            return escape("❌ Произошла ошибка при получении событий на неделю. Попробуйте позже.");
        }
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
