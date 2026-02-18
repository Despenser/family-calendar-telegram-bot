package ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.model.entity.ConversationState;
import ru.golubyatnikov.family.calendar.bot.model.context.EventHeaderContext;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.repository.ConversationStateRepository;
import ru.golubyatnikov.family.calendar.bot.repository.UserRepository;

/**
 * Менеджер для управления контекстом шапки события.
 * Отвечает за хранение и управление информацией о шапке "Мои события".
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-13
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EventHeaderContextManager {
    
    private final ConversationStateRepository conversationStateRepository;
    private final UserRepository userRepository;
    
    /**
     * Сохраняет контекст шапки события для пользователя.
     *
     * @param userId идентификатор пользователя
     * @param hasMyEventsHeader флаг наличия шапки "Мои события"
     * @param eventCount количество событий пользователя для формирования шапки
     *
     * @throws IllegalArgumentException если userId равен null
     */
    @Transactional
    public void saveEventHeaderContext(Long userId, boolean hasMyEventsHeader, int eventCount) {
        if (userId == null) {
            throw new IllegalArgumentException("userId не может быть null");
        }
        
        try {
            // Получаем или создаем состояние диалога
            ConversationState state = conversationStateRepository.findByUserId(userId)
                    .orElseGet(() -> {
                        User user = userRepository.findById(userId)
                                .orElseThrow(() -> new IllegalArgumentException("Пользователь с ID=" + userId + " не найден"));

                        return ConversationState.builder()
                                .user(user)
                                .build();
                    });
            
            // Сохраняем контекст шапки
            state.setEventHasMyEventsHeader(hasMyEventsHeader);
            state.setEventCountForHeader(eventCount);
            conversationStateRepository.save(state);
            
        } catch (Exception e) {
            log.error("Ошибка при сохранении контекста шапки: userId={}, error={}", userId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Получает сохраненный контекст шапки события для пользователя.
     *
     * @param userId идентификатор пользователя
     *
     * @return EventHeaderContext с флагом hasMyEventsHeader и eventCount, или null если контекст не найден
     * @throws IllegalArgumentException если userId равен null
     */
    @Transactional(readOnly = true)
    public EventHeaderContext getEventHeaderContext(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId не может быть null");
        }
        
        return conversationStateRepository.findByUserId(userId)
                .filter(ConversationState::hasEventHeaderContext)
                .map(state -> new EventHeaderContext(
                        state.getEventHasMyEventsHeader(),
                        state.getEventCountForHeader()
                ))
                .orElse(null);
    }
}
