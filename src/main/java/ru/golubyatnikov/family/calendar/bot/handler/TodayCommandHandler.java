package ru.golubyatnikov.family.calendar.bot.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.EventService;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Обработчик команды /today для отображения событий на текущий день.
 * 
 * <p>Этот обработчик показывает все события семьи, запланированные на сегодня,
 * включая семейные события и персональные события пользователя.</p>
 * 
 * <p>События отображаются с использованием Markdown форматирования для
 * улучшения читаемости. Если событий на сегодня нет, отправляется
 * соответствующее сообщение.</p>
 * 
 * <p><b>Требования:</b> 28.1</p>
 * 
 * @see CommandHandler
 * @see EventService
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-08
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TodayCommandHandler implements CommandHandler {
    
    private final EventService eventService;
    private final TelegramMessageService messageService;
    
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy (EEEE)");
    
    /**
     * Обрабатывает команду /today.
     * 
     * <p>Получает все события семьи на текущий день и отправляет
     * отформатированный список пользователю.</p>
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
            List<Event> todayEvents = eventService.getUpcomingEvents(user.getFamily().getId(), 1);
            
            log.debug("Найдено {} событий до фильтрации для семьи ID={}", 
                    todayEvents.size(), user.getFamily().getId());
            
            // Фильтрация событий: только на сегодня
            LocalDate today = LocalDate.now();
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
                String responseMessage = escape("📅 ") + bold("События на сегодня") + escape("\n\n") +
                               escape("На сегодня событий не запланировано. Отличный день для отдыха! 😊");
                log.debug("Пользователю ID={} будет отправлено сообщение об отсутствии событий на сегодня", user.getId());
                return responseMessage;
            }
            
            // Формирование сообщения с событиями
            StringBuilder messageBuilder = new StringBuilder();
            messageBuilder.append(escape("📅 ")).append(bold("События на сегодня"))
                         .append(escape(" ("))
                         .append(escape(today.format(DATE_FORMATTER)))
                         .append(escape(")\n\n"));
            
            for (Event event : filteredEvents) {
                messageBuilder.append(formatEvent(event, user));
                messageBuilder.append(escape("\n"));
            }
            
            messageBuilder.append(escape("\n")).append(italic("Всего событий: " + filteredEvents.size()));
            
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
    
    /**
     * Форматирует событие для отображения в списке.
     * 
     * @param event событие для форматирования
     * @param user текущий пользователь (для определения персональных событий)
     * @return отформатированная строка с информацией о событии
     */
    private String formatEvent(Event event, User user) {
        StringBuilder sb = new StringBuilder();
        
        // Иконка типа события
        if (event.getIsPersonal()) {
            sb.append(escape("🔒 "));
        } else {
            sb.append(escape("👨‍👩‍👧‍👦 "));
        }
        
        // Время события
        if (event.getEventTime() != null) {
            sb.append(bold(event.getEventTime().format(TIME_FORMATTER)));
            
            // Временной интервал
            if (event.getEndTime() != null) {
                sb.append(escape(" - ")).append(bold(event.getEndTime().format(TIME_FORMATTER)));
            }
            
            sb.append(escape(" | "));
        }
        
        // Название события
        sb.append(bold(event.getTitle()));
        
        // Создатель события
        if (!event.belongsToUser(user.getId())) {
            sb.append(escape(" (")).append(escape(event.getUser().getFirstName())).append(escape(")"));
        }
        
        // Описание события
        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            sb.append(escape("\n   ")).append(italic(event.getDescription()));
        }
        
        return sb.toString();
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
