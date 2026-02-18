package ru.golubyatnikov.family.calendar.bot.service.domain.trash;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.enums.EventStatus;
import ru.golubyatnikov.family.calendar.bot.model.enums.ActionType;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventHistoryService;
import ru.golubyatnikov.family.calendar.bot.service.domain.reminder.ReminderSchedulingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.message.TrashMessageService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.TrashHeaderFormattingService;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Сервис для управления корзиной удаленных событий.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrashService {

    private static final int TRASH_RETENTION_DAYS = 30;
    
    private final EventRepository eventRepository;
    private final EventHistoryService eventHistoryService;
    private final ReminderSchedulingService reminderSchedulingService;
    private final TrashMessageService trashMessageService;
    private final TrashHeaderFormattingService trashHeaderFormattingService;
    
    /**
     * Получает список удаленных событий пользователя.
     *
     * @param userId идентификатор пользователя
     * @return список удаленных событий
     * @throws IllegalArgumentException если userId равен null
     */
    @Transactional(readOnly = true)
    public List<Event> getUserTrash(Long userId) {
        if (userId == null) {
            log.error("Попытка получить корзину с userId=null");
            throw new IllegalArgumentException("ID пользователя не может быть null");
        }
        
        log.debug("Получение корзины для пользователя ID={}", userId);
        
        List<Event> trashedEvents = eventRepository.findByUserIdAndStatusOrderByDeletedAtDesc(
            userId, 
            EventStatus.DELETED
        );
        
        log.info("Получено {} удаленных событий для пользователя ID={}", 
                 trashedEvents.size(), userId);
        
        return trashedEvents;
    }
    
    /**
     * Восстанавливает событие из корзины.
     * 
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя, восстанавливающего событие
     * @return восстановленное событие
     * @throws EventNotFoundException если событие не найдено
     * @throws UnauthorizedAccessException если пользователь не является создателем события
     * @throws IllegalStateException если событие не находится в корзине
     */
    @Transactional
    public Event restoreEvent(Long eventId, Long userId) {
        validateIds(eventId, userId, "восстановить");
        
        log.debug("Восстановление события ID={} пользователем ID={}", eventId, userId);
        
        Event event = findEventById(eventId);
        validateEventOwnership(event, userId, "восстановить");
        validateEventInTrash(event, "восстановить");
        
        // Удаляем старое сообщение события
        trashMessageService.deleteEventMessage(event);
        
        // Восстанавливаем событие
        restoreEventState(event);
        Event restoredEvent = eventRepository.save(event);
        
        // Пересчитываем напоминания
        recalculateRemindersIfNeeded(event);
        
        // Записываем в историю
        recordRestoreAction(eventId, userId);
        
        log.info("Событие ID={} успешно восстановлено пользователем ID={}", eventId, userId);
        
        // Обновляем шапки после восстановления
        updateHeadersAfterRestore(userId);
        
        return restoredEvent;
    }
    
    /**
     * Восстанавливает состояние события.
     */
    private void restoreEventState(@NonNull Event event) {
        event.setStatus(EventStatus.ACTIVE);
        event.setDeletedAt(null);
        event.setMessageId(null);
        event.setIsMyEventsHeader(false);
        event.setIsTrashHeader(false);
    }
    
    /**
     * Пересчитывает напоминания для события, если необходимо.
     */
    private void recalculateRemindersIfNeeded(@NonNull Event event) {
        if (event.getEventDate() != null && event.getEventTime() != null) {
            try {
                reminderSchedulingService.recalculateReminders(event.getId());
                log.debug("Напоминания пересчитаны для восстановленного события ID={}", event.getId());
            } catch (Exception e) {
                log.warn("Не удалось пересчитать напоминания для события ID={}: {}", 
                        event.getId(), e.getMessage());
            }
        }
    }
    
    /**
     * Записывает действие восстановления в историю.
     */
    private void recordRestoreAction(Long eventId, Long userId) {
        eventHistoryService.recordChange(
            eventId,
            userId,
            ActionType.RESTORED,
            "status",
            EventStatus.DELETED.name(),
            EventStatus.ACTIVE.name()
        );
    }
    
    /**
     * Обновляет шапки после восстановления события.
     */
    private void updateHeadersAfterRestore(Long userId) {
        trashHeaderFormattingService.updateTrashHeaderAfterRemoval(userId);
        trashHeaderFormattingService.updateMyEventsHeaderCount(userId);
    }
    
    /**
     * Окончательно удаляет событие из системы.
     * 
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя, удаляющего событие
     *
     * @throws EventNotFoundException если событие не найдено
     * @throws UnauthorizedAccessException если пользователь не является создателем события
     * @throws IllegalStateException если событие не находится в корзине
     */
    @Transactional
    public void permanentlyDelete(Long eventId, Long userId) {
        validateIds(eventId, userId, "окончательно удалить");
        
        log.debug("Окончательное удаление события ID={} пользователем ID={}", eventId, userId);
        
        Event event = findEventById(eventId);
        validateEventOwnership(event, userId, "окончательно удалить");
        validateEventInTrash(event, "окончательно удалить");
        
        // Удаляем сообщение события
        trashMessageService.deleteEventMessage(event);
        
        // Окончательное удаление
        eventRepository.delete(event);
        
        log.info("Событие ID={} окончательно удалено пользователем ID={}", eventId, userId);
        
        // Обновляем шапку корзины после удаления
        trashHeaderFormattingService.updateTrashHeaderAfterRemoval(userId);
    }
    
    /**
     * Валидирует идентификаторы события и пользователя.
     */
    private void validateIds(Long eventId, Long userId, String action) {
        if (eventId == null || userId == null) {
            log.error("Попытка {} событие с eventId={} или userId=null", action, eventId);
            throw new IllegalArgumentException("ID события и пользователя не могут быть null");
        }
    }
    
    /**
     * Находит событие по идентификатору.
     */
    private Event findEventById(Long eventId) {
        return eventRepository.findById(eventId).orElseThrow(() -> {
                log.error("Событие ID={} не найдено", eventId);
                return new EventNotFoundException("Событие с ID " + eventId + " не найдено");
            });
    }
    
    /**
     * Проверяет права доступа к событию.
     */
    private void validateEventOwnership(@NonNull Event event, Long userId, String action) {
        if (!event.belongsToUser(userId)) {
            log.warn("Пользователь ID={} попытался {} чужое событие ID={}", 
                     userId, action, event.getId());

            throw new UnauthorizedAccessException(
                "Только создатель события может " + action + " его"
            );
        }
    }
    
    /**
     * Проверяет, что событие находится в корзине.
     */
    private void validateEventInTrash(@NonNull Event event, String action) {
        if (event.getStatus() != EventStatus.DELETED) {
            log.error("Попытка {} событие ID={} со статусом {}", 
                     action, event.getId(), event.getStatus());

            throw new IllegalStateException(
                "Можно " + action + " только события из корзины (текущий статус: " + 
                event.getStatus() + ")"
            );
        }
    }
    
    /**
     * Автоматически очищает корзину от старых событий.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupOldTrash() {
        log.info("Запуск автоматической очистки корзины");
        
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(TRASH_RETENTION_DAYS);
        log.debug("Поиск событий в корзине старше {}", cutoffDate);
        
        List<Event> oldEvents = eventRepository.findByStatusAndDeletedAtBefore(
            EventStatus.DELETED,
            cutoffDate
        );
        
        if (oldEvents.isEmpty()) {
            log.info("Старых событий для удаления не найдено");
            return;
        }
        
        log.info("Найдено {} старых событий для удаления", oldEvents.size());
        
        int deletedCount = deleteOldEvents(oldEvents);
        
        log.info("Автоматическая очистка корзины завершена: удалено {} из {} событий", 
                 deletedCount, oldEvents.size());
    }
    
    /**
     * Удаляет старые события из корзины.
     */
    private int deleteOldEvents(@NonNull List<Event> oldEvents) {
        int deletedCount = 0;
        
        for (Event event : oldEvents) {
            try {
                trashMessageService.deleteEventMessage(event);
                eventRepository.delete(event);
                deletedCount++;
                
                log.debug("Событие ID={} окончательно удалено (в корзине с {})", 
                         event.getId(), event.getDeletedAt());

            } catch (Exception e) {
                log.error("Ошибка при удалении события ID={}: {}", 
                         event.getId(), e.getMessage(), e);
            }
        }
        
        return deletedCount;
    }
}
