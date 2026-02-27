package ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.golubyatnikov.family.calendar.bot.model.context.*;
import ru.golubyatnikov.family.calendar.bot.model.enums.EditField;
import java.time.LocalDate;

/**
 * Сервис для управления состоянием диалогов пользователей.
 * Координирует работу специализированных менеджеров контекстов.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-11
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ConversationStateService {
    
    private final SearchContextManager searchContextManager;
    private final EditingContextManager editingContextManager;
    private final CompletionNoteContextManager completionNoteContextManager;
    private final AttachmentContextManager attachmentContextManager;
    private final EventHeaderContextManager eventHeaderContextManager;
    
    // ==================== Методы для работы с поиском ====================
    
    /**
     * Устанавливает состояние ожидания поискового запроса для пользователя.
     *
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для редактирования
     */
    public void setAwaitingSearchQuery(Long userId, Long chatId, Integer messageId) {
        searchContextManager.setAwaitingSearchQuery(userId, chatId, messageId);
    }
    
    /**
     * Проверяет, ожидает ли пользователь ввода поискового запроса.
     * 
     * @param userId идентификатор пользователя
     * @return true, если пользователь ожидает ввода поискового запроса
     */
    public boolean isAwaitingSearchQuery(Long userId) {
        return searchContextManager.isAwaitingSearchQuery(userId);
    }

    /**
     * Получает сохраненный контекст поискового запроса для пользователя.
     *
     * @param userId идентификатор пользователя
     * @return SearchQueryContext с chatId и messageId, или null, если контекст не найден
     */
    public SearchQueryContext getSearchQueryContext(Long userId) {
        return searchContextManager.getSearchQueryContext(userId);
    }
    
    /**
     * Очищает состояние ожидания поискового запроса для пользователя.
     *
     * @param userId идентификатор пользователя
     */
    public void clearAwaitingSearchQuery(Long userId) {
        searchContextManager.clearAwaitingSearchQuery(userId);
    }

    // ==================== Методы для работы с редактированием ====================
    
    /**
     * Начинает процесс редактирования события для пользователя с сохранением messageId.
     * 
     * @param userId идентификатор пользователя
     * @param eventId идентификатор редактируемого события
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для редактирования
     */
    public void startEventEditing(Long userId, Long eventId, Long chatId, Integer messageId) {
        editingContextManager.startEventEditing(userId, eventId, chatId, messageId);
    }
    
    /**
     * Начинает процесс редактирования события для пользователя с сохранением messageId и страницы /my_events.
     * 
     * @param userId идентификатор пользователя
     * @param eventId идентификатор редактируемого события
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для редактирования
     * @param myEventsPage номер страницы /my_events (null если не из /my_events)
     */
    public void startEventEditing(Long userId, Long eventId, Long chatId, Integer messageId, Integer myEventsPage) {
        editingContextManager.startEventEditing(userId, eventId, chatId, messageId, myEventsPage);
    }
    
    /**
     * Начинает процесс редактирования события для пользователя из календаря.
     * 
     * @param userId идентификатор пользователя
     * @param eventId идентификатор редактируемого события
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для редактирования
     * @param sourceDate дата, с которой началось редактирование (для возврата к списку событий)
     */
    public void startEventEditingFromCalendar(Long userId,
                                              Long eventId,
                                              Long chatId,
                                              Integer messageId,
                                              LocalDate sourceDate) {

        editingContextManager.startEventEditingFromCalendar(userId, eventId, chatId, messageId, sourceDate);
    }
    
    /**
     * Проверяет, редактирует ли пользователь событие в данный момент.
     * 
     * @param userId идентификатор пользователя
     * @return true, если пользователь редактирует событие
     */
    public boolean isEditingEvent(Long userId) {
        return editingContextManager.isEditingEvent(userId);
    }
    
    /**
     * Получает контекст редактирования для пользователя.
     * 
     * @param userId идентификатор пользователя
     * @return контекст редактирования или null, если пользователь не редактирует событие
     */
    public EditingContext getEditingContext(Long userId) {
        return editingContextManager.getEditingContext(userId);
    }

    /**
     * Устанавливает текущее редактируемое поле для пользователя.
     * 
     * @param userId идентификатор пользователя
     * @param field поле для редактирования
     */
    public void setEditingField(Long userId, EditField field) {
        editingContextManager.setEditingField(userId, field);
    }
    
    /**
     * Очищает состояние редактирования события для пользователя.
     * 
     * @param userId идентификатор пользователя
     */
    public void clearEventEditing(Long userId) {
        editingContextManager.clearEventEditing(userId);
    }
    
    // ==================== Методы для работы с заметками к завершенным событиям ====================
    
    /**
     * Устанавливает состояние ожидания заметки к завершенному событию.
     * 
     * @param userId идентификатор пользователя
     * @param eventId идентификатор завершенного события
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для редактирования
     * @param myEventsPage номер страницы /my_events (может быть null)
     */
    public void setAwaitingCompletionNote(Long userId,
                                          Long eventId,
                                          Long chatId,
                                          Integer messageId,
                                          Integer myEventsPage) {

        completionNoteContextManager.setAwaitingCompletionNote(userId, eventId, chatId, messageId, myEventsPage);
    }
    
    /**
     * Проверяет, ожидает ли пользователь ввода заметки к завершенному событию.
     * 
     * @param userId идентификатор пользователя
     * @return true, если пользователь ожидает ввода заметки
     */
    public boolean isAwaitingCompletionNote(Long userId) {
        return completionNoteContextManager.isAwaitingCompletionNote(userId);
    }
    
    /**
     * Получает контекст добавления заметки для пользователя.
     * 
     * @param userId идентификатор пользователя
     * @return контекст добавления заметки или null, если пользователь не ожидает ввода
     */
    public CompletionNoteContext getCompletionNoteContext(Long userId) {
        return completionNoteContextManager.getCompletionNoteContext(userId);
    }
    
    /**
     * Очищает состояние ожидания заметки к завершенному событию.
     * 
     * @param userId идентификатор пользователя
     */
    public void clearAwaitingCompletionNote(Long userId) {
        completionNoteContextManager.clearAwaitingCompletionNote(userId);
    }
    
    // ==================== Методы для работы с вложениями ====================
    
    /**
     * Устанавливает состояние ожидания файла для пользователя с контекстом страницы /my_events.
     * 
     * @param userId идентификатор пользователя
     * @param eventId идентификатор события
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения со списком вложений
     * @param myEventsPage номер страницы /my_events (null если не из /my_events)
     */
    public void setAwaitingFile(Long userId, Long eventId, Long chatId, Integer messageId, Integer myEventsPage) {
        attachmentContextManager.setAwaitingFile(userId, eventId, chatId, messageId, myEventsPage);
    }
    
    /**
     * Проверяет, ожидает ли пользователь загрузки файла.
     * 
     * @param userId идентификатор пользователя
     * @return true, если пользователь ожидает загрузки файла
     */
    public boolean isAwaitingFile(Long userId) {
        return attachmentContextManager.isAwaitingFile(userId);
    }
    
    /**
     * Получает контекст ожидания файла для пользователя.
     * 
     * @param userId идентификатор пользователя
     * @return контекст ожидания файла или null
     */
    public AwaitingFileContext getAwaitingFileContext(Long userId) {
        return attachmentContextManager.getAwaitingFileContext(userId);
    }
    
    /**
     * Очищает состояние ожидания файла для пользователя.
     * 
     * @param userId идентификатор пользователя
     */
    public void clearAwaitingFile(Long userId) {
        attachmentContextManager.clearAwaitingFile(userId);
    }
    
    /**
     * Сохраняет messageId сообщения с вложениями для пользователя.
     *
     * @param userId идентификатор пользователя
     * @param eventId идентификатор события
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для редактирования
     */
    public void saveAttachmentMessageId(Long userId, Long eventId, Long chatId, Integer messageId) {
        attachmentContextManager.saveAttachmentMessageId(userId, eventId, chatId, messageId);
    }
    
    /**
     * Очищает сохраненный контекст сообщения с вложениями для пользователя.
     *
     * @param userId идентификатор пользователя
     */
    public void clearAttachmentMessageContext(Long userId) {
        attachmentContextManager.clearAttachmentMessageContext(userId);
    }
    
    // ==================== Методы для работы с шапкой события ====================
    
    /**
     * Получает сохраненный контекст шапки события для пользователя.
     *
     * @param userId идентификатор пользователя
     * @return EventHeaderContext с флагом hasMyEventsHeader и eventCount, или null если контекст не найден
     */
    public EventHeaderContext getEventHeaderContext(Long userId) {
        return eventHeaderContextManager.getEventHeaderContext(userId);
    }
}
