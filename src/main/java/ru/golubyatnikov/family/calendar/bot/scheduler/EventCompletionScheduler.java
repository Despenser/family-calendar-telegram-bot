package ru.golubyatnikov.family.calendar.bot.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.EventHistory;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.service.EventHistoryService;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Планировщик для автоматического завершения истекших событий.
 * 
 * <p>Этот компонент выполняет следующие задачи:</p>
 * <ul>
 *   <li>Поиск событий, время которых истекло</li>
 *   <li>Автоматическое изменение статуса на COMPLETED</li>
 *   <li>Отправка уведомлений создателям о завершении</li>
 *   <li>Предложение добавить заметку о том, как прошло событие</li>
 * </ul>
 * 
 * <p>Планировщик запускается каждые 10 минут для проверки истекших событий.
 * Событие считается истекшим, если его дата и время (или endTime) прошли.</p>
 * 
 * <p><b>Требования:</b> 25.1, 25.2, 25.5</p>
 * 
 * @see Event
 * @see EventRepository
 * @see EventHistoryService
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-08
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventCompletionScheduler {
    
    private final EventRepository eventRepository;
    private final EventHistoryService eventHistoryService;
    private final TelegramMessageService messageService;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    
    /**
     * Автоматически завершает истекшие события.
     * 
     * <p>Выполняется каждые 10 минут по расписанию (fixedDelay = 600000 мс).
     * Находит все активные события, время которых истекло, изменяет их статус
     * на COMPLETED и отправляет уведомления создателям.</p>
     * 
     * <p>Событие считается истекшим если:</p>
     * <ul>
     *   <li>Есть endTime - если текущее время > endTime</li>
     *   <li>Нет endTime, но есть eventTime - если текущее время > eventTime</li>
     *   <li>Нет времени - если текущая дата > eventDate</li>
     * </ul>
     */
    @Scheduled(fixedDelay = 600000) // Каждые 10 минут
    @Transactional
    public void completeExpiredEvents() {
        log.debug("Запуск проверки истекших событий");
        
        try {
            LocalDateTime now = LocalDateTime.now();
            
            // Получаем все активные события, которые истекли
            List<Event> expiredEvents = eventRepository.findExpiredActiveEvents(now);
            
            if (expiredEvents.isEmpty()) {
                log.debug("Истекших событий не найдено");
                return;
            }
            
            log.info("Найдено {} истекших событий для завершения", expiredEvents.size());
            
            int completedCount = 0;
            for (Event event : expiredEvents) {
                try {
                    // Изменяем статус на COMPLETED
                    Event.EventStatus oldStatus = event.getStatus();
                    event.setStatus(Event.EventStatus.COMPLETED);
                    event.setCompletedAt(now);
                    
                    eventRepository.save(event);
                    
                    // Записываем в историю
                    eventHistoryService.recordChange(
                        event.getId(),
                        event.getUser().getId(),
                        EventHistory.ActionType.UPDATED,
                        "status",
                        oldStatus.name(),
                        Event.EventStatus.COMPLETED.name()
                    );
                    
                    // Отправляем уведомление создателю
                    sendCompletionNotification(event);
                    
                    completedCount++;
                    log.debug("Событие ID={} автоматически завершено", event.getId());
                    
                } catch (Exception e) {
                    log.error("Ошибка при завершении события ID={}: {}", 
                             event.getId(), e.getMessage(), e);
                }
            }
            
            log.info("Автоматическое завершение событий выполнено: {} из {} событий завершено", 
                     completedCount, expiredEvents.size());
            
        } catch (Exception e) {
            log.error("Ошибка при выполнении автоматического завершения событий: {}", 
                     e.getMessage(), e);
        }
    }
    
    /**
     * Отправляет уведомление создателю о завершении события.
     * 
     * <p>Уведомление содержит информацию о событии и inline-кнопку
     * для добавления заметки о том, как прошло событие.</p>
     * 
     * @param event завершенное событие
     */
    private void sendCompletionNotification(Event event) {
        try {
            Long chatId = event.getUser().getTelegramId();
            
            // Формируем сообщение
            StringBuilder message = new StringBuilder();
            message.append("✅ ").append(bold("Событие завершено")).append("\n\n");
            
            // Название события
            message.append("📝 ").append(bold(event.getTitle())).append("\n");
            
            // Дата события
            message.append("📅 ").append(escape(event.getEventDate().format(DATE_FORMATTER)));
            
            // Время события
            if (event.getEventTime() != null) {
                message.append(" в ").append(escape(event.getEventTime().format(TIME_FORMATTER)));
                
                if (event.getEndTime() != null) {
                    message.append(" - ").append(escape(event.getEndTime().format(TIME_FORMATTER)));
                }
            }
            
            message.append("\n\n");
            message.append(italic("Событие автоматически отмечено как завершенное.")).append("\n");
            message.append(italic("Хотите добавить заметку о том, как прошло событие?"));
            
            // Создаем клавиатуру с кнопкой добавления заметки
            InlineKeyboardMarkup keyboard = createCompletionKeyboard(event.getId());
            
            messageService.sendMessage(chatId, message.toString(), keyboard);
            
            log.info("Уведомление о завершении события ID={} отправлено пользователю ID={}", 
                     event.getId(), event.getUser().getId());
            
        } catch (Exception e) {
            log.error("Ошибка при отправке уведомления о завершении события ID={}: {}", 
                     event.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Создает inline-клавиатуру с кнопкой добавления заметки.
     * 
     * @param eventId идентификатор события
     * @return объект InlineKeyboardMarkup с кнопкой
     */
    private InlineKeyboardMarkup createCompletionKeyboard(Long eventId) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        List<InlineKeyboardButton> row = new ArrayList<>();
        
        // Кнопка "Добавить заметку"
        InlineKeyboardButton addNoteButton = new InlineKeyboardButton();
        addNoteButton.setText("📝 Добавить заметку");
        addNoteButton.setCallbackData("add_completion_note_" + eventId);
        row.add(addNoteButton);
        
        keyboard.add(row);
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);
        return markup;
    }
}

