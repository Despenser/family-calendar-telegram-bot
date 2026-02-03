package ru.golubyatnikov.family.calendar.bot.service.event;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.EventHistory;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.service.reminder.ReminderService;
import ru.golubyatnikov.family.calendar.bot.service.telegram.TelegramMessageService;

import java.time.LocalDateTime;

/**
 * Сервис для операций удаления и восстановления событий.
 * 
 * <p>Предоставляет методы для мягкого удаления событий (перемещение в корзину),
 * завершения событий и восстановления удаленных событий. Использует событийную
 * архитектуру для уведомления других компонентов системы.</p>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-01
 */
@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class EventDeletionService {
    
    private final EventRepository eventRepository;
    private final EventHistoryService eventHistoryService;
    private final TelegramMessageService telegramMessageService;
    private final ReminderService reminderService;
    private final EventNotificationService eventNotificationService;
    
    /**
     * Перемещает событие в корзину (мягкое удаление).
     * 
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя
     * @return удаленное событие
     */
    @Transactional
    @CacheEvict(value = {"upcomingEvents", "userEvents"}, allEntries = true)
    public Event deleteEvent(Long eventId, Long userId) {
        log.debug("Перемещение события ID={} в корзину пользователем ID={}", eventId, userId);
        
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> {
                log.error("Событие с ID={} не найдено при попытке удаления", eventId);
                return new EventNotFoundException(eventId);
            });
        
        if (!event.belongsToUser(userId)) {
            log.warn("Пользователь ID={} попытался удалить чужое событие ID={} (владелец: ID={})", 
                     userId, eventId, event.getUser().getId());
            throw new UnauthorizedAccessException(
                "Только создатель события может его удалить");
        }
        
        if (event.getMessageId() != null) {
            Long chatId = event.getUser().getTelegramId();
            if (chatId != null) {
                try {
                    telegramMessageService.deleteMessageSilently(chatId, event.getMessageId().intValue());
                    log.debug("Сообщение события удалено при удалении: eventId={}, messageId={}", 
                             eventId, event.getMessageId());
                } catch (Exception e) {
                    log.warn("Не удалось удалить сообщение события при удалении: eventId={}, messageId={}, error={}", 
                             eventId, event.getMessageId(), e.getMessage());
                }
            } else {
                log.warn("Не удалось получить chatId для удаления сообщения события ID={}", eventId);
            }
        }
        
        event.setStatus(Event.EventStatus.DELETED);
        event.setDeletedAt(LocalDateTime.now());
        event.setMessageId(null);
        event.setIsMyEventsHeader(false);
        
        Event deletedEvent = eventRepository.save(event);
        
        log.info("Событие ID={} успешно перемещено в корзину пользователем ID={}", eventId, userId);
        
        eventHistoryService.recordDeletion(eventId, userId);
        
        // Обновляем шапку /my_events после удаления события
        eventNotificationService.updateMyEventsHeaderAfterRemoval(userId);
        
        return deletedEvent;
    }
    
    /**
     * Завершает событие вручную.
     * 
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя
     * @return завершенное событие
     */
    @Transactional
    @CacheEvict(value = {"upcomingEvents", "userEvents"}, allEntries = true)
    public Event completeEvent(
            @NotNull(message = "eventId не может быть null") Long eventId,
            @NotNull(message = "userId не может быть null") Long userId) {
        log.debug("Завершение события ID={} пользователем ID={}", eventId, userId);
        
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> {
                log.error("Событие с ID={} не найдено при попытке завершения", eventId);
                return new EventNotFoundException(eventId);
            });
        
        if (!event.belongsToUser(userId)) {
            log.warn("Пользователь ID={} попытался завершить чужое событие ID={} (владелец: ID={})", 
                     userId, eventId, event.getUser().getId());
            throw new UnauthorizedAccessException(
                "Только создатель события может его завершить");
        }
        
        if (event.getStatus() != Event.EventStatus.ACTIVE) {
            log.warn("Попытка завершить неактивное событие ID={} (статус: {})", 
                     eventId, event.getStatus());
            throw new IllegalStateException(
                String.format("Можно завершить только активное событие (текущий статус: %s)", 
                             event.getStatus()));
        }
        
        if (event.getMessageId() != null) {
            Long chatId = event.getUser().getTelegramId();
            if (chatId != null) {
                try {
                    telegramMessageService.deleteMessageSilently(chatId, event.getMessageId().intValue());
                    log.debug("Сообщение события удалено при завершении: eventId={}, messageId={}", 
                             eventId, event.getMessageId());
                } catch (Exception e) {
                    log.warn("Не удалось удалить сообщение события при завершении: eventId={}, messageId={}, error={}", 
                             eventId, event.getMessageId(), e.getMessage());
                }
            } else {
                log.warn("Не удалось получить chatId для удаления сообщения события ID={}", eventId);
            }
        }
        
        event.setStatus(Event.EventStatus.COMPLETED);
        event.setCompletedAt(LocalDateTime.now());
        event.setMessageId(null);
        event.setIsMyEventsHeader(false);
        
        Event completedEvent = eventRepository.save(event);
        log.info("Событие ID={} успешно завершено вручную пользователем ID={}", eventId, userId);
        
        eventHistoryService.recordChange(
            eventId,
            userId,
            EventHistory.ActionType.UPDATED,
            "status",
            "ACTIVE",
            "COMPLETED"
        );
        
        handleEventCompletion(eventId);
        
        // Обновляем шапку /my_events после завершения события
        eventNotificationService.updateMyEventsHeaderAfterRemoval(userId);
        
        return completedEvent;
    }
    
    /**
     * Завершает событие вручную без удаления сообщения.
     * 
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя
     * @return завершенное событие с сохраненным messageId
     */
    @Transactional
    @CacheEvict(value = {"upcomingEvents", "userEvents"}, allEntries = true)
    public Event completeEventWithoutDeletion(
            @NotNull(message = "eventId не может быть null") Long eventId,
            @NotNull(message = "userId не может быть null") Long userId) {
        log.debug("Завершение события ID={} без удаления сообщения пользователем ID={}", eventId, userId);
        
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> {
                log.error("Событие с ID={} не найдено при попытке завершения", eventId);
                return new EventNotFoundException(eventId);
            });
        
        if (!event.belongsToUser(userId)) {
            log.warn("Пользователь ID={} попытался завершить чужое событие ID={} (владелец: ID={})", 
                     userId, eventId, event.getUser().getId());
            throw new UnauthorizedAccessException(
                "Только создатель события может его завершить");
        }
        
        if (event.getStatus() != Event.EventStatus.ACTIVE) {
            log.warn("Попытка завершить неактивное событие ID={} (статус: {})", 
                     eventId, event.getStatus());
            throw new IllegalStateException(
                String.format("Можно завершить только активное событие (текущий статус: %s)", 
                             event.getStatus()));
        }
        
        event.setStatus(Event.EventStatus.COMPLETED);
        event.setCompletedAt(LocalDateTime.now());
        event.setIsMyEventsHeader(false);
        
        Event completedEvent = eventRepository.save(event);
        log.info("Событие ID={} успешно завершено без удаления сообщения пользователем ID={}, messageId сохранён: {}", 
                 eventId, userId, event.getMessageId());
        
        eventHistoryService.recordChange(
            eventId,
            userId,
            EventHistory.ActionType.UPDATED,
            "status",
            "ACTIVE",
            "COMPLETED"
        );
        
        handleEventCompletion(eventId);
        
        // Обновляем шапку /my_events после завершения события
        eventNotificationService.updateMyEventsHeaderAfterRemoval(userId);
        
        return completedEvent;
    }
    
    /**
     * Завершает событие вручную без удаления сообщения и без обновления шапки /my_events.
     * 
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя
     * @return завершенное событие с сохраненным messageId
     */
    @Transactional
    @CacheEvict(value = {"upcomingEvents", "userEvents"}, allEntries = true)
    public Event completeEventWithoutHeaderUpdate(
            @NotNull(message = "eventId не может быть null") Long eventId,
            @NotNull(message = "userId не может быть null") Long userId) {
        log.debug("Завершение события ID={} без удаления сообщения и без обновления шапки пользователем ID={}", eventId, userId);
        
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> {
                log.error("Событие с ID={} не найдено при попытке завершения", eventId);
                return new EventNotFoundException(eventId);
            });
        
        if (!event.belongsToUser(userId)) {
            log.warn("Пользователь ID={} попытался завершить чужое событие ID={} (владелец: ID={})", 
                     userId, eventId, event.getUser().getId());
            throw new UnauthorizedAccessException(
                "Только создатель события может его завершить");
        }
        
        if (event.getStatus() != Event.EventStatus.ACTIVE) {
            log.warn("Попытка завершить неактивное событие ID={} (статус: {})", 
                     eventId, event.getStatus());
            throw new IllegalStateException(
                String.format("Можно завершить только активное событие (текущий статус: %s)", 
                             event.getStatus()));
        }
        
        event.setStatus(Event.EventStatus.COMPLETED);
        event.setCompletedAt(LocalDateTime.now());
        event.setIsMyEventsHeader(false);
        
        Event completedEvent = eventRepository.save(event);
        log.info("Событие ID={} успешно завершено без удаления сообщения и без обновления шапки пользователем ID={}, messageId сохранён: {}", 
                 eventId, userId, event.getMessageId());
        
        eventHistoryService.recordChange(
            eventId,
            userId,
            EventHistory.ActionType.UPDATED,
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
     * @return обновленное событие с заметкой
     */
    @Transactional
    @CacheEvict(value = {"upcomingEvents", "userEvents"}, allEntries = true)
    public Event addCompletionNote(Long eventId, Long userId, String note) {
        log.debug("Добавление заметки к завершенному событию ID={} пользователем ID={}", eventId, userId);
        
        if (note == null || note.isBlank()) {
            log.warn("Попытка добавить пустую заметку к событию ID={}", eventId);
            throw new IllegalArgumentException("Заметка не может быть пустой");
        }
        
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> {
                log.error("Событие с ID={} не найдено при попытке добавления заметки", eventId);
                return new EventNotFoundException(eventId);
            });
        
        if (!event.belongsToUser(userId)) {
            log.warn("Пользователь ID={} попытался добавить заметку к чужому событию ID={}", userId, eventId);
            throw new UnauthorizedAccessException(
                "Только создатель события может добавить заметку");
        }
        
        if (!event.isCompleted()) {
            log.warn("Попытка добавить заметку к незавершенному событию ID={}", eventId);
            throw new IllegalStateException("Заметку можно добавить только к завершенному событию");
        }
        
        String oldNote = event.getCompletionNote();
        event.setCompletionNote(note);
        Event updatedEvent = eventRepository.save(event);
        
        log.info("Заметка успешно добавлена к событию ID={} пользователем ID={}", eventId, userId);
        
        eventHistoryService.recordChange(
            eventId,
            userId,
            EventHistory.ActionType.UPDATED,
            "completion_note",
            oldNote,
            note
        );
        
        return updatedEvent;
    }
    
    /**
     * Обрабатывает завершение события.
     * Отмечает все неотправленные напоминания как отправленные.
     * 
     * @param eventId идентификатор события
     */
    private void handleEventCompletion(Long eventId) {
        log.info("Обработка завершения события ID={}", eventId);
        
        try {
            reminderService.markRemindersAsSent(eventId);
            log.info("Напоминания отмечены как отправленные для события ID={}", eventId);
        } catch (Exception e) {
            log.error("Ошибка при отметке напоминаний для события ID={}: {}", 
                     eventId, e.getMessage(), e);
        }
    }
}
