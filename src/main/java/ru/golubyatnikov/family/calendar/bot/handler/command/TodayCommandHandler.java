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

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Commands.TODAY;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Status.ERROR;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.escape;

/**
 * Обработчик команды /today для отображения событий на текущий день.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-08
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TodayCommandHandler implements CommandHandler {
    
    private final EventService eventService;
    private final ReminderSchedulingService reminderSchedulingService;
    private final EventFormattingService eventFormattingService;
    private final DateTimeFormattingService dateTimeFormattingService;
    
    /**
     * Обрабатывает команду /today.

     * @param message сообщение от пользователя с командой
     * @param user пользователь, отправивший команду
     *
     * @return сообщение для отправки пользователю
     */
    @Override
    public String handle(Message message, @NonNull User user) {
        try {
            // Получение событий на сегодня (1 день от текущей даты)
            List<Event> todayEvents = eventService.getUpcomingEvents(
                user.getFamily().getId(), 1, user.getZoneId());
            
            // Фильтрация событий: только на сегодня
            LocalDate today = user.getCurrentDate();
            List<Event> todayOnlyEvents = todayEvents.stream()
                .filter(event -> event.getEventDate().equals(today))
                .toList();

            List<Event> filteredEvents = todayOnlyEvents.stream()
                .filter(event -> !event.getIsPersonal() || event.belongsToUser(user.getId()))
                .toList();
            
            if (filteredEvents.isEmpty()) {
                return eventFormattingService.formatNoEventsMessage(
                    TODAY,
                    "События на сегодня",
                    "На сегодня событий не запланировано."
                );
            }
            
            // Формирование сообщения с событиями
            StringBuilder messageBuilder = new StringBuilder();
            messageBuilder.append(eventFormattingService.formatCommandHeader(
                TODAY,
                "События на сегодня",
                dateTimeFormattingService.formatDateWithDayOfWeek(today)
            ));
            messageBuilder.append(escape("\n\n"));
            
            // Для команды /today не добавляем заголовок дня, так как дата уже указана в основном заголовке
            filteredEvents.forEach(event -> {
                boolean hasReminders = reminderSchedulingService.hasActiveReminders(event.getId());
                messageBuilder.append(eventFormattingService.formatEvent(event, user, hasReminders));
            });
            
            messageBuilder.append(eventFormattingService.formatEventCounter(filteredEvents.size()));

            return messageBuilder.toString();
            
        } catch (Exception e) {
            log.error("Ошибка при обработке команды /today для пользователя ID={}", user.getId(), e);
            return escape(ERROR + " Произошла ошибка при получении событий на сегодня. Попробуйте позже.");
        }
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
