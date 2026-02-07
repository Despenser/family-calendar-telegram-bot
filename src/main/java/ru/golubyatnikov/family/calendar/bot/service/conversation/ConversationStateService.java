package ru.golubyatnikov.family.calendar.bot.service.conversation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.model.ConversationState;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.repository.ConversationStateRepository;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.repository.UserRepository;
import ru.golubyatnikov.family.calendar.bot.service.attachment.AttachmentMessageContext;

import java.time.Instant;
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
@RequiredArgsConstructor
public class ConversationStateService {
    
    private final ConversationStateRepository conversationStateRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    
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
     * Map для отслеживания пользователей, ожидающих загрузки файла для вложения.
     * Key: userId, Value: AwaitingFileContext (eventId, chatId, messageId)
     */
    private final Map<Long, AwaitingFileContext> usersAwaitingFile = new ConcurrentHashMap<>();
    
    /**
     * Устанавливает состояние ожидания поискового запроса для пользователя.
     * 
     * <p>Метод сохраняет контекст поиска в базе данных, чтобы система могла
     * редактировать сообщение при последующих операциях поиска.
     * Если состояние диалога для пользователя не существует, оно будет создано.</p>
     * 
     * <p>Метод выполняется в транзакции для обеспечения атомарности операции.</p>
     * 
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для редактирования
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
        
        // Сохраняем в in-memory map для быстрого доступа
        usersAwaitingSearchQuery.put(userId, chatId);
        
        // Получаем или создаем состояние диалога
        ConversationState state = conversationStateRepository.findByUserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new IllegalArgumentException("Пользователь с ID=" + userId + " не найден"));
                    
                    ConversationState newState = ConversationState.builder()
                            .user(user)
                            .build();
                    
                    log.debug("Создано новое состояние диалога для пользователя ID={}", userId);
                    return newState;
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
     * Получает chatId для пользователя, ожидающего ввода поискового запроса.
     * 
     * @param userId идентификатор пользователя
     * @return chatId или null, если пользователь не ожидает ввода
     */
    public Long getSearchQueryChatId(Long userId) {
        return usersAwaitingSearchQuery.get(userId);
    }
    
    /**
     * Получает сохраненный контекст поискового запроса для пользователя.
     * 
     * <p>Метод извлекает сохраненный контекст из базы данных.
     * Если контекст не найден или не полный, метод вернет null.</p>
     * 
     * @param userId идентификатор пользователя
     * @return SearchQueryContext с chatId и messageId, или null если контекст не найден
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
     * <p>Метод удаляет контекст поиска как из in-memory map, так и из базы данных.
     * Используется при завершении поиска или при переходе к другой операции.</p>
     * 
     * <p>Метод выполняется в транзакции для обеспечения атомарности операции.</p>
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
        
        // Очищаем из in-memory map
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
    
    /**
     * Начинает процесс редактирования события для пользователя.
     * 
     * @param userId идентификатор пользователя
     * @param eventId идентификатор редактируемого события
     * @param chatId идентификатор чата
     */
    public void startEventEditing(Long userId, Long eventId, Long chatId) {
        EditingContext context = new EditingContext(eventId, chatId, null, null, null);
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
    public void startEventEditingFromCalendar(Long userId, Long eventId, Long chatId, Integer messageId, java.time.LocalDate sourceDate) {
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
        log.info("Пользователь ID={} переведен в режим ожидания заметки для события ID={}, messageId={}", 
                userId, eventId, messageId);
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
        log.info("Пользователь ID={} переведен в режим ожидания файла для события ID={}", userId, eventId);
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
        log.debug("Состояние ожидания файла очищено для пользователя ID={}", userId);
    }
    
    /**
     * Сохраняет messageId сообщения с вложениями для пользователя.
     * 
     * <p>Метод сохраняет контекст сообщения в базе данных, чтобы система могла
     * редактировать это сообщение при последующих операциях с вложениями.
     * Если состояние диалога для пользователя не существует, оно будет создано.</p>
     * 
     * <p>Метод выполняется в транзакции для обеспечения атомарности операции.</p>
     * 
     * @param userId идентификатор пользователя
     * @param eventId идентификатор события
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для редактирования
     * @throws IllegalArgumentException если userId, eventId, chatId или messageId равны null
     * @see AttachmentMessageContext
     * @see ConversationState
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
        
        log.debug("Сохранение attachment messageId={} для пользователя ID={}, события ID={}, чата ID={}", 
                messageId, userId, eventId, chatId);
        
        // Получаем или создаем состояние диалога
        ConversationState state = conversationStateRepository.findByUserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new IllegalArgumentException("Пользователь с ID=" + userId + " не найден"));
                    
                    ConversationState newState = ConversationState.builder()
                            .user(user)
                            .build();
                    
                    log.debug("Создано новое состояние диалога для пользователя ID={}", userId);
                    return newState;
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
        
        log.info("Сохранен attachment messageId={} для пользователя ID={}, события ID={}", 
                messageId, userId, eventId);
    }
    
    /**
     * Получает сохраненный контекст сообщения с вложениями для пользователя.
     * 
     * <p>Метод извлекает сохраненный контекст из базы данных и проверяет его валидность.
     * Если контекст истек (прошло более 47 часов), метод вернет null и очистит
     * истекший контекст из базы данных.</p>
     * 
     * @param userId идентификатор пользователя
     * @return AttachmentMessageContext с chatId и messageId, или null если контекст не найден или истек
     * @throws IllegalArgumentException если userId равен null
     * @see AttachmentMessageContext
     * @see ConversationState
     */
    @Transactional
    public AttachmentMessageContext getAttachmentMessageContext(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId не может быть null");
        }
        
        log.debug("Получение attachment message context для пользователя ID={}", userId);
        
        return conversationStateRepository.findByUserId(userId)
                .filter(ConversationState::hasAttachmentMessageContext)
                .map(state -> {
                    AttachmentMessageContext context = new AttachmentMessageContext(
                            state.getAttachmentEvent().getId(),
                            state.getAttachmentChatId(),
                            state.getAttachmentMessageId(),
                            state.getAttachmentContextCreatedAt()
                    );
                    
                    // Проверяем истечение контекста
                    if (context.isExpired()) {
                        log.info("Attachment message context истек для пользователя ID={}, очистка контекста", userId);
                        state.clearAttachmentMessageContext();
                        conversationStateRepository.save(state);
                        return null;
                    }
                    
                    log.debug("Найден валидный attachment message context для пользователя ID={}: eventId={}, chatId={}, messageId={}", 
                            userId, context.getEventId(), context.getChatId(), context.getMessageId());
                    return context;
                })
                .orElse(null);
    }
    
