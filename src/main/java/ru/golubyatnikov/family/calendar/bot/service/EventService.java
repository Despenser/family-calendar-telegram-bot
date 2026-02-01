package ru.golubyatnikov.family.calendar.bot.service;

import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import ru.golubyatnikov.family.calendar.bot.exception.UserNotFoundException;
import ru.golubyatnikov.family.calendar.bot.handler.MyEventsCommandHandler;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.EventFilter;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.repository.UserRepository;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Фасадный сервис для управления событиями в семейном календаре.
 * 
 * <p>Делегирует операции специализированным сервисам:</p>
 * <ul>
 *   <li>{@link EventQueryService} - операции чтения</li>
 *   <li>{@link EventCommandService} - операции записи</li>
 *   <li>{@link EventDeletionService} - операции удаления и завершения</li>
 *   <li>{@link EventValidationService} - валидация бизнес-правил</li>
 *   <li>{@link EventNotificationService} - отправка уведомлений</li>
 * </ul>
 * 
 * <p>Сохраняет только метод {@link #completeEventWithReordering} как оркестратор
 * сложного процесса завершения события с переупорядочиванием списка.</p>
 * 
 * @author Family Calendar Bot Team
 * @version 2.0.0
 * @since 2026-02-01
 */
@Service
@Validated
@Slf4j
public class EventService {
    
    // Специализированные сервисы
    private final EventQueryService eventQueryService;
    private final EventCommandService eventCommandService;
    private final EventDeletionService eventDeletionService;
    private final EventValidationService eventValidationService;
    private final EventNotificationService eventNotificationService;
    
    // Зависимости для completeEventWithReordering
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final TelegramMessageService telegramMessageService;
    private final KeyboardService keyboardService;
    private final BotMessageBuilder botMessageBuilder;
    private final MyEventsCommandHandler myEventsCommandHandler;
    
    /**
     * Конструктор с инъекцией зависимостей.
     */
    public EventService(
            EventQueryService eventQueryService,
            EventCommandService eventCommandService,
            EventDeletionService eventDeletionService,
            EventValidationService eventValidationService,
            EventNotificationService eventNotificationService,
            EventRepository eventRepository,
            UserRepository userRepository,
            TelegramMessageService telegramMessageService,
            KeyboardService keyboardService,
            BotMessageBuilder botMessageBuilder,
            @Lazy MyEventsCommandHandler myEventsCommandHandler) {
        this.eventQueryService = eventQueryService;
        this.eventCommandService = eventCommandService;
        this.eventDeletionService = eventDeletionService;
        this.eventValidationService = eventValidationService;
        this.eventNotificationService = eventNotificationService;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.telegramMessageService = telegramMessageService;
        this.keyboardService = keyboardService;
        this.botMessageBuilder = botMessageBuilder;
        this.myEventsCommandHandler = myEventsCommandHandler;
    }
    
    // ===== Делегирование к EventCommandService =====
    
    @Transactional
    public Event createEvent(Long userId, String title, String description, LocalDateTime eventDateTime) {
        return eventCommandService.createEvent(userId, title, description, eventDateTime);
    }
    
    @Transactional
    public Event createEvent(Long userId, String title, String description, LocalDateTime eventDateTime, 
                            LocalTime endTime, Boolean isPersonal) {
        return eventCommandService.createEvent(userId, title, description, eventDateTime, endTime, isPersonal);
    }
    
    @Transactional
    public Event updateEvent(Long eventId, Long userId, String title, String description, LocalDateTime eventDateTime) {
        return eventCommandService.updateEvent(eventId, userId, title, description, eventDateTime);
    }
    
    @Transactional
    public Event updateEvent(Long eventId, Long userId, String title, String description, 
                            LocalDateTime eventDateTime, LocalTime endTime) {
        return eventCommandService.updateEvent(eventId, userId, title, description, eventDateTime, endTime);
    }
    
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
    public Event saveEvent(Event event) {
        return eventCommandService.saveEvent(event);
    }
    
    // ===== Делегирование к EventQueryService =====
    
    @Transactional(readOnly = true)
    public List<Event> getUpcomingEvents(Long familyId, int days, java.time.ZoneId zoneId) {
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
    public Event getEventByIdWithReminders(Long eventId) {
        return eventQueryService.getEventByIdWithReminders(eventId);
    }
    
    @Transactional(readOnly = true)
    public List<Event> getFilteredEvents(User user, EventFilter filter) {
        return eventQueryService.getFilteredEvents(user, filter);
    }
    
    @Transactional(readOnly = true)
    public int getActiveEventsCount(Long userId) {
        return eventQueryService.getActiveEventsCount(userId);
    }
    
    public boolean isToday(LocalDate eventDate, User user) {
        return eventQueryService.isToday(eventDate, user);
    }
    
    public boolean isTomorrow(LocalDate eventDate, User user) {
        return eventQueryService.isTomorrow(eventDate, user);
    }
    
    // ===== Делегирование к EventDeletionService =====
    
    @Transactional
    public Event deleteEvent(Long eventId, Long userId) {
        return eventDeletionService.deleteEvent(eventId, userId);
    }
    
    @Transactional
    public Event completeEvent(Long eventId, Long userId) {
        return eventDeletionService.completeEvent(eventId, userId);
    }
    
    @Transactional
    public Event completeEventWithoutDeletion(Long eventId, Long userId) {
        return eventDeletionService.completeEventWithoutDeletion(eventId, userId);
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
    public Event sendOrUpdateEventMessage(Event event, Long chatId) throws TelegramApiException {
        return eventNotificationService.sendOrUpdateEventMessage(event, chatId);
    }
    
    // ===== Оркестратор: completeEventWithReordering =====
    
    /**
     * Завершает событие и переупорядочивает список "Мои события" если необходимо.
     * 
     * <p>Этот метод остаётся в EventService как оркестратор сложного процесса,
     * координирующий работу нескольких сервисов.</p>
     * 
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя
     * @return завершённое событие
     */
    @Transactional
    public Event completeEventWithReordering(
            @NotNull(message = "eventId не может быть null") Long eventId,
            @NotNull(message = "userId не может быть null") Long userId) {
        log.info("Начало завершения события ID={} с переупорядочиванием для пользователя ID={}", 
                eventId, userId);
        
        // 1. Получаем событие
        Event event = getEventById(eventId);
        log.debug("Событие ID={} получено: title='{}', status={}", 
                 eventId, event.getTitle(), event.getStatus());
        
        // 2. Получаем список активных событий ДО завершения
        List<Event> activeEventsBefore = getUserEvents(userId);
        log.debug("Получен список активных событий ДО завершения: {} событий", 
                 activeEventsBefore.size());
        
        // 3. Определяем позицию события в списке
        int eventPosition = findEventPosition(activeEventsBefore, eventId);
        
        if (eventPosition == -1) {
            log.warn("Событие ID={} не найдено в списке активных событий пользователя ID={}", 
                    eventId, userId);
        }
        
        // 4. Проверяем, является ли событие последним
        boolean isLastEvent = (eventPosition == activeEventsBefore.size() - 1);
        log.debug("Событие ID={} находится на позиции {} из {}, является последним: {}", 
                 eventId, eventPosition, activeEventsBefore.size(), isLastEvent);
        
        // 5. Завершаем событие БЕЗ обновления шапки
        Event completedEvent = completeEventWithoutHeaderUpdate(eventId, userId);
        log.info("Событие ID={} успешно завершено, статус изменён на COMPLETED", eventId);
        
        // 6. Если событие не последнее и есть другие события - переупорядочиваем список
        if (!isLastEvent && activeEventsBefore.size() > 1) {
            log.info("Событие ID={} не является последним (позиция {} из {}), начинаем переупорядочивание", 
                    eventId, eventPosition, activeEventsBefore.size());
            reorderMyEventsList(userId, completedEvent, activeEventsBefore);
            log.info("Переупорядочивание списка завершено для пользователя ID={}", userId);
        } else {
            if (isLastEvent) {
                log.info("Событие ID={} является последним в списке, редактируем сообщение", 
                        eventId);
            } else if (activeEventsBefore.size() <= 1) {
                log.info("В списке только одно событие, редактируем сообщение");
            }
            
            // Получаем пользователя
            User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("Пользователь с ID={} не найден при редактировании сообщения последнего события", userId);
                    return new UserNotFoundException(userId);
                });
            
            Long chatId = user.getTelegramId();
            
            if (chatId != null) {
                log.debug("Редактирование сообщения с предложением добавить заметку для последнего события ID={}", eventId);
                editCompletedEventWithNote(chatId, completedEvent, userId);
                log.info("Сообщение последнего события ID={} обработано (отредактировано или отправлено новое)", eventId);
            } else {
                log.warn("Не удалось получить chatId для пользователя ID={}, сообщение не отправлено", userId);
            }
            
            log.debug("Обновление шапки /my_events отложено до выбора пользователя (добавить заметку или пропустить)");
        }
        
        log.info("Завершение события ID={} с переупорядочиванием успешно выполнено для пользователя ID={}", 
                eventId, userId);
        
        return completedEvent;
    }
    
    // ===== Вспомогательные методы для completeEventWithReordering =====
    
    private int findEventPosition(List<Event> events, Long eventId) {
        log.debug("Поиск позиции события ID={} в списке из {} событий", eventId, events.size());
        
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).getId().equals(eventId)) {
                log.debug("Событие ID={} найдено на позиции {}", eventId, i);
                return i;
            }
        }
        
        log.warn("Событие ID={} не найдено в списке активных событий", eventId);
        return -1;
    }
    
    private void deleteActiveEventMessages(List<Event> events, Long userId) {
        log.debug("Удаление сообщений {} активных событий для пользователя ID={}", 
                 events.size(), userId);
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> {
                log.error("Пользователь с ID={} не найден при удалении сообщений событий", userId);
                return new UserNotFoundException(userId);
            });
        
        Long chatId = user.getTelegramId();
        
        if (chatId == null) {
            log.warn("Не удалось получить chatId для пользователя ID={}", userId);
            return;
        }
        
        int deletedCount = 0;
        for (Event event : events) {
            if (event.getMessageId() != null) {
                try {
                    telegramMessageService.deleteMessageSilently(
                        chatId, 
                        event.getMessageId().intValue()
                    );
                    event.setMessageId(null);
                    eventRepository.save(event);
                    deletedCount++;
                    log.debug("Сообщение события ID={} успешно удалено", event.getId());
                } catch (Exception e) {
                    log.warn("Не удалось удалить сообщение события ID={}: {}", 
                            event.getId(), e.getMessage());
                }
            }
        }
        
        log.info("Удалено {} из {} сообщений событий для пользователя ID={}", 
                deletedCount, events.size(), userId);
    }
    
    private void sendEventWithHeader(Long chatId, String header, Event event, Long userId) {
        log.debug("Отправка события ID={} с шапкой для пользователя ID={}", 
                 event.getId(), userId);
        
        try {
            String eventText = botMessageBuilder.buildEventMessage(event);
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
            
            log.info("Событие ID={} с шапкой успешно отправлено, messageId={}", 
                    event.getId(), sentMessage.getMessageId());
        } catch (Exception e) {
            log.error("Ошибка при отправке события ID={} с шапкой: {}", 
                     event.getId(), e.getMessage(), e);
        }
    }
    
    private void sendEvent(Long chatId, Event event, Long userId) {
        log.debug("Отправка события ID={} без шапки для пользователя ID={}", 
                 event.getId(), userId);
        
        try {
            String eventText = botMessageBuilder.buildEventMessage(event);
            
            InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, userId);
            
            Message sentMessage = telegramMessageService.sendMessageAndGet(
                chatId, 
                eventText, 
                keyboard
            );
            
            event.setMessageId((long) sentMessage.getMessageId());
            event.setIsMyEventsHeader(false);
            eventRepository.save(event);
            
            log.info("Событие ID={} без шапки успешно отправлено, messageId={}", 
                    event.getId(), sentMessage.getMessageId());
        } catch (Exception e) {
            log.error("Ошибка при отправке события ID={} без шапки: {}", 
                     event.getId(), e.getMessage(), e);
        }
    }
    
    private void sendCompletedEventWithNote(Long chatId, Event event, Long userId) {
        log.debug("Отправка завершённого события ID={} с предложением добавить заметку для пользователя ID={}", 
                 event.getId(), userId);
        
        try {
            String completedMessage = botMessageBuilder.buildCompletedEventMessage(event);
            
            InlineKeyboardMarkup keyboard = keyboardService.createCompletionNoteKeyboard(event.getId());
            
            Message sentMessage = telegramMessageService.sendMessageAndGet(
                chatId, 
                completedMessage, 
                keyboard
            );
            
            event.setMessageId((long) sentMessage.getMessageId());
            eventRepository.save(event);
            
            log.info("Завершённое событие ID={} с предложением добавить заметку успешно отправлено, messageId={}", 
                    event.getId(), sentMessage.getMessageId());
        } catch (Exception e) {
            log.error("Ошибка при отправке завершённого события ID={} с предложением добавить заметку: {}", 
                     event.getId(), e.getMessage(), e);
        }
    }
    
    private void editCompletedEventWithNote(Long chatId, Event event, Long userId) {
        log.debug("Редактирование сообщения последнего завершённого события ID={} для пользователя ID={}", 
                 event.getId(), userId);
        
        if (event.getMessageId() == null) {
            log.info("У события ID={} отсутствует messageId, отправляем новое сообщение", event.getId());
            sendCompletedEventWithNote(chatId, event, userId);
            return;
        }
        
        try {
            String completedMessage = botMessageBuilder.buildCompletedEventMessage(event);
            
            InlineKeyboardMarkup keyboard = keyboardService.createCompletionNoteKeyboard(event.getId());
            
            boolean edited = telegramMessageService.tryEditMessageText(
                chatId, 
                event.getMessageId().intValue(), 
                completedMessage, 
                keyboard
            );
            
            if (edited) {
                log.info("Сообщение последнего завершённого события ID={} успешно отредактировано, messageId={}", 
                        event.getId(), event.getMessageId());
            } else {
                log.info("Не удалось отредактировать сообщение события ID={} (удалено или не найдено), отправляем новое", 
                        event.getId());
                sendCompletedEventWithNote(chatId, event, userId);
            }
            
        } catch (Exception e) {
            log.error("Ошибка при редактировании сообщения последнего завершённого события ID={}: {}, отправляем новое сообщение", 
                     event.getId(), e.getMessage(), e);
            sendCompletedEventWithNote(chatId, event, userId);
        }
    }
    
    private void reorderMyEventsList(Long userId, Event completedEvent, 
                                     List<Event> previousActiveEvents) {
        log.info("Начало переупорядочивания списка 'Мои события' для пользователя ID={}, завершённое событие ID={}", 
                userId, completedEvent.getId());
        
        List<Event> currentActiveEvents = getUserEvents(userId);
        log.debug("Получен актуальный список активных событий: {} событий (до завершения было {})", 
                 currentActiveEvents.size(), previousActiveEvents.size());
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> {
                log.error("Пользователь с ID={} не найден при удалении сообщения завершённого события", userId);
                return new UserNotFoundException(userId);
            });
        
        Long chatId = user.getTelegramId();
        
        if (chatId != null && completedEvent.getMessageId() != null) {
            try {
                telegramMessageService.deleteMessageSilently(
                    chatId, 
                    completedEvent.getMessageId().intValue()
                );
                completedEvent.setMessageId(null);
                eventRepository.save(completedEvent);
                log.debug("Сообщение завершённого события ID={} успешно удалено", completedEvent.getId());
            } catch (Exception e) {
                log.warn("Не удалось удалить сообщение завершённого события ID={}: {}", 
                        completedEvent.getId(), e.getMessage());
            }
        }
        
        log.debug("Удаление сообщений {} активных событий", currentActiveEvents.size());
        deleteActiveEventMessages(currentActiveEvents, userId);
        log.debug("Сообщения активных событий удалены");
        
        log.debug("Отправка событий в новом порядке: {} активных + 1 завершённое", 
                 currentActiveEvents.size());
        resendMyEventsWithHeader(userId, currentActiveEvents, completedEvent);
        
        log.info("Переупорядочивание списка 'Мои события' завершено для пользователя ID={}", userId);
    }
    
    private void resendMyEventsWithHeader(Long userId, List<Event> activeEvents, 
                                          Event completedEvent) {
        log.debug("Отправка списка событий заново для пользователя ID={}: {} активных + 1 завершённое", 
                 userId, activeEvents.size());
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> {
                log.error("Пользователь с ID={} не найден при отправке списка событий", userId);
                return new UserNotFoundException(userId);
            });
        
        Long chatId = user.getTelegramId();
        
        if (chatId == null) {
            log.warn("Не удалось получить chatId для пользователя ID={}", userId);
            return;
        }
        
        int totalCount = activeEvents.size() + 1;
        String header = botMessageBuilder.buildMyEventsHeader(totalCount);
        log.debug("Сформирована шапка для {} событий", totalCount);
        
        if (!activeEvents.isEmpty()) {
            Event firstEvent = activeEvents.get(0);
            log.debug("Отправка первого активного события ID={} с шапкой", firstEvent.getId());
            sendEventWithHeader(chatId, header, firstEvent, userId);
            
            for (int i = 1; i < activeEvents.size(); i++) {
                Event event = activeEvents.get(i);
                log.debug("Отправка активного события ID={} без шапки (позиция {})", event.getId(), i);
                sendEvent(chatId, event, userId);
            }
            
            log.debug("Отправка завершённого события ID={} с предложением добавить заметку", 
                     completedEvent.getId());
            sendCompletedEventWithNote(chatId, completedEvent, userId);
        } else {
            log.debug("Активных событий нет, отправка только завершённого события ID={} с шапкой", 
                     completedEvent.getId());
            
            String completedMessage = botMessageBuilder.buildCompletedEventMessage(completedEvent);
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
                
                log.info("Завершённое событие ID={} с шапкой успешно отправлено, messageId={}", 
                        completedEvent.getId(), sentMessage.getMessageId());
            } catch (Exception e) {
                log.error("Ошибка при отправке завершённого события ID={} с шапкой: {}", 
                         completedEvent.getId(), e.getMessage(), e);
            }
        }
        
        log.info("Список событий успешно отправлен заново для пользователя ID={}", userId);
    }
}
