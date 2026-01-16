package ru.golubyatnikov.family.calendar.bot.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис для управления состоянием диалогов пользователей.
 * 
 * <p>Отслеживает различные состояния взаимодействия пользователей с ботом,
 * такие как ожидание поискового запроса, ожидание ввода комментария, редактирование событий и т.д.</p>
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
     * Map для отслеживания пользователей, редактирующих события.
     * Key: userId, Value: EditingContext (eventId, chatId, currentField)
     */
    private final Map<Long, EditingContext> usersEditingEvents = new ConcurrentHashMap<>();
    
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
    
    /**
     * Начинает процесс редактирования события для пользователя.
     * 
     * @param userId идентификатор пользователя
     * @param eventId идентификатор редактируемого события
     * @param chatId идентификатор чата
     */
    public void startEventEditing(Long userId, Long eventId, Long chatId) {
        EditingContext context = new EditingContext(eventId, chatId, null);
        usersEditingEvents.put(userId, context);
        log.info("Пользователь ID={} начал редактирование события ID={}", userId, eventId);
    }
    
    /**
     * Проверяет, редактирует ли пользователь событие в данный момент.
     * 
     * @param userId идентификатор пользователя
     * @return true, если пользователь редактирует событие
     */
    public boolean isEditingEvent(Long userId) {
        return usersEditingEvents.containsKey(userId);
    }
    
    /**
     * Получает контекст редактирования для пользователя.
     * 
     * @param userId идентификатор пользователя
     * @return контекст редактирования или null, если пользователь не редактирует событие
     */
    public EditingContext getEditingContext(Long userId) {
        return usersEditingEvents.get(userId);
    }
    
    /**
     * Устанавливает текущее редактируемое поле для пользователя.
     * 
     * @param userId идентификатор пользователя
     * @param field поле для редактирования
     */
    public void setEditingField(Long userId, EditField field) {
        EditingContext context = usersEditingEvents.get(userId);
        if (context != null) {
            context.setCurrentField(field);
            log.debug("Пользователь ID={} выбрал поле для редактирования: {}", userId, field);
        }
    }
    
    /**
     * Очищает состояние редактирования события для пользователя.
     * 
     * @param userId идентификатор пользователя
     */
    public void clearEventEditing(Long userId) {
        usersEditingEvents.remove(userId);
        log.debug("Состояние редактирования очищено для пользователя ID={}", userId);
    }
    
    /**
     * Контекст редактирования события.
     * Содержит информацию о редактируемом событии, чате и текущем поле.
     */
    @Data
    @AllArgsConstructor
    public static class EditingContext {
        /**
         * Идентификатор редактируемого события
         */
        private Long eventId;
        
        /**
         * Идентификатор чата
         */
        private Long chatId;
        
        /**
         * Текущее редактируемое поле
         */
        private EditField currentField;
    }
    
    /**
     * Поля события, доступные для редактирования.
     */
    public enum EditField {
        /**
         * Название события
         */
        TITLE,
        
        /**
         * Дата события
         */
        DATE,
        
        /**
         * Время события
         */
        TIME,
        
        /**
         * Описание события
         */
        DESCRIPTION
    }
}
