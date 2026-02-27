package ru.golubyatnikov.family.calendar.bot.service.domain.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.enums.ActionType;
import ru.golubyatnikov.family.calendar.bot.model.enums.EventStatus;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.service.domain.reminder.ReminderSchedulingService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import java.time.LocalDateTime;

/**
 * Сервис для операций удаления и восстановления событий.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-01
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EventDeletionService {
    
    private final EventRepository eventRepository;
    private final EventHistoryService eventHistoryService;
    private final TelegramMessageService telegramMessageService;
    private final ReminderSchedulingService reminderSchedulingService;
    
    /**
     * Перемещает событие в корзину.
     *
     * @param eventId идентификатор события
     * @param userId  идентификатор пользователя
     */
    @Transactional
    @CacheEvict(value = {"upcomingEvents", "userEvents", "myEventsPage"}, allEntries = true)
    public void deleteEvent(Long eventId, Long userId) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new EventNotFoundException(eventId));
        
        if (!event.belongsToUser(userId)) {
            throw new UnauthorizedAccessException("Только создатель события может его удалить");
        }
        
        if (event.getMessageId() != null) {
            Long chatId = event.getUser().getTelegramId();
            if (chatId != null) {
                try {
                    telegramMessageService.deleteMessageSilently(chatId, event.getMessageId().intValue());

                } catch (Exception e) {
                    log.warn("Не удалось удалить сообщение события при удалении: eventId={}, messageId={}, error={}", 
                             eventId, event.getMessageId(), e.getMessage());
                }
            }
        }
        
        event.setStatus(EventStatus.DELETED);
        event.setDeletedAt(LocalDateTime.now());
        event.setMessageId(null);
        event.setIsMyEventsHeader(false);
        
        eventRepository.save(event);
        
        eventHistoryService.recordDeletion(eventId, userId);

    }
    
    /**
     * Завершает событие вручную без удаления сообщения и без обновления шапки /my_events.
     * 
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя
     *
     * @return завершенное событие с сохраненным messageId
     */
    @Transactional
    @CacheEvict(value = {"upcomingEvents", "userEvents", "myEventsPage"}, allEntries = true)
    public Event completeEventWithoutHeaderUpdate(Long eventId, Long userId) {
        Event event = getEventAndValidateCompletion(eventId, userId);

        event.setStatus(EventStatus.COMPLETED);
        event.setCompletedAt(LocalDateTime.now());
        event.setIsMyEventsHeader(false);

        Event completedEvent = eventRepository.save(event);

        eventHistoryService.recordChange(
            eventId,
            userId,
            ActionType.UPDATED,
            "status",
            "ACTIVE",
            "COMPLETED"
        );

        handleEventCompletion(eventId);

        return completedEvent;
    }
    
    /**
     * Добавляет заметку к завершенному событию.
     * 
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя
     * @param note текст заметки
     *
     * @return обновленное событие с заметкой
     */
    @Transactional
    @CacheEvict(value = {"upcomingEvents", "userEvents", "myEventsPage"}, allEntries = true)
    public Event addCompletionNote(Long eventId, Long userId, String note) {
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("Заметка не может быть пустой");
        }
        
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        
        if (!event.belongsToUser(userId)) {
            throw new UnauthorizedAccessException("Только создатель события может добавить заметку");
        }
        
        if (!event.isCompleted()) {
            throw new IllegalStateException("Заметку можно добавить только к завершенному событию");
        }
        
        String oldNote = event.getCompletionNote();
        event.setCompletionNote(note);
        Event updatedEvent = eventRepository.save(event);
        
        eventHistoryService.recordChange(
            eventId,
            userId,
            ActionType.UPDATED,
            "completion_note",
            oldNote,
            note
        );
        
        return updatedEvent;
    }
    
    /**
     * Получает событие и проверяет права на его завершение.
     * 
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя
     *
     * @return событие, готовое к завершению
     * @throws EventNotFoundException если событие не найдено
     * @throws UnauthorizedAccessException если нет прав на завершение
     * @throws IllegalStateException если событие не в активном статусе
     */
    private @NonNull Event getEventAndValidateCompletion(Long eventId, Long userId) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new EventNotFoundException(eventId));
        
        if (!event.belongsToUser(userId)) {
            throw new UnauthorizedAccessException("Только создатель события может его завершить");
        }
        
        if (event.getStatus() != EventStatus.ACTIVE) {
            throw new IllegalStateException(
                String.format("Можно завершить только активное событие (текущий статус: %s)", 
                             event.getStatus()));
        }
        
        return event;
    }
    
    /**
     * Обрабатывает завершение события.
     * Отмечает все неотправленные напоминания как отправленные.
     * 
     * @param eventId идентификатор события
     */
    private void handleEventCompletion(Long eventId) {
        try {
            reminderSchedulingService.markRemindersAsSent(eventId);

        } catch (Exception e) {
            log.error("Ошибка при отметке напоминаний для события ID={}: {}", eventId, e.getMessage(), e);
        }
    }
}
