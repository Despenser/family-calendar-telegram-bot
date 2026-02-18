package ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.model.context.AwaitingFileContext;
import ru.golubyatnikov.family.calendar.bot.model.entity.ConversationState;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.repository.ConversationStateRepository;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.repository.UserRepository;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер для управления контекстом вложений событий.
 * Отвечает за хранение и управление состоянием загрузки файлов пользователями.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-13
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AttachmentContextManager {
    
    private final ConversationStateRepository conversationStateRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    
    /**
     * Map для отслеживания пользователей, ожидающих загрузки файла для вложения.
     * Key: userId, Value: AwaitingFileContext (eventId, chatId, messageId)
     */
    private final Map<Long, AwaitingFileContext> usersAwaitingFile = new ConcurrentHashMap<>();
    
    /**
     * Устанавливает состояние ожидания файла для пользователя.
     * 
     * @param userId идентификатор пользователя
     * @param eventId идентификатор события
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения со списком вложений
     */
    public void setAwaitingFile(Long userId, Long eventId, Long chatId, Integer messageId) {
        AwaitingFileContext context = new AwaitingFileContext(eventId, chatId, messageId);
        usersAwaitingFile.put(userId, context);
    }
    
    /**
     * Проверяет, ожидает ли пользователь загрузки файла.
     * 
     * @param userId идентификатор пользователя
     * @return true, если пользователь ожидает загрузки файла
     */
    public boolean isAwaitingFile(Long userId) {
        return usersAwaitingFile.containsKey(userId);
    }
    
    /**
     * Получает контекст ожидания файла для пользователя.
     * 
     * @param userId идентификатор пользователя
     * @return контекст ожидания файла или null
     */
    public AwaitingFileContext getAwaitingFileContext(Long userId) {
        return usersAwaitingFile.get(userId);
    }
    
    /**
     * Очищает состояние ожидания файла для пользователя.
     * 
     * @param userId идентификатор пользователя
     */
    public void clearAwaitingFile(Long userId) {
        usersAwaitingFile.remove(userId);
        }
    
    /**
     * Сохраняет messageId сообщения с вложениями для пользователя.
     *
     * @param userId идентификатор пользователя
     * @param eventId идентификатор события
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для редактирования
     *
     * @throws IllegalArgumentException если userId, eventId, chatId или messageId равны null
     */
    @Transactional
    public void saveAttachmentMessageId(Long userId, Long eventId, Long chatId, Integer messageId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId не может быть null");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("eventId не может быть null");
        }
        if (chatId == null) {
            throw new IllegalArgumentException("chatId не может быть null");
        }
        if (messageId == null) {
            throw new IllegalArgumentException("messageId не может быть null");
        }
        
        // Получаем или создаем состояние диалога
        ConversationState state = conversationStateRepository.findByUserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new IllegalArgumentException("Пользователь с ID=" + userId + " не найден"));
                    return ConversationState.builder().user(user).build();
                });
        
        // Получаем событие
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Событие с ID=" + eventId + " не найдено"));
        
        // Сохраняем контекст
        state.setAttachmentEvent(event);
        state.setAttachmentChatId(chatId);
        state.setAttachmentMessageId(messageId);
        state.setAttachmentContextCreatedAt(Instant.now());
        conversationStateRepository.save(state);
        
        }
    
    /**
     * Очищает сохраненный контекст сообщения с вложениями для пользователя.
     *
     * @param userId идентификатор пользователя
     * @throws IllegalArgumentException если userId равен null
     */
    @Transactional
    public void clearAttachmentMessageContext(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId не может быть null");
        }
        
        conversationStateRepository.findByUserId(userId)
                .ifPresent(state -> {
                    if (state.hasAttachmentMessageContext()) {
                        state.clearAttachmentMessageContext();
                        conversationStateRepository.save(state);
                    }
                });
    }
}
