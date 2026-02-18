package ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.exception.UserNotFoundException;
import ru.golubyatnikov.family.calendar.bot.model.enums.ConversationStep;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.enums.EventStatus;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.repository.UserRepository;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Сервис для управления состоянием многошагового диалога создания события.
 *
 * @author Golubyatnikov Aleksey
 * @since 2025-12-30
 */
@Service
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
     *
     * @return созданный черновик события
     * @throws UserNotFoundException если пользователь не найден
     */
    public Event startEventCreation(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        
        // Создаем новый черновик
        Event draft = Event.builder()
            .user(user)
            .family(user.getFamily())
            .status(EventStatus.DRAFT)
            .notified(false)
            .build();

        return eventRepository.save(draft);
    }
    
    /**
     * Обновляет тип события в черновике (персональное или семейное).
     *
     * @param userId идентификатор пользователя
     * @param isPersonal true для персонального события, false для семейного
     *
     * @throws IllegalStateException если активный черновик не найден
     */
    public void updateEventType(Long userId, boolean isPersonal) {
        Event draft = getActiveDraft(userId);
        draft.setIsPersonal(isPersonal);
        eventRepository.save(draft);
    }
    
    /**
     * Обновляет дату в черновике события.
     *
     * @param userId идентификатор пользователя
     * @param date выбранная дата события
     *
     * @throws IllegalStateException если активный черновик не найден
     */
    public void updateEventDate(Long userId, LocalDate date) {
        Event draft = getActiveDraft(userId);
        draft.setEventDate(date);
        eventRepository.save(draft);
    }
    
    /**
     * Обновляет время в черновике события.
     * 
     * @param userId идентификатор пользователя
     * @param time выбранное время события
     *
     * @return обновленный черновик
     * @throws IllegalStateException если активный черновик не найден
     */
    public Event updateEventTime(Long userId, LocalTime time) {
        Event draft = getActiveDraft(userId);
        draft.setEventTime(time);
        return eventRepository.save(draft);
    }
    
    /**
     * Обновляет название в черновике события.
     *
     * @param userId идентификатор пользователя
     * @param title  название события
     *
     * @throws IllegalStateException если активный черновик не найден
     */
    public void updateEventTitle(Long userId, String title) {
        Event draft = getActiveDraft(userId);
        draft.setTitle(title);
        eventRepository.save(draft);
    }
    
    /**
     * Сохраняет messageId сообщения создания в черновике события.
     *
     * @param userId идентификатор пользователя, создающего событие
     * @param messageId идентификатор сообщения Telegram, которое будет обновляться
     *
     * @throws IllegalStateException если активный черновик не найден для пользователя
     * @throws IllegalArgumentException если userId или messageId равны null
     */
    @Transactional
    public void setCreationMessageId(Long userId, Long messageId) {
        Event draft = getActiveDraft(userId);
        draft.setMessageId(messageId);
        eventRepository.save(draft);
    }
    
    /**
     * Завершает создание события, обновляя описание и меняя статус на ACTIVE.
     * После этого событие становится полноценным и доступным для отображения.
     * Также автоматически создаются напоминания по умолчанию.
     * 
     * @param userId идентификатор пользователя
     * @param description описание события (может быть null или пустым)
     *
     * @return завершенное событие со статусом ACTIVE
     * @throws IllegalStateException если активный черновик не найден
     */
    public Event completeEventCreation(Long userId, String description) {
        Event draft = getActiveDraft(userId);
        draft.setDescription(description);
        draft.setStatus(EventStatus.ACTIVE);
        
        Event completed = eventRepository.save(draft);

        // Автоматически создаем напоминания по умолчанию
        eventService.handleEventCreated(completed, draft.getUser());
        
        return completed;
    }
    
    /**
     * Отменяет создание события, удаляя все черновики пользователя из базы данных.
     *
     * @param userId идентификатор пользователя
     */
    @Transactional
    public void cancelEventCreation(Long userId) {
        try {
            cancelPendingDrafts(userId);

        } catch (Exception e) {
            log.error("Ошибка при отмене создания события для пользователя {}: {}", userId, e.getMessage(), e);
        }
    }
    
    /**
     * Получает активный черновик пользователя.
     * 
     * @param userId идентификатор пользователя
     *
     * @return активный черновик события
     * @throws IllegalStateException если активный черновик не найден
     */
    public Event getActiveDraft(Long userId) {
        return eventRepository.findByUserIdAndStatus(userId, EventStatus.DRAFT)
            .orElseThrow(() -> new IllegalStateException("Активный черновик не найден для пользователя " + userId));
    }
    
    /**
     * Проверяет, есть ли у пользователя активный черновик.
     * 
     * @param userId идентификатор пользователя
     * @return true, если есть активный черновик, иначе false
     */
    @Transactional(readOnly = true)
    public boolean hasActiveDraft(Long userId) {
        return eventRepository.findByUserIdAndStatus(userId, EventStatus.DRAFT)
            .isPresent();
    }
    
    /**
     * Получает текущий шаг диалога на основе заполненности полей черновика.
     * 
     * @param draft черновик события
     * @return текущий шаг диалога
     */
    public ConversationStep getCurrentStep(@NonNull Event draft) {
        if (draft.getEventDate() == null) {
            return ConversationStep.WAITING_FOR_DATE;
        }
        if (draft.getEventTime() == null) {
            return ConversationStep.WAITING_FOR_TIME;
        }
        if (draft.getIsPersonal() == null) {
            return ConversationStep.WAITING_FOR_TYPE;
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
        List<Event> drafts = eventRepository.findAllByUserIdAndStatus(userId, EventStatus.DRAFT);
        if (!drafts.isEmpty()) {
            eventRepository.deleteAll(drafts);
        }
    }
}