    /**
     * Очищает сохраненный контекст сообщения с вложениями для пользователя.
     * 
     * <p>Метод удаляет все поля attachment context из состояния диалога пользователя.
     * Используется при возврате к карточке события или при завершении работы с вложениями.</p>
     * 
     * <p>Метод выполняется в транзакции для обеспечения атомарности операции.</p>
     * 
     * @param userId идентификатор пользователя
     * @throws IllegalArgumentException если userId равен null
     * @see ConversationState
     */
    @Transactional
    public void clearAttachmentMessageContext(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId не может быть null");
        }
        
        log.debug("Очистка attachment message context для пользователя ID={}", userId);
        
        conversationStateRepository.findByUserId(userId)
                .ifPresent(state -> {
                    if (state.hasAttachmentMessageContext()) {
                        state.clearAttachmentMessageContext();
                        conversationStateRepository.save(state);
                        log.info("Attachment message context очищен для пользователя ID={}", userId);
                    } else {
                        log.debug("Attachment message context не найден для пользователя ID={}, очистка не требуется", userId);
                    }
                });
    }
    
    /**
     * Сохраняет контекст шапки события для пользователя.
     * 
     * <p>Метод сохраняет информацию о том, что событие было открыто с шапкой "Мои события",
     * чтобы при возврате к событию из списка вложений можно было восстановить эту шапку.
     * Если состояние диалога для пользователя не существует, оно будет создано.</p>
     * 
     * <p>Метод выполняется в транзакции для обеспечения атомарности операции.</p>
     * 
     * @param userId идентификатор пользователя
     * @param hasMyEventsHeader флаг наличия шапки "Мои события"
     * @param eventCount количество событий пользователя для формирования шапки
     * @throws IllegalArgumentException если userId равен null
     * @see EventHeaderContext
     * @see ConversationState
     */
    @Transactional
    public void saveEventHeaderContext(Long userId, boolean hasMyEventsHeader, int eventCount) {
        if (userId == null) {
            throw new IllegalArgumentException("userId не может быть null");
        }
        
        try {
            log.debug("Сохранение контекста шапки для пользователя ID={}: hasMyEventsHeader={}, eventCount={}", 
                    userId, hasMyEventsHeader, eventCount);
            
            // Получаем или создаем состояние диалога
            ConversationState state = conversationStateRepository.findByUserId(userId)
                    .orElseGet(() -> {
                        User user = userRepository.findById(userId)
                                .orElseThrow(() -> new IllegalArgumentException("Пользователь с ID=" + userId + " не найден"));
                        
                        ConversationState newState = ConversationState.builder()
                                .user(user)
                                .build();
                        
                        log.debug("Создано новое состояние диалога для пользователя ID={}", userId);
                        return newState;
                    });
            
            // Сохраняем контекст шапки
            state.setEventHasMyEventsHeader(hasMyEventsHeader);
            state.setEventCountForHeader(eventCount);
            
            conversationStateRepository.save(state);
            
            log.info("Сохранен контекст шапки для пользователя ID={}: hasMyEventsHeader={}, eventCount={}", 
                    userId, hasMyEventsHeader, eventCount);
        } catch (Exception e) {
            log.error("Ошибка при сохранении контекста шапки: userId={}, error={}", 
                    userId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Получает сохраненный контекст шапки события для пользователя.
     * 
     * <p>Метод извлекает сохраненный контекст из базы данных.
     * Если контекст не найден или не полный, метод вернет null.</p>
     * 
     * @param userId идентификатор пользователя
     * @return EventHeaderContext с флагом hasMyEventsHeader и eventCount, или null если контекст не найден
     * @throws IllegalArgumentException если userId равен null
     * @see EventHeaderContext
     * @see ConversationState
     */
    @Transactional(readOnly = true)
    public EventHeaderContext getEventHeaderContext(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId не может быть null");
        }
        
        log.debug("Получение контекста шапки для пользователя ID={}", userId);
        
        return conversationStateRepository.findByUserId(userId)
                .filter(ConversationState::hasEventHeaderContext)
                .map(state -> {
                    EventHeaderContext context = new EventHeaderContext(
                            state.getEventHasMyEventsHeader(),
                            state.getEventCountForHeader()
                    );
                    
                    log.debug("Найден контекст шапки для пользователя ID={}: hasMyEventsHeader={}, eventCount={}", 
                            userId, context.isHasMyEventsHeader(), context.getEventCount());
                    return context;
                })
                .orElseGet(() -> {
                    log.debug("Контекст шапки не найден для пользователя ID={}", userId);
                    return null;
                });
    }
    
    /**
     * Очищает сохраненный контекст шапки события для пользователя.
     * 
     * <p>Метод удаляет поля контекста шапки из состояния диалога пользователя.
     * Используется при завершении работы с событием или при переходе к другому событию.</p>
     * 
     * <p>Метод выполняется в транзакции для обеспечения атомарности операции.</p>
     * 
     * @param userId идентификатор пользователя
     * @throws IllegalArgumentException если userId равен null
     * @see ConversationState
     */
    @Transactional
    public void clearEventHeaderContext(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId не может быть null");
        }
        
        log.debug("Очистка контекста шапки для пользователя ID={}", userId);
        
        conversationStateRepository.findByUserId(userId)
                .ifPresent(state -> {
                    if (state.hasEventHeaderContext()) {
                        state.clearEventHeaderContext();
                        conversationStateRepository.save(state);
                        log.info("Контекст шапки очищен для пользователя ID={}", userId);
                    } else {
                        log.debug("Контекст шапки не найден для пользователя ID={}, очистка не требуется", userId);
                    }
                });
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
        
        /**
         * Дата, с которой началось редактирование (для возврата к списку событий на эту дату).
         * Если null, редактирование началось не из календаря.
         */
        private java.time.LocalDate sourceDate;
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
     * Содержит информацию о событии, чате и сообщении для редактирования.
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
        
        /**
         * Идентификатор сообщения для редактирования.
         * Используется для обновления того же сообщения на всех этапах добавления заметки.
         */
        private Integer messageId;
    }
    
    /**
     * Контекст ожидания файла для вложения.
     * Содержит информацию о событии, чате и сообщении со списком вложений.
     */
    @Data
    @AllArgsConstructor
    public static class AwaitingFileContext {
        /**
         * Идентификатор события, к которому добавляется вложение
         */
        private Long eventId;
        
        /**
         * Идентификатор чата
         */
        private Long chatId;
        
        /**
         * Идентификатор сообщения со списком вложений для обновления
         */
        private Integer messageId;
    }
    
    /**
     * Контекст шапки события.
     * Содержит информацию о наличии шапки "Мои события" и количестве событий.
     */
    @Data
    @AllArgsConstructor
    public static class EventHeaderContext {
        /**
         * Флаг наличия шапки "Мои события"
         */
        private boolean hasMyEventsHeader;
        
        /**
         * Количество событий для формирования шапки
         */
        private int eventCount;
    }
    
    /**
     * Контекст поискового запроса.
     * Содержит информацию о чате и сообщении для редактирования при поиске.
     */
    @Data
    @AllArgsConstructor
    public static class SearchQueryContext {
        /**
         * Идентификатор чата
         */
        private Long chatId;
        
        /**
         * Идентификатор сообщения для редактирования
         */
        private Integer messageId;
    }
}
