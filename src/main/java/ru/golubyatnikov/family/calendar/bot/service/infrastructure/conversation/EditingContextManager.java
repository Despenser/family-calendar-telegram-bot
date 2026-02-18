package ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.golubyatnikov.family.calendar.bot.model.enums.EditField;
import ru.golubyatnikov.family.calendar.bot.model.context.EditingContext;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер для управления контекстом редактирования событий.
 * Отвечает за хранение и управление состоянием редактирования событий пользователями.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-13
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EditingContextManager {
    
    /**
     * Map для отслеживания пользователей, редактирующих события.
     * Key: userId, Value: EditingContext (eventId, chatId, currentField, messageId, sourceDate)
     */
    private final Map<Long, EditingContext> usersEditingEvents = new ConcurrentHashMap<>();
    
    /**
     * Начинает процесс редактирования события для пользователя с сохранением messageId.
     * 
     * @param userId идентификатор пользователя
     * @param eventId идентификатор редактируемого события
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для редактирования
     */
    public void startEventEditing(Long userId, Long eventId, Long chatId, Integer messageId) {
        EditingContext context = new EditingContext(eventId, chatId, null, messageId, null);
        usersEditingEvents.put(userId, context);

        log.info("Пользователь ID={} начал редактирование события ID={} в сообщении ID={}", 
                userId, eventId, messageId);
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

        EditingContext context = new EditingContext(eventId, chatId, null, messageId, sourceDate);
        usersEditingEvents.put(userId, context);

        log.info("Пользователь ID={} начал редактирование события ID={} из календаря (дата={}) в сообщении ID={}", 
                userId, eventId, sourceDate, messageId);
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
}
