package ru.golubyatnikov.family.calendar.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.EventHistory;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Сервис для управления корзиной удаленных событий.
 * 
 * <p>Этот сервис предоставляет функциональность для работы с удаленными событиями:</p>
 * <ul>
 *   <li>Получение списка удаленных событий пользователя</li>
 *   <li>Восстановление событий из корзины</li>
 *   <li>Окончательное удаление событий</li>
 *   <li>Автоматическая очистка старых событий (старше 30 дней)</li>
 * </ul>
 * 
 * <p>События в корзине хранятся 30 дней, после чего автоматически удаляются
 * scheduled задачей, которая выполняется каждый день в 2:00.</p>
 * 
 * <p><b>Требования:</b> 19.1, 19.2, 19.4, 19.5, 19.6</p>
 * 
 * @see Event
 * @see EventRepository
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-08
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrashService {
    
    private final EventRepository eventRepository;
    private final EventHistoryService eventHistoryService;
    private final ReminderService reminderService;
    
    private static final int TRASH_RETENTION_DAYS = 30;
    
    /**
     * Получает список удаленных событий пользователя.
     * 
     * <p>Возвращает события со статусом DELETED, отсортированные по дате удаления
     * (от новых к старым).</p>
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
            Event.EventStatus.DELETED
        );
        
        log.info("Получено {} удаленных событий для пользователя ID={}", 
                 trashedEvents.size(), userId);
        
        return trashedEvents;
    }
    
    /**
     * Восстанавливает событие из корзины.
     * 
     * <p>Изменяет статус события с DELETED на ACTIVE и очищает дату удаления.
     * Записывает действие в историю изменений.</p>
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
        if (eventId == null || userId == null) {
            log.error("Попытка восстановить событие с eventId={} или userId=null", eventId);
            throw new IllegalArgumentException("ID события и пользователя не могут быть null");
        }
        
        log.debug("Восстановление события ID={} пользователем ID={}", eventId, userId);
        
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> {
                log.error("Событие ID={} не найдено при попытке восстановления", eventId);
                return new EventNotFoundException("Событие с ID " + eventId + " не найдено");
            });
        
        // Проверка прав доступа
        if (!event.belongsToUser(userId)) {
            log.warn("Пользователь ID={} попытался восстановить чужое событие ID={}", 
                     userId, eventId);
            throw new UnauthorizedAccessException(
                "Только создатель события может восстановить его из корзины"
            );
        }
        
        // Проверка, что событие находится в корзине
        if (event.getStatus() != Event.EventStatus.DELETED) {
            log.error("Попытка восстановить событие ID={} со статусом {}", 
                     eventId, event.getStatus());
            throw new IllegalStateException(
                "Событие не находится в корзине (статус: " + event.getStatus() + ")"
            );
        }
        
        // Восстановление события
        event.setStatus(Event.EventStatus.ACTIVE);
        event.setDeletedAt(null);
        
        Event restoredEvent = eventRepository.save(event);
        
        // Пересчет напоминаний при восстановлении из корзины (Требование 9.4)
        if (event.getEventDate() != null && event.getEventTime() != null) {
            try {
                reminderService.recalculateReminders(eventId);
                log.debug("Напоминания пересчитаны для восстановленного события ID={}", eventId);
            } catch (Exception e) {
                log.warn("Не удалось пересчитать напоминания для восстановленного события ID={}: {}", 
                        eventId, e.getMessage());
                // Не прерываем восстановление события из-за ошибки пересчета напоминаний
            }
        }
        
        // Запись в историю
        eventHistoryService.recordChange(
            eventId,
            userId,
            EventHistory.ActionType.RESTORED,
            "status",
            Event.EventStatus.DELETED.name(),
            Event.EventStatus.ACTIVE.name()
        );
        
        log.info("Событие ID={} успешно восстановлено пользователем ID={}", eventId, userId);
        
        return restoredEvent;
    }
    
    /**
     * Окончательно удаляет событие из системы.
     * 
     * <p>Физически удаляет событие из базы данных. Это действие необратимо.
     * Событие должно находиться в корзине (статус DELETED).</p>
     * 
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя, удаляющего событие
     * @throws EventNotFoundException если событие не найдено
     * @throws UnauthorizedAccessException если пользователь не является создателем события
     * @throws IllegalStateException если событие не находится в корзине
     */
    @Transactional
    public void permanentlyDelete(Long eventId, Long userId) {
        if (eventId == null || userId == null) {
            log.error("Попытка окончательно удалить событие с eventId={} или userId=null", eventId);
            throw new IllegalArgumentException("ID события и пользователя не могут быть null");
        }
        
        log.debug("Окончательное удаление события ID={} пользователем ID={}", eventId, userId);
        
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> {
                log.error("Событие ID={} не найдено при попытке окончательного удаления", eventId);
                return new EventNotFoundException("Событие с ID " + eventId + " не найдено");
            });
        
        // Проверка прав доступа
        if (!event.belongsToUser(userId)) {
            log.warn("Пользователь ID={} попытался окончательно удалить чужое событие ID={}", 
                     userId, eventId);
            throw new UnauthorizedAccessException(
                "Только создатель события может окончательно удалить его"
            );
        }
        
        // Проверка, что событие находится в корзине
        if (event.getStatus() != Event.EventStatus.DELETED) {
            log.error("Попытка окончательно удалить событие ID={} со статусом {}", 
                     eventId, event.getStatus());
            throw new IllegalStateException(
                "Можно окончательно удалить только события из корзины (текущий статус: " + 
                event.getStatus() + ")"
            );
        }
        
        // Окончательное удаление
        eventRepository.delete(event);
        
        log.info("Событие ID={} окончательно удалено пользователем ID={}", eventId, userId);
    }
    
    /**
     * Автоматически очищает корзину от старых событий.
     * 
     * <p>Выполняется каждый день в 2:00 по расписанию.
     * Удаляет события, которые находятся в корзине более 30 дней.</p>
     * 
     * <p>Использует cron выражение: "0 0 2 * * ?" (каждый день в 2:00)</p>
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupOldTrash() {
        log.info("Запуск автоматической очистки корзины");
        
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(TRASH_RETENTION_DAYS);
        
        log.debug("Поиск событий в корзине старше {}", cutoffDate);
        
        List<Event> oldEvents = eventRepository.findByStatusAndDeletedAtBefore(
            Event.EventStatus.DELETED,
            cutoffDate
        );
        
        if (oldEvents.isEmpty()) {
            log.info("Старых событий для удаления не найдено");
            return;
        }
        
        log.info("Найдено {} старых событий для удаления", oldEvents.size());
        
        int deletedCount = 0;
        for (Event event : oldEvents) {
            try {
                eventRepository.delete(event);
                deletedCount++;
                log.debug("Событие ID={} окончательно удалено (в корзине с {})", 
                         event.getId(), event.getDeletedAt());
            } catch (Exception e) {
                log.error("Ошибка при удалении события ID={}: {}", 
                         event.getId(), e.getMessage(), e);
            }
        }
        
        log.info("Автоматическая очистка корзины завершена: удалено {} из {} событий", 
                 deletedCount, oldEvents.size());
    }
}

