package ru.golubyatnikov.family.calendar.bot.service.domain.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.exception.UserNotFoundException;
import ru.golubyatnikov.family.calendar.bot.model.entity.EventHistory;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.model.enums.ActionType;
import ru.golubyatnikov.family.calendar.bot.repository.EventHistoryRepository;
import ru.golubyatnikov.family.calendar.bot.repository.UserRepository;

/**
 * Сервис для управления историей изменений событий.
 * Предоставляет функциональность для записи и получения истории
 * всех действий пользователей с событиями.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-01
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EventHistoryService {
    
    private final EventHistoryRepository eventHistoryRepository;
    private final UserRepository userRepository;
    
    /**
     * Записывает изменение события в историю.
     *
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя, выполнившего действие
     * @param actionType тип действия (CREATED, UPDATED, DELETED, RESTORED)
     * @param fieldName название измененного поля (для UPDATED, может быть null)
     * @param oldValue старое значение поля (для UPDATED, может быть null)
     * @param newValue новое значение поля (для UPDATED, может быть null)
     *
     * @throws UserNotFoundException если пользователь не найден
     */
    @Transactional
    public void recordChange(Long eventId,
                             Long userId,
                             ActionType actionType,
                             String fieldName,
                             String oldValue,
                             String newValue) {

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
        
        eventHistoryRepository.save(history);
        }
    
    /**
     * Записывает удаление события в историю.
     *
     * @param eventId идентификатор удаленного события
     * @param userId  идентификатор пользователя, удалившего событие
     *
     * @throws UserNotFoundException если пользователь не найден
     */
    @Transactional
    public void recordDeletion(Long eventId, Long userId) {
        recordChange(eventId, userId, ActionType.DELETED, null, null, null);
    }
}
