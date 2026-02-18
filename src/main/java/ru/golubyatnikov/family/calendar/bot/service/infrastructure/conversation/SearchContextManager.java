package ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.model.entity.ConversationState;
import ru.golubyatnikov.family.calendar.bot.model.context.SearchQueryContext;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.repository.ConversationStateRepository;
import ru.golubyatnikov.family.calendar.bot.repository.UserRepository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер для управления контекстом поиска событий.
 * Отвечает за хранение и управление состоянием поиска событий пользователями.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-13
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SearchContextManager {
    
    private final ConversationStateRepository conversationStateRepository;
    private final UserRepository userRepository;
    
    /**
     * Map для отслеживания пользователей, ожидающих ввода поискового запроса.
     * Key: userId, Value: chatId
     */
    private final Map<Long, Long> usersAwaitingSearchQuery = new ConcurrentHashMap<>();
    
    /**
     * Устанавливает состояние ожидания поискового запроса для пользователя.
     *
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для редактирования
     *
     * @throws IllegalArgumentException если userId, chatId или messageId равны null
     */
    @Transactional
    public void setAwaitingSearchQuery(Long userId, Long chatId, Integer messageId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId не может быть null");
        }
        if (chatId == null) {
            throw new IllegalArgumentException("chatId не может быть null");
        }
        if (messageId == null) {
            throw new IllegalArgumentException("messageId не может быть null");
        }
        
        log.debug("Установка состояния ожидания поискового запроса для пользователя ID={}, чата ID={}, сообщения ID={}", 
                userId, chatId, messageId);

        usersAwaitingSearchQuery.put(userId, chatId);
        
        // Получаем или создаем состояние диалога
        ConversationState state = conversationStateRepository.findByUserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new IllegalArgumentException("Пользователь с ID=" + userId + " не найден"));

                    return ConversationState.builder()
                            .user(user)
                            .build();
                });
        
        // Сохраняем контекст поиска
        state.setSearchChatId(chatId);
        state.setSearchMessageId(messageId);
        conversationStateRepository.save(state);
        
        log.info("Пользователь ID={} переведен в режим ожидания поискового запроса, messageId={}", userId, messageId);
    }
    
    /**
     * Проверяет, ожидает ли пользователь ввода поискового запроса.
     * 
     * @param userId идентификатор пользователя
     * @return true, если пользователь ожидает ввода поискового запроса
     */
    public boolean isAwaitingSearchQuery(Long userId) {
        return usersAwaitingSearchQuery.containsKey(userId);
    }

    /**
     * Получает сохраненный контекст поискового запроса для пользователя.
     *
     * @param userId идентификатор пользователя
     *
     * @return SearchQueryContext с chatId и messageId, или null, если контекст не найден
     * @throws IllegalArgumentException если userId равен null
     */
    @Transactional(readOnly = true)
    public SearchQueryContext getSearchQueryContext(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId не может быть null");
        }
        
        log.debug("Получение контекста поискового запроса для пользователя ID={}", userId);
        
        return conversationStateRepository.findByUserId(userId)
                .filter(ConversationState::hasSearchContext)
                .map(state -> {
                    SearchQueryContext context = new SearchQueryContext(
                            state.getSearchChatId(),
                            state.getSearchMessageId()
                    );
                    log.debug("Найден контекст поискового запроса для пользователя ID={}: chatId={}, messageId={}", 
                            userId, context.getChatId(), context.getMessageId());
                    return context;
                })
                .orElseGet(() -> {
                    log.debug("Контекст поискового запроса не найден для пользователя ID={}", userId);
                    return null;
                });
    }
    
    /**
     * Очищает состояние ожидания поискового запроса для пользователя.
     *
     * @param userId идентификатор пользователя
     * @throws IllegalArgumentException если userId равен null
     */
    @Transactional
    public void clearAwaitingSearchQuery(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId не может быть null");
        }
        
        log.debug("Очистка состояния ожидания поискового запроса для пользователя ID={}", userId);

        usersAwaitingSearchQuery.remove(userId);
        
        // Очищаем из базы данных
        conversationStateRepository.findByUserId(userId)
                .ifPresent(state -> {
                    if (state.hasSearchContext()) {
                        state.clearSearchContext();
                        conversationStateRepository.save(state);
                        log.info("Контекст поиска очищен для пользователя ID={}", userId);

                    } else {
                        log.debug("Контекст поиска не найден в БД для пользователя ID={}, очистка не требуется", userId);
                    }
                });
        
        log.debug("Состояние ожидания поискового запроса очищено для пользователя ID={}", userId);
    }
}
