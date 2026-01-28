package ru.golubyatnikov.family.calendar.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.exception.UserNotFoundException;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Сервис для управления состоянием многошагового диалога создания события.
 * 
 * <p>Использует черновики событий в базе данных для хранения промежуточного состояния.
 * Черновики имеют статус {@link Event.EventStatus#DRAFT} и постепенно заполняются
 * по мере прохождения пользователем шагов диалога:</p>
 * <ol>
 *   <li>Выбор даты через inline-календарь</li>
 *   <li>Выбор времени через inline-кнопки</li>
 *   <li>Ввод названия события</li>
 *   <li>Ввод описания события (опционально)</li>
 * </ol>
 * 
 * <p>После завершения всех шагов черновик переводится в статус 
 * {@link Event.EventStatus#ACTIVE} и становится полноценным событием.</p>
 * 
 * <p><b>Требования:</b> 15.1, 15.2, 15.3, 15.4, 15.5, 15.6, 15.7</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ConversationService {
    
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final EventService eventService;
    
    /**
     * Начинает новый диалог создания события.
     * Создает черновик события в БД со статусом DRAFT.
     * Автоматически удаляет предыдущие незавершенные черновики пользователя.
     * 
     * @param userId идентификатор пользователя
     * @return созданный черновик события
     * @throws UserNotFoundException если пользователь не найден
     */
    public Event startEventCreation(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        
        // Удаляем предыдущие незавершенные черновики пользователя
        int deletedCount = cancelPendingDrafts(userId);
        if (deletedCount > 0) {
            log.debug("Cleaned up {} existing draft(s) before starting new event creation for user {}", 
                deletedCount, userId);
        }
        
        // Создаем новый черновик
        Event draft = Event.builder()
            .user(user)
            .family(user.getFamily())
            .status(Event.EventStatus.DRAFT)
            .notified(false)
            .build();
        
        Event savedDraft = eventRepository.save(draft);
        log.info("Created draft event {} for user {}", savedDraft.getId(), userId);
        
        return savedDraft;
    }
    
    /**
     * Обновляет тип события в черновике (персональное или семейное).
     * 
     * @param userId идентификатор пользователя
     * @param isPersonal true для персонального события, false для семейного
     * @return обновленный черновик
     * @throws IllegalStateException если активный черновик не найден
     */
    public Event updateEventType(Long userId, boolean isPersonal) {
        Event draft = getActiveDraft(userId);
        draft.setIsPersonal(isPersonal);
        
        Event updated = eventRepository.save(draft);
        log.info("Updated draft {} with type: {}", draft.getId(), isPersonal ? "personal" : "family");
        
        return updated;
    }
    
    /**
     * Обновляет дату в черновике события.
     * 
     * @param userId идентификатор пользователя
     * @param date выбранная дата события
     * @return обновленный черновик
     * @throws IllegalStateException если активный черновик не найден
     */
    public Event updateEventDate(Long userId, LocalDate date) {
        Event draft = getActiveDraft(userId);
        draft.setEventDate(date);
        
        Event updated = eventRepository.save(draft);
        log.info("Updated draft {} with date {}", draft.getId(), date);
        
        return updated;
    }
    
    /**
     * Обновляет время в черновике события.
     * 
     * @param userId идентификатор пользователя
     * @param time выбранное время события
     * @return обновленный черновик
     * @throws IllegalStateException если активный черновик не найден
     */
    public Event updateEventTime(Long userId, LocalTime time) {
        Event draft = getActiveDraft(userId);
        draft.setEventTime(time);
        
        Event updated = eventRepository.save(draft);
        log.info("Updated draft {} with time {}", draft.getId(), time);
        
        return updated;
    }
    
    /**
     * Обновляет название в черновике события.
     * 
     * @param userId идентификатор пользователя
     * @param title название события
     * @return обновленный черновик
     * @throws IllegalStateException если активный черновик не найден
     */
    public Event updateEventTitle(Long userId, String title) {
        Event draft = getActiveDraft(userId);
        draft.setTitle(title);
        
        Event updated = eventRepository.save(draft);
        log.info("Updated draft {} with title", draft.getId());
        
        return updated;
    }
    
    /**
     * Сохраняет messageId сообщения создания в черновике события.
     * 
     * <p>MessageId используется для обновления одного и того же сообщения бота
     * на протяжении всего процесса создания события, вместо отправки новых сообщений.
     * Это делает чат более чистым и удобным для пользователя.</p>
     * 
     * <p><b>Использование:</b></p>
     * <p>Метод должен вызываться сразу после отправки начального сообщения
     * создания события (после команды /add_event). Сохраненный messageId затем
     * используется для обновления сообщения на каждом шаге диалога:</p>
     * <ul>
     *   <li>При выборе типа события (персональное/семейное)</li>
     *   <li>При выборе даты через inline-календарь</li>
     *   <li>При выборе времени через inline-кнопки</li>
     *   <li>При вводе названия события</li>
     *   <li>При вводе описания события</li>
     * </ul>
     * 
     * <p><b>Пример использования:</b></p>
     * <pre>{@code
     * // В AddEventCommandHandler после отправки сообщения
     * Message sentMessage = messageService.sendMessageWithInlineKeyboardAndGet(
     *     chatId, "Выберите тип события:", keyboard);
     * conversationService.setCreationMessageId(userId, sentMessage.getMessageId().longValue());
     * }</pre>
     * 
     * <p><b>Требования:</b> 4.1</p>
     * 
     * @param userId идентификатор пользователя, создающего событие
     * @param messageId идентификатор сообщения Telegram, которое будет обновляться
     * @throws IllegalStateException если активный черновик не найден для пользователя
     * @throws IllegalArgumentException если userId или messageId равны null
     * 
     * @see Event#getMessageId()
     * @see Event#setMessageId(Long)
     * @see ru.golubyatnikov.family.calendar.bot.handler.AddEventCommandHandler#handle
     * @see ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService#sendMessageWithInlineKeyboardAndGet
     * @see ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService#editMessageText
     */
    public void setCreationMessageId(Long userId, Long messageId) {
        Event draft = getActiveDraft(userId);
        draft.setMessageId(messageId);
        
        eventRepository.save(draft);
        log.debug("MessageId сообщения создания сохранен: userId={}, draftId={}, messageId={}", 
            userId, draft.getId(), messageId);
    }
    
    /**
     * Завершает создание события, обновляя описание и меняя статус на ACTIVE.
     * После этого событие становится полноценным и доступным для отображения.
     * Также автоматически создаются напоминания по умолчанию.
     * 
     * @param userId идентификатор пользователя
     * @param description описание события (может быть null или пустым)
     * @return завершенное событие со статусом ACTIVE
     * @throws IllegalStateException если активный черновик не найден
     */
    public Event completeEventCreation(Long userId, String description) {
        Event draft = getActiveDraft(userId);
        draft.setDescription(description);
        draft.setStatus(Event.EventStatus.ACTIVE);
        
        Event completed = eventRepository.save(draft);
        log.info("Completed event creation: {}", completed.getId());
        
        // Автоматически создаем напоминания по умолчанию
        eventService.handleEventCreated(completed, draft.getUser());
        
        return completed;
    }
    
    /**
     * Отменяет создание события, удаляя все черновики пользователя из базы данных.
     * Метод безопасен и не выбрасывает исключений, если черновиков нет.
     * 
     * <p>Используется в следующих сценариях:</p>
     * <ul>
     *   <li>Пользователь явно отменяет создание события</li>
     *   <li>Возникает ошибка в процессе создания события</li>
     *   <li>Начинается создание нового события (очистка предыдущих черновиков)</li>
     * </ul>
     * 
     * @param userId идентификатор пользователя
     */
    public void cancelEventCreation(Long userId) {
        log.debug("Attempting to cancel event creation for user {}", userId);
        
        try {
            int deletedCount = cancelPendingDrafts(userId);
            
            if (deletedCount > 0) {
                log.info("Successfully cancelled event creation for user {}: deleted {} draft(s)", 
                    userId, deletedCount);
            } else {
                log.debug("No drafts found to cancel for user {}", userId);
            }
        } catch (Exception e) {
            log.error("Error while cancelling event creation for user {}: {}", 
                userId, e.getMessage(), e);
            // Не пробрасываем исключение - метод должен быть безопасным
        }
    }
    
    /**
     * Получает активный черновик пользователя.
     * 
     * @param userId идентификатор пользователя
     * @return активный черновик события
     * @throws IllegalStateException если активный черновик не найден
     */
    public Event getActiveDraft(Long userId) {
        return eventRepository.findByUserIdAndStatus(userId, Event.EventStatus.DRAFT)
            .orElseThrow(() -> new IllegalStateException(
                "No active draft found for user " + userId));
    }
    
    /**
     * Проверяет, есть ли у пользователя активный черновик.
     * 
     * @param userId идентификатор пользователя
     * @return true если есть активный черновик, иначе false
     */
    public boolean hasActiveDraft(Long userId) {
        return eventRepository.findByUserIdAndStatus(userId, Event.EventStatus.DRAFT)
            .isPresent();
    }
    
    /**
     * Получает текущий шаг диалога на основе заполненности полей черновика.
     * 
     * @param draft черновик события
     * @return текущий шаг диалога
     */
    public ConversationStep getCurrentStep(Event draft) {
        if (draft.getIsPersonal() == null) {
            return ConversationStep.WAITING_FOR_TYPE;
        }
        if (draft.getEventDate() == null) {
            return ConversationStep.WAITING_FOR_DATE;
        }
        if (draft.getEventTime() == null) {
            return ConversationStep.WAITING_FOR_TIME;
        }
        if (draft.getTitle() == null || draft.getTitle().isBlank()) {
            return ConversationStep.WAITING_FOR_TITLE;
        }
        return ConversationStep.WAITING_FOR_DESCRIPTION;
    }
    
    /**
     * Удаляет все незавершенные черновики пользователя.
     * Вызывается перед созданием нового черновика или при отмене диалога.
     * 
     * @param userId идентификатор пользователя
     * @return количество удаленных черновиков
     */
    private int cancelPendingDrafts(Long userId) {
        List<Event> drafts = eventRepository.findAllByUserIdAndStatus(
            userId, Event.EventStatus.DRAFT);
        
        if (!drafts.isEmpty()) {
            log.debug("Found {} pending draft(s) for user {}", drafts.size(), userId);
            
            // Логируем ID удаляемых черновиков для отладки
            if (log.isDebugEnabled()) {
                drafts.forEach(draft -> 
                    log.debug("Deleting draft {}: created at {}, title={}, date={}, time={}", 
                        draft.getId(), 
                        draft.getCreatedAt(),
                        draft.getTitle(),
                        draft.getEventDate(),
                        draft.getEventTime()));
            }
            
            eventRepository.deleteAll(drafts);
            log.info("Deleted {} pending draft(s) for user {}", drafts.size(), userId);
            
            return drafts.size();
        }
        
        log.debug("No pending drafts found for user {}", userId);
        return 0;
    }
    
    /**
     * Шаги диалога создания события.
     * Определяют текущее состояние процесса создания события.
     */
    public enum ConversationStep {
        /** Ожидание выбора типа события (персональное или семейное) */
        WAITING_FOR_TYPE,
        
        /** Ожидание выбора даты через inline-календарь */
        WAITING_FOR_DATE,
        
        /** Ожидание выбора времени через inline-кнопки */
        WAITING_FOR_TIME,
        
        /** Ожидание ввода названия события через текстовое сообщение */
        WAITING_FOR_TITLE,
        
        /** Ожидание ввода описания события (опционально) */
        WAITING_FOR_DESCRIPTION
    }
}
