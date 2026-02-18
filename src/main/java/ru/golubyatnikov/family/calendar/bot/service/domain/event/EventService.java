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
import java.util.stream.IntStream;

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
    public List<Event> getUserEvents(Long userId) {
        return eventQueryService.getUserEvents(userId);
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
     * Завершает событие и переупорядочивает список "Мои события" если необходимо.
     *
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя
     *
     * @return завершённое событие
     */
    @Transactional
    public Event completeEventWithReordering(Long eventId, Long userId) {
        // Получаем событие
        Event event = getEventById(eventId);

        // Получаем список активных событий ДО завершения
        List<Event> activeEventsBefore = getUserEvents(userId);

        // Определяем позицию события в списке
        int eventPosition = findEventPosition(activeEventsBefore, eventId);
        
        if (eventPosition == -1) {
            log.warn("Событие ID={} не найдено в списке активных событий пользователя ID={}", eventId, userId);
        }

        // Проверяем, является ли событие частью списка /my_events
        boolean hasMessageId = (event.getMessageId() != null);
        boolean isInActiveList = (eventPosition != -1);
        boolean isFirstInMyEventsList = Boolean.TRUE.equals(event.getIsMyEventsHeader());

        // Проверяем, есть ли в списке событие с isMyEventsHeader=true (список существует)
        boolean listHasFirstEvent = activeEventsBefore.stream()
            .anyMatch(e -> Boolean.TRUE.equals(e.getIsMyEventsHeader()));

        boolean isPartOfMyEventsList = hasMessageId
                && isInActiveList
                && (isFirstInMyEventsList || (listHasFirstEvent && eventPosition > 0));
        
        // Проверяем, является ли событие последним
        boolean isLastEvent = (eventPosition == activeEventsBefore.size() - 1);

        // Завершаем событие БЕЗ обновления шапки
        Event completedEvent = completeEventWithoutHeaderUpdate(eventId, userId);
        log.info("Событие ID={} успешно завершено, статус изменён на COMPLETED", eventId);
        
        // Если событие часть списка /my_events, не последнее и есть другие события - переупорядочиваем список
        if (isPartOfMyEventsList && !isLastEvent && activeEventsBefore.size() > 1) {
            reorderMyEventsList(userId, completedEvent);

        } else {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new UserNotFoundException(userId));
            
            Long chatId = user.getTelegramId();
            
            if (chatId != null) {
                editCompletedEventWithNote(chatId, completedEvent, userId);
            }
        }
        
        return completedEvent;
    }
    
    // ===== Вспомогательные методы для completeEventWithReordering =====
    
    private int findEventPosition(@NonNull List<Event> events, Long eventId) {
        return IntStream.range(0, events.size())
                .filter(i -> events.get(i).getId().equals(eventId))
                .findFirst()
                .orElse(-1);
    }
    
    private void deleteActiveEventMessages(@NonNull List<Event> events, Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        
        Long chatId = user.getTelegramId();
        
        if (chatId == null) {
            return;
        }

        for (Event event : events) {
            if (event.getMessageId() != null) {
                try {
                    telegramMessageService.deleteMessageSilently(chatId, event.getMessageId().intValue());
                    event.setMessageId(null);
                    eventRepository.save(event);

                } catch (Exception e) {
                    log.warn("Не удалось удалить сообщение события ID={}: {}", event.getId(), e.getMessage());
                }
            }
        }
        
        }
    
    private void sendEventWithHeader(Long chatId, String header, @NonNull Event event, Long userId) {
        try {
            String eventText = botMessageFormattingService.buildEventMessage(event);
            String combinedMessage = header + "\n" + eventText;
            
            InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, userId);
            
            Message sentMessage = telegramMessageService.sendMessageAndGet(
                chatId, 
                combinedMessage, 
                keyboard
            );
            
            event.setMessageId((long) sentMessage.getMessageId());
            event.setIsMyEventsHeader(true);
            eventRepository.save(event);
            
        } catch (Exception e) {
            log.error("Ошибка при отправке события ID={} с шапкой: {}", event.getId(), e.getMessage(), e);
        }
    }
    
    private void sendEvent(Long chatId, @NonNull Event event, Long userId) {
        try {
            String eventText = botMessageFormattingService.buildEventMessage(event);

            InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, userId);

            Message sentMessage = telegramMessageService.sendMessageAndGet(
                chatId, 
                eventText, 
                keyboard
            );
            
            event.setMessageId((long) sentMessage.getMessageId());
            event.setIsMyEventsHeader(false);
            eventRepository.save(event);
            
        } catch (Exception e) {
            log.error("Ошибка при отправке события ID={} без шапки: {}", event.getId(), e.getMessage(), e);
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
    
    private void editCompletedEventWithNote(Long chatId, @NonNull Event event, Long userId) {
        if (event.getMessageId() == null) {
            sendCompletedEventWithNote(chatId, event);
            return;
        }
        
        try {
            String completedMessage = botMessageFormattingService.buildCompletedEventMessage(event);
            
            InlineKeyboardMarkup keyboard = keyboardService.createCompletionNoteKeyboard(event.getId());
            
            boolean edited = telegramMessageService.tryEditMessageText(
                chatId, 
                event.getMessageId().intValue(), 
                completedMessage, 
                keyboard
            );
            
            if (!edited) {
                sendCompletedEventWithNote(chatId, event);
            }
            
        } catch (Exception e) {
            log.error("Ошибка при редактировании сообщения последнего завершённого события ID={}: {}, отправляем новое сообщение", 
                     event.getId(), e.getMessage(), e);

            sendCompletedEventWithNote(chatId, event);
        }
    }
    
    private void reorderMyEventsList(Long userId, @NonNull Event completedEvent) {
        List<Event> currentActiveEvents = getUserEvents(userId);
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        
        Long chatId = user.getTelegramId();
        
        if (chatId != null && completedEvent.getMessageId() != null) {
            try {
                telegramMessageService.deleteMessageSilently(
                    chatId, 
                    completedEvent.getMessageId().intValue()
                );

                completedEvent.setMessageId(null);
                eventRepository.save(completedEvent);

            } catch (Exception e) {
                log.warn("Не удалось удалить сообщение завершённого события ID={}: {}",
                        completedEvent.getId(), e.getMessage());
            }
        }
        
        deleteActiveEventMessages(currentActiveEvents, userId);
        resendMyEventsWithHeader(userId, currentActiveEvents, completedEvent);
        
    }
    
    private void resendMyEventsWithHeader(Long userId, @NonNull List<Event> activeEvents, Event completedEvent) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        
        Long chatId = user.getTelegramId();
        
        if (chatId == null) {
            return;
        }
        
        int totalCount = activeEvents.size() + 1;
        String header = botMessageFormattingService.buildMyEventsHeader(totalCount);
        if (!activeEvents.isEmpty()) {
            Event firstEvent = activeEvents.getFirst();
            sendEventWithHeader(chatId, header, firstEvent, userId);

            IntStream.range(1, activeEvents.size())
                    .mapToObj(activeEvents::get)
                    .forEach(event -> sendEvent(chatId, event, userId));
            
            sendCompletedEventWithNote(chatId, completedEvent);

        } else {
            String completedMessage = botMessageFormattingService.buildCompletedEventMessage(completedEvent);
            String combinedMessage = header + "\n" + completedMessage;
            
            InlineKeyboardMarkup keyboard = keyboardService.createCompletionNoteKeyboard(completedEvent.getId());
            
            try {
                Message sentMessage = telegramMessageService.sendMessageAndGet(
                    chatId, 
                    combinedMessage, 
                    keyboard
                );
                
                completedEvent.setMessageId((long) sentMessage.getMessageId());
                completedEvent.setIsMyEventsHeader(true);
                eventRepository.save(completedEvent);
                
            } catch (Exception e) {
                log.error("Ошибка при отправке завершённого события ID={} с шапкой: {}", 
                         completedEvent.getId(), e.getMessage(), e);
            }
        }
        
    }
}
