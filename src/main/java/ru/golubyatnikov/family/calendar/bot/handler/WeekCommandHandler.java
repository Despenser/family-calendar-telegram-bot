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
import java.util.Map;
import java.util.stream.Collectors;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Обработчик команды /week для отображения событий на текущую неделю.
 * 
 * <p>Этот обработчик показывает все события семьи на ближайшие 7 дней,
 * сгруппированные по дням недели. Включает семейные события и персональные
 * события пользователя.</p>
 * 
 * <p>События отображаются с использованием Markdown форматирования и
 * группируются по датам для удобства восприятия.</p>
 * 
 * <p><b>Требования:</b> 28.2</p>
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
public class WeekCommandHandler implements CommandHandler {
    
    private final EventService eventService;
    private final TelegramMessageService messageService;
    
    private static final int WEEK_DAYS = 7;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy (EEEE)");
    private static final DateTimeFormatter SHORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM (EEEE)");
    
    /**
     * Обрабатывает команду /week.
     * 
     * <p>Получает все события семьи на ближайшие 7 дней, группирует их
     * по датам и отправляет отформатированный список пользователю.</p>
     * 
     * @param message сообщение от пользователя с командой
     * @param user пользователь, отправивший команду
     * @return сообщение для отправки пользователю
     */
    @Override
    public String handle(Message message, User user) {
        Long chatId = message.getChatId();
        log.debug("Обработка команды /week для пользователя ID={}, семья ID={}", 
                  user.getId(), user.getFamily().getId());
        
        try {
            // Получение событий на неделю (7 дней от текущей даты)
            List<Event> weekEvents = eventService.getUpcomingEvents(user.getFamily().getId(), WEEK_DAYS);
            
            log.debug("Найдено {} событий до фильтрации для семьи ID={}", 
                    weekEvents.size(), user.getFamily().getId());
            
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
            List<Event> filteredEvents = weekEvents.stream()
                .filter(event -> !event.getIsPersonal() || event.belongsToUser(user.getId()))
                .collect(Collectors.toList());
            
            log.info("После фильтрации осталось {} событий на неделю для пользователя ID={}", 
                    filteredEvents.size(), user.getId());
            
            if (filteredEvents.isEmpty()) {
                String responseMessage = escape("📅 ") + bold("События на неделю (7 дней)") + escape("\n\n") +
                                       escape("На ближайшую неделю событий не запланировано. ") +
                                       escape("Время для новых планов! 📝");
                log.info("Пользователю ID={} будет отправлено сообщение об отсутствии событий на неделю", user.getId());
                return responseMessage;
            }
            
            // Группировка событий по датам
            Map<LocalDate, List<Event>> eventsByDate = filteredEvents.stream()
                .collect(Collectors.groupingBy(Event::getEventDate));
            
            // Формирование сообщения с событиями
            StringBuilder messageBuilder = new StringBuilder();
            messageBuilder.append(escape("📅 ")).append(bold("События на неделю (7 дней)")).append(escape("\n"));
            messageBuilder.append(italic(LocalDate.now().format(DATE_FORMATTER) +
                         " - " +
                         LocalDate.now().plusDays(6).format(DATE_FORMATTER)))
                         .append(escape("\n\n"));
            
            // Сортировка дат и вывод событий по дням
            LocalDate today = LocalDate.now();
            for (int i = 0; i < 7; i++) {
                LocalDate date = today.plusDays(i);
                List<Event> dayEvents = eventsByDate.get(date);
                
                if (dayEvents != null && !dayEvents.isEmpty()) {
                    messageBuilder.append(formatDayHeader(date, today));
                    
                    for (Event event : dayEvents) {
                        messageBuilder.append(formatEvent(event, user));
                        messageBuilder.append(escape("\n"));
                    }
                    
                    messageBuilder.append(escape("\n"));
                }
            }
            
            messageBuilder.append(italic("Всего событий: " + filteredEvents.size()));
            
            String responseMessage = messageBuilder.toString();
            log.info("Пользователю ID={} будет отправлен список из {} событий на неделю", 
                     user.getId(), filteredEvents.size());
            return responseMessage;
            
        } catch (Exception e) {
            log.error("Ошибка при обработке команды /week для пользователя ID={}", user.getId(), e);
            String errorMessage = escape("❌ Произошла ошибка при получении событий на неделю. Попробуйте позже.");
            return errorMessage;
        }
    }
    
    /**
     * Форматирует заголовок дня с датой.
     * 
     * @param date дата для форматирования
     * @param today текущая дата (для определения "сегодня" и "завтра")
     * @return отформатированный заголовок дня
     */
    private String formatDayHeader(LocalDate date, LocalDate today) {
        StringBuilder sb = new StringBuilder();
        
        if (date.equals(today)) {
            sb.append(escape("📍 ")).append(bold("Сегодня")).append(escape(" ("));
        } else if (date.equals(today.plusDays(1))) {
            sb.append(escape("🔜 ")).append(bold("Завтра")).append(escape(" ("));
        } else {
            sb.append(escape("📆 "));
        }
        
        sb.append(bold(date.format(SHORT_DATE_FORMATTER)));
        
        if (date.equals(today) || date.equals(today.plusDays(1))) {
            sb.append(escape(")"));
        }
        
        sb.append(escape("\n"));
        return sb.toString();
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
        sb.append(escape("   "));
        
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
        
        // Описание события
        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            sb.append(escape("\n      ")).append(italic(event.getDescription()));
        }
        
        // Создатель события
        if (!event.belongsToUser(user.getId())) {
            sb.append(escape(" (")).append(escape(event.getUser().getFirstName())).append(escape(")"));
        }
        
        return sb.toString();
    }
    
    @Override
    public String getCommand() {
        return "/week";
    }
    
    @Override
    public String getDescription() {
        return "Показать события на неделю (7 дней)";
    }
    
    @Override
    public boolean requiresAuth() {
        return true;
    }
}
