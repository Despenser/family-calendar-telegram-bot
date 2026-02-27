package ru.golubyatnikov.family.calendar.bot.service.domain.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.exception.UserNotFoundException;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.model.enums.EventFilter;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.repository.UserRepository;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.BotMessageFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Фасадный сервис для управления событиями в семейном календаре.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-01
 */
@Service
@Validated
@RequiredArgsConstructor
@Slf4j
public class EventService {

    private final EventQueryService eventQueryService;
    private final EventCommandService eventCommandService;
    private final EventDeletionService eventDeletionService;
    private final EventNotificationService eventNotificationService;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final TelegramMessageService telegramMessageService;
    private final KeyboardService keyboardService;
    private final BotMessageFormattingService botMessageFormattingService;
    
    // ===== Делегирование к EventCommandService =====
    
    @Transactional
    public Event updateEventTitle(Long eventId, Long userId, String newTitle) {
        return eventCommandService.updateEventTitle(eventId, userId, newTitle);
    }
    
    @Transactional
    public Event updateEventDate(Long eventId, Long userId, LocalDate newDate) {
        return eventCommandService.updateEventDate(eventId, userId, newDate);
    }
    
    @Transactional
    public Event updateEventTime(Long eventId, Long userId, LocalTime newTime) {
        return eventCommandService.updateEventTime(eventId, userId, newTime);
    }
    
    @Transactional
    public Event updateEventDescription(Long eventId, Long userId, String newDescription) {
        return eventCommandService.updateEventDescription(eventId, userId, newDescription);
    }
    
    @Transactional
    public void saveEvent(Event event) {
        eventCommandService.saveEvent(event);
    }
    
    // ===== Делегирование к EventQueryService =====
    
    @Transactional(readOnly = true)
    public List<Event> getUpcomingEvents(Long familyId, int days, ZoneId zoneId) {
        return eventQueryService.getUpcomingEvents(familyId, days, zoneId);
    }
    
    @Transactional(readOnly = true)
    public Event getEventById(Long eventId) {
        return eventQueryService.getEventById(eventId);
    }
    
    @Transactional(readOnly = true)
    public List<Event> getFilteredEvents(User user, EventFilter filter) {
        return eventQueryService.getFilteredEvents(user, filter);
    }
    
    @Transactional(readOnly = true)
    public int getActiveEventsCount(Long userId) {
        return eventQueryService.getActiveEventsCount(userId);
    }

    @Transactional(readOnly = true)
    public List<Event> getEventsByDate(Long familyId, LocalDate date) {
        return eventQueryService.getEventsByDate(familyId, date);
    }
    
    @Transactional(readOnly = true)
    public List<Event> getEventsByDateIncludingCompleted(Long familyId, LocalDate date) {
        return eventQueryService.getEventsByDateIncludingCompleted(familyId, date);
    }
    
    // ===== Делегирование к EventDeletionService =====
    
    @Transactional
    public void deleteEvent(Long eventId, Long userId) {
        eventDeletionService.deleteEvent(eventId, userId);
    }

    @Transactional
    public Event completeEventWithoutHeaderUpdate(Long eventId, Long userId) {
        return eventDeletionService.completeEventWithoutHeaderUpdate(eventId, userId);
    }
    
    @Transactional
    public Event addCompletionNote(Long eventId, Long userId, String note) {
        return eventDeletionService.addCompletionNote(eventId, userId, note);
    }
    
    // ===== Делегирование к EventNotificationService =====
    
    @Transactional
    public void handleEventCreated(Event event, User user) {
        eventNotificationService.handleEventCreated(event, user);
    }
    
    @Transactional
    public void sendOrUpdateEventMessage(Event event, Long chatId) throws TelegramApiException {
        eventNotificationService.sendOrUpdateEventMessage(event, chatId);
    }
    
    // ===== Оркестратор: completeEventWithReordering =====
    
    /**
     * Завершает событие и показывает карточку с предложением добавить заметку.
     *
     * @param eventId   идентификатор события
     * @param userId    идентификатор пользователя
     * @param messageId идентификатор сообщения для редактирования (может быть null)
     */
    @Transactional
    public void completeEventWithReordering(Long eventId, Long userId, Integer messageId) {
        // Завершаем событие
        Event completedEvent = completeEventWithoutHeaderUpdate(eventId, userId);
        log.info("Событие ID={} успешно завершено, статус изменён на COMPLETED", eventId);
        
        // Просто показываем карточку завершенного события с предложением добавить заметку
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        
        Long chatId = user.getTelegramId();
        
        if (chatId != null) {
            editCompletedEventWithNote(chatId, completedEvent, messageId);
        }

    }
    
    private void sendCompletedEventWithNote(Long chatId, @NonNull Event event) {
        try {
            String completedMessage = botMessageFormattingService.buildCompletedEventMessage(event);
            
            InlineKeyboardMarkup keyboard = keyboardService.createCompletionNoteKeyboard(event.getId());
            
            Message sentMessage = telegramMessageService.sendMessageAndGet(
                chatId, 
                completedMessage, 
                keyboard
            );
            
            event.setMessageId((long) sentMessage.getMessageId());
            eventRepository.save(event);
            
        } catch (Exception e) {
            log.error("Ошибка при отправке завершённого события ID={} с предложением добавить заметку: {}", 
                     event.getId(), e.getMessage(), e);
        }
    }
    
    private void editCompletedEventWithNote(Long chatId, @NonNull Event event, Integer messageId) {
        if (messageId == null) {
            sendCompletedEventWithNote(chatId, event);
            return;
        }
        
        try {
            String completedMessage = botMessageFormattingService.buildCompletedEventMessage(event);
            
            InlineKeyboardMarkup keyboard = keyboardService.createCompletionNoteKeyboard(event.getId());
            
            boolean edited = telegramMessageService.tryEditMessageText(
                chatId, 
                messageId, 
                completedMessage, 
                keyboard
            );
            
            if (edited) {
                // Обновляем messageId события после успешного редактирования
                event.setMessageId((long) messageId);
                eventRepository.save(event);
            } else {
                sendCompletedEventWithNote(chatId, event);
            }
            
        } catch (Exception e) {
            log.error("Ошибка при редактировании сообщения завершённого события ID={}: {}, отправляем новое сообщение", 
                     event.getId(), e.getMessage(), e);

            sendCompletedEventWithNote(chatId, event);
        }
    }
}
