package ru.golubyatnikov.family.calendar.bot.service.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.exception.UserNotFoundException;
import ru.golubyatnikov.family.calendar.bot.model.EventHistory;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.model.ActionType;
import ru.golubyatnikov.family.calendar.bot.repository.EventHistoryRepository;
import ru.golubyatnikov.family.calendar.bot.repository.UserRepository;

import java.util.List;

/**
 * Сервис для управления историей изменений событий.
 * Предоставляет функциональность для записи и получения истории
 * всех действий пользователей с событиями.
 * 
 * <p>Основные функции:</p>
 * <ul>
 *   <li>Запись изменений событий (создание, обновление, удаление, восстановление)</li>
 *   <li>Получение полной истории изменений события</li>
 *   <li>Аудит действий пользователей</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 29.1, 29.2, 29.4</p>
 * 
 * @author Family Calendar Bot
 * @version 1.0
 * @see EventHistory
 * @see EventHistoryRepository
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class EventHistoryService {
    
    private final EventHistoryRepository eventHistoryRepository;
    private final UserRepository userRepository;
    
    /**
     * Записывает изменение события в историю.
     * 
     * <p>Создает запись в истории с информацией о типе действия,
     * пользователе и деталях изменения.</p>
     * 
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя, выполнившего действие
     * @param actionType тип действия (CREATED, UPDATED, DELETED, RESTORED)
     * @param fieldName название измененного поля (для UPDATED, может быть null)
     * @param oldValue старое значение поля (для UPDATED, может быть null)
     * @param newValue новое значение поля (для UPDATED, может быть null)
     * @return созданная запись истории
     * @throws UserNotFoundException если пользователь не найден
     */
    public EventHistory recordChange(Long eventId, Long userId, ActionType actionType,
                                    String fieldName, String oldValue, String newValue) {
        log.debug("Запись изменения события ID {}: actionType={}, userId={}, field={}", 
                  eventId, actionType, userId, fieldName);
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        
        EventHistory history = EventHistory.builder()
            .eventId(eventId)
            .user(user)
            .actionType(actionType)
            .fieldName(fieldName)
            .oldValue(oldValue)
            .newValue(newValue)
            .build();
        
        EventHistory saved = eventHistoryRepository.save(history);
        
        log.info("Изменение события ID {} записано в историю: actionType={}, historyId={}", 
                 eventId, actionType, saved.getId());
        
        return saved;
    }
    
    /**
     * Записывает создание события в историю.
     * 
     * <p>Удобный метод для записи действия CREATED без дополнительных параметров.</p>
     * 
     * @param eventId идентификатор созданного события
     * @param userId идентификатор пользователя, создавшего событие
     * @return созданная запись истории
     * @throws UserNotFoundException если пользователь не найден
     */
    public EventHistory recordCreation(Long eventId, Long userId) {
        return recordChange(eventId, userId, ActionType.CREATED, null, null, null);
    }
    
    /**
     * Записывает обновление поля события в историю.
     * 
     * <p>Удобный метод для записи действия UPDATED с деталями изменения.</p>
     * 
     * @param eventId идентификатор обновленного события
     * @param userId идентификатор пользователя, обновившего событие
     * @param fieldName название измененного поля
     * @param oldValue старое значение поля
     * @param newValue новое значение поля
     * @return созданная запись истории
     * @throws UserNotFoundException если пользователь не найден
     */
    public EventHistory recordUpdate(Long eventId, Long userId, String fieldName, 
                                    String oldValue, String newValue) {
        return recordChange(eventId, userId, ActionType.UPDATED, 
                          fieldName, oldValue, newValue);
    }
    
    /**
     * Записывает удаление события в историю.
     * 
     * <p>Удобный метод для записи действия DELETED без дополнительных параметров.</p>
     * 
     * @param eventId идентификатор удаленного события
     * @param userId идентификатор пользователя, удалившего событие
     * @return созданная запись истории
     * @throws UserNotFoundException если пользователь не найден
     */
    public EventHistory recordDeletion(Long eventId, Long userId) {
        return recordChange(eventId, userId, ActionType.DELETED, null, null, null);
    }
    
    /**
     * Записывает восстановление события из корзины в историю.
     * 
     * <p>Удобный метод для записи действия RESTORED без дополнительных параметров.</p>
     * 
     * @param eventId идентификатор восстановленного события
     * @param userId идентификатор пользователя, восстановившего событие
     * @return созданная запись истории
     * @throws UserNotFoundException если пользователь не найден
     */
    public EventHistory recordRestoration(Long eventId, Long userId) {
        return recordChange(eventId, userId, ActionType.RESTORED, null, null, null);
    }
    
    /**
     * Получает полную историю изменений события.
     * 
     * <p>Возвращает все записи истории для события, отсортированные
     * по дате изменения (от новых к старым).</p>
     * 
     * @param eventId идентификатор события
     * @return список записей истории, отсортированный по дате (новые первыми)
     */
    @Transactional(readOnly = true)
    public List<EventHistory> getEventHistory(Long eventId) {
        log.debug("Получение истории изменений для события ID {}", eventId);
        
        List<EventHistory> history = eventHistoryRepository.findByEventIdOrderByChangedAtDesc(eventId);
        
        log.debug("Найдено {} записей истории для события ID {}", history.size(), eventId);
        return history;
    }
}
