package ru.golubyatnikov.family.calendar.bot.handler.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.reminder.ReminderService;
import ru.golubyatnikov.family.calendar.bot.service.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.util.EventFormatter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
//TODO Исправить проблему с deprecated сервисом
@Component
@RequiredArgsConstructor
@Slf4j
public class WeekCommandHandler implements CommandHandler {

    private static final DateTimeFormatter DATE_RANGE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final int WEEK_DAYS = 7;
    
    private final EventService eventService;
    private final ReminderService reminderService;

    /**
     * Обрабатывает команду /week.
     * 
     * @param message сообщение от пользователя с командой
     * @param user пользователь, отправивший команду
     * @return сообщение для отправки пользователю
     */
    @Override
    public String handle(Message message, User user) {
        log.debug("Обработка команды /week для пользователя ID={}, семья ID={}", 
                  user.getId(), user.getFamily().getId());
        
        try {
            List<Event> weekEvents = eventService.getUpcomingEvents(user.getFamily().getId(), WEEK_DAYS, user.getZoneId());
            
            log.debug("Найдено {} событий до фильтрации для семьи ID={}", 
                    weekEvents.size(), user.getFamily().getId());

            List<Event> filteredEvents = weekEvents.stream()
                .filter(event -> !event.getIsPersonal() || event.belongsToUser(user.getId()))
                .toList();
            
            log.debug("После фильтрации осталось {} событий на неделю для пользователя ID={}", 
                    filteredEvents.size(), user.getId());
            
            if (filteredEvents.isEmpty()) {
                String responseMessage = EventFormatter.formatNoEventsMessage(
                    "📆",
                    "События на неделю",
                    "На ближайшую неделю событий не запланировано."
                );
                log.debug("Пользователю ID={} будет отправлено сообщение об отсутствии событий на неделю", user.getId());
                return responseMessage;
            }
            
            // Группировка событий по датам
            Map<LocalDate, List<Event>> eventsByDate = filteredEvents.stream()
                .collect(Collectors.groupingBy(Event::getEventDate));
            
            // Формирование сообщения с событиями
            StringBuilder messageBuilder = new StringBuilder();
            
            // Заголовок команды
            LocalDate startDate = user.getCurrentDate();
            LocalDate endDate = startDate.plusDays(6);
            String dateRange = startDate.format(DATE_RANGE_FORMATTER) + " - " + endDate.format(DATE_RANGE_FORMATTER);
            messageBuilder.append(EventFormatter.formatCommandHeader("📆", "События на неделю", dateRange));
            messageBuilder.append(escape("\n\n"));
            
            // Сортировка дат и вывод событий по дням
            LocalDate today = user.getCurrentDate();
            boolean firstDay = true;
            for (int i = 0; i < 7; i++) {
                LocalDate date = today.plusDays(i);
                List<Event> dayEvents = eventsByDate.get(date);
                
                if (dayEvents != null && !dayEvents.isEmpty()) {
                    // Добавляем разделитель перед каждым днем, кроме первого
                    if (!firstDay) {
                        messageBuilder.append(EventFormatter.formatDaySeparator());
                        messageBuilder.append(escape("\n\n")); // Пустая строка ПОСЛЕ разделителя
                    }
                    firstDay = false;
                    
                    messageBuilder.append(EventFormatter.formatDayHeader(date, today));
                    
                    for (Event event : dayEvents) {
                        boolean hasReminders = reminderService.hasActiveReminders(event.getId());
                        messageBuilder.append(EventFormatter.formatEvent(event, user, hasReminders));
                    }
                }
            }
            
            // Добавляем счетчик без разделителя перед ним
            messageBuilder.append(EventFormatter.formatEventCounter(filteredEvents.size()));
            
            String responseMessage = messageBuilder.toString();
            log.debug("Пользователю ID={} будет отправлен список из {} событий на неделю", 
                     user.getId(), filteredEvents.size());
            return responseMessage;
            
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
