package ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.golubyatnikov.family.calendar.bot.model.context.CompletionNoteContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер для управления контекстом добавления заметок к завершенным событиям.
 * Отвечает за хранение и управление состоянием добавления заметок пользователями.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-13
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CompletionNoteContextManager {
    
    /**
     * Map для отслеживания пользователей, добавляющих заметку к завершенному событию.
     * Key: userId, Value: CompletionNoteContext (eventId, chatId, messageId)
     */
    private final Map<Long, CompletionNoteContext> usersAwaitingCompletionNote = new ConcurrentHashMap<>();
    
    /**
     * Устанавливает состояние ожидания заметки к завершенному событию.
     * 
     * @param userId идентификатор пользователя
     * @param eventId идентификатор завершенного события
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для редактирования
     */
    public void setAwaitingCompletionNote(Long userId, Long eventId, Long chatId, Integer messageId) {
        CompletionNoteContext context = new CompletionNoteContext(eventId, chatId, messageId);
        usersAwaitingCompletionNote.put(userId, context);
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
        }
}
