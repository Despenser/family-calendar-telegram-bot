package ru.golubyatnikov.family.calendar.bot.service.domain.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.exception.UserNotFoundException;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.model.enums.EventStatus;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.repository.UserRepository;
import ru.golubyatnikov.family.calendar.bot.service.domain.reminder.ReminderCreationService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.BotMessageFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;

import java.util.List;

/**
 * Сервис для отправки уведомлений о событиях.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-01
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EventNotificationService {
    
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final TelegramMessageService telegramMessageService;
    private final KeyboardService keyboardService;
    private final BotMessageFormattingService botMessageFormattingService;
    private final ReminderCreationService reminderCreationService;
    
    /**
     * Обрабатывает создание события и автоматически создает напоминания по умолчанию.
     * 
     * @param event созданное событие
     * @param user пользователь, создавший событие
     */
    @Transactional
    public void handleEventCreated(@NonNull Event event, @NonNull User user) {
        reminderCreationService.createDefaultReminders(event, user);
    }
    
    /**
     * Отправляет или обновляет сообщение о событии в Telegram.
     * 
     * @param event событие для отправки/обновления
     * @param chatId ID чата для отправки
     *
     * @return обновленное событие с актуальным messageId
     * @throws TelegramApiException при критических ошибках отправки
     */
    @Transactional
    public Event sendOrUpdateEventMessage(Event event, Long chatId) throws TelegramApiException {
        
        if (event == null) {
            throw new IllegalArgumentException("Event не может быть null");
        }
        
        if (chatId == null) {
            throw new IllegalArgumentException("ChatId не может быть null");
        }
        
        String messageText = botMessageFormattingService.buildEventMessage(event);
        if (Boolean.TRUE.equals(event.getIsMyEventsHeader())) {
            int eventCount = eventRepository.countByUserIdAndStatus(event.getUser().getId(), EventStatus.ACTIVE);
            String header = botMessageFormattingService.buildMyEventsHeader(eventCount);
            messageText = header + "\n" + messageText;
        }
        
        InlineKeyboardMarkup keyboard;
        if (event.getStatus() == EventStatus.DRAFT) {
            keyboard = keyboardService.createEditFieldSelectionKeyboard(event.getId());
        } else {
            Long userId = event.getUser().getId();
            keyboard = keyboardService.createEventActionsKeyboard(event, userId);
        }
        
        if (event.getMessageId() != null) {
            boolean updated = telegramMessageService.tryEditMessageText(
                    chatId, 
                    event.getMessageId().intValue(), 
                    messageText, 
                    keyboard
            );
            
            if (updated) {
                return event;
            }
        }
        
        Message sentMessage = telegramMessageService.sendMessageAndGet(chatId, messageText, keyboard);
        event.setMessageId((long) sentMessage.getMessageId());

        return eventRepository.save(event);
    }
    
    /**
     * Обновляет шапку /my_events после удаления или завершения события.
     * 
     * @param userId идентификатор пользователя
     */
    @Transactional
    public void updateMyEventsHeaderAfterRemoval(Long userId) {
        List<Event> activeEvents = eventRepository.findByUserIdAndStatusOrderByEventDateAscEventTimeAsc(
            userId, EventStatus.ACTIVE);
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        
        Long chatId = user.getTelegramId();
        
        if (chatId == null) {
            return;
        }
        
        if (activeEvents.isEmpty()) {
            String emptyMessage = buildEmptyStateMessage();
            try {
                telegramMessageService.sendMessage(chatId, emptyMessage);

            } catch (Exception e) {
                log.error("Ошибка при отправке сообщения о пустом состоянии пользователю ID={}: {}", 
                         userId, e.getMessage(), e);
            }
            return;
        }
        
        Event newFirstEvent = activeEvents.getFirst();
        
        if (!Boolean.TRUE.equals(newFirstEvent.getIsMyEventsHeader())) {
            newFirstEvent.setIsMyEventsHeader(true);
            eventRepository.save(newFirstEvent);
        }
        
        for (int i = 1; i < activeEvents.size(); i++) {
            Event evt = activeEvents.get(i);
            if (Boolean.TRUE.equals(evt.getIsMyEventsHeader())) {
                evt.setIsMyEventsHeader(false);
                eventRepository.save(evt);
            }
        }
        
        // Всегда обновляем сообщение первого события, чтобы счетчик был актуальным
        try {
            sendOrUpdateEventMessage(newFirstEvent, chatId);

        } catch (Exception e) {
            log.error("Ошибка при обновлении сообщения с шапкой для события ID={}: {}", 
                     newFirstEvent.getId(), e.getMessage(), e);
        }
        
    }
    
    /**
     * Формирует сообщение о пустом состоянии /my_events.
     * 
     * @return отформатированное сообщение о пустом состоянии
     */
    private String buildEmptyStateMessage() {
        return botMessageFormattingService.buildEmptyMyEventsMessage();
    }
}
