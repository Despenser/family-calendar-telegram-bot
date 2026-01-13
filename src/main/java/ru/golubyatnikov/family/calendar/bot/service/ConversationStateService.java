package ru.golubyatnikov.family.calendar.bot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис для управления состоянием диалогов пользователей.
 * 
 * <p>Отслеживает различные состояния взаимодействия пользователей с ботом,
 * такие как ожидание поискового запроса, ожидание ввода комментария и т.д.</p>
 * 
 * <p>Использует потокобезопасную ConcurrentHashMap для хранения состояний.</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-11
 */
@Service
@Slf4j
public class ConversationStateService {
    
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
     */
    public void setAwaitingSearchQuery(Long userId, Long chatId) {
        usersAwaitingSearchQuery.put(userId, chatId);
        log.info("Пользователь ID={} переведен в режим ожидания поискового запроса", userId);
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
     * Получает chatId для пользователя, ожидающего ввода поискового запроса.
     * 
     * @param userId идентификатор пользователя
     * @return chatId или null, если пользователь не ожидает ввода
     */
    public Long getSearchQueryChatId(Long userId) {
        return usersAwaitingSearchQuery.get(userId);
    }
    
    /**
     * Очищает состояние ожидания поискового запроса для пользователя.
     * 
     * @param userId идентификатор пользователя
     */
    public void clearAwaitingSearchQuery(Long userId) {
        usersAwaitingSearchQuery.remove(userId);
        log.debug("Состояние ожидания поискового запроса очищено для пользователя ID={}", userId);
    }
}
