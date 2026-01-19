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
     * Map для отслеживания пользователей, добавляющих заметку к завершенному событию.
     * Key: userId, Value: CompletionNoteContext (eventId, chatId)
     */
    private final Map<Long, CompletionNoteContext> usersAwaitingCompletionNote = new ConcurrentHashMap<>();
    
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
        EditingContext context = new EditingContext(eventId, chatId, null, null);
        usersEditingEvents.put(userId, context);
        log.info("Пользователь ID={} начал редактирование события ID={}", userId, eventId);
    }
    
    /**
     * Начинает процесс редактирования события для пользователя с сохранением messageId.
     * 
     * @param userId идентификатор пользователя
     * @param eventId идентификатор редактируемого события
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для редактирования
     */
    public void startEventEditing(Long userId, Long eventId, Long chatId, Integer messageId) {
        EditingContext context = new EditingContext(eventId, chatId, null, messageId);
        usersEditingEvents.put(userId, context);
        log.info("Пользователь ID={} начал редактирование события ID={} в сообщении ID={}", 
                userId, eventId, messageId);
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
     * Получает messageId для текущего редактирования.
     * 
     * @param userId идентификатор пользователя
     * @return messageId или null, если пользователь не редактирует событие
     */
    public Integer getEditingMessageId(Long userId) {
        EditingContext context = usersEditingEvents.get(userId);
        return context != null ? context.getMessageId() : null;
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
     * Устанавливает состояние ожидания заметки к завершенному событию.
     * 
     * @param userId идентификатор пользователя
     * @param eventId идентификатор завершенного события
     * @param chatId идентификатор чата
     */
    public void setAwaitingCompletionNote(Long userId, Long eventId, Long chatId) {
        CompletionNoteContext context = new CompletionNoteContext(eventId, chatId);
        usersAwaitingCompletionNote.put(userId, context);
        log.info("Пользователь ID={} переведен в режим ожидания заметки для события ID={}", userId, eventId);
    }
    
    /**
     * Проверяет, ожидает ли пользователь ввода заметки к завершенному событию.
     * 
     * @param userId идентификатор пользователя
     * @return true, если пользователь ожидает ввода заметки
     */
    public boolean isAwaitingCompletionNote(Long userId) {
        return usersAwaitingCompletionNote.containsKey(userId);
    }
    
    /**
     * Получает контекст добавления заметки для пользователя.
     * 
     * @param userId идентификатор пользователя
     * @return контекст добавления заметки или null, если пользователь не ожидает ввода
     */
    public CompletionNoteContext getCompletionNoteContext(Long userId) {
        return usersAwaitingCompletionNote.get(userId);
    }
    
    /**
     * Очищает состояние ожидания заметки к завершенному событию.
     * 
     * @param userId идентификатор пользователя
     */
    public void clearAwaitingCompletionNote(Long userId) {
        usersAwaitingCompletionNote.remove(userId);
        log.debug("Состояние ожидания заметки очищено для пользователя ID={}", userId);
    }
    
    /**
     * Контекст редактирования события.
     * Содержит информацию о редактируемом событии, чате, текущем поле и сообщении.
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
        
        /**
         * Идентификатор сообщения, в котором происходит редактирование.
         * Используется для обновления того же сообщения при изменениях.
         */
        private Integer messageId;
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
    
    /**
     * Контекст добавления заметки к завершенному событию.
     * Содержит информацию о событии и чате.
     */
    @Data
    @AllArgsConstructor
    public static class CompletionNoteContext {
        /**
         * Идентификатор завершенного события
         */
        private Long eventId;
        
        /**
         * Идентификатор чата
         */
        private Long chatId;
    }
}
