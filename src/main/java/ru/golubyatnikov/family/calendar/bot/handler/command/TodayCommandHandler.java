package ru.golubyatnikov.family.calendar.bot.handler.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.util.EventFormatter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.escape;

/**
 * Обработчик команды /today для отображения событий на текущий день.
 * 
 * <p>Этот обработчик показывает все события семьи, запланированные на сегодня,
 * включая семейные события и персональные события пользователя.</p>
 * 
 * <p>События отображаются с использованием единообразного форматирования через
 * {@link EventFormatter} для обеспечения консистентного пользовательского опыта
 * во всех командах списка событий.</p>
 * 
 * <p><b>Требования:</b> 1.1, 2.1, 2.2, 2.3, 2.4, 2.5, 3.1, 3.2, 3.3, 4.1, 5.1, 5.2, 6.1, 6.2, 6.3, 6.4</p>
 * 
 * @see CommandHandler
 * @see EventService
 * @see EventFormatter
 * @author Family Calendar Bot Team
 * @version 2.0.0
 * @since 2026-01-08
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TodayCommandHandler implements CommandHandler {
    
    private final EventService eventService;
    private final TelegramMessageService messageService;
    private final ru.golubyatnikov.family.calendar.bot.service.reminder.ReminderService reminderService;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy - EEEE", Locale.forLanguageTag("ru"));
    
    /**
     * Обрабатывает команду /today.
     * 
     * <p>Получает все события семьи на текущий день и отправляет
     * отформатированный список пользователю с использованием единообразного
     * форматирования через {@link EventFormatter}.</p>
     * 
     * <p>Применяется фильтрация персональных событий: семейные события видны всем,
     * персональные события видны только создателю.</p>
     * 
     * @param message сообщение от пользователя с командой
     * @param user пользователь, отправивший команду
     * @return сообщение для отправки пользователю
     */
    @Override
    public String handle(Message message, User user) {
        Long chatId = message.getChatId();
        log.debug("Обработка команды /today для пользователя ID={}, семья ID={}", 
                  user.getId(), user.getFamily().getId());
        
        try {
            // Получение событий на сегодня (1 день от текущей даты)
            List<Event> todayEvents = eventService.getUpcomingEvents(
                user.getFamily().getId(), 1, user.getZoneId());
            
            log.debug("Найдено {} событий до фильтрации для семьи ID={}", 
                    todayEvents.size(), user.getFamily().getId());
            
            // Фильтрация событий: только на сегодня
            LocalDate today = user.getCurrentDate();
            List<Event> todayOnlyEvents = todayEvents.stream()
                .filter(event -> event.getEventDate().equals(today))
                .collect(Collectors.toList());
            
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
            List<Event> filteredEvents = todayOnlyEvents.stream()
                .filter(event -> !event.getIsPersonal() || event.belongsToUser(user.getId()))
                .collect(Collectors.toList());
            
            log.debug("После фильтрации осталось {} событий на сегодня для пользователя ID={}", 
                    filteredEvents.size(), user.getId());
            
            if (filteredEvents.isEmpty()) {
                String responseMessage = EventFormatter.formatNoEventsMessage(
                    "📅",
                    "События на сегодня",
                    "На сегодня событий не запланировано."
                );
                log.debug("Пользователю ID={} будет отправлено сообщение об отсутствии событий на сегодня", user.getId());
                return responseMessage;
            }
            
            // Формирование сообщения с событиями
            StringBuilder messageBuilder = new StringBuilder();
            messageBuilder.append(EventFormatter.formatCommandHeader(
                "📅",
                "События на сегодня",
                today.format(DATE_FORMATTER)
            ));
            messageBuilder.append(escape("\n\n"));
            
            // Для команды /today не добавляем заголовок дня, так как дата уже указана в основном заголовке
            
            for (Event event : filteredEvents) {
                boolean hasReminders = reminderService.hasActiveReminders(event.getId());
                messageBuilder.append(EventFormatter.formatEvent(event, user, hasReminders));
            }
            
            messageBuilder.append(EventFormatter.formatEventCounter(filteredEvents.size()));
            
            String responseMessage = messageBuilder.toString();
            log.debug("Пользователю ID={} будет отправлен список из {} событий на сегодня", 
                     user.getId(), filteredEvents.size());
            return responseMessage;
            
        } catch (Exception e) {
            log.error("Ошибка при обработке команды /today для пользователя ID={}", user.getId(), e);
            String errorMessage = escape("❌ Произошла ошибка при получении событий на сегодня. Попробуйте позже.");
            return errorMessage;
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
    
    @Override
    public boolean requiresAuth() {
        return true;
    }
}
