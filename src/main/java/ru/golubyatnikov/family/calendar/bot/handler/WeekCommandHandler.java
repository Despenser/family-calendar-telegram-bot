package ru.golubyatnikov.family.calendar.bot.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.EventService;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;
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
 * <p>Этот обработчик показывает все события семьи на ближайшие 7 дней,
 * сгруппированные по дням недели с разделителями между днями. Включает семейные 
 * события и персональные события пользователя.</p>
 * 
 * <p>События отображаются в едином компактном формате без отступов с использованием
 * {@link EventFormatter}, что обеспечивает согласованный пользовательский опыт
 * во всех командах списка событий. Между группами событий разных дней добавляются
 * визуальные разделители для улучшения читаемости.</p>
 * 
 * <p><b>Требования:</b> 1.2, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 3.1, 3.2, 3.3, 3.4, 4.2, 5.1, 5.2,
 * 6.1, 6.2, 6.3, 6.4, 6.5, 7.1, 7.2, 7.3, 7.4, 7.5, 7.6</p>
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
public class WeekCommandHandler implements CommandHandler {
    
    private final EventService eventService;
    private final TelegramMessageService messageService;
    private final ru.golubyatnikov.family.calendar.bot.service.ReminderService reminderService;
    
    private static final int WEEK_DAYS = 7;
    private static final DateTimeFormatter DATE_RANGE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    
    /**
     * Обрабатывает команду /week.
     * 
     * <p>Получает все события семьи на ближайшие 7 дней, группирует их
     * по датам и отправляет отформатированный список пользователю. События
     * отображаются в едином компактном формате без отступов, с разделителями
     * между группами разных дней.</p>
     * 
     * <p>Использует {@link EventFormatter} для единообразного форматирования
     * заголовков, событий, разделителей и счетчиков.</p>
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
            
            log.debug("После фильтрации осталось {} событий на неделю для пользователя ID={}", 
                    filteredEvents.size(), user.getId());
            
            if (filteredEvents.isEmpty()) {
                String responseMessage = EventFormatter.formatNoEventsMessage(
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
            messageBuilder.append(EventFormatter.formatCommandHeader("События на неделю", dateRange));
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
            String errorMessage = escape("❌ Произошла ошибка при получении событий на неделю. Попробуйте позже.");
            return errorMessage;
        }
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
