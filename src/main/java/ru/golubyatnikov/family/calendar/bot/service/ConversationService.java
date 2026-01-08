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
        cancelPendingDrafts(userId);
        
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
     * Завершает создание события, обновляя описание и меняя статус на ACTIVE.
     * После этого событие становится полноценным и доступным для отображения.
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
        
        return completed;
    }
    
    /**
     * Отменяет создание события, удаляя черновик из базы данных.
     * 
     * @param userId идентификатор пользователя
     */
    public void cancelEventCreation(Long userId) {
        cancelPendingDrafts(userId);
        log.info("Cancelled event creation for user {}", userId);
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
     */
    private void cancelPendingDrafts(Long userId) {
        List<Event> drafts = eventRepository.findAllByUserIdAndStatus(
            userId, Event.EventStatus.DRAFT);
        
        if (!drafts.isEmpty()) {
            eventRepository.deleteAll(drafts);
            log.info("Deleted {} pending drafts for user {}", drafts.size(), userId);
        }
    }
    
    /**
     * Шаги диалога создания события.
     * Определяют текущее состояние процесса создания события.
     */
    public enum ConversationStep {
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
